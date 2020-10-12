package ch.psi.imagej.hdf5;

import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.h5.H5File;
import ij.IJ;
import ij.ImagePlus;
import ij.CompositeImage;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;

import java.io.File;
import java.lang.reflect.Array;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HDF5Reader implements PlugIn {
	
	private static final Logger logger = Logger.getLogger(HDF5Reader.class.getName());
	
	/**
	 * Main function for testing
	 * @param args
	 */
	public static void main(String[] args){
		HDF5Reader r = new HDF5Reader();
//		r.run("");
		r.open("",false, "/Users/ebner/Desktop/pollen.h5", "/t0/channel0", true);
	}

	public void run(String arg) {
		open(arg, true, null, null, true);
	}

	/**
	 * Main function plugin
	 * arg is a space separated list of arguments that can be passed to the run method.
	 * arg looks something like this: "para1=value1 para2=value2 ....."
	 *
	 * Supported arguments for arg:
	 * open=&lt;path&gt;
	 * dataset=/your/path/to/dataset
	 *
	 */

	public ImageStack open(String arg, boolean interactive, String filename, String nameOfDataset, boolean virtualstack) {
		return open(arg, interactive, filename, nameOfDataset, virtualstack, true);
	}

	public ImageStack open(String arg, boolean interactive, String filename, String nameOfDataset, boolean virtualstack, boolean showImage) {

//		Map arguments = HDF5Reader.parseArguments(arg);

		File tfile = null;
		if(interactive) {
			OpenDialog od = new OpenDialog("Open HDF5 ...", arg);
			tfile = new File(od.getDirectory() + od.getFileName());
			if (!tfile.exists() || !tfile.canRead()) {
				IJ.showMessage("Cannot open file: "+tfile.getAbsolutePath());
				return null;
			}

			// Overwrite filename with selected filename
			filename = tfile.getAbsolutePath();

			IJ.showStatus("Loading HDF5 File: " + filename);
			IJ.showProgress(0.0);
		}
		
		// Read HDF5 file
		H5File file = null;
		boolean close = true;

		List<ImageStack> stacks = new ArrayList<>();
		ImageStack stack = null;

		try {
			file = new H5File(filename, H5File.READ);
			file.setMaxMembers(Integer.MAX_VALUE);
			file.open();

			List<Dataset> datasets = HDF5Utilities.getDatasets(file);

			DatasetSelection selectedDatasets = null;
			if(interactive){
				logger.info("Using manual selection");
				// Manual selection of the dataset and other parameters via a dialog
				selectedDatasets = selectDatasets(datasets);
			}
			else{
				logger.info("Using automatic selection");
				selectedDatasets = new DatasetSelection();
				for(Dataset dataset: datasets){
					if(dataset.getFullName().equals(nameOfDataset)){
						selectedDatasets.getDatasets().add(dataset);
						break; // we only support one selection for the time being
					}
				}
				selectedDatasets.setVirtualStack(virtualstack);
			}


			// TODO to be removed - Workaround virtual stack - keep HDF5 file open at the end 
			close=!selectedDatasets.isVirtualStack();

			// TODO Remove
			// Hack as a proof of principle
			if(selectedDatasets.isGroup()){
				
				for (Dataset var : selectedDatasets.getDatasets()) {
					if(stack == null){
						long[] dimensions= var.getDims();
						stack = new ImageStack((int) dimensions[1], (int) dimensions[0]);
					}
					
					Object wholeDataset = var.read();
					addSlice(stack, wholeDataset);
				}
				
				ImagePlus imp = new ImagePlus(filename, stack);
				imp.resetDisplayRange();
				if(showImage) {
					imp.show();
				}

				stacks.add(stack);
				return stack; // TODO should return stacks instead of stack
			}

			for (Dataset var : selectedDatasets.getDatasets()) {

				// Read dataset attributes and properties
				String datasetName = var.getName();
				Datatype datatype = var.getDatatype();
				int numberOfDimensions = var.getRank();
				long[] dimensions= var.getDims();

				logger.info("Reading dataset: " + datasetName + " Dimensions: " + numberOfDimensions + " Type: " + datatype.getDescription());


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

					stack = new ImageStack((int) dimensions[3], (int) dimensions[2]);
					stacks.add(stack);
					int stackSize = (int) (dimensions[2] * dimensions[3] * 3);
					int singleVolumeSize = (int) (dimensions[1] * stackSize);
					for (int volIDX = 0; volIDX < dimensions[0]; ++volIDX) {
						for (int lev = 0; lev < dimensions[1]; ++lev) {
							int startIdx = (volIDX * singleVolumeSize * 3) + (lev * stackSize);
							addSliceRGB(stack, wholeDataset, (int) dimensions[2], (int) dimensions[3], startIdx);
						}
					}

					ImagePlus imp = new ImagePlus(filename + " " + datasetName, stack);
					imp.setDimensions(3, (int) dimensions[1], (int) dimensions[0]);
					imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
					imp.setOpenAsHyperStack(true);
					imp.resetDisplayRange();
					if(showImage) {
						imp.show();
					}
					
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

					stack = new ImageStack((int) dimensions[2], (int) dimensions[1]);
					stacks.add(stack);
					int stackSize = (int) (dimensions[1] * dimensions[2] * 3);
					for (int lev = 0; lev < dimensions[0]; ++lev) {
						int startIdx = lev * stackSize;
						addSliceRGB( stack, wholeDataset, (int) dimensions[1], (int) dimensions[2], startIdx);
					}

					ImagePlus imp = new ImagePlus(filename + " " + datasetName, stack);
					imp.setDimensions(3, (int) dimensions[0], 1);
					imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
					imp.setOpenAsHyperStack(true);
					imp.resetDisplayRange();
					if(showImage) {
						imp.show();
					}
					
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

					stack = new ImageStack((int) dimensions[3], (int) dimensions[2]);
					stacks.add(stack);
					int size = (int) (dimensions[2] * dimensions[3]);
					long singleVolumeSize = dimensions[1] * size;
					for (int volIDX = 0; volIDX < dimensions[0]; ++volIDX) {
						for (int lev = 0; lev < dimensions[1]; ++lev) {
							int startIdx = (int) ((volIDX * singleVolumeSize) + (lev * size));
							addSlice(stack, wholeDataset, startIdx, size);
						}
					}

					ImagePlus imp = new ImagePlus(filename + " " + datasetName, stack);
					imp.setDimensions(1, (int) dimensions[1], (int) dimensions[0]);
					imp.setOpenAsHyperStack(true);
					imp.resetDisplayRange();
					if(showImage) {
						imp.show();
					}
					
				} else if (numberOfDimensions == 3 && dimensions[2] == 3) {
					logger.info("2D RGB Image");

					// Select what to readout - read the whole dataset, since
					// reading chunked datasets slice-wise is too slow
					long[] selected = var.getSelectedDims();
					selected[0] = dimensions[0];
					selected[1] = dimensions[1];
					selected[2] = dimensions[2];

					Object wholeDataset = var.read();

					stack = new ImageStack((int) dimensions[1], (int) dimensions[0]);
					stacks.add(stack);
					addSliceRGB(stack, wholeDataset, (int) dimensions[0], (int) dimensions[1]);

					ImagePlus imp = new ImagePlus(filename + " " + datasetName, stack);
					imp.setDimensions(3, 1, 1);
					imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
					imp.setOpenAsHyperStack(true);
					imp.resetDisplayRange();
					if(showImage) {
						imp.show();
					}
					
				} else if (numberOfDimensions == 3) {
					logger.info("3D Image");

					if(selectedDatasets.isVirtualStack()){
						logger.info("Use virtual stack");
						stack = new VirtualStackHDF5(file, var);
					}
					else{
						if(selectedDatasets.getSlice()!=null){
							
							// Select what to readout
							long[] selected = var.getSelectedDims();
							selected[0] = 1;
							selected[1] = dimensions[1];
							selected[2] = dimensions[2];
							
							long[] start = var.getStartDims();
							start[0] = selectedDatasets.getSlice();
	
							Object wholeDataset = var.read();
							
							stack = new ImageStack((int) dimensions[2], (int) dimensions[1]);
							int size = (int) (dimensions[1] * dimensions[2]);
							
	//						int startIdx = selectedDatasets.getSlice() * size;
							addSlice(stack, wholeDataset, 0, size);
						}
						else if(selectedDatasets.getModulo()!=null){
							logger.info("Read every "+selectedDatasets.getModulo()+" image");
							// Select what to readout
							
							stack = new ImageStack((int) dimensions[2], (int) dimensions[1]);
							
							for(int indexToRead=0;indexToRead<dimensions[0]; indexToRead=indexToRead+selectedDatasets.getModulo()){
								
								long[] selected = var.getSelectedDims();
								selected[0] = 1;
								selected[1] = dimensions[1];
								selected[2] = dimensions[2];
		
								long[] start = var.getStartDims();
								start[0] = indexToRead;
								
								Object wholeDataset = var.read();
		
								int size = (int) (dimensions[1] * dimensions[2]);
	//							int startIdx = selectedDatasets.getSlice() * size;
								addSlice(stack, wholeDataset, 0, size);
							}
						}
						else{
							// Select what to readout
							long[] selected = var.getSelectedDims();
							selected[0] = dimensions[0];
							selected[1] = dimensions[1];
							selected[2] = dimensions[2];
	
							
							Object wholeDataset = var.read();
	
							stack = new ImageStack((int) dimensions[2], (int) dimensions[1]);
							int size = (int) (dimensions[1] * dimensions[2]);
							
							for (int lev = 0; lev < dimensions[0]; ++lev) {
								int startIdx = lev * size;
								addSlice(stack, wholeDataset, startIdx, size);
							}
						}
					}

					stacks.add(stack);
					ImagePlus imp = new ImagePlusHDF5(filename + " " + datasetName, stack);
					imp.resetDisplayRange();
					if(showImage) {
						imp.show();
					}
					
				} else if (numberOfDimensions == 2) {
					logger.info("2D Image");
					
					Object wholeDataset = var.read();

					stack = new ImageStack((int) dimensions[1], (int) dimensions[0]);
					stacks.add(stack);
					addSlice(stack, wholeDataset);
					
					ImagePlus imp = new ImagePlus(filename + " " + datasetName, stack);
					imp.resetDisplayRange();
					if(showImage) {
						imp.show();
					}
					
				} else {
					IJ.showStatus("Variable Dimension " + numberOfDimensions + " not supported");
				}
			}

		} catch (Exception e) {
			logger.log(Level.WARNING, "Error while opening: " + filename, e);
			IJ.showStatus("Error while opening file: " + filename);
		} catch (OutOfMemoryError e) {
			IJ.outOfMemory("Out of memory while loading file: " + filename);
		} finally {
			try {
				// TODO workaround - to be removed
				if(close){
					if (file != null) {
						file.close();
					}
				}
			} catch (HDF5Exception e) {
				logger.log(Level.WARNING, "Error while closing: " + filename, e);
				IJ.showStatus("Error while closing: " + filename);
			}
		}

		IJ.showProgress(1.0);

		return stack; // TODO should return stacks instead of stack
	}

	/**
	 * Selection of the datasets to visualize
	 * 
	 * @param datasets
	 * @return	List of datasets to visualize. If nothing selected the list will be empty
	 * @throws HDF5Exception
	 */
	private DatasetSelection selectDatasets(List<Dataset> datasets) throws HDF5Exception {
		
		GenericDialog gd = new GenericDialog("Variable Name Selection");
		gd.addMessage("Please select variables to be loaded.\n");
		
		SelectionPanel panel = new SelectionPanel(datasets);
			
			gd = new GenericDialog("Variable Name Selection");
			gd.add(panel);
			gd.addMessage("");
			gd.pack();
			gd.showDialog();

			DatasetSelection selectedDatasets = new DatasetSelection();
			if (!gd.wasCanceled()) {
				selectedDatasets.setDatasets(panel.getSelectedValues());
				selectedDatasets.setGroup(panel.groupValues());
				selectedDatasets.setSlice(panel.getSlice());
				selectedDatasets.setModulo(panel.getModulo());
				selectedDatasets.setVirtualStack(panel.useVirtualStack());
			}
		
		return selectedDatasets;
	}

	/**
	 * Add slice to image stack
	 * @param stack		Stack to add slice
	 * @param dataset	Dataset to create slice from
	 * @param startIdx	Index of dataset to start to create slice
	 * @param size		Size of dataset to add
	 */
	private void addSlice(ImageStack stack, Object dataset, int startIdx, int size) {
		Object copy = Array.newInstance(dataset.getClass().getComponentType(), size);
		System.arraycopy(dataset, startIdx, copy, 0, size);
		addSlice(stack, copy);
	}
	
	
	/**
	 * Add slice to image stack
	 * @param stack		Stack to add slice
	 * @param dataset	Dataset to create slice from
	 */
	private void addSlice(ImageStack stack, Object dataset){
		if (dataset instanceof byte[]) {
			stack.addSlice(null, (byte[]) dataset);
		} else if (dataset instanceof short[]) {
			stack.addSlice(null, (short[]) dataset);
		} else if (dataset instanceof int[]) {
			stack.addSlice(null, HDF5Utilities.convertToFloat((int[]) dataset));
		} else if (dataset instanceof long[]) {
			stack.addSlice(null, HDF5Utilities.convertToFloat((long[]) dataset));
		} else if (dataset instanceof float[]) {
			stack.addSlice(null, (float[]) dataset);
		} else if (dataset instanceof double[]) {
			stack.addSlice(null, HDF5Utilities.convertToFloat((double[]) dataset));
		} else {
			logger.warning("Datatype not supported");
		}
	}
	

	/**
	 * Add RGB slice to image stack
	 * @param stack		Stack to add slice
	 * @param dataset	Dataset to create slice from
	 * @param nRows		Number of rows of the dataset
	 * @param nColumns	Number of columns of the dataset
	 */
	private void addSliceRGB(ImageStack stack, Object dataset, int nRows, int nColumns) {
		addSliceRGB(stack, dataset, nRows, nColumns, 0);
	}
	
	
	/**
	 * Add RGB slice to image stack
	 * @param stack		Stack to add slice
	 * @param dataset	Dataset to create slice from
	 * @param nRows		Number of rows of the dataset
	 * @param nColumns	Number of columns of the dataset
	 * @param startIdx	Index of dataset to start to create slice
	 */
	private void addSliceRGB(ImageStack stack, Object dataset, int nRows, int nColumns, int startIdx) {
		int size = nRows*nColumns;
		Class<?> type = dataset.getClass().getComponentType();
		
		Object r = Array.newInstance(type, size);
		Object g = Array.newInstance(type, size);
		Object b = Array.newInstance(type, size);
		
		for (int row = 0; row < nRows; ++row) {
			for (int col = 0; col < nColumns; ++col) {
				int offsetRGB = startIdx + (row * nColumns * 3) + (col * 3);
				int offset = (row * nColumns) + col;
				Array.set(r, offset,Array.get(dataset,offsetRGB + 0));
				Array.set(g, offset,Array.get(dataset,offsetRGB + 1));
				Array.set(b, offset,Array.get(dataset,offsetRGB + 2));
			}
		}
		stack.addSlice(null, r);
		stack.addSlice(null, g);
		stack.addSlice(null, b);
	}

	public static Map<String,String> parseArguments(String arg){

		/// ImageJ arguments look something like this: "para1=value1 para2=value2 ....."
		Map<String,String> map = new HashMap<>();
		arg = arg.trim();
		for(String argument: arg.split("\\s+")){
			String[] entry = argument.split("=");
			if(entry.length==2) {
				map.put(entry[0], entry[1]);
			}
			else{
				// ignore
				logger.warning("Cannot parse argument " + argument + " - Ignore");
			}
		}
		return map;
	}
}
