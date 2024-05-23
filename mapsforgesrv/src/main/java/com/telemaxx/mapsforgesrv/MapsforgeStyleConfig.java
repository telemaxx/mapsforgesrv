package com.telemaxx.mapsforgesrv;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapsforgeStyleConfig extends PropertiesParser{
	
	private File themeFile = null;
	private String styleName = null;
	private String[] themeFileOverlays = null;
	private String themeFileStyle = null;
	private float deviceScale;
	private float userScale;
	private float textScale;
	private float symbolScale;
	private float lineScale;
	protected double[] hillShadingArguments;
	protected String hillShadingAlgorithm = null;
	protected double hillShadingMagnitude;
	private int blackValue;
	private double gammaValue;
	
	private final static Logger logger = LoggerFactory.getLogger(MapsforgeStyleConfig.class);
	
	public MapsforgeStyleConfig(String styleName, File styleFile) throws Exception {
		this.styleName = styleName;
		readConfig(styleFile);
		initConfig();
	}
	
	private void parseThemeFile() throws Exception {
		String configValue = "themefile";
		String configString = retrieveConfigValue(configValue);
		String msgHeader = "Theme";
		if (configString == null) {
			themeFile = new File("OSMARENDER");
			logger.info(parsePadMsg(msgHeader + " " + FILE) + ": default [OSMARENDER]"); //$NON-NLS-1$
		} else if (configString.trim().equals("OSMARENDER")) {
			themeFile = new File("OSMARENDER");
			logger.info(parsePadMsg(msgHeader + " " + FILE) + ": defined [OSMARENDER]"); //$NON-NLS-1$
		} else if (configString.trim().equals("DEFAULT")) {
			themeFile = new File("DEFAULT");
			logger.info(parsePadMsg(msgHeader + " " + FILE) + ": defined [DEFAULT]"); //$NON-NLS-1$
		} else {
			themeFile = parseFile(configValue, FILE, false, msgHeader, "OSMARENDER");
		}
	}
	
	private void parseThemeOverlays() throws Exception {
		String msgHeader = parsePadMsg("Theme overlay(s)"); //$NON-NLS-1$
		String optionValue = retrieveConfigValue("overlays"); //$NON-NLS-1$
		if (optionValue != null)
			themeFileOverlays = StringUtils.stripAll(optionValue.trim().split(",")); //$NON-NLS-1$
		if (themeFileOverlays != null && themeFileOverlays.length != 0) {
			logger.info(msgHeader + ": defined [{" + String.join(",", themeFileOverlays) + "}]"); //$NON-NLS-1$
		} else {
			logger.info(msgHeader + ": default [undefined]"); //$NON-NLS-1$
		}
	}

	
	
	private void initConfig() throws Exception {
		logger.info("################## STYLE '"+styleName+"' CONFIG ##################");
		themeFileStyle = parseString(null, "style", null, "Theme style"); //$NON-NLS-1$ //$NON-NLS-2$
		parseThemeFile();
		parseThemeOverlays();
		parseHillShading();
		hillShadingMagnitude = (double) parseNumber(DEFAULT_HILLSHADING_MAGNITUDE, "hillshading-magnitude", 0., 4., "Hillshading magnitude",false); //$NON-NLS-1$ //$NON-NLS-2$
		blackValue = (int) parseNumber(DEFAULT_BLACK, "contrast-stretch", 0, 254, "Contrast stretch",false); //$NON-NLS-1$ //$NON-NLS-2$
		gammaValue = (double) parseNumber(DEFAULT_GAMMA, "gamma-correction", 0., null, "Gamma correction",true); //$NON-NLS-1$ //$NON-NLS-2$
		deviceScale = (float) parseNumber(DEFAULT_DEVICESCALE, "device-scale", 0., null, "Device scale factor",true); //$NON-NLS-1$ //$NON-NLS-2$
		userScale = (float) parseNumber(DEFAULT_USERSCALE, "user-scale", 0., null, "User scale factor",true); //$NON-NLS-1$ //$NON-NLS-2$
		textScale = (float) parseNumber(DEFAULT_TEXTSCALE, "text-scale", 0., null, "Text scale factor",true); //$NON-NLS-1$ //$NON-NLS-2$
		symbolScale = (float) parseNumber(DEFAULT_SYMBOLSCALE, "symbol-scale", 0., null, "Symbol scale factor",true); //$NON-NLS-1$ //$NON-NLS-2$
		lineScale = (float) parseNumber(DEFAULT_LINESCALE, "line-scale", 0., null, "Line scale factor",true); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private void parseHillShading() throws Exception {
		String msgHeader = parsePadMsg("HillShading algorithm");
		hillShadingArguments = null;
		String hillShadingOption = retrieveConfigValue("hillshading-algorithm"); //$NON-NLS-1$
		if (hillShadingOption != null) {
			hillShadingOption = hillShadingOption.trim();
			Pattern P = Pattern.compile("(simple)(?:\\((\\d+\\.?\\d*|\\d*\\.?\\d+),(\\d+\\.?\\d*|\\d*\\.?\\d+)\\))?|(diffuselight)(?:\\((\\d+\\.?\\d*|\\d*\\.?\\d+)\\))?");
			Matcher m = P.matcher(hillShadingOption);
			if (m.matches()) {
				if (m.group(1) != null) {
					hillShadingAlgorithm = new String(m.group(1)); // ShadingAlgorithm = simple
					hillShadingArguments = new double[2];
					if (m.group(2) != null) {
						hillShadingArguments[0] = Double.parseDouble(m.group(2));
						hillShadingArguments[1] = Double.parseDouble(m.group(3));
					} else { // default values
						hillShadingArguments[0] = DEFAULT_HILLSHADING_SIMPLE[0];
						hillShadingArguments[1] = DEFAULT_HILLSHADING_SIMPLE[1];
					}
					logger.info(msgHeader + ": defined [" + hillShadingAlgorithm + "(" + hillShadingArguments[0] + "," //$NON-NLS-3$
							+ hillShadingArguments[1] + ")]");
				} else {
					hillShadingAlgorithm = new String(m.group(4)); // ShadingAlgorithm = diffuselight
					hillShadingArguments = new double[1];
					if (m.group(5) != null) {
						hillShadingArguments[0] = Double.parseDouble(m.group(5));
					} else { // default value
						hillShadingArguments[0] = DEFAULT_HILLSHADING_DIFDUSELIGHT;
					}
					logger.info(msgHeader + ": defined [" + hillShadingAlgorithm + "(" + hillShadingArguments[0] + ")]"); //$NON-NLS-1$
				}
			} else {
				parseError(msgHeader, "'" + hillShadingOption + "' invalid", "undefined");
			}
		}
	}
	
	public double getHillShadingMagnitude() {
		return this.hillShadingMagnitude;
	}

	public String getHillShadingAlgorithm() {
		return this.hillShadingAlgorithm;
	}

	public double[] getHillShadingArguments() {
		return this.hillShadingArguments;
	}
	
	public String getThemeFileStyle() {
		return this.themeFileStyle;
	}

	public String[] getThemeFileOverlays() {
		return this.themeFileOverlays;
	}
	
	public File getThemeFile() {
		return this.themeFile;
	}
	
	public float getDeviceScale() {
		return this.deviceScale;
	}
	
	public float getUserScale() {
		return this.userScale;
	}
		
	public float getTextScale() {
		return this.textScale;
	}
	
	public float getSymbolScale() {
		return this.symbolScale;
	}

	public float getLineScale() {
		return this.lineScale;
	}

	public int getBlackValue() {
		return this.blackValue;
	}
	
	public double getGammaValue() {
		return this.gammaValue;
	}

}
