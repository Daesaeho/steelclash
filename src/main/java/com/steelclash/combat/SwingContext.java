package com.steelclash.combat;

import com.steelclash.core.AttackType;
import com.steelclash.profile.WeaponProfile;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Marks "this damage comes from a Steel Clash swing" while we call vanilla attack code, so our event listeners
 * can decide parries/blocks, disable vanilla crits/sweeps and apply the attack's multipliers. Server thread only.
 */
public final class SwingContext {
    public record Active(Entity attacker, AttackType type, WeaponProfile.AttackSpec spec) {
    }

    @Nullable
    private static Active current;

    private SwingContext() {
    }

    public static void run(Entity attacker, AttackType type, WeaponProfile.AttackSpec spec, Runnable action) {
        Active previous = current;
        current = new Active(attacker, type, spec);
        try {
            action.run();
        } finally {
            current = previous;
        }
    }

    @Nullable
    public static Active forAttacker(@Nullable Entity attacker) {
        Active active = current;
        return active != null && attacker != null && active.attacker() == attacker ? active : null;
    }
}
