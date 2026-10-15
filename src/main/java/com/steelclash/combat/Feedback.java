package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.combat.SwingTracer.Clank;
import com.steelclash.combat.SwingTracer.Contact;
import com.steelclash.net.FeedbackPayload;
import com.steelclash.net.ModNetwork;
import com.steelclash.sound.ModSounds;
import java.util.function.Supplier;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Sounds, particles and per-player feel (camera shake, hit-stop) for combat events. Server side. */
public final class Feedback {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 1.1f);
    private static final Map<Entity, FeedbackPayload> LAST_PAYLOAD = new WeakHashMap<>();

    private Feedback() {
    }

    /** Most recent local-feel packet for debug inspection; weak keys and one value per participant. */
    @Nullable
    public static FeedbackPayload lastPayload(Entity entity) {
        return LAST_PAYLOAD.get(entity);
    }

    public static void windup(LivingEntity entity) {
        sound(entity, entity.position(), ModSounds.WINDUP, 1f);
    }

    public static void swing(LivingEntity entity, boolean heavy) {
        sound(entity, entity.position(), heavy ? ModSounds.SWING_HEAVY : ModSounds.SWING_LIGHT, 1f);
    }

    public static void feint(LivingEntity entity) {
        sound(entity, entity.position(), ModSounds.FEINT, 1f);
    }

    /** A swing connected with flesh without captured contact metadata. */
    public static void hit(LivingEntity attacker, LivingEntity target, boolean heavy) {
        hit(attacker, target, heavy, null);
    }

    /** Plain effects can still carry the authoritative contact used to gate local hit-stop. */
    static void hit(LivingEntity attacker, LivingEntity target, boolean heavy, @Nullable Contact contact) {
        Vec3 point = contactPoint(attacker, target);
        sound(target, point, ModSounds.HIT, 1f);
        if (Config.BLOOD_PARTICLES.get()) {
            blood(target, point, heavy ? 14 : 8);
        }
        landed(attacker, target, heavy, contact);
    }

    /** The attacker's hit-stop and the target's camera shake. */
    static void landed(LivingEntity attacker, LivingEntity target, boolean heavy) {
        landed(attacker, target, heavy, null);
    }

    static void landed(LivingEntity attacker, LivingEntity target, boolean heavy, @Nullable Contact contact) {
        sendContact(attacker, FeedbackPayload.Kind.LANDED, heavy ? 1.5f : 1f, contact,
                contact == null ? recipientSerial(attacker) : contact.attackerRecipientSerial());
        sendContact(target, FeedbackPayload.Kind.TAKEN, heavy ? 1.5f : 1f, contact, recipientSerial(target));
    }

    static void blood(LivingEntity target, Vec3 point, int count) {
        particles(target, BLOOD, point, count, 0.15, 0.1);
    }

    public static void parry(LivingEntity defender, Entity attacker) {
        if (!HitFeel.parried(defender, attacker)) {
            parried(defender, attacker, defender.getEyePosition().add(attacker.getEyePosition()).scale(0.5));
        }
    }

    static void parried(LivingEntity defender, Entity attacker, Vec3 point) {
        parried(defender, attacker, point, contactFor(attacker));
    }

    static void parried(LivingEntity defender, Entity attacker, Vec3 point, @Nullable Contact contact) {
        sound(defender, point, ModSounds.PARRY, 1f);
        particles(defender, ParticleTypes.CRIT, point, 12, 0.15, 0.4);
        sendContact(defender, FeedbackPayload.Kind.PARRIED, 1f, contact, recipientSerial(defender), true);
        sendContact(attacker, FeedbackPayload.Kind.PARRIED, 1.3f, contact,
                contact == null ? recipientSerial(attacker) : contact.attackerRecipientSerial());
    }

    public static void shieldBlock(LivingEntity blocker, Entity attacker) {
        Contact contact = contactFor(attacker);
        sendContact(blocker, FeedbackPayload.Kind.BLOCKED, 0.8f, contact, recipientSerial(blocker));
        sendContact(attacker, FeedbackPayload.Kind.BLOCKED, 1f, contact,
                contact == null ? recipientSerial(attacker) : contact.attackerRecipientSerial());
    }

    public static void clank(LivingEntity entity, Vec3 where) {
        sound(entity, where, ModSounds.CLANK, 1f);
        particles(entity, ParticleTypes.CRIT, where, 8, 0.05, 0.25);
        send(entity, FeedbackPayload.Kind.CLANK, 1f);
    }

    public static void clank(LivingEntity entity, Clank clank) {
        sound(entity, clank.point(), ModSounds.CLANK, 1f);
        particles(entity, ParticleTypes.CRIT, clank.point(), 8, 0.05, 0.25);
        sendPayload(entity, FeedbackPayload.Kind.CLANK, 1f, new FeedbackPayload.Contact(clank.point(), clank.point(), Vec3.ZERO,
                clank.attackIdentity(), clank.serverTick(), clank.progress(), clank.normal(),
                clank.collisionProvenance()), clank.attackIdentity().serial(), false);
    }

    public static void kick(LivingEntity target, boolean guardBroken) {
        sound(target, target.position(), guardBroken ? ModSounds.GUARD_BREAK : ModSounds.KICK, 1f);
        send(target, FeedbackPayload.Kind.TAKEN, 0.8f);
    }

    public static void guardBreak(LivingEntity entity) {
        sound(entity, entity.position(), ModSounds.GUARD_BREAK, 1f);
    }

    public static void disarm(LivingEntity entity) {
        sound(entity, entity.position(), ModSounds.DISARM, 1f);
    }

    private static Vec3 contactPoint(LivingEntity attacker, LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter().add(0, target.getBbHeight() * 0.15, 0);
        Vec3 toAttacker = attacker.getEyePosition().subtract(center);
        return center.add(toAttacker.normalize().scale(target.getBbWidth() * 0.5));
    }

    private static void sound(Entity source, Vec3 at, Supplier<SoundEvent> sound, float volume) {
        sound(source, at, sound, volume, 1f);
    }

    static void sound(Entity source, Vec3 at, Supplier<SoundEvent> sound, float volume, float pitch) {
        source.level().playSound(null, at.x, at.y, at.z, sound.get(), source.getSoundSource(), volume, pitch);
    }

    private static void particles(Entity source, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        if (source.level() instanceof ServerLevel level) {
            level.sendParticles(type, at.x, at.y, at.z, count, spread, spread, spread, speed);
        }
    }

    private static void send(Entity entity, FeedbackPayload.Kind kind, float strength) {
        sendPayload(entity, kind, strength, null, recipientSerial(entity));
    }

    private static void sendContact(Entity entity, FeedbackPayload.Kind kind, float strength, @Nullable Contact contact,
                                    int recipientSerial) {
        sendContact(entity, kind, strength, contact, recipientSerial, false);
    }

    private static void sendContact(Entity entity, FeedbackPayload.Kind kind, float strength, @Nullable Contact contact,
                                    int recipientSerial, boolean parryDefender) {
        FeedbackPayload.Contact metadata = contact == null ? null : new FeedbackPayload.Contact(contact.capturedPoint(),
                contact.entryPoint(), contact.motion(), contact.attackIdentity(), contact.serverTick(), contact.progress(),
                contact.normal(), contact.collisionProvenance());
        sendPayload(entity, kind, strength, metadata, recipientSerial, parryDefender);
    }

    private static void sendPayload(Entity entity, FeedbackPayload.Kind kind, float strength,
                                    @Nullable FeedbackPayload.Contact contact, int recipientSerial) {
        sendPayload(entity, kind, strength, contact, recipientSerial, false);
    }

    private static void sendPayload(Entity entity, FeedbackPayload.Kind kind, float strength,
                                    @Nullable FeedbackPayload.Contact contact, int recipientSerial, boolean parryDefender) {
        FeedbackPayload payload = new FeedbackPayload(kind, strength, contact, recipientSerial, parryDefender);
        LAST_PAYLOAD.put(entity, payload);
        if (entity instanceof ServerPlayer player) {
            ModNetwork.sendTo(player, payload);
        }
    }

    private static int recipientSerial(Entity entity) {
        return entity.hasData(ModAttachments.COMBAT)
                ? entity.getData(ModAttachments.COMBAT).machine.attackSerial() : -1;
    }

    @Nullable
    private static Contact contactFor(Entity attacker) {
        SwingContext.Active swing = SwingContext.forAttacker(attacker);
        return swing == null ? null : swing.contact();
    }
}
