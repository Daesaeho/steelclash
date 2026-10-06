package com.steelclash.client;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.client.anim.CombatPose;
import com.steelclash.net.FeedbackPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Local-only feel: hit-stop (your swing animation freezes for a few frames when it connects), camera shake on
 * impacts, and a subtle camera roll that follows your own swing. All scaled by the client config (0 = off).
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class ClientFeel {
    private static long freezeUntilNanos;
    private static float frozenPartialTick;
    private static boolean frozen;
    private static float shake;
    private static long lastFrameNanos = System.nanoTime();

    private ClientFeel() {
    }

    public static void onFeedback(FeedbackPayload payload) {
        float strength = payload.strength();
        switch (payload.kind()) {
            case LANDED -> {
                hitStop();
                addShake(0.35f * strength);
            }
            case TAKEN -> addShake(0.6f * strength);
            case PARRIED -> {
                hitStop();
                addShake(0.5f * strength);
            }
            case BLOCKED -> addShake(0.4f * strength);
            case CLANK -> {
                hitStop();
                addShake(0.7f * strength);
            }
        }
    }

    private static void hitStop() {
        int millis = Config.Client.HIT_STOP_MILLIS.get();
        if (millis > 0) {
            freezeUntilNanos = System.nanoTime() + millis * 1_000_000L;
            frozen = false;
        }
    }

    private static void addShake(float amount) {
        shake = Math.min(1.5f, shake + amount * Config.Client.CAMERA_MOTION.get().floatValue());
    }

    /** Partial tick to animate {@code entity} with: frozen while the local player's hit-stop lasts. */
    public static float animationPartialTick(LivingEntity entity, float partialTick) {
        if (entity != Minecraft.getInstance().player) {
            return partialTick;
        }
        if (System.nanoTime() < freezeUntilNanos) {
            if (!frozen) {
                frozen = true;
                frozenPartialTick = partialTick;
            }
            return frozenPartialTick;
        }
        frozen = false;
        return partialTick;
    }

    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        float motion = Config.Client.CAMERA_MOTION.get().floatValue();
        Minecraft mc = Minecraft.getInstance();
        if (motion <= 0 || mc.player == null) {
            shake = 0;
            return;
        }
        float roll = 0;
        float pitch = 0;
        if (shake > 0.001f) {
            double t = now / 1e9;
            roll += (float) Math.sin(t * 53) * shake * 2.2f;
            pitch += (float) Math.cos(t * 41) * shake * 1.2f;
            shake *= (float) Math.exp(-dt * 9);
        }
        // Lean the view into your own swing (first person only; third person shows the body doing it).
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            CombatPose pose = CombatPose.of(mc.player, (float) event.getPartialTick()).orElse(null);
            if (pose != null && !pose.kick()) {
                double relativeYaw = pose.aimYaw() - (mc.player.getViewYRot((float) event.getPartialTick())
                        - net.minecraft.util.Mth.rotLerp((float) event.getPartialTick(), mc.player.yBodyRotO, mc.player.yBodyRot));
                roll += (float) (-relativeYaw * 0.05 * pose.weight() * motion);
            }
        }
        event.setRoll(event.getRoll() + roll);
        event.setPitch(event.getPitch() + pitch);
    }
}
