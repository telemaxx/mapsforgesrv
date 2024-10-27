# mapsforgesrv

### mapsforgesrv cloned from the MOBAC project:
http://mobac.sourceforge.net/

The MapsforgeSrv is a local webserver providing rendered mapsforge map tiles.  
http://wiki.openstreetmap.org/wiki/Mapsforge  
The tiles are always rendered on the fly when requested.

The description of this tool you find at the origin page:  
https://sourceforge.net/p/mobac/code/HEAD/tree/trunk/tools/MapsforgeSrv/

An example how to use the tile server on client side, you find eg at qmapshack project:  
https://github.com/Maproom/qmapshack/wiki/DocBasicsMapDem#mapsforge-maps  

Graphical user interface *Mapsforge-for-QMapShack* between MapsforgeSrv and QMapShack application:  
https://github.com/JFritzle/Mapsforge-for-QMapShack  
Graphical user interface *Mapsforge-for-MyTourbook* between MapsforgeSrv and MyTourbook application:  
https://github.com/JFritzle/Mapsforge-for-MyTourbook  
Graphical user interface *Mapsforge-to-Tiles* to render tiles with MapsforgeSrv without map application:  
https://github.com/JFritzle/Mapsforge-to-Tiles   

The releases can now be found on the Release Page:
https://github.com/telemaxx/mapsforgesrv/releases
~~The ready2use folder is now located in the bin folder:~~
~~git\mapsforgesrv\mapsforgesrv\bin~~
Only one jar containing everything you need.

	1. mapsforgesrv-fatjar.jar (branch "master") developed and built with java version 11, needs java version 11 or higher to run
	2. mapsforgesrv4java8.jar (branch "java8") developed and built with java version 8, should run on all java starting from version 8

Whats different to the origin?

	1. mapsforge libs updated from 0.6.0 to 0.21.0
	2. updates all other libs to latest versions
	3. build system "gradle" for easier libs management.
	4. new command line interface: -m mapfile(s) -t themefile(optional) -l language(optional) -s themestyle(optional)
	5. tcp port selection(optional), eg -p 8081
	6. interface selection(optional): -if [all,localhost]
	7. language selection(optional): -l en
	8. selectable style (optional): -s elmt-mtb
	9. selectable overlays (optional): -o "elmt-mtbs_tracks,elmt-mtb_routes"
	10. selectable renderer (optional): -r [database,direct]
	11. selectable contrast-stretch (optional): -cs [0..254]
	12. selectable gamma correction (optional): -gc value
	13. selectable hillshading algorithm (optional): -hs [simple,simple(linearity,scale),diffuselight,diffuselight(angle)]
	14. selectable hillshading magnitude (optional): -hm factor
	15. selectable DEM folder (optional): -d demfolder
	16. selectable device scale (optional): -sfd factor
	17. selectable user scale (optional): -sfu factor
	18. selectable text scale (optional): -sft factor
	19. selectable symbol scale (optional): -sfs factor
	20. selectable line scale (optional): -sfl factor


Command parameters:

	1.  -p   [port]				TCP port to listen on (default:  8080 is used)
	2.  -if  [interface]			Interface to listen on [all,localhost] (default: localhost)
						With "all" its useful to run on a server. raspberry runs nice.
	3.  -m   [mapfiles]			Path to the mapfile(s). at least one file is mandatory. comma-separated list of mapsforge map files (.map)
	4.  -t   [themefile]			Path to the themefile (default: internal theme)
	5.  -s   [style]			When using a themefile, selecting the style, eg "elmt-hiking". (default: themefile's default style)
	6.  -o   [overlays]			When using a themefile, enable only overlays of this comma-separated list. override enable attributes inside the themefile (default: default theme overlays)
	7.  -l   [language]			Preferred language if supported by map file (ISO 639-1 or ISO 639-2 if an ISO 639-1 code doesn't exist)
	8.  -cs  [contrast-stretch]		Stretch contrast [0..254] (default: 0)
	9.  -gc  [gamma-correction] 		Gamma correction value [> 0] (default: 1)
	10. -hs  [hillshading-algorithm]	Hillshading algorithm and optional parameters [simple,simple(linearity,scale),diffuselight,diffuselight(angle)] (default: no hillshading)
						Parameter defaults: linearity=0.1, scale=0.666, angle=50.
						Note: Hillshading requires to be enabled in themefile too!
	11. -hm  [hillshading-magnitude]	Hillshading gray value scaling factor [>= 0] (default: 1))
	12. -d   [demfolder]			Folder path containing digital elevation model files [.hgt] for hillshading (default: none)
	13. -r   [renderer]			Mapsforge renderer algorithm [database,direct] (default: database)
						Sometimes "direct" giving better results	
	14. -sfd [device-scale]			Device scale factor [> 0] (default: 1)
	15. -sfu [user-scale]			User scale factor [> 0] (default: 1)
	16. -sft [text-scale]			Text scale factor [> 0] (default: 1)
	17. -sfs [symbol-scale]			Symbol scale factor [> 0] (default: 1)
	18. -sfl [line-scale]			Line scale factor [> 0] (default: 1)
	19. -cc  [cache-control]		If set, add Cache-Control header for served tiles. value in seconds, (default: 0 - disabled)
	20. -mxq [max-queuesize]		Maximum queue size for waiting & running rendering jobs (default: 256)
	21. -mxt [max-thread]			Maximum concurrent threads for rendering job (default: 8)
	22. -mit [min-thread]			Minimum pool size for rendering job (default: 0)
	23. -idl [idle-timeout]			Maximum thread idle time in milliseconds (default: 0 - disabled)
	24. -ct  [connectors]			Comma-separated list of enabled server connector protocol(s) [http11,proxy,h2c] (default: http11)
	25. -tms [outofrange_tms]		Url pattern [ex. https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png] of an external TMS server used in redirect for out-of-range tiles (default: unset - disabled)
	26. -wm  [worldmap]			Append built-in world map (default: undefined)
	27. -c   [config]			Config file overriding cmd line parameters (default: none)
	28. -h   [help]				Print the help text and exit 


Example:  
```console
java -jar mapsforgesrv/bin/jars_ready2use/mapsforgesrv4java8.jar -p 8080 -if all
     -m "path2mapfile1.map, path2mapfile2.map" -t path2themefile.xml -l en -r "direct" 
     -s "elmt-hiking" -o "elmt-mtbs_tracks,elmt-mtb_routes,elmt-mtb_c_routes"
     -cs 32 -gc 0.8 -hs simple(0.1,0.666) -hm 1.2 -d path2demfolder
```

Example MapsforgeSrv working as hillshading only tile server:  
```console
java -jar mapsforgesrv/bin/jars_ready2use/mapsforgesrv4java8.jar -p 8081 -if all
     -m "" -hs simple(0.1,0.666) -hm 1.2 -d path2demfolder
```  
Hillshading tiles with alpha transparency may be used as overlay for any map.

Branches:

	1. "java8", when an old java 8 is installed, this branch is to be used for the development.
	2. "master", this branch is for development with java version 11.
 	3. "styles", this branch is working with config files instead of endless long commandline parameters. Attention a different Readme in that branch.

Building the jar:

	There are some gradle tasks. Building the jar is done by:
	"copyFatJar2jars_ready2use" builds the jar and copies to "$buildDir/../bin/jars_ready2use/" (master and java8 branch)
 	"shadowDistTar" builds the jar in the local "dist" folder (styles branch). The dist folder i set to "Ignore" on github to keep the repro small.

-------------
### Contributors
- r_x created this tile server as part of mobac project https://sourceforge.net/projects/mobac/
- Thomas Th. @telemaxx: converted the mapserver server part in own git project with gradle nature.
- @pingurus (fixing stylesheets error)
- Bernd @bjmdev (multi map support)
- @JFritzle (selectable theme style, overlays, renderer, contrast-stretch, hillshading, gamma correction,    
  some rendering scale factors, hillshading overlay with alpha transparency)
- @nono303 a lot of improvements/rework

### Licenses
- MapsforgeSrv  
MapsforgeSrv is licensed under the GNU General Public License v3.0. 
- Built-in Mapsforge world map  
All map data © OpenStreetMap contributors http://www.openstreetmap.org/copyright.   
OpenStreetMap is open data, licensed under the Open Data Commons Open Database License (ODbL).
- Included Marlin renderer engine   
[Marlin](https://github.com/bourgesl/marlin-renderer) is a fork from OpenJDK 8 and therefore licensed under the GNU General Public License, version 2, with the Classpath Exception.
