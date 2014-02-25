package ch.psi.imagej.hdf5;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TimeFrame implements Comparable<TimeFrame> {
	
	private final int frameIndex;
	private final List<Integer> channels = new ArrayList<Integer>();
	
	public TimeFrame(int index) {
		frameIndex = index;
	}

	public TimeFrame(String index) {
		frameIndex = Integer.parseInt(index);
	}

	public void addChannel(Integer index) {
		if (!channels.contains(index)){
			channels.add(new Integer(index));
		}
	}

	public void addChannel(String index) {
		addChannel(Integer.parseInt(index));
	}

	public boolean equals(Object o) {
		return (((TimeFrame)o).frameIndex == frameIndex);
	}

	public String toString() {
		String s = "FrameIdx: " + Integer.toString(frameIndex) + "; ";
		s = s + "nChannels: " + Integer.toString(channels.size()) + "; ";
		s = s + "channels: ";
		for (int i = 0; i < channels.size(); i++){
			s = s + Integer.toString(channels.get(i)) + ";";
		}

		return s;
	}

	public int getNChannels() {
		return channels.size();
	}

	public int getFrameIndex() {
		return frameIndex;
	}

	public int[] getChannelIndices() {
		Object[] channelsAsArray = channels.toArray();
		Arrays.sort(channelsAsArray);
		int[] channelsIdx = new int[channelsAsArray.length];
		for (int i = 0; i < channelsAsArray.length; i++){
			channelsIdx[i] = ((Integer) channelsAsArray[i]).intValue();
		}
		return channelsIdx;
	}

	public int compareTo(TimeFrame f) {
		if (frameIndex < f.frameIndex){
			return -1;
		}
		else if (frameIndex > f.frameIndex){
			return 1;
		}
		else{
			return 0;
		}
	}
}