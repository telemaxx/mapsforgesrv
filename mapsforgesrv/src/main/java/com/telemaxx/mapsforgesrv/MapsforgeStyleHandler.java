package com.telemaxx.mapsforgesrv;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleLayer;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu;
import org.mapsforge.map.rendertheme.XmlThemeResourceProvider;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MapsforgeStyleHandler {
	
	final static Logger logger = LoggerFactory.getLogger(MapsforgeStyleHandler.class);
	
	protected final DisplayModel displayModel;

	protected HillsRenderConfig hillsRenderConfig = null;

	protected XmlRenderTheme xmlRenderTheme;
	protected RenderThemeFuture renderThemeFuture;


	protected XmlRenderThemeStyleMenu renderThemeStyleMenu;
	protected final TileBasedLabelStore labelStore;
	protected final TileCache tileCache;


	protected final boolean renderLabels;
	protected final boolean cacheLabels;

	protected final File themeFile;
	protected final String themeFileStyle;
	
	private ShadingAlgorithm shadingAlgorithm = null;

	private int[] colorLookupTable = null;
	private String name;



	private MapsforgeHandler mapsforgeHandler;
	
	public MapsforgeStyleHandler(MapsforgeHandler mapsforgeHandler, MapsforgeStyleConfig mapsforgeStyleConfig, String name) throws Exception {
		
		this.name = name;
		this.mapsforgeHandler = mapsforgeHandler;
		MapsforgeConfig mapsforgeConfig = mapsforgeHandler.getMapsforgeConfig();

		if (mapsforgeHandler.isHillShadingOverlay()) {
			logger.info("No map -> hillshading overlay with alpha transparency only!");
			tileCache = null;
			labelStore = null;
			renderLabels = false;
			cacheLabels = false;
			colorLookupTable = new int[256];
			int pixelValue,gray,dist,alpha;
			int range = (int)(30*mapsforgeConfig.getDefaultStyle().getHillShadingMagnitude()); // gray value range
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
			int blackValue = mapsforgeStyleConfig.getBlackValue();
			double gammaValue = mapsforgeStyleConfig.getGammaValue();
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

		DisplayModel.setDeviceScaleFactor(mapsforgeStyleConfig.getDeviceScale());
		DisplayModel.textScale = mapsforgeStyleConfig.getTextScale();
		DisplayModel.symbolScale = mapsforgeStyleConfig.getSymbolScale();
		DisplayModel.lineScale = mapsforgeStyleConfig.getLineScale();
		displayModel = new DisplayModel();
		displayModel.setUserScaleFactor(mapsforgeStyleConfig.getUserScale());		

		if (mapsforgeStyleConfig.getHillShadingAlgorithm() != null && mapsforgeConfig.getDemFolder() != null) { // hillshading
			if (mapsforgeStyleConfig.getHillShadingAlgorithm().equals("simple")) {
				shadingAlgorithm = new SimpleShadingAlgorithm(mapsforgeStyleConfig.getHillShadingArguments()[0],
						mapsforgeStyleConfig.getHillShadingArguments()[1]);
			} else {
				shadingAlgorithm = new DiffuseLightShadingAlgorithm(
						(float) mapsforgeStyleConfig.getHillShadingArguments()[0]);
			}
			DemFolderFS demFolder = new DemFolderFS(mapsforgeConfig.getDemFolder());
			MemoryCachingHgtReaderTileSource tileSource = new MemoryCachingHgtReaderTileSource(
					demFolder, shadingAlgorithm, mapsforgeHandler.getGraphicFactory());
			tileSource.setEnableInterpolationOverlap(mapsforgeConfig.HILLSHADINGENABLEINTERPOLATIONOVERLAP);
			hillsRenderConfig = new HillsRenderConfig(tileSource);
			hillsRenderConfig.setMaginuteScaleFactor((float) mapsforgeStyleConfig.getHillShadingMagnitude());
			hillsRenderConfig.indexOnThread();
		}

		if (mapsforgeConfig.getRendererName().equals("direct")) {
			if (hillsRenderConfig != null)
				mapsforgeHandler.getDirectRenderer().put("hs"+name,
						new DirectRenderer(mapsforgeHandler.getMultiMapDataStore(), mapsforgeHandler.getGraphicFactory(), renderLabels, hillsRenderConfig));
			mapsforgeHandler.getDirectRenderer().put("std"+name, new DirectRenderer(mapsforgeHandler.getMultiMapDataStore(), mapsforgeHandler.getGraphicFactory(), renderLabels, null));
		} else {
			if (hillsRenderConfig != null)
				mapsforgeHandler.getDatabaseRenderer().put("hs"+name, new DatabaseRenderer(mapsforgeHandler.getMultiMapDataStore(), mapsforgeHandler.getGraphicFactory(), tileCache,
						labelStore, renderLabels, cacheLabels, hillsRenderConfig));
			mapsforgeHandler.getDatabaseRenderer().put("std"+name, new DatabaseRenderer(mapsforgeHandler.getMultiMapDataStore(), mapsforgeHandler.getGraphicFactory(), tileCache,
					labelStore, renderLabels, cacheLabels, null));
		}

		XmlRenderThemeMenuCallback callBack = new XmlRenderThemeMenuCallback() {

			@Override
			public Set<String> getCategories(XmlRenderThemeStyleMenu styleMenu) {
				String id = null;
				renderThemeStyleMenu = styleMenu;
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
					String[] themeFileOverlays = mapsforgeStyleConfig.getThemeFileOverlays();
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
		
		themeFile = mapsforgeStyleConfig.getThemeFile();
		themeFileStyle = mapsforgeStyleConfig.getThemeFileStyle();

		if (mapsforgeHandler.isHillShadingOverlay()) {
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
		logger.info("################ THEME '"+name+"' STYLES ################"); //$NON-NLS-1$
		logger.info("Default Style: " + mapStyleParser.getDefaultStyle()); //$NON-NLS-1$
		for (final Style style : styles) {
			logger.info("Stylename to use for '-s' option: " + "'" + style.getXmlLayer() + "'" + " --> " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					+ style.getName(Locale.getDefault().getLanguage()));

			if (style.getXmlLayer().equals(themeFileStyle)) {
				selectedStyleExists = true;
			}
		}
		if (!selectedStyleExists && themeFileStyle != null) {
			logger.error("The defined style '"+themeFileStyle+"' not found: exiting"); //$NON-NLS-1$
			System.exit(2);
		}
		if (selectedStyleExists && themeFileStyle != null) {
			logger.info("The defined style '"+themeFileStyle+"' found"); //$NON-NLS-1$
		}
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
