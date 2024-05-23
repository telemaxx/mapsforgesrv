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

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MapsforgeHandler extends AbstractHandler {

	final static Logger logger = LoggerFactory.getLogger(MapsforgeHandler.class);

	private final TreeSet<String> KNOWN_PARAMETER_NAMES = new TreeSet<>(Arrays.asList(
			new String[] { "x", "y", "z", "textScale", "userScale", "transparent", "tileRenderSize", "hillshading", "style" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

	protected final MultiMapDataStore multiMapDataStore;
	protected final GraphicFactory graphicFactory = AwtGraphicFactory.INSTANCE;
	protected final String outOfRangeTms;

	private Map<String, MapsforgeStyleHandler> stylesHandler;
	private MapsforgeConfig mapsforgeConfig;
	private boolean hillShadingOverlay = false;

	private static boolean stopped = false;
	private static final Pattern P = Pattern.compile("/(\\d+)/(-?\\d+)/(-?\\d+)\\.(.*)"); //$NON-NLS-1$
	private static BufferedImage BI_NOCONTENT;

	public MapsforgeHandler(MapsforgeConfig mapsforgeConfig) throws Exception {
		super();
		// https://stackoverflow.com/questions/10235728/convert-bufferedimage-into-byte-without-i-o
		ImageIO.setUseCache(false);
		BI_NOCONTENT = ImageIO.read(getClass().getClassLoader().getResourceAsStream("assets/mapsforgesrv/no_content.png"));

		this.mapsforgeConfig = mapsforgeConfig;
		outOfRangeTms = mapsforgeConfig.getOutOfRangeTms();

		logger.info("################### MAPS INFO ###################");
		multiMapDataStore = new MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL);
		boolean appendWorldMap = mapsforgeConfig.getAppendWorldMap();
		if (mapsforgeConfig.getMapFiles().size() == 0) {
			appendWorldMap = true;
			if (mapsforgeConfig.getDefaultStyle().getHillShadingAlgorithm() != null && mapsforgeConfig.getDemFolder() != null) {
				hillShadingOverlay = true;
			}
		} else {
			mapsforgeConfig.getMapFiles().forEach(mapFile -> {
				MapFile map = new MapFile(mapFile, mapsforgeConfig.getPreferredLanguage());
				String[] mapLanguages = map.getMapLanguages();
				String msgMap = "'" + mapFile + "' supported languages: ";
				if (mapLanguages != null) {
					logger.info(msgMap+"{"+String.join(",", mapLanguages)+"}");
				} else {
					logger.info(msgMap+"-");
				}
				multiMapDataStore.addMapDataStore(map, true, true);
			});
		}
		// Append built-in world.map
		if (appendWorldMap) {
			InputStream inputStream = getClass().getResourceAsStream("/assets/mapsforgesrv/world.map");
			// FileSystem fileSystem = Jimfs.newFileSystem();
			FileSystem fileSystem = MemoryFileSystemBuilder.newEmpty().build();
			Path rootPath = fileSystem.getPath("");
			Path worldMapPath = rootPath.resolve("world.map");
			Files.copy(inputStream, worldMapPath, StandardCopyOption.REPLACE_EXISTING);
			FileChannel mapFileChannel = FileChannel.open(worldMapPath, StandardOpenOption.READ);
			MapFile map = new MapFile(mapFileChannel);
			logger.info("'(built-in)" + System.getProperty("file.separator") + "world.map'");
			multiMapDataStore.addMapDataStore(map, true, true);
		}

		stylesHandler = new HashMap<String, MapsforgeStyleHandler>();
		for(String style : mapsforgeConfig.getStylesConfig().keySet()) 
			stylesHandler.put(style, new MapsforgeStyleHandler(this, mapsforgeConfig.getStylesConfig(style), style));
	}

	private String logRequest(HttpServletRequest request, long startTime, Exception ex, String engine) {
		// request
		String msg = request.getPathInfo();
		String query = request.getQueryString();
		if (query != null) msg += "?" + query;
		// response time;idle threads
		if (mapsforgeConfig.LOG_RESP_TIME)
			msg = String.format("%-" + 6 + "s", Math.round((System.nanoTime() - startTime) / 1000000))+msg;
		// exception
		if (ex != null)
			return msg + " ! " + ex.getMessage() + System.lineSeparator() + ExceptionUtils.getStackTrace(ex);
		return msg;
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
		baseRequest.setHandled(true);
		long startTime = System.nanoTime();
		String path = request.getPathInfo();
		String engine = "std";
		try {
			if (path.equals("/terminate")) { //$NON-NLS-1$
				// Accept terminate request from loopback addresses only!
				if (baseRequest.getHttpChannel().getRemoteAddress().getAddress().isLoopbackAddress()
						&& mapsforgeConfig.getAcceptTerminate()) {
					response.setContentLength(0);
					response.setStatus(HttpServletResponse.SC_OK);
					response.flushBuffer();
					stopped = true;
					System.exit(0);
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
				for(String key : stylesHandler.keySet()) 
					stylesHandler.get(key).updateRenderThemeFuture();
				response.setHeader("Cache-Control", "private, no-cache");
				response.setHeader("Pragma", "no-cache");
				response.setContentType("text/html;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				baseRequest.setHandled(true);
				response.getWriter().println("<html><body><h1>updatemapstyle</h1>OK</body></html>");
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
			
			/* style */
			MapsforgeStyleHandler styleHandler = null;
			MapsforgeStyleConfig  styleConfig = null;
			if(request.getParameter("style") == null || request.getParameter("style").isEmpty()) {
				styleHandler = stylesHandler.get("default");
				styleConfig = mapsforgeConfig.getStylesConfig("default");
			} else {
				styleHandler = stylesHandler.get(request.getParameter("style"));
				if(styleHandler == null) 
					throw new ServletException("Unsupported style: " + request.getParameter("style")); //$NON-NLS-1$
				styleConfig = mapsforgeConfig.getStylesConfig(request.getParameter("style"));
			}

			int x, y, z;
			String ext = mapsforgeConfig.EXTENSIONDEFAULT; // $NON-NLS-1$
			Matcher m = P.matcher(path);
			if (m.matches()) {
				x = Integer.parseInt(m.group(2));
				y = Integer.parseInt(m.group(3));
				z = Integer.parseInt(m.group(1));
				ext = m.group(4);
			} else {
				x = Integer.parseInt(request.getParameter("x")); //$NON-NLS-1$
				y = Integer.parseInt(request.getParameter("y")); //$NON-NLS-1$
				z = Integer.parseInt(request.getParameter("z")); //$NON-NLS-1$
			}
			if (x < 0 || x >= (1 << z)) {
				logger.error("Tile number x=" + x + " out of range!"); //$NON-NLS-1$
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return;
			}
			if (y < 0 || y >= (1 << z)) {
				logger.error("Tile number y=" + y + " out of range!"); //$NON-NLS-1$
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return;
			}

			float requestedTextScale = 1.0f; // Original text scaling comes from config value
			try {
				String tmp = request.getParameter("textScale"); //$NON-NLS-1$
				if (tmp != null) {
//					Override text scaling from config value by text scaling from HTTP request
//					Final text scaling = textScale * requestedTextScale
					requestedTextScale = Float.parseFloat(tmp) / styleConfig.getTextScale();
				}
			} catch (Exception e) {
				throw new ServletException("Failed to parse \"textScale\" property: " + e.getMessage(), e); //$NON-NLS-1$
			}

			float requestedUserScale = styleHandler.getDisplayModel().getUserScaleFactor();
			try {
				String tmp = request.getParameter("userScale"); //$NON-NLS-1$
				if (tmp != null) {
					requestedUserScale = Float.parseFloat(tmp);
				}
			} catch (Exception e) {
				throw new ServletException("Failed to parse \"userScale\" property: " + e.getMessage(), e); //$NON-NLS-1$
			}

			boolean requestedTransparent = mapsforgeConfig.TRANSPARENTDEFAULT;
			try {
				String tmp = request.getParameter("transparent"); //$NON-NLS-1$
				if (tmp != null) {
					requestedTransparent = Boolean.parseBoolean(tmp);
				}
			} catch (Exception e) {
				throw new ServletException("Failed to parse \"transparent\" property: " + e.getMessage(), e); //$NON-NLS-1$
			}

			int requestedTileRenderSize = mapsforgeConfig.TILERENDERSIZEDEFAULT;
			try {
				String tmp = request.getParameter("tileRenderSize"); //$NON-NLS-1$
				if (tmp != null)
					requestedTileRenderSize = Integer.parseInt(tmp);
			} catch (Exception e) {
				throw new ServletException("Failed to parse \"tileRenderSize\" property: " + e.getMessage(), e); //$NON-NLS-1$
			}

			TileBitmap tileBitmap = null;
			Tile tile = new Tile(x, y, (byte) z, requestedTileRenderSize);
			if (multiMapDataStore.supportsTile(tile)) {
				boolean enable_hs = true;
				try {
					String tmp = request.getParameter("hillshading"); //$NON-NLS-1$
					if (tmp != null)
						enable_hs = Integer.parseInt(request.getParameter("hillshading")) != 0; //$NON-NLS-1$
				} catch (Exception e) {
					throw new ServletException("Failed to parse \"hillshading\" property: " + e.getMessage(), e); //$NON-NLS-1$
				}
				if (styleHandler.getHillsRenderConfig() != null && enable_hs) engine = "hs";

				// requestedUserScale = 2.0f; // Uncomment for testing purpose only!
//				Calling "displayModel.setUserScaleFactor" alone has no visible impact on rendering.
//				Starting new "renderThemeFuture" afterwards helps, but can significantly impact rendering performance
//				if different TMS clients take turns requesting different userScale values !!!
				if (styleHandler.getDisplayModel().getUserScaleFactor() != requestedUserScale) {
					styleHandler.getDisplayModel().setUserScaleFactor (requestedUserScale);
					styleHandler.updateRenderThemeFuture();
				}

				RendererJob job = new RendererJob(tile, multiMapDataStore, styleHandler.getRenderThemeFuture(), styleHandler.getDisplayModel(),
					requestedTextScale, requestedTransparent, false);

// Synchronizing render jobs has no visible effect -> disabled
// 				synchronized (this) {
					if (mapsforgeConfig.getRendererName().equals("direct")) {
						tileBitmap = styleHandler.getDirectRenderer().get(engine).executeJob(job);
					} else {
						tileBitmap = styleHandler.getDatabaseRenderer().get(engine).executeJob(job);
					}
					if (!hillShadingOverlay) styleHandler.getTileCache().put(job, null);
// 				}
			}

			BufferedImage image;
			if (tileBitmap != null) {
				image = AwtGraphicFactory.getBitmap(tileBitmap); // image type is TYPE_INT_RGB
				//int imageType = image.getType(); // returns 1 (TYPE_INT_RGB)
				int imageWidth  = image.getWidth();
				int imageHeight = image.getHeight();
				//int dataBufferType = image.getRaster().getDataBuffer().getDataType(); // returns 3 (TYPE_INT)
				// DataBuffer created by Mapsforge renderer is of type DataBufferInt,
				// i.e. one int value 0xaarrggbb per pixel
				DataBufferInt dataBuffer = (DataBufferInt) image.getRaster().getDataBuffer();
				int[] pixelArray = dataBuffer.getData();
				int pixelValue;
				int pixelCount = imageWidth * imageHeight;
				if (hillShadingOverlay) { // transparent hillshading overlay image requested
					BufferedImage newImage = new BufferedImage (imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
					DataBufferInt newDataBuffer = (DataBufferInt) newImage.getRaster().getDataBuffer();
					int[] newPixelArray = newDataBuffer.getData();
					while (pixelCount-- > 0) {
						pixelValue = pixelArray[pixelCount];
						if (pixelValue == 0xfff8f8f8) { // 'nodata' hillshading value
							newPixelArray[pixelCount] = 0x00000000; // fully transparent pixel
						} else { // get gray value of pixel from blue value of pixel
							newPixelArray[pixelCount] = styleHandler.getColorLookupTable()[pixelValue & 0xff];
						}
					}
					image = newImage; // return transparent overlay image instead of original image
				} else if (styleHandler.getColorLookupTable() != null) { // apply gamma correction and/or contrast-stretching
					while (pixelCount-- > 0) {
						pixelValue = pixelArray[pixelCount];
						pixelArray[pixelCount] = (pixelValue & 0xff000000) // alpha value
								| (styleHandler.getColorLookupTable()[(pixelValue >>> 16) & 0xff] << 16) // red value
								| (styleHandler.getColorLookupTable()[(pixelValue >>> 8) & 0xff] << 8) // green value
								| (styleHandler.getColorLookupTable()[pixelValue & 0xff]); // blue value
					}
				}
				response.setStatus(HttpServletResponse.SC_OK);
			} else {
				if(this.outOfRangeTms != null) {
					response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
					String redirecturl = this.outOfRangeTms.replace("{x}", x+"").replace("{y}", y+"").replace("{z}", z+"");
					response.setHeader("Location", redirecturl);
					response.flushBuffer();
					logger.info("out-of-range redirect '"+redirecturl+"'");
					return;
				} else {
					image = BI_NOCONTENT;
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
			if (mapsforgeConfig.getCacheControl() > 0) {
				response.addHeader("Cache-Control", "public, max-age=" + mapsforgeConfig.getCacheControl()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			response.setContentType("image/" + ext); //$NON-NLS-1$
			//ImageIO.write(image, ext, response.getOutputStream());
			int bufferSize = 256 + 4*image.getWidth()*image.getHeight(); // Assume image data size <= bufferSize
			MyResponseBufferOutputStream responseBufferStream = new MyResponseBufferOutputStream(bufferSize);
			ImageIO.write(image, ext, responseBufferStream);
			responseBufferStream.flush(response);
			responseBufferStream.close();
			logger.info(logRequest(request, startTime, null, engine));
		} catch (Exception e) {
			if (stopped) return;
			String extmsg = ExceptionUtils.getRootCauseMessage(e);
			try {
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, extmsg);
			} catch (IOException e1) {
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
			logger.error(logRequest(request, startTime, e, engine));
		}
	}

	// Extend class ByteArrayOutputStream by setting content length on buffer flush
	private static class MyResponseBufferOutputStream extends ByteArrayOutputStream {
		public MyResponseBufferOutputStream(int bufferSize) {
			buf = new byte[bufferSize];
		}
		public void flush (HttpServletResponse response) throws IOException {
			response.setContentLength(count);
			ServletOutputStream responseOutputStream = response.getOutputStream();
			responseOutputStream.write(buf, 0, count);
			responseOutputStream.flush();
		}
	}
	
	public MultiMapDataStore getMultiMapDataStore() {
		return multiMapDataStore;
	}
	
	public GraphicFactory getGraphicFactory() {
		return graphicFactory;
	}
	
	public MapsforgeConfig getMapsforgeConfig() {
		return mapsforgeConfig;
	}
	
	public boolean isHillShadingOverlay() {
		return hillShadingOverlay;
	}
}
