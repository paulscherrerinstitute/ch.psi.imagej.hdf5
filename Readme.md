 # Overview
ImageJ plugin for reading and writing HDF5 files.

Upon opening an HDF5 file, an import dialog lists the available image data sets contained in the file and allows the selection of one or multiple data sets to be opened:

![Import dialog to select datasets to be opened](hdf5plugin_select_datasets.png)

Note that the Fiji distribution of ImageJ comes with an hdf5 plugin already installed out of the box. This packaged hdf5 plugin (HDF5_Vibez) has some great features, and may be all you need. However, it does not allow one to load large image arrays as virtual stacks or to select only sliced subsets of the data, and thus often results in "out of memory" errors when working with large data sets. In those cases, the PSI plugin for reading and writing HDF5 files described here might be your preferred choice to work with HDF5 files.

# Usage

To open a HDF5 file use:

```
File > Import > HDF5...
```

To save to an HDF5 file use:

```
File > SaveAs > HDF5
```

## Scripting

To use this plugin from the ImageJs' (python) scripting interface these lines
can be used to open a dataset:

```python
from ch.psi.imagej.hdf5 import HDF5Reader
reader = HDF5Reader()
stack = reader.open("",False, "/Users/ebner/Desktop/A8_d_400N030_.h5", "/exchange/data_dark", True)
```

# Installation

## Prerequisites
This plugin requireds ImageJ/Fiji to be run with a Java 8 or greater. To check with wich version your installation is running please refer to the [Troubleshooting](#Troubleshooting) section below.

## ImageJ
All you need is to download the latest HDF5 ImageJ plugin from [releases](https://github.com/paulscherrerinstitute/ch.psi.imagej.hdf5/releases) and copy the jar into the `plugins` directory of ImageJ. After this you should be able to simply start ImageJ and use the Plugin.

## Fiji
Fiji already comes with an HDF5 plugin (HDF5_Vibez) installed. However, as mentioned above it has certain limitations. Before installing this plugin, HDF5_Vibez need to be deinstalled.

To disable the standard hdf5 plugin, follow these steps.

* Close any running instances of the Fiji applications
* Locate the installation directory of the Fiji application (`FIJI_DIR`).
* Remove the HDF5_Vibez jar from the `plugins` directory: `rm $FIJI_DIR/plugins/HDF5_Vibez*.jar`
* Remove the provided HDF5 binary jar from the `jar` directory: `rm $FIJI_DIR/jars/jhdf5-*.jar`

The installation of the plugin essentially only requires one to add the downloaded jar into the `plugins` directory inside the Fiji installation directory. Follow these steps:

* Download the latest HDF5 ImageJ plugin from [releases](https://github.com/paulscherrerinstitute/ch.psi.imagej.hdf5/releases) and copy the jar into the `$FIJI_DIR/plugins` directory
* Restart Fiji.

To verify the correct installation:
* Try to import an hdf5 file: `Fiji > File > Import > HDF5...`. If the installation of the pluging was sucessful, the file import dialog should look like in the screenshot below once you have selected an hdf5 file.
    
![Import dialog to select datasets to be opened](hdf5plugin_select_datasets.png)


## Configuration (Optional)

If you want to configure the HDF5 Reader as a standard file reader you need to register the reader within the `HandleExtraFileTypes.java` file. This can be done as follows (details on this can be found on: http://albert.rierol.net/imagej_programming_tutorials.html): 

* Add `HandleExtraFileTypes.java` 

```java
if (name.endsWith(".h5") || name.endsWith(".hdf5")) {
    return tryPlugIn("ch.psi.imagej.hdf5.HDF5Reader", path);
}
```

* Recompile  `HandleExtraFileTypes.java`
```
javac -classpath ij.jar ./plugins/Input-Output/HandleExtraFileTypes.java
```


# Troubleshooting
## Checking the Java version

You can check whether Java-8 is included with Fiji as follows:

* Open the update dialog from the menu via `Fiji > Help > Update...`.
* Wait for the application to finish checking for new updates.
* In the ImageJ Updater Window, click on "Advanced Mode"
* Type `java-8` into the "Search" field.
* If you see an entry `lib/Java-8` in the results box below, then Java 8 is
  ready to be used on your system (see screenshots below).
  
![ImageJ Updater Advanced Mode](ImageJ_Updater_AdvancedMode.png)
![ImageJ Updater Search Java 8](ImageJ_Updater_search_java8.png)


## Check Which Plugin is Installed

You will be able to tell which plugin is currently active when trying to import an hdf5 file (`Fiji > File > Import > HDF5...`). Once you have selected an hdf5 file, the standard HDF5_Vibez plugin opens an import dialog that looks as follows: 

![Import dialog of packaged HDF5_Vibez plugin](hdf5_Vibez_select_datasets.png)


## Running Older Fiji Versions with Java 8 or Greater
For older versions of Fiji, we have to instruct Fiji to use an alternative Java  (which has to be installed on the systems separately, of course). This can be  done as follows: 

```
cd <FIJI_HOME>
<fiji> --java-home /usr/lib/jvm/jre-1.8.0-openjdk.x86_64
```

## Adjust Memory Settings
For normal usage, you should just be able to open Fiji/ImageJ as usual and start using the hdf5 plugin. If you experience out of memory problems while opening very large datasets try to increaste the initial amount of memory to be used as follows:

```
java -Xmx3048m -jar ij.jar
```

The `Xmx` setting is quite "random" it depends on how big hdf5 files you are planning to open.


# Development
To create an all in one jar file for installation in a ImageJ installation use: `./gradlew clean fatJar`

The jar file is an all in one jar including all required native libraries for Windows, Linux and Mac OS X.


## Dependencies
The java HDF5 libary as well as the precompiled code we downloaded and copied from: https://wiki-bsse.ethz.ch/display/JHDF5/Download+Page . All the necessary jars where copied from there into the `/lib` directory.

The files in the package hdf.objects in this repository were copied from the hdfviewer source code. We were not able to find a jar that contained them.
 

# Acknowledgements
This project was inspired by the ImageJ HDF Plugin of Matthias Schlachter Chair of Pattern Recognition and Image Processing, University of Freiburg, Germany (https://code.google.com/p/imagej-hdf ) . It is a complete rewrite of the code with the focus on efficiency and maintainability
