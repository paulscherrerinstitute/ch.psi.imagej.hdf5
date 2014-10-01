package ch.psi.imagej.hdf5;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.tree.DefaultMutableTreeNode;

import ncsa.hdf.object.Attribute;
import ncsa.hdf.object.Dataset;
import ncsa.hdf.object.FileFormat;
import ncsa.hdf.object.Group;
import ncsa.hdf.object.HObject;
import ncsa.hdf.object.h5.H5File;

public class HDF5Utilities {
	
	private static final Logger logger = Logger.getLogger(HDF5Utilities.class.getName());
	
	/**
	 * Get attributes from object
	 * @param object	Object to retrieve the attributes from
	 * @return			Map of attributes or null if an error occurred while retrieving the attributes or the passed object is null
	 */
	public static  Map<String,Attribute> getAttributes(HObject object) {
		Objects.requireNonNull(object);

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
	
	
	/**
	 * Retrieve relative group descriptor for given descriptor
	 * 
	 * Example: /test/one/two/three returns test/one/two
	 * 
	 * @param descriptor 	Full qualified descriptor
	 * @return	Group descriptor
	 */
	public static String getGroupDescriptor(String descriptor) {
		Pattern p = Pattern.compile("^/?(.*)/");
		Matcher m = p.matcher(descriptor);
		m.find();
		return m.group(1);
	}
	
	/**
	 * Get dataset name
	 * 
	 * Example: /a/b/c/d/ returns d
	 *  
	 * @param descriptor
	 * @return 	Dataset name
	 */
	public static String getDatasetName(String descriptor) {
		Pattern p = Pattern.compile("/+([^/]*)/?$");
		Matcher m = p.matcher(descriptor);
		m.find();
		return m.group(1);
	}
	
	/**
	 * Create group
	 * 
	 * @param file			HDF5 file handle
	 * @param groupName		Group to create
	 * @return
	 */
	public static Group createGroup( FileFormat file, String groupName) {
		return createGroup(file, (Group) ((DefaultMutableTreeNode) file.getRootNode()).getUserObject(),  groupName);
	}
	
	/**
	 * Creates group relative to the given base group
	 * @param file			HDF5 file handle
	 * @param group			Base group
	 * @param groupName		Group to create
	 * @return
	 */
	public static Group createGroup( FileFormat file, Group group, String groupName) {
		Objects.requireNonNull(file);
		Objects.requireNonNull(groupName);

		if (group == null){
			group = (Group) ((DefaultMutableTreeNode) file.getRootNode()).getUserObject();
		}

		Group ngroup = group;
		
		try{
		String[] groups = groupName.split("/");
		for(String g: groups){
			Group cgroup = (Group) file.get(ngroup.getFullName()+"/"+g); // check if already exist
			if (cgroup == null){
				ngroup = file.createGroup(g, ngroup);
			}
			else{
				ngroup = cgroup;
			}
		}
		return ngroup;
		}
		catch(Exception e){
			throw new RuntimeException("Unable to create group ");
		}
	}
	
	
	/**
	 * Get all datasets of a file
	 * @param file
	 * @return
	 */
	public static  List<Dataset> getDatasets(H5File file) {
		Group rootNode = (Group) ((javax.swing.tree.DefaultMutableTreeNode) file.getRootNode()).getUserObject();
		List<Dataset> datasets = getDatasets(rootNode);
		return datasets;
	}
	
	/**
	 * Get all datasets of a group
	 * @param group	Group to search for datasets
	 * @return	List of datasets If group is null datasets will be empty
	 */
	public static  List<Dataset> getDatasets(Group group){
		List<Dataset> datasets = new ArrayList<>();
		return getDatasets(group, datasets);
	}
	
	/**
	 * Recursively get list of all datasets in file
	 * @param group		Group to search for datasets
	 * @param datasets	List of datasets
	 * @return	List of datasets. If group is null datasets will be empty
	 */
	public static  List<Dataset> getDatasets(Group group, List<Dataset> datasets) {
		if (group == null){
			return datasets;
		}

		for (HObject o: group.getMemberList()) {
			if (o instanceof Dataset) {
				((Dataset) o).init();
				datasets.add((Dataset) o);
			} else if (o instanceof Group) {
				datasets = (getDatasets((Group) o, datasets));
			}
		}
		return datasets;
	}
	
	/**
	 * Convert double to float array
	 * @param array 	double array to convert
	 * @return	Converted float array
	 */
	public static float[] convertToFloat(double[] array) {
		float[] narray = new float[array.length];
		for (int i = 0; i < array.length; i++) {
			narray[i] = (float) array[i];
		}
		return narray;
	}
	
	
	/**
	 * Convert int to float array
	 * @param array 	int array to convert
	 * @return	Converted short array
	 */
	public static float[] convertToFloat(int[] array) {
		float[] narray = new float[array.length];
		for (int i = 0; i < array.length; i++) {
			narray[i] = array[i];
		}
		return narray;
	}
	
	
	/**
	 * Convert long (or int64) to float
	 * @param array 	long array to convert
	 * @return	Converted short array
	 */
	public static float[] convertToFloat(long[] array) {
		float[] narray = new float[array.length];
		for (int i = 0; i < array.length; i++) {
			narray[i] = array[i];
		}
		return narray;
	}

}
