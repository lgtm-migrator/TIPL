package tipl.formats;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/** Writes TImg data to a RAW file */
public class RAWWriter implements TWriter {
	OutputStream os;
	String rawName;
	TImg outImg;
	int rawType;

	public RAWWriter(int irawType) {
		rawType = irawType;
	}

	@Override
	public boolean isParallel() {
		return false;
	}

	@Override
	public void SetupWriter(TImg inImg, String outpath) {
		outImg = inImg;
		rawName = outpath;
		try {
			os = new FileOutputStream(outpath);
		} catch (final Exception e) {
			System.out.println(writerName() + ": Cannot write raw file "
					+ outpath);
			e.printStackTrace();
		}
		System.gc();
	}

	@Override
	public void Write() {
		WriteHeader();
		for (int n = 0; n < outImg.getDim().z; n++) {
			WriteSlice(n);
		}

		try {
			os.close();
		} catch (final Exception e) {
			System.out.println(writerName() + ": Cannot close raw file");
			e.printStackTrace();
		}

	}

	@Override
	public void WriteHeader() {
		try {
			// Write the procedure log to a text file
			final FileWriter fstream = new FileWriter(rawName + "-raw.dat");
			final BufferedWriter out = new BufferedWriter(fstream);
			out.write(outImg.getDim().x + "," + outImg.getDim().y + ","
					+ outImg.getDim().z + "\n");
			out.write(rawType + "\n");
			out.write(outImg.getPos().x + "," + outImg.getPos().y + ","
					+ outImg.getPos().z + "\n");

			// Close the output stream
			out.close();
		} catch (final Exception e) {// Catch exception if any
			System.out.println("Error: " + e.getMessage());
		}

	}

	@Override
	public String writerName() {
		return "RAWWriter";
	}

	@Override
	public void WriteSlice(int n) {
		byte[] buffer = null;
		final TImg.TImgFull fullOutImg = new TImg.TImgFull(outImg);
		try {
			switch (rawType) {
			case 0:
				final char[] cslice = fullOutImg.getByteArray(n);
				buffer = new byte[cslice.length];
				for (int i = 0; i < cslice.length; i++)
					buffer[i] = (byte) cslice[i];
				break;
			case 1:
				final short[] sslice = fullOutImg.getShortArray(n);
				buffer = new byte[2 * sslice.length];
				for (int i = 0; i < sslice.length; i++) {
					final byte[] tbuffer = ByteBuffer.allocate(2)
							.putShort(sslice[i]).array();
					for (int j = 0; j < 2; j++)
						buffer[4 * i + j] = tbuffer[j];
				}
				break;
			case 2:
				final int[] islice = fullOutImg.getIntArray(n);
				buffer = new byte[4 * islice.length];
				for (int i = 0; i < islice.length; i++) {
					final byte[] tbuffer = ByteBuffer.allocate(4)
							.putInt(islice[i]).array();
					for (int j = 0; j < 4; j++)
						buffer[4 * i + j] = tbuffer[j];
				}
				break;
			case 3:
				final float[] fslice = fullOutImg.getFloatArray(n);
				buffer = new byte[4 * fslice.length];
				for (int i = 0; i < fslice.length; i++) {
					final byte[] tbuffer = ByteBuffer.allocate(4)
							.putFloat(fslice[i]).array();
					for (int j = 0; j < 4; j++)
						buffer[4 * i + j] = tbuffer[j];
				}
				break;
			case 10:
				final boolean[] bslice = fullOutImg.getBoolArray(n);
				int byteLen = bslice.length / 8;
				if ((bslice.length % 8) != 0)
					byteLen++;
				buffer = new byte[byteLen];
				int bitIndex = 0,
				byteIndex = 0;
				for (int i = 0; i < bslice.length; i++) {
					if (bslice[i]) {
						buffer[byteIndex] |= (byte) (((byte) 1) << bitIndex);
					}
					bitIndex++;
					if (bitIndex == 8) {
						bitIndex = 0;
						byteIndex++;
					}
				}
				break;
			}
			os.write(buffer);
		} catch (final Exception e) {
			System.out.println("Cannot write slice " + n + " raw file");
			e.printStackTrace();
		}
	}
}