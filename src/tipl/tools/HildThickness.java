package tipl.tools;

import java.util.ArrayList;

import tipl.formats.TImg;
import tipl.formats.TImgRO;
import tipl.util.ArgumentParser;
import tipl.util.D3int;
import tipl.util.SGEJob;
import tipl.util.TImgTools;

/** Thickness map based on the Hildebrand Method */
public class HildThickness extends Thickness {
	/** Run the distance label initialization routines in parallel */
	private static class dlRunner extends Thread {
		int sslice, fslice;
		volatile HildThickness parent;
		long bcount = 0;

		public dlRunner(HildThickness iparent, int isslice, int ifslice) {
			super("dlRunner:<" + isslice + ", " + ifslice + ">");
			sslice = isslice;
			fslice = ifslice;
			parent = iparent;
		}

		/**
		 * Distribute (using divideThreadWork) and process (using processWork)
		 * the work across the various threads
		 */
		@Override
		public void run() {
			bcount += parent.locateSeeds(sslice, fslice);
			System.out.println("DlRunner Finished:, <" + sslice + ", " + fslice
					+ ">" + ", Ridges-" + bcount);
		}
	}

	/** date and version number of the script **/
	public static final String kVer = "08-08-2013 v02";

	/**
	 * Similar to DTObjection function in XIPL, takes a black and white input
	 * image and calculates the thickness map of the object (white) of this
	 * image. It then writes the thickness map to the file outAimFile and the
	 * histogram to histoFile
	 * 
	 * @param inAimFile
	 *            the binary aim file to use for the thickness map
	 * @param outAimFile
	 *            the name of the output thickness map file
	 * @param histoFile
	 *            the name of the csv histogram file to write
	 */
	public static boolean DTO(String inAimFile, String outAimFile,
			String histoFile) {
		final TImg thickmapAim = DTO(TImgTools.ReadTImg(inAimFile));
		thickmapAim.WriteAim(outAimFile);
		GrayAnalysis.StartHistogram(thickmapAim, histoFile + ".csv");
		return true;
	}

	/**
	 * Similar to DTObjection function in XIPL, takes a black and white input
	 * image and calculates the thickness map of the object (white) of this
	 * image. It then writes the thickness map to the file outAimFile and the
	 * histogram to histoFile
	 * 
	 * @param inAimFile
	 *            the binary aim file to use for the thickness map
	 * @param outDistFile
	 *            the name of the output distance map file
	 * @param outAimFile
	 *            the name of the output thickness map file
	 * @param histoFile
	 *            the name of the csv histogram file to write
	 * @param profileFile
	 *            the name of the file to write with the profile information
	 */
	public static boolean DTO(String inAimFile, String outDistFile,
			String outAimFile, String histoFile, String profileFile) {
		final TImg maskAim = TImgTools.ReadTImg(inAimFile);
		final TImg[] mapAims = DTOD(maskAim);
		if (outDistFile.length() > 0)
			mapAims[0].WriteAim(outDistFile);
		if (outAimFile.length() > 0)
			mapAims[1].WriteAim(outAimFile);
		if (histoFile.length() > 0)
			GrayAnalysis.StartHistogram(mapAims[1], histoFile + ".tsv");
		if (profileFile.length() > 0) {
			GrayAnalysis.StartZProfile(mapAims[1], maskAim, profileFile
					+ "_z.tsv", 0.1f);
			GrayAnalysis.StartRProfile(mapAims[1], maskAim, profileFile
					+ "_r.tsv", 0.1f);
			GrayAnalysis.StartRCylProfile(mapAims[1], maskAim, profileFile
					+ "_rcyl.tsv", 0.1f);
		}
		return true;
	}

	/**
	 * Similar to DTObjection function in XIPL, takes a black and white input
	 * image and calculates the thickness map of the object (white) of this
	 * image. It returns the thickness as an colored image
	 * 
	 * @param bwObject
	 *            The binary input image
	 */
	public static TImg DTO(TImg bwObject) {
		final TImg[] outImgs = DTOD(bwObject);
		return outImgs[1];
	}

	/**
	 * Similar to DTObjection function in XIPL, takes a black and white input
	 * image and calculates the thickness map of the object (white) of this
	 * image. It returns the distance map and thickness map as an colored image
	 * 
	 * @param bwObject
	 *            The binary input image
	 */
	public static TImg[] DTOD(TImg bwObject) {
		VoronoiTransform KV = new kVoronoiShrink(bwObject, false);
		KV.run();
		final TImg distAim = KV.ExportDistanceAim(bwObject);
		KV = null;
		final Thickness KT = new HildThickness(distAim);
		KT.run();
		return new TImg[] { distAim, KT.ExportAim(distAim) };
	}

	public static void main(String[] args) {
		System.out.println("Hildebrand-based Thickness Map v"
				+ HildThickness.kVer);
		System.out.println(" By Kevin Mader (kevin.mader@gmail.com)");
		final ArgumentParser p = new ArgumentParser(args);
		final String inAimFile = p.getOptionString("in", "",
				"In image to calculate the thickness map of");

		String defOutName = inAimFile;
		if (inAimFile.lastIndexOf(".") > 0)
			defOutName = defOutName.substring(0, inAimFile.lastIndexOf("."));
		final String outDistFile = p.getOptionString("distmap", defOutName
				+ "_dist.tif", "Output distance map");
		final String outAimFile = p.getOptionString("thickmap", defOutName
				+ "_dto.tif", "Output thickness map");
		final String histoFile = p.getOptionString("csv", defOutName + "_dto",
				"Histogram of thickness values");
		final String profileFile = p.getOptionString("profile", histoFile,
				"Profile of thickness values");

		final boolean runAsJob = p
				.getOptionBoolean("sge:runasjob",
						"Run this script as an SGE job (adds additional settings to this task");
		SGEJob jobToRun = null;
		if (runAsJob)
			jobToRun = SGEJob.runAsJob("tipl.tools.HildThickness", p, "sge:");
		p.checkForInvalid();
		if (runAsJob)
			jobToRun.submit();
		else
			DTO(inAimFile, outDistFile, outAimFile, histoFile, profileFile);
	}

	public boolean[] diffmask;
	public int maxlabel;
	public int unfilledVox = 0;

	private final double MINWALLDIST = 3;
	/** criterium to identify high points on the distance map for the ridge **/
	public double FLATCRIT = 0.45;// 0.401;
	/** how many voxels to skip while running the filling **/
	public int SKIPFILLING = 3;
	protected boolean isDiffMaskReady = false;

	int remVoxels = aimLength;

	int totalVoxels = aimLength;
	public int bubbleCount = 0;

	public HildThickness() {

	}

	public HildThickness(TImg distmapAim) {
		LoadImages(new TImg[] { distmapAim });
	}

	/**
	 * Object to divide the thread work into supportCores equal parts, default
	 * is z-slices
	 */
	public int[] divideSlices(int cThread) {
		final int minSlice = lowz + OUTERSHELL;
		final int maxSlice = (uppz - OUTERSHELL);

		final int range = (maxSlice - minSlice) / neededCores();

		int startSlice = minSlice;
		int endSlice = startSlice + range;

		for (int i = 0; i < cThread; i++) {
			startSlice = endSlice; // must overlap since i<endSlice is always
									// used, endslice is never run
			endSlice = startSlice + range;
		}
		if (cThread == (neededCores() - 1))
			endSlice = maxSlice;
		if (cThread >= neededCores())
			return null;
		return (new int[] { startSlice, endSlice });
	}

	@Override
	public boolean execute() {
		if (runMulticore()) {

			final String outString = "HildThicknes: Ran in "
					+ StrRatio(System.currentTimeMillis() - jStartTime, 1000)
					+ " seconds on " + neededCores() + " cores";
			System.out.println(outString);
			procLog += outString + "\n";

			procLog += "CMD:" + getPluginName() + ": Max Bubbles:"
					+ bubbleCount + "\n";
			runCount++;
			return true;
		} else
			return false;

	}

	/** export the bubble seeds if anyone actually wants them */
	public TImg ExportRidgeAim(TImgRO.CanExport templateAim) {
		if (isDiffMaskReady) {
			final TImg outAimData = templateAim.inheritedAim(diffmask, dim,
					offset);
			outAimData.appendProcLog(procLog);
			return outAimData;
		} else {
			throw new IllegalArgumentException(
					"The plug-in : "
							+ getPluginName()
							+ ", has not yet been initialized, exporting the ridge map does not make any sense");
			// return templateAim.inheritedAim(templateAim);

		}
	}

	@Override
	public String getPluginName() {
		return "Hildebrand Thickness";
	}

	protected void Init(D3int idim, D3int ioffset) {
		outAim = new int[aimLength];
		System.arraycopy(inAim, 0, outAim, 0, inAim.length);
		diffmask = new boolean[aimLength];
		InitDims(idim, ioffset);
		InitDiffmask();
	}

	public void InitDiffmask() {

		Thread.currentThread();
		final ArrayList<dlRunner> threadList = new ArrayList<dlRunner>();

		jStartTime = System.currentTimeMillis();
		// Call the other threads
		for (int i = 0; i < neededCores(); i++) { // setup the background
													// threads
			final int[] mySlices = divideSlices(i);
			final dlRunner bgThread = new dlRunner(this, mySlices[0],
					mySlices[1]);
			threadList.add(bgThread);
			bgThread.start();
		}

		for (final dlRunner theThread : threadList) { // for all other threads:
			try {
				theThread.join(); // wait until thread has finished
			} catch (final InterruptedException e) {
				System.out.println("ERROR - Thread : " + theThread
						+ " was interrupted, proceed carefully!");
			}

		}

		final String outString = "Distance Ridge: Ran in "
				+ StrRatio(System.currentTimeMillis() - jStartTime, 1000)
				+ " seconds on " + neededCores() + " cores and found ...";
		System.out.println(outString);
		procLog += outString + "\n";
		isDiffMaskReady = true;
	}

	@Override
	protected void InitLabels(D3int idim, D3int ioffset) {

	}

	/**
	 * Load the distance map and create the ridge data
	 * 
	 */
	@Override
	public void LoadImages(TImgRO[] inImages) {
		// TODO Auto-generated method stub
		if (inImages.length < 1)
			throw new IllegalArgumentException(
					"Too few arguments for LoadImages in:" + getPluginName());
		final TImgRO inImg = inImages[0];
		final int[] inputmap = TImgTools.makeTImgFullReadable(inImg)
				.getIntAim();
		aimLength = inputmap.length;
		if (Thickness.doPreserveInput) {
			inAim = new int[aimLength];
			System.arraycopy(inputmap, 0, inAim, 0, aimLength);
		} else {
			inAim = inputmap;
		}
		Init(inImg.getDim(), inImg.getOffset());
	}

	private int locateSeeds(int startSlice, int finalSlice) {
		final D3int iNeighborSize = new D3int(2);
		int off = 0;
		double avgGrad = 0;
		double avgDGrad = 0;
		double avgSGrad = 0;
		int inVox = 0;
		unfilledVox = 0;
		int ridgeCnt = 0;
		final double cFLATCRIT = FLATCRIT;
		for (int z = startSlice; z < finalSlice; z++) {
			for (int y = lowy + OUTERSHELL; y < (uppy - OUTERSHELL); y++) {
				off = (z * dim.y + y) * dim.x + lowx + OUTERSHELL;
				for (int x = lowx + OUTERSHELL; x < (uppx - OUTERSHELL); x++, off++) {
					// The code is optimized so the least number of voxels make
					// it past the first check
					final float cVDist = (float) distScalar * inAim[off];

					if (((cVDist) > MINWALLDIST)) {
						unfilledVox++;
						double gradX = 0.0, gradY = 0.0, gradZ = 0.0;
						double lapVal = 0.0;
						int lapCount = 0;
						int gradCount = 0;
						for (int z2 = max(z - iNeighborSize.z, lowz); z2 <= min(
								z + iNeighborSize.z, uppz - 1); z2++) {
							for (int y2 = max(y - iNeighborSize.y, lowy); y2 <= min(
									y + iNeighborSize.y, uppy - 1); y2++) {
								int off2 = (z2 * dim.y + y2) * dim.x
										+ max(x - iNeighborSize.x, lowx);
								for (int x2 = max(x - iNeighborSize.x, lowx); x2 <= min(
										x + iNeighborSize.x, uppx - 1); x2++, off2++) {
									if (off != off2) {
										if ((Math.abs(x2 - x) <= 1)
												&& (Math.abs(x2 - x) <= 1)
												&& (Math.abs(x2 - x) <= 1)) { // Local
																				// gradient
											gradX += (x2 - x) * (inAim[off2]);
											gradY += (y2 - y) * (inAim[off2]);
											gradZ += (z2 - z) * (inAim[off2]);
											gradCount++;
										}

										if (((x2 != x) ? 1 : 0)
												+ ((y2 != y) ? 1 : 0)
												+ ((z2 != z) ? 1 : 0) <= 1) { // slightly
																				// larger
																				// laplacian
											lapVal += (distScalar * (inAim[off2]));
											lapCount++;
										}

										// First derivative is 0 and second
										// derivative is less than 0 (local
										// maxima)

									}
								}
							}
						}
						lapVal += -lapCount * (distScalar * (inAim[off]));
						lapVal /= lapCount;
						gradX /= gradCount;
						gradY /= gradCount;
						gradZ /= gradCount;

						gradX *= distScalar;
						gradY *= distScalar;
						gradZ *= distScalar;

						final double cGrad = Math.sqrt(gradX * gradX + gradY
								* gradY + gradZ * gradZ);

						avgGrad += cGrad;
						avgSGrad += cGrad * cGrad;
						inVox++;
						if ((cGrad <= cFLATCRIT)) {
							// System.out.println("GradVal:"+cGrad+"->("+gradX+", "+gradY+", "+gradZ+"), "+gradCount+", Lap:"+lapVal+", LC"+lapCount);
							diffmask[off] = true;
							ridgeCnt++;
							avgDGrad += cGrad;
						}

					} // End mask and dist-check
				} // End x
			} // End y
		} // End z
		avgGrad /= inVox;
		avgDGrad /= ridgeCnt;
		System.out.println("Average GradVal:" + avgGrad + " - STD:"
				+ Math.sqrt(avgSGrad / inVox - avgGrad * avgGrad)
				+ ", AvgRidgeVal:" + avgDGrad + " - Ridge Comp:"
				+ StrPctRatio(ridgeCnt * 100, inVox));
		// procLog+="CMD:LocalMaxima: GradVal:"+avgGrad+" - STD:"+Math.sqrt(avgSGrad/inVox-avgGrad*avgGrad)+", Lap:"+avgLap+" - STD:"+Math.sqrt(avgSLap/inVox-avgLap*avgLap)+"\n";
		return ridgeCnt;

	}

	@Override
	protected void processWork(Object currentWork) {
		final int[] range = (int[]) currentWork;
		final int bSlice = range[0];
		final int tSlice = range[1];
		runSection(bSlice, tSlice);
	}

	@Override
	@Deprecated
	public void run() {
		execute();
	}

	protected void runSection(int startSlice, int endSlice) {
		System.out.println("RidgeGrow Started:, <" + startSlice + ", "
				+ endSlice + ">");
		int cBubbleCount = 0;
		final int cSKIPFILLING = SKIPFILLING;
		for (int z = startSlice + OUTERSHELL; z < (endSlice + OUTERSHELL); z++) {
			if ((z - startSlice) % 3 == 2)
				System.out.println("RGRunning:, <" + startSlice + ", "
						+ endSlice + ">:" + z + ", " + cBubbleCount);
			for (int y = lowy + OUTERSHELL; y < (uppy + OUTERSHELL); y++) {
				int off = (z * dim.y + y) * dim.x + lowx + OUTERSHELL;
				for (int x = lowx + OUTERSHELL; x < (uppx + OUTERSHELL); x++, off++) {
					if (diffmask[off]) {
						if (inAim[off] > 0) {
							if ((cBubbleCount % cSKIPFILLING) == 0) {
								final double nVal = (inAim[off]) * distScalar;
								final boolean useSync = ((z + nVal) >= endSlice)
										| ((z - nVal) < startSlice);
								fillBubble(x, y, z, nVal, useSync);

							}
							cBubbleCount++;
						}
					}
				}
			}
		}

		bubbleCount += cBubbleCount;

	}

	@Override
	public ArgumentParser setParameter(ArgumentParser p, String prefix) {
		final ArgumentParser args = super.setParameter(p, prefix);
		FLATCRIT = args
				.getOptionDouble(prefix + "flatcrit", FLATCRIT,
						"Criterion for determining if ridge points on the distance map are flat enough");
		return args;
	}

}
