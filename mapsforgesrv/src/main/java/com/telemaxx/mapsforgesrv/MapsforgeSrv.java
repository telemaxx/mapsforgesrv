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
 ******************************************************************************/

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
import org.slf4j.bridge.SLF4JBridgeHandler;

public class MapsforgeSrv {
	
	private final static String VERSION = "0.20.0"; // starting with eg 0.13, the mapsforge version //$NON-NLS-1$
	
	final static Logger logger = LoggerFactory.getLogger(MapsforgeSrv.class);

	private MapsforgeConfig mapsforgeConfig = null;
	private ExecutorThreadPool pool = null;
	private LinkedBlockingQueue<Runnable> queue = null;
	
	public MapsforgeSrv(String[] args) throws Exception {
		
		/* IMPORTANT: the output of following line is used by other programs like guis. never change this syntax */
		logger.info("MapsforgeSrv - a mapsforge tile server. " + "version: " + VERSION); //$NON-NLS-1$ //$NON-NLS-2$
		
		logger.info("Java runtime version: " + System.getProperty("java.version")); //$NON-NLS-1$

		logger.debug("Current dir [user.dir]: " + System.getProperty("user.dir"));

		mapsforgeConfig = new MapsforgeConfig(args);
		
		queue = new LinkedBlockingQueue<Runnable>(mapsforgeConfig.getMaxQueueSize());
		pool = new ExecutorThreadPool(mapsforgeConfig.getMaxThreads(), mapsforgeConfig.getMinThreads(), queue);
		pool.setIdleTimeout((int)mapsforgeConfig.getIdleTimeout());
		pool.setName("queue");
		Server server = new Server(pool);
		MapsforgeHandler mapsforgeHandler = new MapsforgeHandler(mapsforgeConfig, pool, queue);
		server.setHandler(mapsforgeHandler);
		HttpConfiguration httpConfig = new HttpConfiguration();
		ServerConnector connector = new ServerConnector(server, mapsforgeConfig.SERVERACCEPTORS, mapsforgeConfig.SERVERSELECTORS);
		if (Arrays.asList(mapsforgeConfig.getServerConnectors()).contains("http11") || Arrays.asList(mapsforgeConfig.getServerConnectors()).contains("proxy")) {
			HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);
			if (Arrays.asList(mapsforgeConfig.getServerConnectors()).contains("http11")){
				connector.addConnectionFactory(http11);
				logger.debug("+ add 'http11' connection factory");
			}
			if (!Arrays.asList(mapsforgeConfig.getServerConnectors()).contains("proxy")) {
				ProxyConnectionFactory proxy = new ProxyConnectionFactory(http11.getProtocol());
				connector.addConnectionFactory(proxy);
				logger.debug("+ add 'proxy' connection factory");
			}
		}
		if (Arrays.asList(mapsforgeConfig.getServerConnectors()).contains("h2c")) {
			HTTP2CServerConnectionFactory h2c = new HTTP2CServerConnectionFactory(httpConfig);
			connector.addConnectionFactory(h2c);
			logger.debug("+ add 'h2c' connection factory");
		}
		connector.setAcceptQueueSize(mapsforgeConfig.SERVERACCEPTQUEUESIZE);
		connector.setIdleTimeout(mapsforgeConfig.getIdleTimeout());
		connector.setPort(mapsforgeConfig.getPortNumber());
		if (mapsforgeConfig.getListeningInterface().toLowerCase().equals("localhost"))  //$NON-NLS-1$
			connector.setHost("127.0.0.1");
		server.addConnector(connector);
		try {
			logger.info("################ STARTING SERVER ################");
			server.start();
		} catch (BindException e) {
			logger.error("Stopping server", e); //$NON-NLS-1$
			System.exit(1);
		}
		//logger.debug("> server listening on '"+mapsforgeConfig.getListeningInterface().toLowerCase()+":" + mapsforgeConfig.getPortNumber()+"'"); //$NON-NLS-1$
		logger.info("server connector configured with accept queue size '"+connector.getAcceptQueueSize()+"', idle timeout '"+connector.getIdleTimeout()+"'");
		logger.info("job executor configured with threads min '"+pool.getMinThreads()+"', max '"+pool.getMaxThreads()+"', idle timeout '"+pool.getIdleTimeout()+"'");
		logger.info("job queue configured with max size '"+queue.remainingCapacity()+"'");
		logger.info("server is up and listening on '"+mapsforgeConfig.getListeningInterface().toLowerCase()+":" + mapsforgeConfig.getPortNumber()+"'"); //$NON-NLS-1$
		server.join();
	}

	public static void main(String[] args) throws Exception {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
		new MapsforgeSrv(args);
	}
}

