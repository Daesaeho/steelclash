package com.steelclash.combat;

import com.steelclash.Config;
import net.minecraft.world.entity.player.Player;

/**
 * Chivalry 2 health regeneration: after a few seconds out of combat (not hurt, attacking, guarding or blocking with
 * a shield), health comes back steadily, up to a cap (about 40% of max health). Players only.
 */
public final class HealthRegen {
    private HealthRegen() {
    }

    /** Called every server tick for every player. */
    public static void tick(Player player, CombatData data) {
        long now = player.level().getGameTime();
        if (data.machine.isBusy() || player.isBlocking()) {
            data.lastCombatAt = now; // fighting pauses regeneration just like being hurt
        }
        if (!Config.HEALTH_REGEN.get() || !player.isAlive()) {
            return;
        }
        float cap = (float) (player.getMaxHealth() * Config.HEALTH_REGEN_CAP.get());
        if (player.getHealth() >= cap) {
            return;
        }
        if (now - Math.max(data.lastHurtAt, data.lastCombatAt) < Config.HEALTH_REGEN_DELAY_TICKS.get()) {
            return;
        }
        player.heal(Math.min(cap - player.getHealth(), (float) (Config.HEALTH_REGEN_PER_SECOND.get() / 20.0)));
    }
}
