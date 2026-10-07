package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.net.DownedPayload;
import com.steelclash.net.ModNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Chivalry 2's downed state, for players (report §18.2, architecture plan §21.1). A lethal blow that ordinary armour
 * and totems didn't stop puts the player on the ground with a little health instead of killing them, provided an
 * ally is around to help. Downed, they crawl, can't fight or use items, and bleed out after {@code bleedOutSeconds}.
 * An ally crouching beside them for {@code reviveSeconds} gets them back up with part of their health; taking damage
 * restarts the revive. Another lethal blow finishes them. Damage that bypasses invulnerability ({@code /kill}, the
 * void) kills outright, and logging out while downed counts as bleeding out.
 */
public final class Downed {
    public static final ResourceKey<DamageType> BLED_OUT = ResourceKey.create(Registries.DAMAGE_TYPE, SteelClash.id("bled_out"));
    private static final ResourceLocation CRAWL = SteelClash.id("downed_crawl");
    private static final ResourceLocation NO_JUMP = SteelClash.id("downed_no_jump");
    /** How often the bleed-out countdown is re-sent while nothing else changes, ticks. */
    private static final int SYNC_INTERVAL = 10;

    private Downed() {
    }

    public static boolean isDowned(LivingEntity entity) {
        return entity.hasData(ModAttachments.COMBAT) && entity.getData(ModAttachments.COMBAT).isDowned();
    }

    /**
     * The player is about to die from {@code source}: put them down instead, if the rules allow.
     *
     * @return true if the death must be cancelled
     */
    public static boolean onLethal(Player player, DamageSource source) {
        if (!Config.DOWNED.get() || player.level().isClientSide() || player.isSpectator()) {
            return false;
        }
        CombatData data = player.getData(ModAttachments.COMBAT);
        if (data.isDowned() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(BLED_OUT)
                || !allyNearby(player)) {
            return false; // finished off, /kill or the void, or nobody to come for them
        }
        down(player, data);
        return true;
    }

    private static boolean allyNearby(Player player) {
        double range = Config.DOWNED_ALLY_RANGE.get();
        if (range <= 0) {
            return true;
        }
        return player.level().players().stream().anyMatch(other -> other != player && other.isAlive() && !other.isSpectator()
                && !isDowned(other) && Allies.areAllies(player, other) && other.distanceToSqr(player) <= range * range);
    }

    static void down(Player player, CombatData data) {
        player.setHealth((float) Math.min(player.getMaxHealth(), Config.DOWNED_HEALTH.get()));
        Combat.cancel(player, data);
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        data.downedTicksLeft = Config.BLEED_OUT_SECONDS.get() * 20;
        data.reviveTicks = 0;
        data.reviverId = -1;
        player.setForcedPose(Pose.SWIMMING); // crawling
        player.setSprinting(false);
        modifier(player.getAttribute(Attributes.MOVEMENT_SPEED), CRAWL, -0.7);
        modifier(player.getAttribute(Attributes.JUMP_STRENGTH), NO_JUMP, -1);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 0.6f);
        for (Player other : player.level().players()) {
            if (other != player && Allies.areAllies(player, other) && other instanceof ServerPlayer ally) {
                ally.displayClientMessage(Component.translatable("steelclash.downed.ally_down", player.getDisplayName()), true);
            }
        }
        sync(player, data);
    }

    /** Each server tick for players: bleed out, or progress a revive. */
    public static void tick(Player player, CombatData data) {
        if (!data.isDowned()) {
            return;
        }
        player.setForcedPose(Pose.SWIMMING);
        @Nullable Player reviver = reviver(player);
        int needed = reviveTicksNeeded();
        if (reviver != null && (data.reviverId == -1 || data.reviverId == reviver.getId())) {
            data.reviverId = reviver.getId();
            data.reviveTicks++;
            if (data.reviveTicks >= needed) {
                revive(player, data);
                return;
            }
            sync(player, data);
        } else if (data.reviverId != -1) {
            data.reviverId = -1;
            data.reviveTicks = 0;
            sync(player, data);
        }
        data.downedTicksLeft--;
        if (data.downedTicksLeft <= 0) {
            bleedOut(player);
            return;
        }
        if (data.downedTicksLeft % SYNC_INTERVAL == 0) {
            sync(player, data);
        }
    }

    public static int reviveTicksNeeded() {
        return Math.max(1, (int) Math.round(Config.REVIVE_SECONDS.get() * 20));
    }

    /** An ally crouching close by who isn't fighting, down themselves, or hurt this very tick. */
    @Nullable
    private static Player reviver(Player downed) {
        double range = Config.REVIVE_RANGE.get();
        long now = downed.level().getGameTime();
        Player best = null;
        for (Player other : downed.level().players()) {
            if (other == downed || !other.isAlive() || other.isSpectator() || !other.isShiftKeyDown()
                    || !Allies.areAllies(downed, other) || other.distanceToSqr(downed) > range * range) {
                continue;
            }
            CombatData data = other.getData(ModAttachments.COMBAT);
            if (data.isDowned() || data.machine.isBusy() || data.lastHurtAt >= now) {
                continue;
            }
            if (best == null || other.distanceToSqr(downed) < best.distanceToSqr(downed)) {
                best = other;
            }
        }
        return best;
    }

    static void revive(Player player, CombatData data) {
        stand(player, data);
        player.setHealth((float) Math.max(1, player.getMaxHealth() * Config.REVIVE_HEALTH.get()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        sync(player, data);
    }

    /** Leaves the downed state (revived, or about to die for good). */
    private static void stand(Player player, CombatData data) {
        data.downedTicksLeft = -1;
        data.reviveTicks = 0;
        data.reviverId = -1;
        player.setForcedPose(null);
        remove(player.getAttribute(Attributes.MOVEMENT_SPEED), CRAWL);
        remove(player.getAttribute(Attributes.JUMP_STRENGTH), NO_JUMP);
    }

    /** The countdown ran out (or the player left): they die for good. */
    public static void bleedOut(Player player) {
        CombatData data = player.getData(ModAttachments.COMBAT);
        stand(player, data);
        sync(player, data);
        player.hurt(player.damageSources().source(BLED_OUT), Float.MAX_VALUE);
    }

    private static void modifier(@Nullable AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute != null && !attribute.hasModifier(id)) {
            attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void remove(@Nullable AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null) {
            attribute.removeModifier(id);
        }
    }

    static void sync(Player player, CombatData data) {
        ModNetwork.sendToTrackingAndSelf(player, new DownedPayload(player.getId(), data.downedTicksLeft, data.reviveTicks,
                data.reviverId));
    }
}
