/*******************************************************************************
 * Copyright 2016 r_x
 * Copyright 2019, 2021, 2022 Thomas Theussing and Contributors
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Str, Fifth Floor, Boston, MA 02110, USA
 *
 *******************************************************************************/

package com.telemaxx.mapsforgesrv;

import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MapsforgeHandler extends AbstractHandler {

	final static Logger logger = LoggerFactory.getLogger(MapsforgeHandler.class);

	private final TreeSet<String> KNOWN_PARAMETER_NAMES = new TreeSet<>(Arrays.asList(
			new String[] { "textScale", "userScale", "transparent", "tileRenderSize", "hillshading", "task" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

	protected final GraphicFactory graphicFactory = AwtGraphicFactory.INSTANCE;

	private Map<String, MapsforgeTaskHandler> tasksHandler;
	private MapsforgeConfig mapsforgeConfig;

	private static boolean stopped = false;

	public MapsforgeHandler(MapsforgeConfig mapsforgeConfig) throws Exception {
		super();

		this.mapsforgeConfig = mapsforgeConfig;

		tasksHandler = new HashMap<String, MapsforgeTaskHandler>();
		for(String task : mapsforgeConfig.getTasksConfig().keySet()) {
			MapsforgeTaskHandler mapsforgeTaskHandler = new MapsforgeTaskHandler(this, mapsforgeConfig.getTaskConfig(task), task);
			tasksHandler.put(task, mapsforgeTaskHandler);
		}
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
		baseRequest.setHandled(true);
		String path = request.getPathInfo();
		try {
			if (path.equals("/terminate")) { //$NON-NLS-1$
				// Accept terminate request from loopback addresses only!
				if (baseRequest.getHttpChannel().getRemoteAddress().getAddress().isLoopbackAddress()
						&& mapsforgeConfig.getAcceptTerminate()) {
					response.setContentLength(0);
					response.setStatus(HttpServletResponse.SC_OK);
					response.flushBuffer();
					stopped = true;
					MapsforgeSrv.stop();
				} else {
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
				}
				return;
			}

			if (path.equals("/favicon.ico")) { //$NON-NLS-1$
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}

			if (path.equals("/updatemapstyle")) { //$NON-NLS-1$
				StringBuffer updatedThemes = new StringBuffer();
				for(String key : tasksHandler.keySet()) {
					tasksHandler.get(key).updateRenderThemeFuture();
					updatedThemes.append(key+" updated<br>");
				}
				updatedThemes.append("<br>Nb Threads: "+Thread.getAllStackTraces().size()+"<br>");
				for(Thread th : Thread.getAllStackTraces().keySet())
					if(th.getName().startsWith("RenderThemeFuture"))
						updatedThemes.append(th.getName()+" updated<br>");
				response.setHeader("Cache-Control", "private, no-cache");
				response.setHeader("Pragma", "no-cache");
				response.setContentType("text/html;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				baseRequest.setHandled(true);
				response.getWriter().println("<html><body><h1>updatemapstyle</h1>"+updatedThemes.toString()+"</body></html>");
				response.flushBuffer();
				return;
			}

			Enumeration<String> paramNames = request.getParameterNames();
			while (paramNames.hasMoreElements()) {
				String name = paramNames.nextElement();
				if (!KNOWN_PARAMETER_NAMES.contains(name)) {
					throw new ServletException("Unsupported query parameter: " + name); //$NON-NLS-1$
				}
			}

			/* task */
			String key = request.getParameter("task");
			if(key == null || key.isEmpty()) {
				key = "default";
			} else {
				if(tasksHandler.get(key) == null)
					throw new ServletException("Unsupported task: " + key); //$NON-NLS-1$
			}
			tasksHandler.get(key).handle(target, baseRequest, request, response);
		} catch (Exception e) {
			if (stopped) return;
			String extmsg = ExceptionUtils.getRootCauseMessage(e);
			try {
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, extmsg);
			} catch (IOException e1) {
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			} catch (IllegalStateException e2) {
				logger.warn(request.getRequestURI()+"?"+request.getQueryString()+" : "+e2.getMessage()); //$NON-NLS-1$
			}
		}
	}

	public GraphicFactory getGraphicFactory() {
		return graphicFactory;
	}

	public MapsforgeConfig getMapsforgeConfig() {
		return mapsforgeConfig;
	}

	public Map<String, MapsforgeTaskHandler> getTasksHandler() {
		return tasksHandler;
	}


}
