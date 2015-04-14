package ch.psi.imagej.hdf5;

import java.util.ArrayList;
import java.util.List;

import ncsa.hdf.object.Dataset;

public class DatasetSelection {

	private List<Dataset> datasets = new ArrayList<Dataset>();
	private boolean group = false;
	private Integer slice;
	// Intervall to read images
	private Integer modulo;
	
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
	public void setSlice(Integer slice) {
		this.slice = slice;
	}
	public Integer getSlice() {
		return slice;
	}
	public void setModulo(Integer modulo) {
		this.modulo = modulo;
	}
	public Integer getModulo() {
		return modulo;
	}
}
