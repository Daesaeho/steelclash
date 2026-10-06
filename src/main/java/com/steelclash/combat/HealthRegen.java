package com.steelclash.combat;

import com.steelclash.Config;
import net.minecraft.world.entity.player.Player;

/** Chivalry 2 health regeneration: after a few seconds without taking damage, health comes back steadily. Players only. */
public final class HealthRegen {
    private HealthRegen() {
    }

    /** Called every server tick for every player. */
    public static void tick(Player player, CombatData data) {
        if (!Config.HEALTH_REGEN.get() || !player.isAlive() || player.getHealth() >= player.getMaxHealth()) {
            return;
        }
        if (player.level().getGameTime() - data.lastHurtAt < Config.HEALTH_REGEN_DELAY_TICKS.get()) {
            return;
        }
        player.heal((float) (Config.HEALTH_REGEN_PER_SECOND.get() / 20.0));
    }
}
