package com.sobersquid.ridelock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransientCurveFailureGuardTest {
    @Test
    public void retainsCurveAcrossShortDoubleTurnSamplingGap() {
        TransientCurveFailureGuard guard = new TransientCurveFailureGuard();

        for (int tick = 0; tick < TransientCurveFailureGuard.MAX_CONSECUTIVE_FAILURE_TICKS; tick++) {
            assertFalse(guard.recordFailure());
        }
        assertEquals(TransientCurveFailureGuard.MAX_CONSECUTIVE_FAILURE_TICKS,
                guard.consecutiveFailures());
    }

    @Test
    public void sustainedFailureEventuallyAllowsFallback() {
        TransientCurveFailureGuard guard = new TransientCurveFailureGuard();

        for (int tick = 0; tick < TransientCurveFailureGuard.MAX_CONSECUTIVE_FAILURE_TICKS; tick++) {
            guard.recordFailure();
        }
        assertTrue(guard.recordFailure());
    }

    @Test
    public void validSampleResetsTheFailureWindow() {
        TransientCurveFailureGuard guard = new TransientCurveFailureGuard();

        guard.recordFailure();
        guard.recordFailure();
        guard.recordSuccess();

        assertEquals(0, guard.consecutiveFailures());
        assertFalse(guard.recordFailure());
    }
}
