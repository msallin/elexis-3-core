# Elexis Ungrad Core Repository

This is a fork of [Elexis classic core](http://github.com/elexis/elexis-3-core) from the original creator of Elexis.

## Current branches:

* ungrad2022 - Latest stable branch. Will always compile and work
* develop - Development branch. Will not always compile and mostly not work flawless

## Build:

Prerequisites: Git, Java-sdk8, Maven 3.6.x; Linux or Windows recommended. MacOS will be a bit tricky,

```bash
git clone -b ungrad2022 elexis-3-core
cd elexis-3-core
./build.sh
```

This will build Elexis Ungrad and its core repository for Windows32, Windows64, Linux, and MacOS. You'll find the products in `ch.elexis.core.p2site/target/products`, and the repository in `ch.elexis.core.p2site/target/repository`.

This will be a working Elexis core. Addidtional plugins are in the [elexis-3-base](http://github.com/rgwch/elexis-3-base) and [elexis-ungrad](http://github.com/rgwch/elexis-ungrad) repositories.


## Develop

You'll need an [Eclipse IDE for RCP and RAP Developers](https://www.eclipse.org/downloads/packages/release/2022-09/r/eclipse-ide-rcp-and-rap-developers)

* Use import->Projects from git to import the elexis-3-core repository into the IDE. I recommend to create a working-set "core" for these plugins.

* Set "Window-Preferenes-Maven-Errors/Warnings-Plugin execution not covered by lifecycle configuration" to "ignore"

* open ch.elexis.target/elexis.target.
* Click "set as target platform" and wait until finished. If it's not able to load the target definition, select the erroneous entry and click "Update". Probably ths step is necessary after each restart of the Eclips ide.

* Select project-clean to rebuild all Projects.

* Create a Run configuration. Select the ch.elexos.core.application.ElexisApp to run as Application. On the Plug-ins Tab, selecz "Plugins selected below" and Deselect all. Then, select only "ch.elexis.core.application" and "select required". Click Apply and run. Elexis should start then.
