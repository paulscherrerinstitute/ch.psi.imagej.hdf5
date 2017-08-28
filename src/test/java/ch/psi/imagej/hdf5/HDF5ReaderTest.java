package ch.psi.imagej.hdf5;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 *
 */
public class HDF5ReaderTest {
    @Test
    public void parseArguments() throws Exception {

        Map map = HDF5Reader.parseArguments("para1=value1 para2=value2  PARA=VAL");
        assertTrue(map.get("para1").equals("value1"));
        assertTrue(map.get("para2").equals("value2"));
        assertTrue(map.get("PARA").equals("VAL"));
    }

}