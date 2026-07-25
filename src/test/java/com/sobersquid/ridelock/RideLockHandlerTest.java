package com.sobersquid.ridelock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RideLockHandlerTest {
    @Test
    public void convertsPlayerYawToForgeCameraRotation() {
        assertEquals(180.0f, RideLockHandler.cameraSetupYaw(0.0f), 0.0f);
        assertEquals(90.0f, RideLockHandler.cameraSetupYaw(-90.0f), 0.0f);
        assertEquals(270.0f, RideLockHandler.cameraSetupYaw(90.0f), 0.0f);
    }
}
