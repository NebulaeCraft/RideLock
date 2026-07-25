package com.sobersquid.ridelock;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RideLockConfigTest {
    @After
    public void restoreDefaults() {
        RideLockConfig.trajectoryWindowSeconds = 1.5;
        RideLockConfig.sampleIntervalMilliseconds = 10;
    }

    @Test
    public void normalizesWindowToOneDecimalAndKeepsIntervalIntegral() {
        RideLockConfig.trajectoryWindowSeconds = 1.56;
        RideLockConfig.sampleIntervalMilliseconds = 17;

        RideLockConfig.normalize();

        assertEquals(1.6, RideLockConfig.trajectoryWindowSeconds, 0.0);
        assertEquals(17, RideLockConfig.sampleIntervalMilliseconds);
        assertEquals(1_600_000_000L, RideLockConfig.getWindowNanos());
        assertEquals(17_000_000L, RideLockConfig.getSampleIntervalNanos());
    }

    @Test
    public void clampsValuesToSupportedRanges() {
        RideLockConfig.trajectoryWindowSeconds = 20.0;
        RideLockConfig.sampleIntervalMilliseconds = 1;
        RideLockConfig.normalize();
        assertEquals(5.0, RideLockConfig.trajectoryWindowSeconds, 0.0);
        assertEquals(5, RideLockConfig.sampleIntervalMilliseconds);

        RideLockConfig.trajectoryWindowSeconds = 0.1;
        RideLockConfig.sampleIntervalMilliseconds = 100;
        RideLockConfig.normalize();
        assertEquals(0.5, RideLockConfig.trajectoryWindowSeconds, 0.0);
        assertEquals(50, RideLockConfig.sampleIntervalMilliseconds);
    }
}
