# Overview

ImageJ plugin for reading and writing HDF5 files.

This project originated from and is inspired by: ImageJ HDF Plugin of Matthias Schlachter Chair of Pattern Recognition and Image Processing, University of Freiburg, Germany.
https://code.google.com/p/imagej-hdf/

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

* Download latest HDF5 ImageJ plugin from [here](http://slsyoke4.psi.ch:8081/artifactory/releases/HDF5_Viewer-0.2.0.zip).

* Go into the ImageJ installation folder and extract the downloaded zip.

```
cd <IMAGEJ_HOME>
unzip <path of downloaded zip>
```

## Prerequisites
This plugin requires ImageJ to be run with a Java 7 or greater JVM.

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

# Development
To create an all in one zip file for installation in a ImageJ installation use: 
`mvn clean compile assembly:assembly`

The zip file contains an all in one jar as well as the required native libraries for Windows, Linux and Mac OS X.
