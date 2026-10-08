package com.steelclash.client;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.client.anim.CombatPose;
import com.steelclash.client.anim.CombatPresentation;
import com.steelclash.net.FeedbackPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Local-only feel: hit-stop (your swing animation freezes for a few frames when it connects), camera shake on
 * impacts, and a subtle camera roll that follows your own swing. All scaled by the client config (0 = off).
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class ClientFeel {
    private static float shake;
    private static long lastFrameNanos = System.nanoTime();
    private static LocalPlayer lastPlayer;

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
            case HEADSHOT -> {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    player.playSound(SoundEvents.ARROW_HIT_PLAYER, 0.8f, 1.3f); // vanilla's arrow "ding", pitched up
                }
            }
            case DRAW_INTERRUPTED -> {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    player.stopUsingItem(); // the server already dropped the draw; holding the key starts a new one
                }
                addShake(0.3f * strength);
            }
        }
    }

    private static void hitStop() {
        int millis = Config.Client.HIT_STOP_MILLIS.get();
        if (millis > 0) {
            CombatPresentation.hitStop(System.nanoTime(), millis);
        }
    }

    private static void addShake(float amount) {
        shake = Math.min(1.5f, shake + amount * Config.Client.CAMERA_MOTION.get().floatValue()
                * Config.Client.IMPACT_SHAKE.get().floatValue());
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        LocalPlayer current = Minecraft.getInstance().player;
        if (current != lastPlayer) {
            CombatPresentation.reset();
            shake = 0;
            lastFrameNanos = System.nanoTime();
            lastPlayer = current;
        }
    }

    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        long now = System.nanoTime();
        CombatPresentation.beginFrame(now);
        float dt = Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        float motion = Config.Client.CAMERA_MOTION.get().floatValue();
        Minecraft mc = Minecraft.getInstance();
        if (motion <= 0 || mc.player == null || event.getCamera().getEntity() != mc.player) {
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
            CombatPose pose = CombatPresentation.get(mc.player, (float) event.getPartialTick()).orElse(null);
            if (pose != null && !pose.kick()) {
                roll += (float) (-pose.relativeYaw() * 0.05 * pose.weight() * motion * Config.Client.CAMERA_SWAY.get());
            }
        }
        event.setRoll(event.getRoll() + roll);
        event.setPitch(event.getPitch() + pitch);
    }
}
