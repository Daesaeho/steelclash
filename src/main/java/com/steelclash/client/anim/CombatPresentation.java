package com.steelclash.client.anim;

import com.steelclash.client.dev.PoseSheet;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.core.PoseBlend;
import com.steelclash.core.PresentationFreeze;
import com.steelclash.core.PoseSampleStamp;
import com.steelclash.net.FeedbackPayload;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** One owned pose sample per entity/frame, shared by the camera, player layer, and mob hooks. Render thread only. */
public final class CombatPresentation {
    private static final long TRANSITION_NANOS = (long) (PoseBlend.BLEND_TICKS * 50_000_000L);
    private static final Map<LivingEntity, Entry> ENTRIES = new WeakHashMap<>();
    private static long frame;
    private static long frameNanos = System.nanoTime();
    private static final long HIT_STOP_METADATA_LIFE_NANOS = 2_500_000_000L;
    @Nullable
    private static FeedbackPayload.Contact lastHitStopContact;
    private static long lastHitStopAt;

    /** QA view keeps the actual local freeze separate from the retained source-metadata TTL. */
    public record HitStopDebugSnapshot(boolean freezeHolding, @Nullable FeedbackPayload.Contact source,
                                       long sourceAgeNanos, boolean sourceWithinMetadataTtl) {}

    private CombatPresentation() {}

    public static void beginFrame(long now) {
        frame++;
        frameNanos = now;
    }

    public static void reset() { ENTRIES.clear(); lastHitStopContact = null; lastHitStopAt = 0; }
    public static long frameNumber() { return frame; }
    public static long frameTimeNanos() { return frameNanos; }

    public static void hitStop(long now, int millis, @Nullable FeedbackPayload.Contact contact) {
        LivingEntity player = Minecraft.getInstance().player;
        if (player != null && millis > 0) {
            lastHitStopContact = contact;
            lastHitStopAt = now;
            Entry entry = ENTRIES.computeIfAbsent(player, ignored -> new Entry());
            entry.freeze.request(now, millis * 1_000_000L);
            entry.sample.clear();
        }
    }

    /** Recent captured source for the opt-in debug view alongside the associated presentation freeze. */
    @Nullable
    public static FeedbackPayload.Contact lastHitStopContact() {
        return System.nanoTime() - lastHitStopAt <= HIT_STOP_METADATA_LIFE_NANOS ? lastHitStopContact : null;
    }

    public static HitStopDebugSnapshot hitStopDebugSnapshot(long now) {
        LivingEntity player = Minecraft.getInstance().player;
        Entry entry = player == null ? null : ENTRIES.get(player);
        boolean withinTtl = lastHitStopContact != null && now - lastHitStopAt <= HIT_STOP_METADATA_LIFE_NANOS;
        return new HitStopDebugSnapshot(entry != null && entry.freeze.holding(now),
                withinTtl ? lastHitStopContact : null,
                lastHitStopAt == 0 ? -1 : Math.max(0, now - lastHitStopAt), withinTtl);
    }

    public static void corrected(LivingEntity entity) {
        Entry entry = ENTRIES.get(entity);
        if (entry != null) {
            entry.freeze.corrected();
            entry.sample.clear();
            entry.corrected = true;
        }
    }

    public static boolean hasTail(LivingEntity entity) {
        Entry entry = ENTRIES.get(entity);
        return entry != null && entry.displayed != null;
    }

    public static Optional<CombatPose> get(LivingEntity entity, float partialTick) {
        if (!entity.hasData(ModAttachments.COMBAT) || !entity.isAlive()) {
            ENTRIES.remove(entity);
            return Optional.empty();
        }
        var data = entity.getData(ModAttachments.COMBAT);
        if (data.isDowned()) {
            ENTRIES.remove(entity); // the forced crawl pose must take effect immediately
            return Optional.empty();
        }
        if (data.machine.phase() != Phase.IDLE && CombatPose.staleLocalWeapon(entity)) {
            ENTRIES.remove(entity);
            return Optional.empty();
        }
        Entry entry = ENTRIES.computeIfAbsent(entity, ignored -> new Entry());
        long generation = AnimationLibrary.INSTANCE.generation();
        float time = PoseSheet.running() ? 0 : partialTick;
        Signature signature = new Signature(data.machine.attackSerial(), data.machine.phase(), data.machine.type(),
                data.machine.isHeavy(), data.machine.isMirrored(), data.machine.variant(), entity.getMainArm(),
                entity.getMainHandItem().getItem(), entity.getOffhandItem().getItem());
        if (entry.sample.matches(frame, entity.tickCount, time, data.machine.phaseElapsedUs(), data.machine.phaseDurationUs())
                && signature.equals(entry.signature) && entry.generation == generation) {
            return Optional.ofNullable(entry.displayed);
        }
        if (entry.generation >= 0 && entry.generation != generation) {
            entry.freeze.clear();
            entry.from = null;
            entry.displayed = null;
        }
        if (entry.signature != null && (entry.signature.weapon != signature.weapon || entry.signature.offhand != signature.offhand)) {
            entry.freeze.clear();
            entry.from = null;
            entry.displayed = null; // disarms and item changes do not retain the old weapon pose
        }
        CombatPose live = entity == Minecraft.getInstance().player && !PoseSheet.running()
                ? entry.freeze.sample(signature.serial, frameNanos, () -> BodyFollow.apply(entity, CombatPose.of(entity, time).orElse(null), time))
                : BodyFollow.apply(entity, CombatPose.of(entity, time).orElse(null), time);
        boolean release = live != null && live.phase() == Phase.RELEASE;
        if (PoseSheet.running() || release || entry.freeze.holding(frameNanos)) {
            entry.from = null; // release stays locked to the hit arc; frozen sheets/samples must remain exact
        } else if (entry.displayed != null && (entry.corrected || handOver(entry.signature, signature))) {
            entry.from = entry.displayed;
            entry.transitionAt = frameNanos;
            entry.chained = signature.phase == Phase.WINDUP && entry.displayed.weight() > 0.02;
        }
        if (signature.phase != Phase.WINDUP) {
            entry.chained = false;
        }
        if (entry.chained && live != null) {
            live = live.withWeight(1); // a combo or riposte goes straight from the last pose into the windup, not via rest
        }
        CombatPose displayed = live;
        if (entry.from != null) {
            double t = Math.max(0, Math.min(1, (frameNanos - entry.transitionAt) / (double) TRANSITION_NANOS));
            double blend = t * t * (3 - 2 * t);
            if (t == 1) entry.from = null;
            else if (live == null) displayed = entry.from.withWeight(entry.from.weight() * (1 - blend));
            else if (live.leftHanded() == entry.from.leftHanded() && live.kick() == entry.from.kick()) {
                displayed = live.blendFrom(entry.from, blend);
            }
        }
        entry.sample.record(frame, entity.tickCount, time, data.machine.phaseElapsedUs(), data.machine.phaseDurationUs());
        entry.signature = signature;
        entry.generation = generation;
        entry.displayed = displayed;
        entry.corrected = false;
        return Optional.ofNullable(displayed);
    }

    /**
     * A change the pose doesn't carry smoothly by itself (see {@link PoseBlend#continuous}), or a heavy upgrade or weapon
     * morph that rewinds the windup.
     */
    private static boolean handOver(@Nullable Signature before, Signature now) {
        return before != null && (before.heavy != now.heavy || before.variant != now.variant
                || !PoseBlend.continuous(before.serial, before.type, before.mirrored, before.phase,
                        now.serial, now.type, now.mirrored, now.phase));
    }

    private record Signature(int serial, Phase phase, AttackType type, boolean heavy, boolean mirrored, int variant,
                             Object mainArm, Object weapon, Object offhand) {}

    private static final class Entry {
        final PresentationFreeze<CombatPose> freeze = new PresentationFreeze<>();
        final PoseSampleStamp sample = new PoseSampleStamp();
        long generation = -1;
        long transitionAt;
        boolean corrected;
        /** This windup started while the arm was still posed: it stays at full weight. */
        boolean chained;
        @Nullable Signature signature;
        @Nullable CombatPose from;
        @Nullable CombatPose displayed;
    }
}
