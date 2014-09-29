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
		IJ.showProgress(0.0);
		
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
				Datatype datatype = var.getDatatype();
				Datatype datatypeIfUnsupported = null;
				int numberOfDimensions = var.getRank();
				long[] dimensions= var.getDims();

				logger.info("Reading dataset: " + datasetName + " Dimensions: " + numberOfDimensions + " Type: " + datatype.getDatatypeDescription());

				// Check if datatype is supported
				datatypeIfUnsupported = checkIfDatatypeSupported(datatype);

				// Read dataset
				if (numberOfDimensions == 5 && dimensions[4] == 3) {
					logger.info("4D RGB Image (HyperVolume)");

					// Select what to readout - read the whole dataset, since
					// reading chunked datasets slice-wise is too slow
					long[] selected = var.getSelectedDims();
					selected[0] = dimensions[0];
					selected[1] = dimensions[1];
					selected[2] = dimensions[2];
					selected[3] = dimensions[3];
					selected[4] = dimensions[4];

					Object wholeDataset = var.read();
					wholeDataset = checkUnsigned(datatype, wholeDataset);

					ImageStack stack = new ImageStack((int) dimensions[3], (int) dimensions[2]);
					long stackSize = dimensions[2] * dimensions[3];
					long singleVolumeSize = dimensions[1] * stackSize;
					for (int volIDX = 0; volIDX < dimensions[0]; ++volIDX) {
						for (int lev = 0; lev < dimensions[1]; ++lev) {
							int startIdx = (int) ((volIDX * singleVolumeSize * 3) + (lev * stackSize * 3));
							int endIdx = (int) (startIdx + stackSize * 3 - 1);
							copyPixels3(datatypeIfUnsupported, (int) dimensions[2], (int) dimensions[3], stack, wholeDataset, (int) stackSize, startIdx, endIdx);
						}
					}

					ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
					imp.setDimensions(3, (int) dimensions[1], (int) dimensions[0]);
					imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
					imp.setOpenAsHyperStack(true);
					imp.resetDisplayRange();
					imp.show();
					
				} else if (numberOfDimensions == 4 && dimensions[3] == 3) {
					logger.info("3D RGB Image");

					// Select what to readout - read the whole dataset, since
					// reading chunked datasets slice-wise is too slow
					long[] selected = var.getSelectedDims();
					selected[0] = dimensions[0];
					selected[1] = dimensions[1];
					selected[2] = dimensions[2];
					selected[3] = dimensions[3];

					Object wholeDataset = var.read();
					wholeDataset = checkUnsigned(datatype, wholeDataset);

					ImageStack stack = new ImageStack((int) dimensions[2], (int) dimensions[1]);
					long stackSize = dimensions[1] * dimensions[2] * 3;
					for (int lev = 0; lev < dimensions[0]; ++lev) {
						int startIdx = (int) (lev * stackSize);
						int endIdx = (int) (startIdx + stackSize - 1);
						int size = (int) (dimensions[2] * dimensions[1]);
						copyPixels1((int) dimensions[1], (int) dimensions[2], stack, wholeDataset, size, startIdx, endIdx);
					}

					ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
					imp.setDimensions(3, (int) dimensions[0], 1);
					imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
					imp.setOpenAsHyperStack(true);
					imp.resetDisplayRange();
					imp.show();
					
				} else if (numberOfDimensions == 4) {
					logger.info("4D Image (HyperVolume)");

					// Select what to readout - read the whole dataset, since
					// reading chunked datasets slice-wise is too slow
					long[] selected = var.getSelectedDims();
					selected[0] = dimensions[0];
					selected[1] = dimensions[1];
					selected[2] = dimensions[2];
					selected[3] = dimensions[3];

					Object wholeDataset = var.read();
					wholeDataset = checkUnsigned(datatype, wholeDataset);

					ImageStack stack = new ImageStack((int) dimensions[3], (int) dimensions[2]);
					long stackSize = dimensions[2] * dimensions[3];
					long singleVolumeSize = dimensions[1] * stackSize;
					for (int volIDX = 0; volIDX < dimensions[0]; ++volIDX) {
						for (int lev = 0; lev < dimensions[1]; ++lev) {
							int startIdx = (int) ((volIDX * singleVolumeSize) + (lev * stackSize));
							int endIdx = (int) (startIdx + stackSize);
							convertDatatypesAndSlice(datatypeIfUnsupported, stack, wholeDataset, startIdx, endIdx);
						}
					}

					ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
					imp.setDimensions(1, (int) dimensions[1], (int) dimensions[0]);
					imp.setOpenAsHyperStack(true);
					imp.resetDisplayRange();
					imp.show();
					
				} else if (numberOfDimensions == 3 && dimensions[2] == 3) {
					logger.info("2D RGB Image");

					// Select what to readout - read the whole dataset, since
					// reading chunked datasets slice-wise is too slow
					long[] selected = var.getSelectedDims();
					selected[0] = dimensions[0];
					selected[1] = dimensions[1];
					selected[2] = dimensions[2];

					Object wholeDataset = var.read();
					wholeDataset = checkUnsigned(datatype, wholeDataset);

					ImageStack stack = new ImageStack((int) dimensions[1], (int) dimensions[0]);
					copyPixels2((int) dimensions[0], (int) dimensions[1], stack, wholeDataset, (int) (dimensions[1] * dimensions[0]));

					ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
					imp.setDimensions(3, 1, 1);
					imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
					imp.setOpenAsHyperStack(true);
					imp.resetDisplayRange();
					imp.show();
					
				} else if (numberOfDimensions == 3) {
					logger.info("3D Image");

					// Select what to readout
					long[] selected = var.getSelectedDims();
					selected[0] = dimensions[0];
					selected[1] = dimensions[1];
					selected[2] = dimensions[2];

					Object wholeDataset = var.read();
					wholeDataset = checkUnsigned(datatype, wholeDataset);

					ImageStack stack = new ImageStack((int) dimensions[2], (int) dimensions[1]);
					long stackSize = dimensions[1] * dimensions[2];
					for (int lev = 0; lev < dimensions[0]; ++lev) {
						int startIdx = (int) (lev * stackSize);
						int endIdx = (int) (startIdx + stackSize);
						convertDatatypesAndSlice(datatypeIfUnsupported, stack, wholeDataset, startIdx, endIdx);
					}

					ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
					imp.resetDisplayRange();
					imp.show();
					
				} else if (numberOfDimensions == 2) {
					logger.info("2D Image");
					
					Object wholeDataset = var.read();
					wholeDataset = checkUnsigned(datatype, wholeDataset);

					ImageStack stack = new ImageStack((int) dimensions[1], (int) dimensions[0]);
					if (wholeDataset instanceof byte[]) {
						byte[] tmp = (byte[]) wholeDataset;
						stack.addSlice(null, tmp);
					} else if (wholeDataset instanceof short[]) {
						short[] tmp = (short[]) wholeDataset;
						stack.addSlice(null, tmp);
					} else if (wholeDataset instanceof int[]) {
						int[] tmp = (int[]) wholeDataset;
						if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
							stack.addSlice(null, convertInt32ToFloat(tmp));
						}
						if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
							stack.addSlice(null, convertInt32ToShort(tmp));
						}
					} else if (wholeDataset instanceof long[]) {
						long[] tmp = (long[]) wholeDataset;
						if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
							stack.addSlice(null, convertInt64ToFloat(tmp));
						}
						if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
							stack.addSlice(null, convertInt64ToShort(tmp));
						}
					} else if (wholeDataset instanceof float[]) {
						float[] tmp = (float[]) wholeDataset;
						stack.addSlice(null, tmp);
					} else if (wholeDataset instanceof double[]) {
						float[] tmp = convertDoubleToFloat((double[]) wholeDataset);
						stack.addSlice(null, tmp);
					} else {
						// try to put pixels on stack
						stack.addSlice(null, wholeDataset);
					}
					
					ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
					imp.resetDisplayRange();
					imp.show();
					
				} else {
					System.err.println("   Error: Variable Dimensions " + numberOfDimensions + " not supported (yet).");
					IJ.showStatus("Variable Dimension " + numberOfDimensions + " not supported");
				}
			}

		} catch (Exception e) {
			logger.log(Level.WARNING, "Error while opening '" + directory + name + "'", e);
			IJ.showStatus("Error while opening file '" + directory + name + "'");
		} catch (OutOfMemoryError o) {
			IJ.outOfMemory("Out of memory while loading file '" + directory + name + "'");
		} finally {
			try {
				if (inFile != null) {
					inFile.close();
				}
			} catch (HDF5Exception err) {
				logger.log(Level.WARNING, "Error while closing '" + directory + name + "'", err);
				IJ.showStatus("Error while closing '" + directory + name + "'");
			}
		}

		IJ.showProgress(1.0);
	}


	/**
	 * @param datatype
	 * @param wholeDataset
	 * @return
	 */
	private Object checkUnsigned(Datatype datatype, Object wholeDataset) {
		// check for unsigned datatype
		int unsignedConvSelec = 0;
		boolean isSigned16Bit = !datatype.isUnsigned() && (datatype.getDatatypeClass() == Datatype.CLASS_INTEGER) && (datatype.getDatatypeSize() == 2);
		if (isSigned16Bit) {
			GenericDialog convDiag = new GenericDialog("Unsigend to signed conversion");
			convDiag.addMessage("Detected unsigned datatype, which " + "is not supported.");
			String[] convOptions = new String[2];
			convOptions[0] = "cut off values";
			convOptions[1] = "convert to float";
			convDiag.addChoice("Please select an conversion option:", convOptions, convOptions[0]);
			convDiag.showDialog();
			if (convDiag.wasCanceled())
				return wholeDataset;
			unsignedConvSelec = convDiag.getNextChoiceIndex();
			wholeDataset = convertToUnsigned(wholeDataset, unsignedConvSelec);
		}
		return wholeDataset;
	}


	/**
	 * Returns a datatype if native datatype is not supported. Returns null if datatype is supported.
	 * @param datatype
	 * @return
	 */
	private Datatype checkIfDatatypeSupported(Datatype datatype) {
		Datatype datatypeIfUnsupported = null;
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
				return null;
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
		return datatypeIfUnsupported;
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
	 * @param nRows (extent[2])
	 * @param nColumns (extent[3])
	 * @param stack
	 * @param wholeDataset
	 * @param size
	 * @param startIdx
	 * @param endIdx
	 */
	private void copyPixels3(Datatype datatypeIfUnsupported, int nRows, int nColumns, ImageStack stack, Object wholeDataset, int size, int startIdx, int endIdx) {
		if (wholeDataset instanceof byte[]) {
			byte[] tmp = Arrays.copyOfRange((byte[]) wholeDataset, startIdx, endIdx);
			byte[] rChannel = new byte[size];
			byte[] gChannel = new byte[size];
			byte[] bChannel = new byte[size];
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nRows * 3) + (col * 3);
					int offset = (row * nRows) + col;
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
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nRows * 3) + (col * 3);
					int offset = (row * nRows) + col;
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
				for (int row = 0; row < nRows; ++row) {
					for (int col = 0; col < nColumns; ++col) {
						int offsetRGB = (row * nRows * 3) + (col * 3);
						int offset = (row * nRows) + col;
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
				for (int row = 0; row < nRows; ++row) {
					for (int col = 0; col < nColumns; ++col) {
						int offsetRGB = (row * nRows * 3) + (col * 3);
						int offset = (row * nRows) + col;
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
				for (int row = 0; row < nRows; ++row) {
					for (int col = 0; col < nColumns; ++col) {
						int offsetRGB = (row * nRows * 3) + (col * 3);
						int offset = (row * nRows) + col;
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
				for (int row = 0; row < nRows; ++row) {
					for (int col = 0; col < nColumns; ++col) {
						int offsetRGB = (row * nRows * 3) + (col * 3);
						int offset = (row * nRows) + col;
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
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nRows * 3) + (col * 3);
					int offset = (row * nRows) + col;
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
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nRows * 3) + (col * 3);
					int offset = (row * nRows) + col;
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
	 * @param nRows (extent[0])
	 * @param nColumns (extent[1])
	 * @param stack
	 * @param wholeDataset
	 * @param size
	 */
	private void copyPixels2(int nRows, int nColumns, ImageStack stack, Object wholeDataset, int size) {
		if (wholeDataset instanceof byte[]) {
			byte[] tmp = (byte[]) wholeDataset;
			byte[] rChannel = new byte[size];
			byte[] gChannel = new byte[size];
			byte[] bChannel = new byte[size];
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof short[]) {
			short[] tmp = (short[]) wholeDataset;
			short[] rChannel = new short[size];
			short[] gChannel = new short[size];
			short[] bChannel = new short[size];
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof int[]) {
			int[] tmp = (int[]) wholeDataset;
			int[] rChannel = new int[size];
			int[] gChannel = new int[size];
			int[] bChannel = new int[size];
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof long[]) {
			long[] tmp = (long[]) wholeDataset;
			long[] rChannel = new long[size];
			long[] gChannel = new long[size];
			long[] bChannel = new long[size];
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof float[]) {
			float[] tmp = (float[]) wholeDataset;
			float[] rChannel = new float[size];
			float[] gChannel = new float[size];
			float[] bChannel = new float[size];
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
					rChannel[offset] = tmp[offsetRGB + 0];
					gChannel[offset] = tmp[offsetRGB + 1];
					bChannel[offset] = tmp[offsetRGB + 2];
				}
			}
			stack.addSlice(null, rChannel);
			stack.addSlice(null, gChannel);
			stack.addSlice(null, bChannel);
		} else if (wholeDataset instanceof double[]) {
			double[] tmp = (double[]) wholeDataset;
			double[] rChannel = new double[size];
			double[] gChannel = new double[size];
			double[] bChannel = new double[size];
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
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
	 * @param nRows
	 * @param nColumns
	 * @param stack
	 * @param wholeDataset
	 * @param startIdx
	 * @param endIdx
	 * @param size
	 */
	private void copyPixels1(int nRows, int nColumns, ImageStack stack, Object wholeDataset, int size, int startIdx, int endIdx) {
		if (wholeDataset instanceof byte[]) {
			byte[] tmp = Arrays.copyOfRange((byte[]) wholeDataset, startIdx, endIdx);
			byte[] rChannel = new byte[size];
			byte[] gChannel = new byte[size];
			byte[] bChannel = new byte[size];
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
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
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
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
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
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
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
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
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
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
			for (int row = 0; row < nRows; ++row) {
				for (int col = 0; col < nColumns; ++col) {
					int offsetRGB = (row * nColumns * 3) + (col * 3);
					int offset = (row * nColumns) + col;
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
