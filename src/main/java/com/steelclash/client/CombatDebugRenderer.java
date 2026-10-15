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
import com.steelclash.core.CollisionProvenance;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.LagMath;
import com.steelclash.core.Phase;
import com.steelclash.core.Vec;
import com.steelclash.net.FeedbackPayload;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
    private static final long CONTACT_LIFE_NANOS = 2_500_000_000L;
    private static final int MAX_CONTACT_MARKERS = 24;
    private record ContactMarker(FeedbackPayload.Contact contact, long receivedAt) {}
    private static final Deque<ContactMarker> CONTACTS = new ArrayDeque<>();
    private static Level contactsLevel;
    private static boolean enabled;
    private static long contactsReceived;
    private static long lastContactDrawFrame = -1;
    private static long lastContactDrawAt;
    private static int lastContactDrawMarkers;
    private static int lastContactDrawNormalLines;

    /** Read-only view of contacts received from the server and markers actually submitted to a render pass. */
    public record ContactDebugSnapshot(boolean enabled, int markersRetained, long contactsReceived,
                                       @org.jetbrains.annotations.Nullable FeedbackPayload.Contact latest,
                                       long latestAgeNanos, long lastDrawFrame, long lastDrawAgeNanos,
                                       int lastDrawMarkers, int lastDrawNormalLines) {}

    private CombatDebugRenderer() {
    }

    /** Dev tools (pose sheet) switch it on to check that the drawn blade sits on the traced one. */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() { return enabled; }

    public static ContactDebugSnapshot contactDebugSnapshot(long now) {
        pruneContacts(now);
        ContactMarker latest = CONTACTS.peekLast();
        return new ContactDebugSnapshot(enabled, CONTACTS.size(), contactsReceived,
                latest == null ? null : latest.contact(), latest == null ? -1 : Math.max(0, now - latest.receivedAt()),
                lastContactDrawFrame, lastContactDrawAt == 0 ? -1 : Math.max(0, now - lastContactDrawAt),
                lastContactDrawMarkers, lastContactDrawNormalLines);
    }

    /** Received server contact for the opt-in debug view; world replacement cannot leave markers in another level. */
    public static void recordContact(FeedbackPayload payload) {
        if (payload.contact() == null) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level != contactsLevel) {
            CONTACTS.clear();
            contactsLevel = level;
            contactsReceived = 0;
        }
        long now = System.nanoTime();
        pruneContacts(now);
        CONTACTS.addLast(new ContactMarker(payload.contact(), now));
        contactsReceived++;
        while (CONTACTS.size() > MAX_CONTACT_MARKERS) {
            CONTACTS.removeFirst();
        }
    }

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("steelclash_debug").executes(context -> {
            enabled = !enabled;
            context.getSource().sendSystemMessage(Component.literal("Steel Clash debug view " + (enabled ? "on" : "off")));
            return 1;
        }));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        Level level = Minecraft.getInstance().level;
        if (level != contactsLevel) {
            CONTACTS.clear();
            contactsLevel = level;
            contactsReceived = 0;
        }
        pruneContacts(System.nanoTime());
    }

    /** Latency readout: what the server's lag compensation does for this player (using this side's config values). */
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.getConnection() == null || mc.options.hideGui) {
            return;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        pruneContacts(System.nanoTime());
        int ping = info == null ? 0 : info.getLatency();
        String text = "ping " + ping + " ms | rewind " + LagMath.rewindTicks(ping, Config.INTERPOLATION_TICKS.get(), Config.MAX_REWIND_MS.get())
                + " t | parry grace " + LagMath.graceTicks(ping, Config.MAX_PARRY_GRACE_MS.get()) + " t"
                + (Config.LAG_COMPENSATION.get() ? "" : " (compensation off)");
        event.getGuiGraphics().drawString(mc.font, text, 4, 4, 0xFFFFFF);
        ContactMarker latest = CONTACTS.peekLast();
        int detailsY = 15;
        int detailsWidth = Math.max(80, mc.getWindow().getGuiScaledWidth() - 8);
        if (latest != null) {
            FeedbackPayload.Contact c = latest.contact();
            String marker = "contact " + identity(c.attackIdentity()) + " capture @" + vector(c.capturedPoint()) + " entry @" + vector(c.entryPoint())
                    + " tick " + c.serverTick() + " p " + String.format(java.util.Locale.ROOT, "%.3f", c.progress())
                    + " " + c.collisionProvenance() + " n " + (c.normal() == null ? "unknown" : vector(c.normal()));
            var markerText = Component.literal(marker);
            event.getGuiGraphics().drawWordWrap(mc.font, markerText, 4, detailsY, detailsWidth, 0xFFFFFF);
            detailsY += mc.font.split(markerText, detailsWidth).size() * mc.font.lineHeight;
        }
        FeedbackPayload.Contact frozen = CombatPresentation.lastHitStopContact();
        if (frozen != null) {
            var frozenText = Component.literal("hit-stop source " + identity(frozen.attackIdentity())
                    + " tick " + frozen.serverTick() + " p "
                    + String.format(java.util.Locale.ROOT, "%.3f", frozen.progress()) + " "
                    + frozen.collisionProvenance());
            event.getGuiGraphics().drawWordWrap(mc.font, frozenText, 4, detailsY, detailsWidth, 0xFFFFFF);
        }
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null) {
            return;
        }
        if (contactsLevel != mc.level) {
            CONTACTS.clear();
            contactsLevel = mc.level;
            contactsReceived = 0;
        }
        pruneContacts(System.nanoTime());
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
        int drawnContactMarkers = 0;
        int drawnNormalLines = 0;
        for (ContactMarker marker : CONTACTS) {
            drawnContactMarkers++;
            FeedbackPayload.Contact contact = marker.contact();
            float r = 0.65f, g = 0.65f, b = 0.65f;
            if (contact.collisionProvenance() == CollisionProvenance.BODY_AABB_CONTACT) {
                r = 0.15f; g = 0.95f; b = 0.25f;
            } else if (contact.collisionProvenance() == CollisionProvenance.INFLATED_AABB_ENTRY_BODY_CLAMPED) {
                r = 1f; g = 0.5f; b = 0.05f;
            } else if (contact.collisionProvenance() == CollisionProvenance.BLOCK_SHAPE) {
                r = 0.1f; g = 0.8f; b = 1f;
            }
            point(poseStack, lines, contact.capturedPoint(), 0.07, r, g, b);
            if (contact.entryPoint().distanceToSqr(contact.capturedPoint()) > 1e-8) {
                line(poseStack, lines, contact.entryPoint(), contact.capturedPoint(), r, g, b, 0.65f);
                point(poseStack, lines, contact.entryPoint(), 0.035, r, g, b);
            }
            if (contact.normal() != null) {
                line(poseStack, lines, contact.entryPoint(), contact.entryPoint().add(contact.normal().scale(0.4)),
                        1f, 0.95f, 0.1f, 1f);
                drawnNormalLines++;
            }
            if (contact.motion().lengthSqr() > 1e-8) {
                line(poseStack, lines, contact.entryPoint(), contact.entryPoint().add(contact.motion().normalize().scale(0.3)),
                        0.2f, 0.65f, 1f, 0.9f);
            }
        }
        lastContactDrawFrame = CombatPresentation.frameNumber();
        lastContactDrawAt = System.nanoTime();
        lastContactDrawMarkers = drawnContactMarkers;
        lastContactDrawNormalLines = drawnNormalLines;
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
        line(poseStack, consumer, new Vec3(segment.hilt().x(), segment.hilt().y(), segment.hilt().z()),
                new Vec3(segment.tip().x(), segment.tip().y(), segment.tip().z()), r, g, b, a);
    }

    private static void line(PoseStack poseStack, VertexConsumer consumer, Vec3 a0, Vec3 a1,
                             float r, float g, float b, float a) {
        PoseStack.Pose pose = poseStack.last();
        Vec dir = new Vec(a1.x - a0.x, a1.y - a0.y, a1.z - a0.z);
        double len = Math.max(1e-6, dir.length());
        float nx = (float) (dir.x() / len);
        float ny = (float) (dir.y() / len);
        float nz = (float) (dir.z() / len);
        consumer.addVertex(pose, (float) a0.x, (float) a0.y, (float) a0.z).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) a1.x, (float) a1.y, (float) a1.z).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }

    private static void point(PoseStack poseStack, VertexConsumer consumer, Vec3 at, double radius,
                              float r, float g, float b) {
        line(poseStack, consumer, at.add(-radius, 0, 0), at.add(radius, 0, 0), r, g, b, 1);
        line(poseStack, consumer, at.add(0, -radius, 0), at.add(0, radius, 0), r, g, b, 1);
        line(poseStack, consumer, at.add(0, 0, -radius), at.add(0, 0, radius), r, g, b, 1);
    }

    private static void pruneContacts(long now) {
        while (!CONTACTS.isEmpty() && now - CONTACTS.peekFirst().receivedAt() > CONTACT_LIFE_NANOS) {
            CONTACTS.removeFirst();
        }
    }

    private static String vector(Vec3 vector) {
        return String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", vector.x, vector.y, vector.z);
    }

    private static String identity(CombatStateMachine.AttackIdentity identity) {
        return identity.type() + "#" + identity.serial() + (identity.heavy() ? "H" : "")
                + (identity.morphed() ? "M" : "") + (identity.counterFeinted() ? "C" : "")
                + "v" + identity.variant() + (identity.mirrored() ? "L" : "R");
    }
}
