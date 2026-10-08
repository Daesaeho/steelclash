package com.steelclash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.client.anim.CombatPose;
import com.steelclash.client.anim.CombatPresentation;
import com.steelclash.combat.CombatMath;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.ArcPath;
import com.steelclash.core.Blade;
import com.steelclash.core.LagMath;
import com.steelclash.core.Phase;
import com.steelclash.core.Vec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * {@code /steelclash_debug}: draws every active swing. Yellow = windup (with the upcoming arc as a faint fan),
 * red = live blade during release, grey = recovery, blue = parry, dim blue = lowering guard, magenta = staggered.
 * Uses the same arc math as the server tracer. The top-left corner shows ping and the lag compensation it gets.
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class CombatDebugRenderer {
    private static final int ARC_PREVIEW_SAMPLES = 12;
    private static boolean enabled;

    private CombatDebugRenderer() {
    }

    /** Dev tools (pose sheet) switch it on to check that the drawn blade sits on the traced one. */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("steelclash_debug").executes(context -> {
            enabled = !enabled;
            context.getSource().sendSystemMessage(Component.literal("Steel Clash debug view " + (enabled ? "on" : "off")));
            return 1;
        }));
    }

    /** Latency readout: what the server's lag compensation does for this player (using this side's config values). */
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.getConnection() == null || mc.options.hideGui) {
            return;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        int ping = info == null ? 0 : info.getLatency();
        String text = "ping " + ping + " ms | rewind " + LagMath.rewindTicks(ping, Config.INTERPOLATION_TICKS.get(), Config.MAX_REWIND_MS.get())
                + " t | parry grace " + LagMath.graceTicks(ping, Config.MAX_PARRY_GRACE_MS.get()) + " t"
                + (Config.LAG_COMPENSATION.get() ? "" : " (compensation off)");
        event.getGuiGraphics().drawString(mc.font, text, 4, 4, 0xFFFFFF);
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity entity) || !entity.hasData(ModAttachments.COMBAT)) {
                continue;
            }
            // Same clock as the model (hit-stop freezes it), so the line and the drawn weapon show the same moment.
            CombatPresentation.get(entity, partialTick).map(CombatPose::swing).ifPresent(pose -> {
                Vec pivot = CombatMath.toVec(CombatMath.pivot(entity, partialTick));
                double yaw = CombatMath.viewYaw(entity, partialTick);
                double pitch = entity.getViewXRot(partialTick);
                double length = CombatMath.bladeLength(entity, pose.spec());
                ArcPath path = pose.path();
                Phase phase = pose.phase();
                switch (phase) {
                    case WINDUP -> {
                        for (int i = 0; i <= ARC_PREVIEW_SAMPLES; i++) {
                            Blade.Segment s = Blade.at(pivot, yaw, pitch, path, i / (double) ARC_PREVIEW_SAMPLES, length);
                            line(poseStack, lines, s, 1f, 1f, 0.3f, 0.35f);
                        }
                        line(poseStack, lines, Blade.at(pivot, yaw, pitch, path, 0, length), 1f, 0.9f, 0f, 1f);
                    }
                    case RELEASE -> line(poseStack, lines, Blade.at(pivot, yaw, pitch, path, pose.releaseProgress(), length), 1f, 0.1f, 0.1f, 1f);
                    case PARRY -> guard(poseStack, lines, pivot, yaw, pitch, pose, 0.2f, 0.5f, 1f);
                    case GUARD_RECOVERY -> guard(poseStack, lines, pivot, yaw, pitch, pose, 0.3f, 0.3f, 0.6f);
                    case STAGGER -> guard(poseStack, lines, pivot, yaw, pitch, pose, 0.9f, 0.2f, 0.9f);
                    default -> line(poseStack, lines, Blade.at(pivot, yaw, pitch, path, pose.releaseProgress(), length), 0.6f, 0.6f, 0.6f, 0.8f);
                }
            });
        }
        buffers.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    /** Draws the weapon where the pose holds it (guard, stagger). */
    private static void guard(PoseStack poseStack, VertexConsumer lines, Vec pivot, double yaw, double pitch, SwingPose pose,
                              float r, float g, float b) {
        Vec dir = Blade.direction(yaw + pose.yaw(), Math.max(-90, Math.min(90, pitch + pose.pitch())));
        line(poseStack, lines, new Blade.Segment(pivot, pivot.add(dir.scale(1.5))), r, g, b, 1f);
    }

    private static void line(PoseStack poseStack, VertexConsumer consumer, Blade.Segment segment,
                             float r, float g, float b, float a) {
        PoseStack.Pose pose = poseStack.last();
        Vec a0 = segment.hilt();
        Vec a1 = segment.tip();
        Vec dir = a1.subtract(a0);
        double len = Math.max(1e-6, dir.length());
        float nx = (float) (dir.x() / len);
        float ny = (float) (dir.y() / len);
        float nz = (float) (dir.z() / len);
        consumer.addVertex(pose, (float) a0.x(), (float) a0.y(), (float) a0.z()).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) a1.x(), (float) a1.y(), (float) a1.z()).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }
}
