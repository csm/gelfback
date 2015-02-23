package org.metastatic.gelfback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

/**
 * Generic GELF codec; turns log messages into JSON bytes.
 */
public class GELFCodec {
    public static final String GELF_VERSION = "1.1";
    private static final BigDecimal TIMESTAMP_DIVISOR = BigDecimal.valueOf(1000);

    private final String host;
    private final boolean includeCallerData;

    public GELFCodec(String host, boolean includeCallerData) {
        if (host == null)
            throw new NullPointerException();
        if (host.length() == 0)
            throw new IllegalArgumentException("host must not be empty");
        this.host = host;
        this.includeCallerData = includeCallerData;
    }

    /**
     * Encode the given log event as uncompressed JSON, framed for TCP transport with a null byte.
     *
     * @param event The event.
     * @return An array of byte buffers containing the JSON bytes ready to send.
     */
    public ByteBuffer[] framed(ILoggingEvent event) {
        ByteBuffersOutputStream out = new ByteBuffersOutputStream();
        encodeTo(event, out);
        out.write(0);
        return out.toBuffers();
    }

    private void encodeTo(ILoggingEvent event, OutputStream out) {
        try {
            OutputStreamWriter w = new OutputStreamWriter(out, "UTF-8");
            w.write('{');
            writeJsonString(w, "version");
            w.write(':');
            writeJsonString(w, GELF_VERSION);
            w.write(',');

            writeJsonString(w, "level");
            w.write(':');
            writeJsonInt(w, mapLevelToSyslog(event.getLevel()));
            w.write(',');

            writeJsonString(w, "host");
            w.write(':');
            writeJsonString(w, host);
            w.write(',');

            String message = event.getFormattedMessage();

            if (message.indexOf('\n') > 0) {
                writeJsonString(w, "full_message");
                w.write(':');
                writeJsonString(w, message);
                w.write(',');

                writeJsonString(w, "short_message");
                w.write(':');
                writeJsonString(w, message.substring(0, message.indexOf('\n')));
                w.write(',');
            } else {
                writeJsonString(w, "short_message");
                w.write(':');
                writeJsonString(w, message);
                w.write(',');
            }

            writeJsonString(w, "timestamp");
            w.write(':');
            writeJsonString(w, BigDecimal.valueOf(event.getTimeStamp()).divide(TIMESTAMP_DIVISOR).toPlainString());
            w.write(',');

            writeJsonString(w, "_logger");
            w.write(':');
            writeJsonString(w, event.getLoggerName());
            w.write(',');

            writeJsonString(w, "_thread");
            w.write(':');
            writeJsonString(w, event.getThreadName());

            if (includeCallerData || event.hasCallerData()) {
                StackTraceElement[] stack = event.getCallerData();
                if (stack != null && stack.length > 0) {
                    w.write(',');

                    writeJsonString(w, "_file");
                    w.write(':');
                    writeJsonString(w, stack[0].getFileName());
                    w.write(',');

                    writeJsonString(w, "_line");
                    w.write(':');
                    writeJsonInt(w, stack[0].getLineNumber());
                }
            }

            w.write('}');
            w.flush();
        }
        catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static final int SYSLOG_ERROR = 3;
    private static final int SYSLOG_WARN = 4;
    private static final int SYSLOG_NOTICE = 5;
    private static final int SYSLOG_INFO = 6;
    private static final int SYSLOG_DEBUG = 7;

    private static int mapLevelToSyslog(Level l) {
        if (l.isGreaterOrEqual(Level.ERROR)) {
            return SYSLOG_ERROR;
        } else if (l.isGreaterOrEqual(Level.WARN)) {
            return SYSLOG_WARN;
        } else if (l.isGreaterOrEqual(Level.INFO)) {
            return SYSLOG_INFO;
        } else {
            return SYSLOG_DEBUG;
        }
    }

    private static void writeJsonInt(Writer w, Integer i) throws IOException {
        w.write(i.toString());
    }

    private static void writeJsonString(Writer w, String s) throws IOException {
        w.write('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isISOControl(ch)) {
                w.write(String.format("\\u%04x", (int) ch));
            } else if (ch == '"' || ch == '\\') {
                w.write('\\');
                w.write(ch);
            } else {
                w.write(ch);
            }
        }
        w.write('"');
    }
}
