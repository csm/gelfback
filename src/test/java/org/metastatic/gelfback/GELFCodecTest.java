package org.metastatic.gelfback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import org.slf4j.helpers.NOPLogger;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Collections;

import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;

/**
 * Created by cmarshall on 2/23/15.
 */
public class GELFCodecTest {
    public void dump(ByteBuffer[] buffers) {
        for (int i = 0; i < buffers.length; i++) {
            System.out.printf("Buffer %d:%n", i);
            ByteBuffer buffer = buffers[i].duplicate();
            byte[] slice = new byte[16];
            int o = 0;
            while (buffer.hasRemaining()) {
                int l = Math.min(slice.length, buffer.remaining());
                buffer.get(slice, 0, l);
                System.out.printf("%08x  ", o);
                o += l;
                for (int j = 0; j < l; j++) {
                    System.out.printf("%02x ", slice[j] & 0xFF);
                }
                for (int j = 0; j < 16 - l; j++) {
                    System.out.printf("   ");
                }
                System.out.print("  ");
                for (int j = 0; j < l; j++) {
                    char c = (char) slice[j];
                    if (Character.isLetterOrDigit(c) || c == ' ' || !(Character.isWhitespace(c) || Character.isISOControl(c))) {
                        System.out.printf("%c", c);
                    } else {
                        System.out.printf(".");
                    }
                }
                System.out.println();
            }
        }
    }

    public byte[] glue(ByteBuffer[] buffers) {
        int len = 0;
        for (ByteBuffer buf : buffers)
            len += buf.remaining();
        byte[] ret = new byte[len];
        int o = 0;
        for (ByteBuffer buf : buffers) {
            int l = buf.remaining();
            buf.get(ret, o, l);
            o += l;
        }
        return ret;
    }

    @Test
    public void testBasic() throws UnsupportedEncodingException {
        GELFCodec codec = new GELFCodec("test", true, true);
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage("test message, my integer: {}");
        event.setArgumentArray(new Object[]{1234});
        event.setTimeStamp(978336000000L);
        event.setLoggerName("TestLogger");
        event.setThreadName("TestThread");
        event.setCallerData(Thread.currentThread().getStackTrace());
        ByteBuffer[] buffers = codec.framed(event, Collections.<String, String>emptyMap());
        dump(buffers);
        byte[] bytes = glue(buffers);
        String jsonString = new String(bytes, 0, bytes.length - 1, "UTF-8");
        Gson gson = new Gson();
        JsonObject o = gson.fromJson(jsonString, JsonObject.class);
        assertThat("parsed object is not null", o, notNullValue());
        assertThat("version is 1.1", o.get("version").getAsString(), is("1.1"));
        assertThat("level is 6", o.get("level").getAsInt(), is(6));
        assertThat("host is test", o.get("host").getAsString(), is("test"));
        assertThat("timestamp is 978336000 seconds", o.get("timestamp").getAsBigDecimal(), is(BigDecimal.valueOf(978336000L)));
        assertThat("short_message", o.get("short_message").getAsString(), is("test message, my integer: 1234"));
    }

    @Test
    public void testWithThrowable() {
        GELFCodec codec = new GELFCodec("test", true, true);
        try {
            throw new Exception("bam!");
        } catch (Exception e) {
            LoggingEvent event = new LoggingEvent(GELFCodecTest.class.getName(), new LoggerContext().getLogger(getClass()), Level.ERROR, "some error occurred: {}", e, new Object[]{1234});
            ByteBuffer[] buffers = codec.framed(event);
            dump(buffers);
        }
    }
}
