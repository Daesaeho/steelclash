package com.steelclash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Optional learning aid (client config {@code enemyTelegraphs}, off by default): above every enemy winding up an
 * attack, show its type and a countdown bar, e.g. "OVERHEAD ▮▮▮▯▯".
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class EnemyTelegraphRenderer {
    private static final double MAX_DISTANCE = 16;
    private static final int SEGMENTS = 6;

    private EnemyTelegraphRenderer() {
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null || mc.player == null
                || !Config.Client.ENEMY_TELEGRAPHS.get()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity entity) || entity == mc.player || !entity.hasData(ModAttachments.COMBAT)
                    || entity.distanceToSqr(mc.player) > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }
            CombatData data = entity.getData(ModAttachments.COMBAT);
            if (data.machine.phase() != Phase.WINDUP) {
                continue;
            }
            Component label = Component.literal(label(data.machine.type(), data.machine.isHeavy()) + " "
                    + bar(data.machine.phaseProgress(partialTick)));
            Vec3 pos = entity.getPosition(partialTick).add(0, entity.getBbHeight() + 0.6, 0);
            poseStack.pushPose();
            poseStack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(0.025f, -0.025f, 0.025f);
            Matrix4f matrix = poseStack.last().pose();
            float x = -font.width(label) / 2f;
            int color = data.machine.isHeavy() ? 0xFFF08020 : 0xFFF0D040;
            font.drawInBatch(label, x, 0, color, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000,
                    LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    private static String label(AttackType type, boolean heavy) {
        String name = switch (type) {
            case SLASH -> "SLASH";
            case OVERHEAD -> "OVERHEAD";
            case STAB -> "STAB";
            case KICK -> "KICK";
            case SPECIAL -> "SPECIAL";
            case THROW -> "THROW";
        };
        return heavy ? "HEAVY " + name : name;
    }

    /** Remaining windup as filled segments: full when the attack starts, empty when it lands. */
    private static String bar(double progress) {
        int filled = (int) Math.ceil((1 - progress) * SEGMENTS);
        return "▮".repeat(Math.max(0, filled)) + "▯".repeat(Math.max(0, SEGMENTS - filled));
    }
}
