package ch.psi.imagej.hdf5;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

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
	
	
	/**
	 * TODO: to be replaced by a simple regex expression
	 * 
	 * Retrieve relative group descriptor for given descriptor
	 * 
	 * Example:
	 * The group descriptor of /test/one/two/three is test/one/two
	 * 
	 * @param descriptor 	Full qualified descriptor
	 * @return	Group descriptor
	 */
	public static String getGroupDescriptor(String descriptor) {
		String groupName = descriptor;

		// Trim leading and trailing slashes
		while (groupName.charAt(0) == '/') {
			groupName = groupName.substring(1);
		}
		while (groupName.charAt(groupName.length() - 1) == '/') {
			groupName = groupName.substring(0, groupName.length() - 2);
		}
		
		int posOfLastSlash = groupName.lastIndexOf('/');
		if (posOfLastSlash == -1)
			return null;
		else
			return groupName.substring(0, posOfLastSlash);
	}
	
	/**
	 * TODO: to be replaced by a simple regex expression
	 * 
	 * Get relative dataset descriptor
	 * 
	 * Example:
	 * 
	 * /a/b/c/d/ returns d
	 *  
	 * @param descriptor
	 * @return relative dataset descriptor
	 */
	public static String getDataSetDescriptor(String descriptor) {
		String dataSetName = descriptor;
		
		// Trim leading and trailing slashes
		while (dataSetName.charAt(0) == '/') {
			dataSetName = dataSetName.substring(1);
		}
		while (dataSetName.charAt(dataSetName.length() - 1) == '/') {
			dataSetName = dataSetName.substring(0, dataSetName.length() - 2);
		}
		int posOfLastSlash = dataSetName.lastIndexOf('/');
		if (posOfLastSlash == -1)
			return dataSetName;
		else
			return dataSetName.substring(posOfLastSlash + 1);
	}
	
	/**
	 * Creates group recursively relative to the given base group
	 * 
	 * @param groupRelativName relative group to be created
	 * @param group	Base group - if null create group relative to /
	 * @param file	File handle
	 * @return
	 */
	public static Group createGroup(String groupRelativName, Group group, FileFormat file) {
		if (groupRelativName == null || file == null)
			return null;

		if (group == null){
			group = (Group) ((DefaultMutableTreeNode) file.getRootNode()).getUserObject();
		}

		// Trim leading and trailing slashes
		while (groupRelativName.charAt(0) == '/') {
			groupRelativName = groupRelativName.substring(1);
		}
		while (groupRelativName.charAt(groupRelativName.length() - 1) == '/') {
			groupRelativName = groupRelativName.substring(0, groupRelativName.length() - 2);
		}

		int posOfSlash = groupRelativName.indexOf('/');

		if (posOfSlash == -1) {
			try {
				Group newGroup;
				String newGroupName;
				if (group.isRoot()){
					newGroupName = "/" + groupRelativName;
				}
				else{
					newGroupName = group.getFullName() + "/" + groupRelativName;
				}
				newGroup = (Group) file.get(newGroupName);
				if (newGroup == null){
					newGroup = file.createGroup(newGroupName, group);
				}
				return newGroup;
			} catch (Exception e) {
				return null;
			}
		} else {
			String subgroupRelativName = groupRelativName.substring(posOfSlash);
			String currentGroup = groupRelativName.substring(0, posOfSlash);
			logger.info("Create: " + currentGroup);
			logger.info("Call back for: " + subgroupRelativName);
			try {
				Group newGroup;
				String newGroupName;
				if (group.isRoot()){
					newGroupName = "/" + currentGroup;
				}
				else {
					newGroupName = group.getFullName() + "/" + currentGroup;
				}

				logger.info("try opening: " + newGroupName);
				newGroup = (Group) file.get(newGroupName);

				if (newGroup == null) {
					newGroup = file.createGroup(newGroupName, group);
				}

				return createGroup(subgroupRelativName, newGroup, file);
			} catch (Exception e) {
				return null;
			}

		}
		// never come here
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
}
