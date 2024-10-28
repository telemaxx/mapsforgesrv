# mapsforgesrv

### mapsforgesrv project was originally cloned from the [MOBAC](http://mobac.sourceforge.net) project

The MapsforgeSrv is a local [webserver](http://wiki.openstreetmap.org/wiki/Mapsforge) providing rendered Mapsforge map tiles.  
The tiles are always rendered on the fly when requested.

Source of this tile server is located at branch _tasks_ of [mapsforgesrv](https://github.com/telemaxx/mapsforgesrv) project.  
The jar file `mapsforgesrv-fatjar.jar` can be downloaded from assets of _<release\>\_for\_java11\_tasks_ at [releases](https://github.com/telemaxx/mapsforgesrv/releases).   
The jar file is developed and built with java version 11 and needs java version 11 or higher to run.  
The jar file contains everything needed to run.



Command line parameters:

	-c   [config]			Configuration folder (default: none)
	-h   [help]				Print the help text and exit 

Run mapsforge tile server:

	java -jar <Path>/mapsforgesrv-fatjar.jar -c <Configuration folder>

Configuration folder must contain server configuration file `server.poperties` and subfolder `tasks`. Subfolder `tasks` may contain several task configuration files with file extension `.properties`, each one defining a separate server task to be processed concurrently. Case-sensitive server task name = task file name with file extension cut off. 

Each configuration file can contain
* Parameter assignment lines of type `name=value` or `name="value"`
* Comment lines starting with character `#`
* Separator lines containing white spaces only

Server configuration file `server.poperties` recognizes the following parameters:

| Name | Description |
| ---- | ----------- |
| `host` | IP address to listen on<br>Default: unset = listen on all interfaces
| `port` | TCP port to listen on<br>Default: `8486`
| `cache-control` | Browser cache TTL<br>Default: `0`
| `terminate` | Accept terminate request<br>Default: `false`
| `outofrange_tms` | URL pattern of an external TMS server<br>used to redirect for out-of-range tiles<br>e.g. https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png<br>Default: unset = no redirection
| `requestlog-format` | Output format of logged server requests<br>Empty value suppresses request logging!<br>Default: `"%{client}a - %u %t '%r' %s %O '%{Referer}i' '%{User-Agent}i' '%C'"`<br>For description of format syntax see [here](https://javadoc.io/doc/org.eclipse.jetty/jetty-server/latest/org.eclipse.jetty.server/org/eclipse/jetty/server/CustomRequestLog.html).  

Task configuration files recognize the following parameters:

| Name | Description |
| ---- | ----------- |
| `mapfiles` | Comma-separated list of map file paths with file extension `.map`<br>Default: unset = built-in world map automatically used
| `worldmap` | Append built-in world map to list of map files<br>Default: `false`
| `language` | Preferred language if supported by map file<br>(ISO 639-1 or ISO 639-2 if an ISO 639-1 code doesn't exist)<br>Default: unset = primary available map language used
| `themefile` | Theme file path with file extension `.xml`<br>or built-in Mapsforge theme `DEFAULT` or `OSMARENDER`<br>used for rendering<br>Default: built-in Mapsforge theme `OSMARENDER`
| `style` | Theme file's style used for rendering<br>Default: unset =  theme file's built-in default style
| `overlays` | Comma-separated list of style's overlays <br>to be enabled for rendering<br>Default: unset = style's overlays enabled by default
| `demfolder` | Folder path containing DEM (Digital Elevation Model) data files<br>with file extension `.hgt` used for hillshading<br>Default: unset = no hillshading
| `hillshading-algorithm` | Hillshading algorithm `simple` or `simple(linearity,scale)` or `diffuselight` or `diffuselight(angle)` used for hillshading<br>Default: unset = no hillshading<br>Parameter defaults: linearity=0.1, scale=0.666, angle=50.
| `hillshading-magnitude` | Hillshading's gray value magnitude scaling, 0. ≤ value ≤ 4.<br>Value < 1. = brighter gray, value > 1. = darker gray<br>Default: `1.` = unscaled gray values
| `contrast-stretch` | Contrast stretching of color value, 0 ≤ value ≤ 254<br>to increase color contrast by raising black level over value 0<br>Default: `0` = no contrast stretching
| `gamma-correction` | Gamma correction value > 0. for nonlinear luminance mapping<br>Default: `1.` = no gamma correction
| `text-scale` | Text scale factor > 0. to scale size of labels on map<br>Default: `1.` = no text size scaling
| `symbol-scale` | Symbol scale factor > 0. to scale size of symbols on map<br>Default: `1.` = no symbol size scaling
| `line-scale` | Line scale factor > 0. to scale thickness of lines on map<br>Default: `1.` = no line thickness scaling
| `user-scale` | Overall scale factor > 0. to scale all map elements<br>Scales value of `text-scale` and `symbol-scale` and `line-scale`<br>Default: `1.` = no overall scaling
| `device-scale` | Device scale factor > 0.<br>Default: `1.` = no scaling

Hillshading requirements:
* Must be enabled in theme file
* Parameter `demfolder` must be set
* Parameter `hillshading-algorithm` must be set
* If parameter `mapfiles` is set, hillshading is applied to rendered map tiles,<br>if parameter `mapfiles` is not set, alpha transparent overlay tiles are rendered

URLs to request tiles from tiles server:  
```
scheme://address:port/zoom/x/y.format?task=name
```

| URL item | description |
| -----| ----------- |
| scheme | protocol either _http_ or _https_ |
| address | tile server's IP address |
| mport | tcp port to request map tiles |
| hport | tcp port to request hillshading overlay tiles |
| port | tcp port to request map and hillshading overlay tiles |
| zoom | zoom level of requested tile |
| x | tile number in x direction (longitude) |
| y | tile number in y direction (latitude) |
| format | tile image format _png_, _jpg_, _tif_, _bmp_, ... |
| name | name of server task |

URL example for requesting tiles from a task configured by the `Map.properties` task file:
```
http://127.0.0.1:60815/14/8584/5595.png?task=Map
```
<br>

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
