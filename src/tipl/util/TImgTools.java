/**
 * 
 */
package tipl.util;

import ij.ImageStack;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;

import tipl.formats.ConcurrentReader;
import tipl.formats.TImg;
import tipl.formats.TImgRO;
import tipl.formats.TImgRO.FullReadable;
import tipl.formats.VirtualAim;

/**
 * Library of static functions used for TImg (since TImg is just an interface)
 * 
 * @author maderk
 * 
 * <pre> v3 28May13 Added generic slice converting function
 * 
 * <pre> v2 04Feb13 Added elSize to the mirrorImage function
 */
public class TImgTools {
	/**
	 * put just the relevant dimension reading code in a seperate interface
	 * 
	 * @author mader
	 * 
	 */
	public static interface ChangesDimensions {
		/** add a line to the procedure log **/
		public String appendProcLog(String inData);

		/** The size of the image */
		public void setDim(D3int inData);

		/** The element size (in mm) of a voxel */
		public void setElSize(D3float inData);

		/**
		 * The size of the border around the image which does not contain valid
		 * voxel data
		 */
		public void setOffset(D3int inData);

		/**
		 * The position of the bottom leftmost voxel in the image in real space,
		 * only needed for ROIs
		 */
		public void setPos(D3int inData);
	}

	/**
	 * put just the relevant dimension reading code in a seperate interface
	 * 
	 * @author mader
	 * 
	 */
	public static interface HasDimensions {
		/** The size of the image */
		public D3int getDim();

		/** The element size (in mm) of a voxel */
		public D3float getElSize();

		/**
		 * The size of the border around the image which does not contain valid
		 * voxel data
		 */
		public D3int getOffset();

		/**
		 * The position of the bottom leftmost voxel in the image in real space,
		 * only needed for ROIs
		 */
		public D3int getPos();

		/**
		 * Procedure Log, string containing past operations and information on
		 * the aim-file
		 */
		public String getProcLog();

		public int getSlices();
	}

	//
	public static final int FAST_TIFF_BASED = 0;
	public static final int FAST_MEMORY_MAP_BASED = 1;
	public static final int FAST_MEMORY_COMPUTATION_BASED = 2;
	public static final int FAST_MEMORY_BASED = 3;
	/** minimum isfast level to count as being cached */
	public static int FAST_CACHED = FAST_MEMORY_MAP_BASED;
	/**
	 * A global image cache so images can be referenced until they are unloaded
	 * by just their name
	 */
	protected static LinkedHashMap<String, TImg> cachedImages = new LinkedHashMap<String, TImg>();

	public static String appendProcLog(String curLog, String appText) {
		return curLog + "\n" + new Date() + "\t" + appText;
	}

	/**
	 * check to see if the image is faster than loading a tiff, if it is not
	 * fast and there is enough memory (not yet implemented), than cache it
	 */
	@Deprecated
	public static TImgRO CacheImage(TImgRO inImage) {
		if (inImage.isFast() > FAST_TIFF_BASED)
			return inImage;
		else
			return ConcurrentReader.CacheImage(inImage, inImage.getImageType());
	}

	/**
	 * The general function for comparing the dimensions of two TImg class
	 * images
	 **/
	public static boolean CheckSizes2(TImgRO inVA, TImgRO otherVA) {

		boolean isMatch = true;
		isMatch = isMatch & (inVA.getDim().x == otherVA.getDim().x);
		isMatch = isMatch & (inVA.getDim().y == otherVA.getDim().y);
		isMatch = isMatch & (inVA.getDim().z == otherVA.getDim().z);
		isMatch = isMatch & (inVA.getPos().x == otherVA.getPos().x);
		isMatch = isMatch & (inVA.getPos().y == otherVA.getPos().y);
		isMatch = isMatch & (inVA.getPos().z == otherVA.getPos().z);
		isMatch = isMatch & (inVA.getOffset().x == otherVA.getOffset().x);
		isMatch = isMatch & (inVA.getOffset().y == otherVA.getOffset().y);
		isMatch = isMatch & (inVA.getOffset().z == otherVA.getOffset().z);
		return isMatch;
	}

	/**
	 * Generic function for converting array types
	 * 
	 * @param inArray
	 *            the input array as an object
	 * @param inType
	 *            the type for the input
	 * @param outType
	 *            the desired type for the output
	 * @param isSigned
	 *            whether or not the value is signed
	 * @param shortScaleFactor
	 *            the factor to scale shorts/integers/chars by when converting
	 *            to a float and vice versa
	 * @param maxVal
	 * @return slice as an object (must be casted)
	 * @throws IOException
	 */
	public static Object convertArrayType(final Object inArray,
			final int inType, final int outType, final boolean isSigned,
			final float shortScaleFactor, final int maxVal) {
		assert isValidType(inType);
		assert isValidType(outType);
		switch (inType) {
		case 0: // byte
			return convertCharArray((char[]) inArray, outType, isSigned,
					shortScaleFactor, maxVal);
		case 1: // short
			return convertShortArray((short[]) inArray, outType, isSigned,
					shortScaleFactor, maxVal);
		case 2: // int
			return convertIntArray((int[]) inArray, outType, isSigned,
					shortScaleFactor);
		case 3: // float
			return convertFloatArray((float[]) inArray, outType, isSigned,
					shortScaleFactor);
		case 10: // boolean
			return convertBooleanArray((boolean[]) inArray, outType);
		}
		return inArray;
	}

	@Deprecated
	private static Object convertBooleanArray(final boolean[] gf,
			final int asType) {
		assert (asType >= 0 && asType <= 3) || asType == 10;
		final int sliceSize = gf.length;
		switch (asType) {
		case 0: // Char
			final char[] gb = new char[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				if (gf[i])
					gb[i] = 127;
			return gb;
		case 1: // Short
			// Read short data type in
			final short[] gs = new short[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				if (gf[i])
					gs[i] = 127;
			return gs;
		case 2: // Spec / Int
			// Read integer data type in
			final int[] gi = new int[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				if (gf[i])
					gi[i] = 127;
			return gi;
		case 3: // Float - Long
			final float[] gout = new float[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				if (gf[i])
					gout[i] = 1.0f;
			return gout;
		case 10: // Mask
			return gf;
		default:
			throw new IllegalArgumentException("Unknown data type!!!" + asType
					+ ", " + gf);
		}

	}

	@Deprecated
	private static Object convertCharArray(char[] gs, final int asType,
			final boolean isSigned, final float shortScaleFactor, int maxVal) {
		final int sliceSize = gs.length;
		switch (asType) {
		case 0: // Char
			return gs;
		case 1: // Short
			// Read short data type in
			final short[] gshort = new short[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gshort[i] = (short) gs[i];
			return gshort;
		case 2: // Spec / Int
			// Read integer data type in
			final int[] gi = new int[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gi[i] = gi[i];
			return gi;

		case 3: // Float - Long
			final float[] gf = new float[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gf[i] = (gs[i] - (isSigned ? maxVal / 2.0f : 0.0f))
						* shortScaleFactor;
			return gf;

		case 10: // Mask
			final boolean[] gbool = new boolean[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gbool[i] = gs[i] > 0;
			return gbool;
		default:
			throw new IllegalArgumentException("Unknown data type!!!" + asType
					+ ", " + gs);

		}
	}

	@Deprecated
	public static Object convertFloatArray(final float[] gf, final int asType,
			final boolean isSigned, final float shortScaleFactor) {
		assert (asType >= 0 && asType <= 3) || asType == 10;
		final int sliceSize = gf.length;
		switch (asType) {
		case 0: // Char
			final char[] gb = new char[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gb[i] = (char) ((gf[i] / shortScaleFactor) + (isSigned ? 127
						: 0));
			return gb;
		case 1: // Short
			// Read short data type in
			final short[] gs = new short[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gs[i] = (short) ((gf[i] / shortScaleFactor) + (isSigned ? 32768
						: 0));
			return gs;
		case 2: // Spec / Int
			// Read integer data type in
			final int[] gi = new int[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gi[i] = (int) ((gf[i] / shortScaleFactor) + (isSigned ? 32768
						: 0));
			return gi;
		case 3: // Float - Long
			return gf;
		case 10: // Mask
			final boolean[] gbool = new boolean[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gbool[i] = gf[i] > 0;
			return gbool;
		default:
			throw new IllegalArgumentException("Unknown data type!!!" + asType
					+ ", " + gf);
		}

	}

	@Deprecated
	public static Object convertIntArray(int[] gi, final int asType,
			final boolean isSigned, final float ShortScaleFactor) {
		return convertIntArray(gi, asType, isSigned, ShortScaleFactor, 65536);
	}

	@Deprecated
	public static Object convertIntArray(int[] gi, final int asType,
			final boolean isSigned, final float ShortScaleFactor, int maxVal) {
		final int sliceSize = gi.length;
		switch (asType) {
		case 0: // Char
			final char[] gb = new char[sliceSize];
			for (int i = 0; i < sliceSize; i++) {
				gb[i] = (char) gi[i];
			}

			return gb;

		case 1: // Short
			// Read short data type in
			final short[] gs = new short[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gs[i] = (short) gi[i];
			return gs;

		case 2: // Spec / Int
			// Read integer data type in

			return gi;

		case 3: // Float - Long
			final float[] gf = new float[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gf[i] = (gi[i] - (isSigned ? maxVal / 2.0f : 0.0f))
						* ShortScaleFactor;
			return gf;

		case 10: // Mask
			final boolean[] gbool = new boolean[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gbool[i] = gi[i] > 0;

			return gbool;
		default:
			throw new IllegalArgumentException("Unknown data type!!!" + asType
					+ ", " + gi);

		}
	}

	@Deprecated
	public static Object convertShortArray(short[] gs, final int asType,
			final boolean isSigned, final float ShortScaleFactor, int maxVal) {
		final int sliceSize = gs.length;
		switch (asType) {
		case 0: // Char
			final char[] gb = new char[sliceSize];
			for (int i = 0; i < sliceSize; i++) {
				gb[i] = (char) gs[i];
			}

			return gb;

		case 1: // Short
			// Read short data type in

			return gs;

		case 2: // Spec / Int
			// Read integer data type in
			final int[] gi = new int[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gi[i] = gi[i];
			return gi;

		case 3: // Float - Long
			final float[] gf = new float[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gf[i] = (gs[i] - (isSigned ? maxVal / 2.0f : 0.0f))
						* ShortScaleFactor;
			return gf;

		case 10: // Mask
			final boolean[] gbool = new boolean[sliceSize];
			for (int i = 0; i < sliceSize; i++)
				gbool[i] = gs[i] > 0;

			return gbool;
		default:
			throw new IllegalArgumentException("Unknown data type!!!" + asType
					+ ", " + gs);

		}
	}

	public static D3int getDXYZFromVec(D3int vecDim, int pixVal, int slicen) {
		// int x,y,z;
		final D3int oPos = new D3int();
		oPos.x = pixVal % vecDim.getWidth();
		oPos.y = (pixVal - oPos.x) / vecDim.getWidth();
		oPos.z = slicen;
		return oPos;
	}

	public static int getJFromVec(D3int vecPos, D3int vecDim, int x, int y) {
		return getJFromVec(vecPos, vecDim, x, y, true);
	}

	public static int getJFromVec(D3int vecPos, D3int vecDim, int x, int y,
			boolean relCoord) {
		int curX = x;
		int curY = y;
		if (relCoord) {
			curX -= vecPos.x;
			curY -= vecPos.y;
		}
		return (curY) * vecDim.getWidth() + curX;

	}

	public static D3float getRXYZFromVec(D3int vecPos, D3float vecSize,
			D3int iPos, boolean asMeasure) {
		final D3float oPos = new D3float();
		if (asMeasure) {
			oPos.x = ((float) iPos.x + (float) vecPos.x) * vecSize.x;
			oPos.y = ((float) iPos.y + (float) vecPos.y) * vecSize.y;
			oPos.z = ((float) iPos.z + (float) vecPos.z) * vecSize.z;
		} else {
			oPos.x = ((float) iPos.x + (float) vecPos.x);
			oPos.y = ((float) iPos.y + (float) vecPos.y);
			oPos.z = ((float) iPos.z + (float) vecPos.z);
		}
		return oPos;
	}

	public static D3float getRXYZFromVec(D3int vecPos, D3int iPos) {
		final D3float oPos = new D3float();
		return getRXYZFromVec(vecPos, oPos, iPos, false);
	}

	public static D3float getRXYZFromVec(D3int vecPos, D3int vecDim,
			int pixVal, int slicen) {
		final D3int iPos = getDXYZFromVec(vecDim, pixVal, slicen);
		return getRXYZFromVec(vecPos, iPos);
	}

	/**
	 * Get a double array of the x,y,z position given a current slice index and
	 * current slice
	 **/
	public static Double[] getXYZVecFromVec(D3int vecPos, D3int vecDim,
			int cIndex, int cSlice) {
		final D3float npos = getRXYZFromVec(vecPos, vecDim, cIndex, cSlice);
		final Double[] cPos = new Double[3];
		cPos[0] = new Double(npos.x);
		cPos[1] = new Double(npos.y);
		cPos[2] = new Double(npos.z);
		return cPos;
	}

	/**
	 * Get a double array of the x,y,z position given a current slice index and
	 * current slice
	 **/
	public static Double[] getXYZVecFromVec(TImgRO inImg, int cIndex, int cSlice) {
		return getXYZVecFromVec(inImg.getPos(), inImg.getDim(), cIndex, cSlice);
	}

	/**
	 * Check to see if the type chosen is valid
	 * 
	 * @param asType
	 *            the type to check
	 * @return true if valid otherwise false
	 */
	public static boolean isValidType(int asType) {
		return (asType >= 0 && asType <= 3) || asType == 10;
	}

	/**
	 * A method to implement the inheritance functionality to a standard TImgRO
	 * currently uses VirtualAim, but this will be fixed soon
	 * 
	 * @param inImg
	 * @return an exportable version of inImg
	 */
	public static TImgRO.CanExport makeTImgExportable(TImgRO inImg) {
		return VirtualAim.TImgToVirtualAim(inImg);
	}

	/**
	 * A method to implement the full array reading functionality to a standard
	 * TImgRO currently uses VirtualAim, but this will be fixed soon
	 * 
	 * @param inImg
	 * @return a fullreadable version of inImg
	 */
	@Deprecated
	public static FullReadable makeTImgFullReadable(TImgRO inImg) {
		return VirtualAim.TImgToVirtualAim(inImg);
	}

	/** Copy the size of one TImg to another **/
	public static void mirrorImage(HasDimensions inData,
			ChangesDimensions outData) {
		outData.setPos(inData.getPos());
		outData.setOffset(inData.getOffset());
		outData.setDim(inData.getDim());
		outData.setElSize(inData.getElSize());

		outData.appendProcLog(inData.getProcLog());
	}

	public static TImg ReadTImg(String path) {
		return ReadTImg(path, false, false);
	}

	/**
	 * Read an image and save it to the global cache for later retrival (must
	 * then be manually deleted)
	 * 
	 * @param path
	 * @param readFromCache
	 *            check the cache to see if the image is already present
	 * @param saveToCache
	 *            put the image into the cache after it has been read
	 * @return loaded image
	 */
	public static TImg ReadTImg(String path, boolean readFromCache,
			boolean saveToCache) {
		if (readFromCache)
			if (cachedImages.containsKey(path))
				return cachedImages.get(path);
		final TImg curImg = new VirtualAim(path);
		if (saveToCache)
			cachedImages.put(path, curImg);
		return curImg;
	}

	public static void RemoveTImgFromCache(String path) {
		try {
			cachedImages.remove(path);
			System.gc();
		} catch (final Exception e) {
			e.printStackTrace();
			System.err.println("Image:" + path + " is not in the cache!");
		}
	}

	/**
	 * The size in bytes of each datatype
	 * 
	 * @param inType
	 * @return size in bytes
	 */
	public static long typeSize(int inType) {
		assert isValidType(inType);
		switch (inType) {
		case 0:
			return 1;
		case 1:
			return 2;
		case 2:
			return 4;
		case 10:
			return 1;
		}
		return -1;
	}

	public static TImg WrapTImgRO(TImgRO inImage) {
		return new VirtualAim(inImage);
		// return new TImgFromTImgRO(inImage);
	}

	/**
	 * Starts a new thread to save the current image without interrupting other
	 * processings. The thread then closes when the saving operation is complete
	 * 
	 * @param inImg
	 *            name of the file to save
	 * @param filename
	 *            path of the saved file
	 */
	public static void WriteBackground(final TImgRO.CanExport inImg,
			final String filename) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				System.out.println("BG Save Started for Image:" + inImg
						+ " to path:" + filename);
				inImg.WriteAim(filename);
			}
		}).start();

	}

	/**
	 * Method to write an image to disk and return whether or not it was
	 * successful
	 * 
	 * @param curImg
	 * @param path
	 * @return success
	 */
	public static boolean WriteTImg(TImgRO curImg, String path) {
		return WriteTImg(curImg, path, false);
	}

	/**
	 * Method to write an image to disk and return whether or not it was
	 * successful
	 * 
	 * @param curImg
	 * @param path
	 * @param saveToCache
	 * @return success
	 */
	public static boolean WriteTImg(TImgRO curImg, String path,
			boolean saveToCache) {

		try {
			if (curImg instanceof VirtualAim)
				curImg.WriteAim(path);
			else
				VirtualAim.TImgToVirtualAim(curImg).WriteAim(path);
			return true;
		} catch (final Exception e) {
			System.err.println("Image: " + curImg.getSampleName() + " @ "
					+ curImg + ", could not be written to " + path);
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Write a TImg with all of the appropriate parameters
	 * 
	 * @param inImg
	 * @param outpath
	 * @param outType
	 * @param scaleVal
	 * @param IisSigned
	 */
	@Deprecated
	public static void WriteTImg(TImgRO inImg, String outpath, int outType,
			float scaleVal, boolean IisSigned) {
		VirtualAim.TImgToVirtualAim(inImg).WriteAim(outpath, outType, scaleVal,
				IisSigned);
	}
}
