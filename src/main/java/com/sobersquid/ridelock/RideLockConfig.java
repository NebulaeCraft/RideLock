package com.sobersquid.ridelock;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.File;

/** Forge-managed client configuration. */
@Config(modid = RideLock.MOD_ID)
@Mod.EventBusSubscriber(modid = RideLock.MOD_ID, value = Side.CLIENT)
public final class RideLockConfig {
    static final int DEFAULT_TANGENT_SAMPLE_SPACING = 4;
    static final int MIN_TANGENT_SAMPLE_SPACING = 1;
    static final int MAX_TANGENT_SAMPLE_SPACING = 32;
    static final int DEFAULT_CONTROL_POINT_SPACING = 8;
    static final int MIN_CONTROL_POINT_SPACING = 2;
    static final int MAX_CONTROL_POINT_SPACING = 64;
    static final int DEFAULT_SMOOTHNESS_WEIGHT = 4;
    static final int MIN_SMOOTHNESS_WEIGHT = 1;
    static final int MAX_SMOOTHNESS_WEIGHT = 64;
    static final double DEFAULT_VERTICAL_CAMERA_INFLUENCE = 1.0;
    static final double MIN_VERTICAL_CAMERA_INFLUENCE = 0.0;
    static final double MAX_VERTICAL_CAMERA_INFLUENCE = 2.0;

    @Config.Comment({
            "Arc length in blocks between fitted-curve tangent samples.",
            "Smaller values follow the curve more precisely; larger values smooth more broadly."
    })
    @Config.LangKey("config.ridelock.tangentSampleSpacing")
    @Config.RangeInt(min = MIN_TANGENT_SAMPLE_SPACING, max = MAX_TANGENT_SAMPLE_SPACING)
    @Config.SlidingOption
    public static int tangentSampleSpacing = DEFAULT_TANGENT_SAMPLE_SPACING;

    @Config.Comment({
            "Approximate distance in blocks between B-spline control points.",
            "Smaller values follow local track details; larger values fit the path more broadly."
    })
    @Config.LangKey("config.ridelock.controlPointSpacing")
    @Config.RangeInt(min = MIN_CONTROL_POINT_SPACING, max = MAX_CONTROL_POINT_SPACING)
    @Config.SlidingOption
    public static int controlPointSpacing = DEFAULT_CONTROL_POINT_SPACING;

    @Config.Comment({
            "Strength of the three-dimensional curvature smoothing penalty.",
            "Larger values produce gentler curvature but can move the fit farther from the rails."
    })
    @Config.LangKey("config.ridelock.smoothnessWeight")
    @Config.RangeInt(min = MIN_SMOOTHNESS_WEIGHT, max = MAX_SMOOTHNESS_WEIGHT)
    @Config.SlidingOption
    public static int smoothnessWeight = DEFAULT_SMOOTHNESS_WEIGHT;

    @Config.Comment({
            "Multiplier applied to the fitted tangent's vertical component when calculating camera pitch.",
            "Zero disables curve-driven pitch; one uses the fitted direction unchanged."
    })
    @Config.LangKey("config.ridelock.verticalCameraInfluence")
    @Config.RangeDouble(min = MIN_VERTICAL_CAMERA_INFLUENCE, max = MAX_VERTICAL_CAMERA_INFLUENCE)
    @Config.SlidingOption
    public static double verticalCameraInfluence = DEFAULT_VERTICAL_CAMERA_INFLUENCE;

    private RideLockConfig() {
    }

    public static int tangentSampleSpacing() {
        return normalizeTangentSampleSpacing(tangentSampleSpacing);
    }

    public static int controlPointSpacing() {
        return normalizeControlPointSpacing(controlPointSpacing);
    }

    public static int smoothnessWeight() {
        return normalizeSmoothnessWeight(smoothnessWeight);
    }

    public static double verticalCameraInfluence() {
        return normalizeVerticalCameraInfluence(verticalCameraInfluence);
    }

    static int normalizeTangentSampleSpacing(int value) {
        return clamp(value,
                MIN_TANGENT_SAMPLE_SPACING, MAX_TANGENT_SAMPLE_SPACING);
    }

    static int normalizeControlPointSpacing(int value) {
        return clamp(value,
                MIN_CONTROL_POINT_SPACING, MAX_CONTROL_POINT_SPACING);
    }

    static int normalizeSmoothnessWeight(int value) {
        return clamp(value,
                MIN_SMOOTHNESS_WEIGHT, MAX_SMOOTHNESS_WEIGHT);
    }

    static double normalizeVerticalCameraInfluence(double value) {
        if (!Double.isFinite(value)) value = DEFAULT_VERTICAL_CAMERA_INFLUENCE;
        value = Math.max(MIN_VERTICAL_CAMERA_INFLUENCE,
                Math.min(MAX_VERTICAL_CAMERA_INFLUENCE, value));
        // Match Forge 1.12's one-decimal slider display in the saved value.
        return Math.floor(value * 10.0 + 1.0e-9) / 10.0;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static void migrateLegacyNumericTypes() {
        File file = new File(Loader.instance().getConfigDir(), RideLock.MOD_ID + ".cfg");
        if (!file.isFile()) return;

        Configuration configuration = new Configuration(file);
        configuration.load();
        if (!configuration.hasCategory(Configuration.CATEGORY_GENERAL)) return;

        ConfigCategory category = configuration.getCategory(Configuration.CATEGORY_GENERAL);
        boolean changed = false;
        changed |= migrateDoubleProperty(category, "tangentSampleSpacing",
                DEFAULT_TANGENT_SAMPLE_SPACING,
                MIN_TANGENT_SAMPLE_SPACING, MAX_TANGENT_SAMPLE_SPACING);
        changed |= migrateDoubleProperty(category, "controlPointSpacing",
                DEFAULT_CONTROL_POINT_SPACING,
                MIN_CONTROL_POINT_SPACING, MAX_CONTROL_POINT_SPACING);
        changed |= migrateDoubleProperty(category, "smoothnessWeight",
                DEFAULT_SMOOTHNESS_WEIGHT,
                MIN_SMOOTHNESS_WEIGHT, MAX_SMOOTHNESS_WEIGHT);
        if (changed) configuration.save();
    }

    static boolean migrateDoubleProperty(ConfigCategory category, String name,
                                         int defaultValue, int minimum, int maximum) {
        Property oldProperty = category.get(name);
        if (oldProperty == null || oldProperty.getType() != Property.Type.DOUBLE) return false;

        double oldValue = oldProperty.getDouble(defaultValue);
        if (!Double.isFinite(oldValue)) oldValue = defaultValue;
        int integerValue = (int) Math.round(Math.max(minimum, Math.min(maximum, oldValue)));
        category.put(name, new Property(name, Integer.toString(integerValue), Property.Type.INTEGER));
        return true;
    }

    static void normalizeLoadedValue() {
        tangentSampleSpacing = tangentSampleSpacing();
        controlPointSpacing = controlPointSpacing();
        smoothnessWeight = smoothnessWeight();
        verticalCameraInfluence = verticalCameraInfluence();
        ConfigManager.sync(RideLock.MOD_ID, Config.Type.INSTANCE);
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!RideLock.MOD_ID.equals(event.getModID())) return;

        // Pull the GUI values first, then clamp and save their canonical values.
        ConfigManager.sync(RideLock.MOD_ID, Config.Type.INSTANCE);
        normalizeLoadedValue();
    }
}
