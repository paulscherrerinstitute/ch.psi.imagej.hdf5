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


import ij.*;
import ij.io.*;
import ij.plugin.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.lang.Math;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;

import ncsa.hdf.object.*; // the common object package
import ncsa.hdf.object.h5.*; // the HDF5 implementation
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdflib.HDFException;

public class HDF5_Writer_ implements PlugInFilter
{
  private Boolean _batchMode = false;
  private String[] _batchVarNames = null;
  private String _batchFileName = null;
  public int setup(String arg, ImagePlus imp)
  {
    if (arg.equals("about"))
    {
      showAbout();
      return DONE;
    }
    // FIXME: set DOES_xx for image type here:
    // currently RGB-Types are still missing
    // see
    // http://rsb.info.nih.gov/ij/developer/api/ij/plugin/filter/PlugInFilter.html
    return DOES_8G + DOES_16 + DOES_32 + DOES_RGB
           + NO_CHANGES;
  }

  public void setToBatchMode(String filename, String [] varnames)
  {
    _batchMode = true;
    _batchFileName = filename;
    _batchVarNames = varnames;
  }

  public void run(ImageProcessor ip)
  {
    int[] wList = WindowManager.getIDList();
    
    if (wList == null)
    {
      IJ.error("No windows are open.");
      return;
    }

    String filename = null;
    if(_batchMode)
    {
      filename = _batchFileName;
    }
    else
    {
      SaveDialog sd = new SaveDialog("Save HDF5 ...", "", ".h5");
      String directory = sd.getDirectory();
      String name = sd.getFileName();
      filename = directory + name;

      if (name == null)
          return;
      if (name == "")
          return;
    }
    
    
    // Retrieve an instance of the implementing class for the HDF5 format
    FileFormat fileFormat = 
        FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5);

    // If the implementing class wasn't found, it's an error.
    if (fileFormat == null)
    {
      System.err.println("Cannot find HDF5 FileFormat.");
      return;
    }

    String[] varNames = null;
    if(_batchMode)
    {
      varNames = _batchVarNames;
    }

    ImagePlus imp = WindowManager.getCurrentImage();

    GenericDialog gd = null;
    gd = new GenericDialog("Variable Name Selection");

    // check for hyperstack
    if(imp.getOpenAsHyperStack() || imp.isHyperStack())
    {
      System.out.println("This is a hyperstack");
      boolean splitChannels = true;
      gd.addCheckbox("Split frames and channels",splitChannels);
      gd.addStringField(imp.getTitle(), "/t$T/channel$C");
      String title = imp.getTitle();
      int nDims = imp.getNDimensions();
      int nFrames = imp.getNFrames();
      int nChannels = imp.getNChannels();
      int nLevs = imp.getNSlices();
      int nRows = imp.getHeight();
      int nCols = imp.getWidth();
      boolean isComposite = imp.isComposite() ;
      System.out.println("isComposite: "+Boolean.toString(isComposite));
      System.out.println("Saving image \""+title+"\"");
      System.out.println("nDims: "+Integer.toString(nDims));
      System.out.println("nFrames: "+Integer.toString(nFrames));
      System.out.println("nChannels: "+Integer.toString(nChannels));
      System.out.println("nSlices: "+Integer.toString(nLevs));
      System.out.println("nRows: "+Integer.toString(nRows));
      System.out.println("nCols: "+Integer.toString(nCols));
      gd.showDialog();
      if (gd.wasCanceled())
      {
        IJ.error("Plugin canceled!");
        return;
      }
      splitChannels = gd.getNextBoolean();
      String formatString = gd.getNextString();
      System.out.println("formatString: "+formatString);
      System.out.println("Bitdepth: "+ imp.getBitDepth());
      System.out.println("Saving HDF5 File: " + filename);
        
      int imgColorDepth = imp.getBitDepth();
      int imgColorType = imp.getType();
      Datatype type = null;
      if (imgColorType == ImagePlus.GRAY8)
      {
        System.out.println("   bit depth: " + imgColorDepth
                           + ", type: GRAY8");
        type = new H5Datatype(Datatype.CLASS_CHAR, Datatype.NATIVE,
                              Datatype.NATIVE, Datatype.SIGN_NONE);
      }
      else if (imgColorType == ImagePlus.GRAY16)
      {
        System.out.println("   bit depth: " + imgColorDepth
                           + ", type: GRAY16");
        int typeSizeInByte = 2;
        type = new H5Datatype(Datatype.CLASS_INTEGER, typeSizeInByte,
                              Datatype.NATIVE, Datatype.SIGN_NONE);
      }
      else if (imgColorType == ImagePlus.GRAY32)
      {
        System.out.println("   bit depth: " + imgColorDepth
                           + ", type: GRAY32");
        int typeSizeInByte = 4;
        type = new H5Datatype(Datatype.CLASS_FLOAT, Datatype.NATIVE,
                              Datatype.NATIVE, -1);
      }
        
      // open the outfile
      H5File outFile = null;
      try
      {
        outFile = (H5File) fileFormat.createFile(filename,
                                                 FileFormat.FILE_CREATE_OPEN);
        if (!outFile.canWrite())
        {
          IJ.error("File `" + filename + "`is readonly!");
          return;
        }
        // open the file
        outFile.open();
        
        if(splitChannels)
        {
          // parse format string
          String[] formatTokens = 
              HDF5_GroupedVarnames.
              parseFormatString(formatString,
                                "[0-9]+"); // dummy regexp
          long[] channelDims = null;
          if(nLevs>1)
          {
            channelDims = new long[3];
            channelDims[0] = nLevs;
            channelDims[1] = nRows;
            channelDims[2] = nCols;
          }
          else
          {
            channelDims = new long[2];
            channelDims[0] = nRows;
            channelDims[1] = nCols;
          }
          // iterate over frames and channels
          ImageStack stack = imp.getStack();
          for(int f=0;f<nFrames;f++)
          {
            IJ.showProgress(f, nFrames);
            for(int c=0;c<nChannels;c++)
            {
              String fullName = makeDataSetName(formatTokens,
                                                f,c);
              String dataSetName = getDataSetDescriptor(fullName);
              System.out.println("dataset name: " + dataSetName);
              String groupName = getGroupDescriptor(fullName);
              System.out.println("group name: " + groupName);
              // ensure group exists
              Group group = createGroupRecursive(groupName, null, outFile);
              // create data set
              Dataset dataset = null;
              // select hyperslabs
              long[] maxdims = channelDims;
              long[] chunks = null;
              int gzip = 0; // no compression
              try
              {
                dataset = (Dataset) outFile.get(groupName + "/" + dataSetName);
              }
              catch( Exception e)
              {
                dataset = null;
              }
              if(dataset == null)
              {
                try
                {
                  dataset = outFile.createScalarDS(dataSetName, group, type,
                                                   channelDims, 
                                                   maxdims, chunks, gzip,
                                                   null);
                }
                catch (Exception err)
                {
                  IJ.error(err.getMessage());
                  return;
                }
              }
              dataset.init();
              long[] selected = dataset.getSelectedDims(); // the
              // selected
              // size of
              // the
              // dataet
              // write levels
              
              System.out.println("selected.length: "+
                                 Integer.toString(selected.length));
              System.out.println("channelDims.length: "+
                                 Integer.toString(channelDims.length));
              if(nLevs == 1)
              {
                for (int d = 0; d < selected.length; d++)
                {
                  selected[d] = channelDims[d];
                }
                int stackIndex = imp.getStackIndex(c+1,1,f+1);
                System.out.println("Stackindex: "+
                                   Integer.toString(stackIndex));
                // get raw data
                Object slice = stack.getPixels(stackIndex);
                assert(slice != null);
                // write data
                try
                {
                  dataset.write(slice);
                }
                catch(Exception e)
                {
                  IJ.showStatus("Error writing data to file.");
                }
              }
              else
              {
                selected[0] = 1;
                for (int d = 1; d < selected.length; d++)
                {
                  selected[d] = channelDims[d];
                }
                long[] start = dataset.getStartDims(); // the off set of
                // the selection
                for (int lvl = 0; lvl < nLevs; ++lvl)
                {
                  // select hyperslab
                  start[0] = lvl;
                  int stackIndex = imp.getStackIndex(c+1,lvl+1,f+1);
                  // get raw data
                  Object slice = stack.getPixels(stackIndex);
                  // write data
                  try
                  {
                    dataset.write(slice);
                  }
                  catch(Exception e)
                  {
                    IJ.showStatus("Error writing data to file.");
                  }
                }
              }
            }
          }
        }
        else
        {
          // write one big array
        }
        outFile.close();
      }
      catch (HDF5Exception err)
      {
        IJ.error(err.getMessage());
        return;
      }
      catch (Exception err)
      {
        IJ.error(err.getMessage());
        return;
      }
    }
    else
    {
      System.out.println("This is NO hyperstack");
      // String title = imp.getTitle();
      // int nDims = imp.getNDimensions();
      // int nFrames = imp.getNFrames();
      // int nChannels = imp.getNChannels();
      // int nLevs = imp.getNSlices();
      // int nRows = imp.getHeight();
      // int nCols = imp.getWidth();
      // boolean isComposite = imp.isComposite() ;
      // System.out.println("isComposite: "+Boolean.toString(isComposite));
      // System.out.println("Saving image \""+title+"\"");
      // System.out.println("nDims: "+Integer.toString(nDims));
      // System.out.println("nFrames: "+Integer.toString(nFrames));
      // System.out.println("nChannels: "+Integer.toString(nChannels));
      // System.out.println("nSlices: "+Integer.toString(nLevs));
      // System.out.println("nRows: "+Integer.toString(nRows));
      // System.out.println("nCols: "+Integer.toString(nCols));
        

      gd.addStringField(imp.getTitle(), "");
      gd.showDialog();
      if (gd.wasCanceled())
      {
        IJ.error("Plugin canceled!");
        return;
      }

      String varName = gd.getNextString();
      if(varName == "")
      {
        IJ.error("No data set name given. Plugin canceled!");
        return;
      }
      // write data set
      try
      {
        H5File outFile = null;
        try
        {
          outFile = (H5File) fileFormat.createFile(filename,
                                                   FileFormat.FILE_CREATE_OPEN);
          if (!outFile.canWrite())
          {
            IJ.error("File `" + filename + "`is readonly!");
            return;
          }
        }
        catch (HDF5Exception err)
        {
          IJ.error(err.getMessage());
          return;
        }

        outFile.open();
        // first create all dimensions and variables

        // Image color depth and color type
        int imgColorDepth;
        int imgColorType;
          
        System.out.println("writing data to variable: " + varName);

        String dataSetName = getDataSetDescriptor(varName);
        System.out.println("dataset name: " + dataSetName);
        String groupName = getGroupDescriptor(varName);
        System.out.println("group name: " + groupName);

        // ensure group exists
        Group group = createGroupRecursive(groupName, null, outFile);

        int nLevels = imp.getStackSize();
        int nRows = imp.getHeight();
        int nCols = imp.getWidth();

        // get image type (bit depth)
        imgColorDepth = imp.getBitDepth();
        imgColorType = imp.getType();
        long[] dims;
        if (imgColorType == ImagePlus.COLOR_RGB ||
            imgColorType == ImagePlus.COLOR_256)
        {
          if(nLevels == 1)
          {
            // color image
            dims = new long[3];
            dims[0] = nRows;
            dims[1] = nCols;
            dims[2] = 3;
          }
          else
          {
            // color images have 4 dimensions, grey value images
            // have 3.
            System.out.println("adding 4 dimensions");
            dims = new long[4];
            dims[0] = nLevels;
            dims[1] = nRows;
            dims[2] = nCols;
            dims[3] = 3;
          }
        }
        else
        {
          if(nLevels == 1)
          {
            // color image
            dims = new long[2];
            dims[0] = nRows;
            dims[1] = nCols;
          }
          else
          {
            System.out.println("adding 3 dimensions");
            dims = new long[3];
            dims[0] = nLevels;
            dims[1] = nRows;
            dims[2] = nCols;
          }
        }

        // The following is a list of a few example of H5Datatype.
        //
        // 1. to create unsigned native integer
        // H5Datatype type = new H5Dataype(CLASS_INTEGER, NATIVE,
        // NATIVE, SIGN_NONE);
        // 2. to create 16-bit signed integer with big endian
        // H5Datatype type = new H5Dataype(CLASS_INTEGER, 2,
        // ORDER_BE, NATIVE);
        // 3. to create native float
        // H5Datatype type = new H5Dataype(CLASS_FLOAT, NATIVE,
        // NATIVE, -1);
        // 4. to create 64-bit double
        // H5Datatype type = new H5Dataype(CLASS_FLOAT, 8, NATIVE,
        // -1);
        // H5Datatype type = new
        // H5Datatype(H5Datatype.CLASS_INTEGER,
        // H5Datatype.NATIVE, H5Datatype.NATIVE,
        // H5Datatype.SIGN_NONE);
        Datatype type = null;
        // supported data types
        // FIXME: set the right signed and precision stuff
        if (imgColorType == ImagePlus.GRAY8)
        {
          System.out.println("   bit depth: " + imgColorDepth
                             + ", type: GRAY8");
          type = new H5Datatype(Datatype.CLASS_CHAR, Datatype.NATIVE,
                                Datatype.NATIVE, Datatype.SIGN_NONE);
        }
        else if (imgColorType == ImagePlus.GRAY16)
        {
          System.out.println("   bit depth: " + imgColorDepth
                             + ", type: GRAY16");
          int typeSizeInByte = 2;
          type = new H5Datatype(Datatype.CLASS_INTEGER, typeSizeInByte,
                                Datatype.NATIVE, Datatype.SIGN_NONE);
        }
        else if (imgColorType == ImagePlus.GRAY32)
        {
          System.out.println("   bit depth: " + imgColorDepth
                             + ", type: GRAY32");
          int typeSizeInByte = 4;
          type = new H5Datatype(Datatype.CLASS_FLOAT, Datatype.NATIVE,
                                Datatype.NATIVE, -1);
        }
        else if (imgColorType == ImagePlus.COLOR_RGB)
        {
          System.out.println("   bit depth: " + imgColorDepth
                             + ", type: COLOR_RGB");
          type = new H5Datatype(Datatype.CLASS_CHAR, Datatype.NATIVE,
                                Datatype.NATIVE, Datatype.SIGN_NONE);
        }
        else if (imgColorType == ImagePlus.COLOR_256)
        {
          // FIXME: not supported yet
          System.out.println("   bit depth: " + imgColorDepth
                             + ", type: COLOR_256");
          System.out.println(" ERROR: untested, this might fail.");
          type = new H5Datatype(Datatype.CLASS_CHAR, Datatype.NATIVE,
                                Datatype.NATIVE, Datatype.SIGN_NONE);
        }

        // select hyperslabs
        long[] maxdims = dims;
        int nDims = dims.length;
//           long[] chunks = findOptimalChunksize( nDims,
//                                                 dims);
        long[] chunks = null;
        int gzip = 0; // no compression

        // create dataset
        Dataset dataset = null;

        try
        {
          dataset = (Dataset) outFile.get(groupName + "/" + dataSetName);
        }
        catch( Exception e)
        {
          dataset = null;
        }
        if(dataset == null)
        {

          dataset = outFile.createScalarDS(dataSetName, group, type,
                                           dims, maxdims, chunks, gzip,
                                           null);
        }
        dataset.init();
        long[] selected = dataset.getSelectedDims(); // the
        // selected
        // size of
        // the
        // dataet
        ImageStack stack = imp.getStack();
        if(nLevels == 1)
        {
          for (int d = 0; d < selected.length; d++)
          {
            selected[d] = dims[d];
          }
          // get raw data
          Object slice = stack.getPixels(nLevels);
          if(imgColorType == ImagePlus.COLOR_RGB)
              slice = computeRgbSlice(stack.getPixels(nLevels));
          // write data
          dataset.write(slice);
            
        }
        else
        {
          selected[0] = 1;
          for (int d = 1; d < selected.length; d++)
          {
            selected[d] = dims[d];
          }
          long[] start = dataset.getStartDims(); // the off set of
          // the selection
          for (int lvl = 0; lvl < nLevels; ++lvl)
          {
            IJ.showProgress(lvl, nLevels);
            // select hyperslab
            start[0] = lvl;

            // get raw data
            Object slice = stack.getPixels(lvl + 1);
            if(imgColorType == ImagePlus.COLOR_RGB)
                slice = computeRgbSlice(stack.getPixels(lvl + 1));
            // write data
            dataset.write(slice);
          }
        }
        // get pixel sizes
        ij.measure.Calibration cal = imp.getCalibration();
        System.out.println("   Element-Size in um (level,row,col): "
                           + cal.pixelDepth + ", " + cal.pixelHeight + ", "
                           + cal.pixelWidth);

        float[] element_sizes = new float[3];
        element_sizes[0] = (float) cal.pixelDepth;
        element_sizes[1] = (float) cal.pixelHeight;
        element_sizes[2] = (float) cal.pixelWidth;
        Datatype attrType = new H5Datatype(Datatype.CLASS_FLOAT,
                                           Datatype.NATIVE, Datatype.NATIVE,
                                           -1);
        long[] attrDims = { 3 };
        Attribute element_size_um = null;
        try
        {
          element_size_um = getAttribute(dataset, "element_size_um");
        }
        catch(Exception e)
        {
          element_size_um = null;
        }
        if(element_size_um == null)
        {
          element_size_um = new Attribute("element_size_um",
                                          attrType, attrDims);
        }
        element_size_um.setValue(element_sizes);
        // write element_size_um
        dataset.writeMetadata(element_size_um);
            
          

        outFile.close();
      }
      catch (HDF5Exception err)
      {
        System.err.println("Caught HDF5Exception");
        err.printStackTrace();
      }
      catch (java.io.IOException err)
      {
        System.err.println("IO Error while writing '" + filename + "': "
                           + err);
      }
      catch (Exception err)
      {
        System.err.println("Range Error while writing '" + filename
                           + "': " + err);
      }
    }
    

  }

  int byteToUnsignedByte(int n)
  {
    if (n < 0)
      return (256 + n);
    return n;
  }

  void showAbout()
  {
    IJ.showMessage("About HDF5 Writer:", "Written by Matthias Schlachter\n"
                                         + "University of Freiburg, 2010");
  }

  private static Group createGroupRecursive(String groupRelativName,
                                            Group group, FileFormat file)
  {
    if (groupRelativName == null || file == null)
      return null;

    if (group == null)
      group = (Group) ((DefaultMutableTreeNode) file.getRootNode())
          .getUserObject();

    while (groupRelativName.charAt(0) == '/')
    {
      // trim leading slash
      groupRelativName = groupRelativName.substring(1);
    }
    while (groupRelativName.charAt(groupRelativName.length() - 1) == '/')
    {
      // trim last slash
      groupRelativName = groupRelativName.substring(0, groupRelativName
          .length() - 2);
    }

    int posOfSlash = groupRelativName.indexOf('/');

    if (posOfSlash == -1)
    {
      try
      {
        Group newGroup;
        String newGroupName;
        if (group.isRoot())
          newGroupName = "/" + groupRelativName;
        else
          newGroupName = group.getFullName() + "/" + groupRelativName;
        newGroup = (Group) file.get(newGroupName);
        if (newGroup == null)
          newGroup = file.createGroup(newGroupName, group);
        return newGroup;
      }
      catch (Exception e)
      {
        return null;
      }
    }
    else
    {
      String subgroupRelativName = groupRelativName.substring(posOfSlash);
      String currentGroup = groupRelativName.substring(0, posOfSlash);
      System.out.println("Create: " + currentGroup);
      System.out.println("Call back for: " + subgroupRelativName);
      try
      {
        Group newGroup;
        String newGroupName;
        if (group.isRoot())
          newGroupName = "/" + currentGroup;
        else
          newGroupName = group.getFullName() + "/" + currentGroup;

        System.out.println("try opening: "+newGroupName);
        newGroup = (Group) file.get(newGroupName);
        
        if (newGroup == null)
          newGroup = file.createGroup(newGroupName, group);

        return createGroupRecursive(subgroupRelativName, newGroup, file);
      }
      catch (Exception e)
      {
        return null;
      }

    }
    // never come here
  }

  private static String getGroupDescriptor(String absName)
  {
    String groupName = absName;

    while (groupName.charAt(0) == '/')
    {
      // trim leading slash
      groupName = groupName.substring(1);
    }
    while (groupName.charAt(groupName.length() - 1) == '/')
    {
      // trim last slash
      groupName = groupName.substring(0, groupName.length() - 2);
    }
    int posOfLastSlash = groupName.lastIndexOf('/');
    if (posOfLastSlash == -1)
      return null;
    else
      return groupName.substring(0, posOfLastSlash);
  }

  private static String getDataSetDescriptor(String absName)
  {
    String dataSetName = absName;
    while (dataSetName.charAt(0) == '/')
    {
      // trim leading slash
      dataSetName = dataSetName.substring(1);
    }
    while (dataSetName.charAt(dataSetName.length() - 1) == '/')
    {
      // trim last slash
      dataSetName = dataSetName.substring(0, dataSetName.length() - 2);
    }
    int posOfLastSlash = dataSetName.lastIndexOf('/');
    if (posOfLastSlash == -1)
      return dataSetName;
    else
      return dataSetName.substring(posOfLastSlash + 1);
  }

  
  long[] findOptimalChunksize( int Rank,
                               long[] dataDims)
  {
    long[] best_chunksize = new long[Rank];
    int maxChunkVol = 262144;
    // small sanity check first:
    int data_volume = 1;
    for( int d = 0; d < Rank; ++d)
        data_volume *= dataDims[d];
    if( data_volume < maxChunkVol) {
      for( int d = 0; d < Rank; ++d)
          best_chunksize[d] = dataDims[d];
      return best_chunksize;
    }
    else return null;
  }


  private static List<Attribute> getAttrList(Dataset ds) throws Exception
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
    List<Attribute> attrList = getAttrList(ds);
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
  
  private Object computeRgbSlice(Object pixels)
  {
    byte rgbslice[];
    int size = ((int[])pixels).length;
    rgbslice = new byte[size*3];
    for(int i=0;i<size;i++)
    {  
      int red= (((int[]) pixels)[i] & 0xff0000) >> 16;
      int green= (((int[]) pixels)[i] & 0x00ff00) >> 8;
      int blue= ((int[]) pixels)[i] & 0x0000ff; 
      rgbslice[3*i + 0] = (byte) red;
      rgbslice[3*i + 1] = (byte) green;
      rgbslice[3*i + 2] =(byte)  blue;
    }
    return rgbslice;
  }

  private String makeDataSetName(String[] toks,int frame,int channel)
  {
    String dName = 
        toks[0] + 
        Integer.toString(frame) + 
        toks[1] +
        Integer.toString(channel);
    if(toks.length>2)
        dName = dName + toks[2];
    return dName;
  }
    
// end of class
}
