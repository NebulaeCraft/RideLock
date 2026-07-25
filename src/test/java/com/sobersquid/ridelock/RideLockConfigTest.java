package com.sobersquid.ridelock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RideLockConfigTest {
    @Test
    public void limitsTangentSpacingToOneDecimalPlaceLikeForgeSlider() {
        assertEquals(4.1, RideLockConfig.normalizeTangentSampleSpacing(4.149), 0.0);
        assertEquals(4.1, RideLockConfig.normalizeTangentSampleSpacing(4.199), 0.0);
        assertEquals(4.2, RideLockConfig.normalizeTangentSampleSpacing(4.2), 0.0);
    }

    @Test
    public void clampsTangentSpacingToSafeRange() {
        assertEquals(RideLockConfig.MIN_TANGENT_SAMPLE_SPACING,
                RideLockConfig.normalizeTangentSampleSpacing(-5.0), 0.0);
        assertEquals(RideLockConfig.MAX_TANGENT_SAMPLE_SPACING,
                RideLockConfig.normalizeTangentSampleSpacing(100.0), 0.0);
    }

    @Test
    public void replacesNonFiniteTangentSpacingWithDefault() {
        assertEquals(RideLockConfig.DEFAULT_TANGENT_SAMPLE_SPACING,
                RideLockConfig.normalizeTangentSampleSpacing(Double.NaN), 0.0);
        assertEquals(RideLockConfig.DEFAULT_TANGENT_SAMPLE_SPACING,
                RideLockConfig.normalizeTangentSampleSpacing(Double.POSITIVE_INFINITY), 0.0);
    }
}
