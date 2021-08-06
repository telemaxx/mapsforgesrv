# mapsforgesrv

### mapsforgesrv cloned from the MOBAC project:
http://mobac.sourceforge.net/

The MapsforgeSrv is a local webserver providing rendered mapsforge map tiles.<br/>
http://wiki.openstreetmap.org/wiki/Mapsforge<br/>
The tiles are always rendered on the fly when requested

The description of this tool you find at the origin page:<br/>
https://sourceforge.net/p/mobac/code/HEAD/tree/trunk/tools/MapsforgeSrv/

An example how to use the tile server on client side, you find eg at qmapshack project:<br/>
https://github.com/Maproom/qmapshack/wiki/DocBasicsMapDem#mapsforge-maps
Graphical user interface *Mapsforge-for-QMapShack* between MapsforgeSrv and QMapShack application:<br/>
https://github.com/JFritzle/Mapsforge-for-QMapShack

The ready2use folder is now located in the bin folder:<br/>
git\mapsforgesrv\mapsforgesrv\bin<br/>
Only one jar containing everything you need.

	1. mapsforgesrv-fatjar.jar (branch "master") developed with java version 11, needs java 11 to run
	2. mapsforgesrv4java8.jar (branch "java8") developed with java version 8, should run on all java starting version 8

Whats different to the origin?

	1. mapsforge libs updated from 0.6.0 to 0.16.0
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
	

Command parameters:

	1. -m  path to the mapfile(s). at least one file is mandatory. comma-separated list of mapsforge map files (.map)
	2. -t  path to the themefile. this is optional. without, the internal theme is used
	3. -p  tcp port to listen on. this is optional. without, port 8080 is used
	4. -if interface to listen on. this is optional. without, localhost is used. possibilities "-if all" or "-if localhost"
		with "-if all" its useful to run on a server. raspberry runs nice.
	5. -l  preferred language if supported by map file (ISO 639-1 or ISO 639-2 if an ISO 639-1 code doesn't exist)
    6. -s  when using a themefile, selecting the style, eg "elmt-hiking". (Default: themefile's default style)
    7. -o  when using a themefile, enable only overlays of this comma-separated list. override enable attributes inside the themefile.
    8. -r  mapsforge renderer [database,direct] (default: database). sometimes "direct" giving better results
    9. -cs contrast-stretch. stretch contrast within range 0..254 (default: 0)
    10. -h  print the help text and terminate 
    

longest example:
```console
java -jar mapsforgesrv/bin/jars_ready2use/mapsforgesrv-fatjar.jar -m "path2mapfile1.map, path2mapfile2.map" -t path2themefile.xml -p 8080 -if all -l EN -s "elmt-hiking" -r "direct" -o elmt-mtbs_tracks,elmt-mtb_routes,elmt-mtb_c_routes" -cs 32
```

Branches:

	1. "java8", when an old java 8 is installed, this branch is to be used for the development.
	2. "master", this version is for development with java 11.
	
Building the jar:

	there are some gradle task. building the jar is done by:
	"copyFatJar2jars_ready2use" builds the jar and copying it to "$buildDir/../bin/jars_ready2use/"

-------------
### Contributors
- r_x created this tile server as part of mobac project https://sourceforge.net/projects/mobac/
- Thomas Th. @telemaxx: converted the mapserver server part in own git project with gradle nature.
- @pingurus (fixing stylesheets error)
- Bernd @bjmdev (multi map support)
- @JFritzle (selectable theme style, overlays, renderer & contrast-stretch)

