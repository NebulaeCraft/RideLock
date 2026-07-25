package com.sobersquid.ridelock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrajectoryFitterTest {
    private static final long MILLIS = 1_000_000L;

    @Test
    public void fitsStraightDiagonalAndSlopeWithIrregularFrames() {
        TrajectoryFitter fitter = new TrajectoryFitter();
        TrajectoryFitter.FitResult result = null;
        long time = 0L;
        int[] frameDurations = {13, 17, 22, 11, 29, 16, 19};
        int frame = 0;

        while (time <= 1_800L * MILLIS) {
            double seconds = time * 1.0E-9;
            result = fitter.update(time, 2.0 * seconds, 0.5 * seconds, 4.0 * seconds);
            time += frameDurations[frame++ % frameDurations.length] * MILLIS;
        }

        assertTrue(result.valid);
        assertEquals(2.0, result.vx, 1.0E-6);
        assertEquals(0.5, result.vy, 1.0E-6);
        assertEquals(4.0, result.vz, 1.0E-6);
    }

    @Test
    public void recoversEndpointDerivativeOfQuadraticCurve() {
        TrajectoryFitter fitter = new TrajectoryFitter();
        TrajectoryFitter.FitResult result = null;
        double endpointSeconds = 2.0;

        for (int millis = 0; millis <= 2_000; millis += 20) {
            double t = millis / 1000.0;
            double x = 1.5 * t + 0.4 * t * t;
            double y = -0.2 * t + 0.1 * t * t;
            double z = 3.0 * t - 0.3 * t * t;
            result = fitter.update(millis * MILLIS, x, y, z);
        }

        assertTrue(result.valid);
        assertEquals(1.5 + 0.8 * endpointSeconds, result.vx, 1.0E-6);
        assertEquals(-0.2 + 0.2 * endpointSeconds, result.vy, 1.0E-6);
        assertEquals(3.0 - 0.6 * endpointSeconds, result.vz, 1.0E-6);
    }

    @Test
    public void latestRasterStepDoesNotConstrainTheCurveEndpoint() {
        TrajectoryFitter fitter = new TrajectoryFitter();
        TrajectoryFitter.FitResult result = null;

        for (int millis = 0; millis < 1_500; millis += 10) {
            result = fitter.update(millis * MILLIS, 0.0, 0.0, millis * 0.008);
        }
        // Model the live end landing partway through a sideways voxel step.
        result = fitter.update(1_500L * MILLIS, 0.5, 0.0, 12.0);

        assertTrue(result.valid);
        assertEquals(8.0, result.vz, 0.05);
        assertTrue("a single endpoint step must not pull the tangent sideways",
                Math.abs(result.vx) < 0.1);
    }

    @Test
    public void remainsStableAtLargeWorldCoordinates() {
        TrajectoryFitter fitter = new TrajectoryFitter();
        TrajectoryFitter.FitResult result = null;

        for (int millis = 0; millis <= 1_500; millis += 10) {
            double seconds = millis / 1000.0;
            result = fitter.update(millis * MILLIS,
                    29_999_000.0 + 3.0 * seconds,
                    200.0 + 0.25 * seconds,
                    -29_999_000.0 + 7.0 * seconds);
        }

        assertTrue(result.valid);
        assertEquals(3.0, result.vx, 1.0E-6);
        assertEquals(0.25, result.vy, 1.0E-6);
        assertEquals(7.0, result.vz, 1.0E-6);
    }

    @Test
    public void keepsOnlyTheFixedOnePointFiveSecondWindow() {
        TrajectoryFitter fitter = new TrajectoryFitter();

        for (int millis = 0; millis <= 3_000; millis += 10) {
            double seconds = millis / 1000.0;
            fitter.update(millis * MILLIS, 8.0 * seconds, 0.0, 0.0);
        }

        assertEquals(TrajectoryFitter.DEFAULT_WINDOW_NANOS, fitter.getSampleSpanNanos());
        assertTrue(fitter.getSampleCount() <= TrajectoryFitter.MAX_SAMPLES);
        assertEquals(12.0, 8.0 * fitter.getSampleSpanNanos() * 1.0E-9, 1.0E-9);
    }

    @Test
    public void hotConfigurationClearsHistoryAndUsesNewDurations() {
        TrajectoryFitter fitter = new TrajectoryFitter();
        for (int millis = 0; millis <= 1_000; millis += 10) {
            fitter.update(millis * MILLIS, millis * 0.008, 0.0, 0.0);
        }
        assertTrue(fitter.getSampleCount() > 1);

        assertTrue(fitter.configure(800L * MILLIS, 20L * MILLIS));
        assertEquals(0, fitter.getSampleCount());
        assertFalse(fitter.configure(800L * MILLIS, 20L * MILLIS));

        for (int millis = 0; millis <= 1_200; millis += 10) {
            fitter.update(2_000L * MILLIS + millis * MILLIS, millis * 0.008, 0.0, 0.0);
        }
        assertEquals(800L * MILLIS, fitter.getSampleSpanNanos());
        assertTrue(fitter.getSampleCount() <= 42);
    }

    @Test
    public void smoothsVoxelizedRadiusTwoHundredCurve() {
        TrajectoryFitter fitter = new TrajectoryFitter();
        List<Double> fittedYaw = new ArrayList<>();
        List<Double> idealYaw = new ArrayList<>();
        double radius = 200.0;
        double speed = 8.0;

        for (int millis = 0; millis <= 5_000; millis += 10) {
            double seconds = millis / 1000.0;
            double angle = Math.PI / 4.0 + speed * seconds / radius;
            int tickStartMillis = millis - millis % 50;
            double tickFraction = (millis - tickStartMillis) / 50.0;
            double tickStartSeconds = tickStartMillis / 1000.0;
            double tickEndSeconds = tickStartSeconds + 0.05;
            double startAngle = Math.PI / 4.0 + speed * tickStartSeconds / radius;
            double endAngle = Math.PI / 4.0 + speed * tickEndSeconds / radius;
            double startX = Math.rint(radius * Math.cos(startAngle) * 2.0) / 2.0;
            double startZ = Math.rint(radius * Math.sin(startAngle) * 2.0) / 2.0;
            double endX = Math.rint(radius * Math.cos(endAngle) * 2.0) / 2.0;
            double endZ = Math.rint(radius * Math.sin(endAngle) * 2.0) / 2.0;
            double voxelX = startX + (endX - startX) * tickFraction;
            double voxelZ = startZ + (endZ - startZ) * tickFraction;
            TrajectoryFitter.FitResult result = fitter.update(millis * MILLIS, voxelX, 0.0, voxelZ);
            if (result.valid && millis >= 1_500) {
                fittedYaw.add(yaw(result.vx, result.vz));
                double idealVx = -speed * Math.sin(angle);
                double idealVz = speed * Math.cos(angle);
                idealYaw.add(yaw(idealVx, idealVz));
            }
        }

        assertFalse(fittedYaw.isEmpty());
        double maxStep = 0.0;
        double maxError = 0.0;
        for (int i = 0; i < fittedYaw.size(); i++) {
            maxError = Math.max(maxError, Math.abs(wrapDegrees(fittedYaw.get(i) - idealYaw.get(i))));
            if (i > 0) {
                maxStep = Math.max(maxStep, Math.abs(wrapDegrees(fittedYaw.get(i) - fittedYaw.get(i - 1))));
            }
        }
        assertTrue("fitted tangent should stay near the radius-200 circle tangent", maxError < 8.0);
        assertTrue("fitted yaw should not alternate with voxel steps", maxStep < 5.0);
    }

    @Test
    public void followsTurnoutWithoutDirectionFlips() {
        TrajectoryFitter fitter = new TrajectoryFitter();
        List<Double> yaws = new ArrayList<>();

        for (int millis = 0; millis <= 3_000; millis += 10) {
            double seconds = millis / 1000.0;
            double x;
            double z;
            if (seconds <= 1.0) {
                x = 0.0;
                z = 8.0 * seconds;
            } else if (seconds <= 1.25) {
                double turnTime = seconds - 1.0;
                x = 8.0 * turnTime * turnTime;
                z = 8.0 + 8.0 * turnTime - 8.0 * turnTime * turnTime;
            } else {
                x = 0.5 + 8.0 * (seconds - 1.25);
                z = 9.5;
            }
            TrajectoryFitter.FitResult result = fitter.update(millis * MILLIS, x, 0.0, z);
            if (result.valid) {
                yaws.add(yaw(result.vx, result.vz));
            }
        }

        assertFalse(yaws.isEmpty());
        double previous = yaws.get(0);
        for (double current : yaws) {
            assertTrue("turnout fit must never reverse the camera", Math.abs(wrapDegrees(current - previous)) < 45.0);
            previous = current;
        }
        assertTrue("turnout tangent should settle onto the branch", Math.abs(wrapDegrees(previous + 90.0)) < 8.0);
    }

    @Test
    public void holdsAtRestAndResetsForTeleportTimeGapAndReverse() {
        TrajectoryFitter fitter = new TrajectoryFitter();
        TrajectoryFitter.FitResult moving = null;
        for (int millis = 0; millis <= 1_500; millis += 20) {
            moving = fitter.update(millis * MILLIS, 0.0, 0.0, millis * 0.008);
        }
        assertTrue(moving.valid);

        TrajectoryFitter.FitResult stopped = moving;
        for (int millis = 1_520; millis <= 2_000; millis += 20) {
            stopped = fitter.update(millis * MILLIS, 0.0, 0.0, 12.0);
        }
        assertTrue(stopped.valid);
        assertEquals(yaw(moving.vx, moving.vz), yaw(stopped.vx, stopped.vz), 1.0E-9);

        TrajectoryFitter.FitResult teleported = fitter.update(2_020L * MILLIS, 100.0, 0.0, 100.0);
        assertFalse(teleported.valid);
        assertTrue(fitter.consumeDiscontinuityReset());
        assertFalse(fitter.consumeDiscontinuityReset());
        assertEquals(1, fitter.getSampleCount());

        TrajectoryFitter.FitResult afterGap = fitter.update(3_000L * MILLIS, 100.0, 0.0, 100.0);
        assertFalse(afterGap.valid);
        assertTrue(fitter.consumeDiscontinuityReset());
        assertEquals(1, fitter.getSampleCount());

        TrajectoryFitter reverseFitter = new TrajectoryFitter();
        for (int millis = 0; millis <= 1_500; millis += 20) {
            reverseFitter.update(millis * MILLIS, 0.0, 0.0, millis * 0.008);
        }
        reverseFitter.update(1_520L * MILLIS, 0.0, 0.0, 11.84);
        TrajectoryFitter.FitResult reversed = reverseFitter.update(1_540L * MILLIS, 0.0, 0.0, 11.68);
        assertFalse(reversed.valid);
        assertEquals(1, reverseFitter.getSampleCount());
    }

    @Test
    public void waitsForEnoughSamplesAndHandlesDuplicatePositions() {
        TrajectoryFitter fitter = new TrajectoryFitter();
        assertFalse(fitter.update(0L, 1.0, 2.0, 3.0).valid);
        assertFalse(fitter.update(20L * MILLIS, 1.0, 2.0, 3.0).valid);
        assertFalse(fitter.update(40L * MILLIS, 1.0, 2.0, 3.0).valid);
        assertFalse(fitter.update(60L * MILLIS, 1.0, 2.0, 3.0).valid);
        assertFalse(fitter.update(120L * MILLIS, 1.0, 2.0, 3.0).valid);
    }

    private static double yaw(double vx, double vz) {
        return Math.toDegrees(Math.atan2(-vx, vz));
    }

    private static double wrapDegrees(double angle) {
        angle %= 360.0;
        if (angle >= 180.0) {
            angle -= 360.0;
        }
        if (angle < -180.0) {
            angle += 360.0;
        }
        return angle;
    }
}
