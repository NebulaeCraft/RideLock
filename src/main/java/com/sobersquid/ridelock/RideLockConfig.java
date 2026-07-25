package com.sobersquid.ridelock;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = RideLock.MOD_ID)
public final class RideLockConfig {
    private static final double MIN_WINDOW_SECONDS = 0.5;
    private static final double MAX_WINDOW_SECONDS = 5.0;
    private static final int MIN_SAMPLE_INTERVAL_MILLIS = 5;
    private static final int MAX_SAMPLE_INTERVAL_MILLIS = 50;

    @Config.Name("trajectoryWindowSeconds")
    @Config.LangKey("config.ridelock.trajectoryWindowSeconds")
    @Config.Comment({
            "Length of the player-position history used for curve fitting, in seconds.",
            "Stored with one decimal place. Valid range: 0.5 to 5.0."
    })
    @Config.RangeDouble(min = MIN_WINDOW_SECONDS, max = MAX_WINDOW_SECONDS)
    public static double trajectoryWindowSeconds = 1.5;

    @Config.Name("sampleIntervalMilliseconds")
    @Config.LangKey("config.ridelock.sampleIntervalMilliseconds")
    @Config.Comment({
            "Minimum time between stored player-position samples, in milliseconds.",
            "Stored as an integer. Valid range: 5 to 50."
    })
    @Config.RangeInt(min = MIN_SAMPLE_INTERVAL_MILLIS, max = MAX_SAMPLE_INTERVAL_MILLIS)
    public static int sampleIntervalMilliseconds = 10;

    private RideLockConfig() {
    }

    public static void normalize() {
        trajectoryWindowSeconds = oneDecimal(clamp(
                trajectoryWindowSeconds, MIN_WINDOW_SECONDS, MAX_WINDOW_SECONDS));
        sampleIntervalMilliseconds = clamp(
                sampleIntervalMilliseconds, MIN_SAMPLE_INTERVAL_MILLIS, MAX_SAMPLE_INTERVAL_MILLIS);
    }

    public static long getWindowNanos() {
        return Math.round(oneDecimal(trajectoryWindowSeconds) * 1_000_000_000.0);
    }

    public static long getSampleIntervalNanos() {
        return sampleIntervalMilliseconds * 1_000_000L;
    }

    private static double oneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class EventHandler {
        @SubscribeEvent
        public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (!RideLock.MOD_ID.equals(event.getModID())) {
                return;
            }
            ConfigManager.sync(RideLock.MOD_ID, Config.Type.INSTANCE);
            RideLockConfig.normalize();
            ConfigManager.sync(RideLock.MOD_ID, Config.Type.INSTANCE);
        }
    }
}
