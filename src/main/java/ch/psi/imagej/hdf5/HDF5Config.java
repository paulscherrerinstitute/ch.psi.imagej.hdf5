package ch.psi.imagej.hdf5;

import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import java.util.regex.*;
import java.lang.String;

public class HDF5Config implements PlugIn {

	public void run(String arg) {
		// set default values
		setDefaultsIfNoValueExists();
		// read ImageJ Preferences
		boolean groupVarsByName = Boolean.getBoolean(getDefaultValue("HDF5.groupVarsByName"));
		groupVarsByName = Prefs.get("HDF5.groupVarsByName", groupVarsByName);

		boolean showUnmatchedDataSetNames = Boolean.getBoolean(getDefaultValue("HDF5.showUnmatchedDataSetNames"));
		showUnmatchedDataSetNames = Prefs.get("HDF5.showUnmatchedDataSetNames", showUnmatchedDataSetNames);

		String groupVarsByNameFormatGroup = getDefaultValue("HDF5.groupVarsByNameFormatGroup");
		groupVarsByNameFormatGroup = Prefs.get("HDF5.groupVarsByNameFormatGroup", groupVarsByNameFormatGroup);

		String groupVarsByNameFormat = getDefaultValue("HDF5.groupVarsByNameFormat");
		groupVarsByNameFormat = Prefs.get("HDF5.groupVarsByNameFormat", groupVarsByNameFormat);

		String dollarRegexpForGrouping = getDefaultValue("HDF5.dollarRegexpForGrouping");
		dollarRegexpForGrouping = Prefs.get("HDF5.dollarRegexpForGrouping", dollarRegexpForGrouping);

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
		Prefs.set("HDF5.groupVarsByName", groupVarsByName);
		Prefs.set("HDF5.showUnmatchedDataSetNames", showUnmatchedDataSetNames);
		Prefs.set("HDF5.groupVarsByNameFormatGroup", groupVarsByNameFormatGroup);
		Prefs.set("HDF5.groupVarsByNameFormat", groupVarsByNameFormat);
		//
		// ignore the $ regexp for now, because only numbers work
		//
		Prefs.set("HDF5.dollarRegexpForGrouping", dollarRegexpForGrouping);

	}

	public static void setDefaultsIfNoValueExists() {
		boolean groupVarsByName = Boolean.getBoolean(getDefaultValue("HDF5.groupVarsByName"));
		groupVarsByName = Prefs.get("HDF5.groupVarsByName", groupVarsByName);
		Prefs.set("HDF5.groupVarsByName", groupVarsByName);

		boolean showUnmatchedDataSetNames = Boolean.getBoolean(getDefaultValue("HDF5.showUnmatchedDataSetNames"));
		showUnmatchedDataSetNames = Prefs.get("HDF5.showUnmatchedDataSetNames", showUnmatchedDataSetNames);
		Prefs.set("HDF5.showUnmatchedDataSetNames", showUnmatchedDataSetNames);

		String groupVarsByNameFormatGroup = getDefaultValue("HDF5.groupVarsByNameFormatGroup");
		groupVarsByNameFormatGroup = Prefs.get("HDF5.groupVarsByNameFormatGroup", groupVarsByNameFormatGroup);
		Prefs.set("HDF5.groupVarsByNameFormatGroup", groupVarsByNameFormatGroup);

		String groupVarsByNameFormat = getDefaultValue("HDF5.groupVarsByNameFormat");
		groupVarsByNameFormat = Prefs.get("HDF5.groupVarsByNameFormat", groupVarsByNameFormat);
		Prefs.set("HDF5.groupVarsByNameFormat", groupVarsByNameFormat);

		String dollarRegexpForGrouping = getDefaultValue("HDF5.dollarRegexpForGrouping");
		dollarRegexpForGrouping = Prefs.get("HDF5.dollarRegexpForGrouping", dollarRegexpForGrouping);
		Prefs.set("HDF5.dollarRegexpForGrouping", dollarRegexpForGrouping);
	}

	public static String getDefaultValue(String key) {
		if (key.equals("HDF5.groupVarsByName")) {
			boolean groupVarsByName = true; // default
			return Boolean.toString(groupVarsByName);
		} else if (key.equals("HDF5.showUnmatchedDataSetNames")) {
			boolean showUnmatchedDataSetNames = true; // default
			return Boolean.toString(showUnmatchedDataSetNames);
		} else if (key.equals("HDF5.groupVarsByNameFormatGroup")) {
			String groupVarsByNameFormatGroup = "/hints"; // default
			return groupVarsByNameFormatGroup;
		} else if (key.equals("HDF5.groupVarsByNameFormat")) {
			String groupVarsByNameFormat = "/t$T/channel$C"; // default
			return groupVarsByNameFormat;
		} else if (key.equals("HDF5.dollarRegexpForGrouping")) {
			String dollarRegexpForGrouping = "[0-9]+"; // default
			return dollarRegexpForGrouping;
		} else {
			System.out.println("No default value for key: " + key);
			return null;
		}
	}

}
