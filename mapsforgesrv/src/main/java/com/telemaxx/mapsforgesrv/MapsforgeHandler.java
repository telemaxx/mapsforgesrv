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
 * Inc., 51 Franklin Str, Fifth Floor, Boston, MA 02110, USA
 * 
 *******************************************************************************/

package com.telemaxx.mapsforgesrv;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.mapelements.MapElementContainer;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.awt.graphics.AwtTileBitmap;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.labels.TileBasedLabelStore;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.DirectRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.layer.hills.MemoryCachingHgtReaderTileSource;
import org.mapsforge.map.layer.hills.ShadingAlgorithm;
import org.mapsforge.map.layer.hills.SimpleShadingAlgorithm;
import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.HillsRenderConfig;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleLayer;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

// Java 11+
//import jakarta.servlet.ServletException;
//import jakarta.servlet.ServletOutputStream;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//Java 8
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("unused")
public class MapsforgeHandler extends AbstractHandler {

	private final TreeSet<String> KNOWN_PARAMETER_NAMES = new TreeSet<>(
			Arrays.asList(new String[] { "x", "y", "z", "textScale", "userScale", "transparent", "tileRenderSize" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

	protected final List<File> mapFiles;
	protected final File themeFile;
	protected final String themeFileStyle;
	protected final String[] themeFileOverlays;
	protected final MultiMapDataStore multiMapDataStore;
	protected final DisplayModel displayModel;
	protected HillsRenderConfig hillsRenderConfig = null;
	protected DatabaseRenderer databaseRenderer = null;
	protected DirectRenderer directRenderer = null;
	protected XmlRenderTheme xmlRenderTheme;
	protected RenderThemeFuture renderThemeFuture;
	protected XmlRenderThemeStyleMenu renderThemeStyleMenu;
	protected TileBasedLabelStore tileBasedLabelStore = new MyTileBasedLabelStore(1000);
	protected DummyCache labelInfoCache = new DummyCache();
	protected int blackValue;	

	private static final Pattern P = Pattern.compile("/(\\d+)/(-?\\d+)/(-?\\d+)\\.(.*)"); //$NON-NLS-1$
	private static final DateTimeFormatter FormatDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	public MapsforgeHandler(String rendererName, List<File> mapFiles, File themeFile, String themeFileStyle, String[] themeFileOverlays,
			String preferredLanguage, String hillShadingAlgorithm, double[] hillShadingArguments, double hillShadingMagnitude,
			File demFolder, int blackValue) throws FileNotFoundException {
		super();
		ImageIO.setUseCache(false);
		this.mapFiles = mapFiles;
		this.themeFile = themeFile;
		this.themeFileStyle = themeFileStyle;
		this.themeFileOverlays = themeFileOverlays;
		this.blackValue = blackValue;
		
		GraphicFactory graphicFactory = AwtGraphicFactory.INSTANCE;
		multiMapDataStore = new MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL);

		mapFiles.forEach(mapFile -> {
			MapFile map = new MapFile(mapFile, preferredLanguage);
			String[] mapLanguages = map.getMapLanguages();
			System.out.print ("Map file: " + mapFile + ", supported languages: ");
			if (mapLanguages != null) {
				System.out.println (String.join(",", mapLanguages));
			} else {
			    System.out.println ("-");
			}
			multiMapDataStore.addMapDataStore(map, true, true);
		});

		displayModel = new DisplayModel();

		if (hillShadingAlgorithm != null && demFolder != null) { // hillshading
			ShadingAlgorithm shadingAlgorithm = null;
			if (hillShadingAlgorithm.equals("simple")) {
				shadingAlgorithm = new SimpleShadingAlgorithm(hillShadingArguments[0], hillShadingArguments[1]);
			} else {
				shadingAlgorithm = new DiffuseLightShadingAlgorithm((float)hillShadingArguments[0]);
			}
			MemoryCachingHgtReaderTileSource tileSource = new MemoryCachingHgtReaderTileSource(demFolder, shadingAlgorithm, graphicFactory);
//			tileSource.setEnableInterpolationOverlap(true);		// More precise at tile edges but much slower, therefore commented out
			tileSource.setEnableInterpolationOverlap(false);	// Less precise at tile edges but much faster, therefore hard-coded
			hillsRenderConfig = new HillsRenderConfig(tileSource);
			hillsRenderConfig.setMaginuteScaleFactor ((float)hillShadingMagnitude);
			hillsRenderConfig.indexOnThread();
		}
		
		if (rendererName.equals("direct")) {
			directRenderer = new DirectRenderer(multiMapDataStore, graphicFactory, true, hillsRenderConfig);
		} else {
			databaseRenderer = new DatabaseRenderer(multiMapDataStore, graphicFactory, labelInfoCache, tileBasedLabelStore, true, true, hillsRenderConfig);
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
				for (XmlRenderThemeStyleLayer overlay : baseLayer.getOverlays()) {
					String overlayId = overlay.getId();
					boolean overlayEnabled = false;
					if (themeFileOverlays == null) {
						overlayEnabled = overlay.isEnabled();
					} else {
						for (int i = 0; i < themeFileOverlays.length; i++) {
							if (themeFileOverlays[i].equals(overlayId))
								overlayEnabled = true;
						}
					}
					System.out.println("Overlay id=\"" + overlayId + "\" enabled=\"" + Boolean.toString(overlayEnabled)
							+ "\" title=\"" + overlay.getTitle(preferredLanguage) + "\"");
										
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
	 * displaying the containing styles inside theme xml
	 * if a style is given(!=null) but this style did not exist the program terminates
	 */
	protected void showStyleNames() {
		final MapsforgeStyleParser mapStyleParser = new MapsforgeStyleParser();
		final List<Style> styles = mapStyleParser.readXML(themeFile.getAbsolutePath());
		Boolean selectedStyleExists = false;
		System.out.println("####### Infos about the selected themefile #######"); //$NON-NLS-1$
		System.out.println("Default Style: " + mapStyleParser.getDefaultStyle()); //$NON-NLS-1$
		for (final Style style : styles) {
			System.out.println("Stylename to use for \"-s\" option: " + "\"" + style.getXmlLayer() + "\"" + " --> " + style.getName(Locale.getDefault().getLanguage())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

			if(style.getXmlLayer().equals(themeFileStyle)) {
				selectedStyleExists = true;
			}
		}		
		System.out.println("####### Infos end ################################"); //$NON-NLS-1$
		if(!selectedStyleExists && themeFileStyle != null) {
			System.out.println("the given style not found, existing. given style: " + themeFileStyle); //$NON-NLS-1$
			System.exit(2);
		}
		if(selectedStyleExists && themeFileStyle != null) {
			System.out.println("the given style found: " + themeFileStyle); //$NON-NLS-1$
		}
	}
	
	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

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

		System.out.println(LocalDateTime.now().format(FormatDateTime) + " " + request); //$NON-NLS-1$
		
		String path = request.getPathInfo();
		int x, y, z;
		String ext = "png"; //$NON-NLS-1$
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
		if (x<0 || x>=(1<<z)) {
			System.out.println("ERROR: tile number x=" + x + " out of range!"); //$NON-NLS-1$
		    return;
		}
		if (y<0 || y>=(1<<z)) {
			System.out.println("ERROR: tile number y=" + y + " out of range!"); //$NON-NLS-1$
		    return;
		}
		float textScale = 1.0f;
		try {
			String tmp = request.getParameter("textScale"); //$NON-NLS-1$
			if (tmp != null) {
				textScale = Float.parseFloat(tmp);
			} else {
				textScale = 1.0f;
			}
		} catch (Exception e) {
			throw new ServletException("Failed to parse \"textScale\" property: " + e.getMessage(), e); //$NON-NLS-1$
		}

		float userScale = 1.0f;
		try {
			String tmp = request.getParameter("userScale"); //$NON-NLS-1$
			if (tmp != null) {
				userScale = Float.parseFloat(tmp);
			}
		} catch (Exception e) {
			throw new ServletException("Failed to parse \"userScale\" property: " + e.getMessage(), e); //$NON-NLS-1$
		}
		displayModel.setUserScaleFactor(userScale);

		boolean transparent = false;
		try {
			String tmp = request.getParameter("transparent"); //$NON-NLS-1$
			if (tmp != null) {
				transparent = Boolean.parseBoolean(tmp);
			}
		} catch (Exception e) {
			throw new ServletException("Failed to parse \"transparent\" property: " + e.getMessage(), e); //$NON-NLS-1$
		}

		int tileRenderSize = 256;
		try {
			String tmp = request.getParameter("tileRenderSize"); //$NON-NLS-1$
			if (tmp != null) {
				tileRenderSize = Integer.parseInt(tmp);
			}
		} catch (Exception e) {
			throw new ServletException("Failed to parse \"tileRenderSize\" property: " + e.getMessage(), e); //$NON-NLS-1$
		}

		RendererJob job;
		Bitmap tileBitmap;
		Tile tile = new Tile(x, y, (byte) z, tileRenderSize);
		job = new RendererJob(tile, multiMapDataStore, renderThemeFuture, displayModel, textScale, transparent, false);
		synchronized (this) {
			if (directRenderer != null) {
				tileBitmap = (AwtTileBitmap) directRenderer.executeJob(job);
			} else {
				tileBitmap = (AwtTileBitmap) databaseRenderer.executeJob(job);
				labelInfoCache.put(job, null);
			}
		}
		BufferedImage image = AwtGraphicFactory.getBitmap(tileBitmap);

		if (blackValue > 0) {		// contrast-stretching
			// DataBuffer created by Mapsforge renderer is of type DataBufferInt, i.e. one int value 0xaarrggbb per pixel 
			DataBufferInt dataBuffer = (DataBufferInt)image.getRaster().getDataBuffer();
			int[] pixelArray = dataBuffer.getData();
			int pixelCount = image.getWidth()*image.getHeight();
			int pixelValue,alphaValue,redValue,greenValue,blueValue;
			float stretchFactor = (float)255/(float)(255-blackValue);
			while (pixelCount-- > 0) {
				pixelValue = pixelArray[pixelCount];
				alphaValue = (pixelValue>>>24) & 0xff;
				redValue   = (pixelValue>>>16) & 0xff;
				greenValue = (pixelValue>>> 8) & 0xff;
				blueValue  =  pixelValue       & 0xff;
				redValue   = redValue   > blackValue ? Math.round((redValue  -blackValue)*stretchFactor) : 0;
				greenValue = greenValue > blackValue ? Math.round((greenValue-blackValue)*stretchFactor) : 0;
				blueValue  = blueValue  > blackValue ? Math.round((blueValue -blackValue)*stretchFactor) : 0;
				pixelValue = (((((alphaValue<<8)|redValue)<<8)|greenValue)<<8)|blueValue;
				pixelArray[pixelCount] = pixelValue;
			}
		}
		
		baseRequest.setHandled(true);
		response.setStatus(200);
		response.setContentType("image/" + ext); //$NON-NLS-1$
		ImageIO.write(image, ext, response.getOutputStream());
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