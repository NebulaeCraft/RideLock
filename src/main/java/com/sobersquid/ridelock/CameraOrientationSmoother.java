package com.sobersquid.ridelock;

/**
 * Smooths initial lock-on and genuinely discontinuous direction changes.
 * Routine curve refreshes do not restart the transition.
 */
final class CameraOrientationSmoother {
    static final long TRANSITION_NANOS = 250_000_000L;
    static final long DISCONTINUITY_CONFIRMATION_NANOS = 75_000_000L;
    static final float YAW_DISCONTINUITY_DEGREES = 45.0f;
    static final float PITCH_DISCONTINUITY_DEGREES = 20.0f;
    private static final float PENDING_YAW_TOLERANCE_DEGREES = 15.0f;
    private static final float PENDING_PITCH_TOLERANCE_DEGREES = 8.0f;

    private boolean initialized;
    private boolean transitioning;
    private long transitionStartNanos;
    private float transitionStartYaw;
    private float transitionStartPitch;
    private float acceptedTargetYaw;
    private float acceptedTargetPitch;
    private boolean hasPendingDiscontinuity;
    private long pendingSinceNanos;
    private float pendingTargetYaw;
    private float pendingTargetPitch;
    private float yaw;
    private float pitch;

    Orientation update(float targetYaw, float targetPitch, float currentYaw,
                       float currentPitch, long nowNanos) {
        if (!initialized) {
            transitionStartYaw = currentYaw;
            transitionStartPitch = currentPitch;
            transitionStartNanos = nowNanos;
            transitioning = true;
            initialized = true;
            acceptedTargetYaw = targetYaw;
            acceptedTargetPitch = targetPitch;
        } else if (isDiscontinuous(targetYaw, targetPitch)) {
            updatePendingDiscontinuity(targetYaw, targetPitch, nowNanos);
        } else {
            clearPendingDiscontinuity();
            acceptedTargetYaw = targetYaw;
            acceptedTargetPitch = targetPitch;
        }

        if (transitioning) {
            float progress = clamp((float) ((nowNanos - transitionStartNanos)
                    / (double) TRANSITION_NANOS), 0.0f, 1.0f);
            float smoothProgress = progress * progress * (3.0f - 2.0f * progress);
            yaw = interpolateAngle(transitionStartYaw, acceptedTargetYaw, smoothProgress);
            pitch = transitionStartPitch
                    + (acceptedTargetPitch - transitionStartPitch) * smoothProgress;
            if (progress >= 1.0f) transitioning = false;
        } else {
            yaw = acceptedTargetYaw;
            pitch = acceptedTargetPitch;
        }
        return new Orientation(yaw, pitch);
    }

    private boolean isDiscontinuous(float targetYaw, float targetPitch) {
        return Math.abs(wrapDegrees(targetYaw - acceptedTargetYaw)) >= YAW_DISCONTINUITY_DEGREES
                || Math.abs(targetPitch - acceptedTargetPitch) >= PITCH_DISCONTINUITY_DEGREES;
    }

    private void updatePendingDiscontinuity(float targetYaw, float targetPitch, long nowNanos) {
        boolean matchesPending = hasPendingDiscontinuity
                && Math.abs(wrapDegrees(targetYaw - pendingTargetYaw))
                <= PENDING_YAW_TOLERANCE_DEGREES
                && Math.abs(targetPitch - pendingTargetPitch)
                <= PENDING_PITCH_TOLERANCE_DEGREES;
        if (!matchesPending) {
            hasPendingDiscontinuity = true;
            pendingSinceNanos = nowNanos;
            pendingTargetYaw = targetYaw;
            pendingTargetPitch = targetPitch;
            return;
        }

        pendingTargetYaw = targetYaw;
        pendingTargetPitch = targetPitch;
        if (nowNanos - pendingSinceNanos < DISCONTINUITY_CONFIRMATION_NANOS) return;

        transitionStartYaw = yaw;
        transitionStartPitch = pitch;
        transitionStartNanos = nowNanos;
        transitioning = true;
        acceptedTargetYaw = targetYaw;
        acceptedTargetPitch = targetPitch;
        clearPendingDiscontinuity();
    }

    private void clearPendingDiscontinuity() {
        hasPendingDiscontinuity = false;
    }

    void reset() {
        initialized = false;
        transitioning = false;
        clearPendingDiscontinuity();
    }

    boolean isTransitioning() {
        return transitioning;
    }

    boolean hasPendingDiscontinuity() {
        return hasPendingDiscontinuity;
    }

    static float interpolateAngle(float from, float to, float amount) {
        return from + wrapDegrees(to - from) * amount;
    }

    private static float wrapDegrees(float value) {
        value %= 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class Orientation {
        final float yaw;
        final float pitch;

        private Orientation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
