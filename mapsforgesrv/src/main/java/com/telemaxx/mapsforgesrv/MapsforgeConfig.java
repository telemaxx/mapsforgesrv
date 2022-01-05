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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapsforgeConfig {
	
	private final static int DEFAULTSERVERPORT = 8080;
	private final static String DEFAULTSERVERINTERFACE = "localhost"; //$NON-NLS-1$
	private final static int DEFAULTSERVERMAXQUEUESIZE = 256;
	private final static int DEFAULTSERVERMINTHREADS = 0;
	private final static int DEFAULTSERVERMAXTHREADS = 8;
	private final static int DEFAULTSERVERIDELTIMEOUT = 0;
	private final static String[] AUTHORIZEDCONNECTORS = { "http11", "proxy", "h2c" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private final static String[] DEFAULTCONNECTORS = { "http11" }; //$NON-NLS-1$

	public final String RENDERERDEFAULT = "dataset";
	public final boolean HILLSHADINGENABLEINTERPOLATIONOVERLAP = false;
	/*
	 * true:  More precise at tile edges but much slower
	 * false: Less precise at tile edges but much faster
	 */
	public final double[] HILLSHADINGSIMPLEDEFAULT = {0.1,0.666};
	public final double HILLSHADINGDIFDUSELIGHTDEFAULT = 50.;
	public final double HILLSHADINGMAGNITUDEDEFAULT = 1.;
	public final int BLACKVALUEDEFAULT = 0;
	public final double GAMMAVALUEDEFAULT = 1.;
	public final String EXTENSIONDEFAULT = "png"; //$NON-NLS-1$
	public final float TEXTSCALEDEFAULT = 1.0f;
	public final float USERSCALEDEFAULT = 1.0f;
	public final int TILERENDERSIZEDEFAULT = 256;
	public final boolean TRANSPARENTDEFAULT = false;
	public final int SERVERACCEPTQUEUESIZE = 128;
	public final int SERVERACCEPTORS = 1;
	public final int SERVERSELECTORS = 1;
	public final long CACHECONTROLDEFAULT = 0;

	private CommandLine configCmd;
	private Properties configFile;

	private int portNumber = DEFAULTSERVERPORT;
	private String[] serverConnectors = DEFAULTCONNECTORS;
	private String listeningInterface = DEFAULTSERVERINTERFACE;
	private String preferredLanguage = null;
	private String rendererName = RENDERERDEFAULT;
	private ArrayList<File> mapFiles = null;
	private File themeFile = null;
	private String[] themeFileOverlays = null;
	private String themeFileStyle = null;
	private double[] hillShadingArguments;
	private String hillShadingAlgorithm = null;
	private double hillShadingMagnitude = HILLSHADINGMAGNITUDEDEFAULT;
	private File demFolder = null;
	private int blackValue = BLACKVALUEDEFAULT;
	private double gammaValue = GAMMAVALUEDEFAULT;
	private long cacheControl = CACHECONTROLDEFAULT;
	private int maxQueueSize = DEFAULTSERVERMAXQUEUESIZE;
	private int minThreads = DEFAULTSERVERMINTHREADS;
	private int maxThreads = DEFAULTSERVERMAXTHREADS;
	private long idleTimeout = DEFAULTSERVERIDELTIMEOUT;
	
	// log hillshading configuration detail for each request
	public final boolean LOGHSREQDET = true;
	private final static Logger logger = LoggerFactory.getLogger(MapsforgeConfig.class);

	public MapsforgeConfig(String[] args) {
		Options options = new Options();
		Option optionArgument;

		optionArgument = new Option("cc", "cachecontrol", true, //$NON-NLS-1$ //$NON-NLS-2$
				"If set, add Cache-Control header for served tiles. value in seconds, (default: 0 - disabled)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("mxq", "maxqueuesize", true, //$NON-NLS-1$ //$NON-NLS-2$
				"Maximum queue size for rendering jobs [waiting & running] (default: 256)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("mxt", "maxthread", true, //$NON-NLS-1$ //$NON-NLS-2$
				"Maximum concurrent threads for rendering job (default: 8)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("mit", "minthread", true, //$NON-NLS-1$ //$NON-NLS-2$
				"Minimum pool size for rendering job (default: 0)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("idl", "idletimeout", true, //$NON-NLS-1$ //$NON-NLS-2$
				"Server maximum Idle time for a connection in milliseconds (default: 0 - disabled)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("ct", "connectors", true, //$NON-NLS-1$ //$NON-NLS-2$
				"comma-separated list of server connector protocol [http11,proxy,h2c] (default: http11)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("c", "config", true, //$NON-NLS-1$ //$NON-NLS-2$
				"config file overriding params"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("r", "renderer", true, //$NON-NLS-1$ //$NON-NLS-2$
				"mapsforge renderer [database,direct] (default: database)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("m", "mapfiles", true, //$NON-NLS-1$ //$NON-NLS-2$
				"comma-separated list of mapsforge map files (.map)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("t", "themefile", true, //$NON-NLS-1$ //$NON-NLS-2$
				"mapsforge theme file(.xml), (default: the internal OSMARENDER)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("s", "style", true, //$NON-NLS-1$ //$NON-NLS-2$
				"style of the theme file(.xml), (default: default defined in xml file)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("o", "overlays", true, //$NON-NLS-1$ //$NON-NLS-2$
				"comma-separated list of style\'s overlay ids of the theme file(.xml), (default: overlays enabled in xml file)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("l", "language", true, //$NON-NLS-1$ //$NON-NLS-2$
				"preferred language (default: native language)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("hs", "hillshading-algorithm", true, //$NON-NLS-1$ //$NON-NLS-2$
				"simple or simple(linearity,scale) or diffuselight or diffuselight(angle), (default: no hillshading)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("hm", "hillshading-magnitude", true, //$NON-NLS-1$ //$NON-NLS-2$
				"gray value scaling factor >= 0 (default: 1.)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("d", "demfolder", true, //$NON-NLS-1$ //$NON-NLS-2$
				"folder containing .hgt digital elevation model files"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("cs", "contrast-stretch", true, //$NON-NLS-1$ //$NON-NLS-2$
				"stretch contrast within range 0..254 (default: 0)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);
		
		optionArgument = new Option("gc", "gamma-correction", true, "gamma correction value > 0. (default: 1.)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("p", "port", true, //$NON-NLS-1$ //$NON-NLS-2$
				"port, where the server is listening (default: " + DEFAULTSERVERPORT + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("if", "interface", true, //$NON-NLS-1$ //$NON-NLS-2$
				"which interface listening [all,localhost] (default: localhost)"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

		optionArgument = new Option("h", "help", false, //$NON-NLS-1$ //$NON-NLS-2$
				"print this help text and exit"); //$NON-NLS-1$
		optionArgument.setRequired(false);
		options.addOption(optionArgument);

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
				logger.error("ERROR: can't find config file '" + config + "'"); //$NON-NLS-1$
				System.exit(1);
			} catch (IOException e) {
				logger.error("ERROR: can't parse config file '" + config + "'"); //$NON-NLS-1$
				System.exit(1);
			}
		}
	}

	public void initConfig() {
		String configValue;
		
		configValue = retrieveConfigValue("interface"); //$NON-NLS-1$
		if (configValue != null) {
			listeningInterface = configValue;
		}

		configValue = retrieveConfigValue("port"); //$NON-NLS-1$
		if (configValue != null) {
			try {
				// logger.info("portString" + configValue);
				portNumber = Integer.parseInt(configValue);
				if (portNumber < 1024 || portNumber > 65535) {
					logger.error("ERROR: portnumber not 1024-65535!"); //$NON-NLS-1$
					System.exit(1);
				} else {
					// logger.info("Using port: " + portNumber); //$NON-NLS-1$
				}
			} catch (NumberFormatException e) {
				logger.error("couldnt parse portnumber, using " + portNumber); //$NON-NLS-1$
			}
		} else {
			logger.info("no port given, using " + portNumber); //$NON-NLS-1$
		}

		configValue = retrieveConfigValue("cachecontrol"); //$NON-NLS-1$
		if (configValue != null) {
			try {
				cacheControl = Long.parseLong(configValue);
				if (cacheControl < 0) {
					logger.error("ERROR: cachecontrol '" + cacheControl + "' not positive: disabled"); //$NON-NLS-1$
				} else {
					logger.info("Browser cache control ttl: " + cacheControl); //$NON-NLS-1$
				}
			} catch (NumberFormatException e) {
				logger.error("couldnt parse cachecontrol: disabled"); //$NON-NLS-1$
			}
		} else {
			logger.info("no browser cache control ttl given: disabled"); //$NON-NLS-1$
		}

		configValue = retrieveConfigValue("maxqueuesize"); //$NON-NLS-1$
		if (configValue != null) {
			try {
				maxQueueSize = Integer.parseInt(configValue);
				if (maxQueueSize < 0 || maxQueueSize > 65535) {
					logger.error("ERROR: maxqueuesize not 0-65535!"); //$NON-NLS-1$
					System.exit(1);
				} else {
					logger.info("Max queue size: " + maxQueueSize); //$NON-NLS-1$
				}
			} catch (NumberFormatException e) {
				logger.error("couldnt parse maxqueuesize, using " + maxQueueSize); //$NON-NLS-1$
			}
		} else {
			logger.info("no max queue size given, using " + maxQueueSize); //$NON-NLS-1$
		}

		configValue = retrieveConfigValue("minthread"); //$NON-NLS-1$
		if (configValue != null) {
			try {
				minThreads = Integer.parseInt(configValue);
				if (minThreads < 0 || minThreads > 65535) {
					logger.error("ERROR: minthread not 0-65535!"); //$NON-NLS-1$
					System.exit(1);
				} else {
					logger.info("Min thread(s): " + minThreads); //$NON-NLS-1$
				}
			} catch (NumberFormatException e) {
				logger.error("couldnt parse minthread, using " + minThreads); //$NON-NLS-1$
			}
		} else {
			logger.info("no min thread(s) given, using " + minThreads); //$NON-NLS-1$
		}

		configValue = retrieveConfigValue("maxthread"); //$NON-NLS-1$
		if (configValue != null) {
			try {
				maxThreads = Integer.parseInt(configValue);
				if (maxThreads < 1 || maxThreads > 65535) {
					logger.error("ERROR: maxthread not 1-65535!"); //$NON-NLS-1$
					System.exit(1);
				} else {
					logger.info("Max thread(s): " + maxThreads); //$NON-NLS-1$
				}
			} catch (NumberFormatException e) {
				logger.error("couldnt parse maxthread, using " + maxThreads); //$NON-NLS-1$
			}
		} else {
			logger.info("no max thread(s) given, using " + maxThreads); //$NON-NLS-1$
		}

		configValue = retrieveConfigValue("idletimeout"); //$NON-NLS-1$
		if (configValue != null) {
			try {
				idleTimeout = Long.parseLong(configValue);
				if (idleTimeout < 0) {
					logger.error("ERROR: idletimeout not positive!"); //$NON-NLS-1$
					System.exit(1);
				} else {
					logger.info("Connection idle timeout: " + idleTimeout); //$NON-NLS-1$
				}
			} catch (NumberFormatException e) {
				logger.error("couldnt parse idletimeout, using " + idleTimeout); //$NON-NLS-1$
			}
		} else {
			logger.info("no connection idle timeout given, using " + idleTimeout); //$NON-NLS-1$
		}

		configValue = retrieveConfigValue("renderer"); //$NON-NLS-1$
		if (configValue != null) {
			rendererName = configValue.toLowerCase();
			logger.info("Renderer: " + rendererName); //$NON-NLS-1$
			if ((!rendererName.equals("database")) && (!rendererName.equals("direct"))) {
				logger.error("ERROR: unknown renderer!"); //$NON-NLS-1$
				System.exit(1);
			}
		}

		configValue = retrieveConfigValue("connectors"); //$NON-NLS-1$
		if (configValue != null) {
			serverConnectors = configValue.split(","); //$NON-NLS-1$ //$NON-NLS-2$
			for (String connector : serverConnectors) {
				if (!Arrays.asList(AUTHORIZEDCONNECTORS).contains(connector)) {
					logger.error("ERROR: server connector '" + connector + "' does not exist! " //$NON-NLS-1$
							+ Arrays.toString(AUTHORIZEDCONNECTORS));
					System.exit(1);
				}
			}
			logger.info("Server connectors: " + Arrays.toString(serverConnectors)); //$NON-NLS-1$
		} else {
			logger.info("no server connectors given, using " + Arrays.toString(serverConnectors)); //$NON-NLS-1$
		}

		configValue = retrieveConfigValue("mapfiles");
		if (configValue != null) {
			String[] mapFilePaths = configValue.split(","); //$NON-NLS-1$ //$NON-NLS-2$
			mapFiles = new ArrayList<>();
			for (String path : mapFilePaths) {
				mapFiles.add(new File(path.trim()));
			}
			mapFiles.forEach(mapFile -> {
				logger.info("Map file: " + mapFile); //$NON-NLS-1$
				if (!mapFile.isFile()) {
					logger.error("ERROR: Map file does not exist!"); //$NON-NLS-1$
					System.exit(1);
				}
			});
		} else {
			logger.error("ERROR: no mapfiles specified!"); //$NON-NLS-1$
			System.exit(1);
		}

		configValue = retrieveConfigValue("themefile"); //$NON-NLS-1$
		if (configValue != null) {
			themeFile = new File(configValue);
			logger.info("Theme file: " + themeFile); //$NON-NLS-1$
			if (!themeFile.isFile()) {
				logger.error("ERROR: theme file does not exist!"); //$NON-NLS-1$
				System.exit(1);
			}
		} else {
			logger.info("Theme: OSMARENDER"); //$NON-NLS-1$
		}

		configValue = retrieveConfigValue("style"); //$NON-NLS-1$
		if (configValue != null) {
			themeFileStyle = configValue;
			logger.info("Selected ThemeStyle: " + themeFileStyle); //$NON-NLS-1$
		}

		configValue = retrieveConfigValue("overlays"); //$NON-NLS-1$
		if (configValue != null) {
			themeFileOverlays = configValue.split(","); //$NON-NLS-1$
			for (int i = 0; i < themeFileOverlays.length; i++) {
				themeFileOverlays[i] = themeFileOverlays[i].trim();
				logger.info("Selected ThemeOverlay: " + themeFileOverlays[i]); // $NON-NLS-1
			}
		}

		configValue = retrieveConfigValue("language"); //$NON-NLS-1$
		if (configValue != null) {
			preferredLanguage = configValue;
			logger.info("Preferred map language: " + preferredLanguage); //$NON-NLS-1$
		}

		configValue = retrieveConfigValue("hillshading-algorithm"); //$NON-NLS-1$
		if (configValue != null) {
			Pattern P = Pattern.compile(
					"(simple)(?:\\((\\d+\\.?\\d*|\\d*\\.?\\d+),(\\d+\\.?\\d*|\\d*\\.?\\d+)\\))?|(diffuselight)(?:\\((\\d+\\.?\\d*|\\d*\\.?\\d+)\\))?");
			Matcher m = P.matcher(configValue);
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
					logger.info("Hillshading algorithm: " + hillShadingAlgorithm + "(" + hillShadingArguments[0] + "," //$NON-NLS-3$
							+ hillShadingArguments[1] + ")");
				} else {
					hillShadingAlgorithm = new String(m.group(4)); // ShadingAlgorithm = diffuselight
					hillShadingArguments = new double[1];
					if (m.group(5) != null) {
						hillShadingArguments[0] = Double.parseDouble(m.group(5));
					} else { // default value
						hillShadingArguments[0] = HILLSHADINGDIFDUSELIGHTDEFAULT;
					}
					logger.info("Hillshading algorithm: " + hillShadingAlgorithm + "(" + hillShadingArguments[0] + ")"); //$NON-NLS-1$
				}
			} else {
				logger.error("ERROR: hillshading algorithm '" + configValue + "' invalid!"); //$NON-NLS-1$
				System.exit(1);
			}
		}

		configValue = retrieveConfigValue("hillshading-magnitude"); //$NON-NLS-1$
		if (configValue != null) {
			Pattern P = Pattern.compile("(\\d+\\.?\\d*|\\d*\\.?\\d+)");
			Matcher m = P.matcher(configValue);
			if (m.matches()) {
				hillShadingMagnitude = Double.parseDouble(m.group(1));
				logger.info("Hillshading magnitude: " + hillShadingMagnitude); //$NON-NLS-1$
			} else {
				logger.error("ERROR: hillshading magnitude '" + configValue + "' invalid!"); //$NON-NLS-1$
				System.exit(1);
			}
		}

		configValue = retrieveConfigValue("demfolder"); //$NON-NLS-1$
		if (configValue != null) {
			demFolder = new File(configValue);
			if (!demFolder.isDirectory()) {
				logger.error("ERROR: DEM folder '" + demFolder + "' does not exist!"); //$NON-NLS-1$
				System.exit(1);
			} else if (demFolder.listFiles().length == 0) {
				logger.error("ERROR: DEM folder '" + demFolder + "' is empty!"); //$NON-NLS-1$
				System.exit(1);
			} else {
				logger.info("DEM folder (digital elevation model): " + demFolder); //$NON-NLS-1$
			}
		}
		
		configValue = retrieveConfigValue("gamma-correction"); //$NON-NLS-1$
		if (configValue != null) {
			try {
				gammaValue = Double.parseDouble(configValue);
				if (gammaValue <= 0.) {
					logger.error("ERROR: gamma-correction not > 0.!"); //$NON-NLS-1$
					System.exit(1);
				} else {
					logger.info("Gamma correction: " + gammaValue); //$NON-NLS-1$
				}
			} catch (NumberFormatException e) {
				logger.error("ERROR: gamma-correction '" + configValue + "' invalid!"); //$NON-NLS-1$
				System.exit(1);
			}
		}
		
		configValue = retrieveConfigValue("contrast-stretch"); //$NON-NLS-1$
		if (configValue != null) {
			try {
				blackValue = Integer.parseInt(configValue);
				if (blackValue < 0 || blackValue > 254) {
					logger.error("ERROR: contrast-stretch not 0-254!"); //$NON-NLS-1$
					System.exit(1);
				} else {
					logger.info("Contrast-stretch: " + blackValue); //$NON-NLS-1$
				}
			} catch (NumberFormatException e) {
				logger.error("ERROR: contrast-stretch '" + configValue + "' invalid!"); //$NON-NLS-1$
				System.exit(1);
			}
		}
	}

	private String retrieveConfigValue(String key) {
		String configValue;
		if (configFile != null) {
			configValue = configFile.getProperty(key);
		} else {
			configValue = configCmd.getOptionValue(key);
		}
		if (configValue != null) configValue = configValue.trim();
		return configValue;
	}

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
		return this.serverConnectors;
	}
	
	public double getGammaValue() {
		return this.gammaValue;
	}

}
