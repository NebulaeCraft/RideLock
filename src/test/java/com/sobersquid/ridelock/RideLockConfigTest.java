package com.sobersquid.ridelock;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RideLockConfigTest {
    @Test
    public void retainsIntegerTangentSpacing() {
        assertEquals(4, RideLockConfig.normalizeTangentSampleSpacing(4));
        assertEquals(12, RideLockConfig.normalizeTangentSampleSpacing(12));
    }

    @Test
    public void clampsTangentSpacingToSafeRange() {
        assertEquals(RideLockConfig.MIN_TANGENT_SAMPLE_SPACING,
                RideLockConfig.normalizeTangentSampleSpacing(-5));
        assertEquals(RideLockConfig.MAX_TANGENT_SAMPLE_SPACING,
                RideLockConfig.normalizeTangentSampleSpacing(100));
    }

    @Test
    public void clampsControlPointSpacingToSafeIntegerRange() {
        assertEquals(8, RideLockConfig.normalizeControlPointSpacing(8));
        assertEquals(RideLockConfig.MIN_CONTROL_POINT_SPACING,
                RideLockConfig.normalizeControlPointSpacing(0));
        assertEquals(RideLockConfig.MAX_CONTROL_POINT_SPACING,
                RideLockConfig.normalizeControlPointSpacing(100));
    }

    @Test
    public void clampsSmoothnessWeightToSafeIntegerRange() {
        assertEquals(4, RideLockConfig.normalizeSmoothnessWeight(4));
        assertEquals(RideLockConfig.MIN_SMOOTHNESS_WEIGHT,
                RideLockConfig.normalizeSmoothnessWeight(-1));
        assertEquals(RideLockConfig.MAX_SMOOTHNESS_WEIGHT,
                RideLockConfig.normalizeSmoothnessWeight(100));
    }

    @Test
    public void migratesLegacyDecimalPropertyToNearestIntegerType() {
        ConfigCategory category = new ConfigCategory("general");
        category.put("tangentSampleSpacing", new Property(
                "tangentSampleSpacing", "4.7", Property.Type.DOUBLE));

        boolean changed = RideLockConfig.migrateDoubleProperty(
                category, "tangentSampleSpacing", 4, 1, 32);

        assertEquals(true, changed);
        assertEquals(Property.Type.INTEGER, category.get("tangentSampleSpacing").getType());
        assertEquals(5, category.get("tangentSampleSpacing").getInt());
    }

    @Test
    public void normalizesVerticalCameraInfluenceToOneDecimal() {
        assertEquals(1.0, RideLockConfig.normalizeVerticalCameraInfluence(1.0), 0.0);
        assertEquals(1.1, RideLockConfig.normalizeVerticalCameraInfluence(1.199), 0.0);
        assertEquals(0.0, RideLockConfig.normalizeVerticalCameraInfluence(-1.0), 0.0);
        assertEquals(2.0, RideLockConfig.normalizeVerticalCameraInfluence(3.0), 0.0);
        assertEquals(RideLockConfig.DEFAULT_VERTICAL_CAMERA_INFLUENCE,
                RideLockConfig.normalizeVerticalCameraInfluence(Double.NaN), 0.0);
    }
}
