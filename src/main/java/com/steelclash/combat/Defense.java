package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.core.Guard;
import com.steelclash.core.Phase;
import com.steelclash.profile.WeaponProfile;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import org.jetbrains.annotations.Nullable;

/** Weapon parries, shield blocks and their stamina consequences. Server side. */
public final class Defense {
    /** Spartan Shields' tower shield tag; referenced by id so Spartan Shields stays optional. */
    private static final TagKey<Item> TOWER_SHIELDS = ItemTags.create(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("spartanshieldsunofficial", "tower_shields"));

    private Defense() {
    }

    /**
     * Tries to parry incoming melee damage with a raised weapon guard.
     *
     * @return true if the damage was parried and must be cancelled
     */
    public static boolean tryParry(LivingEntity defender, DamageSource source, @Nullable SwingContext.Active swing) {
        if (!defender.hasData(ModAttachments.COMBAT)) {
            return false;
        }
        CombatData data = defender.getData(ModAttachments.COMBAT);
        if (data.machine.phase() != Phase.PARRY || !(source.getEntity() instanceof LivingEntity attacker)) {
            return false;
        }
        boolean melee = swing != null || source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
        if (!melee) {
            return false;
        }
        Optional<WeaponProfile.GuardSpec> guard = Combat.currentProfile(defender, data).flatMap(WeaponProfile::guard);
        if (guard.isEmpty() || !Guard.inCone(CombatMath.viewYaw(defender), defender.getX(), defender.getZ(),
                attacker.getX(), attacker.getZ(), guard.get().cone())) {
            return false;
        }

        float staminaDamage = swing != null ? swing.spec().staminaDamage() : Config.VANILLA_MELEE_STAMINA_DAMAGE.get().floatValue();
        boolean exhausted = data.stamina.spend(staminaDamage * guard.get().staminaMult());
        clashEffects(defender, attacker);
        if (exhausted) {
            Disarm.disarm(defender);
            Combat.stagger(defender, data, Config.GUARD_BREAK_STAGGER_TICKS.get(), false);
        } else {
            data.machine.parrySucceeded(Config.RIPOSTE_WINDOW_TICKS.get());
            Combat.sync(defender, data, true);
        }
        if (swing != null) {
            // The parried attacker reels back; they may still parry the coming riposte.
            Combat.stagger(attacker, attacker.getData(ModAttachments.COMBAT), Config.PARRIED_STAGGER_TICKS.get(), true);
        }
        return true;
    }

    /**
     * Narrows vanilla's 180° shield to the shield's guard cone, drains stamina, and breaks the guard when stamina runs
     * out. Covers projectiles too (tower shields stop arrows from the front only).
     */
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!event.getBlocked()) {
            return;
        }
        LivingEntity blocker = event.getEntity();
        DamageSource source = event.getDamageSource();
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return;
        }
        ItemStack shield = blocker.getUseItem();
        boolean tower = shield.is(TOWER_SHIELDS);
        double cone = tower ? Config.TOWER_SHIELD_CONE.get() : Config.BASIC_SHIELD_CONE.get();
        if (!Guard.inCone(CombatMath.viewYaw(blocker), blocker.getX(), blocker.getZ(), from.x, from.z, cone)) {
            event.setBlocked(false);
            return;
        }

        SwingContext.Active swing = SwingContext.forAttacker(source.getEntity());
        float base = swing != null ? swing.spec().staminaDamage()
                : source.is(DamageTypeTags.IS_PROJECTILE) ? Config.PROJECTILE_STAMINA_DAMAGE.get().floatValue()
                : Config.VANILLA_MELEE_STAMINA_DAMAGE.get().floatValue();
        double mult = tower ? Config.TOWER_SHIELD_STAMINA_MULT.get() : Config.BASIC_SHIELD_STAMINA_MULT.get();
        CombatData data = blocker.getData(ModAttachments.COMBAT);
        if (data.stamina.spend((float) (base * mult))) {
            breakShieldGuard(blocker, data, shield);
        }

        int bounce = Config.SHIELD_BOUNCE_STAGGER_TICKS.get();
        if (swing != null && bounce > 0 && source.getEntity() instanceof LivingEntity attacker) {
            Combat.stagger(attacker, attacker.getData(ModAttachments.COMBAT), bounce, true);
        }
    }

    private static void breakShieldGuard(LivingEntity blocker, CombatData data, ItemStack shield) {
        blocker.stopUsingItem();
        if (blocker instanceof Player player && !shield.isEmpty()) {
            player.getCooldowns().addCooldown(shield.getItem(), Config.SHIELD_BREAK_COOLDOWN_TICKS.get());
        }
        blocker.level().playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(),
                SoundEvents.SHIELD_BREAK, blocker.getSoundSource(), 1f, 0.9f);
        Combat.stagger(blocker, data, Config.GUARD_BREAK_STAGGER_TICKS.get(), false);
        SteelClash.LOGGER.debug("{} guard broken", blocker);
    }

    private static void clashEffects(LivingEntity defender, Entity attacker) {
        Vec3 mid = defender.getEyePosition().add(attacker.getEyePosition()).scale(0.5);
        defender.level().playSound(null, mid.x, mid.y, mid.z, SoundEvents.ANVIL_PLACE, defender.getSoundSource(), 0.35f, 1.8f);
        if (defender.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 12, 0.15, 0.15, 0.15, 0.4);
        }
    }
}
