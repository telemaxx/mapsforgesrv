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
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 * 
 * changelog:
 * 0.13: selectable style
 * 0.13.1: selectable overlays
 * 0.16.0: mapsforge 16
 * 0.16.1: contrast stretching (JFBeck)
 * 0.16.2: Check for valid tile numbers (JFBeck)
 * 0.16.3: hillshading (JFBeck)
 *******************************************************************************/

package com.telemaxx.mapsforgesrv;

import java.io.File;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.*;
import org.eclipse.jetty.server.Server;

public class MapsforgeSrv {

	public static void main(String[] args) throws Exception {
		final String VERSION = "0.16.3"; //starting with eg 0.13, the mapsforge version //$NON-NLS-1$
		System.out.println("MapsforgeSrv - a mapsforge tile server. " + "version: " + VERSION); //$NON-NLS-1$ //$NON-NLS-2$

		String rendererName = null;
		String[] mapFilePaths = null;
		String themeFilePath = null;
		String themeFileStyle = null;
		String optionValue = null;
		String[] themeFileOverlays = null;
		String preferredLanguage = null;
		String contrastStretch = null;
		String hillShadingOption = null;
		String demFolderPath = null;
		final int DEFAULTPORT = 8080; 
		String portNumberString = "" + DEFAULTPORT; //$NON-NLS-1$

		Options options = new Options();
		
		Option rendererArgument = new Option("r", "renderer", true, "mapsforge renderer [database,direct] (default: database)"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		rendererArgument.setRequired(false);
		options.addOption(rendererArgument);

		Option mapfileArgument = new Option("m", "mapfiles", true, "comma-separated list of mapsforge map files (.map)"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		mapfileArgument.setRequired(true);
		options.addOption(mapfileArgument);

		Option themefileArgument = new Option("t", "themefile", true, "mapsforge theme file(.xml), (default: the internal OSMARENDER)"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		themefileArgument.setRequired(false);
		options.addOption(themefileArgument);
		
		Option themefileStyleArgument = new Option("s", "style", true, "style of the theme file(.xml), (default: default defined in xml file)"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		themefileStyleArgument.setRequired(false);
		options.addOption(themefileStyleArgument);	
		
		Option themefileOverlayArgument = new Option("o", "overlays", true, "comma-separated list of style\'s overlay ids of the theme file(.xml), (default: overlays enabled in xml file)"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		themefileOverlayArgument.setRequired(false);
		options.addOption(themefileOverlayArgument);

		Option languageArgument = new Option("l", "language", true, "preferred language (default: native language)"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		languageArgument.setRequired(false);
		options.addOption(languageArgument);

		Option hillShadingAlgorithmArgument = new Option("hs", "hillshading-algorithm", true, "simple or simple(angle) or diffuselight or diffuselight(linearity,scale), (default: no hillshading)"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		hillShadingAlgorithmArgument.setRequired(false);
		options.addOption(hillShadingAlgorithmArgument);

		Option hillShadingMagnitudeArgument = new Option("hm", "hillshading-magnitude", true, "scaling factor >= 0 (default: 1.)"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		hillShadingMagnitudeArgument.setRequired(false);
		options.addOption(hillShadingMagnitudeArgument);

		Option demFolderArgument = new Option("d", "demfolder", true, "folder containing .hgt digital elevation model files"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		demFolderArgument.setRequired(false);
		options.addOption(demFolderArgument);

		Option contrastArgument = new Option("cs", "contrast-stretch", true, "stretch contrast within range 0..254 (default: 0)"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		contrastArgument.setRequired(false);
		options.addOption(contrastArgument);

		Option portArgument = new Option("p", "port", true, "port, where the server is listening (default: " + DEFAULTPORT + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		portArgument.setRequired(false);
		options.addOption(portArgument);
		
		Option interfaceArgument = new Option("if", "interface", true, "which interface listening [all,localhost] (default: localhost)"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		interfaceArgument.setRequired(false);
		options.addOption(interfaceArgument);
					
		Option helpArgument = new Option("h", "help", false, "print this help text and exit"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		helpArgument.setRequired(false);
		options.addOption(helpArgument);		

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd = null;      

		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("mapsforgesrv", options); //$NON-NLS-1$
			System.exit(1);
			//System.exit(0);
		}

		if(cmd.hasOption("help")) { //$NON-NLS-1$
			formatter.printHelp("mapsforgesrv", options); //$NON-NLS-1$
			System.exit(0);
		}
		
		int portNumber = DEFAULTPORT;
		portNumberString = cmd.getOptionValue("port"); //$NON-NLS-1$
		if (portNumberString != null) {
			try {
				portNumberString = portNumberString.trim();
				//System.out.println("portString" + portNumberString);
				portNumber = Integer.parseInt(portNumberString);
				if (portNumber < 1024 || portNumber > 65535) {
					portNumber = DEFAULTPORT;
					System.out.println("ERROR: portnumber not 1024-65535!"); //$NON-NLS-1$
					System.exit(1);
				} else {
					System.out.println("Using port: " + portNumber); //$NON-NLS-1$
				}
			} catch (NumberFormatException e){
				portNumber = DEFAULTPORT;
				System.out.println("couldnt parse portnumber, using " + DEFAULTPORT); //$NON-NLS-1$
				//e.printStackTrace();
			}
		} else {
			System.out.println("no port given, using " + DEFAULTPORT); //$NON-NLS-1$
		}

		rendererName = cmd.getOptionValue("renderer"); //$NON-NLS-1$
		if (rendererName != null) {
			rendererName = rendererName.trim().toLowerCase();
			System.out.println("Renderer: " + rendererName); //$NON-NLS-1$
			if ((!rendererName.equals("database")) && (!rendererName.equals("direct"))) {
				System.out.println("ERROR: unknown renderer!"); //$NON-NLS-1$
				System.exit(1);
			}
		} else {
			rendererName = "database";
		}
		
		mapFilePaths = cmd.getOptionValue("mapfiles").trim().split(","); //$NON-NLS-1$ //$NON-NLS-2$
		ArrayList<File> mapFiles = new ArrayList<>();
		for (String path : mapFilePaths) {
			mapFiles.add(new File(path));
		}
		mapFiles.forEach(mapFile -> {
			System.out.println("Map file: " + mapFile); //$NON-NLS-1$
			if (!mapFile.isFile()) {
				System.err.println("ERROR: Map file does not exist!"); //$NON-NLS-1$
				System.exit(1);
			}
		});

		themeFilePath = cmd.getOptionValue("themefile"); //$NON-NLS-1$
		if (themeFilePath != null) {
			themeFilePath = themeFilePath.trim();
		}
		File themeFile = null;
		if (themeFilePath != null) {	
			themeFile = new File(themeFilePath);
			System.out.println("Theme file: " + themeFile); //$NON-NLS-1$
			if (!themeFile.isFile()) {
				System.err.println("ERROR: theme file does not exist!"); //$NON-NLS-1$
				System.exit(1);
			}
		} else {
			System.out.println("Theme: OSMARENDER"); //$NON-NLS-1$
		}
		
		themeFileStyle = cmd.getOptionValue("style"); //$NON-NLS-1$
		if (themeFileStyle != null) {
			themeFileStyle = themeFileStyle.trim();
			System.out.println("Selected ThemeStyle: " + themeFileStyle); //$NON-NLS-1$
		}
		
		optionValue = cmd.getOptionValue("overlays"); //$NON-NLS-1$
		if (optionValue != null) {
			themeFileOverlays = optionValue.trim().split(","); //$NON-NLS-1$
			for (int i = 0; i < themeFileOverlays.length; i++) {
				themeFileOverlays[i] = themeFileOverlays[i].trim();
				System.out.println("Selected ThemeOverlay: " + themeFileOverlays[i]); //$NON-NLS-1
			}
		}
		
		preferredLanguage = cmd.getOptionValue("language"); //$NON-NLS-1$
		if (preferredLanguage != null) {
			System.out.println("Preferred map language: " + preferredLanguage); //$NON-NLS-1$
		}
		
		String hillShadingAlgorithm = null;
		double[] hillShadingArguments = null;
		hillShadingOption = cmd.getOptionValue("hillshading-algorithm"); //$NON-NLS-1$
		if (hillShadingOption != null) {
			hillShadingOption = hillShadingOption.trim();
			Pattern P = Pattern.compile("(simple)(?:\\((\\d*\\.?\\d*),(\\d*\\.?\\d*)\\))?|(diffuselight)(?:\\((\\d*\\.?\\d*)\\))?");
			Matcher m = P.matcher(hillShadingOption);
			if (m.matches()) {
				if (m.group(1) != null) {
					hillShadingAlgorithm = new String(m.group(1));	// ShadingAlgorithm = simple
					hillShadingArguments = new double[2];
					if (m.group(2) != null) {
						hillShadingArguments[0] = Double.parseDouble(m.group(2));
						hillShadingArguments[1] = Double.parseDouble(m.group(3));
					} else {										// default values
						hillShadingArguments[0] = 0.1;
						hillShadingArguments[1] = 0.666;
					}
					System.out.println("Hillshading algorithm: " + hillShadingAlgorithm + "(" + hillShadingArguments[0] + 
							"," + hillShadingArguments[1] + ")"); //$NON-NLS-1$
				} else {
					hillShadingAlgorithm = new String(m.group(4));	// ShadingAlgorithm = diffuselight
					hillShadingArguments = new double[1];
					if (m.group(5) != null) {
						hillShadingArguments[0] = Double.parseDouble(m.group(5));
					} else {										// default value
						hillShadingArguments[0] = 50.;
					}
					System.out.println("Hillshading algorithm: " + hillShadingAlgorithm + "(" + hillShadingArguments[0] + ")"); //$NON-NLS-1$
				}
			} else {
				System.out.println("ERROR: hillshading algorithm '" + hillShadingOption + "' invalid!"); //$NON-NLS-1$
				System.exit(1);
			}
		}
		
		double hillShadingMagnitude = 1.;
		hillShadingOption = cmd.getOptionValue("hillshading-magnitude"); //$NON-NLS-1$
		if (hillShadingOption != null) {
			hillShadingOption = hillShadingOption.trim();
			Pattern P = Pattern.compile("(\\d*\\.?\\d*)");
			Matcher m = P.matcher(hillShadingOption);
			if (m.matches()) {
				hillShadingMagnitude = Double.parseDouble(m.group(1));
				System.out.println("Hillshading magnitude: " + hillShadingMagnitude); //$NON-NLS-1$
			} else {
				System.out.println("ERROR: hillshading magnitude '" + hillShadingOption + "' invalid!"); //$NON-NLS-1$
				System.exit(1);
			}
		}
		
		File demFolder = null;
		demFolderPath = cmd.getOptionValue("demfolder"); //$NON-NLS-1$
		if (demFolderPath != null) {
			demFolderPath = demFolderPath.trim();
			demFolder = new File(demFolderPath);
			if (!demFolder.isDirectory()) {
				System.err.println("ERROR: DEM folder '" + demFolder + "' does not exist!"); //$NON-NLS-1$
				System.exit(1);
			} else if (demFolder.listFiles().length == 0) {
				System.err.println("ERROR: DEM folder '" + demFolder + "' is empty!"); //$NON-NLS-1$
				System.exit(1);
			} else {
				System.out.println("DEM folder (digital elevation model): " + demFolder); //$NON-NLS-1$	
			}
		}
		
	
		int blackValue = 0;
		contrastStretch = cmd.getOptionValue("contrast-stretch"); //$NON-NLS-1$
		if (contrastStretch != null) {
			contrastStretch = contrastStretch.trim();
			try {
				blackValue = Integer.parseInt(contrastStretch);
				if (blackValue < 0 || blackValue > 254) {
					System.out.println("ERROR: contrast-stretch not 0-254!"); //$NON-NLS-1$
					System.exit(1);
				} else {
					System.out.println("Contrast-stretch: " + blackValue); //$NON-NLS-1$
				}
			} catch (NumberFormatException e){
				System.out.println("ERROR: contrast-stretch '" + contrastStretch + "' invalid!"); //$NON-NLS-1$
				System.exit(1);
			}
		}
		
		MapsforgeHandler mapsforgeHandler = new MapsforgeHandler(rendererName, mapFiles, themeFile, themeFileStyle, themeFileOverlays,
				preferredLanguage, hillShadingAlgorithm, hillShadingArguments, hillShadingMagnitude, demFolder, blackValue);
		
		Server server = null;
		String listeningInterface = cmd.getOptionValue("interface"); //$NON-NLS-1$
		if (listeningInterface != null) {
			listeningInterface = listeningInterface.trim();
			if (listeningInterface.toLowerCase().equals("all")) { //$NON-NLS-1$
				System.out.println("listening on all interfaces, port:" + portNumber); //$NON-NLS-1$
				server = new Server(portNumber);
			} else if (listeningInterface.toLowerCase().equals("localhost")) { //$NON-NLS-1$
				//listeningInterface = "localhost";
				System.out.println("listening on localhost port:" + portNumber); //$NON-NLS-1$
				server = new Server(InetSocketAddress.createUnresolved("localhost", portNumber)); //$NON-NLS-1$
			} else {
				System.out.println("unkown Interface, only \"all\" or \"localhost\" , not " + listeningInterface ); //$NON-NLS-1$
				System.exit(1);	
			}
		} else {
			System.out.println("listening on localhost port:" + portNumber); //$NON-NLS-1$
			server = new Server(InetSocketAddress.createUnresolved("localhost", portNumber)); //$NON-NLS-1$
		}

		server.setHandler(mapsforgeHandler);
		try {
			server.start();
		} catch (BindException e) {
			System.out.println(e.getMessage());
			System.out.println("Stopping server"); //$NON-NLS-1$
			System.exit(1);
		}
		server.join();
	}

}