package org.metastatic.gelfback;

import java.nio.ByteBuffer;
import org.junit.Test;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class ByteBuffersOutputStreamTest {
    @Test
    public void testEmpty() {
        ByteBuffersOutputStream out = new ByteBuffersOutputStream();
        ByteBuffer[] buffers = out.toBuffers();
        assertThat("there is one buffer", buffers.length, is(1));
        assertThat("the one buffer is empty", buffers[0].remaining(), is(0));
    }

    @Test
    public void testOne() {
        ByteBuffersOutputStream out = new ByteBuffersOutputStream();
        out.write('x');
        ByteBuffer[] buffers = out.toBuffers();
        assertThat("there is one buffer", buffers.length, is(1));
        assertThat("there is one element in the buffer", buffers[0].remaining(), is(1));
        assertThat("the value in the buffer is 'x'", buffers[0].get(), is((byte) 'x'));
    }

    @Test
    public void testSpan() {
        byte[] b = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
        ByteBuffersOutputStream out = new ByteBuffersOutputStream(7);
        out.write(b);
        ByteBuffer[] buffers = out.toBuffers();
        assertThat("there are two buffers", buffers.length, is(2));
        assertThat("there are seven elements in the first buffer", buffers[0].remaining(), is(7));
        assertThat("there are three elements in the second buffer", buffers[1].remaining(), is(3));
        byte[] b1 = new byte[7];
        buffers[0].get(b1);
        for (int i = 0; i < 7; i++) {
            assertThat("the first buffer contains the first seven elements", b1[i], is(b[i]));
        }
        byte[] b2 = new byte[3];
        buffers[1].get(b2);
        for (int i = 0; i < 3; i++) {
            assertThat("the second buffer contains the three remaining elements", b2[i], is(b[7 + i]));
        }
    }
}
