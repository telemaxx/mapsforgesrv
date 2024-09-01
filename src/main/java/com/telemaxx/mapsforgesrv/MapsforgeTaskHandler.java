package com.telemaxx.mapsforgesrv;

import java.io.File;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import org.mapsforge.map.layer.renderer.DirectRenderer;
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

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder;

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
	
	private boolean hillShadingOverlay = false;
	private HillsRenderConfig hillsRenderConfig = null;
	private XmlRenderTheme xmlRenderTheme;
	private RenderThemeFuture renderThemeFuture;
	private ShadingAlgorithm shadingAlgorithm = null;
	private int[] colorLookupTable = null;
	private String name;
	private Map<String, DatabaseRenderer> databaseRenderer = null;
	private Map<String, DirectRenderer> directRenderer = null;

	private MapsforgeHandler mapsforgeHandler;
	
	public MapsforgeTaskHandler(MapsforgeHandler mapsforgeHandler, MapsforgeTaskConfig mapsforgeTaskConfig, String name) throws Exception {
		
		logger.info("################ STARTING TASK '"+name+"' ################"); //$NON-NLS-1$
		
		this.name = name;
		this.mapsforgeHandler = mapsforgeHandler;
		MapsforgeConfig mapsforgeConfig = mapsforgeHandler.getMapsforgeConfig();
		
		logger.info("################### MAPS INFO ###################");
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
			int range = (int)(30*mapsforgeConfig.getDefaultConfig().getHillShadingMagnitude()); // gray value range
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

		if (mapsforgeTaskConfig.getRendererName().equals("direct")) {
			directRenderer = new HashMap<String, DirectRenderer>();
			if (hillsRenderConfig != null)
				directRenderer.put("hs",
						new DirectRenderer(multiMapDataStore, mapsforgeHandler.getGraphicFactory(), renderLabels, hillsRenderConfig));
			directRenderer.put("std", new DirectRenderer(multiMapDataStore, mapsforgeHandler.getGraphicFactory(), renderLabels, null));
		} else {
			databaseRenderer = new HashMap<String, DatabaseRenderer>();
			if (hillsRenderConfig != null)
				databaseRenderer.put("hs", new DatabaseRenderer(multiMapDataStore, mapsforgeHandler.getGraphicFactory(), tileCache,
						labelStore, renderLabels, cacheLabels, hillsRenderConfig));
			databaseRenderer.put("std", new DatabaseRenderer(multiMapDataStore, mapsforgeHandler.getGraphicFactory(), tileCache,
					labelStore, renderLabels, cacheLabels, null));
		}

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
				logger.info("################# THEME '"+name+"' OVERLAYS #################"); //$NON-NLS-1$
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
			try {
				xmlRenderTheme = new ExternalRenderTheme(themeFile, callBack);
			} catch (Exception e) {
				logger.error("The defined theme file '"+themeFile+"' does not exist or cannot be read: exiting"); //$NON-NLS-1$
				System.exit(2);
			}
			showStyleNames();
		}

		updateRenderThemeFuture();
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

	/**
	 * displaying the containing styles inside theme xml if a style is given(!=null)
	 * but this style did not exist the program terminates
	 */
	protected void showStyleNames() {
		final MapsforgeStyleParser mapStyleParser = new MapsforgeStyleParser();
		final List<Style> styles = mapStyleParser.readXML(themeFile.getAbsolutePath());
		Boolean selectedStyleExists = false;
		logger.info("################ THEME '"+name+"' STYLES ################"); //$NON-NLS-1$
		logger.info("Default style   : " + mapStyleParser.getDefaultStyle()); //$NON-NLS-1$
		for (final Style style : styles) {
			logger.info("Available style : " + style.getXmlLayer() + " --> " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					+ style.getName(Locale.getDefault().getLanguage()));


			if (style.getXmlLayer().equals(themeFileStyle)) {
				selectedStyleExists = true;
			}
		}
		if (!selectedStyleExists && themeFileStyle != null) {
			logger.error("Defined style '"+themeFileStyle+"' not available: exiting"); //$NON-NLS-1$
			System.exit(2);
		}
		if (selectedStyleExists && themeFileStyle != null) {
			logger.info("Defined style '"+themeFileStyle+"' used"); //$NON-NLS-1$
		}
	}

	public MultiMapDataStore getMultiMapDataStore() {
		return multiMapDataStore;
	}
	
	public boolean getHillShadingOverlay() {
		return hillShadingOverlay;
	}
	
	public HillsRenderConfig getHillsRenderConfig() {
		return hillsRenderConfig;
	}
	
	public DisplayModel getDisplayModel() {
		return displayModel;
	}

	public RenderThemeFuture getRenderThemeFuture() {
		return renderThemeFuture;
	}
	
	public TileCache getTileCache() {
		return tileCache;
	}
	
	public String getName() {
		return name;
	}

	public int[] getColorLookupTable() {
		return colorLookupTable;
	}
	
	public Map<String, DatabaseRenderer> getDatabaseRenderer() {
		return databaseRenderer;
	}

	public Map<String, DirectRenderer> getDirectRenderer() {
		return directRenderer;
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
