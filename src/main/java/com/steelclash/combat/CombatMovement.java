package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Chivalry 2 footwork: fighters move slower while attacking, guarding or staggered. A transient movement-speed
 * modifier, kept in step with the combat phase every tick (players and mobs; the local client predicts its own).
 */
public final class CombatMovement {
    private static final ResourceLocation SLOWDOWN = SteelClash.id("combat_slowdown");

    private CombatMovement() {
    }

    public static void update(LivingEntity entity, CombatData data) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double factor = factor(data);
        AttributeModifier current = speed.getModifier(SLOWDOWN);
        if (factor >= 1) {
            if (current != null) {
                speed.removeModifier(SLOWDOWN);
            }
            return;
        }
        double amount = factor - 1;
        if (current == null || current.amount() != amount) {
            speed.removeModifier(SLOWDOWN);
            speed.addTransientModifier(new AttributeModifier(SLOWDOWN, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Fraction of normal movement speed for the current phase. */
    public static double factor(CombatData data) {
        return switch (data.machine.phase()) {
            case WINDUP, RELEASE -> Config.ATTACK_MOVE_SPEED.get();
            case RECOVERY -> Config.RECOVERY_MOVE_SPEED.get();
            case PARRY, GUARD_RECOVERY -> Config.GUARD_MOVE_SPEED.get();
            case STAGGER -> Config.STAGGER_MOVE_SPEED.get();
            default -> 1.0;
        };
    }
}
