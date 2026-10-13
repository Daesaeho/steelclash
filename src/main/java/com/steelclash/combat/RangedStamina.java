package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.net.FeedbackPayload;
import com.steelclash.net.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Archery costs stamina: drawing a bow, loading a crossbow and holding a throwing weapon back all drain it for as long
 * as they last, and it doesn't regenerate meanwhile. Running out lowers the weapon without a shot, and an exhausted
 * fighter can't start again until the stamina is back ({@link com.steelclash.core.Stamina#isExhausted}).
 * <p>
 * Weapons are told apart by their use animation, not their class, so modded bows, crossbows and javelins follow the
 * same rule. Applies to whoever has combat state: players and fighter mobs.
 */
public final class RangedStamina {
    public enum Draw {
        /** Drawing a bow, or holding it drawn. */
        BOW,
        /** Loading a crossbow. A loaded one isn't in use: carrying and firing it are free. */
        CROSSBOW,
        /** Holding a trident, javelin or the like back, ready to throw. */
        THROW
    }

    private RangedStamina() {
    }

    /** What using this item amounts to, or null for anything that isn't a ranged weapon being readied. */
    @Nullable
    public static Draw of(ItemStack stack) {
        return switch (stack.getUseAnimation()) {
            case BOW -> Draw.BOW;
            case CROSSBOW -> Draw.CROSSBOW;
            case SPEAR -> Draw.THROW;
            default -> null;
        };
    }

    public static double perSecond(Draw draw) {
        return switch (draw) {
            case BOW -> Config.BOW_DRAW_STAMINA_PER_SECOND.get();
            case CROSSBOW -> Config.CROSSBOW_LOAD_STAMINA_PER_SECOND.get();
            case THROW -> Config.THROW_CHARGE_STAMINA_PER_SECOND.get();
        };
    }

    /**
     * Whether a fighter may start using {@code stack}: not a ranged weapon while exhausted. Both sides ask (a player's
     * own client knows its stamina), so the bow doesn't twitch up before the server refuses.
     */
    public static boolean refuses(LivingEntity entity, ItemStack stack) {
        Draw draw = of(stack);
        return draw != null && perSecond(draw) > 0 && entity.hasData(ModAttachments.COMBAT)
                && entity.getData(ModAttachments.COMBAT).stamina.isExhausted();
    }

    /** One server tick of a fighter's combat state: pay for the draw in progress, or lose it. */
    static void tick(LivingEntity entity, CombatData data) {
        if (!entity.isUsingItem()) {
            return;
        }
        Draw draw = of(entity.getUseItem());
        if (draw == null) {
            return;
        }
        if (data.stamina.spend((float) (perSecond(draw) / 20.0))) {
            entity.stopUsingItem(); // lowered, not released: no arrow flies and a crossbow stays unloaded
            if (entity instanceof ServerPlayer player) {
                // Tells the client to lower it too; strength 0 = no camera shake, nothing hit them.
                ModNetwork.sendTo(player, new FeedbackPayload(FeedbackPayload.Kind.DRAW_INTERRUPTED, 0f));
            }
        }
    }
}
