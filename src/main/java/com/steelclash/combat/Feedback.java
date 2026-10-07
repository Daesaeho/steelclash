package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.net.FeedbackPayload;
import com.steelclash.net.ModNetwork;
import com.steelclash.sound.ModSounds;
import java.util.function.Supplier;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Sounds, particles and per-player feel (camera shake, hit-stop) for combat events. Server side. */
public final class Feedback {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 1.1f);

    private Feedback() {
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

    /** A swing connected with flesh. */
    public static void hit(LivingEntity attacker, LivingEntity target, boolean heavy) {
        Vec3 point = contactPoint(attacker, target);
        sound(target, point, ModSounds.HIT, 1f);
        if (Config.BLOOD_PARTICLES.get()) {
            particles(target, BLOOD, point, heavy ? 14 : 8, 0.15, 0.1);
        }
        send(attacker, FeedbackPayload.Kind.LANDED, heavy ? 1.5f : 1f);
        send(target, FeedbackPayload.Kind.TAKEN, heavy ? 1.5f : 1f);
    }

    public static void parry(LivingEntity defender, Entity attacker) {
        Vec3 point = defender.getEyePosition().add(attacker.getEyePosition()).scale(0.5);
        sound(defender, point, ModSounds.PARRY, 1f);
        particles(defender, ParticleTypes.CRIT, point, 12, 0.15, 0.4);
        send(defender, FeedbackPayload.Kind.PARRIED, 1f);
        send(attacker, FeedbackPayload.Kind.PARRIED, 1.3f);
    }

    public static void shieldBlock(LivingEntity blocker, Entity attacker) {
        send(blocker, FeedbackPayload.Kind.BLOCKED, 0.8f);
        send(attacker, FeedbackPayload.Kind.BLOCKED, 1f);
    }

    public static void clank(LivingEntity entity, Vec3 where) {
        sound(entity, where, ModSounds.CLANK, 1f);
        particles(entity, ParticleTypes.CRIT, where, 8, 0.05, 0.25);
        send(entity, FeedbackPayload.Kind.CLANK, 1f);
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
        source.level().playSound(null, at.x, at.y, at.z, sound.get(), source.getSoundSource(), volume, 1f);
    }

    private static void particles(Entity source, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        if (source.level() instanceof ServerLevel level) {
            level.sendParticles(type, at.x, at.y, at.z, count, spread, spread, spread, speed);
        }
    }

    private static void send(Entity entity, FeedbackPayload.Kind kind, float strength) {
        if (entity instanceof ServerPlayer player) {
            ModNetwork.sendTo(player, new FeedbackPayload(kind, strength));
        }
    }
}
