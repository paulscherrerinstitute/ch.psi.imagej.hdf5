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

import java.util.ArrayList;
import java.util.Arrays;
//import java.util.Iterator;
//import java.util.List;


public class TimeFrame implements Comparable
{
  public TimeFrame(int index)
        {
          frameIndex = index;
        }

  public TimeFrame(String index)
        {
          frameIndex = Integer.parseInt(index);
        }

  public void addChannel(int index)
        {
          Integer channelIndex = new Integer(index);
          if(!channels.contains(channelIndex))
              channels.add(new Integer(index));
          else
              System.out.println("channel" + channelIndex.toString() 
                                 + " already in list!");
        }

  public void addChannel(String index)
        {
          addChannel(Integer.parseInt(index));
        }

  public boolean equals(Object obj)
        {
          TimeFrame f = (TimeFrame) obj;
          if(f.frameIndex == frameIndex)
              return true;
          return false;
        }

  public String toString()
        {
          String s = "FrameIdx: " + Integer.toString(frameIndex) + "; ";
          s = s + "nChannels: " + Integer.toString(channels.size()) + "; ";
          s = s + "channels: ";
          for(int i=0;i<channels.size();i++)
              s = s + Integer.toString(channels.get(i)) + ";";
      
          return s;
        }

  public int getNChannels()
        {
          return channels.size();
        }

  public int getFrameIndex()
        {
          return frameIndex;
        }
    
  public int[] getChannelIndices()
        {            
          Object[] channelsAsArray = channels.toArray();
          Arrays.sort(channelsAsArray);
          int[] channelsIdx = new int[channelsAsArray.length];
          for(int i=0;i<channelsAsArray.length;i++)
              channelsIdx[i] = ((Integer) channelsAsArray[i]).intValue();
          return channelsIdx;
        }
    
  public int compareTo(Object obj)
        {
          TimeFrame f = (TimeFrame) obj;
          if(frameIndex<f.frameIndex)
              return -1;
          else if(frameIndex>f.frameIndex)
              return 1;
          else 
              return 0;
        }
    
  private final int frameIndex;
  private final ArrayList<Integer> channels = new ArrayList<Integer>();
}