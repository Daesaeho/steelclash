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
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
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

    public static final Supplier<EntityType<ThrownWeapon>> THROWN_WEAPON = ENTITY_TYPES.register("thrown_weapon",
            () -> EntityType.Builder.<ThrownWeapon>of(ThrownWeapon::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(6)
                    .updateInterval(10)
                    .build("thrown_weapon"));

    public static final Supplier<EntityType<Soldier>> FOOTMAN = soldier("footman", Soldier.Rank.FOOTMAN);
    public static final Supplier<EntityType<Soldier>> KNIGHT = soldier("knight", Soldier.Rank.KNIGHT);
    public static final Supplier<EntityType<Soldier>> ARCHER = soldier("archer", Soldier.Rank.ARCHER);

    public static final DeferredItem<DeferredSpawnEggItem> FOOTMAN_SPAWN_EGG = ITEMS.register("footman_spawn_egg",
            () -> new DeferredSpawnEggItem(FOOTMAN, 0x7A1F1F, 0x8C8C8C, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> KNIGHT_SPAWN_EGG = ITEMS.register("knight_spawn_egg",
            () -> new DeferredSpawnEggItem(KNIGHT, 0x1F2A5C, 0xC9A23A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ARCHER_SPAWN_EGG = ITEMS.register("archer_spawn_egg",
            () -> new DeferredSpawnEggItem(ARCHER, 0x2F4F2A, 0x7A5A3A, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TRAINING_DUMMY_SPAWN_EGG = ITEMS.register("training_dummy_spawn_egg",
            () -> new DeferredSpawnEggItem(TRAINING_DUMMY, 0xC8A165, 0x5B3A1E, new Item.Properties()));

    private ModEntities() {
    }

    private static Supplier<EntityType<Soldier>> soldier(String name, Soldier.Rank rank) {
        return ENTITY_TYPES.register(name, () -> EntityType.Builder.<Soldier>of(
                        (type, level) -> new Soldier(type, level, rank), MobCategory.MONSTER)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(8)
                .build(name));
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModEntities::onAttributes);
        modEventBus.addListener(ModEntities::onCreativeTab);
        modEventBus.addListener(ModEntities::onSpawnPlacements);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(TRAINING_DUMMY.get(), TrainingDummy.createAttributes().build());
        event.put(FOOTMAN.get(), Soldier.createAttributes(Soldier.Rank.FOOTMAN).build());
        event.put(KNIGHT.get(), Soldier.createAttributes(Soldier.Rank.KNIGHT).build());
        event.put(ARCHER.get(), Soldier.createAttributes(Soldier.Rank.ARCHER).build());
    }

    /** Soldiers spawn on the ground in the dark like other monsters (spawns added by a biome modifier). */
    private static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (Supplier<EntityType<Soldier>> type : java.util.List.of(FOOTMAN, KNIGHT, ARCHER)) {
            event.register(type.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(TRAINING_DUMMY_SPAWN_EGG);
            event.accept(FOOTMAN_SPAWN_EGG);
            event.accept(KNIGHT_SPAWN_EGG);
            event.accept(ARCHER_SPAWN_EGG);
        }
    }
}
