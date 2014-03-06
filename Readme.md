# Overview
ZMQ ImageJ is an ImageJ plugin for viewing images transmitted via ZMQ. It can handle data streamed via the message format defined in [ZMQ Data Steaming](https://confluence.psi.ch/display/SOF/ZMQ+Data+Streaming).

# Installation
To install the plugin into ImageJ drop the all-in-one jar into the plugins directory of ImageJ. Afterwards (re)start ImageJ  and open the plugin via _Plugins > ZeroMQ Viewer_. A plugin window will be opened where source, port and method can be specified. Once clicked on _Start_ and images are streamed from the source an additional window will open showing the images.

# Usage
While using the ZeroMQ Viewer Plugin ImageJ need to be configured to have more memory. This can be done in the `run` file located in the ImageJ discribution.
Add/Modify following flag: `-Xmx1024m`

# Development
To build the code run following maven command/goals:
`mvn clean compile assembly:single`

## References
http://fiji.sc/wiki/index.php/Description_of_ImageJ's_plugin_architecture



# Installation

Drop jar into plugins directory of the ImageJ installation.

## Linux 32Bit
Edit the `run` file of your ImageJ installation
`./jre/bin/java -Djava.library.path=./lib/linux32 -Xmx512m -jar ij.jar`

## Linux 64Bit
Edit the `run` file of your ImageJ installation
`java -Djava.library.path=./lib/linux64 -Xmx512m -jar ij.jar`

## Windows 32Bit
Edit the "ImageJ.cfg" of your ImageJ installation
`C:\Programme\Java\jdk1.5.0_14\bin\javaw.exe -Djava.library.path=lib\win32 -Xmx640m -cp ij.jar ij.ImageJ`

## Mac 32Bit
Edit the `run` file of your ImageJ installation
`java -Djava.library.path=./lib/mac32 -Xmx512m -jar ij.jar`

# References 
Inspired by: ImageJ HDF Plugin of Matthias Schlachter Chair of Pattern Recognition and Image Processing, University of Freiburg, Germany.
https://code.google.com/p/imagej-hdf/
