package com.telemaxx.mapsforgesrv;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapsforgeConfig extends PropertiesParser{

	private CommandLine configCmd;

	private ArrayList<File> mapFiles = null;
	private File demFolder = null;
	private long cacheControl;
	private String rendererName = null;
	private String outOfRangeTms = null;
	private boolean appendWorldMap;
	private boolean acceptTerminate;
	private Map<String, MapsforgeStyleConfig> styles;
	private String configDirectory = null;
	private String preferredLanguage = null;

	private final static long DEFAULTCACHECONTROL = 0;
	private final static String[] AUTHORIZEDRENDERER = { "database", "direct", }; //$NON-NLS-1$ //$NON-NLS-2$
	private final static String DEFAULTRENDERER = AUTHORIZEDRENDERER[0];
	private final static String styleNameRegex = "^[0-9a-z._-]+$"; //$NON-NLS-1$
	private final static Pattern styleNameRegexPattern = Pattern.compile(styleNameRegex);

	/*
	 * enableInterpolationOverlap = true:  More precise at tile edges but much slower 
	 * enableInterpolationOverlap = false: Less precise at tile edges but much faster
	 */
	public final boolean HILLSHADINGENABLEINTERPOLATIONOVERLAP = true;
	public final int HILLSHADING_CACHE = 128; // default is 4
	public final int HILLSHADING_NEIGHBOR_CACHE= 8; // default is 4	

	public final String EXTENSIONDEFAULT = "png"; //$NON-NLS-1$
	public final int TILERENDERSIZEDEFAULT = 256;
	public final boolean TRANSPARENTDEFAULT = false;
	
	/* mandatory config files & directory */
	public final static String FILECONFIG_JETTY = "jetty.xml"; //$NON-NLS-1$
	public final static String FILECONFIG_SERVER = "server.properties"; //$NON-NLS-1$
	public final static String FILECONFIG_DEFAULTSTYLE = "default.properties"; //$NON-NLS-1$
	public final static String DIRCONFIG_STYLE = "styles/"; //$NON-NLS-1$

	// log response time
	public boolean LOG_RESP_TIME = true;

	private final static Logger logger = LoggerFactory.getLogger(MapsforgeConfig.class);

	public MapsforgeConfig(String[] args) throws Exception {
		initOption(args);
		initConfig();
	}

	/*
	 * OPTION
	 */
	private void initOption(String[] args) throws Exception {
		Options options = new Options();
		options.addOption(Option.builder("c") //$NON-NLS-1$
				.longOpt("config") //$NON-NLS-1$
				.desc("Config directory including at least "+FILECONFIG_SERVER+", "+FILECONFIG_JETTY+", "+DIRCONFIG_STYLE+FILECONFIG_DEFAULTSTYLE) //$NON-NLS-1$
				.required(true).hasArg(true).build());
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		formatter.setWidth(132);
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
			if (new File(config).isDirectory()) {
				configDirectory = config+"/";
				if (new File(configDirectory+FILECONFIG_SERVER).isFile()) {
					readConfig(configDirectory+FILECONFIG_SERVER);
				} else {
					logger.error("Server config file '"+configDirectory+FILECONFIG_SERVER+"' doesn't exist: exiting"); //$NON-NLS-1$
					System.exit(1);
				}
				if (!new File(configDirectory+FILECONFIG_JETTY).isFile()) {
					logger.error("Jetty config file '"+configDirectory+FILECONFIG_JETTY+"' doesn't exist: exiting"); //$NON-NLS-1$
					System.exit(1);
				}
				if (!new File(configDirectory+DIRCONFIG_STYLE+FILECONFIG_DEFAULTSTYLE).isFile()) {
					logger.error("Default style config file '"+configDirectory+DIRCONFIG_STYLE+FILECONFIG_DEFAULTSTYLE+"' doesn't exist: exiting"); //$NON-NLS-1$
					System.exit(1);
				}
			} else {
				logger.error("Config directory '"+config+"' set with -c is not a directory: exiting"); //$NON-NLS-1$
				System.exit(1);
			}
		} else {
			logger.error("Config directory not set with -c: exiting"); //$NON-NLS-1$
			System.exit(1);
		}
	}

	/*
	 * CONFIG
	 */

	private void initConfig() throws Exception {
		logger.info("################## SERVER CONFIG ##################");
		parseMapFiles();
		preferredLanguage = parseString(null, "language", null, "Preferred map language"); //$NON-NLS-1$ //$NON-NLS-2$
		demFolder = parseFile("demfolder", FOLDER, true, "DEM", "undefined");
		rendererName = parseString(DEFAULTRENDERER, "renderer", AUTHORIZEDRENDERER, "Renderer algorithm"); //$NON-NLS-1$ //$NON-NLS-2$
		cacheControl = (long) parseNumber(DEFAULTCACHECONTROL, "cache-control", 0, null, "Browser cache ttl",false); //$NON-NLS-1$ //$NON-NLS-2$
		outOfRangeTms = parseString(null, "outofrange_tms", null, "Out of range TMS url"); //$NON-NLS-1$ //$NON-NLS-2$
		appendWorldMap = parseHasOption("worldmap", "Append built-in world map");
		acceptTerminate = parseHasOption("terminate", "Accept terminate request");
		parseStyles();
	}
	
	/*
	 * PARSERS
	 */
	
	private void checkStyleName(String styleName) {
		if(!styleNameRegexPattern.matcher(styleName).find()) {
			logger.error("properties files for style '"+styleName+"' had not a correct name '"+styleNameRegex+"'"); //$NON-NLS-1$
			System.exit(-1);
		}
	}
	
	private void parseStyles() throws Exception {
		File[] styleFiles = new File(configDirectory+"/styles").listFiles();
		if(styleFiles.length == 0) {
			logger.error(configDirectory+"/styles doesn't contain any properties files"); //$NON-NLS-1$
			System.exit(-1);
		} else {
			styles = new HashMap<String, MapsforgeStyleConfig>();
			MapsforgeStyleConfig msc;
			for (File styleFile : styleFiles) { 
				String styleName = styleFile.getName().replaceFirst("[.][^.]+$", ""); //$NON-NLS-1$
				checkStyleName(styleName);
				msc = new MapsforgeStyleConfig(styleName, styleFile);
				styles.put(styleName, msc);
			}
		}
	}
	
	private void parseMapFiles() throws Exception {
		String msgHeader = parsePadMsg("Map file(s)"); //$NON-NLS-1$
		String mapFilePathsString = retrieveConfigValue("mapfiles"); //$NON-NLS-1$
		if (mapFilePathsString != null) {
			String[] mapFilePaths = mapFilePathsString.trim().split(","); //$NON-NLS-1$ //$NON-NLS-2$
			mapFiles = new ArrayList<File>();
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
		} else {
			logger.error(msgHeader + ": exiting - no file(s) specified"); //$NON-NLS-1$
			System.exit(1);
		}
	}

	/*
	 * GETTERS
	 */

	public String getConfigDirectory() {
		return configDirectory;
	}
	
	public File getDemFolder() {
		return this.demFolder;
	}

	public List<File> getMapFiles() {
		return this.mapFiles;
	}
	
	public boolean getAppendWorldMap() {
		return this.appendWorldMap;
	}
	
	public boolean getAcceptTerminate() {
		return this.acceptTerminate;
	}

	public long getCacheControl() {
		return this.cacheControl;
	}
	
	public String getOutOfRangeTms() {
		return this.outOfRangeTms;
	}
	
	public String getRendererName() {
		return this.rendererName;
	}
	
	public String getPreferredLanguage() {
		return this.preferredLanguage;
	}
	
	public Map<String, MapsforgeStyleConfig> getStyles() {
		return styles;
	}
	
	public MapsforgeStyleConfig getStyle(String style) throws Exception {
		try {
			return styles.get(style);
		} catch(Exception e) {
			throw new Exception("Style '"+style+"' don't exist");
		}
	}

	public MapsforgeStyleConfig getDefaultStyle() throws Exception {
		return getStyle("default");
	}

}
