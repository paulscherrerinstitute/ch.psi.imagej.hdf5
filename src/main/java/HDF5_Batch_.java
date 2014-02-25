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

public class HDF5_Batch_ 
{
  public static void run(String arg)
  {
    parseArgs(arg);
    System.out.println("filename");
    System.out.println(_filename);
    System.out.println("varnames");
    for(int i=0; i<_varnames.length; i++)
        System.out.println(_varnames[i]);
    HDF5_Writer_ w = new HDF5_Writer_();
    w.setToBatchMode(_filename,_varnames);
    w.run(null);
  }


  private static void parseArgs(String arg)
  {       
    String[] result = arg.split("]\\s");
    _filename = result[0].replaceAll("file=\\[","");
    String[] splitVars = result[1].split("\\s");
    _varnames = new String[splitVars.length];
    for (int x=0; x<splitVars.length; x++)
        _varnames[x] = splitVars[x];
     
  }

  static private String _filename = null;
  static private String[] _varnames = null;

// end of class
}
