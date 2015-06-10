package org.metastatic.gelfback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by cmarshall on 6/10/15.
 */
public class GELFBasicIT {
    Logger logger;

    @Before
    public void setup() {
        logger = LoggerFactory.getLogger(GELFBasicIT.class.getName());
    }

    @Test
    public void testBasicLogging() {
        logger.info("this is a test message from gelfback");
    }

    @Test
    public void testExceptionLogging() {
        try {
            throw new IllegalArgumentException("nope!");
        } catch (IllegalArgumentException iae) {
            logger.warn("caught an exception", iae);
        }
    }

    @After
    public void teardown() {
        // wait for stuff to get sent
        int count = 0;
        while (!GELFTCPAppender.drained() && count < 500) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
            count++;
        }
        if (!GELFTCPAppender.drained()) {
            System.err.println("WARNING: gelf queue not drained; messages were probably not sent");
        }
    }
}
