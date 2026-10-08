package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.ai.ClashBrain;
import com.steelclash.ai.ClashSpacingGoal;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.entity.TrainingDummy;
import com.steelclash.profile.WeaponProfiles;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SteelClash.MOD_ID)
public final class CombatEvents {
    /** Archetypes that need both hands: they can't raise a shield. */
    private static final Set<String> TWO_HANDED = Set.of("two_handed", "polearm", "spear");

    private CombatEvents() {
    }

    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (entity.level().isClientSide()) {
            if (entity.hasData(ModAttachments.COMBAT)) {
                Combat.tickClient(entity, entity.getData(ModAttachments.COMBAT));
            }
            return;
        }
        CombatProfiler.begin(CombatProfiler.Section.LAG);
        try {
            LagCompensation.record(entity);
        } finally {
            CombatProfiler.end(CombatProfiler.Section.LAG);
        }
        if (entity instanceof ServerPlayer player) {
            Downed.tick(player, player.getData(ModAttachments.COMBAT));
            HealthRegen.tick(player, player.getData(ModAttachments.COMBAT));
        }
        if (entity instanceof Mob mob && Config.MOBS_IGNORE_DOWNED.get() && mob.getTarget() != null
                && Downed.isDowned(mob.getTarget())) {
            mob.setTarget(null); // leave the downed to their allies; they find someone standing
        }
        if (entity instanceof ServerPlayer player && player.tickCount % 4 == 0) {
            Disarm.tryPickUp(player);
        }
        CombatProfiler.begin(CombatProfiler.Section.AI);
        try {
            if (entity instanceof Mob mob && !(mob instanceof TrainingDummy)) {
                if (MobCombat.isFighter(mob)) {
                    CombatData data = mob.getData(ModAttachments.COMBAT);
                    if (mob instanceof PathfinderMob pathfinder && ClashBrain.manages(pathfinder)) {
                        ClashBrain.tick(pathfinder, data);
                    } else {
                        ClashBrain.stop(mob, data);
                        if (mob.getTarget() != null) {
                            MobCombat.tickDefense(mob, data, MobCombat.parryChance(mob));
                        }
                    }
                    MobCombat.keepAggressive(mob, data);
                    Sidearms.tick(mob, data);
                } else if (mob.hasData(ModAttachments.COMBAT)) {
                    // A datapack can remove the fighter tag while the mob is loaded.
                    ClashBrain.stop(mob, mob.getData(ModAttachments.COMBAT));
                }
            }
        } finally {
            CombatProfiler.end(CombatProfiler.Section.AI);
        }
        if (entity.hasData(ModAttachments.COMBAT)) {
            CombatProfiler.begin(CombatProfiler.Section.STATE);
            try {
                Combat.tickServer(entity, entity.getData(ModAttachments.COMBAT));
            } finally {
                CombatProfiler.end(CombatProfiler.Section.STATE);
            }
        }
    }

    /** A jump costs stamina while fighting (Chivalry 2: 12), and a jump with no stamina left is still allowed. */
    @SubscribeEvent
    static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || Config.JUMP_STAMINA_COST.get() <= 0
                || !player.hasData(ModAttachments.COMBAT)) {
            return;
        }
        CombatData data = player.getData(ModAttachments.COMBAT);
        long sinceFight = player.level().getGameTime() - Math.max(data.lastCombatAt, data.lastHurtAt);
        if (sinceFight <= IN_COMBAT_TICKS) {
            data.stamina.spend(Config.JUMP_STAMINA_COST.get().floatValue());
        }
    }

    /** How long after attacking, guarding or being hurt a player still counts as fighting (for the jump cost). */
    private static final int IN_COMBAT_TICKS = 100;

    /**
     * The damage pipeline for every hit, in order: telegraph interception (vanilla fighter-mob melee becomes a windup),
     * weapon parry, then the swing's damage multiplier. Runs before vanilla shield blocking.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        SwingContext.Active swing = SwingContext.forAttacker(event.getSource().getEntity());
        if (swing == null && MobCombat.interceptVanillaMelee(event.getSource())) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
            switch (RangedDefense.defend(event.getEntity(), projectile)) {
                case DEFLECTED -> {
                    event.setCanceled(true); // the projectile bounces off
                    return;
                }
                case WEAPON_BLOCKED -> event.setAmount(RangedDefense.blockedDamage(event.getAmount()));
                case NONE -> {
                }
            }
            if (RangedDefense.isHeadshot(event.getEntity(), projectile)) {
                event.setAmount(event.getAmount() * Config.HEADSHOT_MULTIPLIER.get().floatValue());
                RangedDefense.headshotFeedback(projectile.getOwner());
            }
        }
        if (swing != null && swing.attacker() instanceof LivingEntity attacker && Allies.areAllies(attacker, event.getEntity())) {
            // Friendly fire: never parried, blocked or countered, and scaled down (0 = none).
            event.setAmount(event.getAmount() * swing.damageMult() * Allies.damageScale());
            if (event.getAmount() <= 0) {
                event.setCanceled(true);
            }
            return;
        }
        if (Defense.tryParry(event.getEntity(), event.getSource(), swing)) {
            event.setCanceled(true);
            return;
        }
        if (swing != null) {
            event.setAmount(event.getAmount() * swing.damageMult());
        }
    }

    /** Flinch: taking real damage during your own windup interrupts it, unless the heavy has hyper armor or it's a kick. */
    @SubscribeEvent
    static void onDamageTaken(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (event.getNewDamage() > 0) {
            entity.getData(ModAttachments.COMBAT).lastHurtAt = entity.level().getGameTime();
            if (entity instanceof Player player && !entity.level().isClientSide()) {
                Downed.onReviverHurt(player);
            }
            RangedDefense.interruptDraw(entity);
        }
        if (event.getNewDamage() <= 0 || !entity.hasData(ModAttachments.COMBAT) || Config.FLINCH_TICKS.get() <= 0) {
            return;
        }
        SwingContext.Active swing = SwingContext.forAttacker(event.getSource().getEntity());
        if (swing != null && swing.type() == AttackType.KICK) {
            return; // a kick doesn't interrupt an attack (Chivalry 2); it only breaks guards and staggers the idle
        }
        CombatData data = entity.getData(ModAttachments.COMBAT);
        if (data.machine.phase() == Phase.WINDUP && !Combat.hasHyperArmor(entity, data)) {
            Combat.stagger(entity, data, Config.FLINCH_TICKS.get(), true);
        }
    }

    /** Chivalry 2: a lethal blow downs a player instead (if an ally is around to revive them). */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && Downed.onLethal(player, event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** Downed players don't heal: only a revive gets them up. */
    @SubscribeEvent
    static void onHeal(LivingHealEvent event) {
        if (Downed.isDowned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onChangeTarget(LivingChangeTargetEvent event) {
        if (Config.MOBS_IGNORE_DOWNED.get() && event.getNewAboutToBeSetTarget() != null
                && Downed.isDowned(event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
        }
    }

    /** Logging out while downed is bleeding out (no escaping death by disconnecting). */
    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (Downed.isDowned(event.getEntity())) {
            Downed.bleedOut(event.getEntity());
        }
    }

    /** Downed players can't use, place, break or hit anything. */
    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfDowned(event);
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfDowned(event);
    }

    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfDowned(event);
    }

    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfDowned(event);
    }

    @SubscribeEvent
    static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelIfDowned(event);
    }

    @SubscribeEvent
    static void onAttackEntity(AttackEntityEvent event) {
        if (Downed.isDowned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void cancelIfDowned(PlayerInteractEvent event) {
        if (Downed.isDowned(event.getEntity()) && event instanceof ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onShieldBlock(LivingShieldBlockEvent event) {
        Defense.onShieldBlock(event);
    }

    /** Mobs spawning for real (natural, spawner, egg, command) may get armed once they've joined; see {@link MobGear}. */
    @SubscribeEvent
    static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobGear.markForArming(event.getEntity());
        Sidearms.markForArming(event.getEntity());
    }

    /** Fighter mobs get the spacing goal the bot brain uses to hold back and circle; freshly spawned ones get gear. */
    @SubscribeEvent
    static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PathfinderMob mob)
                || mob instanceof TrainingDummy || !MobCombat.isFighter(mob)) {
            return;
        }
        if (!event.loadedFromDisk()) {
            MobGear.armIfPending(mob);
            Sidearms.armIfPending(mob);
        }
        boolean installed = mob.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof ClashSpacingGoal);
        if (!installed) {
            mob.goalSelector.addGoal(1, new ClashSpacingGoal(mob));
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ClashBrain.TOKENS.clear();
        LagCompensation.clear();
    }

    @SubscribeEvent
    static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity entity) {
            ClashBrain.TOKENS.removeEntity(entity.getId());
        }
    }

    /** Hits held for lagged defenders land (or are parried) at the end of the tick. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        LagCompensation.tick();
        CombatProfiler.endTick();
    }

    /** Two-handed weapons need both hands: no raising a shield in the offhand. */
    @SubscribeEvent
    static void onStartUsingItem(LivingEntityUseItemEvent.Start event) {
        if (!event.getItem().canPerformAction(ItemAbilities.SHIELD_BLOCK)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        WeaponProfiles.resolve(entity.getMainHandItem(), entity.level().registryAccess())
                .filter(resolved -> TWO_HANDED.contains(resolved.profile().archetype()))
                .ifPresent(resolved -> event.setCanceled(true));
    }

    /** Chivalry 2 has no jump crits; heavy and jump attacks get their own multipliers later. */
    @SubscribeEvent
    static void onCriticalHit(CriticalHitEvent event) {
        if (SwingContext.forAttacker(event.getEntity()) != null) {
            event.setCriticalHit(false);
        }
    }

    /** Our arcs decide who gets hit; vanilla's sweep would double up on it. */
    @SubscribeEvent
    static void onSweep(SweepAttackEvent event) {
        if (SwingContext.forAttacker(event.getEntity()) != null) {
            event.setSweeping(false);
        }
    }
}
