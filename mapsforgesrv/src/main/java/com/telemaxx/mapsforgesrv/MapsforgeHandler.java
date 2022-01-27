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
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.mapelements.MapElementContainer;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.awt.graphics.AwtTileBitmap;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.HillsRenderConfig;
import org.mapsforge.map.layer.hills.MemoryCachingHgtReaderTileSource;
import org.mapsforge.map.layer.hills.ShadingAlgorithm;
import org.mapsforge.map.layer.hills.SimpleShadingAlgorithm;
import org.mapsforge.map.layer.labels.TileBasedLabelStore;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.DirectRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleLayer;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class MapsforgeHandler extends AbstractHandler {

	final static Logger logger = LoggerFactory.getLogger(MapsforgeHandler.class);

	private final TreeSet<String> KNOWN_PARAMETER_NAMES = new TreeSet<>(Arrays.asList(
			new String[] { "x", "y", "z", "textScale", "userScale", "transparent", "tileRenderSize", "hillshading" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

	protected final MultiMapDataStore multiMapDataStore;
	protected final DisplayModel displayModel;
	protected HillsRenderConfig hillsRenderConfig = null;
	protected Map<String, DatabaseRenderer> databaseRenderer = null;
	protected Map<String, DirectRenderer> directRenderer = null;
	protected XmlRenderTheme xmlRenderTheme;
	protected RenderThemeFuture renderThemeFuture;
	protected XmlRenderThemeStyleMenu renderThemeStyleMenu;
	protected TileBasedLabelStore tileBasedLabelStore = new MyTileBasedLabelStore(1000);
	protected DummyCache labelInfoCache = new DummyCache();

	protected final File themeFile;
	protected final String themeFileStyle;
	
	protected int blackValue;
	protected double gammaValue;
	
	protected float deviceScale;
	protected float userScale;
	protected float textScale;
	protected float symbolScale;
	
	private ExecutorThreadPool pool;
	private LinkedBlockingQueue<Runnable> queue;
	private MapsforgeConfig mapsforgeConfig;
	private ShadingAlgorithm shadingAlgorithm = null;
	private int[] colorLookupTable = null;

	private static final Pattern P = Pattern.compile("/(\\d+)/(-?\\d+)/(-?\\d+)\\.(.*)"); //$NON-NLS-1$
	private static BufferedImage BI_NOCONTENT;

	public MapsforgeHandler(MapsforgeConfig mapsforgeConfig, ExecutorThreadPool pool,
			LinkedBlockingQueue<Runnable> queue) throws IOException {
		super();
		BI_NOCONTENT = ImageIO.read(getClass().getClassLoader().getResourceAsStream(("assets/mapsforgesrv/no_content.png")));
		// https://stackoverflow.com/questions/10235728/convert-bufferedimage-into-byte-without-i-o
		ImageIO.setUseCache(false);
		this.mapsforgeConfig = mapsforgeConfig;
		this.pool = pool;
		this.queue = queue;
		
		this.themeFile = mapsforgeConfig.getThemeFile();
		this.themeFileStyle = mapsforgeConfig.getThemeFileStyle();
		
		this.blackValue = mapsforgeConfig.getBlackValue();
		this.gammaValue = mapsforgeConfig.getGammaValue();
		// first apply gamma correction and then contrast-stretching
		if (gammaValue != 1. || blackValue != 0) {
			colorLookupTable = new int[256];
			double gammaExponent = 1. / gammaValue;
			double blackNormalized = blackValue / 255.;
			double stretchFactor = 1. / (1. - blackNormalized);
			int index = 256;
			double value;
			while (index-- > 0) {
				value = index / 255.;
				value = Math.pow(value, gammaExponent);
				value = value > blackNormalized ? ((value - blackNormalized) * stretchFactor) : 0.;
				colorLookupTable[index] = (int) Math.round(value * 255.);
			}
		}
		
		this.deviceScale = mapsforgeConfig.getDeviceScale();
		this.userScale   = mapsforgeConfig.getUserScale();
		this.textScale   = mapsforgeConfig.getTextScale();
		this.symbolScale = mapsforgeConfig.getSymbolScale();
		
		GraphicFactory graphicFactory = AwtGraphicFactory.INSTANCE;
		multiMapDataStore = new MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL);

		logger.info("################### MAPS INFO ###################");
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

		DisplayModel.setDeviceScaleFactor(deviceScale);
		DisplayModel.symbolScale = symbolScale;
		displayModel = new DisplayModel();

		if (mapsforgeConfig.getHillShadingAlgorithm() != null && mapsforgeConfig.getDemFolder() != null) { // hillshading
			if (mapsforgeConfig.getHillShadingAlgorithm().equals("simple")) {
				shadingAlgorithm = new SimpleShadingAlgorithm(mapsforgeConfig.getHillShadingArguments()[0],
						mapsforgeConfig.getHillShadingArguments()[1]);
			} else {
				shadingAlgorithm = new DiffuseLightShadingAlgorithm(
						(float) mapsforgeConfig.getHillShadingArguments()[0]);
			}
			MemoryCachingHgtReaderTileSource tileSource = new MemoryCachingHgtReaderTileSource(
					mapsforgeConfig.getDemFolder(), shadingAlgorithm, graphicFactory);
			tileSource.setEnableInterpolationOverlap(mapsforgeConfig.HILLSHADINGENABLEINTERPOLATIONOVERLAP);
			hillsRenderConfig = new HillsRenderConfig(tileSource);
			hillsRenderConfig.setMaginuteScaleFactor((float) mapsforgeConfig.getHillShadingMagnitude());
			hillsRenderConfig.indexOnThread();
		}

		if (mapsforgeConfig.getRendererName().equals("direct")) {
			directRenderer = new HashMap<String, DirectRenderer>();
			if (hillsRenderConfig != null)
				directRenderer.put("hs",
						new DirectRenderer(multiMapDataStore, graphicFactory, true, hillsRenderConfig));
			directRenderer.put("std", new DirectRenderer(multiMapDataStore, graphicFactory, true, null));
		} else {
			databaseRenderer = new HashMap<String, DatabaseRenderer>();
			if (hillsRenderConfig != null)
				databaseRenderer.put("hs", new DatabaseRenderer(multiMapDataStore, graphicFactory, labelInfoCache,
						tileBasedLabelStore, true, true, hillsRenderConfig));
			databaseRenderer.put("std", new DatabaseRenderer(multiMapDataStore, graphicFactory, labelInfoCache,
					tileBasedLabelStore, true, true, null));
		}

		renderThemeFuture = new RenderThemeFuture(graphicFactory, xmlRenderTheme, displayModel);
		XmlRenderThemeMenuCallback callBack = new XmlRenderThemeMenuCallback() {

			@Override
			public Set<String> getCategories(XmlRenderThemeStyleMenu styleMenu) {
				renderThemeStyleMenu = styleMenu;

				String id = null;
				renderThemeStyleMenu = styleMenu;
				if (themeFileStyle != null) {
					id = themeFileStyle;
				} else {
					id = styleMenu.getDefaultValue();
				}

				XmlRenderThemeStyleLayer baseLayer = styleMenu.getLayer(id);
				Set<String> result = baseLayer.getCategories();
				logger.info("################# OVERLAY  INFO #################"); //$NON-NLS-1$
				for (XmlRenderThemeStyleLayer overlay : baseLayer.getOverlays()) {
					String overlayId = overlay.getId();
					boolean overlayEnabled = false;
					String[] themeFileOverlays = mapsforgeConfig.getThemeFileOverlays();
					if (themeFileOverlays == null) {
						overlayEnabled = overlay.isEnabled();
					} else {
						for (int i = 0; i < themeFileOverlays.length; i++) {
							if (themeFileOverlays[i].equals(overlayId))
								overlayEnabled = true;
						}
					}
					logger.info("'" + overlayId + "' enabled: " + Boolean.toString(overlayEnabled)
							+ ", title: '" + overlay.getTitle(mapsforgeConfig.getPreferredLanguage()) + "'");

					if (overlayEnabled) {
						result.addAll(overlay.getCategories());
					}
				}

				return result;
			}

		};
		if (themeFile == null) {
			xmlRenderTheme = InternalRenderTheme.OSMARENDER;
		} else {
			showStyleNames();
			xmlRenderTheme = new ExternalRenderTheme(themeFile, callBack);

		}
		updateRenderThemeFuture();
	}

	protected void updateRenderThemeFuture() {
		renderThemeFuture = new RenderThemeFuture(AwtGraphicFactory.INSTANCE, xmlRenderTheme, displayModel);
		new Thread(renderThemeFuture).start();
	}

	/**
	 * displaying the containing styles inside theme xml if a style is given(!=null)
	 * but this style did not exist the program terminates
	 */
	protected void showStyleNames() {
		final MapsforgeStyleParser mapStyleParser = new MapsforgeStyleParser();
		final List<Style> styles = mapStyleParser.readXML(themeFile.getAbsolutePath());
		Boolean selectedStyleExists = false;
		logger.info("################ THEME FILE INFO ################"); //$NON-NLS-1$
		logger.info("Default Style: " + mapStyleParser.getDefaultStyle()); //$NON-NLS-1$
		for (final Style style : styles) {
			logger.info("Stylename to use for '-s' option: " + "'" + style.getXmlLayer() + "'" + " --> " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					+ style.getName(Locale.getDefault().getLanguage()));

			if (style.getXmlLayer().equals(themeFileStyle)) {
				selectedStyleExists = true;
			}
		}
		if (!selectedStyleExists && themeFileStyle != null) {
			logger.error("The defined style '"+themeFileStyle+"' not found: existing"); //$NON-NLS-1$
			System.exit(2);
		}
		if (selectedStyleExists && themeFileStyle != null) {
			logger.info("The defined style '"+themeFileStyle+"' found"); //$NON-NLS-1$
		}
	}

	private String logRequest(HttpServletRequest request, long startTime, Exception ex, String engine) {
		// request
		String msg = request.getPathInfo() + "?" + request.getQueryString();
		// response time;idle threads;queue size
		if (mapsforgeConfig.LOGREQDET)
			msg += " [ms:" + Math.round((System.nanoTime() - startTime) / 1000000) + ";idle:" + this.pool.getIdleThreads()
				+ ";qs:" + (this.queue.size()) + "]";
		// hillshading configuration
		if (engine == "hs" && mapsforgeConfig.LOGREQDETHS)
			msg += " " + StringUtils.chop(shadingAlgorithm.toString()) + ", magnitude="
					+ mapsforgeConfig.getHillShadingMagnitude() + "}";
		// exception
		if (ex != null)
			return msg + " ! " + ex.getMessage() + System.lineSeparator() + ExceptionUtils.getStackTrace(ex);
		return msg;
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
		long startTime = System.nanoTime();
		String engine = "std";
		try {

			if (request.getPathInfo().equals("/favicon.ico")) { //$NON-NLS-1$
				response.setStatus(404);
				return;
			}

			if (request.getPathInfo().equals("/updatemapstyle")) { //$NON-NLS-1$
				updateRenderThemeFuture();
				try (ServletOutputStream out = response.getOutputStream();) {
					out.print("<html><body><h1>updatemapstyle</h1>OK</body></html>"); //$NON-NLS-1$
					out.flush();
				}
				response.setStatus(200);
				return;
			}

			response.setStatus(500);

			if ((databaseRenderer == null && directRenderer == null) || xmlRenderTheme == null)
				return;

			Enumeration<String> paramNames = request.getParameterNames();
			while (paramNames.hasMoreElements()) {
				String name = paramNames.nextElement();
				if (!KNOWN_PARAMETER_NAMES.contains(name)) {
					throw new ServletException("Unsupported query parameter: " + name); //$NON-NLS-1$
				}
			}

			String path = request.getPathInfo();

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
				return;
			}
			if (y < 0 || y >= (1 << z)) {
				logger.error("Tile number y=" + y + " out of range!"); //$NON-NLS-1$
				return;
			}
			
			float requestedTextScale = textScale;
			try {
				String tmp = request.getParameter("textScale"); //$NON-NLS-1$
				if (tmp != null) {
					requestedTextScale = Float.parseFloat(tmp);
				}
			} catch (Exception e) {
				throw new ServletException("Failed to parse \"textScale\" property: " + e.getMessage(), e); //$NON-NLS-1$
			}

			float requestedUserScale = userScale;
			try {
				String tmp = request.getParameter("userScale"); //$NON-NLS-1$
				if (tmp != null) {
					requestedUserScale = Float.parseFloat(tmp);
				}
			} catch (Exception e) {
				throw new ServletException("Failed to parse \"userScale\" property: " + e.getMessage(), e); //$NON-NLS-1$
			}
			if (requestedUserScale != userScale)
				displayModel.setUserScaleFactor(requestedUserScale);
			
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
			
			RendererJob job;
			Bitmap tileBitmap;
			Tile tile = new Tile(x, y, (byte) z, requestedTileRenderSize);
			job = new RendererJob(tile, multiMapDataStore, renderThemeFuture, displayModel,
					requestedTextScale, requestedTransparent, false);

			boolean enable_hs = true;
			try {
				String tmp = request.getParameter("hillshading"); //$NON-NLS-1$
				if (tmp != null)
					enable_hs = Integer.parseInt(request.getParameter("hillshading")) != 0; //$NON-NLS-1$
			} catch (Exception e) {
				throw new ServletException("Failed to parse \"hillshading\" property: " + e.getMessage(), e); //$NON-NLS-1$
			}

			if (hillsRenderConfig != null && enable_hs)
				engine = "hs";
//			synchronized (this) {		// Thread synchronization disabled for performance reasons
				if (directRenderer != null) {
					tileBitmap = (AwtTileBitmap) directRenderer.get(engine).executeJob(job);
				} else {
					tileBitmap = (AwtTileBitmap) databaseRenderer.get(engine).executeJob(job);
					labelInfoCache.put(job, null);
				}
//			}
			baseRequest.setHandled(true);
			BufferedImage image;
			if (tileBitmap != null) {
				image = AwtGraphicFactory.getBitmap(tileBitmap);
				if (colorLookupTable != null) { // gamma correction and/or contrast-stretching
					// DataBuffer created by Mapsforge renderer is of type DataBufferInt, i.e. one
					// int value 0xaarrggbb per pixel
					DataBufferInt dataBuffer = (DataBufferInt) image.getRaster().getDataBuffer();
					int[] pixelArray = dataBuffer.getData();
					int pixelCount = image.getWidth() * image.getHeight();
					int pixelValue;
					while (pixelCount-- > 0) {
						pixelValue = pixelArray[pixelCount];
						pixelArray[pixelCount] = (pixelValue & 0xff000000) // alphaValue
								| (colorLookupTable[(pixelValue >>> 16) & 0xff] << 16) // redValue
								| (colorLookupTable[(pixelValue >>> 8) & 0xff] << 8) // greenValue
								| (colorLookupTable[pixelValue & 0xff]); // blueValue
					}
				}
				response.setStatus(200);			
			} else {
				// TODO temp
				image = BI_NOCONTENT;
				response.setStatus(404);
			}
			if (mapsforgeConfig.getCacheControl() > 0) {
				response.addHeader("Cache-Control", "public, max-age=" + mapsforgeConfig.getCacheControl()); //$NON-NLS-1$ //$NON-NLS-2$
			}	
			response.setContentType("image/" + ext); //$NON-NLS-1$
			ImageIO.write(image, ext, response.getOutputStream());
			logger.info(logRequest(request, startTime, null, engine));
		} catch (Exception e) {
			String extmsg = ExceptionUtils.getRootCauseMessage(e);
			try {
				response.sendError(500, extmsg);
			} catch (IOException e1) {
				response.setStatus(500);
			}
			logger.error(logRequest(request, startTime, e, engine));
		}
	}

	private static class MyTileBasedLabelStore extends TileBasedLabelStore {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public MyTileBasedLabelStore(int capacity) {
			super(capacity);
		}

		@Override
		public synchronized List<MapElementContainer> getVisibleItems(Tile upperLeft, Tile lowerRight) {
			return super.getVisibleItems(upperLeft, lowerRight);
		}

	}

}
