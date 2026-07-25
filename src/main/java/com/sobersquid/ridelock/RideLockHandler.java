package com.sobersquid.ridelock;

import com.sobersquid.ridelock.curve.SmoothTrackCurve;
import com.sobersquid.ridelock.curve.Vector3;
import com.sobersquid.ridelock.track.RailPathSampler;
import com.sobersquid.ridelock.track.SampledRailPath;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RideLockHandler {
    private static final float LERP_SENSITIVITY = 10.0f;
    private static final float PITCH_STRENGTH = 0.4f;
    private static final double MIN_HORIZONTAL_TANGENT_SQUARED = 1.0e-8;

    private final RailPathSampler railPathSampler = new RailPathSampler();
    private final CameraOrientationSmoother orientationSmoother = new CameraOrientationSmoother();
    private final TransientCurveFailureGuard curveFailureGuard = new TransientCurveFailureGuard();

    private boolean isEnabled = true;
    private World trackedWorld;
    private SmoothTrackCurve fittedCurve;
    private List<BlockPos> fittedBlocks = Collections.emptyList();
    private int fittedTangentSampleSpacing = -1;
    private int fittedControlPointSpacing = -1;
    private int fittedSmoothnessWeight = -1;

    private boolean curveCameraActive;
    private float lockedYaw;
    private float lockedPitch;

    private double fallbackLastX;
    private double fallbackLastY;
    private double fallbackLastZ;
    private float fallbackLastYaw;
    private boolean fallbackWasRiding;
    private long fallbackLastTime = System.currentTimeMillis();
    private float fallbackSmoothedYawDelta;
    private float fallbackSmoothedPitch;

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        handleToggleKey(mc);
        if (mc.player == null || mc.world == null) {
            resetRideState();
            trackedWorld = null;
            return;
        }

        if (trackedWorld != mc.world) {
            resetRideState();
            trackedWorld = mc.world;
        }
        if (!isEnabled || mc.isGamePaused()) {
            if (!isEnabled) resetRideState();
            return;
        }

        Entity vehicle = mc.player.getRidingEntity();
        if (!(vehicle instanceof EntityMinecart)) {
            clearCurveState();
            return;
        }

        SampledRailPath path = railPathSampler.sample((EntityMinecart) vehicle, mc.player.rotationYaw);
        if (path == null || !path.hasEnoughContext()) {
            handleCurveSampleFailure();
            return;
        }

        int tangentSampleSpacing = RideLockConfig.tangentSampleSpacing();
        int controlPointSpacing = RideLockConfig.controlPointSpacing();
        int smoothnessWeight = RideLockConfig.smoothnessWeight();
        if (!path.blocks().equals(fittedBlocks)
                || tangentSampleSpacing != fittedTangentSampleSpacing
                || controlPointSpacing != fittedControlPointSpacing
                || smoothnessWeight != fittedSmoothnessWeight) {
            try {
                SmoothTrackCurve newCurve = SmoothTrackCurve.fit(
                        path.centers(), tangentSampleSpacing,
                        controlPointSpacing, smoothnessWeight);
                fittedCurve = newCurve;
                fittedBlocks = Collections.unmodifiableList(new ArrayList<>(path.blocks()));
                fittedTangentSampleSpacing = tangentSampleSpacing;
                fittedControlPointSpacing = controlPointSpacing;
                fittedSmoothnessWeight = smoothnessWeight;
            } catch (IllegalArgumentException ignored) {
                handleCurveSampleFailure();
                return;
            }
        }
        curveFailureGuard.recordSuccess();
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        curveCameraActive = false;

        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled || mc.player == null || mc.world == null || mc.isGamePaused()
                || fittedCurve == null || !(mc.player.getRidingEntity() instanceof EntityMinecart)) {
            return;
        }

        EntityMinecart cart = (EntityMinecart) mc.player.getRidingEntity();
        float partialTicks = event.renderTickTime;
        Vector3 position = new Vector3(
                cart.lastTickPosX + (cart.posX - cart.lastTickPosX) * partialTicks,
                cart.lastTickPosY + (cart.posY - cart.lastTickPosY) * partialTicks,
                cart.lastTickPosZ + (cart.posZ - cart.lastTickPosZ) * partialTicks);
        Vector3 direction = fittedCurve.directionAt(position);
        double horizontalSquared = direction.x * direction.x + direction.z * direction.z;
        if (horizontalSquared < MIN_HORIZONTAL_TANGENT_SQUARED) return;

        float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float targetPitch = pitchFromCurveDirection(
                direction, horizontalSquared, RideLockConfig.verticalCameraInfluence());

        CameraOrientationSmoother.Orientation orientation = orientationSmoother.update(
                targetYaw, targetPitch, mc.player.rotationYaw, mc.player.rotationPitch,
                System.nanoTime());
        lockedYaw = orientation.yaw;
        lockedPitch = orientation.pitch;

        mc.player.prevRotationYaw = lockedYaw;
        mc.player.rotationYaw = lockedYaw;
        mc.player.prevRotationPitch = lockedPitch;
        mc.player.rotationPitch = lockedPitch;
        curveCameraActive = true;
        fallbackWasRiding = false;
    }

    static float pitchFromCurveDirection(Vector3 direction, double horizontalSquared,
                                         double verticalInfluence) {
        double scaledVertical = direction.y * verticalInfluence;
        float pitch = (float) -Math.toDegrees(
                Math.atan2(scaledVertical, Math.sqrt(horizontalSquared)));
        return MathHelper.clamp(pitch, -90.0f, 90.0f);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.isGamePaused() || !isEnabled) return;

        if (curveCameraActive && fittedCurve != null
                && mc.player.getRidingEntity() instanceof EntityMinecart) {
            // EntityRenderer supplies camera yaw as player yaw + 180 degrees.
            event.setYaw(lockedYaw + 180.0f);
            event.setPitch(lockedPitch);
            return;
        }

        applyMovementFallback(mc, event);
    }

    private void handleToggleKey(Minecraft mc) {
        while (RideLock.toggleKey.isPressed()) {
            isEnabled = !isEnabled;
            if (!isEnabled) resetRideState();
            if (mc.player != null) {
                String keyStatus = isEnabled ? "§aEnabled" : "§cDisabled";
                mc.player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(
                        "§7Ride Lock: " + keyStatus), true);
            }
        }
    }

    private void applyMovementFallback(Minecraft mc, EntityViewRenderEvent.CameraSetup event) {
        Entity vehicle = mc.player.getRidingEntity();
        if (vehicle == null) {
            resetFallbackState();
            return;
        }

        float partialTicks = (float) event.getRenderPartialTicks();
        double currentX = vehicle.lastTickPosX + (vehicle.posX - vehicle.lastTickPosX) * partialTicks;
        double currentY = vehicle.lastTickPosY + (vehicle.posY - vehicle.lastTickPosY) * partialTicks;
        double currentZ = vehicle.lastTickPosZ + (vehicle.posZ - vehicle.lastTickPosZ) * partialTicks;
        long currentTime = System.currentTimeMillis();

        if (!fallbackWasRiding) {
            fallbackLastX = currentX;
            fallbackLastY = currentY;
            fallbackLastZ = currentZ;
            fallbackLastYaw = mc.player.rotationYaw;
            fallbackSmoothedYawDelta = 0.0f;
            fallbackSmoothedPitch = 0.0f;
            fallbackLastTime = currentTime;
            fallbackWasRiding = true;
            return;
        }

        double dx = currentX - fallbackLastX;
        double dy = currentY - fallbackLastY;
        double dz = currentZ - fallbackLastZ;
        double horizontalDistanceSquared = dx * dx + dz * dz;
        float currentYaw = fallbackLastYaw;
        float currentPitch = 0.0f;
        if (horizontalDistanceSquared > 0.000001) {
            currentYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            currentPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(horizontalDistanceSquared)));
        }

        float deltaTime = Math.max(0.0f, (currentTime - fallbackLastTime) / 1000.0f);
        float lerpFactor = MathHelper.clamp(deltaTime * LERP_SENSITIVITY, 0.0f, 1.0f);
        float yawDelta = MathHelper.wrapDegrees(currentYaw - fallbackLastYaw);
        fallbackSmoothedYawDelta += (yawDelta - fallbackSmoothedYawDelta) * lerpFactor;
        fallbackSmoothedPitch += (currentPitch - fallbackSmoothedPitch) * lerpFactor;

        if (Math.abs(fallbackSmoothedYawDelta) > 0.001f) {
            mc.player.rotationYaw = MathHelper.wrapDegrees(mc.player.rotationYaw + fallbackSmoothedYawDelta);
            mc.player.prevRotationYaw = mc.player.rotationYaw - fallbackSmoothedYawDelta;
        }
        event.setPitch(event.getPitch() + fallbackSmoothedPitch * PITCH_STRENGTH);

        fallbackLastX = currentX;
        fallbackLastY = currentY;
        fallbackLastZ = currentZ;
        fallbackLastYaw = currentYaw;
        fallbackLastTime = currentTime;
    }

    private void clearCurveState() {
        railPathSampler.reset();
        clearFittedCurve();
    }

    private void handleCurveSampleFailure() {
        if (fittedCurve == null || curveFailureGuard.recordFailure()) {
            clearFittedCurve();
        }
    }

    private void clearFittedCurve() {
        fittedCurve = null;
        fittedBlocks = Collections.emptyList();
        fittedTangentSampleSpacing = -1;
        fittedControlPointSpacing = -1;
        fittedSmoothnessWeight = -1;
        curveCameraActive = false;
        orientationSmoother.reset();
        curveFailureGuard.reset();
    }

    private void resetFallbackState() {
        fallbackWasRiding = false;
        fallbackSmoothedYawDelta = 0.0f;
        fallbackSmoothedPitch = 0.0f;
        fallbackLastTime = System.currentTimeMillis();
    }

    private void resetRideState() {
        clearCurveState();
        resetFallbackState();
    }
}
