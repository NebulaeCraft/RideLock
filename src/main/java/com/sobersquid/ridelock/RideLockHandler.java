package com.sobersquid.ridelock;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class RideLockHandler {
    private final TrajectoryFitter trajectoryFitter = new TrajectoryFitter();

    private EntityMinecart trackedMinecart;
    private boolean isEnabled = true;

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        Minecraft mc = Minecraft.getMinecraft();
        // Keybind enable/disable logic
        while (RideLock.toggleKey.isPressed()) {
            isEnabled = !isEnabled;
            String keystatus = isEnabled ? "§aEnabled" : "§cDisabled";
            String keytext = "§7Ride Lock: " + keystatus;
            if (mc.player != null) {
                net.minecraft.util.text.ITextComponent message = new net.minecraft.util.text.TextComponentString(keytext);
                mc.player.sendStatusMessage(message, true);
            }
            resetTracking();
        }
        // Safety Check to stop if player doesn't yet exist
        if (mc.player == null || mc.isGamePaused()) {
            resetTracking();
            return;
        }
        
        if (!isEnabled) {
            resetTracking();
            return;
        }

        Entity vehicle = mc.player.getRidingEntity();
        if (!(vehicle instanceof EntityMinecart)) {
            resetTracking();
            return;
        }

        EntityMinecart minecart = (EntityMinecart) vehicle;
        if (trackedMinecart != minecart) {
            resetTracking();
            trackedMinecart = minecart;
        }

        long currentTimeNanos = System.nanoTime();
        trajectoryFitter.configure(
                RideLockConfig.getWindowNanos(), RideLockConfig.getSampleIntervalNanos());

        float partialTicks = (float) event.getRenderPartialTicks();
        double curX = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * partialTicks;
        double curY = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * partialTicks;
        double curZ = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * partialTicks;

        TrajectoryFitter.FitResult tangent = trajectoryFitter.update(currentTimeNanos, curX, curY, curZ);
        trajectoryFitter.consumeDiscontinuityReset();
        if (!tangent.valid) {
            return;
        }

        double horizontalSpeed = Math.sqrt(tangent.vx * tangent.vx + tangent.vz * tangent.vz);
        float currentYaw = (float) Math.toDegrees(Math.atan2(-tangent.vx, tangent.vz));
        float currentPitch = (float) -Math.toDegrees(Math.atan2(tangent.vy, horizontalSpeed));

        // Apply the fitted tangent directly. This avoids integrating small fit
        // errors into the player's view over successive render frames.
        mc.player.rotationYaw = currentYaw;
        mc.player.prevRotationYaw = currentYaw;
        mc.player.rotationPitch = currentPitch;
        mc.player.prevRotationPitch = currentPitch;
        // CameraSetup stores the OpenGL camera rotation. Vanilla supplies the
        // entity yaw plus 180 degrees here, rather than the entity yaw itself.
        event.setYaw(cameraSetupYaw(currentYaw));
        event.setPitch(currentPitch);
    }

    static float cameraSetupYaw(float playerYaw) {
        return playerYaw + 180.0f;
    }

    private void resetTracking() {
        trajectoryFitter.reset();
        trackedMinecart = null;
    }
}
