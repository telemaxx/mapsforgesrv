/*******************************************************************************
 * Copyright 2016 r_x
 * Copyright 2019, 2021 Thomas Theussing and Contributors
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
 * 0.16.1: contrast stretching (JFBeck)
 * 0.16.2: Check for valid tile numbers (JFBeck)
 * 0.16.3: hillshading (JFBeck)
 *******************************************************************************/

package com.telemaxx.mapsforgesrv;

import java.net.BindException;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ProxyConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapsforgeSrv {

		final static Logger logger = LoggerFactory.getLogger(MapsforgeSrv.class);

	private MapsforgeConfig mapsforgeConfig = null;
	private ExecutorThreadPool pool = null;
	private LinkedBlockingQueue<Runnable> queue = null;


	public MapsforgeSrv(String[] args) throws Exception {
		mapsforgeConfig = new MapsforgeConfig(args);
		mapsforgeConfig.initConfig();
		
		logger.info("MapsforgeSrv - a mapsforge tile server. " + "version: " + mapsforgeConfig.VERSION); //$NON-NLS-1$ //$NON-NLS-2$
		logger.info("Current dir: " + System.getProperty("user.dir"));

		queue = new LinkedBlockingQueue<Runnable>(mapsforgeConfig.getMaxQueueSize());
		pool = new ExecutorThreadPool(mapsforgeConfig.getMaxThreads(), mapsforgeConfig.getMinThreads(), queue);
		pool.setName("queue");
		Server server = new Server(pool);
		HttpConfiguration httpConfig = new HttpConfiguration();
		ServerConnector connector = new ServerConnector(server, mapsforgeConfig.SERVERACCEPTORS, mapsforgeConfig.SERVERSELECTORS);
		if (Arrays.asList(mapsforgeConfig.getServerConnectors()).contains("http11") || Arrays.asList(mapsforgeConfig.getServerConnectors()).contains("proxy")) {
			HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);
			if (Arrays.asList(mapsforgeConfig.getServerConnectors()).contains("http11")){
				connector.addConnectionFactory(http11);
			}
			if (!Arrays.asList(mapsforgeConfig.getServerConnectors()).contains("proxy")) {
				ProxyConnectionFactory proxy = new ProxyConnectionFactory(http11.getProtocol());
				connector.addConnectionFactory(proxy);
			}
		}
		if (Arrays.asList(mapsforgeConfig.getServerConnectors()).contains("h2c")) {
			HTTP2CServerConnectionFactory h2c = new HTTP2CServerConnectionFactory(httpConfig);
			connector.addConnectionFactory(h2c);
		}
		connector.setAcceptQueueSize(mapsforgeConfig.SERVERACCEPTQUEUESIZE);
		connector.setIdleTimeout(mapsforgeConfig.getIdleTimeout());
		if (mapsforgeConfig.getListeningInterface().toLowerCase().equals("all")) { //$NON-NLS-1$
			logger.info("listening on all interfaces, port:" + mapsforgeConfig.getPortNumber()); //$NON-NLS-1$
			connector.setPort(mapsforgeConfig.getPortNumber());
		} else if (mapsforgeConfig.getListeningInterface().toLowerCase().equals("localhost")) { //$NON-NLS-1$
			logger.info("listening on localhost port:" + mapsforgeConfig.getPortNumber()); //$NON-NLS-1$
			connector.setPort(mapsforgeConfig.getPortNumber());
			connector.setHost("127.0.0.1");
		} else {
			logger.error("unkown Interface, only \"all\" or \"localhost\" , not " //$NON-NLS-1$
					+ mapsforgeConfig.getListeningInterface());
			System.exit(1);
		}
		server.addConnector(connector);

		MapsforgeHandler mapsforgeHandler = new MapsforgeHandler(mapsforgeConfig, pool, queue);
		server.setHandler(mapsforgeHandler);
		try {
			server.start();
		} catch (BindException e) {
			logger.error("Stopping server", e); //$NON-NLS-1$
			System.exit(1);
		}
		server.join();
	}

	public static void main(String[] args) throws Exception {
		new MapsforgeSrv(args);
	}
}