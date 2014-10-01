package ch.psi.imagej.hdf5;

import ij.IJ;
import ij.ImagePlus;
import ij.CompositeImage;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;

import java.io.File;
import java.lang.reflect.Array;
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
					int stackSize = (int) (dimensions[2] * dimensions[3] * 3);
					int singleVolumeSize = (int) (dimensions[1] * stackSize);
					for (int volIDX = 0; volIDX < dimensions[0]; ++volIDX) {
						for (int lev = 0; lev < dimensions[1]; ++lev) {
							int startIdx = (volIDX * singleVolumeSize * 3) + (lev * stackSize);
							addSliceRGB(stack, wholeDataset, (int) dimensions[2], (int) dimensions[3], startIdx);
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
					int stackSize = (int) (dimensions[1] * dimensions[2] * 3);
					for (int lev = 0; lev < dimensions[0]; ++lev) {
						int startIdx = lev * stackSize;
						addSliceRGB( stack, wholeDataset, (int) dimensions[1], (int) dimensions[2], startIdx);
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
							addSlice(datatypeIfUnsupported, stack, wholeDataset, startIdx, endIdx);
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
					addSliceRGB(stack, wholeDataset, (int) dimensions[0], (int) dimensions[1]);

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
					int stackSize = (int) (dimensions[1] * dimensions[2]);
					for (int lev = 0; lev < dimensions[0]; ++lev) {
						int startIdx = lev * stackSize;
						int endIdx = startIdx + stackSize;
						addSlice(datatypeIfUnsupported, stack, wholeDataset, startIdx, endIdx);
					}

					ImagePlus imp = new ImagePlus(directory + name + " " + datasetName, stack);
					imp.resetDisplayRange();
					imp.show();
					
				} else if (numberOfDimensions == 2) {
					logger.info("2D Image");
					
					Object wholeDataset = var.read();
					wholeDataset = checkUnsigned(datatype, wholeDataset);

					ImageStack stack = new ImageStack((int) dimensions[1], (int) dimensions[0]);
					addSlice(datatypeIfUnsupported, stack, wholeDataset);
					
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
		if (!datatype.isUnsigned() && (datatype.getDatatypeClass() == Datatype.CLASS_INTEGER) && (datatype.getDatatypeSize() == 2)) {
			GenericDialog dialog = new GenericDialog("Unsigned to signed conversion");
			dialog.addMessage("Detected unsigned datatype, which is not supported.");
			String[] convOptions = {"cut off values", "convert to float"};
			dialog.addChoice("Please select an conversion option:", convOptions, convOptions[0]);
			dialog.showDialog();
			if (!dialog.wasCanceled()){
				int unsignedConvSelec = dialog.getNextChoiceIndex();
				if (wholeDataset instanceof short[]) {
					if (unsignedConvSelec == 0) {
						wholeDataset = HDF5Utilities.cutoffNegative((short[]) wholeDataset);
					} else if (unsignedConvSelec == 1) {
						wholeDataset = HDF5Utilities.convertToFloat((short[]) wholeDataset);
					}
				}
			}
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
		if (datatype.getDatatypeClass() == Datatype.CLASS_INTEGER && (datatype.getDatatypeSize() == 4 || datatype.getDatatypeSize() == 8)) {
			logger.info("Datatype not supported by ImageJ");
			GenericDialog typeSelDiag = new GenericDialog("Datatype Selection");
			typeSelDiag.addMessage("The datatype `" + datatype.getDatatypeDescription() + "` is not supported by ImageJ.\n\n");
			typeSelDiag.addMessage("Please select datatype to convert to.\n");
			String[] choices = new String[2];
			choices[0] = "float";
			choices[1] = "short";
			typeSelDiag.addChoice("      Possible types are", choices, "float");
			typeSelDiag.showDialog();

			if (!typeSelDiag.wasCanceled()) {
				int selection = typeSelDiag.getNextChoiceIndex();
				if (selection == 0) {
					datatypeIfUnsupported = new H5Datatype(Datatype.CLASS_FLOAT, Datatype.NATIVE, Datatype.NATIVE, -1);
				}
				if (selection == 1) {
					datatypeIfUnsupported = new H5Datatype(Datatype.CLASS_INTEGER, 2, Datatype.NATIVE, -1);
				}
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
	 * Add slice to image stack
	 * @param datatypeIfUnsupported
	 * @param stack
	 * @param wholeDataset
	 * @param startIdx
	 * @param endIdx
	 */
	private void addSlice(Datatype datatypeIfUnsupported, ImageStack stack, Object wholeDataset, int startIdx, int endIdx) {
		if (wholeDataset instanceof byte[]) {
			byte[] tmp = Arrays.copyOfRange((byte[]) wholeDataset, startIdx, endIdx);
			stack.addSlice(null, tmp);
		} else if (wholeDataset instanceof short[]) {
			short[] tmp = Arrays.copyOfRange((short[]) wholeDataset, startIdx, endIdx);
			stack.addSlice(null, tmp);
		} else if (wholeDataset instanceof int[]) {
			int[] tmp = Arrays.copyOfRange((int[]) wholeDataset, startIdx, endIdx);
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
				stack.addSlice(null, HDF5Utilities.convertToFloat(tmp));
			}
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
				stack.addSlice(null, HDF5Utilities.convertToShort(tmp));
			}
		} else if (wholeDataset instanceof long[]) {
			long[] tmp = Arrays.copyOfRange((long[]) wholeDataset, startIdx, endIdx);
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
				stack.addSlice(null, HDF5Utilities.convertToFloat(tmp));
			}
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
				stack.addSlice(null, HDF5Utilities.convertToShort(tmp));
			}
		} else if (wholeDataset instanceof float[]) {
			float[] tmp = Arrays.copyOfRange((float[]) wholeDataset, startIdx, endIdx);
			stack.addSlice(null, tmp);
		} else if (wholeDataset instanceof double[]) {
			float[] tmp = HDF5Utilities.convertToFloat(Arrays.copyOfRange((double[]) wholeDataset, startIdx, endIdx));
			stack.addSlice(null, tmp);
		} else {
			logger.warning("Datatype not supported");
		}
	}
	
	/**
	 * Add slice to image stack
	 * @param datatypeIfUnsupported
	 * @param stack
	 * @param wholeDataset
	 */
	private void addSlice(Datatype datatypeIfUnsupported, ImageStack stack, Object wholeDataset){
		if (wholeDataset instanceof byte[]) {
			byte[] tmp = (byte[]) wholeDataset;
			stack.addSlice(null, tmp);
		} else if (wholeDataset instanceof short[]) {
			short[] tmp = (short[]) wholeDataset;
			stack.addSlice(null, tmp);
		} else if (wholeDataset instanceof int[]) {
			int[] tmp = (int[]) wholeDataset;
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
				stack.addSlice(null, HDF5Utilities.convertToFloat(tmp));
			}
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
				stack.addSlice(null, HDF5Utilities.convertToShort(tmp));
			}
		} else if (wholeDataset instanceof long[]) {
			long[] tmp = (long[]) wholeDataset;
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_FLOAT) {
				stack.addSlice(null, HDF5Utilities.convertToFloat(tmp));
			}
			if (datatypeIfUnsupported.getDatatypeClass() == Datatype.CLASS_INTEGER) {
				stack.addSlice(null, HDF5Utilities.convertToShort(tmp));
			}
		} else if (wholeDataset instanceof float[]) {
			float[] tmp = (float[]) wholeDataset;
			stack.addSlice(null, tmp);
		} else if (wholeDataset instanceof double[]) {
			float[] tmp = HDF5Utilities.convertToFloat((double[]) wholeDataset);
			stack.addSlice(null, tmp);
		} else {
			logger.warning("Datatype not supported");
		}
	}
	

	/**
	 * Add RGB slice to stack
	 * @param stack
	 * @param wholeDataset
	 * @param nRows
	 * @param nColumns
	 * @param startIdx
	 * @param endIdx
	 */
	private void addSliceRGB(ImageStack stack, Object wholeDataset, int nRows, int nColumns, int startIdx) {
		if(wholeDataset.getClass().isArray()){
			int size = nRows*nColumns;
			Object copy = Array.newInstance(wholeDataset.getClass().getComponentType(), size);
			System.arraycopy(wholeDataset, startIdx, copy, 0, size);
			addSliceRGB(stack, copy, nRows, nColumns);
		}
	}
	
	/**
	 * Add RGB slice to stack
	 * @param stack
	 * @param wholeDataset
	 * @param nRows
	 * @param nColumns
	 */
	private void addSliceRGB(ImageStack stack, Object wholeDataset, int nRows, int nColumns) {
		int size = nRows*nColumns;
		Class<?> type = wholeDataset.getClass().getComponentType();
		
		Object r = Array.newInstance(type, size);
		Object g = Array.newInstance(type, size);
		Object b = Array.newInstance(type, size);
		
		for (int row = 0; row < nRows; ++row) {
			for (int col = 0; col < nColumns; ++col) {
				int offsetRGB = (row * nColumns * 3) + (col * 3);
				int offset = (row * nColumns) + col;
				Array.set(r, offset,Array.get(wholeDataset,offsetRGB + 0));
				Array.set(g, offset,Array.get(wholeDataset,offsetRGB + 1));
				Array.set(b, offset,Array.get(wholeDataset,offsetRGB + 2));
			}
		}
		stack.addSlice(null, r);
		stack.addSlice(null, g);
		stack.addSlice(null, b);
	}

	
	/**
	 * Add AWT scroll bars to the given container.
	 */
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
		if (size.width > maxWidth){
			size.width = maxWidth;
		}
		if (size.height > maxHeight){
			size.height = maxHeight;
		}

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
