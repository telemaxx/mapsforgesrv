package com.telemaxx.mapsforgesrv;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.eclipse.jetty.server.Request;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.hills.DemFolderFS;
import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.HillsRenderConfig;
import org.mapsforge.map.layer.hills.MemoryCachingHgtReaderTileSource;
import org.mapsforge.map.layer.hills.ShadingAlgorithm;
import org.mapsforge.map.layer.hills.SimpleShadingAlgorithm;
import org.mapsforge.map.layer.labels.TileBasedLabelStore;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleLayer;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu;
import org.mapsforge.map.rendertheme.XmlThemeResourceProvider;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MapsforgeTaskHandler {

	final static Logger logger = LoggerFactory.getLogger(MapsforgeTaskHandler.class);

	private final DisplayModel displayModel;
	private final TileBasedLabelStore labelStore;
	private final TileCache tileCache;
	private final MultiMapDataStore multiMapDataStore;
	private final DemFolderFS demFolder;
	private final File themeFile;
	private final String themeFileStyle;
	private final boolean renderLabels;
	private final boolean cacheLabels;

	private boolean taskEnabled = true;
	private boolean hillShadingOverlay = false;
	private HillsRenderConfig hillsRenderConfig = null;
	private XmlRenderTheme xmlRenderTheme;
	private RenderThemeFuture renderThemeFuture;
	private ShadingAlgorithm shadingAlgorithm = null;
	private int[] colorLookupTable = null;
	private String name;
	private Map<String, DatabaseRenderer> databaseRenderer = null;

	private MapsforgeHandler mapsforgeHandler;
	private MapsforgeConfig mapsforgeConfig;
	private MapsforgeTaskConfig mapsforgeTaskConfig;

	private CountDownLatch countDownLatch = new CountDownLatch(0);

	private static final Pattern requestPathPattern = Pattern.compile("/(\\d+)/(-?\\d+)/(-?\\d+)(?:(?:\\.)(.*))?"); //$NON-NLS-1$

	public MapsforgeTaskHandler(MapsforgeHandler mapsforgeHandler, MapsforgeTaskConfig mapsforgeTaskConfig, String name) throws Exception {

		logger.info("################ STARTING TASK '"+name+"' ################"); //$NON-NLS-1$

		this.name = name;
		this.mapsforgeHandler = mapsforgeHandler;
		this.mapsforgeConfig = mapsforgeHandler.getMapsforgeConfig();
		this.mapsforgeTaskConfig = mapsforgeTaskConfig;

		logger.info("------------------- MAPS INFO --------------------");
		multiMapDataStore = new MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL);
		if (mapsforgeTaskConfig.getMapFiles().size() == 0) {
			if (mapsforgeTaskConfig.getHillShadingAlgorithm() != null && mapsforgeTaskConfig.getDemFolder() != null) {
				hillShadingOverlay = true;
			}
		} else {
			mapsforgeTaskConfig.getMapFiles().forEach(mapFile -> {
				MapFile map = new MapFile(mapFile, mapsforgeTaskConfig.getPreferredLanguage());
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
		if (mapsforgeTaskConfig.getAppendWorldMap()) {
			FileChannel mapFileChannel = FileChannel.open(MapsforgeConfig.worldMapPath, StandardOpenOption.READ);
			MapFile map = new MapFile(mapFileChannel);
			logger.info("'(built-in)" + System.getProperty("file.separator") + "world.map'");
			if (mapsforgeTaskConfig.getMapFiles().size() > 0) map.restrictToZoomRange((byte)0, (byte)9);
			multiMapDataStore.addMapDataStore(map, true, true);
		}
		if(mapsforgeTaskConfig.getDemFolder() != null) {
			demFolder = new DemFolderFS(mapsforgeTaskConfig.getDemFolder());
		} else {
			demFolder = null;
		}

		if (hillShadingOverlay) {
			logger.info("No map -> hillshading overlay with alpha transparency only!");
			tileCache = null;
			labelStore = null;
			renderLabels = false;
			cacheLabels = false;
			colorLookupTable = new int[256];
			int pixelValue,gray,dist,alpha;
			int range = (int)(30*mapsforgeTaskConfig.getHillShadingMagnitude()); // gray value range
			if (range > 120) range = 120;	// maximum value range is 120
			int base = 248-range; // obviously base gray value depending on hillshading magnitude
			int index = 256;
			while (index-- > 0) {
				gray = index;
				dist = gray-base;				// distance to base gray value
				alpha = Math.abs(dist) * 2;		// alpha value is 2 * abs(distance) to base gray value
				if (alpha > 255) alpha = 255;	// limit alpha to fully opaque
				pixelValue = alpha << 24;		// black pixel with variable alpha transparency
				if (dist > 0)					// present gray value lighter than base gray:
					pixelValue |= 0x00ffffff;	// white pixel with variable alpha transparency
				colorLookupTable[index] = pixelValue;
			}
		} else {
			tileCache = new DummyCache(1024);
			labelStore = new TileBasedLabelStore(1024);
			renderLabels = true;
			cacheLabels = true;
			int blackValue = mapsforgeTaskConfig.getBlackValue();
			double gammaValue = mapsforgeTaskConfig.getGammaValue();
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
		}

		DisplayModel.setDeviceScaleFactor(mapsforgeTaskConfig.getDeviceScale());
		DisplayModel.textScale = mapsforgeTaskConfig.getTextScale();
		DisplayModel.symbolScale = mapsforgeTaskConfig.getSymbolScale();
		DisplayModel.lineScale = mapsforgeTaskConfig.getLineScale();
		displayModel = new DisplayModel();
		displayModel.setUserScaleFactor(mapsforgeTaskConfig.getUserScale());

		if (mapsforgeTaskConfig.getHillShadingAlgorithm() != null && mapsforgeTaskConfig.getDemFolder() != null) { // hillshading
			if (mapsforgeTaskConfig.getHillShadingAlgorithm().equals("simple")) {
				shadingAlgorithm = new SimpleShadingAlgorithm(mapsforgeTaskConfig.getHillShadingArguments()[0],
						mapsforgeTaskConfig.getHillShadingArguments()[1]);
			} else {
				shadingAlgorithm = new DiffuseLightShadingAlgorithm(
						(float) mapsforgeTaskConfig.getHillShadingArguments()[0]);
			}

			MemoryCachingHgtReaderTileSource tileSource = new MemoryCachingHgtReaderTileSource(
					demFolder, shadingAlgorithm, mapsforgeHandler.getGraphicFactory());
			tileSource.setEnableInterpolationOverlap(MapsforgeConfig.HILLSHADING_INTERPOLATION_OVERLAP);
			tileSource.setMainCacheSize(MapsforgeConfig.HILLSHADING_CACHE);
			if(MapsforgeConfig.HILLSHADING_INTERPOLATION_OVERLAP)
				tileSource.setNeighborCacheSize(MapsforgeConfig.HILLSHADING_NEIGHBOR_CACHE);
			tileSource.applyConfiguration(true); // true for allow parallel

			hillsRenderConfig = new HillsRenderConfig(tileSource);
			hillsRenderConfig.setMaginuteScaleFactor((float) mapsforgeTaskConfig.getHillShadingMagnitude());
			hillsRenderConfig.indexOnThread();
		}

		databaseRenderer = new HashMap<String, DatabaseRenderer>();
		databaseRenderer.put("std", new DatabaseRenderer(multiMapDataStore, mapsforgeHandler.getGraphicFactory(), tileCache,
				labelStore, renderLabels, cacheLabels, null));
		if (hillsRenderConfig != null)
			databaseRenderer.put("hs", new DatabaseRenderer(multiMapDataStore, mapsforgeHandler.getGraphicFactory(), tileCache,
					labelStore, renderLabels, cacheLabels, hillsRenderConfig));

		XmlRenderThemeMenuCallback callBack = new XmlRenderThemeMenuCallback() {
			@Override
			public Set<String> getCategories(XmlRenderThemeStyleMenu styleMenu) {
				String id = null;
				if (themeFileStyle != null) {
					id = themeFileStyle;
				} else {
					id = styleMenu.getDefaultValue();
				}

				XmlRenderThemeStyleLayer baseLayer = styleMenu.getLayer(id);
				Set<String> result = baseLayer.getCategories();
				logger.info("----------------- THEME OVERLAYS -----------------"); //$NON-NLS-1$
				for (XmlRenderThemeStyleLayer overlay : baseLayer.getOverlays()) {
					String overlayId = overlay.getId();
					boolean overlayEnabled = false;
					String[] themeFileOverlays = mapsforgeTaskConfig.getThemeFileOverlays();
					if (themeFileOverlays == null) {
						overlayEnabled = overlay.isEnabled();
					} else {
						for (int i = 0; i < themeFileOverlays.length; i++) {
							if (themeFileOverlays[i].equals(overlayId))
								overlayEnabled = true;
						}
					}
					logger.info("'" + overlayId + "' enabled: " + Boolean.toString(overlayEnabled)
							+ ", title: '" + overlay.getTitle(mapsforgeTaskConfig.getPreferredLanguage()) + "'");
					if (overlayEnabled) {
						result.addAll(overlay.getCategories());
					}
				}

				countDownLatch.countDown();
				return result;
			}
		};

		themeFile = mapsforgeTaskConfig.getThemeFile();
		themeFileStyle = mapsforgeTaskConfig.getThemeFileStyle();

		if (hillShadingOverlay) {
			xmlRenderTheme = MyInternalRenderTheme.HILLSHADING;
		} else if (themeFile == null || themeFile.getPath().equals("OSMARENDER")) {
			xmlRenderTheme = MyInternalRenderTheme.OSMARENDER;
		} else if (themeFile.getPath().equals("DEFAULT")) {
			xmlRenderTheme = MyInternalRenderTheme.DEFAULT;
		} else {
			countDownLatch = new CountDownLatch(1);
			try {
				xmlRenderTheme = new ExternalRenderTheme(themeFile, callBack);
				showStyleNames();
			} catch (Exception e) {
				countDownLatch.countDown();
				logger.error("Defined theme file '"+themeFile+"' does not exist or cannot be read: Task "+name+" disabled"); //$NON-NLS-1$
				taskEnabled = false;
				return;
			}
		}

		updateRenderThemeFuture();
		
		countDownLatch.await();
		logger.info("--------------------------------------------------"); //$NON-NLS-1$
	}

	public CountDownLatch getCountDownLatch() {
		return countDownLatch;
	}

	protected void updateRenderThemeFuture() {
		renderThemeFuture = new RenderThemeFuture(mapsforgeHandler.getGraphicFactory(), xmlRenderTheme, displayModel);
		String tname = "RenderThemeFuture-"+name;
		for (Thread t : Thread.getAllStackTraces().keySet()) {
		if (t.getName().equals(tname)) {
			t.interrupt();
			logger.debug("Thread '"+tname+"' successfully stopped.");
		}
	    }
		new Thread(null,renderThemeFuture,tname).start();
	}

	protected void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws Exception {
		String path = request.getPathInfo();
		String engine = "std";
	
		if (!taskEnabled) {
			logger.error("Task "+name+" disabled. Invalid tile request: "+path); //$NON-NLS-1$
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}

		int x, y, z;
		String ext = MapsforgeConfig.TILE_EXTENSION; // $NON-NLS-1$
		Matcher m = requestPathPattern.matcher(path);
		if (m.matches()) {
			x = Integer.parseInt(m.group(2));
			y = Integer.parseInt(m.group(3));
			z = Integer.parseInt(m.group(1));
			if (m.group(4) != null) ext = m.group(4);
		} else {
			logger.error("Invalid tile request: "+path); //$NON-NLS-1$
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
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
//				Override text scaling from config value by text scaling from HTTP request
//				Final text scaling = textScale * requestedTextScale
				requestedTextScale = Float.parseFloat(tmp) / DisplayModel.textScale;
			}
		} catch (Exception e) {
			throw new ServletException("Failed to parse \"textScale\" property: " + e.getMessage(), e); //$NON-NLS-1$
		}

		float requestedUserScale = mapsforgeTaskConfig.getUserScale();
		try {
			String tmp = request.getParameter("userScale"); //$NON-NLS-1$
			if (tmp != null) {
				requestedUserScale = Float.parseFloat(tmp);
			}
		} catch (Exception e) {
			throw new ServletException("Failed to parse \"userScale\" property: " + e.getMessage(), e); //$NON-NLS-1$
		}

		boolean requestedTransparent = MapsforgeConfig.DEFAULT_TRANSPARENT;
		try {
			String tmp = request.getParameter("transparent"); //$NON-NLS-1$
			if (tmp != null) {
				requestedTransparent = Boolean.parseBoolean(tmp);
			}
		} catch (Exception e) {
			throw new ServletException("Failed to parse \"transparent\" property: " + e.getMessage(), e); //$NON-NLS-1$
		}

		int requestedTileRenderSize = MapsforgeConfig.DEFAULT_TILE_RENDERSIZE;
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
			if (hillsRenderConfig != null && enable_hs) engine = "hs";

			// requestedUserScale = 2.0f; // Uncomment for testing purpose only!
//			Calling "displayModel.setUserScaleFactor" alone has no visible impact on rendering.
//			Starting new "renderThemeFuture" afterwards helps, but can significantly impact rendering performance
//			if different TMS clients take turns requesting different userScale values !!!
			if (displayModel.getUserScaleFactor() != requestedUserScale) {
				displayModel.setUserScaleFactor (requestedUserScale);
				updateRenderThemeFuture();
			}

			RendererJob job = new RendererJob(tile, multiMapDataStore, renderThemeFuture, displayModel,
				requestedTextScale, requestedTransparent, false);

//Synchronizing render jobs has no visible effect -> disabled
//				synchronized (this) {
				tileBitmap = databaseRenderer.get(engine).executeJob(job);
				if (!hillShadingOverlay) tileCache.put(job, null);
//				}
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
						newPixelArray[pixelCount] = colorLookupTable[pixelValue & 0xff];
					}
				}
				image = newImage; // return transparent overlay image instead of original image
			} else if (colorLookupTable != null) { // apply gamma correction and/or contrast-stretching
				while (pixelCount-- > 0) {
					pixelValue = pixelArray[pixelCount];
					pixelArray[pixelCount] = (pixelValue & 0xff000000) // alpha value
							| (colorLookupTable[(pixelValue >>> 16) & 0xff] << 16) // red value
							| (colorLookupTable[(pixelValue >>> 8) & 0xff] << 8) // green value
							| (colorLookupTable[pixelValue & 0xff]); // blue value
				}
			}
			response.setStatus(HttpServletResponse.SC_OK);
		} else {
			String outOfRangeTms = mapsforgeConfig.getOutOfRangeTms();;
			if(outOfRangeTms != null) {
				response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
				String redirecturl = outOfRangeTms.replace("{x}", x+"").replace("{y}", y+"").replace("{z}", z+"");
				response.setHeader("Location", redirecturl);
				response.flushBuffer();
				logger.info("out-of-range redirect '"+redirecturl+"'");
				return;
			} else {
				image = MapsforgeConfig.BI_NOCONTENT;
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

	/**
	 * Display all styles contained in theme
	 * Disable task if defined style does not exist
	 */
	protected void showStyleNames() throws Exception {
		MapsforgeStyleParser mapStyleParser = new MapsforgeStyleParser();
		List<Style> styles = mapStyleParser.readXML(xmlRenderTheme.getRenderThemeAsStream());
		Boolean selectedStyleExists = false;
		String defaultStyle = mapStyleParser.getDefaultStyle();
		logger.info("------------------ THEME STYLES ------------------"); //$NON-NLS-1$
		logger.info("Default style   : " + defaultStyle); //$NON-NLS-1$
		for (final Style style : styles) {
			logger.info("Available style : " + style.getXmlLayer() + " --> " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					+ style.getName(Locale.getDefault().getLanguage()));
			if (style.getXmlLayer().equals(themeFileStyle)) {
				selectedStyleExists = true;
			}
		}
		if (themeFileStyle == null) {
			logger.info("Used style      : " + defaultStyle); //$NON-NLS-1$
		} else if (selectedStyleExists) {
			logger.info("Used style      : " + themeFileStyle); //$NON-NLS-1$
		} else {
			logger.error("Defined style '" + themeFileStyle+"' not available: Task " + name + " disabled"); //$NON-NLS-1$
			taskEnabled = false;
			return;
		}
	}

	// Enumeration of tile server's internal rendering themes
	// (copied and extended from InternalRenderTheme enumeration)
	// Using StreamRenderThemes throws exception when calling "updateRenderThemeFuture" within "handle"
	private enum MyInternalRenderTheme implements XmlRenderTheme {
		DEFAULT("/assets/mapsforge/default.xml"),
		OSMARENDER("/assets/mapsforge/osmarender.xml"),
		HILLSHADING("/assets/mapsforgesrv/hillshading.xml");
		private XmlRenderThemeMenuCallback menuCallback;
		private final String path;
		MyInternalRenderTheme(String path) {
			this.path = path;
		}
		@Override
		public XmlRenderThemeMenuCallback getMenuCallback() {
			return menuCallback;
		}
		// @return the prefix for all relative resource paths.
		@Override
		public String getRelativePathPrefix() {
			return "/assets/";
		}
		@Override
		public InputStream getRenderThemeAsStream() {
			return getClass().getResourceAsStream(this.path);
		}
		@Override
		public XmlThemeResourceProvider getResourceProvider() {
			return null;
		}
		@Override
		public void setMenuCallback(XmlRenderThemeMenuCallback menuCallback) {
			this.menuCallback = menuCallback;
		}
		@Override
		public void setResourceProvider(XmlThemeResourceProvider resourceProvider) {
		}
	}
}
