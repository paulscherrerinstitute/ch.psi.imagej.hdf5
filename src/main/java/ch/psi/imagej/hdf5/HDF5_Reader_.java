package ch.psi.imagej.hdf5;
/* =========================================================================
 * 
 *  Copyright 2011 Matthias Schlachter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *=========================================================================*/


import ij.IJ;
import ij.Prefs;
import ij.ImagePlus;
import ij.CompositeImage;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.awt.*;

import ncsa.hdf.object.*; // the common object package
import ncsa.hdf.object.h5.*; // the HDF5 implementation
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdflib.HDFException;

public class HDF5_Reader_ implements PlugIn
{
  public void run(String arg)
  {
    // make sure default values for config are written
    // HDF5_Config.setDefaultsIfNoValueExists();
    
    // run plugin
    String directory = "";
    String name = "";
    boolean tryAgain;
    String openMSG = "Open HDF5...";
    do
    {
      tryAgain = false;
      OpenDialog od;
      if (directory.equals(""))
        od = new OpenDialog(openMSG, arg);
      else
        od = new OpenDialog(openMSG, directory, arg);

      directory = od.getDirectory();
      name = od.getFileName();
      if (name == null)
        return;
      if (name == "")
        return;

      File testFile = new File(directory + name);
      if (!testFile.exists() || !testFile.canRead())
        return;

      if (testFile.isDirectory())
      {
        directory = directory + name;
        tryAgain = true;
      }
    } while (tryAgain);

    IJ.showStatus("Loading HDF5 File: " + directory + name);
    
    H5File inFile = null;


    // define grouping class
    HDF5_GroupedVarnames groupedVarnames = new HDF5_GroupedVarnames();
    boolean loadGroupedVarNames = true;
    
    try
    {
      inFile = new H5File(directory + name, H5File.READ);
      inFile.open();
      
      /*-------------------------------------------------------------------
       *  read HDF5_Config prefs
       *-------------------------------------------------------------------*/
      boolean groupVarsByName = 
          Boolean.getBoolean(HDF5_Config.
                             getDefaultValue("HDF5.groupVarsByName"));
      groupVarsByName = Prefs.get("HDF5.groupVarsByName", groupVarsByName);

      boolean showUnmatchedDataSetNames = 
          Boolean.getBoolean(HDF5_Config.
                             getDefaultValue("HDF5.showUnmatchedDataSetNames"));
      showUnmatchedDataSetNames = Prefs.get("HDF5.showUnmatchedDataSetNames",
                                            showUnmatchedDataSetNames);
    
      String groupVarsByNameFormatGroup = 
          HDF5_Config.getDefaultValue("HDF5.groupVarsByNameFormatGroup");
      groupVarsByNameFormatGroup 
          = Prefs.get("HDF5.groupVarsByNameFormatGroup",
                      groupVarsByNameFormatGroup);
    
      // TODO: try to read attribute containing format String
      String groupVarsByNameFormat = null;
      try
      {
        HObject gr = inFile.get(groupVarsByNameFormatGroup);
        String attrName = ""; // this will throw an error
        
        if(gr != null)
        {
          // get the attr list and make a selection dialog if necessary
          List<Attribute> hintsAttrList = getAttrList(gr);
          if(hintsAttrList.size() == 1)
              attrName = hintsAttrList.get(0).getName();
          else if(hintsAttrList.size() > 1)
          {
            String[] hintsAttrNames = new String[hintsAttrList.size()];
            for(int a=0;a<hintsAttrList.size();a++)
                hintsAttrNames[a] = hintsAttrList.get(a).getName();
            GenericDialog attrSelecD 
                = new GenericDialog("Format string selector");
            attrSelecD.addChoice("Select format string",
                                 hintsAttrNames,
                                 hintsAttrNames[0]);
            attrSelecD.showDialog();
            if(attrSelecD.wasCanceled())
                return;
            else
                attrName = attrSelecD.getNextChoice();
          }
          
          System.out.println("Reading attribute");
          Attribute attr = getAttribute(gr,
                                        attrName);
          System.out.println("Reading attribute is ok");
          if(attr != null)
              System.out.println("attr is not null");
          System.out.println("attr.getName(): " + 
                             attr.getName());
          Datatype dType = attr.getType();
          System.out.println(dType.getDatatypeDescription());
          
          Object tmp =  attr.getValue();
          if(tmp != null)
              System.out.println("get value is ok");
          if(tmp instanceof String)
          {
            // we have a string
            groupVarsByNameFormat = (String) tmp;
          }
          else if(tmp instanceof String[])
          {
            // we have a cstring
            String[] sTmp = (String[]) tmp;
            groupVarsByNameFormat = "";
            for(int i=0;i<sTmp.length;i++)
                groupVarsByNameFormat 
                    = groupVarsByNameFormat + sTmp[i];
          }
          System.out.println("File has format string for grouping: " +
                             groupVarsByNameFormat);
        }
        else
        {
          System.out.println("File has no format string for grouping" + 
                             ", using default");
          groupVarsByNameFormat 
              = HDF5_Config.getDefaultValue("HDF5.groupVarsByNameFormat");
          groupVarsByNameFormat = Prefs.get("HDF5.groupVarsByNameFormat", 
                                            groupVarsByNameFormat);
        }
      }
      catch(Exception e)
      {
        System.out.println("Error occured read format string " + 
                           "for grouping, using default");
        groupVarsByNameFormat 
            = HDF5_Config.getDefaultValue("HDF5.groupVarsByNameFormat");
        groupVarsByNameFormat = Prefs.get("HDF5.groupVarsByNameFormat", 
                                          groupVarsByNameFormat);
      }

      String dollarRegexpForGrouping = 
          HDF5_Config.getDefaultValue("HDF5.dollarRegexpForGrouping");
      dollarRegexpForGrouping = Prefs.get("HDF5.dollarRegexpForGrouping",
                                          dollarRegexpForGrouping);
      
      /*-------------------------------------------------------------------
       *  init the frame and channel ranges
       *-------------------------------------------------------------------*/
      int minFrameIndex = -1;
      int maxFrameIndex = -1;
      int skipFrameIndex = 1;

      int minChannelIndex = -1;
      int maxChannelIndex = -1;
      int skipChannelIndex = 1;

      /*-------------------------------------------------------------------
       *  parse the file
       *-------------------------------------------------------------------*/

      Group rootNode = (Group)
          ((javax.swing.tree.DefaultMutableTreeNode)
           inFile.getRootNode()).getUserObject();
      List<Dataset> varList = getDataSetList(rootNode,
                                             new ArrayList<Dataset>());

      GenericDialog gd = new GenericDialog("Variable Name Selection");
      gd.addMessage("Please select variables to be loaded.\n");

      if (varList.size() < 1)
      {
        IJ.error("The file did not contain variables. (broken?)");
        inFile.close();
        return;
      }
      // else if (varList.size() < 2)
      // {
      //   gd.addCheckbox("single variable", true);
      // }
      else if(groupVarsByName)
      {
        // parse file structure
        String[] varNames = new String[varList.size()];
        for (int i = 0; i < varList.size(); i++)
        {
          varNames[i] = varList.get(i).getFullName();
        }
        groupedVarnames.parseVarNames(varNames,
                                      groupVarsByNameFormat,
                                      dollarRegexpForGrouping);
        System.out.println(groupedVarnames.toString());

        // make the data set selection dialog
        minFrameIndex = groupedVarnames.getMinFrameIndex();
        maxFrameIndex = groupedVarnames.getMaxFrameIndex();
        minChannelIndex = groupedVarnames.getMinChannelIndex();
        maxChannelIndex = groupedVarnames.getMaxChannelIndex();
        

        gd = new GenericDialog("Variable Name Selection");
        // check if we have matched var names
        if(groupedVarnames.getNFrames()>0)
        {
          gd.addCheckbox("Load grouped data sets",
                         loadGroupedVarNames);

          gd.addMessage("Select frames and channels you want to read");
          gd.addMessage("Frame selection (start/step/end): ");

          gd.addStringField("Frame selection (start:[step:]end): ",
                            Integer.toString(minFrameIndex) + ":" +
                            Integer.toString(skipFrameIndex) + ":" +
                            Integer.toString(maxFrameIndex));
          
          gd.addStringField("Channel selection (start:[step:]end): ",
                            Integer.toString(minChannelIndex) + ":" +
                            Integer.toString(skipChannelIndex) + ":" +
                            Integer.toString(maxChannelIndex));
        }

        // get unmatched names
        List<String> unmatchedVarNames 
            = groupedVarnames.getUnmatchedVarNames();
        if(showUnmatchedDataSetNames)
        {
          // add all unmatched data set names to the dialog
          String[] varSelections = new String[unmatchedVarNames.size()];
          boolean[] defaultValues = new boolean[unmatchedVarNames.size()];
          for (int i = 0; i < unmatchedVarNames.size(); i++)
          {
            Dataset var = (Dataset) inFile.get(unmatchedVarNames.get(i));
            int rank = var.getRank();
            String title = rank + "D: " + var.getFullName() + "              "
                + var.getDatatype().getDatatypeDescription() + "( ";
            long[] extent = var.getDims();
            for (int d = 0; d < rank; ++d)
            {
              if (d != 0)
                  title += "x";
              title += extent[d];
            }
            title += ")";
            varSelections[i] = title;
            defaultValues[i] = false;
          }
          System.out.println("addcheckboxgroup with " 
                             + unmatchedVarNames.size() + " rows");
          gd.addCheckboxGroup(unmatchedVarNames.size(), 1,
                              varSelections, defaultValues);
          addScrollBars(gd);
        }
        gd.showDialog();
        if (gd.wasCanceled())
        {
          return;
        }

        // load grouped var names ?
        if(groupedVarnames.getNFrames()>0)
            loadGroupedVarNames = gd.getNextBoolean();

        // read range selections if we have matched varnames
        String frameRange = null;
        String channelRange = null;
        if(groupedVarnames.getNFrames()>0)
        {
          frameRange = gd.getNextString();
          channelRange = gd.getNextString();
        }
        // parse the range selection
        String[] frameRangeToks = null;
        String[] channelRangeToks = null;
        boolean wrongFrameRange = true;
        boolean wrongChannelRange = true;
        // check if the parsed values are in range
        if(groupedVarnames.getNFrames()>0 && loadGroupedVarNames)
            while(wrongFrameRange || wrongChannelRange)
            {
              // check frame range
              frameRangeToks = frameRange.split(":");
              if(frameRangeToks.length == 1)
              {
                // single frame
                try
                {
                  System.out.println("single frame");
                  minFrameIndex = Integer.parseInt(frameRangeToks[0]);
                  maxFrameIndex = minFrameIndex;
                  wrongFrameRange = false;
                }
                catch(Exception e)
                {
                  wrongFrameRange = true;
                }
              }
              else if(frameRangeToks.length == 2)
              {
                // frame range with skipFrameIndex=1
                try
                {
                  System.out.println("frame range with skipFrameIndex=1");
                  minFrameIndex = Integer.parseInt(frameRangeToks[0]);
                  maxFrameIndex = Integer.parseInt(frameRangeToks[1]);
                  wrongFrameRange = false;
                }
                catch(Exception e)
                {
                  wrongFrameRange = true;
                }
              }
              else if(frameRangeToks.length == 3)
              {
                // frame range with skipFrameIndex
                try
                {
                  System.out.println("frame range with skipFrameIndex");
                  minFrameIndex = Integer.parseInt(frameRangeToks[0]);
                  skipFrameIndex = Integer.parseInt(frameRangeToks[1]);
                  maxFrameIndex = Integer.parseInt(frameRangeToks[2]);
                  wrongFrameRange = false;
                }
                catch(Exception e)
                {
                  wrongFrameRange = true;
                }
              }
              else
              {
                // wrong format
                System.out.println("wrong format");
                wrongFrameRange = true;
              }
        
              // check channel range
              channelRangeToks = channelRange.split(":");
              if(channelRangeToks.length == 1)
              {
                // single channel
                try
                {
                  minChannelIndex = Integer.parseInt(channelRangeToks[0]);
                  maxChannelIndex = minChannelIndex;
                  wrongChannelRange = false;
                }
                catch(Exception e)
                {
                  wrongChannelRange = true;
                }
              }
              else if(channelRangeToks.length == 2)
              {
                // channel range with skipChannelIndex=1
                try
                {
                  minChannelIndex = Integer.parseInt(channelRangeToks[0]);
                  maxChannelIndex = Integer.parseInt(channelRangeToks[1]);
                  wrongChannelRange = false;
                }
                catch(Exception e)
                {
                  wrongChannelRange = true;
                }
              }
              else if(channelRangeToks.length == 3)
              {
                // channel range with skipChannelIndex
                try
                {
                  minChannelIndex = Integer.parseInt(channelRangeToks[0]);
                  skipChannelIndex = Integer.parseInt(channelRangeToks[1]);
                  maxChannelIndex = Integer.parseInt(channelRangeToks[2]);
                  wrongChannelRange = false;
                }
                catch(Exception e)
                {
                  wrongChannelRange = true;
                }
              }
              else
              {
                // wrong format
                wrongChannelRange = true;
              }
              if(wrongFrameRange || wrongChannelRange)
              {
                // show dialog again
                System.out.println("show dialog again");
                // TODO reset dialog when possible
                gd = new GenericDialog("Range Selection");
                gd.addMessage("Select frames and channels you want to read");
                gd.addMessage("Frame selection (start/step/end): ");
          
                gd.addStringField("Frame selection (start:[step:]end): ",
                                  Integer.toString(minFrameIndex) + ":" +
                                  Integer.toString(skipFrameIndex) + ":" +
                                  Integer.toString(maxFrameIndex));
          
                gd.addStringField("Channel selection (start:[step:]end): ",
                                  Integer.toString(minChannelIndex) + ":" +
                                  Integer.toString(skipChannelIndex) + ":" +
                                  Integer.toString(maxChannelIndex));
                gd.showDialog();
                System.out.println("read ranges again");
                frameRange = gd.getNextString();
                channelRange = gd.getNextString();
          
              }
              if (gd.wasCanceled())
              {
                return;
              }
              // the parameters for the range have correct format
            }

        if(showUnmatchedDataSetNames)
        {
          varList = new ArrayList<Dataset>();
          // fill varList with unmatched var names
          for (int i = 0; i < unmatchedVarNames.size(); i++)
          {
            String dsName = unmatchedVarNames.get(i);
            try 
            {
              HObject ds = inFile.get(dsName);
              if(ds != null && ds instanceof Dataset)
              {
                varList.add((Dataset) ds);
              }
            }
            catch(Exception e)
            {
              System.out.println("The file does not contain a variable " +
                                 "with name " + "`" + dsName + "`!");
            }
          }
        }
        else
        {
          // set varList=empty if we dont want unmatched var names
          varList = new ArrayList<Dataset>();
        }

      }
      else if(varList.size() > 1000)
      {
        /*-----------------------------------------------------------------
         *  FIXME: quick an dirty hack for files with more than 1000
         *  datasets
         *-----------------------------------------------------------------*/
        gd = new GenericDialog("Variable Name Selection");
        gd.addMessage("Too many variables in your file! "
                      + "(More than 1000)\n\n"
                      + "Please enter the full name of your desired dataset.");
        gd.addStringField("dataset name", "");
        gd.showDialog();
        
        if (gd.wasCanceled())
        {
          return;
        }
        
        String dsName = gd.getNextString();
        varList = new ArrayList<Dataset>();
        try 
        {
          HObject ds = inFile.get(dsName);
          if(ds != null && ds instanceof Dataset)
          {
            varList.add((Dataset) ds);
            gd.addCheckbox("single variable", true);
          }
          else
          {
            IJ.error("The file does not contain a variable with name "
                     + "`" + dsName + "`!");
            inFile.close();
            return;
          }
        }
        catch(Exception e)
        {
          IJ.error("The file does not contain a variable with name "
                   + "`" + dsName + "`!");
          inFile.close();
          return;
        }
      }
      else
      {
        String[] varSelections = new String[varList.size()];
        boolean[] defaultValues = new boolean[varList.size()];
        for (int i = 0; i < varList.size(); i++)
        {
          Dataset var = varList.get(i);
          int rank = var.getRank();
          String title = rank + "D: " + var.getFullName() + "              "
                         + var.getDatatype().getDatatypeDescription() + "( ";
          long[] extent = var.getDims();
          for (int d = 0; d < rank; ++d)
          {
            if (d != 0)
              title += "x";
            title += extent[d];
          }
          title += ")";
          varSelections[i] = title;
          defaultValues[i] = false;
        }
        System.out.println("addcheckboxgroup with " 
                           + varList.size() + " rows");
        gd.addCheckboxGroup(varList.size(), 1,
                            varSelections, defaultValues);
        addScrollBars(gd);
        gd.showDialog();

        if (gd.wasCanceled())
        {
          return;
        }
      }
      /*-------------------------------------------------------------------
       *  now reading data sets
       *-------------------------------------------------------------------*/
      if(groupVarsByName && 
         groupedVarnames.getNFrames()>0 &&
         loadGroupedVarNames)
      {
        groupedVarnames.setFrameAndChannelRange(minFrameIndex,
                                                skipFrameIndex,
                                                maxFrameIndex,
                                                minChannelIndex,
                                                skipChannelIndex,
                                                maxChannelIndex);
        // TODO implement hyperstack reading
        int nFrames = groupedVarnames.getNFrames();
        int nChannels = groupedVarnames.getNChannels();

        // get extents of first data set
        long[] extent = null;
        int rank = -1;
        double[] elem_sizes = new double[3];
        String dsName = "";
        String[] formatTokens = groupedVarnames.getFormatTokens();
        Dataset var = null;
        boolean isSigned16Bit = false;
        int unsignedConvSelec = 0; // cut off values
        try 
        {
          TimeFrame f = groupedVarnames.getFrame(0);
          if(f == null)
              System.out.println("frame is null");
          if(formatTokens.length == 2)
              dsName = formatTokens[0]
                  + Integer.toString(f.getFrameIndex())
                  + formatTokens[1]
                  + Integer.toString(f.getChannelIndices()[0]);
          else if(formatTokens.length == 3)
              dsName = formatTokens[0]
                  + Integer.toString(f.getFrameIndex())
                  + formatTokens[1]
                  + Integer.toString(f.getChannelIndices()[0])
                  + formatTokens[2];
          System.out.println("VarName: " + dsName);
          HObject ds = inFile.get(dsName);
          if(ds != null && ds instanceof Dataset)
          {
            var = (Dataset) ds;
            Datatype dType = var.getDatatype();
            isSigned16Bit = !dType.isUnsigned() && 
                (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) &&
                (dType.getDatatypeSize() == 2);
            if(isSigned16Bit)
            {
              GenericDialog convDiag 
                  = new GenericDialog("Unsigned to signed conversion");
              convDiag.addMessage("Detected unsigned datatype, which "+
                                  "is not supported.");
              String[] convOptions = new String[2];
              convOptions[0] = "cut off values";
              convOptions[1] = "convert to float";
              convDiag.addChoice("Please select an conversion option:",
                                 convOptions,
                                 convOptions[0]);
              convDiag.showDialog();
              if(convDiag.wasCanceled())
                  return;
              unsignedConvSelec = convDiag.getNextChoiceIndex();
            }
            // TODO check for unsupported datatypes int,long
            rank = var.getRank();
            extent = var.getDims();
            Attribute elemsize_att = getAttribute(var, "element_size_um");
          
            if (elemsize_att == null)
            {
              elem_sizes[0] = 1.0;
              elem_sizes[1] = 1.0;
              elem_sizes[2] = 1.0;
            }
            else
            {
              System.out.println("Reading element_size_um");
              float[] tmp = null;
              try
              {
                tmp = ((float[]) elemsize_att.getValue());
                elem_sizes[0] = tmp[0];
                elem_sizes[1] = tmp[1];
                elem_sizes[2] = tmp[2];
              }
              catch(java.lang.ClassCastException e)
              {
                String title = "Error Reading Element Size";
                String msg = "The element_size_um attribute "
                    + "has wrong format!\n";
                msg += "Setting to default size of (1,1,1)...";
                ij.gui.MessageDialog errMsg = new ij.gui.MessageDialog(null,
                                                                       title,
                                                                       msg);
                elem_sizes[0] = 1;
                elem_sizes[1] = 1;
                elem_sizes[2] = 1;              
              }
            }
          }
          else
          {
            IJ.error("The file does not contain a variable with name "
                     + "`" + dsName + "`!");
            inFile.close();
            return;
          }
        }
        catch(Exception e)
        {
          IJ.error("The file does not contain a variable with name "
                   + "`" + dsName + "`!");
          inFile.close();
          return;
        }
        // String title = rank + "D: " + var.getFullName() + "              "
        //     + var.getDatatype().getDatatypeDescription() + "( ";
        
        // read 3D or 2D dataset
        if(rank == 3 || rank == 2)
        {
          int nRows = -1; // height
          int nCols = -1; // width
          int nSlices = 1; // if rank == 2
          if(rank == 3)
          {
            nSlices = (int) extent[0];
            nRows = (int) extent[1];
            nCols = (int) extent[2];
          }
          else
          {
            nRows = (int) extent[0];
            nCols = (int) extent[1];
          }
            

          // create a new image stack and fill in the data
          ImageStack stack = new ImageStack(nCols,
                                            nRows,
                                            nFrames*nSlices*nChannels);
          System.out.println("stackSize: " 
                             + Integer.toString(stack.getSize()));

          ImagePlus imp = new ImagePlus();
          // to get getFrameIndex() working
          imp.setDimensions(nChannels,nSlices,nFrames);

          long stackSize = nCols*nRows;
          // global min max values of all data sets
          double[] minValChannel = new double[nChannels];
          double[] maxValChannel = new double[nChannels];
          
          // TODO implement frame and channel ranges
          // now read frame by frame
          for(int fIdx=0; fIdx<nFrames; fIdx++)
          {
            // get current frame
            TimeFrame f = groupedVarnames.getFrame(fIdx);
            if(f == null)
                System.out.println("frame is null");
            // get channel indices
            int[] channels = f.getChannelIndices();
            
            // TODO: check if frame has same parameters as first, 
            // skip otherwise

            // now read channel by channel of frame
            for(int cIdx=0; cIdx<nChannels; cIdx++)
            {
              if(formatTokens.length == 2)
                  dsName = formatTokens[0]
                      + Integer.toString(f.getFrameIndex())
                      + formatTokens[1]
                      + Integer.toString(f.getChannelIndices()[cIdx]);
              else if(formatTokens.length == 3)
                  dsName = formatTokens[0]
                      + Integer.toString(f.getFrameIndex())
                      + formatTokens[1]
                      + Integer.toString(f.getChannelIndices()[cIdx])
                      + formatTokens[2];
              
              System.out.println("VarName: " + dsName);

              HObject ds = inFile.get(dsName);
              if(ds != null && ds instanceof Dataset)
              {
                var = (Dataset) ds;
                rank = var.getRank();
                extent = var.getDims();
              }

              // TODO: check if dataset has same parameters as first, 
              // skip otherwise

              long[] selected = var.getSelectedDims(); // the
              // selected
              // size of
              // the
              // dataet
              selected[0] = extent[0];
              selected[1] = extent[1];
              if(selected.length>2)
                  selected[2] = extent[2];

              Object wholeDataset = var.read();
              if(isSigned16Bit)
                  wholeDataset = convertToUnsigned(wholeDataset,
                                                   unsignedConvSelec);
              
              long wholeDatasetSize = 1;
              for(int d=0;d<extent.length;d++)
                  wholeDatasetSize*=extent[d];
              
              if(fIdx == 0)
              {
                // init min/max
                double[] minMaxVal = getMinMax(wholeDataset, 
                                               wholeDatasetSize);
                minValChannel[cIdx] = minMaxVal[0];
                maxValChannel[cIdx] = minMaxVal[1];
              }
              else
              {
                // update minMaxVal
                double[] minMaxVal = getMinMax(wholeDataset, 
                                               wholeDatasetSize);
                minValChannel[cIdx] = Math.min(minMaxVal[0],
                                               minValChannel[cIdx]);
                maxValChannel[cIdx] = Math.max(minMaxVal[1],
                                               maxValChannel[cIdx]);
              }  
              
              
              for (int lev = 0; lev < nSlices; ++lev)
              {
                // if ((lev % progressDivisor) == 0)
                //     IJ.showProgress((float) lev / (float) extent[0]);
                // select hyperslab for lev
//               start[0] = lev;
//               Object slice = var.read();
              
                long startIdx = lev * stackSize;
                long numElements = stackSize;
                if (wholeDataset instanceof byte[])
                {
                  byte[] tmp = (byte[]) extractSubarray(wholeDataset,
                                                        startIdx,
                                                        numElements);
                  stack.setPixels(tmp,
                                  imp.getStackIndex(cIdx+1,lev+1,fIdx+1));
                }
                else if (wholeDataset instanceof short[])
                {
                  short[] tmp = (short[]) extractSubarray(wholeDataset,
                                                          startIdx,
                                                          numElements);
                  stack.setPixels(tmp,
                                  imp.getStackIndex(cIdx+1,lev+1,fIdx+1));
                }
                else if (wholeDataset instanceof int[])
                {
                  System.out.println("Datatype `int` is not supported. " + 
                                     "Skipping whole frame!");
                  // int[] tmp = (int[]) extractSubarray(wholeDataset,
                  //                                     startIdx,
                  //                                     numElements);
                  // if(datatypeIfUnsupported.getDatatypeClass()
                  //    == Datatype.CLASS_FLOAT)
                  // {
                  //   stack.setPixels(convertInt32ToFloat(tmp),
                  //                   imp.getStackIndex(cIdx+1,lev+1,fIdx+1));
                  // }
                  // if(datatypeIfUnsupported.getDatatypeClass()
                  //    == Datatype.CLASS_INTEGER)
                  // {
                  //   stack.setPixels(convertInt32ToShort(tmp),
                  //                   imp.getStackIndex(cIdx+1,lev+1,fIdx+1));
                  // }
                }
                else if (wholeDataset instanceof long[])
                {
                  System.out.println("Datatype `long` is not supported. " + 
                                     "Skipping whole frame!");
                  // long[] tmp = (long[]) extractSubarray(wholeDataset,
                  //                                       startIdx,
                  //                                       numElements);
                  // if(datatypeIfUnsupported.getDatatypeClass()
                  //    == Datatype.CLASS_FLOAT)
                  // {
                  //   stack.setPixels(convertInt64ToFloat(tmp),
                  //                   imp.getStackIndex(cIdx+1,lev+1,fIdx+1));
                  // }
                  // if(datatypeIfUnsupported.getDatatypeClass()
                  //    == Datatype.CLASS_INTEGER)
                  // {
                  //   stack.setPixels(convertInt64ToShort(tmp),
                  //                   imp.getStackIndex(cIdx+1,lev+1,fIdx+1));
                  // }
                }
                else if (wholeDataset instanceof float[])
                {
                  float[] tmp = (float[]) extractSubarray(wholeDataset,
                                                          startIdx,
                                                          numElements);
                  stack.setPixels(tmp,
                                  imp.getStackIndex(cIdx+1,lev+1,fIdx+1));
                }
                else if (wholeDataset instanceof double[])
                {
                  System.out.println("Datatype `double` is not supported. " + 
                                     "Converting whole frame to `float`!");
                  float[] tmp
                      = convertDoubleToFloat((double[])
                                             extractSubarray(wholeDataset,
                                                             startIdx,
                                                             numElements));
                  stack.setPixels(tmp,
                                  imp.getStackIndex(cIdx+1,lev+1,fIdx+1));
                }
                else
                {
                  // try to put pixels on stack
                  stack.setPixels(extractSubarray(wholeDataset,
                                                  startIdx,
                                                  numElements),
                                  imp.getStackIndex(cIdx+1,lev+1,fIdx+1));
                }
              }
            }
          }
          IJ.showProgress(1.f);
          
          System.out.println("Creating image plus");
          //stack.trim();
          imp = new ImagePlus(directory + name + ": " +
                              groupedVarnames.getFormatString(),
                              stack);
              
          imp.setDimensions(nChannels,nSlices,nFrames);

          if(nChannels>1)
          {
            System.out.println("Creating composite hyperstack with " + 
                               Integer.toString(nChannels) + 
                               " channels.");
            imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
          }
          else
          {
            System.out.println("Creating grayscale hyperstack.");
            //imp = new CompositeImage(imp, CompositeImage.GRAYSCALE);
          }
          

          System.out.println("nFrames: " + Integer.toString(nFrames));
          System.out.println("nSlices: " + Integer.toString(nSlices));
              
          System.out.println("stackSize: " 
                             + Integer.toString(stack.getSize()));

          // set element_size_um
          imp.getCalibration().pixelDepth = elem_sizes[0];
          imp.getCalibration().pixelHeight = elem_sizes[1];
          imp.getCalibration().pixelWidth = elem_sizes[2];


          // System.out.println("   Min = " + minMaxVal[0] + 
          //                    ", Max = " + minMaxVal[1]);
          //imp.setDisplayRange(1.5*minMaxVal[0], 0.5*minMaxVal[1]);
          //imp.resetDisplayRange();
          int[] channelsIJ = {4,2,1};
          for(int c=0;c<nChannels;c++)
          {
            // imp.setDisplayRange(minValChannel[c],
            //                     maxValChannel[c],
            //                     channelsIJ[c]);
            //imp.setSlice(c+1);
            imp.setPosition(c+1,1,1);
            System.out.println("Current channel: "+
                               Integer.toString(imp.getChannel()-1));

            imp.setDisplayRange(minValChannel[c],
                                maxValChannel[c]);
            //,
            //channelsIJ[c]);
            System.out.println("Setting display range for channel "
                               +Integer.toString(c)+" (ij idx: "
                               +Integer.toString(channelsIJ[c])
                               +"): \n\t"
                               +Double.toString(minValChannel[c])
                               +"/"
                               +Double.toString(maxValChannel[c]));
          }
          
          imp.show();
          imp.updateStatusbarValue();
          imp.setOpenAsHyperStack(true); 
        }
        else
        {
          // not supported format
          IJ.error("The file does not contain a supported data set structure!"+
                   "\nChannels have to be 2D or 3D scalar data sets!");
        }
      }
      
      // varList should have size=0 if only grouping is wanted
      // use old style
      for (int i = 0; i < varList.size(); ++i)
      {
        if (gd.getNextBoolean())
        {
          Dataset var = varList.get(i);
          int rank = var.getRank();
          Datatype datatype = var.getDatatype();
          Datatype datatypeIfUnsupported = null;
          long[] extent = var.getDims();

          System.out.println("Reading Variable: " + var.getName());
          System.out.println("   Rank = " + rank + ", Data-type = "
                             + datatype.getDatatypeDescription());
          System.out.print("   Extent in px (level,row,col):");
          for (int d = 0; d < rank; ++d)
            System.out.print(" " + extent[d]);
          System.out.println("");
          IJ.showStatus("Reading Variable: " + var.getName() + " (" + extent[0]
                        + " slices)");

          Attribute elemsize_att = getAttribute(var, "element_size_um");
          double[] elem_sizes = new double[3];
          if (elemsize_att == null)
          {
            elem_sizes[0] = 1.0;
            elem_sizes[1] = 1.0;
            elem_sizes[2] = 1.0;
          }
          else
          {
            System.out.println("Reading element_size_um");
            Object tmp = elemsize_att.getValue();
              if(tmp instanceof float[])
              {
                elem_sizes[0] = ((float[])tmp)[0];
                elem_sizes[1] = ((float[])tmp)[1];
                elem_sizes[2] = ((float[])tmp)[2];
              }
              else if(tmp instanceof double[])
              {
                elem_sizes[0] = ((double[])tmp)[0];
                elem_sizes[1] = ((double[])tmp)[1];
                elem_sizes[2] = ((double[])tmp)[2];
              }
              else
              {
                String title = "Error Reading Element Size";
                String msg = "The element_size_um attribute has "+
                    "wrong format!\n"+
                    "Setting to default size of (1,1,1)...";
                ij.gui.MessageDialog errMsg = new ij.gui.MessageDialog(null,
                                                                       title,
                                                                       msg);
                elem_sizes[0] = 1.0;
                elem_sizes[1] = 1.0;
                elem_sizes[2] = 1.0;              
              }
          }
          System.out.println("   Element-Size in um (level,row,col): "
                             + elem_sizes[0] + ", " + elem_sizes[1] + ", "
                             + elem_sizes[2]);

          // nice gadget to update the progress bar
          long progressDivisor = extent[0] / progressSteps;
          if (progressDivisor < 1)
            progressDivisor = 1;

          // check if we have an unsupported datatype
          if(datatype.getDatatypeClass() == Datatype.CLASS_INTEGER &&
             (datatype.getDatatypeSize() == 4 ||
              datatype.getDatatypeSize() == 8 ))
          {
            System.out.println("Datatype not supported by ImageJ");
            GenericDialog typeSelDiag =
                new GenericDialog("Datatype Selection");
            typeSelDiag.addMessage("The datatype `" +
                                   datatype.getDatatypeDescription() +
                                     "` is not supported by ImageJ.\n\n");
            typeSelDiag.addMessage("Please select your wanted datatype.\n");
            String[] choices = new String[2];
            choices[0] = "float";
            choices[1] = "short";
            typeSelDiag.addChoice("      Possible types are",
                                  choices,"float");
            typeSelDiag.showDialog();
            
            if (typeSelDiag.wasCanceled())
            {
              return;
            }
            int selection = typeSelDiag.getNextChoiceIndex();
            if(selection == 0)
            {
              System.out.println("float selected");
              datatypeIfUnsupported =
                  new H5Datatype(Datatype.CLASS_FLOAT, Datatype.NATIVE,
                                 Datatype.NATIVE, -1);
            }
            if(selection == 1)
            {
              System.out.println("short selected");
              int typeSizeInByte = 2;
              datatypeIfUnsupported =
                  new H5Datatype(Datatype.CLASS_INTEGER, typeSizeInByte,
                                 Datatype.NATIVE, -1);
            }
          }
          
          // read dataset 
          if (rank == 5 && extent[4] == 3)
          {
            System.out.println("   Detected HyperVolume (type RGB).");
            
            // create a new image stack and fill in the data
            ImageStack stack = new ImageStack((int) extent[3],
                                              (int) extent[2]);
            // read the whole dataset, since reading chunked datasets
            // slice-wise is too slow
            
            long[] dims = var.getDims(); // the dimension sizes
            // of the dataset
            long[] selected = var.getSelectedDims(); // the
            // selected
            // size of
            // the
            // dataet
            selected[0] = dims[0];
            selected[1] = dims[1];
            selected[2] = dims[2];
            selected[3] = dims[3];
            selected[4] = dims[4];// 3
            long[] start = var.getStartDims(); // the off set of
            // the selection
            Object wholeDataset = var.read();
            // check for unsigned datatype
            int unsignedConvSelec = 0;
            Datatype dType = var.getDatatype();
            boolean isSigned16Bit = !dType.isUnsigned() && 
                (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) &&
                (dType.getDatatypeSize() == 2);
            
            if(isSigned16Bit)
            {
              GenericDialog convDiag 
                  = new GenericDialog("Unsigend to signed conversion");
              convDiag.addMessage("Detected unsigned datatype, which "+
                                  "is not supported.");
              String[] convOptions = new String[2];
              convOptions[0] = "cut off values";
              convOptions[1] = "convert to float";
              convDiag.addChoice("Please select an conversion option:",
                                 convOptions,
                                 convOptions[0]);
              convDiag.showDialog();
              if(convDiag.wasCanceled())
                  return;
              unsignedConvSelec = convDiag.getNextChoiceIndex();
              wholeDataset = convertToUnsigned(wholeDataset,
                                               unsignedConvSelec);
            }
            
            long stackSize = extent[2]*extent[3];
            long singleVolumeSize = extent[1]*stackSize;

            for (int volIDX = 0; volIDX < extent[0]; ++volIDX)
            {
              if ((volIDX % progressDivisor) == 0)
                  IJ.showProgress((float) volIDX / (float) extent[0]);
//                 start[0] = volIDX;

              for (int lev = 0; lev < extent[1]; ++lev)
              {
                // select hyperslab for lev
//                 start[1] = lev;
//                 Object slice = var.read();
                long startIdx = (volIDX*singleVolumeSize*3)
                    + (lev*stackSize*3);
                long numElements = stackSize*3;

                if (wholeDataset instanceof byte[])
                {
                  byte[] tmp = (byte[]) extractSubarray(wholeDataset,
                                                        startIdx,
                                                        numElements);
                  byte[] rChannel = new byte[(int)stackSize];
                  byte[] gChannel = new byte[(int)stackSize];
                  byte[] bChannel = new byte[(int)stackSize];  
                  for (int row = 0; row < extent[2]; ++row)
                  {
                    for (int col = 0; col < extent[3]; ++col)
                    {
                      int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                      int offset = (row * (int) extent[2]) + col;     
                      rChannel[offset] = tmp[offsetRGB + 0];
                      gChannel[offset] = tmp[offsetRGB + 1];
                      bChannel[offset] = tmp[offsetRGB + 2];
                    }
                  }
                  stack.addSlice(null, rChannel);
                  stack.addSlice(null, gChannel);
                  stack.addSlice(null, bChannel);
                }
                else if (wholeDataset instanceof short[])
                {
                  short[] tmp = (short[]) extractSubarray(wholeDataset,
                                                          startIdx,
                                                          numElements);
                  short[] rChannel = new short[(int)stackSize];
                  short[] gChannel = new short[(int)stackSize];
                  short[] bChannel = new short[(int)stackSize];  
                  for (int row = 0; row < extent[2]; ++row)
                  {
                    for (int col = 0; col < extent[3]; ++col)
                    {
                      int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                      int offset = (row * (int) extent[2]) + col;     
                      rChannel[offset] = tmp[offsetRGB + 0];
                      gChannel[offset] = tmp[offsetRGB + 1];
                      bChannel[offset] = tmp[offsetRGB + 2];
                    }
                  }
                  stack.addSlice(null, rChannel);
                  stack.addSlice(null, gChannel);
                  stack.addSlice(null, bChannel);
                }
                else if (wholeDataset instanceof int[])
                {
                  if(datatypeIfUnsupported.getDatatypeClass()
                     == Datatype.CLASS_FLOAT)
                  {
                    float[] tmp 
                        = convertInt32ToFloat(
                            (int[]) extractSubarray(wholeDataset,
                                                    startIdx,
                                                    numElements));
                    float[] rChannel = new float[(int)stackSize];
                    float[] gChannel = new float[(int)stackSize];
                    float[] bChannel = new float[(int)stackSize];  
                    for (int row = 0; row < extent[2]; ++row)
                    {
                      for (int col = 0; col < extent[3]; ++col)
                      {
                        int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                        int offset = (row * (int) extent[2]) + col;     
                        rChannel[offset] = tmp[offsetRGB + 0];
                        gChannel[offset] = tmp[offsetRGB + 1];
                        bChannel[offset] = tmp[offsetRGB + 2];
                      }
                    }
                    stack.addSlice(null, rChannel);
                    stack.addSlice(null, gChannel);
                    stack.addSlice(null, bChannel);
                  }
                  if(datatypeIfUnsupported.getDatatypeClass()
                     == Datatype.CLASS_INTEGER)
                  {
                    short[] tmp 
                        = convertInt32ToShort(
                            (int[]) extractSubarray(wholeDataset,
                                                    startIdx,
                                                    numElements));
                    short[] rChannel = new short[(int)stackSize];
                    short[] gChannel = new short[(int)stackSize];
                    short[] bChannel = new short[(int)stackSize];  
                    for (int row = 0; row < extent[2]; ++row)
                    {
                      for (int col = 0; col < extent[3]; ++col)
                      {
                        int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                        int offset = (row * (int) extent[2]) + col;     
                        rChannel[offset] = tmp[offsetRGB + 0];
                        gChannel[offset] = tmp[offsetRGB + 1];
                        bChannel[offset] = tmp[offsetRGB + 2];
                      }
                    }
                    stack.addSlice(null, rChannel);
                    stack.addSlice(null, gChannel);
                    stack.addSlice(null, bChannel);
                  }
                }
                else if (wholeDataset instanceof long[])
                {
                  if(datatypeIfUnsupported.getDatatypeClass()
                     == Datatype.CLASS_FLOAT)
                  {
                    float[] tmp 
                        = convertInt64ToFloat(
                            (long[]) extractSubarray(wholeDataset,
                                                    startIdx,
                                                    numElements));
                    float[] rChannel = new float[(int)stackSize];
                    float[] gChannel = new float[(int)stackSize];
                    float[] bChannel = new float[(int)stackSize];  
                    for (int row = 0; row < extent[2]; ++row)
                    {
                      for (int col = 0; col < extent[3]; ++col)
                      {
                        int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                        int offset = (row * (int) extent[2]) + col;     
                        rChannel[offset] = tmp[offsetRGB + 0];
                        gChannel[offset] = tmp[offsetRGB + 1];
                        bChannel[offset] = tmp[offsetRGB + 2];
                      }
                    }
                    stack.addSlice(null, rChannel);
                    stack.addSlice(null, gChannel);
                    stack.addSlice(null, bChannel);
                  }
                  if(datatypeIfUnsupported.getDatatypeClass()
                     == Datatype.CLASS_INTEGER)
                  {
                    short[] tmp 
                        = convertInt64ToShort(
                            (long[]) extractSubarray(wholeDataset,
                                                    startIdx,
                                                    numElements));
                    short[] rChannel = new short[(int)stackSize];
                    short[] gChannel = new short[(int)stackSize];
                    short[] bChannel = new short[(int)stackSize];  
                    for (int row = 0; row < extent[2]; ++row)
                    {
                      for (int col = 0; col < extent[3]; ++col)
                      {
                        int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                        int offset = (row * (int) extent[2]) + col;     
                        rChannel[offset] = tmp[offsetRGB + 0];
                        gChannel[offset] = tmp[offsetRGB + 1];
                        bChannel[offset] = tmp[offsetRGB + 2];
                      }
                    }
                    stack.addSlice(null, rChannel);
                    stack.addSlice(null, gChannel);
                    stack.addSlice(null, bChannel);
                  }
                }
                else if (wholeDataset instanceof float[])
                {
                  float[] tmp = (float[]) extractSubarray(wholeDataset,
                                                          startIdx,
                                                          numElements); 
                  float[] rChannel = new float[(int)stackSize];
                  float[] gChannel = new float[(int)stackSize];
                  float[] bChannel = new float[(int)stackSize];  
                  for (int row = 0; row < extent[2]; ++row)
                  {
                    for (int col = 0; col < extent[3]; ++col)
                    {
                      int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                      int offset = (row * (int) extent[2]) + col;     
                      rChannel[offset] = tmp[offsetRGB + 0];
                      gChannel[offset] = tmp[offsetRGB + 1];
                      bChannel[offset] = tmp[offsetRGB + 2];
                    }
                  }
                  stack.addSlice(null, rChannel);
                  stack.addSlice(null, gChannel);
                  stack.addSlice(null, bChannel);
                }
                else if (wholeDataset instanceof double[])
                {
                  float[] tmp =
                      convertDoubleToFloat((double[])
                                           extractSubarray(wholeDataset,
                                                           startIdx,
                                                           numElements));
                  float[] rChannel = new float[(int)stackSize];
                  float[] gChannel = new float[(int)stackSize];
                  float[] bChannel = new float[(int)stackSize];  
                  for (int row = 0; row < extent[2]; ++row)
                  {
                    for (int col = 0; col < extent[3]; ++col)
                    {
                      int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                      int offset = (row * (int) extent[2]) + col;     
                      rChannel[offset] = tmp[offsetRGB + 0];
                      gChannel[offset] = tmp[offsetRGB + 1];
                      bChannel[offset] = tmp[offsetRGB + 2];
                    }
                  }
                  stack.addSlice(null, rChannel);
                  stack.addSlice(null, gChannel);
                  stack.addSlice(null, bChannel);
                }
                else
                {
                  // try to put pixels on stack
                  stack.addSlice(null, extractSubarray(wholeDataset,
                                                       startIdx,
                                                       numElements));
                }
              }
            }

            IJ.showProgress(1.f);
            ImagePlus imp = new ImagePlus(directory + name + " "
                                          + var.getName(), stack);
            // new for hyperstack
            int nChannels = 3;
            int nSlices = (int) extent[1];
            int nFrames = (int) extent[0];
            System.out.println("nFrames: " + Integer.toString(nFrames));
            System.out.println("nSlices: " + Integer.toString(nSlices));
              
            System.out.println("stackSize: " 
                               + Integer.toString(stack.getSize()));
              
            imp.setDimensions(nChannels,nSlices,nFrames);
            imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
            imp.setOpenAsHyperStack(true); 
            // imp = imp.createHyperStack(directory + name + " "
            //                            + var.getName(),
            //                            nChannels,
            //                            nSlices,
            //                            nFrames,32);
            // imp.setStack(stack,nChannels,nSlices,nFrames);

            imp.getCalibration().pixelDepth = elem_sizes[0];
            imp.getCalibration().pixelHeight = elem_sizes[1];
            imp.getCalibration().pixelWidth = elem_sizes[2];
            //getMinMax();
            //imp.setDisplayRange(0,229);
            imp.resetDisplayRange();
            imp.show();
          }
          else if (rank == 4)
          {
            if (extent[3] == 3)
            {
              System.out.println("   Detected color Image (type RGB).");

              // create a new image stack and fill in the data
              ImageStack stack = new ImageStack((int) extent[2],
                                                (int) extent[1]);

              long[] dims = var.getDims(); // the dimension sizes
              // of the dataset
              long[] selected = var.getSelectedDims(); // the
              // selected
              // size of
              // the
              // dataet
              selected[0] = dims[0];
              selected[1] = dims[1];
              selected[2] = dims[2];
              selected[3] = dims[3];
//               long[] start = var.getStartDims(); // the off set of
//               // the selection

              Object wholeDataset = var.read();
              // check for unsigned datatype
              int unsignedConvSelec = 0;
              Datatype dType = var.getDatatype();
              boolean isSigned16Bit = !dType.isUnsigned() && 
                  (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) &&
                  (dType.getDatatypeSize() == 2);
              if(isSigned16Bit)
              {
                GenericDialog convDiag 
                    = new GenericDialog("Unsigend to signed conversion");
                convDiag.addMessage("Detected unsigned datatype, which "+
                                    "is not supported.");
                String[] convOptions = new String[2];
                convOptions[0] = "cut off values";
                convOptions[1] = "convert to float";
                convDiag.addChoice("Please select an conversion option:",
                                   convOptions,
                                   convOptions[0]);
                convDiag.showDialog();
                if(convDiag.wasCanceled())
                    return;
                unsignedConvSelec = convDiag.getNextChoiceIndex();
                wholeDataset = convertToUnsigned(wholeDataset,
                                                 unsignedConvSelec);
              }

              long stackSize = extent[1]*extent[2]*3;
              double[] minmax = getMinMax(wholeDataset,extent[0]*stackSize);

              for (int lev = 0; lev < extent[0]; ++lev)
              {
                if ((lev % progressDivisor) == 0)
                    IJ.showProgress((float) lev / (float) extent[0]);

//                 // select hyperslab for lev
//                 start[0] = lev;
//                 Object slice = var.read();

                long startIdx = lev * stackSize;
                long numElements = stackSize;
                Object slice = extractSubarray(wholeDataset,
                                               startIdx,
                                               numElements);
                
                int size = (int) (extent[2]*extent[1]);
                if (slice instanceof byte[])
                {
                  byte[] tmp = (byte[]) slice;   
                  byte[] rChannel = new byte[size];
                  byte[] gChannel = new byte[size];
                  byte[] bChannel = new byte[size];  
                  for (int row = 0; row < extent[1]; ++row)
                  {
                    for (int col = 0; col < extent[2]; ++col)
                    {
                      int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                      int offset = (row * (int) extent[2]) + col;  
                      rChannel[offset] = tmp[offsetRGB + 0];
                      gChannel[offset] = tmp[offsetRGB + 1];
                      bChannel[offset] = tmp[offsetRGB + 2];
                    }
                  }
                  stack.addSlice(null, rChannel);
                  stack.addSlice(null, gChannel);
                  stack.addSlice(null, bChannel);
                }
                else if (slice instanceof short[])
                {
                  short[] tmp = (short[]) slice;
                  short[] rChannel = new short[size];
                  short[] gChannel = new short[size];
                  short[] bChannel = new short[size];  
                  for (int row = 0; row < extent[1]; ++row)
                  {
                    for (int col = 0; col < extent[2]; ++col)
                    {
                      int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                      int offset = (row * (int) extent[2]) + col;
                      rChannel[offset] = tmp[offsetRGB + 0];
                      gChannel[offset] = tmp[offsetRGB + 1];
                      bChannel[offset] = tmp[offsetRGB + 2];
                    }
                  }
                  stack.addSlice(null, rChannel);
                  stack.addSlice(null, gChannel);
                  stack.addSlice(null, bChannel);
                }
                else if (slice instanceof int[])
                {
                  int[] tmp = (int[]) slice;
                  int[] rChannel = new int[size];
                  int[] gChannel = new int[size];
                  int[] bChannel = new int[size];  
                  for (int row = 0; row < extent[1]; ++row)
                  {
                    for (int col = 0; col < extent[2]; ++col)
                    {
                      int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                      int offset = (row * (int) extent[2]) + col;
                      rChannel[offset] = tmp[offsetRGB + 0];
                      gChannel[offset] = tmp[offsetRGB + 1];
                      bChannel[offset] = tmp[offsetRGB + 2];
                    }
                  }
                  stack.addSlice(null, rChannel);
                  stack.addSlice(null, gChannel);
                  stack.addSlice(null, bChannel);
                }
                else if (slice instanceof long[])
                {
                  long[] tmp = (long[]) slice;
                  long[] rChannel = new long[size];
                  long[] gChannel = new long[size];
                  long[] bChannel = new long[size];  
                  for (int row = 0; row < extent[1]; ++row)
                  {
                    for (int col = 0; col < extent[2]; ++col)
                    {
                      int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                      int offset = (row * (int) extent[2]) + col;
                      rChannel[offset] = tmp[offsetRGB + 0];
                      gChannel[offset] = tmp[offsetRGB + 1];
                      bChannel[offset] = tmp[offsetRGB + 2];
                    }
                  }
                  stack.addSlice(null, rChannel);
                  stack.addSlice(null, gChannel);
                  stack.addSlice(null, bChannel);
                }
                else if (slice instanceof float[])
                {
                  float[] tmp = (float[]) slice;
                  float[] rChannel = new float[size];
                  float[] gChannel = new float[size];
                  float[] bChannel = new float[size];  
                  for (int row = 0; row < extent[1]; ++row)
                  {
                    for (int col = 0; col < extent[2]; ++col)
                    {
                      int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                      int offset = (row * (int) extent[2]) + col;
                      rChannel[offset] = tmp[offsetRGB + 0];
                      gChannel[offset] = tmp[offsetRGB + 1];
                      bChannel[offset] = tmp[offsetRGB + 2];
                    }
                  }
                  stack.addSlice(null, rChannel);
                  stack.addSlice(null, gChannel);
                  stack.addSlice(null, bChannel);
                }
                else if (slice instanceof double[])
                {
                  double[] tmp = (double[]) slice;
                  double[] rChannel = new double[size];
                  double[] gChannel = new double[size];
                  double[] bChannel = new double[size];  
                  for (int row = 0; row < extent[1]; ++row)
                  {
                    for (int col = 0; col < extent[2]; ++col)
                    {
                      int offsetRGB = (row * (int) extent[2] * 3) + (col * 3);
                      int offset = (row * (int) extent[2]) + col;
                      rChannel[offset] = tmp[offsetRGB + 0];
                      gChannel[offset] = tmp[offsetRGB + 1];
                      bChannel[offset] = tmp[offsetRGB + 2];
                    }
                  }
                  stack.addSlice(null, rChannel);
                  stack.addSlice(null, gChannel);
                  stack.addSlice(null, bChannel);
                }
              }
              ImagePlus imp = new ImagePlus(directory + name + " "
                                          + var.getName(), stack);
              // new for hyperstack
              int nChannels = 3;
              int nSlices = (int) extent[0];
              int nFrames = 1;
              imp.setDimensions(nChannels,nSlices,nFrames);
              imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
              imp.setOpenAsHyperStack(true); 
              
              imp.getCalibration().pixelDepth = elem_sizes[0];
              imp.getCalibration().pixelHeight = elem_sizes[1];
              imp.getCalibration().pixelWidth = elem_sizes[2];

              //getMinMax();
              imp.resetDisplayRange();
              imp.show();
              imp.updateStatusbarValue();
            }
            else // we have a HyperVolume
            {
              System.out.println("   Detected HyperVolume (type GREYSCALE).");

              // create a new image stack and fill in the data
              ImageStack stack = new ImageStack((int) extent[3],
                                                (int) extent[2]);
              ColorProcessor cp;
              // read the whole dataset, since reading chunked datasets
              // slice-wise is too slow

              long[] dims = var.getDims(); // the dimension sizes
              // of the dataset
              long[] selected = var.getSelectedDims(); // the
              // selected
              // size of
              // the
              // dataet
              selected[0] = dims[0];
              selected[1] = dims[1];
              selected[2] = dims[2];
              selected[3] = dims[3];
              long[] start = var.getStartDims(); // the off set of
              // the selection
              Object wholeDataset = var.read();
              // check for unsigned datatype
              int unsignedConvSelec = 0;
              Datatype dType = var.getDatatype();
              boolean isSigned16Bit = !dType.isUnsigned() && 
                  (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) &&
                  (dType.getDatatypeSize() == 2);
              if(isSigned16Bit)
              {
                GenericDialog convDiag 
                    = new GenericDialog("Unsigend to signed conversion");
                convDiag.addMessage("Detected unsigned datatype, which "+
                                    "is not supported.");
                String[] convOptions = new String[2];
                convOptions[0] = "cut off values";
                convOptions[1] = "convert to float";
                convDiag.addChoice("Please select an conversion option:",
                                   convOptions,
                                   convOptions[0]);
                convDiag.showDialog();
                if(convDiag.wasCanceled())
                    return;
                unsignedConvSelec = convDiag.getNextChoiceIndex();
                wholeDataset = convertToUnsigned(wholeDataset,
                                                 unsignedConvSelec);
              }

              long stackSize = extent[2]*extent[3];
              long singleVolumeSize = extent[1]*stackSize;

              for (int volIDX = 0; volIDX < extent[0]; ++volIDX)
              {
                if ((volIDX % progressDivisor) == 0)
                    IJ.showProgress((float) volIDX / (float) extent[0]);
//                 start[0] = volIDX;

                for (int lev = 0; lev < extent[1]; ++lev)
                {
                  // select hyperslab for lev
//                 start[1] = lev;
//                 Object slice = var.read();
                  long startIdx = (volIDX*singleVolumeSize)
                      + (lev*stackSize);
                  long numElements = stackSize;

                  if (wholeDataset instanceof byte[])
                  {
                    byte[] tmp = (byte[]) extractSubarray(wholeDataset,
                                                          startIdx,
                                                          numElements);
                    stack.addSlice(null, tmp);
                  }
                  else if (wholeDataset instanceof short[])
                  {
                    short[] tmp = (short[]) extractSubarray(wholeDataset,
                                                            startIdx,
                                                            numElements);
                    stack.addSlice(null, tmp);
                  }
                  else if (wholeDataset instanceof int[])
                  {
                    int[] tmp = (int[]) extractSubarray(wholeDataset,
                                                        startIdx,
                                                        numElements);
                    if(datatypeIfUnsupported.getDatatypeClass()
                       == Datatype.CLASS_FLOAT)
                    {
                      stack.addSlice(null, convertInt32ToFloat(tmp));
                    }
                    if(datatypeIfUnsupported.getDatatypeClass()
                       == Datatype.CLASS_INTEGER)
                    {
                      stack.addSlice(null, convertInt32ToShort(tmp));
                    }
                  }
                  else if (wholeDataset instanceof long[])
                  {
                    long[] tmp = (long[]) extractSubarray(wholeDataset,
                                                          startIdx,
                                                          numElements);
                    if(datatypeIfUnsupported.getDatatypeClass()
                       == Datatype.CLASS_FLOAT)
                    {
                      stack.addSlice(null, convertInt64ToFloat(tmp));
                    }
                    if(datatypeIfUnsupported.getDatatypeClass()
                       == Datatype.CLASS_INTEGER)
                    {
                      stack.addSlice(null, convertInt64ToShort(tmp));
                    }
                  }
                  else if (wholeDataset instanceof float[])
                  {
                    float[] tmp = (float[]) extractSubarray(wholeDataset,
                                                            startIdx,
                                                            numElements);
                    stack.addSlice(null, tmp);
                  }
                  else if (wholeDataset instanceof double[])
                  {
                    float[] tmp =
                        convertDoubleToFloat((double[])
                                             extractSubarray(wholeDataset,
                                                             startIdx,
                                                             numElements));
                    stack.addSlice(null, tmp);
                  }
                  else
                  {
                    // try to put pixels on stack
                    stack.addSlice(null, extractSubarray(wholeDataset,
                                                         startIdx,
                                                         numElements));
                  }
                }
              }

              IJ.showProgress(1.f);
              ImagePlus imp = new ImagePlus(directory + name + " "
                                            + var.getName(), stack);
              // new for hyperstack
              int nChannels = 1;
              int nSlices = (int) extent[1];
              int nFrames = (int) extent[0];
              Integer nFramesI = new Integer(nFrames);
              Integer nSlicesI = new Integer(nSlices);
              System.out.println("nFrames: " + nFramesI.toString());
              System.out.println("nSlices: " + nSlicesI.toString());
              
              Integer myStackSize = new Integer(stack.getSize());
              System.out.println("stackSize: " + myStackSize.toString());
              
              imp.setDimensions(nChannels,nSlices,nFrames);
              imp.setOpenAsHyperStack(true); 

              imp.getCalibration().pixelDepth = elem_sizes[0];
              imp.getCalibration().pixelHeight = elem_sizes[1];
              imp.getCalibration().pixelWidth = elem_sizes[2];
              //getMinMax();
              //imp.setDisplayRange(0,229);
              imp.resetDisplayRange();
              imp.show();
            }
          }
          else if (rank == 3 && extent[2] == 3)
          {
            System.out.println("This is an rgb image");
            // create a new image stack and fill in the data
            ImageStack stack = new ImageStack((int) extent[1],
                                              (int) extent[0]);
            ColorProcessor cp;

            long[] dims = var.getDims(); // the dimension sizes
            long[] selected = var.getSelectedDims(); // the
            // selected
            // size of
            // the
            // dataet
            selected[0] = extent[0];
            selected[1] = extent[1];
            selected[2] = extent[2];
            Object slice = var.read();
            // check for unsigned datatype
            int unsignedConvSelec = 0;
            Datatype dType = var.getDatatype();
            boolean isSigned16Bit = !dType.isUnsigned() && 
                (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) &&
                (dType.getDatatypeSize() == 2);
            if(isSigned16Bit)
            {
              GenericDialog convDiag 
                  = new GenericDialog("Unsigend to signed conversion");
              convDiag.addMessage("Detected unsigned datatype, which "+
                                  "is not supported.");
              String[] convOptions = new String[2];
              convOptions[0] = "cut off values";
              convOptions[1] = "convert to float";
              convDiag.addChoice("Please select an conversion option:",
                                 convOptions,
                                 convOptions[0]);
              convDiag.showDialog();
              if(convDiag.wasCanceled())
                  return;
              unsignedConvSelec = convDiag.getNextChoiceIndex();
              slice = convertToUnsigned(slice,
                                        unsignedConvSelec);
            }

            int size = (int) (extent[1]*extent[0]);
            double[] minmax = getMinMax(slice,3*size);

            // ugly but working: copy pixel by pixel
            if (slice instanceof byte[])
            {
              byte[] tmp = (byte[]) slice;   
              byte[] rChannel = new byte[size];
              byte[] gChannel = new byte[size];
              byte[] bChannel = new byte[size];  
              for (int row = 0; row < extent[0]; ++row)
              {
                for (int col = 0; col < extent[1]; ++col)
                {
                  int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
                  int offset = (row * (int) extent[1]) + col;     
                  rChannel[offset] = tmp[offsetRGB + 0];
                  gChannel[offset] = tmp[offsetRGB + 1];
                  bChannel[offset] = tmp[offsetRGB + 2];
                }
              }
              stack.addSlice(null, rChannel);
              stack.addSlice(null, gChannel);
              stack.addSlice(null, bChannel);
            }
            else if (slice instanceof short[])
            {
              short[] tmp = (short[]) slice;
              short[] rChannel = new short[size];
              short[] gChannel = new short[size];
              short[] bChannel = new short[size];  
              for (int row = 0; row < extent[0]; ++row)
              {
                for (int col = 0; col < extent[1]; ++col)
                {
                  int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
                  int offset = (row * (int) extent[1]) + col;    
                  rChannel[offset] = tmp[offsetRGB + 0];
                  gChannel[offset] = tmp[offsetRGB + 1];
                  bChannel[offset] = tmp[offsetRGB + 2];
                }
              }
              stack.addSlice(null, rChannel);
              stack.addSlice(null, gChannel);
              stack.addSlice(null, bChannel);
            }
            else if (slice instanceof int[])
            {
              int[] tmp = (int[]) slice;
              int[] rChannel = new int[size];
              int[] gChannel = new int[size];
              int[] bChannel = new int[size];  
              for (int row = 0; row < extent[0]; ++row)
              {
                for (int col = 0; col < extent[1]; ++col)
                {
                  int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
                  int offset = (row * (int) extent[1]) + col;   
                  rChannel[offset] = tmp[offsetRGB + 0];
                  gChannel[offset] = tmp[offsetRGB + 1];
                  bChannel[offset] = tmp[offsetRGB + 2];
                }
              }
              stack.addSlice(null, rChannel);
              stack.addSlice(null, gChannel);
              stack.addSlice(null, bChannel);
            }
            else if (slice instanceof long[])
            {
              long[] tmp = (long[]) slice;
              long[] rChannel = new long[size];
              long[] gChannel = new long[size];
              long[] bChannel = new long[size];  
              for (int row = 0; row < extent[0]; ++row)
              {
                for (int col = 0; col < extent[1]; ++col)
                {
                  int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
                  int offset = (row * (int) extent[1]) + col;  
                  rChannel[offset] = tmp[offsetRGB + 0];
                  gChannel[offset] = tmp[offsetRGB + 1];
                  bChannel[offset] = tmp[offsetRGB + 2];
                }
              }
              stack.addSlice(null, rChannel);
              stack.addSlice(null, gChannel);
              stack.addSlice(null, bChannel);
            }
            else if (slice instanceof float[])
            {
              float[] tmp = (float[]) slice;
              float[] rChannel = new float[size];
              float[] gChannel = new float[size];
              float[] bChannel = new float[size];  
              for (int row = 0; row < extent[0]; ++row)
              {
                for (int col = 0; col < extent[1]; ++col)
                {
                  int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
                  int offset = (row * (int) extent[1]) + col;  
                  rChannel[offset] = tmp[offsetRGB + 0];
                  gChannel[offset] = tmp[offsetRGB + 1];
                  bChannel[offset] = tmp[offsetRGB + 2];
                }
              }
              stack.addSlice(null, rChannel);
              stack.addSlice(null, gChannel);
              stack.addSlice(null, bChannel);
            }
            else if (slice instanceof double[])
            {
              double[] tmp = (double[]) slice;
              double[] rChannel = new double[size];
              double[] gChannel = new double[size];
              double[] bChannel = new double[size];  
              for (int row = 0; row < extent[0]; ++row)
              {
                for (int col = 0; col < extent[1]; ++col)
                {
                  int offsetRGB = (row * (int) extent[1] * 3) + (col * 3);
                  int offset = (row * (int) extent[1]) + col;  
                  rChannel[offset] = tmp[offsetRGB + 0];
                  gChannel[offset] = tmp[offsetRGB + 1];
                  bChannel[offset] = tmp[offsetRGB + 2];
                }
              }
              stack.addSlice(null, rChannel);
              stack.addSlice(null, gChannel);
              stack.addSlice(null, bChannel);
            }
            IJ.showProgress(1.f);
            ImagePlus imp = new ImagePlus(directory + name + " "
                                          + var.getName(), stack);
            // new for hyperstack
            int nChannels = 3;
            int nSlices = 1;
            int nFrames = 1;
            imp.setDimensions(nChannels,nSlices,nFrames);
            imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
            imp.setOpenAsHyperStack(true); 

            imp.getCalibration().pixelDepth = elem_sizes[0];
            imp.getCalibration().pixelHeight = elem_sizes[1];
            imp.getCalibration().pixelWidth = elem_sizes[2];
            //getMinMax();
            imp.resetDisplayRange();
            imp.show();
            imp.updateStatusbarValue();
          }
          else if (rank == 3)
          {
            System.out.println("Rank is 3");

            // create a new image stack and fill in the data
            ImageStack stack = new ImageStack((int) extent[2],
                                              (int) extent[1]);

            long[] selected = var.getSelectedDims(); // the
              // selected
              // size of
              // the
              // dataet
            selected[0] = extent[0];
            selected[1] = extent[1];
            selected[2] = extent[2];
            Object wholeDataset = var.read();
            // check for unsigned datatype
            int unsignedConvSelec = 0;
            Datatype dType = var.getDatatype();
            boolean isSigned16Bit = !dType.isUnsigned() && 
                (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) &&
                (dType.getDatatypeSize() == 2);
            
            if(isSigned16Bit)
            {
              GenericDialog convDiag 
                  = new GenericDialog("Unsigend to signed conversion");
              convDiag.addMessage("Detected unsigned datatype, which "+
                                  "is not supported.");
              String[] convOptions = new String[2];
              convOptions[0] = "cut off values";
              convOptions[1] = "convert to float";
              convDiag.addChoice("Please select an conversion option:",
                                 convOptions,
                                 convOptions[0]);
              convDiag.showDialog();
              if(convDiag.wasCanceled())
                  return;
              unsignedConvSelec = convDiag.getNextChoiceIndex();
              wholeDataset = convertToUnsigned(wholeDataset,
                                               unsignedConvSelec);
            }

            long stackSize = extent[1]*extent[2];

            for (int lev = 0; lev < extent[0]; ++lev)
            {
              if ((lev % progressDivisor) == 0)
                IJ.showProgress((float) lev / (float) extent[0]);
              // select hyperslab for lev
//               start[0] = lev;
//               Object slice = var.read();

              long startIdx = lev * stackSize;
              long numElements = stackSize;
              if (wholeDataset instanceof byte[])
              {
                byte[] tmp = (byte[]) extractSubarray(wholeDataset,
                                                      startIdx,
                                                      numElements);
                stack.addSlice(null, tmp);
              }
              else if (wholeDataset instanceof short[])
              {
                short[] tmp = (short[]) extractSubarray(wholeDataset,
                                                        startIdx,
                                                        numElements);
                stack.addSlice(null, tmp);
              }
              else if (wholeDataset instanceof int[])
              {
                int[] tmp = (int[]) extractSubarray(wholeDataset,
                                                    startIdx,
                                                    numElements);
                if(datatypeIfUnsupported.getDatatypeClass()
                   == Datatype.CLASS_FLOAT)
                {
                  stack.addSlice(null, convertInt32ToFloat(tmp));
                }
                if(datatypeIfUnsupported.getDatatypeClass()
                   == Datatype.CLASS_INTEGER)
                {
                  stack.addSlice(null, convertInt32ToShort(tmp));
                }
              }
              else if (wholeDataset instanceof long[])
              {
                long[] tmp = (long[]) extractSubarray(wholeDataset,
                                                      startIdx,
                                                      numElements);
                if(datatypeIfUnsupported.getDatatypeClass()
                   == Datatype.CLASS_FLOAT)
                {
                  stack.addSlice(null, convertInt64ToFloat(tmp));
                }
                if(datatypeIfUnsupported.getDatatypeClass()
                   == Datatype.CLASS_INTEGER)
                {
                  stack.addSlice(null, convertInt64ToShort(tmp));
                }
              }
              else if (wholeDataset instanceof float[])
              {
                float[] tmp = (float[]) extractSubarray(wholeDataset,
                                                        startIdx,
                                                        numElements);
                stack.addSlice(null, tmp);
              }
              else if (wholeDataset instanceof double[])
              {
                float[] tmp
                    = convertDoubleToFloat((double[])
                                           extractSubarray(wholeDataset,
                                                           startIdx,
                                                           numElements));
                stack.addSlice(null, tmp);
              }
              else
              {
                // try to put pixels on stack
                stack.addSlice(null, extractSubarray(wholeDataset,
                                                     startIdx,
                                                     numElements));
              }
            }
            IJ.showProgress(1.f);
            ImagePlus imp = new ImagePlus(directory + name + " "
                                          + var.getName(), stack);
            imp.getCalibration().pixelDepth = elem_sizes[0];
            imp.getCalibration().pixelHeight = elem_sizes[1];
            imp.getCalibration().pixelWidth = elem_sizes[2];
            //getMinMax();
            imp.resetDisplayRange();
            imp.show();
            imp.updateStatusbarValue();
          }
          else if (rank == 2)
          {

            // check if we have an unsupported datatype
            if(datatype.getDatatypeClass() == Datatype.CLASS_INTEGER &&
               (datatype.getDatatypeSize() == 4 ||
                datatype.getDatatypeSize() == 8))
            {
              System.out.println("Datatype not supported by ImageJ");
              GenericDialog typeSelDiag =
                  new GenericDialog("Datatype Selection");
              typeSelDiag.addMessage("The datatype `" +
                                     datatype.getDatatypeDescription() +
                                     "` is not supported by ImageJ.\n\n");
              typeSelDiag.addMessage("Please select your wanted datatype.\n");
              String[] choices = new String[2];
              choices[0] = "float";
              choices[1] = "short";
              typeSelDiag.addChoice("      Possible types are",
                                    choices,"float");
              typeSelDiag.showDialog();

              if (typeSelDiag.wasCanceled())
              {
                return;
              }
              int selection = typeSelDiag.getNextChoiceIndex();
              if(selection == 0)
              {
                System.out.println("float selected");
                datatypeIfUnsupported =
                    new H5Datatype(Datatype.CLASS_FLOAT, Datatype.NATIVE,
                                   Datatype.NATIVE, -1);
              }
              if(selection == 1)
              {
                System.out.println("short selected");
                int typeSizeInByte = 2;
                datatypeIfUnsupported =
                    new H5Datatype(Datatype.CLASS_INTEGER, typeSizeInByte,
                                   Datatype.NATIVE, -1);
              }
            }
            IJ.showProgress(0.f);
            ImageStack stack = new ImageStack((int) extent[1],
                                              (int) extent[0]);
            Object slice = var.read();
            // check for unsigned datatype
            int unsignedConvSelec = 0;
            Datatype dType = var.getDatatype();
            boolean isSigned16Bit = !dType.isUnsigned() && 
                (dType.getDatatypeClass() == Datatype.CLASS_INTEGER) &&
                (dType.getDatatypeSize() == 2);
            if(isSigned16Bit)
            {
              GenericDialog convDiag 
                  = new GenericDialog("Unsigend to signed conversion");
              convDiag.addMessage("Detected unsigned datatype, which "+
                                  "is not supported.");
              String[] convOptions = new String[2];
              convOptions[0] = "cut off values";
              convOptions[1] = "convert to float";
              convDiag.addChoice("Please select an conversion option:",
                                 convOptions,
                                 convOptions[0]);
              convDiag.showDialog();
              if(convDiag.wasCanceled())
                  return;
              unsignedConvSelec = convDiag.getNextChoiceIndex();
              slice = convertToUnsigned(slice,
                                        unsignedConvSelec);
            }

            if (slice instanceof byte[])
            {
              byte[] tmp = (byte[]) slice;
              stack.addSlice(null, tmp);
            }
            else if (slice instanceof short[])
            {
              short[] tmp = (short[]) slice;
              stack.addSlice(null, tmp);
            }
            else if (slice instanceof int[])
            {
              int[] tmp = (int[]) slice;
              if(datatypeIfUnsupported.getDatatypeClass()
                 == Datatype.CLASS_FLOAT)
              {
                stack.addSlice(null, convertInt32ToFloat(tmp));
              }
              if(datatypeIfUnsupported.getDatatypeClass()
                 == Datatype.CLASS_INTEGER)
              {
                stack.addSlice(null, convertInt32ToShort(tmp));
              }
            }
            else if (slice instanceof long[])
            {
              long[] tmp = (long[]) slice;
              if(datatypeIfUnsupported.getDatatypeClass()
                 == Datatype.CLASS_FLOAT)
              {
                stack.addSlice(null, convertInt64ToFloat(tmp));
              }
              if(datatypeIfUnsupported.getDatatypeClass()
                 == Datatype.CLASS_INTEGER)
              {
                stack.addSlice(null, convertInt64ToShort(tmp));
              }
            }
            else if (slice instanceof float[])
            {
              float[] tmp = (float[]) slice;
              stack.addSlice(null, tmp);
            }
            else if (slice instanceof double[])
            {
              float[] tmp = convertDoubleToFloat((double[]) slice);

              stack.addSlice(null, tmp);
            }
            else
            {
              // try to put pixels on stack
              stack.addSlice(null, slice);
            }
            IJ.showProgress(1.f);
            ImagePlus imp = new ImagePlus(directory + name + " "
                                          + var.getName(), stack);
            imp.getProcessor().resetMinAndMax();
            imp.show();

            ImageProcessor ips = imp.getProcessor();

            double imgMax = ips.getMax();
            double imgMin = ips.getMin();

            System.out.println("   Min = " + imgMin + ", Max = " + imgMax);
            ips.setMinAndMax(imgMin, imgMax);
            imp.updateAndDraw();
            imp.show();
            imp.updateStatusbarValue();
          }
          else
          {
            System.err.println("   Error: Variable Dimensions " + rank
                               + " not supported (yet).");
            IJ.showStatus("Variable Dimension " + rank + " not supported");
          }
        }
      }

    }
    catch (java.io.IOException err)
    {
      System.err.println("Error while opening '" + directory + name + "'");
      System.err.println(err);
      IJ.showStatus("Error opening file.");
    }
    catch (HDFException err)
    {
      System.err.println("Error while opening '" + directory + name + "'");
      System.err.println(err);
      IJ.showStatus("Error opening file.");
    }
    catch (HDF5Exception err)
    {
      System.err.println("Error while opening '" + directory + name + "'");
      System.err.println(err);
      IJ.showStatus("Error opening file.");
    }
    catch (Exception err)
    {
      System.err.println("Error while opening '" + directory + name + "'");
      System.err.println(err);
      IJ.showStatus("Error opening file.");
    }
    catch (OutOfMemoryError o)
    {
      IJ.outOfMemory("Load HDF5");
    }
    // make sure the file is closed after working with it
    // FIXME: should happen in catch-part, too!
    try
    {
      if (inFile != null)
        inFile.close();
    }
    catch (HDF5Exception err)
    {
      System.err.println("Error while closing '" + directory + name + "'");
      System.err.println(err);
      IJ.showStatus("Error closing file.");
    }

    IJ.showProgress(1.0);
  }

  // int byteToUnsignedByte(int n)
  // {
  //   if (n < 0)
  //     return (256 + n);
  //   return n;
  // }

  private int progressSteps = 50;

  /*-----------------------------------------------------------------------
   *  helpers for hdf5 library
   *-----------------------------------------------------------------------*/
  private static List<Dataset>
  getDataSetList(Group g, List<Dataset> datasets) throws Exception
  {
    if (g == null)
      return datasets;

    List members = g.getMemberList();
    int n = members.size();
    HObject obj = null;
    for (int i = 0; i < n; i++)
    {
      obj = (HObject) members.get(i);
      if (obj instanceof Dataset)
      {
        ((Dataset) obj).init();
        datasets.add((Dataset) obj);
        //System.out.println(obj.getFullName());
      }
      else if (obj instanceof Group)
      {
        datasets = (getDataSetList((Group) obj, datasets));
      }
    }
    return datasets;
  }

  private static List<Attribute> getAttrList(HObject ds) throws Exception
  {
    if (ds == null)
      return null;

    List<Attribute> attributes = new ArrayList<Attribute>();
    List members = ds.getMetadata();
    int n = members.size();
    Metadata obj = null;
    for (int i = 0; i < n; i++)
    {
      obj = (Metadata) members.get(i);
      if (obj instanceof Attribute)
      {
        try
        {
          System.out.println(((Attribute) obj).getName());
          attributes.add((Attribute) obj);
        }
        catch (java.lang.UnsupportedOperationException e)
        {
          System.out
              .println("Caught UnsupportedOperationException datasets2.add((Dataset) obj)");
          System.out.println(e.getMessage());
        }
      }
    }
    return attributes;
  }

  private static Attribute
  getAttribute(Dataset ds, String attrName) throws Exception
  {
    List<Attribute> attrList = getAttrList((HObject)ds);
    Iterator<Attribute> attrIter = attrList.iterator();

    while (attrIter.hasNext())
    {
      Attribute attr = attrIter.next();
      if (attr.getName().equals(attrName))
      {
        return attr;
      }
    }
    return null;
  }

  private static Attribute
  getAttribute(HObject ds, String attrName) throws Exception
  {
    List<Attribute> attrList = getAttrList(ds);
    Iterator<Attribute> attrIter = attrList.iterator();

    while (attrIter.hasNext())
    {
      Attribute attr = attrIter.next();
      System.out.println(attr.getName());
      if (attr.getName().equals(attrName))
      {
        return attr;
      }
    }
    return null;
  }
  /*-----------------------------------------------------------------------
   *  minmax of array
   *-----------------------------------------------------------------------*/
  private double[] getMinMax(Object data, long stackSize)
  {
    double[] minmax = new double[2];
    
    if (data instanceof byte[])
    {
      byte[] tmp = (byte[]) data;
      minmax[0]=tmp[0];
      minmax[1]=tmp[0];
      for(int i=1; i<stackSize;i++)
      {
        double val = (double)tmp[i];
        // we only support unsigned
        if(tmp[i]<0)
            val = (float)Byte.MAX_VALUE -
                (float)Byte.MIN_VALUE +
                (float)tmp[i] + 1;
        
        if(val<minmax[0])
            minmax[0]=val;
        if(val>minmax[1])
            minmax[1]=val;
      }
    }
    else if (data instanceof short[])
    {
      short[] tmp = (short[]) data;
      minmax[0]=tmp[0];
      minmax[1]=tmp[0];
      for(int i=1; i<stackSize;i++)
      {
        double val = (double)tmp[i];
        // we only support unsigned
        if(tmp[i]<0)
            val = (float)Short.MAX_VALUE -
                (float)Short.MIN_VALUE +
                (float)tmp[i] + 1;
        
        if(val<minmax[0])
            minmax[0]=val;
        if(val>minmax[1])
            minmax[1]=val;
      }
    }
    else if (data instanceof int[])
    {
      int[] tmp = (int[]) data;
      minmax[0]=tmp[0];
      minmax[1]=tmp[0];
      for(int i=1; i<stackSize;i++)
      {
        if(tmp[i]<minmax[0])
            minmax[0]=tmp[i];
        if(tmp[i]>minmax[1])
            minmax[1]=tmp[i];
      }
    }
    else if (data instanceof long[])
    {
      long[] tmp = (long[]) data;
      minmax[0]=tmp[0];
      minmax[1]=tmp[0];
      for(int i=1; i<stackSize;i++)
      {
        if(tmp[i]<minmax[0])
            minmax[0]=tmp[i];
        if(tmp[i]>minmax[1])
            minmax[1]=tmp[i];
      }
    }
    else if (data instanceof float[])
    {
      float[] tmp = (float[]) data;
      minmax[0]=tmp[0];
      minmax[1]=tmp[0];
      for(int i=1; i<stackSize;i++)
      {
        if(tmp[i]<minmax[0])
            minmax[0]=tmp[i];
        if(tmp[i]>minmax[1])
            minmax[1]=tmp[i];
      }
    }
    else if (data instanceof double[])
    {
      double[] tmp = (double[]) data;
      minmax[0]=tmp[0];
      minmax[1]=tmp[0];
      for(int i=1; i<stackSize;i++)
      {
        if(tmp[i]<minmax[0])
            minmax[0]=tmp[i];
        if(tmp[i]>minmax[1])
            minmax[1]=tmp[i];
      }
    }
    System.out.println("min: " + minmax[0] + ", max: " + minmax[1]);
    return minmax;
  }
  /*-----------------------------------------------------------------------
   *  converter functions
   *-----------------------------------------------------------------------*/
  private float[] convertDoubleToFloat(double[] dataIn)
  {
    float[] dataOut = new float[dataIn.length];
    for (int index = 0; index < dataIn.length; index++)
    {
      dataOut[index] = (float) dataIn[index];
    }
    return dataOut;
  }

  private float[] convertInt32ToFloat(int[] dataIn)
  {
    float[] dataOut = new float[dataIn.length];
    for (int index = 0; index < dataIn.length; index++)
    {
      dataOut[index] = dataIn[index];
    }
    return dataOut;
  }

  private short[] convertInt32ToShort(int[] dataIn)
  {
    short[] dataOut = new short[dataIn.length];
    for (int index = 0; index < dataIn.length; index++)
    {
      dataOut[index] = (short) dataIn[index];
    }
    return dataOut;
  }

  private float[] convertInt64ToFloat(long[] dataIn)
  {
    float[] dataOut = new float[dataIn.length];
    for (int index = 0; index < dataIn.length; index++)
    {
      dataOut[index] = dataIn[index];
    }
    return dataOut;
  }

  private short[] convertInt64ToShort(long[] dataIn)
  {
    short[] dataOut = new short[dataIn.length];
    for (int index = 0; index < dataIn.length; index++)
    {
      dataOut[index] = (short) dataIn[index];
    }
    return dataOut;
  }


  private Object convertToUnsigned(Object dataIn,
                                   int unsignedConvSelec)
  {
    Object dataOut = null;
    if(unsignedConvSelec == 0)
    {
      // cut off values
      if(dataIn instanceof short[])
      {
        short[] tmp = (short[]) dataIn;
        for(int i=0;i<tmp.length;i++)
            if(tmp[i]<0) tmp[i] = 0;
        dataOut = (Object) tmp;
      }
    }
    else if(unsignedConvSelec == 1)
    {
      // convert to float
      if(dataIn instanceof short[])
      {
        System.out.println("Converting to float");
        short[] tmpIn = (short[]) dataIn;
        float[] tmp = new float[tmpIn.length];
        for(int i=0;i<tmp.length;i++)
            tmp[i] = (float)tmpIn[i];
        dataOut = (Object) tmp;
      }
    }
    return dataOut;
  }
  /*-----------------------------------------------------------------------
   *  extract subarrays
   *-----------------------------------------------------------------------*/
  Object extractSubarray(Object data,
                         long startIdx,
                         long numElements)
  {
    Object subarray = null;

    if (data instanceof byte[])
    {
      subarray = new byte[(int) numElements];
      for(long idx=startIdx;idx<startIdx+numElements;idx++)
      {
        ((byte[]) subarray)[(int) (idx-startIdx)]
            = ((byte[]) data)[(int) (idx)];
      }
    }
    else if (data instanceof short[])
    {
      subarray = new short[(int) numElements];
      for(long idx=startIdx;idx<startIdx+numElements;idx++)
      {
        ((short[]) subarray)[(int) (idx-startIdx)]
            = ((short[]) data)[(int) (idx)];
      }
    }
    else if (data instanceof int[])
    {
      subarray = new int[(int) numElements];
      for(long idx=startIdx;idx<startIdx+numElements;idx++)
      {
        ((int[]) subarray)[(int) (idx-startIdx)]
            = ((int[]) data)[(int) (idx)];
      }
    }
    else if (data instanceof long[])
    {
      subarray = new long[(int) numElements];
      for(long idx=startIdx;idx<startIdx+numElements;idx++)
      {
        ((long[]) subarray)[(int) (idx-startIdx)]
            = ((long[]) data)[(int) (idx)];
      }
    }
    else if (data instanceof float[])
    {
      subarray = new float[(int) numElements];
      for(long idx=startIdx;idx<startIdx+numElements;idx++)
      {
        ((float[]) subarray)[(int) (idx-startIdx)]
            = ((float[]) data)[(int) (idx)];
      }
    }
    else if (data instanceof double[])
    {
      subarray = new double[(int) numElements];
      for(long idx=startIdx;idx<startIdx+numElements;idx++)
      {
        ((double[]) subarray)[(int) (idx-startIdx)]
            = ((double[]) data)[(int) (idx)];
      }
    }
    return subarray;
  }


/** Adds AWT scroll bars to the given container. */
  public static void addScrollBars(Container pane) {
    GridBagLayout layout = (GridBagLayout) pane.getLayout();

    // extract components
    int count = pane.getComponentCount();
    Component[] c = new Component[count];
    GridBagConstraints[] gbc = new GridBagConstraints[count];
    for (int i=0; i<count; i++) {
      c[i] = pane.getComponent(i);
      gbc[i] = layout.getConstraints(c[i]);
    }

    // clear components
    pane.removeAll();
    layout.invalidateLayout(pane);

    // create new container panel
    Panel newPane = new Panel();
    GridBagLayout newLayout = new GridBagLayout();
    newPane.setLayout(newLayout);
    for (int i=0; i<count; i++) {
      newLayout.setConstraints(c[i], gbc[i]);
      newPane.add(c[i]);
    }

    // HACK - get preferred size for container panel
    // NB: don't know a better way:
    // - newPane.getPreferredSize() doesn't work
    // - newLayout.preferredLayoutSize(newPane) doesn't work
    Frame f = new Frame();
    f.setLayout(new BorderLayout());
    f.add(newPane, BorderLayout.WEST);
    f.pack();
    final Dimension size = newPane.getSize();
    f.remove(newPane);
    f.dispose();

    // compute best size for scrollable viewport
    size.width += 15;
    size.height += 15;
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    int maxWidth = 3 * screen.width / 4;
    int maxHeight = 3 * screen.height / 4;
    if (size.width > maxWidth) size.width = maxWidth;
    if (size.height > maxHeight) size.height = maxHeight;

    // create scroll pane
    ScrollPane scroll = new ScrollPane() {
          public Dimension getPreferredSize() {
            return size;
          }
        };
    scroll.add(newPane);

    // add scroll pane to original container
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    layout.setConstraints(scroll, constraints);
    pane.add(scroll);
  }
}

