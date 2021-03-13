# mapsforgesrv

### mapsforgesrv cloned from the MOBAC project:
http://mobac.sourceforge.net/

The MapsforgeSrv is a local webserver providing rendered mapsforge map tiles.
http://wiki.openstreetmap.org/wiki/Mapsforge
The tiles are rendered every time on-the fly when requested

The description of this tool you find at the orign page:
https://sourceforge.net/p/mobac/code/HEAD/tree/trunk/tools/MapsforgeSrv/

An example how to use the tile server on client side, you find eg at qmapshack project:
https://github.com/Maproom/qmapshack/wiki/DocBasicsMapDem#mapsforge-maps

Whats different to the orign?

	1. mapsforge libs updated from 0.6.0 to 0.15.0
	2. updates all other libs to latest versions
	3. build system "gradle" for easier libs management.
	4. new command line interface: -m mapfile(s) -t themefile(optinal) -l language(optional) -s themeStyle(optional)
	5. port selection(optional). eg -p 8081
	6. interface selection(optional) -if [all,localhost]
	7. language selection(optional) -l EN
    8. seletable style (optional): -s elmt-mtb
    9. selectable overlays (optional): -o "elmt-mtbs_tracks,elmt-mtb_routes"
    10. selectable renderer (optional): -r [database,direct]
	

Command parameters:

	1. -m  path to the mapfile(s), at leas one file is mandetorry. comma-separated list of mapsforge map files (.map)
	2. -t  path to the themefile. this is optional, without the internal theme is used
	3. -p  port to listen. this is optional, without 8080 is used
	4. -if interface listen on. this is optional, without localhost is used. possibilities -if all -if localhost
		with "-if all" its useful to run on a server. raspberry runs nice.
	5. -l  preffered language if availible in the map file
    6. -s  when using a themefile, selecting the style. eg "elmt-hiking"
    7. -o  when using a themefile and -o is given, ignore overlays enabled inside the themefile. Use only this comma-separated list of overlays.
    8. -r  mapsforge renderer [database,direct] (default:database), sometimes "direct" giving better results
    9. -h  print the help text and terminate
    

longest example:
```console
java -jar mapsforgesrv\bin\jars_ready2use/mapsforgesrv-fatjar.jar -m "path2mapfile1.map, path2mapfile2.map" -t path2themefile.xml -p 8080 -if all -l EN -s "elmt-hiking" -r "direct" -o elmt-mtbs_tracks,elmt-mtb_routes,elmt-mtb_c_routes"
```


-------------
### Contributors
- r_x created this tile server as part of mobac project https://sourceforge.net/projects/mobac/
- Thomas Th. @telemaxx: converted the mapserver server part in own git project with gradle nature.
- @pingurus (fixing styleseets error)
- Bernd @bjmdev (multi map support)
- @JFritzle (selectable theme style and overlays and renderer)

