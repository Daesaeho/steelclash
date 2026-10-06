package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.ai.ClashBrain;
import com.steelclash.ai.ClashSpacingGoal;
import com.steelclash.core.Phase;
import com.steelclash.entity.TrainingDummy;
import com.steelclash.profile.WeaponProfiles;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
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
        LagCompensation.record(entity);
        if (entity instanceof ServerPlayer player) {
            HealthRegen.tick(player, player.getData(ModAttachments.COMBAT));
        }
        if (entity instanceof ServerPlayer player && player.tickCount % 4 == 0) {
            Disarm.tryPickUp(player);
        }
        if (entity instanceof PathfinderMob pathfinder && !(entity instanceof TrainingDummy) && ClashBrain.manages(pathfinder)) {
            ClashBrain.tick(pathfinder, pathfinder.getData(ModAttachments.COMBAT));
        } else if (entity instanceof Mob mob && MobCombat.isFighter(mob) && !(mob instanceof TrainingDummy) && mob.getTarget() != null) {
            MobCombat.tickDefense(mob, mob.getData(ModAttachments.COMBAT), MobCombat.parryChance(mob));
        }
        if (entity.hasData(ModAttachments.COMBAT)) {
            Combat.tickServer(entity, entity.getData(ModAttachments.COMBAT));
        }
    }

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
        if (Defense.tryParry(event.getEntity(), event.getSource(), swing)) {
            event.setCanceled(true);
            return;
        }
        if (swing != null) {
            event.setAmount(event.getAmount() * swing.damageMult());
        }
    }

    /** Flinch: taking real damage during your own windup interrupts it, unless the heavy has hyper armor. */
    @SubscribeEvent
    static void onDamageTaken(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (event.getNewDamage() > 0) {
            entity.getData(ModAttachments.COMBAT).lastHurtAt = entity.level().getGameTime();
        }
        if (event.getNewDamage() <= 0 || !entity.hasData(ModAttachments.COMBAT) || Config.FLINCH_TICKS.get() <= 0) {
            return;
        }
        CombatData data = entity.getData(ModAttachments.COMBAT);
        if (data.machine.phase() == Phase.WINDUP && !Combat.hasHyperArmor(entity, data)) {
            Combat.stagger(entity, data, Config.FLINCH_TICKS.get(), true);
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

    /** Hits held for lagged defenders land (or are parried) at the end of the tick. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        LagCompensation.tick();
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
