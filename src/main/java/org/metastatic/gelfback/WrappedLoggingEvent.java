package org.metastatic.gelfback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import org.slf4j.Marker;

import java.util.Map;

/**
 * Wrapper for ILoggingEvent; since messages are formatted off the thread that logs
 * the message, stash the info specific to the thread in this object.
 */
class WrappedLoggingEvent implements ILoggingEvent {
    private final ILoggingEvent delegate;
    private final String threadName;
    private final StackTraceElement[] callerData;

    public WrappedLoggingEvent(ILoggingEvent delegate, boolean includeCallerData) {
        this.delegate = delegate;
        this.threadName = delegate.getThreadName();
        if (includeCallerData)
            this.callerData = delegate.getCallerData();
        else
            this.callerData = new StackTraceElement[0];
    }

    public String getThreadName() {
        return threadName;
    }

    public Level getLevel() {
        return delegate.getLevel();
    }

    public String getMessage() {
        return delegate.getMessage();
    }

    public Object[] getArgumentArray() {
        return delegate.getArgumentArray();
    }

    public String getFormattedMessage() {
        return delegate.getFormattedMessage();
    }

    public String getLoggerName() {
        return delegate.getLoggerName();
    }

    public LoggerContextVO getLoggerContextVO() {
        return delegate.getLoggerContextVO();
    }

    public IThrowableProxy getThrowableProxy() {
        return delegate.getThrowableProxy();
    }

    public StackTraceElement[] getCallerData() {
        return callerData;
    }

    public boolean hasCallerData() {
        return callerData.length > 0;
    }

    public Marker getMarker() {
        return delegate.getMarker();
    }

    public Map<String, String> getMDCPropertyMap() {
        return delegate.getMDCPropertyMap();
    }

    public Map<String, String> getMdc() {
        return delegate.getMdc();
    }

    public long getTimeStamp() {
        return delegate.getTimeStamp();
    }

    public void prepareForDeferredProcessing() {
        delegate.prepareForDeferredProcessing();
    }
}
