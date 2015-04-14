package ch.psi.imagej.hdf5;

import java.util.logging.Level;
import java.util.logging.Logger;

import ncsa.hdf.object.Dataset;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class VirtualStackHDF5 extends ImageStack {
	
	
	private static final Logger logger = Logger.getLogger(VirtualStackHDF5.class.getName());
	
	private int bitDepth = 0;
	private Dataset dataset;
	
	public VirtualStackHDF5(Dataset dataset){
		super((int) dataset.getDims()[2], (int) dataset.getDims()[1]);
		this.dataset = dataset;
	}
	
	/** Does noting. */
	public void addSlice(String sliceLabel, Object pixels) {
	}

	/** Does nothing.. */
	public void addSlice(String sliceLabel, ImageProcessor ip) {
	}

	/** Does noting. */
	public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
	}

	/** Does noting. */
	public void deleteSlice(int n) {
	}

	/** Does noting. */
	public void deleteLastSlice() {
	}

	public Object getPixels(int slice) {
		try {
			long[] dimensions = dataset.getDims();

			// Select what to readout
			long[] selected = dataset.getSelectedDims();
			selected[0] = 1;
			selected[1] = dimensions[1];
			selected[2] = dimensions[2];

			long[] start = dataset.getStartDims();
			start[0] = slice-1; // Indexing at image J starts at 1

			Object wholeDataset = dataset.read();

			if (wholeDataset instanceof byte[]) {
				return (byte[]) wholeDataset;
			} else if (wholeDataset instanceof short[]) {
				return (short[]) wholeDataset;
			} else if (wholeDataset instanceof int[]) {
				return HDF5Utilities.convertToFloat((int[]) wholeDataset);
			} else if (wholeDataset instanceof long[]) {
				return HDF5Utilities.convertToFloat((long[]) wholeDataset);
			} else if (wholeDataset instanceof float[]) {
				return (float[]) wholeDataset;
			} else if (wholeDataset instanceof double[]) {
				return HDF5Utilities.convertToFloat((double[]) wholeDataset);
			} else {
				logger.warning("Datatype not supported");
			}
		} catch (OutOfMemoryError | Exception e) {
			logger.log(Level.WARNING, "Unable to open slice", e);
		}
		
		return null;
	}

	/**
	 * Assigns a pixel array to the specified slice, were 1<=n<=nslices.
	 */
	public void setPixels(Object pixels, int n) {
	}

	/**
	 * Returns an ImageProcessor for the specified slice, were 1<=n<=nslices.
	 * Returns null if the stack is empty.
	 */
	public ImageProcessor getProcessor(int slice) {
		
		long[] dimensions = dataset.getDims();
		final Object pixels = getPixels(slice);
		
		// Todo support more ImageProcessor types
		ImageProcessor ip;		
		
		if (pixels instanceof byte[]){
			ip = new ByteProcessor((int) dimensions[2], (int) dimensions[1]);
		}
		else if (pixels instanceof short[]){
			ip = new ShortProcessor((int) dimensions[2], (int) dimensions[1]);
		}
		else if (pixels instanceof int[]){
			ip = new ColorProcessor((int) dimensions[2], (int) dimensions[1]);
		}
		else if (pixels instanceof float[]){
			ip = new FloatProcessor((int) dimensions[2], (int) dimensions[1]);
		}
		else {
			throw new IllegalArgumentException("Unknown stack type");
		}
		
		ip.setPixels(pixels);
		return ip;
	}

	/** Returns the number of slices in this stack. */
	public int getSize() {
		return (int) this.dataset.getDims()[0];
	}

	/** Returns the label of the Nth image. */
	public String getSliceLabel(int slice) {
		return "Slice: "+slice;
	}

	/** Returns null. */
	public Object[] getImageArray() {
		return null;
	}

	/** Does nothing. */
	public void setSliceLabel(String label, int n) {
	}

	/** Always return true. */
	public boolean isVirtual() {
		return true;
	}

	/** Does nothing. */
	public void trim() {
	}

	/**
	 * Returns the bit depth (8, 16, 24 or 32), or 0 if the bit depth is not
	 * known.
	 */
	public int getBitDepth() {
		return bitDepth;
	}
}
