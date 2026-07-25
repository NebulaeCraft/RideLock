package com.sobersquid.ridelock.curve;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SmoothTrackCurveTest {
    @Test
    public void fitsStraightTrackAndSamplesEveryFourBlocks() {
        List<Vector3> points = new ArrayList<>();
        for (int z = -128; z <= 128; z++) points.add(new Vector3(0.5, 64.5, z + 0.5));

        SmoothTrackCurve curve = SmoothTrackCurve.fit(points);
        Vector3 direction = curve.directionAt(new Vector3(0.5, 64.5, 0.5));
        assertEquals(0.0, direction.x, 1.0e-5);
        assertEquals(0.0, direction.y, 1.0e-5);
        assertTrue(direction.z > 0.9999);

        List<SmoothTrackCurve.TangentSample> samples = curve.tangentSamples();
        for (int index = 1; index < samples.size() - 1; index++) {
            assertEquals(SmoothTrackCurve.DEFAULT_TANGENT_SAMPLE_SPACING,
                    samples.get(index).arcLength - samples.get(index - 1).arcLength, 1.0e-7);
        }
    }

    @Test
    public void supportsConfigurableTangentSampleSpacing() {
        List<Vector3> points = new ArrayList<>();
        for (int z = -64; z <= 64; z++) points.add(new Vector3(0.5, 64.5, z + 0.5));

        List<SmoothTrackCurve.TangentSample> samples = SmoothTrackCurve.fit(points, 2.5)
                .tangentSamples();
        for (int index = 1; index < samples.size() - 1; index++) {
            assertEquals(2.5, samples.get(index).arcLength
                    - samples.get(index - 1).arcLength, 1.0e-7);
        }
    }

    @Test
    public void fitsThreeDimensionalSlope() {
        List<Vector3> points = new ArrayList<>();
        for (int z = -64; z <= 64; z++) points.add(new Vector3(10.5, 40.5 + z * 0.25, z + 0.5));

        Vector3 direction = SmoothTrackCurve.fit(points)
                .directionAt(new Vector3(10.5, 40.5, 0.5));
        assertEquals(0.0, direction.x, 1.0e-5);
        assertEquals(0.25, direction.y / direction.z, 1.0e-4);
        assertTrue(direction.z > 0.0);
    }

    @Test
    public void removesAlternatingHeadingFromRadiusTwoHundredVoxelCurve() {
        List<Vector3> points = voxelCircle(200.0, -0.65, 0.65);
        SmoothTrackCurve curve = SmoothTrackCurve.fit(points);
        List<SmoothTrackCurve.TangentSample> samples = curve.tangentSamples();

        Double previousYaw = null;
        int checked = 0;
        for (SmoothTrackCurve.TangentSample sample : samples) {
            if (sample.arcLength < 24.0 || sample.arcLength > curve.length() - 24.0) continue;
            double yaw = Math.toDegrees(Math.atan2(-sample.direction.x, sample.direction.z));
            if (previousYaw != null) {
                double delta = wrapDegrees(yaw - previousYaw);
                assertTrue("heading reversed by " + delta + " degrees", delta <= 0.15);
                assertTrue("heading step was too large: " + delta, delta > -3.0);
            }
            previousYaw = yaw;
            checked++;
        }
        assertTrue(checked > 40);
    }

    @Test
    public void roundsOrdinaryRightAngleWithoutFlippingDirection() {
        List<Vector3> points = new ArrayList<>();
        for (int x = -40; x <= 0; x++) points.add(new Vector3(x + 0.5, 64.5, 0.5));
        for (int z = 1; z <= 40; z++) points.add(new Vector3(0.5, 64.5, z + 0.5));

        SmoothTrackCurve curve = SmoothTrackCurve.fit(points);
        Double previousYaw = null;
        for (SmoothTrackCurve.TangentSample sample : curve.tangentSamples()) {
            if (sample.arcLength < 12.0 || sample.arcLength > curve.length() - 12.0) continue;
            double yaw = Math.toDegrees(Math.atan2(-sample.direction.x, sample.direction.z));
            if (previousYaw != null) {
                double delta = wrapDegrees(yaw - previousYaw);
                assertTrue("right-angle fit reversed by " + delta, delta >= -0.5);
                assertTrue("right-angle fit flipped", delta < 45.0);
            }
            previousYaw = yaw;
        }
    }

    @Test
    public void rejectsInsufficientContext() {
        List<Vector3> points = new ArrayList<>();
        for (int index = 0; index < 8; index++) points.add(new Vector3(index, 0.0, 0.0));
        try {
            SmoothTrackCurve.fit(points);
            fail("Expected the short path to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void reversingSampleOrderReversesTheTangent() {
        List<Vector3> points = new ArrayList<>();
        for (int z = -32; z <= 32; z++) points.add(new Vector3(0.5, 64.5, z + 0.5));
        Vector3 forward = SmoothTrackCurve.fit(points).directionAt(new Vector3(0.5, 64.5, 0.5));

        Collections.reverse(points);
        Vector3 backward = SmoothTrackCurve.fit(points).directionAt(new Vector3(0.5, 64.5, 0.5));
        assertTrue(forward.dot(backward) < -0.9999);
    }

    private static List<Vector3> voxelCircle(double radius, double startAngle, double endAngle) {
        List<Vector3> points = new ArrayList<>();
        int previousX = Integer.MIN_VALUE;
        int previousZ = Integer.MIN_VALUE;
        double angleStep = 0.2 / radius;
        for (double angle = startAngle; angle <= endAngle; angle += angleStep) {
            int x = (int) Math.round(Math.sin(angle) * radius);
            int z = (int) Math.round(Math.cos(angle) * radius);
            if (x != previousX || z != previousZ) {
                points.add(new Vector3(x + 0.5, 64.5, z + 0.5));
                previousX = x;
                previousZ = z;
            }
        }
        return points;
    }

    private static double wrapDegrees(double degrees) {
        degrees %= 360.0;
        if (degrees >= 180.0) degrees -= 360.0;
        if (degrees < -180.0) degrees += 360.0;
        return degrees;
    }
}
