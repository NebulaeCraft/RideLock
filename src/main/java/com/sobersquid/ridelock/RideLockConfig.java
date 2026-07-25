package com.sobersquid.ridelock;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Forge-managed client configuration. */
@Config(modid = RideLock.MOD_ID)
@Mod.EventBusSubscriber(modid = RideLock.MOD_ID, value = Side.CLIENT)
public final class RideLockConfig {
    static final double DEFAULT_TANGENT_SAMPLE_SPACING = 4.0;
    static final double MIN_TANGENT_SAMPLE_SPACING = 0.1;
    static final double MAX_TANGENT_SAMPLE_SPACING = 32.0;

    @Config.Comment({
            "Arc length in blocks between fitted-curve tangent samples.",
            "Smaller values follow the curve more precisely; larger values smooth more broadly."
    })
    @Config.LangKey("config.ridelock.tangentSampleSpacing")
    @Config.RangeDouble(min = MIN_TANGENT_SAMPLE_SPACING, max = MAX_TANGENT_SAMPLE_SPACING)
    @Config.SlidingOption
    public static double tangentSampleSpacing = DEFAULT_TANGENT_SAMPLE_SPACING;

    private RideLockConfig() {
    }

    public static double tangentSampleSpacing() {
        return normalizeTangentSampleSpacing(tangentSampleSpacing);
    }

    static double normalizeTangentSampleSpacing(double value) {
        if (!Double.isFinite(value)) value = DEFAULT_TANGENT_SAMPLE_SPACING;
        value = Math.max(MIN_TANGENT_SAMPLE_SPACING,
                Math.min(MAX_TANGENT_SAMPLE_SPACING, value));
        // Forge 1.12's one-decimal GuiSlider truncates its displayed value.
        // Canonicalize the stored value the same way so the config file never
        // differs from what the user saw in the menu.
        return Math.floor(value * 10.0 + 1.0e-9) / 10.0;
    }

    static void normalizeLoadedValue() {
        tangentSampleSpacing = tangentSampleSpacing();
        ConfigManager.sync(RideLock.MOD_ID, Config.Type.INSTANCE);
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!RideLock.MOD_ID.equals(event.getModID())) return;

        // Pull the GUI value first, then canonicalize and save it with exactly
        // one decimal place of precision.
        ConfigManager.sync(RideLock.MOD_ID, Config.Type.INSTANCE);
        normalizeLoadedValue();
    }
}
