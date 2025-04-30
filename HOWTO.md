### ECLIPSE

- ### prerequisite

  - Eclipse IDE for Java Developers
    - Java SDK _(Preferences > Java > Installed JREs)_
  - [Buildship: Eclipse Plug-ins for Gradle](https://projects.eclipse.org/projects/tools.buildship)
    - _Build Scans might be disabled in Preferences > Gradle_
  - An Eclipse workspace which doesn't already contain a project named **mapsforgesrv**

- ### import

  - File > Import > Gradle > Existing Gradle Project
  - Choose path to your cloned directory as Project root director  > Finish

- ### configure

  > Before launching _Gradle Tasks_, please check configuration files:

  - `config/server.properties` _(required)_
    
  - `config/tasks/*.properties` _(optional)_
    - `mapfiles` path(s) must exist on your system if you want to use them
    - `demfolder` path must exist on your system if you want renderer with hillshading
    - `themefile` path must exist on your system.  
  
  - _Files not having file extension `.properties` are ignored.  
  Changing file extension to anything else avoids loading them._

### GRADLE

> **Gradle** tasks can be triggered by Eclipse's **Gradle Tasks** or run in **terminal**

 - #### [options](https://docs.gradle.org/current/userguide/command_line_interface.html)

   - Note: `%NUMBER_OF_PROCESSORS%` is only for **Windows**

 - #### build

   > **shadowJar** 

      - run `gradlew --warning-mode none --console=verbose --parallel --max-workers %NUMBER_OF_PROCESSORS% shadowJar 2>&1`

   Distribution jar released is **/dist/mapsforgesrv-fatjar.jar**


 - #### run

   > **runShadow** 

      - run `gradlew --warning-mode none --console=verbose --parallel --max-workers %NUMBER_OF_PROCESSORS% runShadow 2>&1`

