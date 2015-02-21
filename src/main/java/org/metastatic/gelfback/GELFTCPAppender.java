package org.metastatic.gelfback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append log messages to Graylog via GELF TCP.
 */
public class GELFTCPAppender extends AppenderBase<ILoggingEvent> {
    private class Sender implements Runnable {
        @Override
        public void run() {
            while (isRunning.get()) {
                try {
                    ILoggingEvent event = events.take();
                    SocketChannel channel = null;
                    connectedLock.lock();
                    try {
                        while (isRunning.get() && (channel = GELFTCPAppender.this.channel.get()) == null) {
                            try {
                                connectedCondition.await(100, TimeUnit.MILLISECONDS);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        if (!isRunning.get() || channel == null)
                            continue;
                        GELFCodec codec = GELFTCPAppender.this.codec.get();
                        if (codec == null) // shouldn't happen
                            continue;
                        ByteBuffer[] formatted = codec.framed(event);
                        channel.write(formatted);
                    } finally {
                        connectedLock.unlock();
                    }
                } catch (Exception e) {
                    // pass
                }
            }
        }
    }

    private class Connector implements Runnable {
        @Override
        public void run() {
            SocketChannel channel;
            while (isRunning.get()) {
                try {
                    connectedLock.lock();
                    try {
                        InetSocketAddress address = new InetSocketAddress(host, port);
                        channel = SocketChannel.open();
                        channel.connect(address);
                        GELFTCPAppender.this.channel.set(channel);
                        connectedCondition.signal();
                    } finally {
                        connectedLock.unlock();
                    }

                    reconnectLock.lock();
                    try {
                        reconnectCondition.await(ttlSeconds, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        // pass
                    } finally {
                        reconnectLock.unlock();
                    }

                    connectedLock.lock();
                    try {
                        GELFTCPAppender.this.channel.set(null);
                        channel.close();
                    } finally {
                        connectedLock.unlock();
                    }
                } catch (IOException e) {
                }
            }
        }
    }

    private static final int QUEUE_SIZE = 1024;
    private final BlockingQueue<ILoggingEvent> events = new ArrayBlockingQueue<ILoggingEvent>(QUEUE_SIZE);

    private AtomicReference<SocketChannel> channel = new AtomicReference<SocketChannel>();

    private AtomicBoolean isStarted = new AtomicBoolean(false);
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    private final Lock connectedLock = new ReentrantLock();
    private final Condition connectedCondition = connectedLock.newCondition();
    private final Lock reconnectLock = new ReentrantLock();
    private final Condition reconnectCondition = reconnectLock.newCondition();

    private String host;
    private int port;
    private String localhost;
    private boolean includeCallerData;
    private AtomicReference<GELFCodec> codec = new AtomicReference<GELFCodec>();
    private int ttlSeconds;

    public GELFTCPAppender() {
    }

    @Override
    public void start() {
        codec.set(new GELFCodec(localhost, includeCallerData));
        if (isStarted.compareAndSet(false, true)) {
            Thread connectorThread = new Thread(new Connector(), "GELF-TCP-Connector");
            connectorThread.setDaemon(true);
            connectorThread.start();
            Thread senderThread = new Thread(new Sender(), "GELF-TCP-Sender");
            senderThread.setDaemon(true);
            senderThread.start();
            isRunning.set(true);
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
        events.add(e);
    }

    public void setGelfPort(int port) {
        this.port = port;
        reconnectLock.lock();
        try {
            reconnectCondition.signal();
        } finally {
            reconnectLock.unlock();
        }
    }

    public void setGelfHost(String host) {
        this.host = host;
        reconnectLock.lock();
        try {
            reconnectCondition.signal();
        } finally {
            reconnectLock.unlock();
        }
    }

    public void setLocalHost(String host) {
        this.localhost = host;
        if (isStarted.get()) {
            codec.set(new GELFCodec(localhost, includeCallerData));
        }
    }

    public void setIncludeCallerData(boolean includeCallerData) {
        this.includeCallerData = includeCallerData;
        if (isStarted.get()) {
            codec.set(new GELFCodec(localhost, includeCallerData));
        }
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
