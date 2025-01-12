# mapsforgesrv

### mapsforgesrv project was originally cloned from the [MOBAC](http://mobac.sourceforge.net) project

The MapsforgeSrv is a local [webserver](http://wiki.openstreetmap.org/wiki/Mapsforge) providing rendered Mapsforge map tiles.  
The tiles are always rendered on the fly when requested.

Source of this tile server is located at branch _tasks_ of [mapsforgesrv](https://github.com/telemaxx/mapsforgesrv) project.  
The JAR file `mapsforgesrv-fatjar.jar` can be downloaded from assets of _<release\>\_for\_java11\_tasks_ at [releases](https://github.com/telemaxx/mapsforgesrv/releases).   
The JAR file is developed and built with Java development kit (JDK) version 11 and needs Java runtime environment (JRE) version 11 or higher to run.  
The JAR file contains everything needed to run.

Some graphical user interfaces to configure interactively and run MapsforgeSrv can be found at https://github.com/JFritzle.  
In particular, there are convenient interfaces between MapsforgeSrv and the QMapShack or MyTourbook map applications.


Command line parameters:

	-c   [config]		Configuration folder (default: none)
	-h   [help]  		Print the help text and exit 

Run mapsforge tile server:

	java -jar <Path>/mapsforgesrv-fatjar.jar -c <Configuration folder>

Configuration requirements:
* Configuration folder must contain a server configuration file `server.poperties` and a subfolder `tasks`.
* Subfolder `tasks` can contain several task configuration files with file extension `.properties`.
* Each task configuration files configures a separate server task to be processed concurrently.<br>While server is running, task files and thus tasks can be added, modified or deleted on the fly.
* Case-sensitive server task name = task file name with file extension cut off.

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
| `terminate` | Accept terminate request (from loopback addresses only!)<br>Default: `false`
| `outofrange_tms` | URL pattern of an external TMS server<br>used to redirect for out-of-range tiles<br>e.g. https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png<br>Default: unset = no redirection
| `requestlog-format` | Output format of logged server requests<br>Default: `"From %{client}a Get %U%q Status %s Size %O bytes Time %{ms}T ms"`<br>Empty value `""` suppresses request logging!<br>For description of format syntax see [here](https://javadoc.io/doc/org.eclipse.jetty/jetty-server/latest/org.eclipse.jetty.server/org/eclipse/jetty/server/CustomRequestLog.html).  

Task configuration files recognize the following parameters:

| Name | Description |
| ---- | ----------- |
| `mapfiles` | Comma-separated list of map file paths with file extension `.map`<br>Default: unset = built-in world map automatically used
| `worldmap` | Append built-in world map to list `mapfiles` of map files<br>Default: `false`
| `language` | Preferred language if supported by map file<br>(ISO 639-1 or ISO 639-2 if an ISO 639-1 code doesn't exist)<br>Default: unset = primary available map language used
| `themefile` | Theme file path with file extension `.xml`<br>or one of built-in Mapsforge themes<br>`DEFAULT`, `OSMARENDER`, `MOTORIDER` or `MOTORIDER_DARK`<br>used for rendering<br>Default: built-in Mapsforge theme `OSMARENDER`
| `style` | Theme file's style used for rendering<br>Default: unset =  theme file's built-in default style
| `overlays` | Comma-separated list of style's overlays <br>to be enabled for rendering<br>Default: unset = style's overlays enabled by default
| `demfolder` | Folder path containing DEM (Digital Elevation Model) data files<br>with file extension `.hgt` required by hillshading<br>Alternatively, `.hgt` files can be embedded in `.zip` archives with same name,<br>e.g. archive N49E008.zip containing one file N49E008.hgt<br>Default: unset = no hillshading
| `hillshading-algorithm` | One of hillshading algorithms used for hillshading<br>`simple(linearity,scale)`<br>`diffuselight(angle)`<br> `stdasy(asymmetryFactor,minSlope,maxSlope,readingThreadsCount,computingThreadsCount,preprocess)`<br>`simplasy(asymmetryFactor,minSlope,maxSlope,readingThreadsCount,computingThreadsCount,preprocess)`<br>`hiresasy(asymmetryFactor,minSlope,maxSlope,readingThreadsCount,computingThreadsCount,preprocess)`<br>`adaptasy(asymmetryFactor,minSlope,maxSlope,readingThreadsCount,computingThreadsCount,preprocess)`<br>Hillshading algorithm name without parentheses and parameters is valid too!<br>Default: unset = no hillshading<br>Parameter defaults:<br>linearity = 0.1, scale = 0.666, angle = 50.,<br>asymmetryFactor = 0.5, minSlope = 0, maxSlope = 80,<br>readingThreadsCount = max(1,AVAILABLE_PROCESSORS/3),<br>computingThreadsCount = AVAILABLE_PROCESSORS,<br>preprocess = true<br>Parameter ranges:<br>0. ≤ linearity ≤ 4., 0. ≤ scale, 0. ≤ angle ≤ 90.,<br>0. ≤ asymmetryFactor ≤ 1., 0 ≤ minSlope < maxSlope ≤ 100,<br>readingThreadsCount ≥ 0, computingThreadsCount ≥ 0, preprocess = {false\|true}
| `hillshading-magnitude` | Hillshading's gray value magnitude scaling, 0. ≤ value ≤ 4.<br>Value < 1. = brighter gray, value > 1. = darker gray<br>Default: `1.` = unscaled gray values
| `contrast-stretch` | Contrast stretching of color value, 0 ≤ value ≤ 254 to increase<br>contrast by raising black level from 0 towards white level 255 <br>Default: `0` = no contrast stretching
| `gamma-correction` | Gamma correction value > 0. for nonlinear luminance mapping<br>Default: `1.` = no gamma correction
| `text-scale` | Text scale factor > 0. to scale size of labels on map<br>Default: `1.` = no text size scaling
| `symbol-scale` | Symbol scale factor > 0. to scale size of symbols on map<br>Default: `1.` = no symbol size scaling
| `line-scale` | Line scale factor > 0. to scale thickness of lines on map<br>Default: `1.` = no line thickness scaling
| `user-scale` | Overall scale factor > 0. to scale all map elements<br>Scales value of `text-scale` and `symbol-scale` and `line-scale`<br>Default: `1.` = no overall scaling
| `device-scale` | Device scale factor > 0.<br>Default: `1.` = no device scaling

Hillshading requirements:
* Must be enabled in theme file
* Parameter `demfolder` must be set
* Parameter `hillshading-algorithm` must be set
* If parameter `mapfiles` is set, hillshading is applied to rendered map tiles,<br>if parameter `mapfiles` is not set, alpha transparent overlay tiles are rendered

URLs to request tiles from tiles server:  
```
scheme://address:port/zoom/x/y.format?task=name
```

| URL item | Description |
| -----| ----------- |
| scheme | protocol either _http_ or _https_ |
| address | tile server's IP address |
| port | tcp port to request tiles |
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
