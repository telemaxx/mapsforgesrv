package com.telemaxx.mapsforgesrv;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;

import static java.nio.file.StandardWatchEventKinds.*;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

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

	private long cacheControl;
	private String outOfRangeTms = null;
	private boolean acceptTerminate;
	private Map<String, MapsforgeTaskConfig> tasksConfig;
	private String configDirectory = null;
	private String requestLogFormat = null;
	
	public static BufferedImage BI_NOCONTENT;

	private final static String  taskFileNameRegex = "^[0-9a-z._-]+.properties$"; //$NON-NLS-1$
	
	private final static Logger logger = LoggerFactory.getLogger(MapsforgeConfig.class);

	public MapsforgeConfig(String[] args) throws Exception {
		// https://stackoverflow.com/questions/10235728/convert-bufferedimage-into-byte-without-i-o
		ImageIO.setUseCache(false);
		BI_NOCONTENT = ImageIO.read(getClass().getClassLoader().getResourceAsStream("assets/mapsforgesrv/no_content.png"));

		initOption(args);
		initConfig();
		
		watchConfig ();
	}

	/*
	 * OPTION
	 */
	private void initOption(String[] args) throws Exception {
		Options options = new Options();
		options.addOption(Option.builder("c") //$NON-NLS-1$
				.longOpt("config") //$NON-NLS-1$
				.desc("Config directory including at least "+FILECONFIG_SERVER+", "+FILECONFIG_JETTY+", "+FILECONFIG_JETTY_THREADPOOL+", "+DIRCONFIG_TASK+FILECONFIG_DEFAULTTASK) //$NON-NLS-1$
				.required(false).hasArg(true).build());
		options.addOption(Option.builder("h") //$NON-NLS-1$
				.longOpt("help") //$NON-NLS-1$
				.desc("Print this help text and exit") //$NON-NLS-1$
				.required(false).hasArg(false).build());
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
			config = config.trim();
			if (new File(config).isDirectory()) {
				configDirectory = config+System.getProperty("file.separator");
				String[] configFiles = {FILECONFIG_SERVER, FILECONFIG_JETTY, FILECONFIG_JETTY_THREADPOOL, DIRCONFIG_TASK+FILECONFIG_DEFAULTTASK};  
				for (String configFile : configFiles) {
					if (!new File(configDirectory+configFile).isFile()) {
						logger.error("Default task config file '"+configDirectory+configFile+"' doesn't exist: exiting"); //$NON-NLS-1$
						System.exit(1);
					}
				}
				readConfig(configDirectory+FILECONFIG_SERVER);
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
		cacheControl = (long) parseNumber(DEFAULT_CACHECONTROL, "cache-control", 0, null, "Browser cache ttl",false); //$NON-NLS-1$ //$NON-NLS-2$
		outOfRangeTms = parseString(null, "outofrange_tms", null, "Out of range TMS url"); //$NON-NLS-1$ //$NON-NLS-2$
		acceptTerminate = parseHasOption("terminate", "Accept terminate request");
		requestLogFormat = parseString("%{client}a - %u %t '%r' %s %O '%{Referer}i' '%{User-Agent}i' '%C'", "requestlog-format", null, "Request log format"); //$NON-NLS-1$ //$NON-NLS-2$
		parseTasks();
	}
	
	/*
	 * PARSERS
	 */
	
	private void parseTasks() throws Exception {
		FilenameFilter filenameFilter = new FilenameFilter() {
			    public boolean accept(File dir, String name) {
			    	return name.matches(taskFileNameRegex);
			    }};
		File[] taskFiles = new File(configDirectory+"/tasks").listFiles(filenameFilter);
		if(taskFiles.length == 0) {
			logger.error(configDirectory+"/tasks doesn't contain any properties files named "+taskFileNameRegex); //$NON-NLS-1$
			System.exit(-1);
		} else {
			tasksConfig = new HashMap<String, MapsforgeTaskConfig>();
			MapsforgeTaskConfig msc;
			for (File taskFile : taskFiles) { 
				String taskFileName = taskFile.getName();
				String taskName = taskFileName.replaceFirst("[.][^.]+$", ""); //$NON-NLS-1$
				msc = new MapsforgeTaskConfig(taskName, taskFile);
				tasksConfig.put(taskName, msc);
			}
		}
	}
	
	private void watchConfig () throws Exception {
		Thread watchConfigThread = new Thread(null, new Runnable() {
			@Override
			public void run() {
				Path configPath = Paths.get(configDirectory+"/tasks");
				try {
					WatchService watchService = FileSystems.getDefault().newWatchService();
					configPath.register(watchService,
				                           ENTRY_CREATE,
				                           ENTRY_DELETE,
				                           ENTRY_MODIFY);
					boolean poll = true;
					while (poll) {
						WatchKey key = watchService.take();
						for (WatchEvent<?> event : key.pollEvents()) {
							Path pathName = (Path) event.context();
							String fileName = pathName.toString();
							if (!Pattern.matches(taskFileNameRegex,fileName)) continue;
							MapsforgeHandler mapsforgeHandler = MapsforgeSrv.getMapsforgeHandler();
							if (mapsforgeHandler == null) continue;
							String taskName = fileName.replaceFirst("[.][^.]+$", "");
							boolean taskExist = (mapsforgeHandler.getTasksHandler().get(taskName) != null);
							System.out.println("Task exist: " + taskExist);
							if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
								System.out.println("File created: " + fileName);
								// If task does not exist, create new task config and task handler
							} else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
								System.out.println("File deleted: " + fileName);
								// If task does exist, delete task handler and config
							} else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
								System.out.println("File modified: " + fileName);
								// If task does exist, delete task handler and config
							}
						}
						poll = key.reset();
					}
				} catch (Exception e) {}
			}
		}, "watchConfig");
		watchConfigThread.start();
	}

	/*
	 * GETTERS
	 */

	public String getConfigDirectory() {
		return configDirectory;
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

	public String getRequestLogFormat() {
		return requestLogFormat;
	}

	public Map<String, MapsforgeTaskConfig> getTasksConfig() {
		return tasksConfig;
	}

	public MapsforgeTaskConfig getTaskConfig(String task) throws Exception {
		try {
			return tasksConfig.get(task);
		} catch(Exception e) {
			throw new Exception("Task '"+task+"' don't exist");
		}
	}

	public MapsforgeTaskConfig getDefaultConfig() throws Exception {
		return getTaskConfig("default");
	}

}
