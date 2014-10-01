package ch.psi.imagej.hdf5;


import ij.*;
import ij.io.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.gui.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import ncsa.hdf.object.*; // the common object package
import ncsa.hdf.object.h5.*; // the HDF5 implementation
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

public class HDF5Writer implements PlugInFilter {

	private static final Logger logger = Logger.getLogger(HDF5Writer.class.getName());

	public int setup(String arg, ImagePlus imp) {
		// see http://rsb.info.nih.gov/ij/developer/api/ij/plugin/filter/PlugInFilter.html
		return DOES_8G + DOES_16 + DOES_32 + DOES_RGB + NO_CHANGES;
	}

	public void run(ImageProcessor ip) {

		// Check whether windows are open
		if (WindowManager.getIDList() == null) {
			IJ.error("No windows are open.");
			return;
		}

		// Query for filename to save data
		SaveDialog sd = new SaveDialog("Save HDF5 ...", "", ".h5");
		String directory = sd.getDirectory();
		String name = sd.getFileName();
		if (name == null || name.equals("")) {
			return;
		}
		String filename = directory + name;

		// Retrieve an instance of the implementing class for the HDF5 format
		FileFormat fileFormat = FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5);

		// If the implementing class wasn't found, it's an error.
		if (fileFormat == null) {
			System.err.println("Cannot find HDF5 FileFormat.");
			return;
		}

		ImagePlus imp = WindowManager.getCurrentImage();
		int nFrames = imp.getNFrames();
		int nChannels = imp.getNChannels();
		int nSlices = imp.getNSlices();
		int stackSize = imp.getStackSize();
		int nRows = imp.getHeight();
		int nCols = imp.getWidth();
		int imgColorDepth = imp.getBitDepth();
		int imgColorType = imp.getType();

		Datatype type = null;
		if (imgColorType == ImagePlus.GRAY8) {
			logger.info("   bit depth: " + imgColorDepth + ", type: GRAY8");
			type = new H5Datatype(Datatype.CLASS_CHAR, Datatype.NATIVE, Datatype.NATIVE, Datatype.SIGN_NONE);
		} else if (imgColorType == ImagePlus.GRAY16) {
			logger.info("   bit depth: " + imgColorDepth + ", type: GRAY16");
			type = new H5Datatype(Datatype.CLASS_INTEGER, 2, Datatype.NATIVE, Datatype.SIGN_NONE);
		} else if (imgColorType == ImagePlus.GRAY32) {
			logger.info("   bit depth: " + imgColorDepth + ", type: GRAY32");
			type = new H5Datatype(Datatype.CLASS_FLOAT, Datatype.NATIVE, Datatype.NATIVE, -1);
		} else if (imgColorType == ImagePlus.COLOR_RGB) {
			logger.info("   bit depth: " + imgColorDepth + ", type: COLOR_RGB");
			type = new H5Datatype(Datatype.CLASS_CHAR, Datatype.NATIVE, Datatype.NATIVE, Datatype.SIGN_NONE);
		}

		if (imp.getOpenAsHyperStack() || imp.isHyperStack()) {
			// Hyperstack

			GenericDialog gd = new GenericDialog("Dataset Name");
			gd.addStringField(imp.getTitle(), "/t$F/channel$C");

			gd.showDialog();
			if (gd.wasCanceled()) {
				return;
			}
			String formatString = gd.getNextString();

			// Open the file
			try {
				H5File file = (H5File) fileFormat.createFile(filename, FileFormat.FILE_CREATE_OPEN);
				if (!file.canWrite()) {
					IJ.error("File `" + filename + "`is readonly!");
					return;
				}
				file.open();

				long[] dimensions = null;
				if (nSlices > 1) {
					dimensions = new long[3];
					dimensions[0] = nSlices;
					dimensions[1] = nRows;
					dimensions[2] = nCols;
				} else {
					dimensions = new long[2];
					dimensions[0] = nRows;
					dimensions[1] = nCols;
				}

				// iterate over frames and channels
				ImageStack stack = imp.getStack();
				for (int f = 0; f < nFrames; f++) {
					for (int c = 0; c < nChannels; c++) {
						String fullName = formatString;
						fullName = fullName.replaceAll("$F", f + "");
						fullName = fullName.replaceAll("$C", c + "");

						String dataSetName = HDF5Utilities.getDatasetName(fullName);
						String groupName = HDF5Utilities.getGroupDescriptor(fullName);

						// Ensure group exists
						Group group = HDF5Utilities.createGroup(file, groupName);

						// Create dataset
						Dataset dataset = null;
						try {
							dataset = (Dataset) file.get(groupName + "/" + dataSetName);
						} catch (Exception e) {
							dataset = null;
						}
						if (dataset == null) {
							long[] maxdims = dimensions;
							long[] chunks = null;
							int gzip = 0; // no compression
							dataset = file.createScalarDS(dataSetName, group, type, dimensions, maxdims, chunks, gzip, null);
						}
						dataset.init();

						long[] selected = dataset.getSelectedDims(); // the
						if (nSlices == 1) {
							System.arraycopy(dimensions, 0, selected, 0, selected.length);
							int stackIndex = imp.getStackIndex(c + 1, 1, f + 1);
							
							Object slice = stack.getPixels(stackIndex);
							dataset.write(slice);
						} else {
							selected[0] = 1;
							System.arraycopy(dimensions, 1, selected, 1, selected.length - 1);
							long[] start = dataset.getStartDims();
							for (int lvl = 0; lvl < nSlices; ++lvl) {
								start[0] = lvl;
								int stackIndex = imp.getStackIndex(c + 1, lvl + 1, f + 1);

								Object slice = stack.getPixels(stackIndex);
								dataset.write(slice);
							}
						}
					}
				}
				file.close();
			} catch (HDF5Exception err) {
				IJ.error(err.getMessage());
				return;
			} catch (Exception err) {
				IJ.error(err.getMessage());
				return;
			}
		} else {
			// No Hyperstack

			GenericDialog gd = new GenericDialog("Dataset Name");
			gd.addStringField(imp.getTitle(), "");
			gd.showDialog();
			if (gd.wasCanceled()) {
				return;
			}
			String varName = gd.getNextString();
			if (varName == "") {
				IJ.error("No data set name given. Plugin canceled!");
				return;
			}

			try {
				H5File file = null;
				try {
					file = (H5File) fileFormat.createFile(filename, FileFormat.FILE_CREATE_OPEN);
					if (!file.canWrite()) {
						IJ.error("File `" + filename + "`is readonly!");
						return;
					}
				} catch (HDF5Exception err) {
					IJ.error(err.getMessage());
					return;
				}
				file.open();

				String datasetName = HDF5Utilities.getDatasetName(varName);
				String groupName = HDF5Utilities.getGroupDescriptor(varName);

				// Ensure group exists
				Group group = HDF5Utilities.createGroup(file, groupName);

				long[] dimensions;
				if (imgColorType == ImagePlus.COLOR_RGB || imgColorType == ImagePlus.COLOR_256) {
					if (stackSize == 1) {
						dimensions = new long[3];
						dimensions[0] = nRows;
						dimensions[1] = nCols;
						dimensions[2] = 3;
					} else {
						dimensions = new long[4];
						dimensions[0] = stackSize;
						dimensions[1] = nRows;
						dimensions[2] = nCols;
						dimensions[3] = 3;
					}
				} else {
					if (stackSize == 1) {
						dimensions = new long[2];
						dimensions[0] = nRows;
						dimensions[1] = nCols;
					} else {
						dimensions = new long[3];
						dimensions[0] = stackSize;
						dimensions[1] = nRows;
						dimensions[2] = nCols;
					}
				}

				// Create dataset
				Dataset dataset = null;

				try {
					dataset = (Dataset) file.get(groupName + "/" + datasetName);
				} catch (Exception e) {
					dataset = null;
				}
				if (dataset == null) {
					long[] maxdims = dimensions;
					long[] chunks = null;
					int gzip = 0; // no compression
					dataset = file.createScalarDS(datasetName, group, type, dimensions, maxdims, chunks, gzip, null);
				}
				dataset.init();

				long[] selected = dataset.getSelectedDims();
				ImageStack stack = imp.getStack();
				if (stackSize == 1) {
					System.arraycopy(dimensions, 0, selected, 0, selected.length);

					Object slice = stack.getPixels(stackSize);
					if (imgColorType == ImagePlus.COLOR_RGB) {
						slice = computeRgbSlice((int[]) stack.getPixels(stackSize));
					}
					dataset.write(slice);

				} else {
					selected[0] = 1;
					System.arraycopy(dimensions, 1, selected, 1, selected.length - 1);
					long[] start = dataset.getStartDims(); // the off set of the
															// selection
					for (int lvl = 0; lvl < stackSize; ++lvl) {
						start[0] = lvl;

						Object slice = stack.getPixels(lvl + 1);
						if (imgColorType == ImagePlus.COLOR_RGB) {
							slice = computeRgbSlice((int[]) stack.getPixels(lvl + 1));
						}
						dataset.write(slice);
					}
				}

				file.close();
			} catch (HDF5Exception e) {
				logger.log(Level.WARNING, "Caught HDF5Exception", e);
			} catch (java.io.IOException e) {
				logger.log(Level.WARNING, "IO Error while writing '" + filename + "'", e);
			} catch (Exception e) {
				logger.log(Level.WARNING, "Range Error while writing '" + filename + "'", e);
			}
		}

	}

	/**
	 * Compute the rgb slice
	 * @param pixels	Original pixels
	 * @return			Slice with separated RGB values
	 */
	private byte[] computeRgbSlice(int[] pixels) {
		int size = pixels.length;
		byte[] rgbslice = new byte[size * 3];
		for (int i = 0; i < size; i++) {
			int red = (((int[]) pixels)[i] & 0xff0000) >> 16;
			int green = (((int[]) pixels)[i] & 0x00ff00) >> 8;
			int blue = ((int[]) pixels)[i] & 0x0000ff;
			rgbslice[3 * i + 0] = (byte) red;
			rgbslice[3 * i + 1] = (byte) green;
			rgbslice[3 * i + 2] = (byte) blue;
		}
		return rgbslice;
	}
}
