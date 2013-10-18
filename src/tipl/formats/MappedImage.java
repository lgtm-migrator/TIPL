/**
 * 
 */
package tipl.formats;

import java.util.HashMap;

import tipl.util.ArgumentParser;
import tipl.util.TImgTools;

/**
 * MappedImage is a subclass of FImage, but without spatial dependence. It can
 * therefore be run, much faster on some datasets
 * 
 * @author mader
 * 
 */

public class MappedImage extends FuncImage {
	/**
	 * FloatThreshold is a simple application of FImage where the function is a
	 * boolean function indicating if the value in the original image falls in
	 * the given range
	 */
	public static class FloatThreshold extends MappedImage {
		protected TImgRO templateData;
		protected int imageType;
		final float mnVal, mxVal;

		/**
		 * FloatThreshold simply returns data from the template file whenever
		 * any resource except slice data is requested
		 */
		public FloatThreshold(final TImgRO dummyDataset, final int iimageType,
				final float threshValA, final float threshValB) {

			super(dummyDataset, iimageType, new StationaryVoxelFunction() {
				final float mnVal = Math.min(threshValA, threshValB);
				final float mxVal = Math.max(threshValA, threshValB);

				@Override
				public double get(final double voxval) {
					if ((voxval >= mnVal) && (voxval <= mxVal))
						return 127;
					return 0;
				}

				@Override
				public double[] getRange() {
					return new double[] { 0, 127 };
				}

				@Override
				public String name() {
					return "Threshold=[" + mnVal + "," + mxVal + "]";
				}

				@Override
				public String toString() {
					return name();
				}
			}, true);
			mnVal = Math.min(threshValA, threshValB);
			mxVal = Math.max(threshValA, threshValB);
		}

		@Override
		public String toString() {
			return "Float-Threshold:<" + mnVal + ", " + mxVal + ">";
		}
	}

	/** Simple threshold is simply an image where */
	public static class IntThreshold extends MappedImage {
		protected TImgRO templateData;
		protected int imageType;
		final int tval;

		/**
		 * Zimage simply returns data from the template file whenever any
		 * resource except slice data is requested
		 */
		public IntThreshold(final TImgRO dummyDataset, final int iimageType,
				final int threshVal) {

			super(dummyDataset, iimageType, new StationaryVoxelFunction() {
				@Override
				public double get(final double voxval) {
					if (((int) voxval) == threshVal)
						return 127;
					return 0;
				}

				@Override
				public double[] getRange() {
					return new double[] { 0, 127 };
				}

				@Override
				public String name() {
					return "Threshold=" + threshVal;
				}

				@Override
				public String toString() {
					return name();
				}
			});
			tval = threshVal;
		}

		@Override
		public String toString() {
			return "Int-Threshold:==" + tval;
		}
	}

	/** Simple mapping of input values to output values */
	public static class SimpleMapImage extends MappedImage {
		protected TImgRO templateData;
		protected int imageType;

		public SimpleMapImage(final TImgRO dummyDataset, final int iimageType,
				final String arrayBlob, final boolean passThrough,
				final double defValue) {
			super(dummyDataset, iimageType, StringToSVF(arrayBlob, passThrough,
					defValue));
		}

		@Override
		public String toString() {
			return "SimpleMapImage";
		}
	}

	public static interface StationaryVoxelFunction {
		/** gray value to return for a voxel at position ipos[] with value v **/
		public double get(double v);

		/** function returning the estimated range of the image **/
		public double[] getRange();

		/** name of the function being applied **/
		public String name();
	}

	public final static String kVer = "130822_001";

	/**
	 * Generic voxel function backed by an array
	 * 
	 * @param name
	 *            the name to return for the function
	 * @param minValue
	 *            the minimum value given as an input (inside the range)
	 * @param valueArray
	 *            the array indexed from 0 to maxValue-minValue (with an offset
	 *            of minValue)
	 * @param passThrough
	 *            should the value be passed through if it is outside the range
	 * @param outsideValue
	 *            the value that should be returned if it is not passed through
	 * @return a function that can be used to create an image
	 */
	protected static StationaryVoxelFunction arrayBackedVoxelFunction(
			final String name, final int minValue, final double[] valueArray,
			final boolean passThrough, final double outsideValue) {
		double tminOVal = valueArray[0];
		double tmaxOVal = valueArray[0];
		for (final double cval : valueArray) {
			if (cval < tminOVal)
				tminOVal = cval;
			if (cval > tmaxOVal)
				tmaxOVal = cval;
		}
		final double minOVal = tminOVal;
		final double maxOVal = tmaxOVal;
		return new StationaryVoxelFunction() {

			@Override
			public double get(final double v) {
				final int index = (int) (v - minValue);
				if ((index < valueArray.length) & (index >= 0))
					return valueArray[index];
				else {
					if (passThrough)
						return v;
					else
						return outsideValue;
				}
			}

			@Override
			public double[] getRange() {
				return new double[] { minOVal, maxOVal };
			}

			@Override
			public String name() {
				return name;
			}

		};
	}

	protected static StationaryVoxelFunction cacheVoxelFunction(
			final StationaryVoxelFunction isvf, final int imageType,
			final double outsideValue) {
		final int minValue, maxValue;
		switch (imageType) {
		case 0: // char
			minValue = Byte.MIN_VALUE;
			maxValue = Byte.MAX_VALUE;
			break;
		case 1: // short
			minValue = Short.MIN_VALUE;
			maxValue = Short.MAX_VALUE;
			break;
		case 2:
			// this may not really work
			minValue = Integer.MIN_VALUE;
			maxValue = Integer.MAX_VALUE;
			break;
		case 10:
			minValue = 0;
			maxValue = 1;
			break;
		default:
			throw new IllegalArgumentException("Type:" + imageType
					+ " is not supported for StationaryVoxel caching");

		}
		return cacheVoxelFunction(isvf, minValue, maxValue, outsideValue);
	}

	protected static StationaryVoxelFunction cacheVoxelFunction(
			final StationaryVoxelFunction isvf, final int minValue,
			final int maxValue, final double outsideValue) {
		final double[] cachedValues = new double[maxValue - minValue + 1];
		System.out.println("Precaching values for: " + isvf.name() + " from: "
				+ minValue + " to " + maxValue);
		for (int i = minValue; i <= maxValue; i++)
			cachedValues[i - minValue] = isvf.get(i);
		return arrayBackedVoxelFunction(isvf.name(), minValue, cachedValues,
				false, outsideValue);
	}

	protected static void checkHelp(final ArgumentParser p) {
		if (p.hasOption("?")) {
			System.out.println(" MappedImage");
			System.out.println(" Remaps images using command line arguments");
			System.out.println(" Arguments::");
			System.out.println(" ");
			System.out.println(p.getHelp());
			System.exit(0);
		}
		p.checkForInvalid();
	}

	public static void main(final String[] args) {

		System.out.println("MappedImage v" + kVer);
		System.out.println("Maps an Image");
		System.out.println(" By Kevin Mader (kevin.mader@gmail.com)");
		final ArgumentParser p = new ArgumentParser(args);
		final String inputFile = p.getOptionString("convert", "",
				"Aim File to Convert");
		final String outputFile = p.getOptionString("output", "",
				"Output Aim File (.raw, .tif, directory/, etc)");
		final String mapping = p.getOptionString("mapping", "",
				"Mapping to apply (in1:out1,in2:out2)");
		final boolean passthrough = p.getOptionBoolean("passthrough",
				"Allow other values to pass through");
		final double defValue = p.getOptionDouble("defaultval", 0.0,
				"Default value for values not in the map");

		if (inputFile.length() > 0) {
			System.out.println("Loading " + inputFile + " ...");
			final VirtualAim inputAim = new VirtualAim(inputFile);
			System.out.println("Dim:" + inputAim.getDim());
			inputAim.setPos(p.getOptionD3int("cpos", inputAim.getPos(),
					"Change Starting Position"));
			inputAim.setOffset(p.getOptionD3int("coffset",
					inputAim.getOffset(), "Change Offset"));
			inputAim.setElSize(p.getOptionD3float("celsize",
					inputAim.getElSize(), "Change Element Size"));

			checkHelp(p);

			if (outputFile.length() > 0) { // Write output File
				inputAim.getIntAim();
				final TImgRO mappedAim = new SimpleMapImage(inputAim, 2, mapping,
						passthrough, defValue);
				System.out.println("Writing " + outputFile + " ...");
				TImgTools.WriteTImg(mappedAim, outputFile);
			}
		} else {
			checkHelp(p);
		}

	}

	public static StationaryVoxelFunction StringToSVF(final String invalues,
			final boolean passThrough, final double defValue) {
		final String[] subValues = invalues.split(",");
		final HashMap<Integer, Float> cMap = new HashMap<Integer, Float>();
		boolean isEmpty = true;
		int minVal = 0;
		int maxVal = 0;
		for (final String curPair : subValues) {
			final String[] sPair = curPair.split(":");
			final int inVal = Integer.parseInt(sPair[0]);
			final float outVal = Float.parseFloat(sPair[1]);
			cMap.put(new Integer(inVal), new Float(outVal));
			if (isEmpty) {
				minVal = inVal;
				maxVal = inVal;
				isEmpty = false;
			}
			if (inVal < minVal)
				minVal = inVal;
			if (inVal > maxVal)
				maxVal = inVal;
		}
		final double[] retArr = new double[maxVal - minVal + 1];
		System.out.println("Input Map (" + minVal + " to " + maxVal + "):"
				+ invalues);
		for (int i = 0; i <= (maxVal - minVal); i++) {
			final Integer cVal = new Integer(i + minVal);
			if (cMap.containsKey(cVal)) {
				retArr[i] = cMap.get(cVal).doubleValue();
			} else {
				if (passThrough)
					retArr[i] = cVal.doubleValue();
				else
					retArr[i] = defValue;
			}
			System.out.println(cVal + "->" + retArr[i]);
		}

		return arrayBackedVoxelFunction("SimpleMap:" + invalues, minVal,
				retArr, passThrough, defValue);
	}

	protected StationaryVoxelFunction svf;

	/**
	 * @param useFloatInput
	 */
	public MappedImage(final boolean useFloatInput) {
		super(useFloatInput);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param dummyDataset
	 * @param iimageType
	 * @param ivf
	 */
	public MappedImage(final TImgRO dummyDataset, final int iimageType,
			final StationaryVoxelFunction isvf) {
		super(dummyDataset, iimageType);
		svf = isvf;
	}

	/**
	 * @param dummyDataset
	 * @param iimageType
	 * @param ivf
	 * @param useFloatInput
	 */
	public MappedImage(final TImgRO dummyDataset, final int iimageType,
			final StationaryVoxelFunction isvf, final boolean useFloatInput) {
		super(dummyDataset, iimageType, useFloatInput);
		svf = isvf;
	}

	/**
	 * Caches the VoxelFunction and creates a new image based on this cache
	 * (much ~10x faster for large images with simple maps)
	 * 
	 * @param cacheType
	 *            type of data being input into the image (matches imageType
	 *            standard)
	 * @param defValue
	 *            default value (outside of range)
	 * @return MappedImage backed by an array instead of a function
	 */
	public MappedImage cache(final int cacheType, final double defValue) {
		return new MappedImage(templateData, getImageType(),
				cacheVoxelFunction(svf, cacheType, defValue), useFloat);
	}

	/**
	 * Caches the VoxelFunction and creates a new image based on this cache
	 * (much ~10x faster for large images with simple maps)
	 * 
	 * @param minValue
	 *            minimum value to map
	 * @param maxValue
	 *            maximum value to map
	 * @param defValue
	 *            default value (outside of range)
	 * @return MappedImage backed by an array instead of a function
	 */
	public MappedImage cache(final int minValue, final int maxValue,
			final double defValue) {
		return new MappedImage(templateData, getImageType(),
				cacheVoxelFunction(svf, minValue, maxValue, defValue), useFloat);
	}

	@Override
	public String getPath() {
		return svf.name() + " @ " + templateData.getPath();
	}

	@Override
	public String getProcLog() {
		return templateData.getProcLog() + "\n" + svf.name() + "\n";
	}

	@Override
	public double[] getRange() {
		return svf.getRange();
	}

	@Override
	public String getSampleName() {
		return svf.name() + " @ " + templateData.getSampleName();
	}

	@Override
	public double getVFvalue(final int cIndex, final int sliceNumber,
			final double v) {
		return svf.get(v);
	}

}
