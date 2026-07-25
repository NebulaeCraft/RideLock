package com.sobersquid.ridelock;

/**
 * Keeps a previously valid curve through short-lived topology sampling gaps.
 * A fitted path covers up to 128 rails ahead and behind, so retaining it for a
 * few client ticks is safer than switching the camera to displacement fallback
 * for a single curved-rail boundary.
 */
final class TransientCurveFailureGuard {
    static final int MAX_CONSECUTIVE_FAILURE_TICKS = 10;

    private int consecutiveFailures;

    boolean recordFailure() {
        consecutiveFailures++;
        return consecutiveFailures > MAX_CONSECUTIVE_FAILURE_TICKS;
    }

    void recordSuccess() {
        consecutiveFailures = 0;
    }

    void reset() {
        consecutiveFailures = 0;
    }

    int consecutiveFailures() {
        return consecutiveFailures;
    }
}
