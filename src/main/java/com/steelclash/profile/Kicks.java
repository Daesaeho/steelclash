package com.steelclash.profile;

import com.steelclash.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The built-in kick and shield bash: the same for every fighter, independent of weapon profiles. */
public final class Kicks {
    /** Spartan Shields' bash tag (includes the vanilla shield); referenced by id so Spartan Shields stays optional. */
    public static final TagKey<Item> SHIELDS_WITH_BASH = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath("spartanshieldsunofficial", "shields_with_bash"));

    private Kicks() {
    }

    /** Reach bonus is relative to the player's 3-block interaction range, giving a ~1.9 block kick. */
    public static WeaponProfile.AttackSpec kick() {
        return new WeaponProfile.AttackSpec(5, 3, 10, 1f,
                new WeaponProfile.ArcSpec(WeaponProfile.ArcSpec.Shape.KICK, 1), 1, -1.1f,
                Config.KICK_STAMINA_DAMAGE.get().floatValue(), 0f);
    }

    public static WeaponProfile.AttackSpec shieldBash() {
        return new WeaponProfile.AttackSpec(6, 3, 12, 2f,
                new WeaponProfile.ArcSpec(WeaponProfile.ArcSpec.Shape.KICK, 1), 1, -0.9f,
                Config.KICK_STAMINA_DAMAGE.get().floatValue() * 1.2f, 0f);
    }

    public static boolean canBash(LivingEntity entity) {
        ItemStack offhand = entity.getOffhandItem();
        return offhand.is(SHIELDS_WITH_BASH) || offhand.is(Items.SHIELD);
    }

    public static WeaponProfile.AttackSpec forEntity(LivingEntity entity) {
        return canBash(entity) ? shieldBash() : kick();
    }
}
