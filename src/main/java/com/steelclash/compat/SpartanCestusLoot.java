package com.steelclash.compat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steelclash.SteelClash;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Filters the cestus family from newly generated weaponsmith loot. */
public final class SpartanCestusLoot extends LootModifier {
    private static final MapCodec<SpartanCestusLoot> CODEC =
            RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, SpartanCestusLoot::new));
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SteelClash.MOD_ID);

    private SpartanCestusLoot(LootItemCondition[] conditions) {
        super(conditions);
    }

    public static void register(IEventBus bus) {
        MODIFIERS.register("exclude_cestus", () -> CODEC);
        MODIFIERS.register(bus);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (Compat.SPARTAN_WEAPONRY.isLoaded()) {
            loot.removeIf(SpartanWeaponryCompat::isCestus);
        }
        return loot;
    }
}
