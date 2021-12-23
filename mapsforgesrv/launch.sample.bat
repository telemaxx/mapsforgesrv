REM https://docs.oracle.com/javase/8/docs/technotes/guides/2d/flags.html#win
REM https://github.com/bourgesl/mapbench/blob/master/bin/env_marlin.sh
java.exe ^
	--patch-module java.desktop=libs\marlin-0.9.4.5-Unsafe-OpenJDK11.jar ^
	-Xlog:gc:D:\mapsforgesrv\logs\gc.log:time,level,tags ^
	-Xmx2G ^
	-Xms2G ^
	-Dsun.java2d.renderer.log=true ^
	-Dsun.java2d.renderer.useLogger=true ^
	-Dsun.java2d.opengl=True ^
	-Dsun.java2d.accthreshold=0 ^
	-Dsun.java2d.renderer.profile=speed ^
	-Dsun.java2d.renderer.useThreadLocal=false ^
	-Dsun.java2d.renderer.useFastMath=true ^
	-Dsun.java2d.render.bufferSize=65536 ^
	-jar build\libs\mapsforgesrv-fatjar.jar ^
-c config.sample.properties 2>&1