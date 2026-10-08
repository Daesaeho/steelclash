package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.core.Phase;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.entity.LivingEntity;

/**
 * Chivalry 2: a hit during your release interrupts your attack. The interrupt is collected when the hit lands and applied
 * at the end of the server tick, so two blades that meet within the same tick both land (a trade) whichever fighter the
 * server happens to tick first, while a blade that lands a tick earlier (an accel) interrupts the other. A release that
 * finished during the tick isn't interrupted any more. Server thread only.
 */
public final class Interrupts {
    private static final Set<LivingEntity> PENDING = new LinkedHashSet<>();

    private Interrupts() {
    }

    /** A real hit landed on {@code victim} while it was releasing an attack (hyper armor already ruled out). */
    static void releaseHit(LivingEntity victim) {
        if (Config.RELEASE_INTERRUPT.get()) {
            PENDING.add(victim);
        }
    }

    /** End of the server tick: interrupt every victim still in the release it was hit in. */
    public static void apply() {
        for (LivingEntity victim : PENDING) {
            if (victim.isRemoved() || !victim.isAlive()) {
                continue;
            }
            CombatData data = victim.getData(ModAttachments.COMBAT);
            if (data.machine.phase() == Phase.RELEASE) {
                Combat.stagger(victim, data, Config.FLINCH_TICKS.get(), true);
            }
        }
        PENDING.clear();
    }

    public static int pending() {
        return PENDING.size();
    }

    public static void clear() {
        PENDING.clear();
    }
}
