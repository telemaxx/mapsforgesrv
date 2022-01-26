REM https://docs.oracle.com/javase/8/docs/technotes/guides/2d/flags.html#win
REM https://github.com/bourgesl/mapbench/blob/master/bin/env_marlin.sh
SET ymdhis=%date:~-4,4%-%date:~-10,2%-%date:~-7,2%_%time:~0,2%-%time:~3,2%-%time:~6,2%
SET ymdhis=%ymdhis: =0%
java.exe ^
	--patch-module java.desktop=libs\marlin-0.9.4.5-Unsafe-OpenJDK11.jar ^
	-Xmx2G ^
	-Xms2G ^
	-Dsun.java2d.renderer.log=true ^
	-Dsun.java2d.renderer.useLogger=true ^
	-Dsun.java2d.accthreshold=0 ^
	-Dsun.java2d.renderer.useThreadLocal=true ^
	-Dsun.java2d.renderer.useFastMath=true ^
	-Dsun.java2d.render.bufferSize=65536 ^
	-jar build\libs\mapsforgesrv-fatjar.jar ^
-c config.sample.properties 2>&1
REM 	-Dsun.java2d.opengl=false ^
REM 	-Dsun.java2d.d3d=true ^
REM		-Dsun.java2d.translaccel=true ^
REM	-Dsun.java2d.renderer.profile=speed ^
REM	-Xlog:gc:D:\mapsforgesrv\logs\gc.%ymdhis%.log:time,level,tags ^
