/*******************************************************************************
 * Copyright 2016 r_x
 * Copyright 2019, 2021, 2022, 2023, 2024 Thomas Theussing and Contributors
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
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 * 
 * changelog:
 * 0.13: selectable style
 * 0.13.1: selectable overlays
 * 0.16.0: mapsforge 16
 * 0.16.1: contrast stretching (JFritzle)
 * 0.16.2: Check for valid tile numbers (JFritzle)
 * 0.16.3: hillshading (JFritzle)
 * 0.16.4: gamma correction (JFritzle)
 * 0.16.5: a lot of improvements/rework (nono303)
 * 0.16.6: bugfix, when a parameter was not given, sometimes the default was not used (nono303)
 * 0.17.0: update mapsforge libs to latest 0.17.0 (nono303)
 * 0.17.1: command line parameters device-scale, user-scale, text-scale, symbol-scale (JFritzle)
 * 0.17.2: hillshading overlay with alpha transparency (JFritzle)
 * 0.17.3: outofrange_tms & maps by directory (nono303)
 * 0.17.4: optionally append built-in world map (JFritzle)
 * 0.17.5: map hillshading 'nodata' to fully transparent (JFritzle)
 *         improve parseHasOption logger (nono303)
 * 0.17.6: return exact contentLength with HTTP response (JFritzle)
 * 0.18.0: mapsforge 18
 * 0.18.1: hillshading overlay gray values by lookup table (JFritzle)
 *         increase zoom level range for hillshading overlay (JFritzle)
 *         update gradle and build.gradle dependencies to latest (JFritzle)
 * 0.19.0: mapsforge 19
 *         update gradle and build.gradle dependencies to latest (JFritzle)
 *         show Java runtime version (JFritzle)
 *         maximum hillshading magnitude = 4 (JFritzle)
 *         terminate server on HTTP request "/terminate" (JFritzle)
 *         - only from loopback addresses
 *         - and only when accepted
 *         set maximum line length of "help" to 132 characters (JFritzle)
 * 0.19.1: raise version to 0.19.1  
 * 0.20.0: mapsforge 0.20.0
 *         raise version to 0.20.0
 *         force sending responses at invalid/unhandled server requests
 *         ignore "IllegalStateException" at terminate server request due to threading
 *         accept internal theme "DEFAULT" or "OSMARENDER" for "themefile" value
 * 0.21.0: mapsforge 0.21.0
 *         raise version to 0.21.0
 *         set stop http server at JVM shutdown behaviour (on HTTP request "/terminate") 
 *         command line parameter line-scale
 *         re-enable HTTP request property "userScale"
 *         some code optimizations
 * 0.21.1: fix "IllegalStateException" on /updatemapstyle request (nono303)
 ******************************************************************************/

package com.telemaxx.mapsforgesrv;

import java.net.BindException;

import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class MapsforgeSrv {
	
	final static Logger logger = LoggerFactory.getLogger(MapsforgeSrv.class);
	private static MapsforgeSrv mapsforgeSrv;
	private static MapsforgeHandler mapsforgeHandler;
	
	public MapsforgeSrv(String[] args) throws Exception {
		
		/* IMPORTANT: the output of following line is used by other programs like guis. never change this syntax */
		logger.info("MapsforgeSrv - a mapsforge tile server. " + "version: " + PropertiesParser.VERSION); //$NON-NLS-1$ //$NON-NLS-2$
		
		logger.info("Java runtime version: " + System.getProperty("java.version")); //$NON-NLS-1$

		logger.debug("Current dir [user.dir]: " + System.getProperty("user.dir"));

		MapsforgeConfig mapsforgeConfig = new MapsforgeConfig(args);
		
		logger.info("################ STARTING SERVER ################");
		XmlConfiguration xmlConfiguration = null;
		QueuedThreadPool queuedThreadPool = new QueuedThreadPool();
		xmlConfiguration = new XmlConfiguration(Resource.newResource(mapsforgeConfig.getConfigDirectory()+MapsforgeConfig.FILECONFIG_JETTY_THREADPOOL));
		xmlConfiguration.configure(queuedThreadPool);
		Server server = new Server(queuedThreadPool);
		xmlConfiguration = new XmlConfiguration(Resource.newResource(mapsforgeConfig.getConfigDirectory()+MapsforgeConfig.FILECONFIG_JETTY));
		xmlConfiguration.configure(server);
		try {
			if(!((QueuedThreadPool)server.getThreadPool()).getVirtualThreadsExecutor().equals(null))
				logger.info("Virtual threads are enabled");
		} catch (NullPointerException e) {
			logger.info("Virtual threads are disabled");
		};
		mapsforgeHandler = new MapsforgeHandler(mapsforgeConfig);
		server.setHandler(mapsforgeHandler);
		
		Slf4jRequestLogWriter slfjRequestLogWriter = new Slf4jRequestLogWriter();
		slfjRequestLogWriter.setLoggerName("com.telemaxx.mapsforgesrv.request");
		CustomRequestLog customRequestLog = new CustomRequestLog(slfjRequestLogWriter, mapsforgeConfig.getRequestLogFormat());
		server.setRequestLog(customRequestLog);

		try {
			server.start();
			logger.info("Started " +server.getThreadPool().toString());
		} catch (BindException e) {
			logger.error("Stopping server", e); //$NON-NLS-1$
			System.exit(1);
		}
		server.join();
	}
	
	public static void main(String[] args) throws Exception {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
		mapsforgeSrv = new MapsforgeSrv(args);
	}
	
	public static MapsforgeSrv getMapsforgeSrv () {
		return mapsforgeSrv;
	}
	
	public static MapsforgeHandler getMapsforgeHandler () {
		return mapsforgeHandler;
	}
}

