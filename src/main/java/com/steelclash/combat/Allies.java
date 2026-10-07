package com.steelclash.combat;

import com.steelclash.Config;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Who fights on whose side (Chivalry 2 team rules, report §21). Allies are players on the same scoreboard team, and
 * with {@code playersAreAllies} (the co-op default) all players. A swing meets an ally's body like any other
 * ({@code friendlyCollision}): slashes and overheads carry on through, stabs stop in it (Chivalry 2 update 2.9/2.10),
 * and the ally takes {@code friendlyDamageScale} of the damage. An ally's guard never parries you.
 */
public final class Allies {
    private Allies() {
    }

    public static boolean areAllies(LivingEntity a, LivingEntity b) {
        if (a == b) {
            return false;
        }
        if (a.isAlliedTo(b)) {
            return true;
        }
        return Config.PLAYERS_ARE_ALLIES.get() && a instanceof Player && b instanceof Player;
    }

    /** A pet of the attacker or of one of its allies: never cut down, and its body doesn't stop the swing. */
    public static boolean isFriendlyPet(LivingEntity attacker, LivingEntity target) {
        if (!(target instanceof OwnableEntity ownable) || ownable.getOwnerUUID() == null) {
            return false;
        }
        if (ownable.getOwnerUUID().equals(attacker.getUUID())) {
            return true;
        }
        return ownable.getOwner() instanceof LivingEntity owner && areAllies(attacker, owner);
    }

    /** Damage multiplier for a hit on an ally (0 = no friendly fire). */
    public static float damageScale() {
        return Config.FRIENDLY_DAMAGE_SCALE.get().floatValue();
    }
}
