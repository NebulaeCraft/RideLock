package com.sobersquid.ridelock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Fits a time-parametrized curve through recent rider positions. The fitted
 * curve is not constrained to pass through the live position, so a block-step
 * at the end of a rasterized track cannot directly pull its tangent sideways.
 * Its derivative at the live sample time describes the direction of travel.
 */
public final class TrajectoryFitter {
    static final long DEFAULT_WINDOW_NANOS = 1_500_000_000L;
    static final long DEFAULT_SAMPLE_INTERVAL_NANOS = 10_000_000L;
    static final long MIN_FIT_SPAN_NANOS = 120_000_000L;
    static final int MIN_FIT_SAMPLES = 5;
    static final int MAX_SAMPLES = 1_024;

    private static final long RECENT_MOVEMENT_NANOS = 80_000_000L;
    private static final long MAX_OBSERVATION_GAP_NANOS = 500_000_000L;
    private static final double MIN_JUMP_DISTANCE = 2.0;
    private static final double MAX_PLAUSIBLE_SPEED = 40.0;
    private static final double MIN_STEP_DISTANCE_SQ = 1.0E-8;
    private static final double MIN_RECENT_MOVEMENT_SQ = 1.0E-6;
    private static final double MIN_HORIZONTAL_SPEED_SQ = 4.0E-4;
    private static final double REVERSE_COSINE_THRESHOLD = -0.5;
    private static final int REVERSE_SAMPLES_TO_RESET = 2;
    private static final double SOLVER_EPSILON = 1.0E-12;

    private final Deque<Sample> samples = new ArrayDeque<>();
    private long windowNanos = DEFAULT_WINDOW_NANOS;
    private long sampleIntervalNanos = DEFAULT_SAMPLE_INTERVAL_NANOS;
    private Sample lastObservation;
    private FitResult lastValidResult;
    private int reverseEvidence;
    private boolean discontinuityReset;

    /**
     * Adds the live rider position and returns the fitted endpoint tangent.
     * Invalid results mean that the new sampling window is not ready yet.
     */
    public FitResult update(long timeNanos, double x, double y, double z) {
        Sample current = new Sample(timeNanos, x, y, z);

        if (lastObservation != null) {
            long elapsed = timeNanos - lastObservation.timeNanos;
            double maxStepDistance = Math.max(MIN_JUMP_DISTANCE, elapsed * 1.0E-9 * MAX_PLAUSIBLE_SPEED);
            if (elapsed <= 0L || elapsed > MAX_OBSERVATION_GAP_NANOS
                    || distanceSq(current, lastObservation) > maxStepDistance * maxStepDistance) {
                reset();
                discontinuityReset = true;
            }
        }

        Sample lastStored = samples.peekLast();
        boolean shouldStore = lastStored == null
                || timeNanos - lastStored.timeNanos >= sampleIntervalNanos;

        if (shouldStore && lastStored != null && lastValidResult != null) {
            double dx = x - lastStored.x;
            double dz = z - lastStored.z;
            double stepLengthSq = dx * dx + dz * dz;
            if (stepLengthSq > MIN_STEP_DISTANCE_SQ) {
                double tangentLength = Math.sqrt(lastValidResult.vx * lastValidResult.vx
                        + lastValidResult.vz * lastValidResult.vz);
                double cosine = (dx * lastValidResult.vx + dz * lastValidResult.vz)
                        / (Math.sqrt(stepLengthSq) * tangentLength);
                if (cosine < REVERSE_COSINE_THRESHOLD) {
                    reverseEvidence++;
                    if (reverseEvidence >= REVERSE_SAMPLES_TO_RESET) {
                        reset();
                        lastStored = null;
                    }
                } else {
                    reverseEvidence = 0;
                }
            }
        }

        if (shouldStore) {
            samples.addLast(current);
            while (samples.size() > MAX_SAMPLES) {
                samples.removeFirst();
            }
        }

        lastObservation = current;
        prune(timeNanos);

        if (lastValidResult != null && !hasRecentHorizontalMovement(current)) {
            return lastValidResult;
        }

        FitResult result = fit(current);
        if (result.valid && result.horizontalSpeedSq() >= MIN_HORIZONTAL_SPEED_SQ) {
            lastValidResult = result;
            return result;
        }
        return lastValidResult != null ? lastValidResult : FitResult.invalid();
    }

    public void reset() {
        samples.clear();
        lastObservation = null;
        lastValidResult = null;
        reverseEvidence = 0;
        discontinuityReset = false;
    }

    /** Applies new sampling settings and clears history when either value changes. */
    public boolean configure(long newWindowNanos, long newSampleIntervalNanos) {
        if (newWindowNanos <= 0L || newSampleIntervalNanos <= 0L) {
            throw new IllegalArgumentException("Sampling durations must be positive");
        }
        if (windowNanos == newWindowNanos && sampleIntervalNanos == newSampleIntervalNanos) {
            return false;
        }
        windowNanos = newWindowNanos;
        sampleIntervalNanos = newSampleIntervalNanos;
        reset();
        return true;
    }

    /** Returns and clears the notification for a clock gap or position jump. */
    public boolean consumeDiscontinuityReset() {
        boolean result = discontinuityReset;
        discontinuityReset = false;
        return result;
    }

    int getSampleCount() {
        return samples.size();
    }

    long getSampleSpanNanos() {
        Sample first = samples.peekFirst();
        Sample last = samples.peekLast();
        return first == null || last == null ? 0L : last.timeNanos - first.timeNanos;
    }

    private void prune(long currentTimeNanos) {
        long cutoff = currentTimeNanos - windowNanos;
        while (!samples.isEmpty() && samples.peekFirst().timeNanos < cutoff) {
            samples.removeFirst();
        }
    }

    private boolean hasRecentHorizontalMovement(Sample current) {
        long targetTime = current.timeNanos - RECENT_MOVEMENT_NANOS;
        Sample comparison = null;
        for (Sample sample : samples) {
            if (sample.timeNanos <= targetTime) {
                comparison = sample;
            } else {
                break;
            }
        }
        if (comparison == null) {
            return true;
        }
        double dx = current.x - comparison.x;
        double dz = current.z - comparison.z;
        return dx * dx + dz * dz >= MIN_RECENT_MOVEMENT_SQ;
    }

    private FitResult fit(Sample endpoint) {
        List<Sample> points = new ArrayList<>(samples);
        Sample storedEndpoint = points.isEmpty() ? null : points.get(points.size() - 1);
        if (storedEndpoint == null || storedEndpoint.timeNanos != endpoint.timeNanos) {
            points.add(endpoint);
        }

        if (points.size() < MIN_FIT_SAMPLES
                || endpoint.timeNanos - points.get(0).timeNanos < MIN_FIT_SPAN_NANOS) {
            return FitResult.invalid();
        }

        double s0 = 0.0;
        double s1 = 0.0;
        double s2 = 0.0;
        double s3 = 0.0;
        double s4 = 0.0;
        double bx0 = 0.0;
        double bx1 = 0.0;
        double bx2 = 0.0;
        double by0 = 0.0;
        double by1 = 0.0;
        double by2 = 0.0;
        double bz0 = 0.0;
        double bz1 = 0.0;
        double bz2 = 0.0;

        for (int i = 0; i < points.size(); i++) {
            Sample point = points.get(i);
            double time = (point.timeNanos - endpoint.timeNanos) * 1.0E-9;
            double weight = coverageWeightSeconds(points, i);
            double time2 = time * time;
            double weightedTime = weight * time;
            double weightedTime2 = weight * time2;

            s0 += weight;
            s1 += weightedTime;
            s2 += weightedTime2;
            s3 += weightedTime2 * time;
            s4 += weightedTime2 * time2;

            // Translate the coordinates for numeric stability at large world
            // positions. The fitted constant remains free, so this does not
            // constrain the curve to pass through the endpoint.
            double dx = point.x - endpoint.x;
            double dy = point.y - endpoint.y;
            double dz = point.z - endpoint.z;
            bx0 += weight * dx;
            bx1 += weightedTime * dx;
            bx2 += weightedTime2 * dx;
            by0 += weight * dy;
            by1 += weightedTime * dy;
            by2 += weightedTime2 * dy;
            bz0 += weight * dz;
            bz1 += weightedTime * dz;
            bz2 += weightedTime2 * dz;
        }

        // Normal matrix for p(t) = c + v*t + a*t^2. Since t=0 is the
        // current observation time, the middle coefficient is its tangent.
        double cofactor01 = s2 * s3 - s1 * s4;
        double cofactor11 = s0 * s4 - s2 * s2;
        double cofactor12 = s1 * s2 - s0 * s3;
        double determinant = s0 * (s2 * s4 - s3 * s3)
                + s1 * cofactor01
                + s2 * (s1 * s3 - s2 * s2);
        if (Math.abs(determinant) > SOLVER_EPSILON) {
            double vx = (cofactor01 * bx0 + cofactor11 * bx1 + cofactor12 * bx2) / determinant;
            double vy = (cofactor01 * by0 + cofactor11 * by1 + cofactor12 * by2) / determinant;
            double vz = (cofactor01 * bz0 + cofactor11 * bz1 + cofactor12 * bz2) / determinant;
            if (isFinite(vx) && isFinite(vy) && isFinite(vz)) {
                return FitResult.valid(vx, vy, vz);
            }
        }

        // Degenerate quadratic fits fall back to an unconstrained line.
        double linearDeterminant = s0 * s2 - s1 * s1;
        if (Math.abs(linearDeterminant) > SOLVER_EPSILON) {
            double vx = (s0 * bx1 - s1 * bx0) / linearDeterminant;
            double vy = (s0 * by1 - s1 * by0) / linearDeterminant;
            double vz = (s0 * bz1 - s1 * bz0) / linearDeterminant;
            if (isFinite(vx) && isFinite(vy) && isFinite(vz)) {
                return FitResult.valid(vx, vy, vz);
            }
        }
        return FitResult.invalid();
    }

    private static double coverageWeightSeconds(List<Sample> points, int index) {
        if (points.size() == 1) {
            return 1.0;
        }
        long previousInterval = index == 0
                ? points.get(1).timeNanos - points.get(0).timeNanos
                : points.get(index).timeNanos - points.get(index - 1).timeNanos;
        long nextInterval = index == points.size() - 1
                ? points.get(index).timeNanos - points.get(index - 1).timeNanos
                : points.get(index + 1).timeNanos - points.get(index).timeNanos;
        return (previousInterval + nextInterval) * 0.5E-9;
    }

    private static double distanceSq(Sample first, Sample second) {
        double dx = first.x - second.x;
        double dy = first.y - second.y;
        double dz = first.z - second.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class Sample {
        private final long timeNanos;
        private final double x;
        private final double y;
        private final double z;

        private Sample(long timeNanos, double x, double y, double z) {
            this.timeNanos = timeNanos;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class FitResult {
        private static final FitResult INVALID = new FitResult(false, 0.0, 0.0, 0.0);

        public final boolean valid;
        public final double vx;
        public final double vy;
        public final double vz;

        private FitResult(boolean valid, double vx, double vy, double vz) {
            this.valid = valid;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
        }

        private static FitResult invalid() {
            return INVALID;
        }

        private static FitResult valid(double vx, double vy, double vz) {
            return new FitResult(true, vx, vy, vz);
        }

        private double horizontalSpeedSq() {
            return vx * vx + vz * vz;
        }
    }
}
