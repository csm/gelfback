package org.metastatic.gelfback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Append log messages to Graylog via GELF TCP.
 */
public class GELFTCPAppender extends AppenderBase<ILoggingEvent> {
    private class Sender implements Runnable {
        public void run() {
            debug("GELF Sender thread starting");
            while (isRunning.get()) {
                try {
                    ILoggingEvent event = events.take();
                    debug("received event: %s", event);
                    SocketChannel channel = null;
                    debug("sender waiting for %s..", connectedMonitor);
                    synchronized (connectedMonitor) {
                        debug("sender locked %s", connectedMonitor);
                        while (isRunning.get() && (channel = GELFTCPAppender.this.channel.get()) == null) {
                            try {
                                debug("waiting for connection...");
                                connectedMonitor.wait(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        if (!isRunning.get() || channel == null)
                            continue;
                        GELFCodec codec = GELFTCPAppender.this.codec.get();
                        if (codec == null) // shouldn't happen
                            continue;
                        ByteBuffer[] formatted = codec.framed(event, staticFields != null ? staticFields : Collections.<String, String>emptyMap());
                        channel.write(formatted);
                        debug("sent message!");
                        inflight--;
                    }
                } catch (Exception e) {
                    debug(e, "exception on sender loop");
                }
            }
        }
    }

    private class Connector implements Runnable {
        public void run() {
            boolean complainedAboutUnknownHost = false;
            debug("GELF Connector thread starting");
            SocketChannel channel;
            while (isRunning.get()) {
                try {
                    debug("connector waiting for %s..", connectedMonitor);
                    synchronized (connectedMonitor) {
                        debug("connector locked %s", connectedMonitor);
                        debug("resolving %s...", host);
                        InetAddress hostaddr = InetAddress.getByName(host);
                        debug("resolved to %s", hostaddr);
                        InetSocketAddress address = new InetSocketAddress(hostaddr, port);
                        debug("connecting to %s:%d...", host, port);
                        channel = SocketChannel.open();
                        channel.connect(address);
                        GELFTCPAppender.this.channel.set(channel);
                        connectedMonitor.notify();
                    }
                    debug("GELF TCP connected!");

                    reconnectLock.lock();
                    try {
                        reconnectCondition.await(ttlSeconds, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        // pass
                    } finally {
                        reconnectLock.unlock();
                    }
                    debug("reconnect TTL expired, reconnecting...");

                    synchronized (connectedMonitor) {
                        GELFTCPAppender.this.channel.set(null);
                        channel.close();
                    }
                } catch (UnknownHostException uhe) {
                    if (!complainedAboutUnknownHost) {
                        GELFTCPAppender.this.addError("Unknown host: " + host);
                        //System.err.println("Unknown host: " + host + ". We will try and resolve the host name again, every 60 seconds. This message will not be printed again.");
                        complainedAboutUnknownHost = true;
                    }
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException e) {
                    }
                } catch (IOException e) {
                    debug(e, "exception caught connecting: %s", e);
                } catch (Exception e) {
                    debug(e, "exception in connector loop");
                }
            }
        }
    }

    private static final Pattern keyValuePattern = Pattern.compile("(?<key>[^=]+)=(?<value>[^,]+)(?:,|$)");

    private static final int QUEUE_SIZE = 1024;
    private final BlockingQueue<ILoggingEvent> events = new ArrayBlockingQueue<ILoggingEvent>(QUEUE_SIZE);

    private AtomicReference<SocketChannel> channel = new AtomicReference<SocketChannel>();

    private AtomicBoolean isStarted = new AtomicBoolean(false);
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    private final Object connectedMonitor = new Object();
    private final Lock reconnectLock = new ReentrantLock();
    private final Condition reconnectCondition = reconnectLock.newCondition();

    private String host;
    private int port;
    private String localhost;
    private boolean includeCallerData;
    private boolean includeStackTrace;
    private AtomicReference<GELFCodec> codec = new AtomicReference<GELFCodec>();
    private int ttlSeconds = 60;
    private Map<String, String> staticFields;
    private long inflight = 0;

    static GELFTCPAppender self;
    static final boolean debugging;

    static {
        String s = System.getenv("GELFBACK_DEBUG_TO_STDERR");
        debugging = s != null && s.equalsIgnoreCase("true");
    }

    public GELFTCPAppender() {
        if (debugging) {
            System.err.println("GELFTCPAppender created");
        }
        self = this;
    }

    @Override
    public void start() {
        codec.set(new GELFCodec(localhost, includeCallerData, includeStackTrace));
        if (isStarted.compareAndSet(false, true)) {
            isRunning.set(true);
            Thread connectorThread = new Thread(new Connector(), "GELF-TCP-Connector");
            connectorThread.setDaemon(true);
            connectorThread.start();
            Thread senderThread = new Thread(new Sender(), "GELF-TCP-Sender");
            senderThread.setDaemon(true);
            senderThread.start();
        }
        super.start();
    }

    @Override
    public void stop() {
        isRunning.set(false);
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent e) {
        debug("appending event %s", e);
        events.add(new WrappedLoggingEvent(e, includeCallerData));
        inflight++;
    }

    public void setGelfPort(int port) {
        debug("setting port to %d", port);
        this.port = port;
        reconnectLock.lock();
        try {
            reconnectCondition.signal();
        } finally {
            reconnectLock.unlock();
        }
    }

    public void setGelfHost(String host) {
        debug("setting host to %s", host);
        this.host = host;
        reconnectLock.lock();
        try {
            reconnectCondition.signal();
        } finally {
            reconnectLock.unlock();
        }
    }

    public void setLocalHost(String host) {
        debug("setting local host: %s", host);
        this.localhost = host;
        if (isStarted.get()) {
            codec.set(new GELFCodec(localhost, includeCallerData, includeStackTrace));
        }
    }

    public void setIncludeCallerData(boolean includeCallerData) {
        debug("setting include caller data: %s", includeCallerData);
        this.includeCallerData = includeCallerData;
        if (isStarted.get()) {
            codec.set(new GELFCodec(localhost, includeCallerData, includeStackTrace));
        }
    }

    public void setIncludeStackTrace(boolean includeStackTrace) {
        debug("setting include stack trace: %s", includeStackTrace);
        this.includeStackTrace = includeStackTrace;
        if (isStarted.get()) {
            codec.set(new GELFCodec(localhost, includeCallerData, includeStackTrace));
        }
    }

    public void setTtlSeconds(int ttlSeconds) {
        debug("setting ttlSeconds: %s", ttlSeconds);
        this.ttlSeconds = ttlSeconds;
    }

    public void setStaticFields(String staticFields) {
        debug("setting static fields: %s", staticFields);
        Matcher matcher = keyValuePattern.matcher(staticFields);
        Map<String, String> kvs = new LinkedHashMap<String, String>();
        while (matcher.find()) {
            kvs.put(matcher.group("key"), matcher.group("value"));
        }
        this.staticFields = kvs;
        debug("static fields: %s", kvs);
    }

    private static void debug(String fmt, Object... args) {
        if (debugging) {
            System.err.println(String.format(fmt, args));
        }
    }

    private static void debug(Throwable t, String fmt, Object... args) {
        debug(fmt, args);
        if (debugging) {
            t.printStackTrace(System.err);
        }
    }

    public static boolean drained() {
        return (self.events.isEmpty() && (self.inflight == 0));
    }
}
