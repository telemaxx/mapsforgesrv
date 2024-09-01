# mapsforgesrv

### mapsforgesrv cloned from the MOBAC project:
http://mobac.sourceforge.net/

The MapsforgeSrv is a local webserver providing rendered mapsforge map tiles.  
http://wiki.openstreetmap.org/wiki/Mapsforge  
The tiles are always rendered on the fly when requested.

The description of this tool you find at the origin page:  
https://sourceforge.net/p/mobac/code/HEAD/tree/trunk/tools/MapsforgeSrv/

The jar is now located in the dist folder:  
git\mapsforgesrv\dist\mapsforgesrv-fatjar.jar 
Only one jar containing everything you need.

	1. mapsforgesrv-fatjar.jar (branch "styles") developed and built with java version 11, needs java version 11 or higher to run

Command parameters:

	1. -c   [config]			Config file overriding cmd line parameters (default: none)
	2. -h   [help]				Print the help text and exit 


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
