package com.telemaxx.mapsforgesrv;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.eclipse.jetty.server.Request;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.hills.AClasyHillShading.ClasyParams;
import org.mapsforge.map.layer.hills.AdaptiveClasyHillShading;
import org.mapsforge.map.layer.hills.DemFolderFS;
import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.HiResClasyHillShading;
import org.mapsforge.map.layer.hills.HillsRenderConfig;
import org.mapsforge.map.layer.hills.MemoryCachingHgtReaderTileSource;
import org.mapsforge.map.layer.hills.ShadingAlgorithm;
import org.mapsforge.map.layer.hills.SimpleClasyHillShading;
import org.mapsforge.map.layer.hills.SimpleShadingAlgorithm;
import org.mapsforge.map.layer.hills.StandardClasyHillShading;
import org.mapsforge.map.layer.labels.LabelStore;
import org.mapsforge.map.layer.labels.TileBasedLabelStore;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;
import org.mapsforge.map.rendertheme.StreamRenderTheme;
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

	private final String taskName;
	private final DisplayModel displayModel;
	private final TileBasedLabelStore labelStore;
	private final TileCache tileCache;
	private final MultiMapDataStore multiMapDataStore;
	private final String language;
	private final File themeFile;
	private final String themeFileStyle;
	private final boolean renderLabels;
	private final boolean cacheLabels;

	private boolean taskEnabled = true;
	private int themeStyleCheck;
	private boolean hillShadingOverlay = false;
	private HillsRenderConfig hillsRenderConfig = null;
	private XmlRenderTheme xmlRenderTheme;
	private RenderThemeFuture renderThemeFuture;
	private int[] colorLookupTable = null;

	private volatile Rendering rendering = null;
	private boolean neighbourLabels = false;

	// The renderers and, with neighbour labels, the render theme their label store was built for.
	// They are replaced together, so a request never pairs one theme with another theme's labels.
	private static final class Rendering {
		final Map<String, DatabaseRenderer> renderers;
		final RenderThemeFuture labelTheme; // null: the request uses the current theme
		Rendering(Map<String, DatabaseRenderer> renderers, RenderThemeFuture labelTheme) {
			this.renderers = renderers;
			this.labelTheme = labelTheme;
		}
	}

	private MapsforgeHandler mapsforgeHandler;
	private MapsforgeConfig mapsforgeConfig;
	private MapsforgeTaskConfig mapsforgeTaskConfig;

	private static final Pattern requestPathPattern = Pattern.compile("/(\\d+)/(-?\\d+)/(-?\\d+)(?:(?:\\.)(.*))?");

	public MapsforgeTaskHandler(MapsforgeHandler mapsforgeHandler, MapsforgeTaskConfig mapsforgeTaskConfig, String name) throws Exception {

		logger.info("################ STARTING TASK '" + name + "' ################");

		this.taskName = name;
		this.mapsforgeHandler = mapsforgeHandler;
		this.mapsforgeConfig = mapsforgeHandler.getMapsforgeConfig();
		this.mapsforgeTaskConfig = mapsforgeTaskConfig;
		this.language = mapsforgeTaskConfig.getPreferredLanguage();
		
		DisplayModel.setDeviceScaleFactor(mapsforgeTaskConfig.getDeviceScale());
		DisplayModel.textScale = mapsforgeTaskConfig.getTextScale();
		DisplayModel.symbolScale = mapsforgeTaskConfig.getSymbolScale();
		DisplayModel.lineScale = mapsforgeTaskConfig.getLineScale();
		displayModel = new DisplayModel();
		displayModel.setUserScaleFactor(mapsforgeTaskConfig.getUserScale());

		int mapFilesSize = mapsforgeTaskConfig.getMapFiles().size();
		String hillShadingAlgorithm = mapsforgeTaskConfig.getHillShadingAlgorithm();
		File demFolder = mapsforgeTaskConfig.getDemFolder();

		logger.info("------------------- MAPS INFO --------------------");
		multiMapDataStore = new MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL);
		if (mapFilesSize == 0) {
			if (hillShadingAlgorithm != null && demFolder != null) hillShadingOverlay = true;
		} else {
			List<File> mapFiles = mapsforgeTaskConfig.getMapFiles();
			List<Integer> pathLen = new ArrayList<Integer>();
			int maxlen = 0;
			Pattern pattern = Pattern.compile(".\\p{M}*"); // Any single Unicode character
			for (File mapFile : mapFiles) {
				Matcher matcher = pattern.matcher(mapFile.getPath());
				int strlen = 0;
				while (matcher.find()) strlen++; // Count Unicode characters
				pathLen.add(strlen);
				if (strlen > maxlen) maxlen = strlen;
			}
			for (File mapFile : mapFiles) {
				MapFile map = new MapFile(mapFile, language);
				String[] mapLanguages = map.getMapLanguages();
				int strlen = pathLen.remove(0);
				String padding = String.format("%-" + (maxlen-strlen+1) + "s", " ");
				logger.info("Map : '" + mapFile.getPath() + "'" + padding + "- Languages : "
						+ (mapLanguages == null ? "-" : "{" + String.join(",", mapLanguages) + "}"));
				multiMapDataStore.addMapDataStore(map, true, true);
			}
		}
		// Append built-in world.map with background map priority
		if (mapsforgeTaskConfig.getAppendWorldMap()) {
			FileChannel mapFileChannel = FileChannel.open(MapsforgeConfig.worldMapPath, StandardOpenOption.READ);
			MapFile map = new MapFile(mapFileChannel);
			logger.info("Map : '(built-in)" + System.getProperty("file.separator") + "world.map'");
			if (mapFilesSize > 0) {
				MultiMapDataStore worldMapDataStore = new MultiMapDataStore();
				worldMapDataStore.setPriority(-1);
				worldMapDataStore.addMapDataStore(map, true, true);
				multiMapDataStore.addMapDataStore(worldMapDataStore, true, true);
			} else {
				multiMapDataStore.addMapDataStore(map, true, true);
			}
		}

		DemFolderFS demFolderFS = null;
		if (demFolder != null) demFolderFS = new DemFolderFS(demFolder);

		if (hillShadingOverlay) {
			logger.info("-> Hillshading overlay map with alpha transparency");
			themeFile = new File("HillShadingOverlay");
			themeFileStyle = null;
			tileCache = null;
			labelStore = null;
			renderLabels = false;
			cacheLabels = false;
			// Mapsforge >= 0.22.0: hillshading bitmap calculation changed
			colorLookupTable = new int[256];
			int pixelValue,gray,alpha;
			int index = 256;
			while (index-- > 0) {
				gray = index;
				alpha = 255-gray;
				pixelValue = alpha << 24;
				colorLookupTable[index] = pixelValue;
			}
		} else {
			themeFile = mapsforgeTaskConfig.getThemeFile();
			themeFileStyle = mapsforgeTaskConfig.getThemeFileStyle();
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

		if (hillShadingAlgorithm != null && demFolder != null) {
			// hillshading
			ShadingAlgorithm shadingAlgorithm = null;
			if (hillShadingAlgorithm.equals("simple")) {
				shadingAlgorithm = new SimpleShadingAlgorithm(mapsforgeTaskConfig.getHillShadingArguments()[0],
						mapsforgeTaskConfig.getHillShadingArguments()[1]);
			} else if (hillShadingAlgorithm.equals("diffuselight")) {
				shadingAlgorithm = new DiffuseLightShadingAlgorithm(
						(float) mapsforgeTaskConfig.getHillShadingArguments()[0]);
			} else if (Arrays.stream(new String[] {"adaptasy","stdasy","hiresasy","simplasy"}).anyMatch(hillShadingAlgorithm::equals)) {
				ClasyParams clasyParams = new ClasyParams();
				clasyParams.setAsymmetryFactor((float) mapsforgeTaskConfig.getHillShadingArguments()[0]);
				clasyParams.setMinSlope((float) mapsforgeTaskConfig.getHillShadingArguments()[1]);
				clasyParams.setMaxSlope((float) mapsforgeTaskConfig.getHillShadingArguments()[2]);
				clasyParams.setReadingThreadsCount((int) mapsforgeTaskConfig.getHillShadingArguments()[3]);
				clasyParams.setComputingThreadsCount((int) mapsforgeTaskConfig.getHillShadingArguments()[4]);
				clasyParams.setPreprocess(mapsforgeTaskConfig.getHillShadingArguments()[5] == 1);
				switch (hillShadingAlgorithm) {
					case "adaptasy":
						shadingAlgorithm = new AdaptiveClasyHillShading(clasyParams,MapsforgeConfig.HILLSHADING_ADAPTIVE_HQ);
						((AdaptiveClasyHillShading)shadingAlgorithm).setAdaptiveZoomEnabled(MapsforgeConfig.HILLSHADING_ADAPTIVE_ZOOM_ENABLED);
						((AdaptiveClasyHillShading)shadingAlgorithm).setCustomQualityScale(MapsforgeConfig.HILLSHADING_ADAPTIVE_CUSTOM_QUALITY_SCALE);
						break;
					case "hiresasy":
						shadingAlgorithm = new HiResClasyHillShading(clasyParams);
						break;
					case "stdasy":
						shadingAlgorithm = new StandardClasyHillShading(clasyParams);
						break;
					case "simplasy":
						shadingAlgorithm = new SimpleClasyHillShading(clasyParams);
						break;
				}
			} else {
				throw new Exception("Unknown HillShadingAlgorithm '" + hillShadingAlgorithm + "'");
			}

			MemoryCachingHgtReaderTileSource tileSource = new MemoryCachingHgtReaderTileSource(
					demFolderFS, shadingAlgorithm, mapsforgeHandler.getGraphicFactory(),
					MapsforgeConfig.HILLSHADING_INTERPOLATION_OVERLAP);
			tileSource.applyConfiguration(true); // true for allow parallel

			hillsRenderConfig = new HillsRenderConfig(tileSource);
			// Mapsforge >= 0.22.0: RenderThemeHandler.java raised default magnitude from 64 to 128
			// For look-and-feel backward compatibility, specified MagnitudeScaleFactor must be divided by 2
			hillsRenderConfig.setMagnitudeScaleFactor((float) (0.5*mapsforgeTaskConfig.getHillShadingMagnitude()));
			hillsRenderConfig.indexOnThread();
		}

		// Neighbour labels: each tile reads the labels of its 3x3 neighbourhood from the map data and
		// places them by a fixed rule, so adjacent tiles agree on every label crossing their border no
		// matter which process renders them or in what order. The tile-based label store only knows
		// neighbours this process has already rendered, so with several server processes (or any
		// request order) a label can be drawn on one side of a tile border and missing on the other.
		// The label store needs the render theme, so in this mode the renderers are created once the
		// theme is ready (updateRenderThemeFuture).
		neighbourLabels = mapsforgeTaskConfig.getNeighbourLabels() && !hillShadingOverlay;
		if (!neighbourLabels) createDatabaseRenderers(labelStore, null);

		updateRenderThemeFuture(false);
	}

	private void createDatabaseRenderers(LabelStore store, RenderThemeFuture labelTheme) {
		Map<String, DatabaseRenderer> renderers = new HashMap<String, DatabaseRenderer>();
		renderers.put("std", new DatabaseRenderer(multiMapDataStore, mapsforgeHandler.getGraphicFactory(), tileCache,
				store, renderLabels, cacheLabels, null));
		if (hillsRenderConfig != null)
			renderers.put("hs", new DatabaseRenderer(multiMapDataStore, mapsforgeHandler.getGraphicFactory(), tileCache,
					store, renderLabels, cacheLabels, hillsRenderConfig));
		rendering = new Rendering(renderers, labelTheme);
	}

	protected XmlRenderThemeMenuCallback menuCallBack = new XmlRenderThemeMenuCallback() {
		@Override
		public Set<String> getCategories(XmlRenderThemeStyleMenu styleMenu) {
			// Get available styles
			List<String> styleIds = new ArrayList<>();
			Map<String, XmlRenderThemeStyleLayer> layers = styleMenu.getLayers();
			for (Map.Entry<String, XmlRenderThemeStyleLayer> item : layers.entrySet()) {
				if (item.getValue().isVisible()) styleIds.add(item.getKey());
			}

			// Show styles
			logger.info("------------------ THEME STYLES ------------------");
			String defaultStyle = styleMenu.getDefaultValue();
			String themeStyle = themeFileStyle == null ? defaultStyle : themeFileStyle;
			int maxlen = 0;
			for (String id : styleIds) {
				int strlen = id.length();
				if (strlen > maxlen) maxlen = strlen;
			}
			String format = "%-" + maxlen + "s";
			logger.info("Default   : " + defaultStyle);
			themeStyleCheck = -1;
			for (String id : styleIds) {
				logger.info("Available : " + String.format(format, id)
					+ " --> " + styleMenu.getLayer(id).getTitle(language));
				if (id.equals(themeStyle)) themeStyleCheck = 1;
			}
			if (themeStyleCheck < 0) return null;
			logger.info("Used      : " + themeStyle);

			// Show overlays
			logger.info("----------------- THEME OVERLAYS -----------------");
			XmlRenderThemeStyleLayer baseLayer = styleMenu.getLayer(themeStyle);
			List<XmlRenderThemeStyleLayer> overlays = baseLayer.getOverlays();
			maxlen = 0;
			for (XmlRenderThemeStyleLayer overlay : overlays) {
				int strlen = overlay.getId().length();
				if (strlen > maxlen) maxlen = strlen;
			}
			format = "%-" + maxlen + "s";
			List<String> themeFileOverlays = mapsforgeTaskConfig.getThemeFileOverlays();
			Set<String> result = baseLayer.getCategories();
			for (XmlRenderThemeStyleLayer overlay : overlays) {
				String id = overlay.getId();
				boolean enabled = themeFileOverlays == null ? overlay.isEnabled() : themeFileOverlays.contains(id);
				logger.info((enabled ? "Enabled " : "Disabled") + "  : " + String.format(format, id)
					+ " --> " + overlay.getTitle(language));
				if (enabled) result.addAll(overlay.getCategories());
			}
			return result;
		}
	};

	protected boolean updateRenderThemeFuture(boolean update) throws Exception {

		ArrayList<XmlRenderTheme> internalRenderThemes = new ArrayList<XmlRenderTheme>();
		internalRenderThemes.addAll(new ArrayList<XmlRenderTheme>(EnumSet.allOf(MapsforgeSrvThemes.class)));	// Server's internal render themes
		internalRenderThemes.addAll(new ArrayList<XmlRenderTheme>(EnumSet.allOf(MapsforgeThemes.class)));		// Mapsforge internal render themes

		boolean internalRenderTheme = false;
		xmlRenderTheme = null;
		for (XmlRenderTheme enumItem : internalRenderThemes) {
			if (enumItem.toString().equals(themeFile.getPath())) {
				xmlRenderTheme = enumItem;
				internalRenderTheme = true;
				break;
			};
		};

		if (update && internalRenderTheme) return false;

		if (xmlRenderTheme == null) {
			try {
				xmlRenderTheme = new ExternalRenderTheme(themeFile);
			} catch (Exception e) {
				logger.error("Defined theme file '" + themeFile + "' does not exist or cannot be read: Task '" + taskName + "' disabled");
				taskEnabled = false;
				return false;
			}
		}

		InputStream inputStream = xmlRenderTheme.getRenderThemeAsStream();
		byte[] renderThemeBytes = inputStream.readAllBytes();
		String renderThemeString = new String(renderThemeBytes, StandardCharsets.UTF_8);
		inputStream.close();

		// Let render theme's built-in hillshading zoom levels override
		Pattern hsPattern = Pattern.compile("(.*)(<hillshading\\s.*?>)(.*)",Pattern.DOTALL);
		Matcher hsMatcher = hsPattern.matcher(renderThemeString);
		if (hsMatcher.matches()) {
			// Built-in hillshading found
			String hsProperty = hsMatcher.group(2);
			Pattern p = Pattern.compile(".*zoom-min=\"(\\d+)\".*");
			Matcher m = p.matcher(hsProperty);
			int hsZoomMin = m.matches() ? Integer.parseInt(m.group(1)) : MapsforgeTaskConfig.DEFAULT_HILLSHADING_ZOOM_MIN;
			if (mapsforgeTaskConfig.getHillShadingZoomMin() != null) hsZoomMin = mapsforgeTaskConfig.getHillShadingZoomMin();
			p = Pattern.compile(".*zoom-max=\"(\\d+)\".*");
			m = p.matcher(hsProperty);
			int hsZoomMax = m.matches() ? Integer.parseInt(m.group(1)) : MapsforgeTaskConfig.DEFAULT_HILLSHADING_ZOOM_MAX;
			if (mapsforgeTaskConfig.getHillShadingZoomMax() != null) hsZoomMax = mapsforgeTaskConfig.getHillShadingZoomMax();
			hsProperty = "<hillshading zoom-min=\"" + hsZoomMin + "\" zoom-max=\"" + hsZoomMax + "\" magnitude=\"128\" />";
			renderThemeString = hsMatcher.group(1) + hsProperty + hsMatcher.group(3);
			renderThemeBytes = renderThemeString.getBytes(StandardCharsets.UTF_8);
			String relativePathPrefix = xmlRenderTheme.getRelativePathPrefix();
			inputStream = new ByteArrayInputStream(renderThemeBytes);
			xmlRenderTheme = new StreamRenderTheme(relativePathPrefix, inputStream);
			inputStream.close();
		}

		if (update) logger.info("################ UPDATING TASK '" + taskName + "' ################");

		// Stop running thread
		if (renderThemeFuture != null) renderThemeFuture.cancel(true);
		String threadName = "RenderThemeFuture-" + taskName;
		for (Thread thread : Thread.getAllStackTraces().keySet()) {
			if (thread.getName().equals(threadName)) {
				thread.interrupt();
				logger.debug("Thread '" + threadName + "' successfully stopped.");
			}
		}

		// Start new thread, parse styles and overlays
		themeStyleCheck = 0;		// Theme has no style, value may get updated in menuCallBack
		taskEnabled = true;
		xmlRenderTheme.setMenuCallback(menuCallBack);
		renderThemeFuture = new RenderThemeFuture(mapsforgeHandler.getGraphicFactory(), xmlRenderTheme, displayModel);
		new Thread(null,renderThemeFuture,threadName).start();

		// Wait until renderThemeFuture becomes ready
		try {
 		    /* RenderTheme theme = */ renderThemeFuture.get();
		} catch (InterruptedException | ExecutionException e) {
		    logger.error("Render theme exception: " + e.getMessage());
		    taskEnabled = false;
		}

		if (themeStyleCheck < 0 || (themeStyleCheck == 0 && themeFileStyle != null)) {
			logger.error("Defined style '" + themeFileStyle + "' not available: Task '" + taskName + "' disabled");
			taskEnabled = false;
		}

		logger.info("--------------------------------------------------");
		if (!taskEnabled) {
			renderThemeFuture.cancel(true);	// Thread cancelled due to error
			renderThemeFuture = null;
		} else if (neighbourLabels) {
			// Text scaling is taken from the config here; a per-request "textScale" does not reach the labels.
			// The theme is read once, so the label store and the snapshot cannot get two different ones.
			RenderThemeFuture theme = renderThemeFuture;
			createDatabaseRenderers(new NeighbourLabelStore(multiMapDataStore, theme, 1.0f,
					displayModel, mapsforgeHandler.getGraphicFactory()), theme);
		}
		return taskEnabled;
}

	protected void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws Exception {
		String path = request.getPathInfo();
		String engine = "std";

		if (!taskEnabled) {
			logger.error("Task " + taskName + " disabled. Invalid tile request: " + path);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}

		int x, y, z;
		String ext = MapsforgeConfig.TILE_EXTENSION;
		Matcher m = requestPathPattern.matcher(path);
		if (m.matches()) {
			x = Integer.parseInt(m.group(2));
			y = Integer.parseInt(m.group(3));
			z = Integer.parseInt(m.group(1));
			if (m.group(4) != null) ext = m.group(4);
		} else {
			logger.error("Invalid tile request: " + path);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		if (x < 0 || x >= (1 << z)) {
			logger.error("Tile number x=" + x + " out of range!");
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		if (y < 0 || y >= (1 << z)) {
			logger.error("Tile number y=" + y + " out of range!");
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}

		float requestedTextScale = 1.0f; // Original text scaling comes from config value
		try {
			String tmp = request.getParameter("textScale");
//			Override text scaling from config value by text scaling from HTTP request
//			Final text scaling = textScale * requestedTextScale
			if (tmp != null) requestedTextScale = Float.parseFloat(tmp) / DisplayModel.textScale;
		} catch (Exception e) {
			throw new ServletException("Failed to parse \"textScale\" property: " + e.getMessage(), e);
		}

		boolean requestedTransparent = MapsforgeConfig.DEFAULT_TRANSPARENT;
		try {
			String tmp = request.getParameter("transparent");
			if (tmp != null) requestedTransparent = Boolean.parseBoolean(tmp);
		} catch (Exception e) {
			throw new ServletException("Failed to parse \"transparent\" property: " + e.getMessage(), e);
		}

		int requestedTileRenderSize = MapsforgeConfig.DEFAULT_TILE_RENDERSIZE;
		try {
			String tmp = request.getParameter("tileRenderSize");
			if (tmp != null) requestedTileRenderSize = Integer.parseInt(tmp);
		} catch (Exception e) {
			throw new ServletException("Failed to parse \"tileRenderSize\" property: " + e.getMessage(), e);
		}
		requestedTileRenderSize = (int) Math.round(requestedTileRenderSize * mapsforgeTaskConfig.getTileScale());

		TileBitmap tileBitmap = null;
		Tile tile = new Tile(x, y, (byte) z, requestedTileRenderSize);
		if (multiMapDataStore.supportsTile(tile)) {
			boolean enable_hs = true;
			try {
				String tmp = request.getParameter("hillshading");
				if (tmp != null) enable_hs = Integer.parseInt(tmp) != 0;
			} catch (Exception e) {
				throw new ServletException("Failed to parse \"hillshading\" property: " + e.getMessage(), e);
			}
			if (hillsRenderConfig != null && enable_hs) engine = "hs";

			Rendering current = rendering;
			RenderThemeFuture theme = current.labelTheme != null ? current.labelTheme : renderThemeFuture;
			RendererJob job = new RendererJob(tile, multiMapDataStore, theme, displayModel,
				requestedTextScale, requestedTransparent, false);

//Synchronizing render jobs has no visible effect -> disabled
//			synchronized (this) {
			tileBitmap = current.renderers.get(engine).executeJob(job);
			if (!hillShadingOverlay) tileCache.put(job, null);
//			}
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
					newPixelArray[pixelCount] = colorLookupTable[pixelValue & 0xff];
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
				String redirecturl = outOfRangeTms.replace("{x}", x + "").replace("{y}", y + "").replace("{z}", z + "");
				response.setHeader("Location", redirecturl);
				response.flushBuffer();
				logger.info("out-of-range redirect '" + redirecturl + "'");
				return;
			} else {
				image = MapsforgeConfig.BI_NOCONTENT;
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
		if (mapsforgeConfig.getCacheControl() > 0) {
			response.addHeader("Cache-Control", "public, max-age=" + mapsforgeConfig.getCacheControl());
		}
		response.setContentType("image/" + ext);
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

	// Enumeration of all tile server's internal rendering themes
	// Using StreamRenderThemes throws exception when calling "updateRenderThemeFuture" within "handle"
	private enum MapsforgeSrvThemes implements XmlRenderTheme {
		HillShadingOverlay("/assets/mapsforgesrv/hillshading.xml");
		private XmlRenderThemeMenuCallback menuCallback;
		private final String path;
		MapsforgeSrvThemes(String path) {
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
