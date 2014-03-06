package ch.psi.imagej.hdf5;

import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.logging.Logger;
import java.util.regex.*;
import java.lang.String;

public class HDF5Config implements PlugIn {
	
	private static final Logger logger = Logger.getLogger(HDF5Config.class.getName());

	private static final String GROUP_VARS_BY_NAME = "HDF5.groupVarsByName";
	private static final String SHOW_UNMATCHED_DATASET_NAMES = "HDF5.showUnmatchedDataSetNames";
	private static final String GROUP_VARS_BY_NAME_FORMAT_GROUP = "HDF5.groupVarsByNameFormatGroup";
	private static final String GROUP_VARS_BY_NAME_FORMAT = "HDF5.groupVarsByNameFormat";
	private static final String DOLLAR_REGEXP_FOR_GROUPING = "HDF5.dollarRegexpForGrouping";
	
	// Getting preference or default values
	private boolean groupVarsByName = Prefs.get(GROUP_VARS_BY_NAME, true);
	private boolean showUnmatchedDataSetNames = Prefs.get(SHOW_UNMATCHED_DATASET_NAMES, true);
	private String groupVarsByNameFormatGroup = Prefs.get(GROUP_VARS_BY_NAME_FORMAT_GROUP, "/hints");
	private String groupVarsByNameFormat = Prefs.get(GROUP_VARS_BY_NAME_FORMAT, "/t$T/channel$C");
	private String dollarRegexpForGrouping = Prefs.get(DOLLAR_REGEXP_FOR_GROUPING, "[0-9]+");
	
	
	public void run(String arg) {
		
		GenericDialog configDiag = new GenericDialog("HDF5 Preferences");
		configDiag.addMessage("Reader:");
		configDiag.addCheckbox("Group data set names instead of showing a list " + "of data set names.", groupVarsByName);
		configDiag.addCheckbox("Show unmatched data set names in a separate list", showUnmatchedDataSetNames);
		configDiag.addStringField("HDF5 group containing pattern " + "for data set grouping: ", groupVarsByNameFormatGroup, 15);
		configDiag.addStringField("Pattern for grouping (if no attributes" + " are found): ", groupVarsByNameFormat, 15);
		
		configDiag.showDialog();

		if (configDiag.wasCanceled()) {
			return;
		}

		// Get parameters check if they are correct
		groupVarsByName = configDiag.getNextBoolean();
		showUnmatchedDataSetNames = configDiag.getNextBoolean();
		groupVarsByNameFormatGroup = configDiag.getNextString();
		groupVarsByNameFormat = configDiag.getNextString();

		
		try {
			String[] formatTokens = HDF5GroupedVarnames.parseFormatString(groupVarsByNameFormat, dollarRegexpForGrouping);
			for (int i = 0; i < formatTokens.length; i++) {
				logger.info("tok " + Integer.toString(i) + " : " + formatTokens[i]);
			}
		} catch (PatternSyntaxException e) {
			// produce an error dialog an start over
			String errMsg = e.getMessage();
			logger.info(errMsg);
			// reset all and return a new dialog
			configDiag.setVisible(false);
			this.run(arg);
			return;
		}
		
		logger.info("Saving...");

		// All OK and "Save" was pressed, so save it...
		Prefs.set(GROUP_VARS_BY_NAME, groupVarsByName);
		Prefs.set(SHOW_UNMATCHED_DATASET_NAMES, showUnmatchedDataSetNames);
		Prefs.set(GROUP_VARS_BY_NAME_FORMAT_GROUP, groupVarsByNameFormatGroup);
		Prefs.set(GROUP_VARS_BY_NAME_FORMAT, groupVarsByNameFormat);
		Prefs.set(DOLLAR_REGEXP_FOR_GROUPING, dollarRegexpForGrouping);

	}


	public boolean isGroupVarsByName() {
		return groupVarsByName;
	}
	public boolean isShowUnmatchedDataSetNames() {
		return showUnmatchedDataSetNames;
	}
	public String getGroupVarsByNameFormatGroup() {
		return groupVarsByNameFormatGroup;
	}
	public String getGroupVarsByNameFormat() {
		return groupVarsByNameFormat;
	}
	public String getDollarRegexpForGrouping() {
		return dollarRegexpForGrouping;
	}
}
