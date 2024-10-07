package com.telemaxx.mapsforgesrv;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapsforgeTaskConfig extends PropertiesParser{

	private ArrayList<File> mapFiles = null;
	private boolean appendWorldMap;
	private String preferredLanguage = null;
	private File demFolder = null;
	private File themeFile = null;
	private String taskName = null;
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
	private String checkSum = null;

	private final static Logger logger = LoggerFactory.getLogger(MapsforgeTaskConfig.class);

	public MapsforgeTaskConfig(String taskName, File taskFile) throws Exception {
		this.taskName = taskName;
		checkSum = readConfig(taskFile);
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

	private void parseMapFiles() throws Exception {
		mapFiles = new ArrayList<File>();
		String msgHeader = parsePadMsg("Map file(s)"); //$NON-NLS-1$
		String mapFilePathsString = retrieveConfigValue("mapfiles"); //$NON-NLS-1$
		if (mapFilePathsString != null) {
			String[] mapFilePaths = mapFilePathsString.trim().split(","); //$NON-NLS-1$ //$NON-NLS-2$
			List<File> mapsErr = new ArrayList<File>();
			for (String path : mapFilePaths) {
				File file = new File(path.trim());
				if (file.exists()) {
					if (file.isFile()) {
						mapFiles.add(file);
					} else if (file.isDirectory()) {
						for (File mapfile : file.listFiles(new FilenameFilter() {
							@Override
							public boolean accept(File dir, String name) {
								return name.endsWith(".map");
							}
						})) {
							mapFiles.add(mapfile);
						}
					}
				}
			}
			mapFiles.forEach(mapFile -> {
				if (!mapFile.isFile())
					mapsErr.add(mapFile);
			});
			String mapFilesString;
			if (mapsErr.size() > 0) {
				mapFiles.removeAll(mapsErr);
				mapFilesString = mapFiles.stream().map(File::getPath).collect(Collectors.joining(","));
				String cnxNotAuth = "{" + mapsErr.stream().map(File::getPath).collect(Collectors.joining(",")) + "} not existing"; //$NON-NLS-2$ //$NON-NLS-3$
				if (mapFilePaths.length == 0) {
					parseError(msgHeader, cnxNotAuth, "{" + mapFilesString + "}");
				} else {
					logger.info(msgHeader + ": defined [{" + mapFilesString + "}] - warn " + cnxNotAuth); //$NON-NLS-1$
				}
			} else {
				mapFilesString = mapFiles.stream().map(File::getPath).collect(Collectors.joining(","));
				logger.info(msgHeader + ": defined [{" + mapFilesString + "}]"); //$NON-NLS-1$
			}
		}
		if (mapFiles.size() == 0) configProperties.setProperty("worldmap", "");
	}

	private void initConfig() throws Exception {
		logger.info("################ TASK '"+taskName+"' PROPERTIES ################");
		parseMapFiles();
		appendWorldMap = parseHasOption("worldmap", "Append built-in world map");
		preferredLanguage = parseString(null, "language", null, "Preferred map language"); //$NON-NLS-1$ //$NON-NLS-2$
		parseThemeFile();
		themeFileStyle = parseString(null, "style", null, "Theme style"); //$NON-NLS-1$ //$NON-NLS-2$
		parseThemeOverlays();
		demFolder = parseFile("demfolder", FOLDER, true, "DEM", "undefined");
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
						hillShadingArguments[0] = DEFAULT_HILLSHADING_DIFFUSELIGHT;
					}
					logger.info(msgHeader + ": defined [" + hillShadingAlgorithm + "(" + hillShadingArguments[0] + ")]"); //$NON-NLS-1$
				}
			} else {
				parseError(msgHeader, "'" + hillShadingOption + "' invalid", "undefined");
			}
		}
	}

	public List<File> getMapFiles() {
		return this.mapFiles;
	}

	public String getPreferredLanguage() {
		return this.preferredLanguage;
	}

	public boolean getAppendWorldMap() {
		return this.appendWorldMap;
	}

	public File getDemFolder() {
		return this.demFolder;
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

	public String getCheckSum() {
		return this.checkSum;
	}

}
