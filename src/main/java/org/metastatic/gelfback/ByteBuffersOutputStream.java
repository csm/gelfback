package org.metastatic.gelfback;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 */
public class ByteBuffersOutputStream extends OutputStream {
    private final LinkedList<ByteBuffer> buffers;
    private final int slabSize;

    public ByteBuffersOutputStream(int slabSize, int initialCapacity) {
        if (slabSize <= 0)
            throw new IllegalArgumentException("slabSize must be positive");
        if (initialCapacity <= 0)
            throw new IllegalArgumentException("initialCapacity must be positive");
        buffers = new LinkedList<ByteBuffer>();
        for (int i = 0; i < initialCapacity; i += slabSize) {
            buffers.add(ByteBuffer.allocate(slabSize));
        }
        this.slabSize = slabSize;
    }

    public ByteBuffersOutputStream(int slabSize) {
        this(slabSize, slabSize);
    }

    public ByteBuffersOutputStream() {
        this(4096);
    }

    /**
     * Return a view of the current buffer contents, prepared for reading.
     *
     * @return An array of buffers
     */
    public ByteBuffer[] toBuffers() {
        ByteBuffer[] ret = buffers.toArray(new ByteBuffer[buffers.size()]);
        for (int i = 0; i < ret.length; i++) {
            ByteBuffer b = ret[i];
            ret[i] = ((ByteBuffer) b.flip()).slice().asReadOnlyBuffer();
        }
        return ret;
    }

    private void extend() {
        buffers.add(ByteBuffer.allocate(slabSize));
    }

    @Override
    public void write(int b) {
        write(new byte[] { (byte) b });
    }

    @Override
    public void write(byte[] b, int offset, int length) {
        int n = 0;
        while (n < length) {
            int w = Math.min(length - n, buffers.getLast().remaining());
            if (w == 0) {
                extend();
                continue;
            }
            buffers.getLast().put(b, offset + n, w);
            n += w;
        }
    }

    @Override
    public void write(byte[] b) {
        write(b, 0, b.length);
    }
}
