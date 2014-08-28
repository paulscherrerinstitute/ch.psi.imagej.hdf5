package ch.psi.imagej.hdf5;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import ncsa.hdf.object.Attribute;
import ncsa.hdf.object.HObject;

public class HDF5Utilities {
	
	private static final Logger logger = Logger.getLogger(HDF5Utilities.class.getName());
	
	/**
	 * Get attributes from object
	 * @param object	Object to retrieve the attributes from
	 * @return			Map of attributes or null if an error occurred while retrieving the attributes or the passed object is null
	 */
	public static  Map<String,Attribute> getAttributes(HObject object) {
		if (object == null){
			return null;
		}

		Map<String, Attribute> attributes = new HashMap<>();
		try{
			for(Object m: object.getMetadata()){
				if(m instanceof Attribute){
					attributes.put(((Attribute) m).getName(), (Attribute) m);
				}
			}
		}
		catch(Exception e){
			logger.warning("Unable to retrieve metadata from object");
			return null;
		}
		
		return attributes;
	}
}
