### ECLIPSE

- ### prerequisite

  - Eclipse IDE for Java Developers
    - Java SDK_(Preferences > Java > Installed JREs)_
  - [Buildship: Eclipse Plug-ins for Gradle](https://projects.eclipse.org/projects/tools.buildship)
    - _Build Scans might be disabled in Preferences > Gradle_
  - An Eclipse workspace which doesn't already contai a project named **mapsforgesrv**

- ### import

  - File > Import > Gradle > Existing Gradle Project
  - Choose path to your cloned directory as Project root director"  > Finish

- ### configure

  > Before launching Gradle tasks, please check configuration files in `/config`:

  - `server.properties`
    - `mapfiles` path must exist on your system if you want to use them
    - `demfolder` path must exist on your system if you want renderer with hillshading
  - `/style/*.properties` _(except `default.properties`)_
    - `themefile` path must exist on your system. _(you can simply suffix files with`.disabled` to avoid loading them)_

### GRADLE

> Tasks can be triggered by **Eclipse** or **terminal**

 - #### [options](https://docs.gradle.org/current/userguide/command_line_interface.html)

   - Note: `%NUMBER_OF_PROCESSORS%` is only for **Windows**

 - #### build

   > **shadowJar** 

      - ex. `gradlew --warning-mode none --console=verbose --parallel --max-workers %NUMBER_OF_PROCESSORS% shadowJar 2>&1`

   Distribution jar released is **/dist/mapsforgesrv-fatjar.jar**


 - #### run

   > **runShadow** 

      - ex. `gradlew --warning-mode none --console=verbose --parallel --max-workers %NUMBER_OF_PROCESSORS% runShadow 2>&1`

