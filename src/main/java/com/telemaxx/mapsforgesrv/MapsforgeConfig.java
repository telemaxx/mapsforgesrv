package com.telemaxx.mapsforgesrv;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;

import static java.nio.file.StandardWatchEventKinds.*;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
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
	private String taskDirectory = null;
	private String requestLogFormat = null;

	public static BufferedImage BI_NOCONTENT;

	private final static String  taskFileNameRegex = "^[0-9a-z._-]+.properties$"; //$NON-NLS-1$

	private final static Logger logger = LoggerFactory.getLogger(MapsforgeConfig.class);

	public MapsforgeConfig(String[] args) throws Exception {
		// https://stackoverflow.com/questions/10235728/convert-bufferedimage-into-byte-without-i-o
		ImageIO.setUseCache(false);
		BI_NOCONTENT = ImageIO.read(getClass().getClassLoader().getResourceAsStream("assets/mapsforgesrv/no_content.png"));

		initOptions(args);
		initConfig();

		watchConfig ();
	}

	/*
	 * OPTION
	 */
	private void initOptions(String[] args) throws Exception {
		Options options = new Options();
		options.addOption(Option.builder("c") //$NON-NLS-1$
				.longOpt("config") //$NON-NLS-1$
				.desc("Config directory including at least "+FILECONFIG_SERVER+", "+FILECONFIG_JETTY+", "+FILECONFIG_JETTY_THREADPOOL+", "+DIRCONFIG_TASKS+FILECONFIG_DEFAULTTASK) //$NON-NLS-1$
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
				String[] configFiles = {FILECONFIG_SERVER, FILECONFIG_JETTY, FILECONFIG_JETTY_THREADPOOL, DIRCONFIG_TASKS+FILECONFIG_DEFAULTTASK};
				for (String configFile : configFiles) {
					if (!new File(configDirectory+configFile).isFile()) {
						logger.error("Default task config file '"+configDirectory+configFile+"' doesn't exist: exiting"); //$NON-NLS-1$
						System.exit(1);
					}
				}
				readConfig(new File(configDirectory+FILECONFIG_SERVER));
				taskDirectory = configDirectory+DIRCONFIG_TASKS;
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
		File[] taskFiles = new File(taskDirectory).listFiles(filenameFilter);
		if(taskFiles.length == 0) {
			logger.error(taskDirectory+" doesn't contain any properties files named "+taskFileNameRegex); //$NON-NLS-1$
			System.exit(-1);
		} else {
			tasksConfig = new HashMap<String, MapsforgeTaskConfig>();
			MapsforgeTaskConfig mapsforgeTaskConfig;
			for (File taskFile : taskFiles) {
				String taskFileName = taskFile.getName();
				String taskName = taskFileName.replaceFirst("[.][^.]+$", ""); //$NON-NLS-1$
				mapsforgeTaskConfig = new MapsforgeTaskConfig(taskName, taskFile);
				tasksConfig.put(taskName, mapsforgeTaskConfig);
			}
		}
	}

	/*
	 * CONFIG TASKS WATCHER
	 */

	private void watchConfig () throws Exception {
		Thread watchConfigThread = new Thread(null, new Runnable() {
			@Override
			public void run() {
				Path configPath = Path.of(taskDirectory);
				try {
					WatchService watchService = FileSystems.getDefault().newWatchService();
					configPath.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
					boolean poll = true;
					while (poll) {
						WatchKey key = watchService.take();
						for (WatchEvent<?> event : key.pollEvents()) {
							Path pathName = (Path) event.context();
							String fileName = pathName.toString();
							if (!Pattern.matches(taskFileNameRegex,fileName)) continue;
							MapsforgeHandler mapsforgeHandler = MapsforgeSrv.getMapsforgeHandler();
							if (mapsforgeHandler == null) continue;
							Map<String, MapsforgeTaskHandler> tasksHandler = mapsforgeHandler.getTasksHandler();
							String taskName = fileName.replaceFirst("[.][^.]+$", "");
							boolean taskExists = tasksHandler.get(taskName) != null;
							if (event.kind() == ENTRY_CREATE) {
								logger.info("New task properties created: " + fileName);
								// If task does not exist, create new task config and task handler
								if (!taskExists) {
									File taskFile = new File(taskDirectory,fileName);
									tasksConfig.put(taskName, new MapsforgeTaskConfig(taskName, taskFile));
									tasksHandler.put(taskName, new MapsforgeTaskHandler(MapsforgeSrv.getMapsforgeHandler(), tasksConfig.get(taskName), taskName));
								}
							} else if (event.kind() == ENTRY_DELETE) {
								logger.info("Existing task properties deleted: " + fileName);
								// If task does exist, delete task handler and config
								if (taskExists) {
									tasksHandler.remove(taskName);
									tasksConfig.remove(taskName);
								}
							} else if (event.kind() == ENTRY_MODIFY) {
								logger.info("Existing task properties modified: " + fileName);
								// If task does exist and properties have been changed, delete task handler and config
								if (taskExists) {
									File taskFile = new File(taskDirectory,fileName);
									String newCheckSum = checkSum(Files.readAllBytes(taskFile.toPath()));
									String oldCheckSum = tasksConfig.get(taskName).getCheckSum();
									if (!newCheckSum.equals(oldCheckSum)) {
										logger.info("Existing task properties modified: " + fileName);
										tasksConfig.remove(taskName);
										tasksConfig.put(taskName, new MapsforgeTaskConfig(taskName, taskFile));
										tasksHandler.put(taskName, new MapsforgeTaskHandler(MapsforgeSrv.getMapsforgeHandler(), tasksConfig.get(taskName), taskName));
									}
								}
							}
						}
						poll = key.reset();
					}
					logger.error("Config directory "+configPath+" no longer watchable: exiting"); //$NON-NLS-1$
					System.exit(1);
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
