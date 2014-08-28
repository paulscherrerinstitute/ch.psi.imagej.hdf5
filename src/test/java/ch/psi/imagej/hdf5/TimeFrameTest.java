package ch.psi.imagej.hdf5;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TimeFrameTest {

	private TimeFrame timeframe;
	
	@Before
	public void setUp() throws Exception {
		timeframe = new TimeFrame(1);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		System.out.println(timeframe.toString());
	}

}
