package org.vadere.util.opencl;


import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility-class without a state. This class offers method to interact with OpenCL e.g. memory management methods.
 *
 * @author Benedikt Zoennchen
 */
public class CLUtils {

    /**
     * Reads the specified resource and returns the raw data as a ByteBuffer.
     *
     * @param resource   the resource to read
     * @param bufferSize the initial buffer size
     * @return the resource data
     * @throws IOException if an IO error occurs
     */
    public static ByteBuffer ioResourceToByteBuffer(final String resource, int bufferSize) throws IOException {
        ByteBuffer buffer;

        Path path = Paths.get(resource);
        if (Files.isReadable(path)) {
            try (SeekableByteChannel fc = Files.newByteChannel(path)) {
                buffer = MemoryUtil.memCalloc((int)fc.size() + 1);
                while (fc.read(buffer) != -1) {}
            }
        } else {
            try (
                    InputStream source = CLUtils.class.getClassLoader().getResourceAsStream(resource);
                    ReadableByteChannel rbc = Channels.newChannel(source)
            ) {

                buffer = MemoryUtil.memCalloc(bufferSize);

                while (true) {
                    int bytes = rbc.read(buffer);
                    if (bytes == -1) {
                        break;
                    }
                    if (buffer.remaining() == 0) {
                        buffer = resizeBuffer(buffer, buffer.capacity() * 2);
                    }
                }
            }
        }

        buffer.flip();
        return buffer;
    }

    public static IntBuffer toIntBuffer(@NotNull final int[] array) {
    	IntBuffer intBuffer = MemoryUtil.memAllocInt(array.length);
    	return intBuffer;
    }

	public static IntBuffer toIntBuffer(@NotNull final int[] array, @NotNull final IntBuffer intBuffer) {
		for(int i = 0; i < array.length; i++) {
			intBuffer.put(i, array[i]);
		}
		return intBuffer;
	}

    public static FloatBuffer toFloatBuffer(@NotNull final float[] floats) {
        FloatBuffer floatBuffer = MemoryUtil.memAllocFloat(floats.length);
        return toFloatBuffer(floats, floatBuffer);
    }

    public static FloatBuffer toFloatBuffer(@NotNull final float[] floats, @NotNull final FloatBuffer floatBuffer) {
        for(int i = 0; i < floats.length; i++) {
            floatBuffer.put(i, floats[i]);
        }
        return floatBuffer;
    }

	public static int[] toIntArray(@NotNull final IntBuffer floatBuffer, final int size) {
		int[] result = new int[size];
		for(int i = 0; i < size; i++) {
			result[i] = floatBuffer.get(i);
		}
		return result;
	}


	public static float[] toFloatArray(@NotNull final FloatBuffer floatBuffer, final int size) {
	    float[] result = new float[size];
	    for(int i = 0; i < size; i++) {
	        result[i] = floatBuffer.get(i);
        }
        return result;
    }

    private static ByteBuffer resizeBuffer(@NotNull final ByteBuffer buffer, int newCapacity) {
        ByteBuffer newBuffer = MemoryUtil.memCalloc(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
	    MemoryUtil.memFree(buffer);
        return newBuffer;
    }
}
