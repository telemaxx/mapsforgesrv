package com.telemaxx.mapsforgesrv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PropertiesParser {

	protected Properties configFile;

	/*
	 * false: use default value true: exit(1)
	 */
	private final static boolean EXITONPARSINGERROR = true;
	private final static int PADMSG = 26;
	protected static final String FILE = "file"; //$NON-NLS-1$
	protected static final String FOLDER = "folder"; //$NON-NLS-1$

	private final static Logger logger = LoggerFactory.getLogger(PropertiesParser.class);

	protected void readConfig(Object config) throws Exception {
		FileInputStream in;
		try {
			if(config instanceof String) {
				in = new FileInputStream((String)config);
			} else if(config instanceof File) {
				in = new FileInputStream((File)config);
			} else {
				throw new Exception("unsupported object type '"+config.toString()+"'");
			}
			configFile = new Properties();
			configFile.load(in);
			in.close();
		} catch (FileNotFoundException e) {
			logger.error("Can't find config file '" + config + "': exiting"); //$NON-NLS-1$
			System.exit(1);
		} catch (IOException e) {
			logger.error("Can't parse config file '" + config + "': exiting"); //$NON-NLS-1$
			System.exit(1);
		}
	}

	protected String retrieveConfigValue(String key) throws Exception {
		if (configFile != null) {
			return configFile.getProperty(key);
		} else {
			throw new Exception("configFile is NULL");
		}
	}

	protected void parseError(String msgHeader, String msgErr, String defaultValue) {
		if (EXITONPARSINGERROR) {
			logger.error(msgHeader + ": exiting - " + msgErr); //$NON-NLS-1$
			System.exit(1);
		}
		if (defaultValue != null) {
			logger.warn(msgHeader + ": default [" + defaultValue + "] - " + msgErr); //$NON-NLS-1$
		} else {
			logger.warn(msgHeader + " - " + msgErr); //$NON-NLS-1$
		}
	}

	protected String parsePadMsg(String msg) {
		return String.format("%-" + PADMSG + "s", msg);
	}

	/*
	 * excludeInRange: true (minValue < value < maxValue) accepted excludeInRange:
	 * false (minValue <= value <= maxValue) accepted
	 */
	protected Number parseNumber(Number defaultValue, String configValue, Number minValue, Number maxValue,
			String msgHeader, boolean excludeInRange) throws Exception {
		msgHeader = parsePadMsg(msgHeader);
		Number target = defaultValue;
		String configString = retrieveConfigValue(configValue); // $NON-NLS-1$
		if (configString != null) {
			try {
				configString = configString.trim();
				String operator;
				if (defaultValue instanceof Long)
					target = Long.parseLong(configString);
				else if (defaultValue instanceof Integer)
					target = Integer.parseInt(configString);
				else if (defaultValue instanceof Double)
					target = Double.parseDouble(configString);
				else if (defaultValue instanceof Float)
					target = Float.parseFloat(configString);
				if (minValue != null && (target.doubleValue() < minValue.doubleValue()
						|| (excludeInRange && target.doubleValue() == minValue.doubleValue()))) {
					operator = "' < '";
					if (excludeInRange)
						operator = "' <= '";
					parseError(msgHeader, "'" + target + operator + minValue + "' ", defaultValue.toString());
					target = defaultValue;
				} else if (maxValue != null && (target.doubleValue() > maxValue.doubleValue()
						|| (excludeInRange && target.doubleValue() == maxValue.doubleValue()))) {
					operator = "' > '";
					if (excludeInRange)
						operator = "' >= '";
					parseError(msgHeader, "'" + target + operator + maxValue + "' ", defaultValue.toString());
					target = defaultValue;
				} else {
					logger.info(msgHeader + ": defined [" + target + "]"); //$NON-NLS-1$
				}
			} catch (NumberFormatException e) {
				parseError(msgHeader, "'" + configString + "' not a number ", defaultValue.toString());
				target = defaultValue;
			}
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
				parseError(msgHeader, "'" + configString + "' not in {" + String.join(",", authorizedValues) + "} ",
						defaultValue);
			} else {
				target = configString;
				logger.info(msgHeader + ": defined [" + target + "]"); //$NON-NLS-1$
			}
		} else {
			logger.info(msgHeader + ": default [" + target + "]"); //$NON-NLS-1$
		}
		return target;
	}

	protected boolean parseHasOption(String configValue, String msgHeader) throws Exception {
		msgHeader = parsePadMsg(msgHeader);
		boolean target = false;
		if (configFile != null) {
			target = configFile.getProperty(configValue) != null;
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
		File target = null;
		String configString = retrieveConfigValue(configValue); // $NON-NLS-1$
		if (configString != null) {
			target = new File(configString.trim());
			if (fileOrFolder == FILE) {
				if (!target.isFile()) {
					target = null;
					parseError(parsePadMsg(msgHeader + " " + fileOrFolder), "'" + configString + "' not a file",
							msgDefault);
				}
			} else if (fileOrFolder == FOLDER) {
				if (!target.isDirectory()) {
					target = null;
					parseError(parsePadMsg(msgHeader + " " + fileOrFolder), "'" + configString + "' not a folder",
							msgDefault);
				} else if (checkFolderNotEmpty && target.listFiles().length == 0) {
					target = null;
					parseError(parsePadMsg(msgHeader + " " + fileOrFolder), "'" + configString + "' empty folder",
							msgDefault);
				}
			} else {
				throw new Exception("fileOrFolder '" + fileOrFolder + "' not in [file|folder]"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (target != null)
				logger.info(parsePadMsg(msgHeader + " " + fileOrFolder) + ": defined [" + target.getPath() + "]"); //$NON-NLS-1$
		} else {
			logger.info(parsePadMsg(msgHeader) + ": default [" + msgDefault + "]"); //$NON-NLS-1$
		}
		return target;
	}
}
