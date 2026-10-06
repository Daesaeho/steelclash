package com.steelclash.compat;

import com.steelclash.SteelClash;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import org.xiyu.spartanweaponryunofficial.api.SpartanWeaponryAPI;
import org.xiyu.spartanweaponryunofficial.api.WeaponClassification;

/** Only class-loaded when Spartan Weaponry is installed (see {@link Compat#SPARTAN_WEAPONRY}). */
public final class SpartanWeaponryCompat {
    private static final int API_VERSION = 15;

    private SpartanWeaponryCompat() {
    }

    public static void init() {
        SpartanWeaponryAPI.assertAPIVersion(SteelClash.MOD_ID, API_VERSION);
    }

    /** The Steel Clash profile name for a Spartan weapon, or empty for ranged/unknown types. */
    public static Optional<String> profileFor(ItemStack stack) {
        Optional<String> typeName = SpartanWeaponryAPI.getWeaponClassification(stack.getItem())
                .map(WeaponClassification::weaponTypeName);
        if (typeName.isEmpty()) {
            // SW only attaches classifications to API-created items; fall back to its per-type item tags.
            for (SpartanWeaponryAPI.WeaponItemType type : SpartanWeaponryAPI.WeaponItemType.values()) {
                if (stack.is(SpartanWeaponryAPI.getWeaponTag(type))) {
                    typeName = Optional.of(type.name());
                    break;
                }
            }
        }
        return typeName.map(name -> profileForType(name.toLowerCase(Locale.ROOT)));
    }

    // Matched by name rather than enum constant so a newer SW with added/removed types can't break class loading.
    private static String profileForType(String type) {
        return switch (type) {
            case "dagger", "parrying_dagger", "throwing_knife" -> "dagger";
            case "longsword", "katana", "saber" -> "sword";
            case "rapier" -> "rapier";
            case "greatsword" -> "two_handed";
            case "battleaxe", "tomahawk" -> "axe";
            case "battle_hammer", "warhammer", "flanged_mace", "club" -> "blunt";
            case "spear", "pike", "lance", "javelin" -> "spear";
            case "halberd", "glaive", "scythe" -> "polearm";
            case "quarterstaff" -> "staff";
            default -> null;
        };
    }
}
