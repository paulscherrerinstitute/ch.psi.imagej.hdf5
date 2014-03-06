package ch.psi.imagej.hdf5;

import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import java.util.regex.*;
import java.lang.String;

public class HDF5Config implements PlugIn {

	public static String GROUP_VARS_BY_NAME = "HDF5.groupVarsByName";
	public static String SHOW_UNMATCHED_DATASET_NAMES = "HDF5.showUnmatchedDataSetNames";
	public static String GROUP_VARS_BY_NAME_FORMAT_GROUP = "HDF5.groupVarsByNameFormatGroup";
	public static String GROUP_VARS_BY_NAME_FORMAT = "HDF5.groupVarsByNameFormat";
	public static String DOLLAR_REGEXP_FOR_GROUPING = "HDF5.dollarRegexpForGrouping";
	
	public void run(String arg) {
		// set default values
		setDefaultsIfNoValueExists();
		// read ImageJ Preferences
		boolean groupVarsByName = Boolean.getBoolean(getDefaultValue(GROUP_VARS_BY_NAME));
		groupVarsByName = Prefs.get(GROUP_VARS_BY_NAME, groupVarsByName);

		boolean showUnmatchedDataSetNames = Boolean.getBoolean(getDefaultValue(SHOW_UNMATCHED_DATASET_NAMES));
		showUnmatchedDataSetNames = Prefs.get(SHOW_UNMATCHED_DATASET_NAMES, showUnmatchedDataSetNames);

		String groupVarsByNameFormatGroup = getDefaultValue(GROUP_VARS_BY_NAME_FORMAT_GROUP);
		groupVarsByNameFormatGroup = Prefs.get(GROUP_VARS_BY_NAME_FORMAT_GROUP, groupVarsByNameFormatGroup);

		String groupVarsByNameFormat = getDefaultValue(GROUP_VARS_BY_NAME_FORMAT);
		groupVarsByNameFormat = Prefs.get(GROUP_VARS_BY_NAME_FORMAT, groupVarsByNameFormat);

		String dollarRegexpForGrouping = getDefaultValue(DOLLAR_REGEXP_FOR_GROUPING);
		dollarRegexpForGrouping = Prefs.get(DOLLAR_REGEXP_FOR_GROUPING, dollarRegexpForGrouping);

		GenericDialog configDiag = new GenericDialog("HDF5 Preferences");
		configDiag.addMessage("Reader:");
		configDiag.addCheckbox("Group data set names instead of showing a list " + "of data set names.", groupVarsByName);
		configDiag.addCheckbox("Show unmatched data set names in a separate list", showUnmatchedDataSetNames);
		configDiag.addStringField("HDF5 group containing pattern " + "for data set grouping: ", groupVarsByNameFormatGroup, 15);
		configDiag.addStringField("Pattern for grouping (if no attributes" + " are found): ", groupVarsByNameFormat, 15);
		// configDiag.addStringField("$ regexp (ignored because only numbers" +
		// " work right now): ",
		// dollarRegexpForGrouping,15);
		configDiag.addMessage("Writer:");

		String yesLabel = "Save";
		String noLabel = "Reset";
		configDiag.enableYesNoCancel(yesLabel, noLabel);
		configDiag.showDialog();

		if (configDiag.wasCanceled()) {
			// do nothing
			return;
		}
		if (!configDiag.wasOKed()) {
			// reset button was pressed
			System.out.println("reset button was pressed");
			// reset all and return a new dialog
			configDiag.setVisible(false);
			this.run(arg);
			return;
		}
		// get parameters check if they are correct

		groupVarsByName = configDiag.getNextBoolean();
		System.out.println("groupVarsByName: " + Boolean.toString(groupVarsByName));

		showUnmatchedDataSetNames = configDiag.getNextBoolean();
		System.out.println("showUnmatchedDataSetNames: " + Boolean.toString(showUnmatchedDataSetNames));

		groupVarsByNameFormatGroup = configDiag.getNextString();
		System.out.println("groupVarsByNameFormatGroup: " + groupVarsByNameFormatGroup);

		groupVarsByNameFormat = configDiag.getNextString();
		System.out.println("groupVarsByNameFormat: " + groupVarsByNameFormat);

		// dollarRegexpForGrouping = configDiag.getNextString();
		// System.out.println("dollarRegexpForGrouping: " +
		// dollarRegexpForGrouping);

		try {
			String[] formatTokens = HDF5GroupedVarnames.parseFormatString(groupVarsByNameFormat, dollarRegexpForGrouping);
			for (int i = 0; i < formatTokens.length; i++) {
				System.out.println("tok " + Integer.toString(i) + " : " + formatTokens[i]);
			}
		} catch (PatternSyntaxException e) {
			// produce an error dialog an start over
			String errMsg = e.getMessage();
			System.out.println(errMsg);
			// reset all and return a new dialog
			configDiag.setVisible(false);
			this.run(arg);
			return;
		}
		System.out.println("Saving...");

		// all OK and "Save" was pressed, so save it...
		Prefs.set(GROUP_VARS_BY_NAME, groupVarsByName);
		Prefs.set(SHOW_UNMATCHED_DATASET_NAMES, showUnmatchedDataSetNames);
		Prefs.set(GROUP_VARS_BY_NAME_FORMAT_GROUP, groupVarsByNameFormatGroup);
		Prefs.set(GROUP_VARS_BY_NAME_FORMAT, groupVarsByNameFormat);
		//
		// ignore the $ regexp for now, because only numbers work
		//
		Prefs.set(DOLLAR_REGEXP_FOR_GROUPING, dollarRegexpForGrouping);

	}

	public static void setDefaultsIfNoValueExists() {
		boolean groupVarsByName = Boolean.getBoolean(getDefaultValue(GROUP_VARS_BY_NAME));
		groupVarsByName = Prefs.get(GROUP_VARS_BY_NAME, groupVarsByName);
		Prefs.set(GROUP_VARS_BY_NAME, groupVarsByName);

		boolean showUnmatchedDataSetNames = Boolean.getBoolean(getDefaultValue(SHOW_UNMATCHED_DATASET_NAMES));
		showUnmatchedDataSetNames = Prefs.get(SHOW_UNMATCHED_DATASET_NAMES, showUnmatchedDataSetNames);
		Prefs.set(SHOW_UNMATCHED_DATASET_NAMES, showUnmatchedDataSetNames);

		String groupVarsByNameFormatGroup = getDefaultValue(GROUP_VARS_BY_NAME_FORMAT_GROUP);
		groupVarsByNameFormatGroup = Prefs.get(GROUP_VARS_BY_NAME_FORMAT_GROUP, groupVarsByNameFormatGroup);
		Prefs.set(GROUP_VARS_BY_NAME_FORMAT_GROUP, groupVarsByNameFormatGroup);

		String groupVarsByNameFormat = getDefaultValue(GROUP_VARS_BY_NAME_FORMAT);
		groupVarsByNameFormat = Prefs.get(GROUP_VARS_BY_NAME_FORMAT, groupVarsByNameFormat);
		Prefs.set(GROUP_VARS_BY_NAME_FORMAT, groupVarsByNameFormat);

		String dollarRegexpForGrouping = getDefaultValue(DOLLAR_REGEXP_FOR_GROUPING);
		dollarRegexpForGrouping = Prefs.get(DOLLAR_REGEXP_FOR_GROUPING, dollarRegexpForGrouping);
		Prefs.set(DOLLAR_REGEXP_FOR_GROUPING, dollarRegexpForGrouping);
	}

	public static String getDefaultValue(String key) {
		if (key.equals(GROUP_VARS_BY_NAME)) {
			boolean groupVarsByName = true; // default
			return Boolean.toString(groupVarsByName);
		} else if (key.equals(SHOW_UNMATCHED_DATASET_NAMES)) {
			boolean showUnmatchedDataSetNames = true; // default
			return Boolean.toString(showUnmatchedDataSetNames);
		} else if (key.equals(GROUP_VARS_BY_NAME_FORMAT_GROUP)) {
			String groupVarsByNameFormatGroup = "/hints"; // default
			return groupVarsByNameFormatGroup;
		} else if (key.equals(GROUP_VARS_BY_NAME_FORMAT)) {
			String groupVarsByNameFormat = "/t$T/channel$C"; // default
			return groupVarsByNameFormat;
		} else if (key.equals(DOLLAR_REGEXP_FOR_GROUPING)) {
			String dollarRegexpForGrouping = "[0-9]+"; // default
			return dollarRegexpForGrouping;
		} else {
			System.out.println("No default value for key: " + key);
			return null;
		}
	}

}
