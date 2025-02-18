package com.telemaxx.mapsforgesrv;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Properties;

import org.mapsforge.map.layer.hills.AThreadedHillShading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PropertiesParser {

	protected Properties configProperties;

	/****************
	 * FIXED VALUES *
	 ****************/

	public final static String 		VERSION = "0.23.0.3"; // starting with eg 0.13, the mapsforge version //$NON-NLS-1$

	public final static String 		TILE_EXTENSION = "png"; //$NON-NLS-1$
	// false: use default value true: exit(1)
	protected static final String 	FILE = "file"; //$NON-NLS-1$
	protected static final String 	FOLDER = "folder"; //$NON-NLS-1$
	// mandatory config files & directory
	public final static String 		FILECONFIG_JETTY = "jetty.xml"; //$NON-NLS-1$
	public final static String 		FILECONFIG_JETTY_THREADPOOL = "jetty-threadpool.xml"; //$NON-NLS-1$
	public final static String 		FILECONFIG_JETTY_THREADPOOL_VR = "jetty-threadpool-virtual.xml"; //$NON-NLS-1$
	public final static String		FILECONFIG_SERVER = "server.properties"; //$NON-NLS-1$
	public final static String		DIRCONFIG_TASKS = "tasks"+System.getProperty("file.separator"); //$NON-NLS-1$

	// true:  More precise at tile edges but much slower / false: Less precise at tile edges but much faster
	public final static boolean 	HILLSHADING_INTERPOLATION_OVERLAP = true;

	/******************
	 * DEFAULT VALUES *
	 ******************/

	// URL.tileRenderSize
	public final static int 		DEFAULT_TILE_RENDERSIZE = 256;
	// URL.transparent
	public final static boolean 	DEFAULT_TRANSPARENT = false;

	// MapsforgeConfig.cacheControl
	protected final static long 	DEFAULT_CACHECONTROL = 0;

	// MapsforgeTaskConfig.gammaValue
	protected final static double 	DEFAULT_GAMMA = 1.;
	// MapsforgeTaskConfig.blackValue
	protected final static int 		DEFAULT_BLACK = 0;
	// MapsforgeTaskConfig.deviceScale
	protected final static float	DEFAULT_DEVICESCALE = 1.0f;
	// MapsforgeTaskConfig.userScale
	protected final static float	DEFAULT_USERSCALE = 1.0f;
	// MapsforgeTaskConfig.textScale
	protected final static float	DEFAULT_TEXTSCALE = 1.0f;
	// MapsforgeTaskConfig.symbolScale
	protected final static float	DEFAULT_SYMBOLSCALE = 1.0f;
	// MapsforgeTaskConfig.lineScale
	protected final static float	DEFAULT_LINESCALE = 1.0f;
	// MapsforgeTaskConfig.hillShadingArguments
	public final static double[] 	DEFAULT_HILLSHADING_SIMPLE = { 0.1, 0.666 };
	public final static	double 		DEFAULT_HILLSHADING_DIFFUSELIGHT = 50;
	public final static	double[] 	DEFAULT_HILLSHADING_CLASY = { 0.5, 0, 80,
		Math.max(1,AThreadedHillShading.AvailableProcessors/3), AThreadedHillShading.AvailableProcessors, 1 };
	// MapsforgeTaskConfig.hillShadingMagnitude
	protected final static double 	DEFAULT_HILLSHADING_MAGNITUDE = 1.;

	// Render theme's built-in hillshading default zoom levels
	public final static int 	    DEFAULT_HILLSHADING_ZOOM_MIN =  9;
	public final static int 	    DEFAULT_HILLSHADING_ZOOM_MAX = 17;

	/* Adaptive clear asymmetry hillshading fixed properties */
	// Whether to enable the use of high-quality (bicubic) algorithm for larger zoom levels. Disabling will reduce memory usage at high zoom levels.
	public final static boolean		HILLSHADING_ADAPTIVE_HQ = true;
	// true to let the algorithm decide which zoom levels are supported (default); false to obey values as set in the render theme.
	public final static boolean		HILLSHADING_ADAPTIVE_ZOOM_ENABLED = false;
	// Set a new custom quality scale value for hill shading rendering.A lower value means lower quality.Sometimes this can be useful to improve the performance of hill shading on high-dpi devices.
	// There is usually no reason to set this to a value higher than 1 (the default), although it is allowed,since it makes no sense to have hill shading rendered at a higher resolution than the device's display.
	public final static int			HILLSHADING_ADAPTIVE_CUSTOM_QUALITY_SCALE = 1;

	/***********
	 * PRIVATE *
	 ***********/

	private boolean					parseError = false;
	private final static int 		PAD_MSG = 26;
	private final static 			Logger logger = LoggerFactory.getLogger(PropertiesParser.class);

	protected String readConfig(File configFile) throws Exception {
		String checkSum = null;
		try {
			byte[] data = Files.readAllBytes(configFile.toPath());
			ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
			InputStreamReader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
			configProperties = new Properties();
			configProperties.load(in);
			in.close();
			inputStream.close();
			checkSum = checkSum(data);
		} catch (FileNotFoundException e) {
			logger.error("Can't find config file '" + configFile + "': exiting"); //$NON-NLS-1$
			System.exit(1);
		} catch (IOException e) {
			logger.error("Can't parse config file '" + configFile + "': exiting"); //$NON-NLS-1$
			System.exit(1);
		}
		return checkSum;
	}

	public String checkSum (byte[] data) throws Exception {
		byte[] hash = MessageDigest.getInstance("MD5").digest(data);
		String checksum = new BigInteger(1, hash).toString(16);
		return checksum;
	}

	protected String retrieveConfigValue(String key) throws Exception {
		if (configProperties != null) {
			return configProperties.getProperty(key);
		} else {
			throw new Exception("configFile is NULL");
		}
	}

	protected void parseError(String msgHeader, String msgErr) {
		logger.error(msgHeader + ": error - " + msgErr); //$NON-NLS-1$
		parseError = true;
	}

	protected boolean parseGetError() {
		return parseError;
	}

	protected void parseResetError() {
		parseError = false;
	}

	protected String parsePadMsg(String msg) {
		return String.format("%-" + PAD_MSG + "s", msg);
	}

	/*
	 * excludeInRange: true (minValue < value < maxValue) accepted excludeInRange:
	 * false (minValue <= value <= maxValue) accepted
	 */
	protected Number parseNumber(Object defaultValue, String configValue, Number minValue, Number maxValue,
			String msgHeader, boolean excludeInRange) throws Exception {
		msgHeader = parsePadMsg(msgHeader);
		String targetClass;
		Number target;
		if (defaultValue instanceof String) {
			targetClass = (String) defaultValue;
			target = null;
		} else {
			targetClass = defaultValue.getClass().getSimpleName();
			target = (Number) defaultValue;
		}
		String configString = retrieveConfigValue(configValue); // $NON-NLS-1$
		if (configString != null) {
			try {
				configString = configString.trim();
				String operator;
				switch (targetClass) {
				case "Long":
					target = Long.parseLong(configString);
					break;
				case "Integer":
					target = Integer.parseInt(configString);
					break;
				case "Double":
					target = Double.parseDouble(configString);
					break;
				case "Float":
					target = Float.parseFloat(configString);
					break;
				}				
				if (minValue != null && (target.doubleValue() < minValue.doubleValue()
						|| (excludeInRange && target.doubleValue() == minValue.doubleValue()))) {
					operator = "' < '";
					if (excludeInRange)
						operator = "' <= '";
					parseError(msgHeader, "'" + target + operator + minValue + "' ");
				} else if (maxValue != null && (target.doubleValue() > maxValue.doubleValue()
						|| (excludeInRange && target.doubleValue() == maxValue.doubleValue()))) {
					operator = "' > '";
					if (excludeInRange)
						operator = "' >= '";
					parseError(msgHeader, "'" + target + operator + maxValue + "' ");
				} else {
					logger.info(msgHeader + ": defined [" + target + "]"); //$NON-NLS-1$
				}
			} catch (NumberFormatException e) {
				parseError(msgHeader, "'" + configString + "' not a number ");
			}
		} else if (defaultValue instanceof String) {
			logger.info(msgHeader + ": default [undefined]"); //$NON-NLS-1$
		} else {
			logger.info(msgHeader + ": default [" + target + "]"); //$NON-NLS-1$
		}
		return target;
	}

	protected String parseString(String defaultValue, String configValue, String[] authorizedValues, String msgHeader)
			throws Exception {
		msgHeader = parsePadMsg(msgHeader);
		String target = defaultValue;
		String configString = retrieveConfigValue(configValue); // $NON-NLS-1$
		if (configString != null) {
			configString = configString.trim();
			if (authorizedValues != null && !Arrays.asList(authorizedValues).contains(configString)) {
				parseError(msgHeader, "'" + configString + "' not in {" + String.join(",", authorizedValues) + "} ");
			} else {
				target = configString;
				logger.info(msgHeader + ": defined [" + target + "]"); //$NON-NLS-1$
			}
		} else {
			logger.info(msgHeader + ": default [" + target + "]"); //$NON-NLS-1$
		}
		return target;
	}
	
	protected boolean parseBoolean(Boolean defaultValue, String configValue, String msgHeader) throws Exception {
		msgHeader = parsePadMsg(msgHeader);
		boolean target = defaultValue;
		String configString = retrieveConfigValue(configValue); // $NON-NLS-1$
		if (configString != null) {
			target = Boolean.parseBoolean(configString.trim());
			logger.info(msgHeader + ": defined [" + target + "]"); //$NON-NLS-1$
		} else {
			logger.info(msgHeader + ": default [" + target + "]"); //$NON-NLS-1$
		}
		return target;
	}

	protected boolean parseHasOption(String configValue, String msgHeader) throws Exception {
		msgHeader = parsePadMsg(msgHeader);
		boolean target = false;
		if (configProperties != null) {
			target = configProperties.getProperty(configValue) != null;
		} else {
			throw new Exception("configFile is NULL");
		}
		if (target) {
			logger.info(msgHeader + ": defined [" + String.valueOf(target) + "]"); //$NON-NLS-1$
		} else {
			logger.info(msgHeader + ": default [false]"); //$NON-NLS-1$
		}
		return target;
	}

	protected File parseFile(String configValue, String fileOrFolder, boolean checkFolderNotEmpty, String msgHeader,
			String msgDefault) throws Exception {
		msgHeader = parsePadMsg(msgHeader + " " + fileOrFolder);
		File target = null;
		String configString = retrieveConfigValue(configValue); // $NON-NLS-1$
		if (configString != null) {
			target = new File(configString.trim());
			if (fileOrFolder == FILE) {
				if (!target.isFile()) {
					target = null;
					parseError(msgHeader, "'" + configString + "' not a file");
				}
			} else if (fileOrFolder == FOLDER) {
				if (!target.isDirectory()) {
					target = null;
					parseError(msgHeader, "'" + configString + "' not a folder");
				} else if (checkFolderNotEmpty && target.listFiles().length == 0) {
					target = null;
					parseError(msgHeader, "'" + configString + "' empty folder");
				}
			} else {
				throw new Exception("fileOrFolder '" + fileOrFolder + "' not in [file|folder]"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (target != null)
				logger.info(msgHeader + ": defined [" + target.getPath() + "]"); //$NON-NLS-1$
		} else {
			logger.info(msgHeader + ": default [" + msgDefault + "]"); //$NON-NLS-1$
		}
		return target;
	}
}
