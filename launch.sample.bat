REM -Xmx2G minimun (under: java.lang.OutOfMemoryError: Java heap space)
REM -Xlog:gc:logs\gc.log:time,level,tags
REM
REM java2d
REM	* log debug:	-Dsun.java2d.trace=log,timestamp,count,out:logs\java2d.log,verbose ^
REM	* params	https://www.oracle.com/java/technologies/graphics-performance-improvements.html
REM	* guide		https://docs.oracle.com/en/java/javase/22/troubleshoot/java-2d-properties.html#GUID-594ED735-F236-404D-8BDF-CCB4AFF1C1C1
REM			https://docs.oracle.com/en/java/javase/22/troubleshoot/java-2d-pipeline-rendering-and-properties.html
REM marlin renderer	https://groups.google.com/g/marlin-renderer
REM	* params	https://github.com/bourgesl/marlin-renderer/wiki/Tuning-options
@echo on
java.exe ^
--patch-module java.desktop=libs\marlin-0.9.4.8-Unsafe-OpenJDK11.jar ^
-Xmx2G ^
-Xms2G ^
-Dsun.java2d.d3d=true ^
-Dsun.java2d.opengl=false ^
-Dsun.java2d.accthreshold=0 ^
-Dsun.java2d.translaccel=true ^
-Dsun.java2d.ddforcevram=true ^
-Dsun.java2d.ddscale=true ^
-Dsun.java2d.ddblit=true ^
-Dsun.java2d.renderer.log=true ^
-Dsun.java2d.renderer.useLogger=true ^
-Dsun.java2d.renderer.profile=speed ^
-Dsun.java2d.renderer.useThreadLocal=true ^
-Dsun.java2d.renderer.useFastMath=true ^
-Dsun.java2d.render.bufferSize=65536 ^
-Dsun.java2d.renderer.pixelWidth=256 ^
-Dsun.java2d.renderer.pixelHeight=256 ^
-Dsun.java2d.renderer.tileSize_log2=8 ^
-Dsun.java2d.renderer.tileWidth_log2=8 ^
-Dawt.useSystemAAFontSettings=on ^
-c config 2>&1