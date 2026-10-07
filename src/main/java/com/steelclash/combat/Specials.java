package com.steelclash.combat;

import com.steelclash.profile.WeaponProfile;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Area effects of weapon specials. Lunges and sweeps are plain attacks; slams also shake everyone nearby. */
public final class Specials {
    public static final double SLAM_RADIUS = 2.5;
    private static final double SLAM_DISTANCE = 2.0;
    private static final int SLAM_STAGGER_TICKS = 10;

    private Specials() {
    }

    /**
     * Ground slam at the end of the release: everyone (except allies) within {@link #SLAM_RADIUS} of the impact point
     * is knocked back, staggered and loses stamina, guarding or not. The impact itself already hit as an overhead.
     */
    public static void slam(LivingEntity attacker, WeaponProfile.AttackSpec spec) {
        Vec3 look = attacker.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z).normalize();
        Vec3 impact = attacker.position().add(horizontal.scale(SLAM_DISTANCE));
        List<LivingEntity> caught = attacker.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(impact, impact).inflate(SLAM_RADIUS, 1.5, SLAM_RADIUS),
                e -> e != attacker && e.isAlive() && !e.isSpectator() && !Allies.areAllies(attacker, e)
                        && e.position().distanceTo(impact) <= SLAM_RADIUS);
        for (LivingEntity target : caught) {
            CombatData data = target.getData(ModAttachments.COMBAT);
            data.stamina.spend(spec.staminaDamage());
            if (target.isBlocking()) {
                target.stopUsingItem();
            }
            Combat.stagger(target, data, SLAM_STAGGER_TICKS, false);
            target.knockback(0.6, impact.x - target.getX(), impact.z - target.getZ());
            target.hurtMarked = true;
        }
        attacker.level().playSound(null, impact.x, impact.y, impact.z, SoundEvents.ANVIL_LAND, attacker.getSoundSource(), 0.6f, 0.5f);
        if (attacker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.1, impact.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CRIT, impact.x, impact.y + 0.2, impact.z, 20, SLAM_RADIUS * 0.4, 0.1, SLAM_RADIUS * 0.4, 0.3);
        }
    }
}
