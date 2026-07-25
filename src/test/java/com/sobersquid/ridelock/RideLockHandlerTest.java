package com.sobersquid.ridelock;

import com.sobersquid.ridelock.curve.Vector3;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RideLockHandlerTest {
    @Test
    public void halvesVerticalTangentInfluenceBeforeCalculatingPitch() {
        Vector3 uphill = new Vector3(0.0, 1.0, 1.0).normalize();
        double horizontalSquared = uphill.x * uphill.x + uphill.z * uphill.z;

        float pitch = RideLockHandler.pitchFromCurveDirection(uphill, horizontalSquared, 0.5);

        assertEquals(-Math.toDegrees(Math.atan(0.5)), pitch, 1.0e-5);
    }

    @Test
    public void preservesPitchSignForDownhillDirection() {
        Vector3 downhill = new Vector3(0.0, -1.0, 1.0).normalize();
        double horizontalSquared = downhill.x * downhill.x + downhill.z * downhill.z;

        float pitch = RideLockHandler.pitchFromCurveDirection(downhill, horizontalSquared, 0.5);

        assertEquals(Math.toDegrees(Math.atan(0.5)), pitch, 1.0e-5);
    }

    @Test
    public void supportsZeroNormalAndDoubleVerticalInfluence() {
        Vector3 uphill = new Vector3(0.0, 1.0, 1.0).normalize();
        double horizontalSquared = uphill.x * uphill.x + uphill.z * uphill.z;

        assertEquals(0.0, RideLockHandler.pitchFromCurveDirection(
                uphill, horizontalSquared, 0.0), 1.0e-5);
        assertEquals(-45.0, RideLockHandler.pitchFromCurveDirection(
                uphill, horizontalSquared, 1.0), 1.0e-5);
        assertEquals(-Math.toDegrees(Math.atan(2.0)), RideLockHandler.pitchFromCurveDirection(
                uphill, horizontalSquared, 2.0), 1.0e-5);
    }
}
