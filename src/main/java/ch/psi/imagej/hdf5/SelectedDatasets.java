package ch.psi.imagej.hdf5;

import java.util.ArrayList;
import java.util.List;

import ncsa.hdf.object.Dataset;

public class SelectedDatasets {

	private List<Dataset> datasets = new ArrayList<Dataset>();
	private boolean group = false;
	
	public List<Dataset> getDatasets() {
		return datasets;
	}
	public void setDatasets(List<Dataset> datasets) {
		this.datasets = datasets;
	}
	public boolean isGroup() {
		return group;
	}
	public void setGroup(boolean group) {
		this.group = group;
	}
	
}
