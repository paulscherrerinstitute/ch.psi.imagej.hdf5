# Overview

ImageJ plugin for reading and writing HDF5 files.



# Usage

To open a HDF5 file use:

```
File > Import > HDF5...
```

To save to an HDF5 file use:

```
File > SaveAs > HDF5
```

# Installation
To be able to install this plugin ImageJ need to be run with a Java 7 or greater JVM.

* Download latest HDF5 ImageJ plugin from [here](http://slsyoke4.psi.ch:8081/artifactory/releases/HDF5_Viewer-0.6.0.zip).

* Go into the ImageJ installation folder and extract the downloaded zip.

```
cd <IMAGEJ_HOME>
unzip <path of downloaded zip>
```

## Configuration (Optional)

If you want to configure the HDF5 Reader as a standard file reader you need to register the reader within the `HandleExtraFileTypes.java` file.
This can be done as follows (details on this can be found on: http://albert.rierol.net/imagej_programming_tutorials.html): 

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

# Usage

## Mac OS X

```
java -Djava.library.path=./lib/mac64 -Xmx3048m -jar ImageJ64.app/Contents/Resources/Java/ij.jar
```

## Linux

```
java -Djava.library.path=./lib/linux64 -Xmx3048m -jar ij.jar
```

The `Xmx` setting is quite random it depends on how big hdf5 files you are planning to open.


# Fiji
Fiji currently comes with Java 6 bundled. As the HDF5 Plugin requires Java 7 or higher we have to instruct Fiji to use an alternative Java.
This can be done as follows: 

```
cd <FIJI_HOME>
<fiji> --java-home /usr/lib/jvm/jre-1.7.0-openjdk.x86_64 -Djava.library.path=lib/linux64
```

Starting with Java 8 just the LD_LIBRARY_PATH variable need to be set. For MacOSX it is export `DYLD_LIBRARY_PATH=lib/mac64/:$DYLD_LIBRARY_PATH`.

# Development
To create an all in one zip file for installation in a ImageJ installation use: 
`mvn clean compile assembly:assembly`

The zip file contains an all in one jar as well as the required native libraries for Windows, Linux and Mac OS X.

# Acknowledgements
This project was inspired by the ImageJ HDF Plugin of Matthias Schlachter Chair of Pattern Recognition and Image Processing, University of Freiburg, Germany ( https://code.google.com/p/imagej-hdf ) . 
It is a complete rewrite of the code with the focus on efficiency and maintainability
