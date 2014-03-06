# Installation

* Download latest HDF5 ImageJ plugin from [here](http://slsyoke4.psi.ch:8081/artifactory/releases/HDF5_Viewer-0.2.0.zip).

Go into the ImageJ installation folder and extract the downloaded zip.

```
cd <IMAGEJ_HOME>
unzip <path of downloaded zip>
```

## Prerequisites
This plugin requires ImageJ to be run with a Java 7 or greater JVM.

# Usage
```
java -Djava.library.path=./lib/mac64 -Xmx3048m -jar ImageJ64.app/Contents/Resources/Java/ij.jar
```

# Development
To create an all in one zip file for installation in a ImageJ installation use: 
`mvn clean compile assembly:assembly`

The zip file contains an all in one jar as well as the required native libraries for Windows, Linux and Mac OS X.

# References 
This project started from and is inspired by: ImageJ HDF Plugin of Matthias Schlachter Chair of Pattern Recognition and Image Processing, University of Freiburg, Germany.
https://code.google.com/p/imagej-hdf/