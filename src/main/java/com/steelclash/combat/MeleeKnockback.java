package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.core.AttackType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code meleeKnockback} setting: how hard an ordinary hit shoves its target. It scales the knockback of Steel
 * Clash weapon swings (players' and mobs') and of a player's plain punch, all of it: the base shove, a sprinting
 * hit's extra and the Knockback enchantment. Kicks, shield bashes and the slam shove on purpose and are left alone,
 * as is everything that isn't a melee hit of ours (arrows, explosions, a wolf's bite). Server thread only.
 */
public final class MeleeKnockback {
    /** The plain player hit in progress: who, at whom, and the game time it started. */
    @Nullable
    private static Player puncher;
    @Nullable
    private static Entity punched;
    private static long punchedAt;

    private MeleeKnockback() {
    }

    /** A player's vanilla attack is starting. Outside a swing it is a punch (a fist, or an item with no weapon profile). */
    static void attackStarting(Player attacker, Entity target) {
        if (!attacker.level().isClientSide() && SwingContext.active() == null) {
            puncher = attacker;
            punched = target;
            punchedAt = target.level().getGameTime();
        }
    }

    static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        double scale = Config.MELEE_KNOCKBACK.get();
        if (scale >= 1 || target.level().isClientSide()) {
            return;
        }
        // A kick's and a slam's own shove comes after their hit, outside any swing, and is never a punch.
        SwingContext.Active swing = SwingContext.active();
        boolean ordinaryHit = swing != null
                ? swing.type() != AttackType.KICK
                // Vanilla names the attacker before it shoves, so someone else's hit later in the tick isn't taken for it.
                : punched == target && punchedAt == target.level().getGameTime() && target.getLastHurtByMob() == puncher;
        if (ordinaryHit) {
            event.setStrength((float) (event.getStrength() * scale));
        }
    }

    /** End of the server tick: a punch doesn't outlive its tick. */
    static void endTick() {
        puncher = null;
        punched = null;
    }
}
