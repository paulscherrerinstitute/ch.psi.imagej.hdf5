package ch.psi.imagej.hdf5;

import java.util.logging.Logger;
import java.util.regex.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class HDF5GroupedVarnames {
	
	private static final Logger logger = Logger.getLogger(HDF5GroupedVarnames.class.getName());
	
	private final List<String> matchedVarNames = new ArrayList<String>();
	private final List<String> unMatchedVarNames = new ArrayList<String>();
	private final List<TimeFrame> frameList = new ArrayList<TimeFrame>();
	private String[] formatTokens = null;
	private String formatString = null;
	private int minFrameIndex = -1;
	private int maxFrameIndex = -1;
	private int minChannelIndex = -1;
	private int maxChannelIndex = -1;
	private int nChannels = -1;
	
	public static String[] parseFormatString(String groupVarsByNameFormat, String dollarRegexpForGrouping) throws PatternSyntaxException {
		String[] formatTokens = null;
		formatTokens = groupVarsByNameFormat.split("([$]T|[$]C)");
		boolean containsFormatVars = groupVarsByNameFormat.contains("$T") && groupVarsByNameFormat.contains("$C");
		boolean rightOrderOfFormatVars = groupVarsByNameFormat.indexOf("$T") < groupVarsByNameFormat.indexOf("$C");

		for (int i = 0; i < formatTokens.length; i++) {
			logger.info("tok " + Integer.toString(i) + " : " + formatTokens[i]);
		}
		if (formatTokens.length < 2 || !containsFormatVars || !rightOrderOfFormatVars) {
			throw new PatternSyntaxException("Your format string has errors. " + "You must provide $T and $C and " + "also in correct order!", groupVarsByNameFormat, -1);
		}
		String regexp = groupVarsByNameFormat;
		regexp = regexp.replace("$T", dollarRegexpForGrouping);
		regexp = regexp.replace("$C", dollarRegexpForGrouping);
		logger.info(regexp);
		// check if we have a regexp;
		Pattern.compile(regexp);
		return formatTokens;
	}

	public void parseVarNames(String[] varNames, String groupVarsByNameFormat, String dollarRegexpForGrouping) {
		// save format string
		formatString = groupVarsByNameFormat;
		try {
			formatTokens = parseFormatString(groupVarsByNameFormat, dollarRegexpForGrouping);
		} catch (PatternSyntaxException e) {
			// produce an error dialog an start over
			String errMsg = e.getMessage();
			logger.info(errMsg);
			return;
		}
		String regexp = groupVarsByNameFormat;
		regexp = regexp.replace("$T", dollarRegexpForGrouping);
		regexp = regexp.replace("$C", dollarRegexpForGrouping);

		logger.info(regexp);
		// check if we have a regexp;
		Pattern p = null;
		p = Pattern.compile(regexp);
		/*---------------------------------------------------------------------
		 *  parse var names
		 *---------------------------------------------------------------------*/
		for (int i = 0; i < varNames.length; i++) {
			Matcher m = p.matcher(varNames[i]);
			boolean b = m.matches();
			if (b) {
				logger.info(varNames[i]);
				matchedVarNames.add(varNames[i]);
			} else {
				unMatchedVarNames.add(varNames[i]);
			}
		}
		splitGroupedVarnames();
		// ugly hack for sorting ArrayList
		Object[] frameListAsArray = frameList.toArray();
		Arrays.sort(frameListAsArray);
		for (int i = 0; i < frameListAsArray.length; i++)
			frameList.set(i, (TimeFrame) frameListAsArray[i]);
	}

	public TimeFrame getFrame(int i) {
		if (i < frameList.size() && i > -1)
			return frameList.get(i);
		else
			return null;
	}

	private void splitGroupedVarnames() {
		Iterator<String> vars = matchedVarNames.iterator();
		while (vars.hasNext()) {
			String varName = vars.next();
			String[] tokens = null;
			if (formatTokens.length == 2) {
				tokens = varName.split(formatTokens[1]);
			} else if (formatTokens.length == 3) {
				tokens = varName.split(formatTokens[2]);
				varName = tokens[0];
				tokens = varName.split(formatTokens[1]);
			}

			if (tokens.length < 2 || tokens.length > 3) {
				logger.info("Error parsing varname!");
			} else {
				Integer channelIndex = new Integer(tokens[1]);
				logger.info("channelIndex: " + channelIndex.toString());
				logger.info("left token: " + tokens[0]);
				tokens = tokens[0].split("/t");
				Integer frameIndex = new Integer(tokens[1]);
				logger.info("frameIndex: " + frameIndex.toString());

				if (minFrameIndex == -1)
					minFrameIndex = frameIndex.intValue();
				minFrameIndex = Math.min(minFrameIndex, frameIndex.intValue());

				if (maxFrameIndex == -1)
					maxFrameIndex = frameIndex.intValue();
				maxFrameIndex = Math.max(maxFrameIndex, frameIndex.intValue());

				if (minChannelIndex == -1)
					minChannelIndex = channelIndex.intValue();
				minChannelIndex = Math.min(minChannelIndex, channelIndex.intValue());

				if (maxChannelIndex == -1)
					maxChannelIndex = channelIndex.intValue();
				maxChannelIndex = Math.max(maxChannelIndex, channelIndex.intValue());

				TimeFrame frame = new TimeFrame(frameIndex.intValue());
				int idx = frameList.indexOf(frame);
				if (idx != -1) {
					frame = (TimeFrame) frameList.get(idx);
					frame.addChannel(channelIndex.intValue());
				} else {
					frame.addChannel(channelIndex.intValue());
					frameList.add(frame);
				}
				// logger.info(frame.toString());
			}
		}
	}

	public int getMinFrameIndex() {
		return minFrameIndex;
	}

	public int getMaxFrameIndex() {
		return maxFrameIndex;
	}

	public int getMinChannelIndex() {
		return minChannelIndex;
	}

	public int getMaxChannelIndex() {
		return maxChannelIndex;
	}

	public int getNFrames() {
		return frameList.size();
	}

	public int getNChannels() {
		// TODO: check all frames for min/max of channels not index
		if (nChannels == -1)
			return maxChannelIndex - minChannelIndex + 1;
		else
			return nChannels;
	}

	public boolean hasAllFramesInRange() {
		return frameList.size() == (maxFrameIndex - minFrameIndex + 1);
	}

	public String toString() {
		String s = "Data set statistics\n";
		s = s + "----------------------------------\n";
		s = s + "nFrames: " + Integer.toString(frameList.size()) + "\n";
		s = s + "minFrameIndex: " + Integer.toString(minFrameIndex) + "\n";
		s = s + "maxFrameIndex: " + Integer.toString(maxFrameIndex) + "\n";
		s = s + "hasAllFramesInRange: " + Boolean.toString(hasAllFramesInRange()) + "\n";
		s = s + "minChannelIndex: " + Integer.toString(minChannelIndex) + "\n";
		s = s + "maxChannelIndex: " + Integer.toString(maxChannelIndex) + "\n";

		// String[] toks = getFormatTokens();
		Iterator<TimeFrame> frames = frameList.iterator();
		while (frames.hasNext()) {
			TimeFrame f = frames.next();
			s = s + f.toString() + "\n";
			// s = s + "(" + toks[0] +
			// Integer.toString(f.getFrameIndex())
			// + toks[1] + "$C";
			// if(toks.length>2)
			// s = s + toks[2] + "\n";
			// else
			// s = s + "\n";
		}
		s = s + "----------------------------------";
		return s;
	}

	public List<String> getUnmatchedVarNames() {
		return unMatchedVarNames;
	}

	public String[] getFormatTokens() {
		return formatTokens;
	}

	public String getFormatString() {
		return formatString;
	}

	public void setFrameAndChannelRange(int minFrame, int skipFrame, int maxFrame, int minChannel, int skipChannel, int maxChannel) {
		logger.info("Setting frame range: " + Integer.toString(minFrame) + ":" + Integer.toString(skipFrame) + ":" + Integer.toString(maxFrame));
		logger.info("Setting channel range: " + Integer.toString(minChannel) + ":" + Integer.toString(skipChannel) + ":" + Integer.toString(maxChannel));
		if (hasAllFramesInRange()) {
			// copy frames
			List<TimeFrame> completeFrameList = new ArrayList<TimeFrame>(frameList);
			// clear frames
			frameList.clear();
			// insert wanted frames and channels
			for (int f = minFrame; f < maxFrame + 1; f += skipFrame) {
				TimeFrame frameAllChannels = completeFrameList.get(f);
				TimeFrame frame = new TimeFrame(frameAllChannels.getFrameIndex());
				// TODO remove unwanted channels
				for (int c = minChannel; c < maxChannel + 1; c += skipChannel) {
					// logger.info("Adding channels: " +
					// Integer.toString(c));
					frame.addChannel(c);
				}
				// if(nChannels == -1)
				// nChannels = frame.getNChannels();
				frameList.add(frame);
			}
			// TODO update min/max of frames/channels
			nChannels = ((maxChannel - minChannel) / skipChannel) + 1;
			logger.info("Adding nChannels: " + Integer.toString(nChannels));
		} else {
			logger.info("-------------------------\n" + "hasAllFramesInRange==false\n" + "-------------------------");
			// copy frames
			List<TimeFrame> completeFrameList = new ArrayList<TimeFrame>(frameList);
			// clear frames
			frameList.clear();
			// insert wanted frames and channels
			for (int f = minFrame; f < maxFrame + 1; f += skipFrame) {
				TimeFrame frame = new TimeFrame(f);
				int idx = completeFrameList.indexOf(frame);
				// logger.info("index of frame in list: " +
				// Integer.toString(idx));
				if (idx != -1) {
					// TODO remove unwanted channels
					for (int c = minChannel; c < maxChannel + 1; c += skipChannel) {
						// logger.info("Adding channels: " +
						// Integer.toString(c));
						frame.addChannel(c);
					}
					// if(nChannels == -1)
					// nChannels = frame.getNChannels();
					frameList.add(frame);
				} else {
					logger.info("Timestep " + Integer.toString(f) + " is missing!");
				}
			}
			// TODO update min/max of frames/channels
			nChannels = ((maxChannel - minChannel) / skipChannel) + 1;
			logger.info("Adding nChannels: " + Integer.toString(nChannels));
		}
	}
}
