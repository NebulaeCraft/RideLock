package com.sobersquid.ridelock;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = RideLock.MOD_ID, name = "Ride Lock", version = RideLock.VERSION)
public class RideLock {
    public static final String MOD_ID = "ridelock";
    public static final String VERSION = "1.0.2";
    public static KeyBinding toggleKey;

    public RideLock() {
        // Forge preserves the numeric type stored in an existing config file.
        // Migrate the former decimal setting before Forge performs its first
        // annotation-driven config sync, so upgraded installs get int sliders.
        RideLockConfig.migrateLegacyNumericTypes();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        RideLockConfig.normalizeLoadedValue();
        toggleKey = new KeyBinding("key.ridelock.toggle", Keyboard.KEY_F7, "Ride Lock");
        ClientRegistry.registerKeyBinding(toggleKey);
        MinecraftForge.EVENT_BUS.register(new RideLockHandler());
    }
}
