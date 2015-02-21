package org.metastatic.gelfback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.Comparator;

/**
 * Created by cmarshall on 2/20/15.
 */
public class LoggingEventLevelComparator implements Comparator<ILoggingEvent> {
    public static final LoggingEventLevelComparator INSTANCE = new LoggingEventLevelComparator();

    @Override
    public int compare(ILoggingEvent event1, ILoggingEvent event2) {
        Level level1 = event1.getLevel();
        Level level2 = event2.getLevel();
        if (level1.equals(level2))
            return 0;
        if (level1.isGreaterOrEqual(level2))
            return -1;
        return 1;
    }
}
