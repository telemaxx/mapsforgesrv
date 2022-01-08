package com.telemaxx.mapsforgesrv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapsforgeConfig {

	private CommandLine configCmd;
	private Properties configFile;

	private File themeFile = null;
	private String rendererName = null;
	private String preferredLanguage = null;
	private int portNumber;
	private ArrayList<File> mapFiles = null;
	private String[] serverConnectors = null;
	private String listeningInterface = null;
	private String[] themeFileOverlays = null;
	private String themeFileStyle = null;
	private double[] hillShadingArguments;
	private String hillShadingAlgorithm = null;
	private double hillShadingMagnitude;
	private File demFolder = null;
	private long cacheControl;
	private int blackValue;
	private int maxQueueSize;
	private int minThreads;
	private int maxThreads;
	private long idleTimeout;
	private double gammaValue;

	private final static long DEFAULTCACHECONTROL = 0;
	private final static int DEFAULTSERVERPORT = 8080;
	private final static int DEFAULTSERVERMAXQUEUESIZE = 256;
	private final static int DEFAULTSERVERMINTHREADS = 0;
	private final static int DEFAULTSERVERMAXTHREADS = 8;
	private final static long DEFAULTSERVERIDELTIMEOUT = 0;
	private final static double DEFAULTGAMMA = 1.;
	private final static double DEFAULTHSMAGNITUDE = 1.;
	private final static int DEFAULTBLACK = 0;
	private final static String[] AUTHORIZEDCONNECTORS = { "http11", "proxy", "h2c" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private final static String[] DEFAULTCONNECTORS = { AUTHORIZEDCONNECTORS[0] }; // $NON-NLS-1$
	private final static String[] AUTHORIZEDRENDERER = { "database", "direct", }; //$NON-NLS-1$ //$NON-NLS-2$
	private final static String DEFAULTRENDERER = AUTHORIZEDRENDERER[0];
	private final static String[] AUTHORIZEDSERVERINTERFACE = { "localhost", "all", }; //$NON-NLS-1$ //$NON-NLS-2$
	private final static String DEFAULTSERVERINTERFACE = AUTHORIZEDSERVERINTERFACE[0];

	/*
	 * true: More precise at tile edges but much slower 
	 * false: Less precise at tile edges but much faster
	 */
	public final boolean HILLSHADINGENABLEINTERPOLATIONOVERLAP = false;
	public final double[] HILLSHADINGSIMPLEDEFAULT = { 0.1, 0.666 };
	public final double HILLSHADINGDIFDUSELIGHTDEFAULT = 50;
	public final String EXTENSIONDEFAULT = "png"; //$NON-NLS-1$
	public final float TEXTSCALEDEFAULT = 1.0f;
	public final float USERSCALEDEFAULT = 1.0f;
	public final int TILERENDERSIZEDEFAULT = 256;
	public final boolean TRANSPARENTDEFAULT = false;
	public final int SERVERACCEPTQUEUESIZE = 128;
	public final int SERVERACCEPTORS = 1;
	public final int SERVERSELECTORS = 1;

	/*
	 * false: use default value 
	 * true: exit(1)
	 */
	private final static boolean EXITONPARSINGERROR = false;

	// log response time thread state and queue size detail for each request
	// ex. [ms:1820;idle:0;qs:92]
	public boolean LOGREQDET = false;
	// log hillshading configuration detail for each request
	// ex. SimpleShadingAlgorithm{linearity=0.0, scale=1.0, magnitude=1.0}
	public boolean LOGREQDETHS = false;
	private final static int PADMSG = 23;
	private final static Logger logger = LoggerFactory.getLogger(MapsforgeConfig.class);
	
	private static final String FILE = "file"; //$NON-NLS-1$
	private static final String FOLDER = "folder"; //$NON-NLS-1$

	public MapsforgeConfig(String[] args) throws Exception {
		initOptions(args);
		initConfig();
	}

	/*
	 * OPTIONS
	 */
	private void initOptions(String[] args) {
		Options options = new Options();

		options.addOption(Option.builder("p") //$NON-NLS-1$
				.longOpt("port") //$NON-NLS-1$
				.desc("Listening TCP Port of the server (default: " + DEFAULTSERVERPORT + ")") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("if") //$NON-NLS-1$
				.longOpt("interface") //$NON-NLS-1$
				.desc("Listening interface(s) of the server [all,localhost] (default: localhost)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("m") //$NON-NLS-1$
				.longOpt("mapfiles") //$NON-NLS-1$
				.desc("Comma-separated list of mapsforge map files [.map]") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("t") //$NON-NLS-1$
				.longOpt("themefile") //$NON-NLS-1$
				.desc("Mapsforge theme file [.xml] (default: the internal OSMARENDER)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("s") //$NON-NLS-1$
				.longOpt("style") //$NON-NLS-1$
				.desc("Style of the theme file [.xml] (default: default defined in xml file)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("o") //$NON-NLS-1$
				.longOpt("overlays") //$NON-NLS-1$
				.desc("Comma-separated list of style's overlay ids of the theme file [.xml] (default: overlays enabled in xml file)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("l") //$NON-NLS-1$
				.longOpt("language") //$NON-NLS-1$
				.desc("Preferred language (default: native language)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("cs") //$NON-NLS-1$
				.longOpt("contrast-stretch") //$NON-NLS-1$
				.desc("Stretch contrast [0..254] (default: 0)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("gc") //$NON-NLS-1$
				.longOpt("gamma-correction") //$NON-NLS-1$
				.desc("Gamma correction value [> 0] (default: 1)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("hs") //$NON-NLS-1$
				.longOpt("hillshading-algorithm") //$NON-NLS-1$
				.desc("Hillshading algorithm and optional parameters [simple,simple(linearity,scale),diffuselight,diffuselight(angle)] (default: no hillshading)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("hm") //$NON-NLS-1$
				.longOpt("hillshading-magnitude") //$NON-NLS-1$
				.desc("Hillshading gray value scaling factor [>= 0] (default: 1)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("d") //$NON-NLS-1$
				.longOpt("demfolder") //$NON-NLS-1$
				.desc("Folder path containing digital elevation model files [.hgt] for hillshading (default: none)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("r") //$NON-NLS-1$
				.longOpt("renderer") //$NON-NLS-1$
				.desc("Mapsforge renderer algorithm [database,direct] (default: database)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("cc") //$NON-NLS-1$
				.longOpt("cache-control") //$NON-NLS-1$
				.desc("If set, add Cache-Control header for served tiles. value in seconds, (default: 0 - disabled)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("mxq") //$NON-NLS-1$
				.longOpt("max-queuesize") //$NON-NLS-1$
				.desc("Maximum queue size for waiting & running rendering jobs (default: 256)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("mxt") //$NON-NLS-1$
				.longOpt("max-thread") //$NON-NLS-1$
				.desc("Maximum concurrent threads for rendering job (default: 8)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("mit") //$NON-NLS-1$
				.longOpt("min-thread") //$NON-NLS-1$
				.desc("Minimum pool size for rendering job (default: 0)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("idl") //$NON-NLS-1$
				.longOpt("idle-timeout") //$NON-NLS-1$
				.desc("Maximum thread idle time in milliseconds (default: 0 - disabled)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("ct") //$NON-NLS-1$
				.longOpt("connectors") //$NON-NLS-1$
				.desc("Comma-separated list of enabled server connector protocol(s) [http11,proxy,h2c] (default: http11)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("c") //$NON-NLS-1$
				.longOpt("config") //$NON-NLS-1$
				.desc("Config file overriding cmd line parameters (default: none)") //$NON-NLS-1$
				.required(false).hasArg(true).build());

		options.addOption(Option.builder("h") //$NON-NLS-1$
				.longOpt("help") //$NON-NLS-1$
				.desc("Print this help text and exit") //$NON-NLS-1$
				.required(false).hasArg(false).build());

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		try {
			configCmd = parser.parse(options, args);
			if (configCmd.hasOption("help")) { //$NON-NLS-1$
				formatter.printHelp("mapsforgesrv", options); //$NON-NLS-1$
				System.exit(0);
			}
		} catch (ParseException e) {
			logger.error(e.getMessage());
			formatter.printHelp("mapsforgesrv", options); //$NON-NLS-1$
			System.exit(1);
		}
		String config = configCmd.getOptionValue("config");
		if (config != null) {
			FileInputStream in;
			try {
				in = new FileInputStream(config);
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
	}

	/*
	 * PARSERS
	 */

	private void parseError(String msgHeader, String msgErr, String defaultValue) {
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

	private String parsePadMsg(String msg) {
		return String.format("%-" + PADMSG + "s", msg);
	}

	/*
	 * excludeInRange: true (minValue < value < maxValue) accepted
	 * excludeInRange: false (minValue <= value <= maxValue) accepted
	 */
	private Number parseNumber(Number defaultValue, String configValue, Number minValue, Number maxValue, String msgHeader, boolean excludeInRange) {
		msgHeader = parsePadMsg(msgHeader);
		Number target = defaultValue;
		String configString = retrieveConfigValue(configValue); // $NON-NLS-1$
		if (configString != null) {
			try {
				configString = configString.trim();
				String operator;
				if (defaultValue instanceof Long)
					target = Long.parseLong(configString);
				if (defaultValue instanceof Integer)
					target = Integer.parseInt(configString);
				if (defaultValue instanceof Double)
					target = Double.parseDouble(configString);
				if (minValue != null && (target.doubleValue() < minValue.doubleValue() || (excludeInRange && target.doubleValue() == minValue.doubleValue()))) {
					operator = "' < '";
					if(excludeInRange)
						operator = "' <= '";
					parseError(msgHeader, "'" + target + operator + minValue + "' ", defaultValue.toString());
					target = defaultValue;
				} else if (maxValue != null && (target.doubleValue() > maxValue.doubleValue() || (excludeInRange && target.doubleValue() == maxValue.doubleValue()))) {
					operator = "' > '";
					if(excludeInRange)
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

	private String parseString(String defaultValue, String configValue, String[] authorizedValues, String msgHeader) {
		msgHeader = parsePadMsg(msgHeader);
		String target = defaultValue;
		String configString = retrieveConfigValue(configValue); // $NON-NLS-1$
		if (configString != null) {
			configString = configString.trim();
			if (authorizedValues != null && !Arrays.asList(authorizedValues).contains(configString)) {
				parseError(msgHeader, "'" + configString + "' not in {" + String.join(",", authorizedValues) + "} ", defaultValue);
			} else {
				target = configString;
				logger.info(msgHeader + ": defined [" + target + "]"); //$NON-NLS-1$
			}
		} else {
			logger.info(msgHeader + ": default [" + target + "]"); //$NON-NLS-1$
		}
		return target;
	}

	private File parseFile(String configValue, String fileOrFolder, boolean checkFolderNotEmpty, String msgHeader, String msgDefault) throws Exception {
		File target = null;
		String configString = retrieveConfigValue(configValue); // $NON-NLS-1$
		if (configString != null) {
			target = new File(configString.trim());
			if (fileOrFolder == FILE) {
				if (!target.isFile()) {
					target = null;
					parseError(parsePadMsg(msgHeader + " " + fileOrFolder), "'" + configString + "' not a file", msgDefault);
				}
			} else if (fileOrFolder == FOLDER) {
				if (!target.isDirectory()) {
					target = null;
					parseError(parsePadMsg(msgHeader + " " + fileOrFolder), "'" + configString + "' not a folder", msgDefault);
				} else if (checkFolderNotEmpty && target.listFiles().length == 0) {
					target = null;
					parseError(parsePadMsg(msgHeader + " " + fileOrFolder), "'" + configString + "' empty folder", msgDefault);
				}
			} else {
				throw new Exception("fileOrFolder '" + fileOrFolder + "' not in [file|foler]"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (target != null)
				logger.info(parsePadMsg(msgHeader + " " + fileOrFolder) + ": defined [" + target.getPath() + "]"); //$NON-NLS-1$
		} else {
			logger.info(parsePadMsg(msgHeader) + ": default [" + msgDefault + "]"); //$NON-NLS-1$
		}
		return target;
	}

	private void parseServerConnectors() {
		String msgHeader = parsePadMsg("Server connector(s)"); //$NON-NLS-1$
		List<String> connectorsErr = new ArrayList<String>();
		String connectorsString = retrieveConfigValue("connectors"); //$NON-NLS-1$
		if (connectorsString != null) {
			serverConnectors = connectorsString.trim().split(","); //$NON-NLS-1$ //$NON-NLS-2$
			for (String connector : serverConnectors) {
				if (!Arrays.asList(AUTHORIZEDCONNECTORS).contains(connector)) {
					connectorsErr.add(connector);
					serverConnectors = ArrayUtils.removeElement(serverConnectors, connector);
				}
			}
			if (connectorsErr.size() > 0) {
				String cnxNotAuth = "{" + String.join(",", connectorsErr) + "} not in {" + String.join(",", AUTHORIZEDCONNECTORS) + "}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				if (serverConnectors.length == 0) {
					parseError(msgHeader, cnxNotAuth, "{" + String.join(",", serverConnectors) + "}");
				} else {
					logger.info(msgHeader + ": defined [{" + String.join(",", serverConnectors) + "}] - warn " + cnxNotAuth); //$NON-NLS-1$
				}
			} else {
				logger.info(msgHeader + ": defined [{" + String.join(",", serverConnectors) + "}]"); //$NON-NLS-1$
			}
		} else {
			serverConnectors = DEFAULTCONNECTORS;
			logger.info(msgHeader + ": default [{" + String.join(",", DEFAULTCONNECTORS) + "}]"); //$NON-NLS-1$
		}
	}

	private void parseMapFiles() {
		String msgHeader = parsePadMsg("Map file(s)"); //$NON-NLS-1$
		String mapFilePathsString = retrieveConfigValue("mapfiles"); //$NON-NLS-1$
		if (mapFilePathsString != null) {
			String[] mapFilePaths = mapFilePathsString.trim().split(","); //$NON-NLS-1$ //$NON-NLS-2$
			mapFiles = new ArrayList<File>();
			List<File> mapsErr = new ArrayList<File>();
			for (String path : mapFilePaths) {
				mapFiles.add(new File(path.trim()));
			}
			mapFiles.forEach(mapFile -> {
				if (!mapFile.isFile())
					mapsErr.add(mapFile);
			});
			String mapFilesSting;
			if (mapsErr.size() > 0) {
				mapFiles.removeAll(mapsErr);
				mapFilesSting = mapFiles.stream().map(File::getPath).collect(Collectors.joining(","));
				String cnxNotAuth = "{" + mapsErr.stream().map(File::getPath).collect(Collectors.joining(",")) + "} not existing"; //$NON-NLS-2$ //$NON-NLS-3$
				if (serverConnectors.length == 0) {
					parseError(msgHeader, cnxNotAuth, "{" + mapFilesSting + "}");
				} else {
					logger.info(msgHeader + ": defined [{" + mapFilesSting + "}] - warn " + cnxNotAuth); //$NON-NLS-1$
				}
			} else {
				mapFilesSting = mapFiles.stream().map(File::getPath).collect(Collectors.joining(","));
				logger.info(msgHeader + ": defined [{" + mapFilesSting + "}]"); //$NON-NLS-1$
			}
		} else {
			logger.error(msgHeader + ": exiting - no file(s) specified"); //$NON-NLS-1$
			System.exit(1);
		}
	}

	private void parseThemeOverlays() {
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

	private void parseHillShading() {
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
						hillShadingArguments[0] = HILLSHADINGSIMPLEDEFAULT[0];
						hillShadingArguments[1] = HILLSHADINGSIMPLEDEFAULT[1];
					}
					logger.info(msgHeader + ": defined [" + hillShadingAlgorithm + "(" + hillShadingArguments[0] + "," //$NON-NLS-3$
							+ hillShadingArguments[1] + ")]");
				} else {
					hillShadingAlgorithm = new String(m.group(4)); // ShadingAlgorithm = diffuselight
					hillShadingArguments = new double[1];
					if (m.group(5) != null) {
						hillShadingArguments[0] = Double.parseDouble(m.group(5));
					} else { // default value
						hillShadingArguments[0] = HILLSHADINGDIFDUSELIGHTDEFAULT;
					}
					logger.info(msgHeader + ": defined [" + hillShadingAlgorithm + "(" + hillShadingArguments[0] + ")]"); //$NON-NLS-1$
				}
			} else {
				parseError(msgHeader, "'" + hillShadingOption + "' invalid", "undefined");
			}
		}
	}

	/*
	 * CONFIG
	 */

	private void initConfig() throws Exception {
		logger.info("################## CONFIG INIT ##################");
		portNumber = (int) parseNumber(DEFAULTSERVERPORT, "port", 1024, 65535, "Listening TCP port",false); //$NON-NLS-1$ //$NON-NLS-2$
		listeningInterface = parseString(DEFAULTSERVERINTERFACE, "interface", AUTHORIZEDSERVERINTERFACE, "Server interface"); //$NON-NLS-1$ //$NON-NLS-2$
		parseMapFiles();
		themeFile = parseFile("themefile", FILE, false, "Theme", "OSMARENDER");
		themeFileStyle = parseString(null, "style", null, "Theme style"); //$NON-NLS-1$ //$NON-NLS-2$
		parseThemeOverlays();
		preferredLanguage = parseString(null, "language", null, "Preferred map language"); //$NON-NLS-1$ //$NON-NLS-2$
		blackValue = (int) parseNumber(DEFAULTBLACK, "contrast-stretch", 0, 254, "Contrast stretch",false); //$NON-NLS-1$ //$NON-NLS-2$
		gammaValue = (double) parseNumber(DEFAULTGAMMA, "gamma-correction", 0., null, "Gamma correction",true); //$NON-NLS-1$ //$NON-NLS-2$
		parseHillShading();
		hillShadingMagnitude = (double) parseNumber(DEFAULTHSMAGNITUDE, "hillshading-magnitude", 0., null, "Hillshading magnitude",false); //$NON-NLS-1$ //$NON-NLS-2$
		demFolder = parseFile("demfolder", FOLDER, true, "DEM", "undefined");
		rendererName = parseString(DEFAULTRENDERER, "renderer", AUTHORIZEDRENDERER, "Renderer algorithm"); //$NON-NLS-1$ //$NON-NLS-2$
		cacheControl = (long) parseNumber(DEFAULTCACHECONTROL, "cache-control", 0, null, "Browser cache ttl",false); //$NON-NLS-1$ //$NON-NLS-2$
		maxQueueSize = (int) parseNumber(DEFAULTSERVERMAXQUEUESIZE, "max-queuesize", 0, null, "Server max queue size",false); //$NON-NLS-1$ //$NON-NLS-2$
		maxThreads = (int) parseNumber(DEFAULTSERVERMAXTHREADS, "max-thread", 1, null, "Server max thread(s)",false); //$NON-NLS-1$ //$NON-NLS-2$
		minThreads = (int) parseNumber(DEFAULTSERVERMINTHREADS, "min-thread", 0, null, "Server min thread(s)",false); //$NON-NLS-1$ //$NON-NLS-2$
		idleTimeout = (long) parseNumber(DEFAULTSERVERIDELTIMEOUT, "idle-timeout", 0, null, "Connection idle timeout",false); //$NON-NLS-1$ //$NON-NLS-2$
		parseServerConnectors();
	}

	private String retrieveConfigValue(String key) {
		if (configFile != null) {
			return configFile.getProperty(key);
		} else {
			return configCmd.getOptionValue(key);
		}
	}

	/*
	 * GETTERS
	 */

	public int getBlackValue() {
		return this.blackValue;
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

	public long getIdleTimeout() {
		return this.idleTimeout;
	}

	public String getThemeFileStyle() {
		return this.themeFileStyle;
	}

	public String[] getThemeFileOverlays() {
		return this.themeFileOverlays;
	}

	public String getListeningInterface() {
		return this.listeningInterface;
	}

	public List<File> getMapFiles() {
		return this.mapFiles;
	}

	public int getMaxQueueSize() {
		return this.maxQueueSize;
	}

	public int getPortNumber() {
		return this.portNumber;
	}

	public int getMaxThreads() {
		return this.maxThreads;
	}

	public int getMinThreads() {
		return this.minThreads;
	}

	public long getCacheControl() {
		return this.cacheControl;
	}

	public String getPreferredLanguage() {
		return this.preferredLanguage;
	}

	public String getRendererName() {
		return this.rendererName;
	}

	public File getThemeFile() {
		return this.themeFile;
	}

	public String[] getServerConnectors() {
		return serverConnectors;
	}

	public double getGammaValue() {
		return gammaValue;
	}
}
