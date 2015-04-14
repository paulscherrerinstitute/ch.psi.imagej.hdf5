package ch.psi.imagej.hdf5;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.logging.Logger;

import ij.ImagePlus;
import ij.ImageStack;

public class ImagePlusHDF5 extends ImagePlus {
	
	
	private static final Logger logger = Logger.getLogger(ImagePlusHDF5.class.getName());
	
	public ImagePlusHDF5(String title, ImageStack stack) {
		super(title, stack);
	}
	
	@Override
	public void show() {
		super.show();
		getWindow().addWindowListener(new WindowListener() {
			
			@Override
			public void windowOpened(WindowEvent e) {
				logger.info("");
			}
			
			@Override
			public void windowIconified(WindowEvent e) {
				logger.info("");
			}
			
			@Override
			public void windowDeiconified(WindowEvent e) {
				logger.info("");
			}
			
			@Override
			public void windowDeactivated(WindowEvent e) {
				logger.info("");
			}
			
			@Override
			public void windowClosing(WindowEvent e) {
				logger.info("Closing");
			}
			
			@Override
			public void windowClosed(WindowEvent e) {
				logger.info("Closed");
				ImageStack stack = getStack();
				if(stack instanceof VirtualStackHDF5){
					((VirtualStackHDF5) stack).close();
				}
			}
			
			@Override
			public void windowActivated(WindowEvent e) {
				logger.info("");
			}
		});
	}
}
