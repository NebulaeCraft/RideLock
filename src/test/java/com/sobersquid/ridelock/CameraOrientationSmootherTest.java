package com.sobersquid.ridelock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CameraOrientationSmootherTest {
    @Test
    public void initialLockUsesTheConfiguredTransition() {
        CameraOrientationSmoother smoother = new CameraOrientationSmoother();

        assertEquals(0.0f, smoother.update(20.0f, 10.0f, 0.0f, 0.0f, 0L).yaw, 1.0e-5f);
        CameraOrientationSmoother.Orientation halfway = smoother.update(
                20.0f, 10.0f, 0.0f, 0.0f, CameraOrientationSmoother.TRANSITION_NANOS / 2);
        assertEquals(10.0f, halfway.yaw, 1.0e-5f);
        assertEquals(5.0f, halfway.pitch, 1.0e-5f);
        CameraOrientationSmoother.Orientation finished = smoother.update(
                20.0f, 10.0f, 0.0f, 0.0f, CameraOrientationSmoother.TRANSITION_NANOS);
        assertEquals(20.0f, finished.yaw, 1.0e-5f);
        assertEquals(10.0f, finished.pitch, 1.0e-5f);
        assertFalse(smoother.isTransitioning());
    }

    @Test
    public void routineTargetUpdatesDoNotRestartAnActiveTransition() {
        CameraOrientationSmoother smoother = new CameraOrientationSmoother();
        smoother.update(10.0f, 0.0f, 0.0f, 0.0f, 0L);
        smoother.update(11.0f, 0.0f, 0.0f, 0.0f, 100_000_000L);
        smoother.update(12.0f, 0.0f, 0.0f, 0.0f, 200_000_000L);

        CameraOrientationSmoother.Orientation finished = smoother.update(
                13.0f, 0.0f, 0.0f, 0.0f, 250_000_000L);
        assertEquals(13.0f, finished.yaw, 1.0e-5f);
        assertFalse(smoother.isTransitioning());

        CameraOrientationSmoother.Orientation following = smoother.update(
                14.0f, 0.0f, 0.0f, 0.0f, 260_000_000L);
        assertEquals(14.0f, following.yaw, 1.0e-5f);
    }

    @Test
    public void trueDirectionReversalStartsAProtectiveTransitionAfterConfirmation() {
        CameraOrientationSmoother smoother = settledAtZero();

        CameraOrientationSmoother.Orientation pending = smoother.update(
                179.0f, 0.0f, 0.0f, 0.0f, 300_000_000L);
        assertEquals(0.0f, pending.yaw, 1.0e-5f);
        assertFalse(smoother.isTransitioning());
        assertTrue(smoother.hasPendingDiscontinuity());

        CameraOrientationSmoother.Orientation start = smoother.update(
                179.0f, 0.0f, 0.0f, 0.0f,
                300_000_000L + CameraOrientationSmoother.DISCONTINUITY_CONFIRMATION_NANOS);
        assertEquals(0.0f, start.yaw, 1.0e-5f);
        assertTrue(smoother.isTransitioning());
        assertFalse(smoother.hasPendingDiscontinuity());

        CameraOrientationSmoother.Orientation halfway = smoother.update(
                179.0f, 0.0f, 0.0f, 0.0f,
                300_000_000L + CameraOrientationSmoother.DISCONTINUITY_CONFIRMATION_NANOS
                        + CameraOrientationSmoother.TRANSITION_NANOS / 2);
        assertEquals(89.5f, halfway.yaw, 1.0e-4f);
    }

    @Test
    public void transientCurveBoundaryReversalNeverMovesTheCamera() {
        CameraOrientationSmoother smoother = settledAtZero();

        assertEquals(0.0f, smoother.update(
                179.0f, 0.0f, 0.0f, 0.0f, 300_000_000L).yaw, 1.0e-5f);
        assertEquals(0.0f, smoother.update(
                179.0f, 0.0f, 0.0f, 0.0f, 340_000_000L).yaw, 1.0e-5f);
        assertEquals(0.0f, smoother.update(
                0.0f, 0.0f, 0.0f, 0.0f, 350_000_000L).yaw, 1.0e-5f);
        assertFalse(smoother.isTransitioning());
        assertFalse(smoother.hasPendingDiscontinuity());
    }

    @Test
    public void yawWrapDoesNotLookLikeADiscontinuity() {
        CameraOrientationSmoother smoother = new CameraOrientationSmoother();
        smoother.update(179.0f, 0.0f, 179.0f, 0.0f, 0L);
        smoother.update(179.0f, 0.0f, 179.0f, 0.0f,
                CameraOrientationSmoother.TRANSITION_NANOS);

        CameraOrientationSmoother.Orientation wrapped = smoother.update(
                -179.0f, 0.0f, 179.0f, 0.0f, 300_000_000L);
        assertEquals(-179.0f, wrapped.yaw, 1.0e-5f);
        assertFalse(smoother.isTransitioning());
    }

    @Test
    public void angleInterpolationUsesTheShortPathAcrossWrap() {
        assertEquals(180.0f,
                CameraOrientationSmoother.interpolateAngle(179.0f, -179.0f, 0.5f), 1.0e-5f);
        assertEquals(-180.0f,
                CameraOrientationSmoother.interpolateAngle(-179.0f, 179.0f, 0.5f), 1.0e-5f);
    }

    private static CameraOrientationSmoother settledAtZero() {
        CameraOrientationSmoother smoother = new CameraOrientationSmoother();
        smoother.update(0.0f, 0.0f, 0.0f, 0.0f, 0L);
        smoother.update(0.0f, 0.0f, 0.0f, 0.0f,
                CameraOrientationSmoother.TRANSITION_NANOS);
        return smoother;
    }
}
