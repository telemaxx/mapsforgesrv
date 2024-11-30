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
 * 0.21.2: Adopt some changes from "styles" branch:
 *         Handle UTF-8 characters in config file correctly
 *         Automatically enable built-in world map if map definition is missing or empty
 *         Remove request query parameters x, y and z and log invalid tile request paths
 *         Assign default file extension .png if file extension missing in tile request path
 * 0.21.3.0: Refactored server using config files only
 *           Initial version (nono303)
 *           Fixes, improvements, extensions (JFritzle)
 * 0.21.3.1: Fixes, improvements, extensions (JFritzle)
 * 0.21.4.0: Update build.gradle, use gradle 8.11, shrink JAR size to 1/3
 ******************************************************************************/

package com.telemaxx.mapsforgesrv;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder;

public class MapsforgeSrv {

	final static Logger logger = LoggerFactory.getLogger(MapsforgeSrv.class);
	static FileSystem memoryFileSystem;
	private static MapsforgeConfig mapsforgeConfig = null;
	private static Server server = null;

	public MapsforgeSrv(String[] args) throws Exception {

		/* IMPORTANT: the output of following line is used by other programs like guis. never change this syntax */
		logger.info("MapsforgeSrv - a mapsforge tile server. " + "version: " + PropertiesParser.VERSION); //$NON-NLS-1$ //$NON-NLS-2$

		Runtime.Version runtimeVersion = Runtime.version();
		logger.info("Java runtime version: " + runtimeVersion); //$NON-NLS-1$

		logger.debug("Current dir [user.dir]: " + System.getProperty("user.dir"));

		memoryFileSystem = MemoryFileSystemBuilder.newEmpty().build();
		mapsforgeConfig = new MapsforgeConfig(args);

		logger.info("################ STARTING SERVER ################");
		{	// Begin of local scope
		XmlConfiguration xmlConfiguration = null;
		String jettyXML = null;
		Resource resource = null;
		File file = null;
		Path path = null;

		if (runtimeVersion.version().get(0) >= 21) {
			jettyXML = MapsforgeConfig.FILECONFIG_JETTY_THREADPOOL_VR;
		} else {
			jettyXML = MapsforgeConfig.FILECONFIG_JETTY_THREADPOOL;
		}
		file = new File(mapsforgeConfig.getConfigDirectory()+jettyXML);
		if (file.isFile()) {
			resource = Resource.newResource(file);
		} else {
			resource = Resource.newSystemResource("assets/mapsforgesrv/"+jettyXML);
		}
		path = Files.createTempFile(memoryFileSystem.getPath(""), null, ".xml");
		overrideResource(resource,path);
		resource = Resource.newResource(path);
		xmlConfiguration = new XmlConfiguration(resource);
		Files.delete(path);
		QueuedThreadPool queuedThreadPool = new QueuedThreadPool();
		xmlConfiguration.configure(queuedThreadPool);
		queuedThreadPool.setStopTimeout(0);

		jettyXML = MapsforgeConfig.FILECONFIG_JETTY;
		file = new File(mapsforgeConfig.getConfigDirectory()+jettyXML);
		if (file.isFile()) {
			resource = Resource.newResource(file);
		} else {
			resource = Resource.newSystemResource("assets/mapsforgesrv/"+jettyXML);
		}
		path = Files.createTempFile(memoryFileSystem.getPath(""), null, ".xml");
		overrideResource(resource,path);
		resource = Resource.newResource(path);
		xmlConfiguration = new XmlConfiguration(resource);
		Files.delete(path);
		server = new Server(queuedThreadPool);
		xmlConfiguration.configure(server);
		try {
			if(!((QueuedThreadPool)server.getThreadPool()).getVirtualThreadsExecutor().equals(null))
				logger.info("Virtual threads are enabled");
		} catch (NullPointerException e) {
			logger.info("Virtual threads are disabled");
		};
		MapsforgeHandler mapsforgeHandler = new MapsforgeHandler(mapsforgeConfig);
		server.setHandler(mapsforgeHandler);
		server.setStopAtShutdown(true);
		server.setStopTimeout(0L);

		String requestLogFormat = mapsforgeConfig.getRequestLogFormat();
		if (!requestLogFormat.equals("")) {
			Slf4jRequestLogWriter slfjRequestLogWriter = new Slf4jRequestLogWriter();
			slfjRequestLogWriter.setLoggerName("com.telemaxx.mapsforgesrv.request");
			CustomRequestLog customRequestLog = new CustomRequestLog(slfjRequestLogWriter, requestLogFormat);
			server.setRequestLog(customRequestLog);
		}
		
		}	// End of local scope: allow garbage collection of no longer referenced objects and save heap memory

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
		new MapsforgeSrv(args);
	}

	public static void stop() throws Exception {
		server.stop();
		System.exit(0);
	}

	public static Server getServer () {
		return server;
	}

	/*
	 * Read jetty XML resource into document
	 * Override jetty XML properties by server.properties values
	 * Write modified document as XML file to path
	*/
	private static void overrideResource(Resource resource, Path path) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		DocumentBuilder db = dbf.newDocumentBuilder();
		InputStream inputStream = resource.getInputStream();
		Document xmlDoc = db.parse(inputStream);
		inputStream.close();
		NodeList setNodes = xmlDoc.getElementsByTagName("Set");
		int setIndex = setNodes.getLength();
		while (setIndex-- > 0) {
			Node setNode = setNodes.item(setIndex);
			String setName = setNode.getAttributes().getNamedItem("name").getTextContent();
			String propertyValue = mapsforgeConfig.retrieveConfigValue(setName);
			if (propertyValue != null) {
				if (!setNode.hasChildNodes()) {
					String propertyName = setNode.getAttributes().getNamedItem("property").getTextContent();
					setNode.getAttributes().removeNamedItem("property");
					Element property = xmlDoc.createElement("Property");
					property.setAttribute("name", propertyName);
					property.setAttribute("default", propertyValue);
					setNode.appendChild(property);
				} else {
					NodeList childNodes = setNode.getChildNodes();
					int childIndex = childNodes.getLength();
					while (childIndex > 0) {
						Node child = childNodes.item(--childIndex);
						if (child.getNodeType() == Node.ELEMENT_NODE) {
							if (child.getNodeName().equals("Property")) {
								child.getAttributes().getNamedItem("default").setTextContent(propertyValue);
							}
						}
					}
				}
			}
		}
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.STANDALONE, "no");
		DocumentType doctype = xmlDoc.getDoctype();
		if(doctype != null) {
			transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, doctype.getPublicId());
			transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, doctype.getSystemId());
		}
		OutputStream outputStream = Files.newOutputStream(path,StandardOpenOption.CREATE);
		transformer.transform(new DOMSource(xmlDoc), new StreamResult(outputStream));
		outputStream.close();
	}

}

