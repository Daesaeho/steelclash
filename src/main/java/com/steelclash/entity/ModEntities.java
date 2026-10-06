package com.steelclash.entity;

import com.steelclash.SteelClash;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SteelClash.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SteelClash.MOD_ID);

    public static final Supplier<EntityType<TrainingDummy>> TRAINING_DUMMY = ENTITY_TYPES.register("training_dummy",
            () -> EntityType.Builder.of(TrainingDummy::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("training_dummy"));

    public static final DeferredItem<DeferredSpawnEggItem> TRAINING_DUMMY_SPAWN_EGG = ITEMS.register("training_dummy_spawn_egg",
            () -> new DeferredSpawnEggItem(TRAINING_DUMMY, 0xC8A165, 0x5B3A1E, new Item.Properties()));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModEntities::onAttributes);
        modEventBus.addListener(ModEntities::onCreativeTab);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(TRAINING_DUMMY.get(), TrainingDummy.createAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(TRAINING_DUMMY_SPAWN_EGG);
        }
    }
}
