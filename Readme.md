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
