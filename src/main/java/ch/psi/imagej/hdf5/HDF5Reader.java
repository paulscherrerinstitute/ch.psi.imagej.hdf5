package ch.psi.imagej.hdf5;

/*
 * =========================================================================
 * 
 * Copyright 2011 Matthias Schlachter
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * =========================================================================
 */

import ij.IJ;
import ij.ImagePlus;
import ij.CompositeImage;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.*;

import ncsa.hdf.object.*;
import ncsa.hdf.object.h5.*;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

public class HDF5Reader implements PlugIn {
	
	private static final Logger logger = Logger.getLogger(HDF5Reader.class.getName());
	
	public static void main(String[] args){
		HDF5Reader r = new HDF5Reader();
		r.run("");
	}
	
	
	public void run(String arg) {
		// make sure default values for config are written
		// HDF5_Config.setDefaultsIfNoValueExists();

		// Run plugin
		String directory = "";
		String name = "";
		boolean tryAgain;
		String openMSG = "Open HDF5...";
		do {
			tryAgain = false;
			OpenDialog od;
			if (directory.equals(""))
				od = new OpenDialog(openMSG, arg);
			else
				od = new OpenDialog(openMSG, directory, arg);

			directory = od.getDirectory();
			name = od.getFileName();
			if (name == null)
				return;
			if (name.equals(""))
				return;

			File testFile = new File(directory + name);
			if (!testFile.exists() || !testFile.canRead())
				return;

			if (testFile.isDirectory()) {
				directory = directory + name;
				tryAgain = true;
			}
		} while (tryAgain);

		IJ.showStatus("Loading HDF5 File: " + directory + name);

		
		// Read HDF5 file
		H5File inFile = null;
		try {
			inFile = new H5File(directory + name, H5File.READ);
			inFile.open();
			
			List<Dataset> datasets = HDF5Utilities.getDatasets(inFile);
			List<Dataset> selectedDatasets = selectDatasets(datasets);
			
			for (Dataset var : selectedDatasets) {
				// Read dataset attributes and properties
				String datasetName = var.getName();
				int rank = var.getRank();
				Datatype datatype = var.getDatatype();
				Datatype datatypeIfUnsupported = null;
				long[] extent = var.getDims(); // Extent in px (level,row,col)

				logger.info("Reading dataset: " + datasetName + " Rank: " + rank + " Type: " + datatype.getDatatypeDescription());
				
				double pixelWith = 1.0;
				double pixelHeight = 1.0;
				double pixelDepth = 1.0;
				
				IJ.showStatus("Reading Variable: " + datasetName + " (" + extent[0] + " slices)");
				
				// nice gadget to update the progress bar
				long progressDivisor = extent[0] / 50; // we assume 50 process
														// steps
				if (progressDivisor < 1)
					progressDivisor = 1;

				// check if we have an unsupported datatype
				if (datatype.getDatatypeClass() == Datatype.CLASS_INTEGER && (datatype.getDatatypeSize() == 4 || datatype.getDatatypeSize() == 8)) {
					logger.info("Datatype not supported by ImageJ");
					GenericDialog typeSelDiag = new GenericDialog("Datatype Selection");
					typeSelDiag.addMessage("The datatype `" + datatype.getDatatypeDescription() + "` is not supported by ImageJ.\n\n");
					typeSelDiag.addMessage("Please select your wanted datatype.\n");
					String[] choices = new String[2];
					choices[0] = "float";
					choices[1] = "short";
					typeSelDiag.addChoice("      Possible types are", choices, "float");
					typeSelDiag.showDialog();

					if (typeSelDiag.wasCanceled()) {
						return;
					}
					int selection = typeSelDiag.getNextChoiceIndex();
					if (selection == 0) {
						logger.info("float selected");
						datatypeIfUnsupported = new H5Datatype(Datatype.CLASS_FLOAT, Datatype.NATIVE, Datatype.NATIVE, -1);
					}
					if (selection == 1) {
						logger.info("short selected");
						int typeSizeInByte = 2;
						datatypeIfUnsupported = new H5Datatype(Datatype.CLASS_INTEGER, typeSizeInByte, Datatype.NATIVE, -1);
					}
				}

				// read dataset
				if (rank == 5 && extent[4] == 3) {
					logger.info("   Detected HyperVolume (type RGB).");

					// create a new image stack and fill in the data
					ImageStack stack = new ImageStack((int) extent[3], (int) extent[2]);
					// read the whole dataset, since reading chunked
					// datasets
					// slice-wise is too slow

					long[] dims = var.getDims(); // the dimension sizes
					// of the dataset
					long[] selected = var.getSelectedDims(); // the
					// selected
					// size of
					// the
					// dataet
					selected[0] = dims[0];
					selected[1] = dims[1];
					selected[2] = dims[2];
					selected[3] = dims[3];
					selected[4] = dims[4];// 3
					// the selection
					Object wholeDataset = var.read();
					// check for unsigned datatype
					int unsignedConvSelec = 0;
					Datatype dType = var.getDatatype();
					boolean isSigned16Bit = !dType.isUnsigned() && (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) && (dType.getDatatypeSize() == 2);

					if (isSigned16Bit) {
						GenericDialog convDiag = new GenericDialog("Unsigend to signed conversion");
						convDiag.addMessage("Detected unsigned datatype, which " + "is not supported.");
						String[] convOptions = new String[2];
						convOptions[0] = "cut off values";
						convOptions[1] = "convert to float";
						convDiag.addChoice("Please select an conversion option:", convOptions, convOptions[0]);
						convDiag.showDialog();
						if (convDiag.wasCanceled())
							return;
						unsignedConvSelec = convDiag.getNextChoiceIndex();
						wholeDataset = convertToUnsigned(wholeDataset, unsignedConvSelec);
					}

					long stackSize = extent[2] * extent[3];
					long singleVolumeSize = extent[1] * stackSize;
					int size = (int) stackSize;

					for (int volIDX = 0; volIDX < extent[0]; ++volIDX) {
						if ((volIDX % progressDivisor) == 0)
							IJ.showProgress((float) volIDX / (float) extent[0]);
						// start[0] = volIDX;

						for (int lev = 0; lev < extent[1]; ++lev) {
							// select hyperslab for lev
							// start[1] = lev;
							// Object slice = var.read();
							int startIdx = (int) ((volIDX * singleVolumeSize * 3) + (lev * stackSize * 3));
							// long numElements = stackSize * 3;
							int endIdx = (int) (startIdx + stackSize * 3 - 1);

							copyPixels3(datatypeIfUnsupported, extent, stack, wholeDataset, size, startIdx, endIdx);
						}
					}

					IJ.showProgress(1.f);
					ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
					// new for hyperstack
					int nChannels = 3;
					int nSlices = (int) extent[1];
					int nFrames = (int) extent[0];
					logger.info("nFrames: " + Integer.toString(nFrames));
					logger.info("nSlices: " + Integer.toString(nSlices));

					logger.info("stackSize: " + Integer.toString(stack.getSize()));

					imp.setDimensions(nChannels, nSlices, nFrames);
					imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
					imp.setOpenAsHyperStack(true);
					imp.getCalibration().pixelDepth = pixelDepth;
					imp.getCalibration().pixelHeight = pixelHeight;
					imp.getCalibration().pixelWidth = pixelWith;
					imp.resetDisplayRange();
					imp.show();
				} else if (rank == 4) {
					if (extent[3] == 3) {
						logger.info("   Detected color Image (type RGB).");

						// create a new image stack and fill in the data
						ImageStack stack = new ImageStack((int) extent[2], (int) extent[1]);

						long[] dims = var.getDims(); // the dimension sizes
						// of the dataset
						long[] selected = var.getSelectedDims(); // the
						// selected
						// size of
						// the
						// dataet
						selected[0] = dims[0];
						selected[1] = dims[1];
						selected[2] = dims[2];
						selected[3] = dims[3];
						// long[] start = var.getStartDims(); // the off set
						// of
						// // the selection

						Object wholeDataset = var.read();
						// check for unsigned datatype
						int unsignedConvSelec = 0;
						Datatype dType = var.getDatatype();
						boolean isSigned16Bit = !dType.isUnsigned() && (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) && (dType.getDatatypeSize() == 2);
						if (isSigned16Bit) {
							GenericDialog convDiag = new GenericDialog("Unsigend to signed conversion");
							convDiag.addMessage("Detected unsigned datatype, which " + "is not supported.");
							String[] convOptions = new String[2];
							convOptions[0] = "cut off values";
							convOptions[1] = "convert to float";
							convDiag.addChoice("Please select an conversion option:", convOptions, convOptions[0]);
							convDiag.showDialog();
							if (convDiag.wasCanceled())
								return;
							unsignedConvSelec = convDiag.getNextChoiceIndex();
							wholeDataset = convertToUnsigned(wholeDataset, unsignedConvSelec);
						}

						long stackSize = extent[1] * extent[2] * 3;

						for (int lev = 0; lev < extent[0]; ++lev) {
							if ((lev % progressDivisor) == 0)
								IJ.showProgress((float) lev / (float) extent[0]);

							// // select hyperslab for lev
							// start[0] = lev;
							// Object slice = var.read();

							int startIdx = (int) (lev * stackSize);
							// long numElements = stackSize;
							int endIdx = (int) (startIdx + stackSize - 1);
							// Object slice = extractSubarray(wholeDataset,
							// startIdx, numElements);

							int size = (int) (extent[2] * extent[1]);
							copyPixels1(extent, stack, wholeDataset, startIdx, endIdx, size);
						}
						ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
						// new for hyperstack
						int nChannels = 3;
						int nSlices = (int) extent[0];
						int nFrames = 1;
						imp.setDimensions(nChannels, nSlices, nFrames);
						imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
						imp.setOpenAsHyperStack(true);
						imp.getCalibration().pixelDepth = pixelDepth;
						imp.getCalibration().pixelHeight = pixelHeight;
						imp.getCalibration().pixelWidth = pixelWith;
						imp.resetDisplayRange();
						imp.show();
						imp.updateStatusbarValue();
					} else // we have a HyperVolume
					{
						logger.info("   Detected HyperVolume (type GREYSCALE).");

						// create a new image stack and fill in the data
						ImageStack stack = new ImageStack((int) extent[3], (int) extent[2]);
						// read the whole dataset, since reading chunked
						// datasets
						// slice-wise is too slow

						long[] dims = var.getDims(); // the dimension sizes
						// of the dataset
						long[] selected = var.getSelectedDims(); // the
						// selected
						// size of
						// the
						// dataet
						selected[0] = dims[0];
						selected[1] = dims[1];
						selected[2] = dims[2];
						selected[3] = dims[3];
						// the selection
						Object wholeDataset = var.read();
						// check for unsigned datatype
						int unsignedConvSelec = 0;
						Datatype dType = var.getDatatype();
						boolean isSigned16Bit = !dType.isUnsigned() && (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) && (dType.getDatatypeSize() == 2);
						if (isSigned16Bit) {
							GenericDialog convDiag = new GenericDialog("Unsigend to signed conversion");
							convDiag.addMessage("Detected unsigned datatype, which " + "is not supported.");
							String[] convOptions = new String[2];
							convOptions[0] = "cut off values";
							convOptions[1] = "convert to float";
							convDiag.addChoice("Please select an conversion option:", convOptions, convOptions[0]);
							convDiag.showDialog();
							if (convDiag.wasCanceled())
								return;
							unsignedConvSelec = convDiag.getNextChoiceIndex();
							wholeDataset = convertToUnsigned(wholeDataset, unsignedConvSelec);
						}

						long stackSize = extent[2] * extent[3];
						long singleVolumeSize = extent[1] * stackSize;

						for (int volIDX = 0; volIDX < extent[0]; ++volIDX) {
							if ((volIDX % progressDivisor) == 0)
								IJ.showProgress((float) volIDX / (float) extent[0]);
							// start[0] = volIDX;

							for (int lev = 0; lev < extent[1]; ++lev) {
								// select hyperslab for lev
								// start[1] = lev;
								// Object slice = var.read();
								int startIdx = (int) ((volIDX * singleVolumeSize) + (lev * stackSize));
								int endIdx = (int) (startIdx + stackSize - 1);
								// long numElements = stackSize;

								convertDatatypesAndSlice(datatypeIfUnsupported, stack, wholeDataset, startIdx, endIdx);
							}
						}

						IJ.showProgress(1.f);
						ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
						// new for hyperstack
						int nChannels = 1;
						int nSlices = (int) extent[1];
						int nFrames = (int) extent[0];
						Integer nFramesI = nFrames;
						Integer nSlicesI = nSlices;
						logger.info("nFrames: " + nFramesI.toString());
						logger.info("nSlices: " + nSlicesI.toString());

						Integer myStackSize = stack.getSize();
						logger.info("stackSize: " + myStackSize.toString());

						imp.setDimensions(nChannels, nSlices, nFrames);
						imp.setOpenAsHyperStack(true);
						imp.getCalibration().pixelDepth = pixelDepth;
						imp.getCalibration().pixelHeight = pixelHeight;
						imp.getCalibration().pixelWidth = pixelWith;
						imp.resetDisplayRange();
						imp.show();
					}
				} else if (rank == 3 && extent[2] == 3) {
					logger.info("This is an rgb image");
					// create a new image stack and fill in the data
					ImageStack stack = new ImageStack((int) extent[1], (int) extent[0]);

					long[] selected = var.getSelectedDims(); // the
					// selected
					// size of
					// the
					// dataet
					selected[0] = extent[0];
					selected[1] = extent[1];
					selected[2] = extent[2];
					Object slice = var.read();
					// check for unsigned datatype
					int unsignedConvSelec = 0;
					Datatype dType = var.getDatatype();
					boolean isSigned16Bit = !dType.isUnsigned() && (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) && (dType.getDatatypeSize() == 2);
					if (isSigned16Bit) {
						GenericDialog convDiag = new GenericDialog("Unsigend to signed conversion");
						convDiag.addMessage("Detected unsigned datatype, which " + "is not supported.");
						String[] convOptions = new String[2];
						convOptions[0] = "cut off values";
						convOptions[1] = "convert to float";
						convDiag.addChoice("Please select an conversion option:", convOptions, convOptions[0]);
						convDiag.showDialog();
						if (convDiag.wasCanceled())
							return;
						unsignedConvSelec = convDiag.getNextChoiceIndex();
						slice = convertToUnsigned(slice, unsignedConvSelec);
					}

					int size = (int) (extent[1] * extent[0]);

					// ugly but working: copy pixel by pixel
					copyPixels2(extent, stack, slice, size);

					IJ.showProgress(1.f);
					ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
					// new for hyperstack
					int nChannels = 3;
					int nSlices = 1;
					int nFrames = 1;
					imp.setDimensions(nChannels, nSlices, nFrames);
					imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
					imp.setOpenAsHyperStack(true);
					imp.getCalibration().pixelDepth = pixelDepth;
					imp.getCalibration().pixelHeight = pixelHeight;
					imp.getCalibration().pixelWidth = pixelWith;
					imp.resetDisplayRange();
					imp.show();
					imp.updateStatusbarValue();
				} else if (rank == 3) {
					logger.info("Rank is 3");

					// create a new image stack and fill in the data
					ImageStack stack = new ImageStack((int) extent[2], (int) extent[1]);

					long[] selected = var.getSelectedDims(); // the
					// selected
					// size of
					// the
					// dataet
					selected[0] = extent[0];
					selected[1] = extent[1];
					selected[2] = extent[2];
					Object wholeDataset = var.read();
					// check for unsigned datatype
					int unsignedConvSelec = 0;
					Datatype dType = var.getDatatype();
					boolean isSigned16Bit = !dType.isUnsigned() && (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) && (dType.getDatatypeSize() == 2);

					if (isSigned16Bit) {
						GenericDialog convDiag = new GenericDialog("Unsigend to signed conversion");
						convDiag.addMessage("Detected unsigned datatype, which " + "is not supported.");
						String[] convOptions = new String[2];
						convOptions[0] = "cut off values";
						convOptions[1] = "convert to float";
						convDiag.addChoice("Please select an conversion option:", convOptions, convOptions[0]);
						convDiag.showDialog();
						if (convDiag.wasCanceled())
							return;
						unsignedConvSelec = convDiag.getNextChoiceIndex();
						wholeDataset = convertToUnsigned(wholeDataset, unsignedConvSelec);
					}

					long stackSize = extent[1] * extent[2];

					for (int lev = 0; lev < extent[0]; ++lev) {
						if ((lev % progressDivisor) == 0)
							IJ.showProgress((float) lev / (float) extent[0]);
						// select hyperslab for lev
						// start[0] = lev;
						// Object slice = var.read();

						int startIdx = (int) (lev * stackSize);
						int endIdx = (int) (startIdx + stackSize - 1);
						// long numElements = stackSize;
						convertDatatypesAndSlice(datatypeIfUnsupported, stack, wholeDataset, startIdx, endIdx);
					}
					IJ.showProgress(1.f);
					ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
					imp.getCalibration().pixelDepth = pixelDepth;
					imp.getCalibration().pixelHeight = pixelHeight;
					imp.getCalibration().pixelWidth = pixelWith;
					imp.resetDisplayRange();
					imp.show();
					imp.updateStatusbarValue();
				} else if (rank == 2) {

					// check if we have an unsupported datatype
					if (datatype.getDatatypeClass() == Datatype.CLASS_INTEGER && (datatype.getDatatypeSize() == 4 || datatype.getDatatypeSize() == 8)) {
						logger.info("Datatype not supported by ImageJ");
						GenericDialog typeSelDiag = new GenericDialog("Datatype Selection");
						typeSelDiag.addMessage("The datatype `" + datatype.getDatatypeDescription() + "` is not supported by ImageJ.\n\n");
						typeSelDiag.addMessage("Please select your wanted datatype.\n");
						String[] choices = new String[2];
						choices[0] = "float";
						choices[1] = "short";
						typeSelDiag.addChoice("      Possible types are", choices, "float");
						typeSelDiag.showDialog();

						if (typeSelDiag.wasCanceled()) {
							return;
						}
						int selection = typeSelDiag.getNextChoiceIndex();
						if (selection == 0) {
							logger.info("float selected");
							datatypeIfUnsupported = new H5Datatype(Datatype.CLASS_FLOAT, Datatype.NATIVE, Datatype.NATIVE, -1);
						}
						if (selection == 1) {
							logger.info("short selected");
							int typeSizeInByte = 2;
							datatypeIfUnsupported = new H5Datatype(Datatype.CLASS_INTEGER, typeSizeInByte, Datatype.NATIVE, -1);
						}
					}
					IJ.showProgress(0.f);
					ImageStack stack = new ImageStack((int) extent[1], (int) extent[0]);
					Object slice = var.read();
					// check for unsigned datatype
					int unsignedConvSelec = 0;
					Datatype dType = var.getDatatype();
					boolean isSigned16Bit = !dType.isUnsigned() && (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) && (dType.getDatatypeSize() == 2);
					if (isSigned16Bit) {
						GenericDialog convDiag = new GenericDialog("Unsigend to signed conversion");
						convDiag.addMessage("Detected unsigned datatype, which " + "is not supported.");
						String[] convOptions = new String[2];
						convOptions[0] = "cut off values";
						convOptions[1] = "convert to float";
						convDiag.addChoice("Please select an conversion option:", convOptions, convOptions[0]);
						convDiag.showDialog();
						if (convDiag.wasCanceled())
							return;
						unsignedConvSelec = convDiag.getNextChoiceIndex();
						slice = convertToUnsigned(slice, unsignedConvSelec);
					}

					if (slice instanceof byte[]) {
						byte[] tmp = (byte[]) slice;
						stack.addSlice(null, tmp);
					} else if (slice instanceof short[]) {
						short[] tmp = (short[]) slice;
						stack.addSlice(null, tmp);
					} else if (slice instanceof int[]) {
						int[] tmp = (int[]) slice;
						if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
							stack.addSlice(null, convertInt32ToFloat(tmp));
						}
						if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
							stack.addSlice(null, convertInt32ToShort(tmp));
						}
					} else if (slice instanceof long[]) {
						long[] tmp = (long[]) slice;
						if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
							stack.addSlice(null, convertInt64ToFloat(tmp));
						}
						if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
							stack.addSlice(null, convertInt64ToShort(tmp));
						}
					} else if (slice instanceof float[]) {
						float[] tmp = (float[]) slice;
						stack.addSlice(null, tmp);
					} else if (slice instanceof double[]) {
						float[] tmp = convertDoubleToFloat((double[]) slice);

						stack.addSlice(null, tmp);
					} else {
						// try to put pixels on stack
						stack.addSlice(null, slice);
					}
					IJ.showProgress(1.f);
					ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
					imp.getProcessor().resetMinAndMax();
					imp.show();

					ImageProcessor ips = imp.getProcessor();

					double imgMax = ips.getMax();
					double imgMin = ips.getMin();

					logger.info("   Min = " + imgMin + ", Max = " + imgMax);
					ips.setMinAndMax(imgMin, imgMax);
					imp.updateAndDraw();
					imp.show();
					imp.updateStatusbarValue();
				} else {
					System.err.println("   Error: Variable Dimensions " + rank + " not supported (yet).");
					IJ.showStatus("Variable Dimension " + rank + " not supported");
				}
			}

		} catch (Exception e) {
			logger.log(Level.WARNING, "Error while opening '" + directory + name + "'", e);
			IJ.showStatus("Error opening file.");
		} catch (OutOfMemoryError o) {
			IJ.outOfMemory("Load HDF5");
		}
		finally{
			try {
				if (inFile != null){
					inFile.close();
				}
			} catch (HDF5Exception err) {
				System.err.println("Error while closing '" + directory + name + "'");
				System.err.println(err);
				IJ.showStatus("Error closing file.");
			}
		}
		
		IJ.showProgress(1.0);
	}


	/**
	 * Selection of the datasets to visualize
	 * 
	 * @param datasets
	 * @return	List of datasets to visualize. If nothing selected the list will be empty
	 * @throws HDF5Exception
	 */
	private List<Dataset> selectDatasets(List<Dataset> datasets) throws HDF5Exception {
		
		List<Dataset> selectedDatasets = new ArrayList<>();
		GenericDialog gd = new GenericDialog("Variable Name Selection");
		gd.addMessage("Please select variables to be loaded.\n");
		
		if (datasets.size() < 1) {
			IJ.error("The file does not contain datasets");
		} else if (datasets.size() > 1000) {
			
			logger.info("#######");
			for(Dataset d: datasets){
				logger.info(d.getFullName());
			}
			logger.info("#######");
			
			gd = new GenericDialog("Variable Name Selection");
			gd.addMessage("There are lots of datasets in your file (check the log output which datasets are available)! Please enter the full path of the dataset to be displayed");
			gd.addStringField("Dataset", "");
			gd.showDialog();

			if (!gd.wasCanceled()) {
				String dsName = gd.getNextString();
				for(Dataset d: datasets){
					if(d.getFullName().equals(dsName)){
						selectedDatasets.add(d);
					}
				}
				if(selectedDatasets.isEmpty()){
					IJ.error("The file does not contain a variable with name " + "`" + dsName + "`!");
				}
			}
		} else {
			String[] varSelections = new String[datasets.size()];
			boolean[] defaultValues = new boolean[datasets.size()];
			for (int i = 0; i < datasets.size(); i++) {
				Dataset var = datasets.get(i);
				int rank = var.getRank();
				String title = rank + "D: " + var.getFullName() + "              " + var.getDatatype().getDatatypeDescription() + "( ";
				long[] extent = var.getDims();
				for (int d = 0; d < rank; ++d) {
					if (d != 0){
						title += "x";
					}
					title += extent[d];
				}
				title += ")";
				varSelections[i] = title;
				defaultValues[i] = false;
			}
			logger.info("Add checkbox group with " + datasets.size() + " rows");
			gd.addCheckboxGroup(datasets.size(), 1, varSelections, defaultValues);
			addScrollBars(gd);
			gd.showDialog();

			if (!gd.wasCanceled()) {
				// Get selected datasets
				for (int i = 0; i < datasets.size(); ++i) {
					if (gd.getNextBoolean()) {
						selectedDatasets.add(datasets.get(i));
					}
				}
			}
		}
		
		
		return selectedDatasets;
	}


	/**
	 * @param datatypeIfUnsupported
	 * @param stack
	 * @param wholeDataset
	 * @param startIdx
	 * @param endIdx
	 */
	private void convertDatatypesAndSlice(Datatype datatypeIfUnsupported, ImageStack stack, Object wholeDataset, int startIdx, int endIdx) {
		if (wholeDataset instanceof byte[]) {
			byte[] tmp = Arrays.copyOfRange((byte[]) wholeDataset, startIdx, endIdx);
			stack.addSlice(null, tmp);
		} else if (wholeDataset instanceof short[]) {
			short[] tmp = Arrays.copyOfRange((short[]) wholeDataset, startIdx, endIdx);
			stack.addSlice(null, tmp);
		} else if (wholeDataset instanceof int[]) {
			int[] tmp = Arrays.copyOfRange((int[]) wholeDataset, startIdx, endIdx);
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
				stack.addSlice(null, convertInt32ToFloat(tmp));
			}
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
				stack.addSlice(null, convertInt32ToShort(tmp));
			}
		} else if (wholeDataset instanceof long[]) {
			long[] tmp = Arrays.copyOfRange((long[]) wholeDataset, startIdx, endIdx);
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
				stack.addSlice(null, convertInt64ToFloat(tmp));
			}
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
				stack.addSlice(null, convertInt64ToShort(tmp));
			}
		} else if (wholeDataset instanceof float[]) {
			float[] tmp = Arrays.copyOfRange((float[]) wholeDataset, startIdx, endIdx);
			stack.addSlice(null, tmp);
		} else if (wholeDataset instanceof double[]) {
			float[] tmp = convertDoubleToFloat(Arrays.copyOfRange((double[]) wholeDataset, startIdx, endIdx));
			stack.addSlice(null, tmp);
		} else {
			logger.warning("Not supported array type");
		}
	}

	/**
	 * @param datatypeIfUnsupported
	 * @param extent
	 * @param stack
	 * @param wholeDataset
	 * @param size
	 * @param startIdx
	 * @param endIdx
	 */
	private void copyPixels3(Datatype datatypeIfUnsupported, long[] extent, ImageStack stack, Object wholeDataset, int size, int startIdx, int endIdx) {
		if (wholeDataset instanceof byte[]) {
			byte[] tmp = Arrays.copyOfRange((byte[]) wholeDataset, startIdx, endIdx);
			byte[] rChannel = new byte[size];
			byte[] gChannel = new byte[size];
			byte[] bChannel = new byte[size];
			for (int row = 0; row < extent[2]; ++row) {
				for (int col = 0; col < extent[3]; ++col) {
					int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
					int offset = (row * (int) extent[2]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof short[]) {
			short[] tmp = Arrays.copyOfRange((short[]) wholeDataset, startIdx, endIdx);
			short[] rChannel = new short[size];
			short[] gChannel = new short[size];
			short[] bChannel = new short[size];
			for (int row = 0; row < extent[2]; ++row) {
				for (int col = 0; col < extent[3]; ++col) {
					int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
					int offset = (row * (int) extent[2]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof int[]) {
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
				float[] tmp = convertInt32ToFloat(Arrays.copyOfRange((int[]) wholeDataset, startIdx, endIdx));
				float[] rChannel = new float[size];
				float[] gChannel = new float[size];
				float[] bChannel = new float[size];
				for (int row = 0; row < extent[2]; ++row) {
					for (int col = 0; col < extent[3]; ++col) {
						int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
						int offset = (row * (int) extent[2]) + col;
						rChannel[offset] = tmp[offsetRGB + 0];
						gChannel[offset] = tmp[offsetRGB + 1];
						bChannel[offset] = tmp[offsetRGB + 2];
					}
				}
				stack.addSlice(null, rChannel);
				stack.addSlice(null, gChannel);
				stack.addSlice(null, bChannel);
			}
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
				short[] tmp = convertInt32ToShort(Arrays.copyOfRange((int[]) wholeDataset, startIdx, endIdx));
				short[] rChannel = new short[size];
				short[] gChannel = new short[size];
				short[] bChannel = new short[size];
				for (int row = 0; row < extent[2]; ++row) {
					for (int col = 0; col < extent[3]; ++col) {
						int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
						int offset = (row * (int) extent[2]) + col;
						rChannel[offset] = tmp[offsetRGB + 0];
						gChannel[offset] = tmp[offsetRGB + 1];
						bChannel[offset] = tmp[offsetRGB + 2];
					}
				}
				stack.addSlice(null, rChannel);
				stack.addSlice(null, gChannel);
				stack.addSlice(null, bChannel);
			}
		} else if (wholeDataset instanceof long[]) {
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
				float[] tmp = convertInt64ToFloat(Arrays.copyOfRange((long[]) wholeDataset, startIdx, endIdx));
				float[] rChannel = new float[size];
				float[] gChannel = new float[size];
				float[] bChannel = new float[size];
				for (int row = 0; row < extent[2]; ++row) {
					for (int col = 0; col < extent[3]; ++col) {
						int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
						int offset = (row * (int) extent[2]) + col;
						rChannel[offset] = tmp[offsetRGB + 0];
						gChannel[offset] = tmp[offsetRGB + 1];
						bChannel[offset] = tmp[offsetRGB + 2];
					}
				}
				stack.addSlice(null, rChannel);
				stack.addSlice(null, gChannel);
				stack.addSlice(null, bChannel);
			}
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
				short[] tmp = convertInt64ToShort(Arrays.copyOfRange((long[]) wholeDataset, startIdx, endIdx));
				short[] rChannel = new short[size];
				short[] gChannel = new short[size];
				short[] bChannel = new short[size];
				for (int row = 0; row < extent[2]; ++row) {
					for (int col = 0; col < extent[3]; ++col) {
						int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
						int offset = (row * (int) extent[2]) + col;
						rChannel[offset] = tmp[offsetRGB + 0];
						gChannel[offset] = tmp[offsetRGB + 1];
						bChannel[offset] = tmp[offsetRGB + 2];
					}
				}
				stack.addSlice(null, rChannel);
				stack.addSlice(null, gChannel);
				stack.addSlice(null, bChannel);
			}
		} else if (wholeDataset instanceof float[]) {
			float[] tmp = Arrays.copyOfRange((float[]) wholeDataset, startIdx, endIdx);
			float[] rChannel = new float[size];
			float[] gChannel = new float[size];
			float[] bChannel = new float[size];
			for (int row = 0; row < extent[2]; ++row) {
				for (int col = 0; col < extent[3]; ++col) {
					int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
					int offset = (row * (int) extent[2]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof double[]) {
			float[] tmp = convertDoubleToFloat(Arrays.copyOfRange((double[]) wholeDataset, startIdx, endIdx));
			float[] rChannel = new float[size];
			float[] gChannel = new float[size];
			float[] bChannel = new float[size];
			for (int row = 0; row < extent[2]; ++row) {
				for (int col = 0; col < extent[3]; ++col) {
					int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
					int offset = (row * (int) extent[2]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else {
			logger.warning("Datatype not supported");
		}
	}


	/**
	 * @param extent
	 * @param stack
	 * @param slice
	 * @param size
	 */
	private void copyPixels2(long[] extent, ImageStack stack, Object slice, int size) {
		if (slice instanceof byte[]) {
			byte[] tmp = (byte[]) slice;
			byte[] rChannel = new byte[size];
			byte[] gChannel = new byte[size];
			byte[] bChannel = new byte[size];
			for (int row = 0; row < extent[0]; ++row) {
				for (int col = 0; col < extent[1]; ++col) {
					int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
					int offset = (row * (int) extent[1]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (slice instanceof short[]) {
			short[] tmp = (short[]) slice;
			short[] rChannel = new short[size];
			short[] gChannel = new short[size];
			short[] bChannel = new short[size];
			for (int row = 0; row < extent[0]; ++row) {
				for (int col = 0; col < extent[1]; ++col) {
					int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
					int offset = (row * (int) extent[1]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (slice instanceof int[]) {
			int[] tmp = (int[]) slice;
			int[] rChannel = new int[size];
			int[] gChannel = new int[size];
			int[] bChannel = new int[size];
			for (int row = 0; row < extent[0]; ++row) {
				for (int col = 0; col < extent[1]; ++col) {
					int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
					int offset = (row * (int) extent[1]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (slice instanceof long[]) {
			long[] tmp = (long[]) slice;
			long[] rChannel = new long[size];
			long[] gChannel = new long[size];
			long[] bChannel = new long[size];
			for (int row = 0; row < extent[0]; ++row) {
				for (int col = 0; col < extent[1]; ++col) {
					int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
					int offset = (row * (int) extent[1]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (slice instanceof float[]) {
			float[] tmp = (float[]) slice;
			float[] rChannel = new float[size];
			float[] gChannel = new float[size];
			float[] bChannel = new float[size];
			for (int row = 0; row < extent[0]; ++row) {
				for (int col = 0; col < extent[1]; ++col) {
					int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
					int offset = (row * (int) extent[1]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (slice instanceof double[]) {
			double[] tmp = (double[]) slice;
			double[] rChannel = new double[size];
			double[] gChannel = new double[size];
			double[] bChannel = new double[size];
			for (int row = 0; row < extent[0]; ++row) {
				for (int col = 0; col < extent[1]; ++col) {
					int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
					int offset = (row * (int) extent[1]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		}
	}


	/**
	 * @param extent
	 * @param stack
	 * @param wholeDataset
	 * @param startIdx
	 * @param endIdx
	 * @param size
	 */
	private void copyPixels1(long[] extent, ImageStack stack, Object wholeDataset, int startIdx, int endIdx, int size) {
		if (wholeDataset instanceof byte[]) {
			byte[] tmp = Arrays.copyOfRange((byte[]) wholeDataset, startIdx, endIdx);
			byte[] rChannel = new byte[size];
			byte[] gChannel = new byte[size];
			byte[] bChannel = new byte[size];
			for (int row = 0; row < extent[1]; ++row) {
				for (int col = 0; col < extent[2]; ++col) {
					int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
					int offset = (row * (int) extent[2]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof short[]) {
			short[] tmp = Arrays.copyOfRange((short[]) wholeDataset, startIdx, endIdx);
			short[] rChannel = new short[size];
			short[] gChannel = new short[size];
			short[] bChannel = new short[size];
			for (int row = 0; row < extent[1]; ++row) {
				for (int col = 0; col < extent[2]; ++col) {
					int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
					int offset = (row * (int) extent[2]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof int[]) {
			int[] tmp = Arrays.copyOfRange((int[]) wholeDataset, startIdx, endIdx);
			int[] rChannel = new int[size];
			int[] gChannel = new int[size];
			int[] bChannel = new int[size];
			for (int row = 0; row < extent[1]; ++row) {
				for (int col = 0; col < extent[2]; ++col) {
					int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
					int offset = (row * (int) extent[2]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof long[]) {
			long[] tmp = Arrays.copyOfRange((long[]) wholeDataset, startIdx, endIdx);
			long[] rChannel = new long[size];
			long[] gChannel = new long[size];
			long[] bChannel = new long[size];
			for (int row = 0; row < extent[1]; ++row) {
				for (int col = 0; col < extent[2]; ++col) {
					int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
					int offset = (row * (int) extent[2]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof float[]) {
			float[] tmp = Arrays.copyOfRange((float[]) wholeDataset, startIdx, endIdx);
			float[] rChannel = new float[size];
			float[] gChannel = new float[size];
			float[] bChannel = new float[size];
			for (int row = 0; row < extent[1]; ++row) {
				for (int col = 0; col < extent[2]; ++col) {
					int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
					int offset = (row * (int) extent[2]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof double[]) {
			double[] tmp = Arrays.copyOfRange((double[]) wholeDataset, startIdx, endIdx);
			double[] rChannel = new double[size];
			double[] gChannel = new double[size];
			double[] bChannel = new double[size];
			for (int row = 0; row < extent[1]; ++row) {
				for (int col = 0; col < extent[2]; ++col) {
					int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
					int offset = (row * (int) extent[2]) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		}
	}

	private float[] convertDoubleToFloat(double[] dataIn) {
		float[] dataOut = new float[dataIn.length];
		for (int index = 0; index < dataIn.length; index++) {
			dataOut[index] = (float) dataIn[index];
		}
		return dataOut;
	}
	
	
	private float[] convertInt32ToFloat(int[] array) {
		float[] narray = new float[array.length];
		for (int index = 0; index < array.length; index++) {
			narray[index] = array[index];
		}
		return narray;
	}
	
	private short[] convertInt32ToShort(int[] array) {
		short[] narray = new short[array.length];
		for (int index = 0; index < array.length; index++) {
			narray[index] = (short) array[index];
		}
		return narray;
	}
	
	private float[] convertInt64ToFloat(long[] array) {
		float[] narray = new float[array.length];
		for (int index = 0; index < array.length; index++) {
			narray[index] = array[index];
		}
		return narray;
	}

	private short[] convertInt64ToShort(long[] array) {
		short[] narray = new short[array.length];
		for (int index = 0; index < array.length; index++) {
			narray[index] = (short) array[index];
		}
		return narray;
	}

	private Object convertToUnsigned(Object dataIn, int unsignedConvSelec) {
		Object dataOut = null;
		if (unsignedConvSelec == 0) {
			// cut off values
			if (dataIn instanceof short[]) {
				short[] tmp = (short[]) dataIn;
				for (int i = 0; i < tmp.length; i++)
					if (tmp[i] < 0)
						tmp[i] = 0;
				dataOut = tmp;
			}
		} else if (unsignedConvSelec == 1) {
			// convert to float
			if (dataIn instanceof short[]) {
				logger.info("Converting to float");
				short[] tmpIn = (short[]) dataIn;
				float[] tmp = new float[tmpIn.length];
				for (int i = 0; i < tmp.length; i++)
					tmp[i] = (float) tmpIn[i];
				dataOut = tmp;
			}
		}
		return dataOut;
	}

	/** Adds AWT scroll bars to the given container. */
	public static void addScrollBars(Container pane) {
		GridBagLayout layout = (GridBagLayout) pane.getLayout();

		// extract components
		int count = pane.getComponentCount();
		Component[] c = new Component[count];
		GridBagConstraints[] gbc = new GridBagConstraints[count];
		for (int i = 0; i < count; i++) {
			c[i] = pane.getComponent(i);
			gbc[i] = layout.getConstraints(c[i]);
		}

		// clear components
		pane.removeAll();
		layout.invalidateLayout(pane);

		// create new container panel
		Panel newPane = new Panel();
		GridBagLayout newLayout = new GridBagLayout();
		newPane.setLayout(newLayout);
		for (int i = 0; i < count; i++) {
			newLayout.setConstraints(c[i], gbc[i]);
			newPane.add(c[i]);
		}

		// HACK - get preferred size for container panel
		// NB: don't know a better way:
		// - newPane.getPreferredSize() doesn't work
		// - newLayout.preferredLayoutSize(newPane) doesn't work
		Frame f = new Frame();
		f.setLayout(new BorderLayout());
		f.add(newPane, BorderLayout.WEST);
		f.pack();
		final Dimension size = newPane.getSize();
		f.remove(newPane);
		f.dispose();

		// compute best size for scrollable viewport
		size.width += 15;
		size.height += 15;
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int maxWidth = 3 * screen.width / 4;
		int maxHeight = 3 * screen.height / 4;
		if (size.width > maxWidth)
			size.width = maxWidth;
		if (size.height > maxHeight)
			size.height = maxHeight;

		// create scroll pane
		ScrollPane scroll = new ScrollPane() {
			private static final long serialVersionUID = 1L;
			public Dimension getPreferredSize() {
				return size;
			}
		};
		scroll.add(newPane);

		// add scroll pane to original container
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.anchor = GridBagConstraints.WEST;
		constraints.fill = GridBagConstraints.BOTH;
		constraints.weightx = 1.0;
		constraints.weighty = 1.0;
		layout.setConstraints(scroll, constraints);
		pane.add(scroll);
	}
}
