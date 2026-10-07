package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.core.AttackType;
import com.steelclash.core.Guard;
import com.steelclash.core.Phase;
import com.steelclash.profile.WeaponProfile;
import java.util.Optional;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
        if (swing != null && swing.type() == AttackType.KICK) {
            return false; // kicks break guards, they're never parried
        }
        CombatData data = defender.getData(ModAttachments.COMBAT);
        if (swing != null && tryCounter(defender, data, swing)) {
            return true;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
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
        if (swing != null && swing.type() == AttackType.JAB && data.machine.type() == AttackType.JAB
                && (data.machine.phase() == Phase.WINDUP || data.machine.phase() == Phase.RELEASE)) {
            // Jabs block jabs (Chivalry 2): the incoming one is turned aside, the defender's carries on.
            Feedback.parry(defender, attacker);
            Combat.stagger(attacker, attacker.getData(ModAttachments.COMBAT), Config.PARRIED_STAGGER_TICKS.get() / 2, true);
            return true;
        }
        if (data.machine.isActiveParry()) {
            activeParry(defender, data, attacker, swing, guard.get());
            return true;
        }
        if (data.machine.forgiveIntoParry(Config.PARRY_FORGIVENESS_TICKS.get(), guard.get().recovery())) {
            Combat.sync(defender, data, true); // the attack it started is gone: the guard is back up
        }
        if (data.machine.phase() != Phase.PARRY) {
            return false;
        }

        float staminaDamage = swing != null ? swing.staminaDamage() : Config.VANILLA_MELEE_STAMINA_DAMAGE.get().floatValue();
        boolean exhausted = data.stamina.spend(staminaDamage * guard.get().staminaMult());
        Feedback.parry(defender, attacker);
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
     * Chivalry 2 counter: answering an incoming attack with the same attack type, started shortly before it lands,
     * parries it and fast-forwards your own swing so it lands first.
     */
    private static boolean tryCounter(LivingEntity defender, CombatData data, SwingContext.Active swing) {
        if (data.machine.phase() != Phase.WINDUP || data.machine.type() != swing.type()
                || !Combat.isWeaponAttack(swing.type()) || !(swing.attacker() instanceof LivingEntity attacker)) {
            return false;
        }
        int attackerWindupUs = attacker.getData(ModAttachments.COMBAT).machine.timings().windupUs();
        if (data.machine.phaseTick() > Guard.counterWindow(Config.COUNTER_WINDOW_TICKS.get(), attackerWindupUs)) {
            return false;
        }
        Optional<WeaponProfile.GuardSpec> guard = Combat.currentProfile(defender, data).flatMap(WeaponProfile::guard);
        if (guard.isEmpty() || !Guard.inCone(CombatMath.viewYaw(defender), defender.getX(), defender.getZ(),
                attacker.getX(), attacker.getZ(), guard.get().cone())) {
            return false;
        }
        data.stamina.spend(swing.staminaDamage() * guard.get().staminaMult() * 0.5f);
        data.machine.counter(Config.COUNTER_RELEASE_TICKS.get(), Config.COUNTER_ACTIVE_PARRY_TICKS.get());
        Combat.sync(defender, data, true);
        Feedback.parry(defender, attacker);
        Combat.stagger(attacker, attacker.getData(ModAttachments.COMBAT), Config.PARRIED_STAGGER_TICKS.get(), true);
        return true;
    }

    /**
     * Active parry (Chivalry 2): a riposte or counter in progress parries a frontal hit without stopping. The attacker
     * is parried as usual; the defender pays the block's stamina but can't be disarmed by it, and the active parry
     * lasts a little longer.
     */
    private static void activeParry(LivingEntity defender, CombatData data, LivingEntity attacker, @Nullable SwingContext.Active swing,
                                    WeaponProfile.GuardSpec guard) {
        float staminaDamage = swing != null ? swing.staminaDamage() : Config.VANILLA_MELEE_STAMINA_DAMAGE.get().floatValue();
        data.stamina.spend(staminaDamage * guard.staminaMult());
        data.machine.extendActiveParry(Config.ACTIVE_PARRY_EXTEND_TICKS.get());
        Feedback.parry(defender, attacker);
        if (swing != null) {
            Combat.stagger(attacker, attacker.getData(ModAttachments.COMBAT), Config.PARRIED_STAGGER_TICKS.get(), true);
        }
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
        float base = swing != null ? swing.staminaDamage()
                : source.is(DamageTypeTags.IS_PROJECTILE) ? Config.PROJECTILE_STAMINA_DAMAGE.get().floatValue()
                : Config.VANILLA_MELEE_STAMINA_DAMAGE.get().floatValue();
        double mult = tower ? Config.TOWER_SHIELD_STAMINA_MULT.get() : Config.BASIC_SHIELD_STAMINA_MULT.get();
        CombatData data = blocker.getData(ModAttachments.COMBAT);
        if (data.stamina.spend((float) (base * mult))) {
            breakShieldGuard(blocker, data, shield);
        }

        if (source.getEntity() != null) {
            Feedback.shieldBlock(blocker, source.getEntity());
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
        Feedback.guardBreak(blocker);
        Combat.stagger(blocker, data, Config.GUARD_BREAK_STAGGER_TICKS.get(), false);
        SteelClash.LOGGER.debug("{} guard broken", blocker);
    }
}
