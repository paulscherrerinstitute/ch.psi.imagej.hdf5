package ch.psi.imagej.hdf5;

import static org.junit.Assert.*;

import org.junit.Test;

public class HDF5UtilitiesTest {

	@Test
	public void testGetGroupDescriptor() {
		String descriptor = "/test/one/two/three";
		String gdescriptor = HDF5Utilities.getGroupDescriptor(descriptor);
		System.out.println(gdescriptor);
		assertEquals(gdescriptor, "test/one/two");
	}
	
	@Test
	public void testGetDatasetDescriptor() {
		String descriptor = "/test/one/two/three";
		String gdescriptor = HDF5Utilities.getDatasetName(descriptor);
		System.out.println(gdescriptor);
		assertEquals(gdescriptor, "three");
	}

}
