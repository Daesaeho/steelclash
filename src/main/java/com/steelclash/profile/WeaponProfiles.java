package com.steelclash.profile;

import com.steelclash.SteelClash;
import com.steelclash.compat.Compat;
import com.steelclash.compat.SpartanWeaponryCompat;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Finds the weapon profile for an item. Resolution order:
 * <ol>
 *     <li>the {@code steelclash:weapon_profile} item data map (datapacks can override anything)</li>
 *     <li>Spartan Weaponry weapon type, when installed (covers SW addon weapons automatically)</li>
 *     <li>vanilla tags: {@code #minecraft:swords} → sword, {@code #minecraft:axes} → axe</li>
 * </ol>
 */
public final class WeaponProfiles {
    public static final ResourceKey<Registry<WeaponProfile>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(SteelClash.id("weapon_profile"));

    public static final DataMapType<Item, WeaponProfileRef> DATA_MAP =
            DataMapType.builder(SteelClash.id("weapon_profile"), Registries.ITEM, WeaponProfileRef.CODEC)
                    .synced(WeaponProfileRef.CODEC, false)
                    .build();

    /** Natural weapons for mobs fighting bare-handed: {@code data/steelclash/data_maps/entity_type/mob_profile.json}. */
    public static final DataMapType<EntityType<?>, WeaponProfileRef> MOB_DATA_MAP =
            DataMapType.builder(SteelClash.id("mob_profile"), Registries.ENTITY_TYPE, WeaponProfileRef.CODEC)
                    .synced(WeaponProfileRef.CODEC, false)
                    .build();

    /** Mobs whose melee attacks become telegraphed Steel Clash attacks (and who can parry, given a guard). */
    public static final TagKey<EntityType<?>> FIGHTERS = TagKey.create(Registries.ENTITY_TYPE, SteelClash.id("fighters"));

    public record Resolved(ResourceKey<WeaponProfile> key, WeaponProfile profile) {
    }

    private WeaponProfiles() {
    }

    public static ResourceKey<WeaponProfile> key(String path) {
        return ResourceKey.create(REGISTRY_KEY, SteelClash.id(path));
    }

    public static void registerRegistry(DataPackRegistryEvent.NewRegistry event) {
        // Network codec = same codec, so clients receive profiles for prediction and visuals.
        event.dataPackRegistry(REGISTRY_KEY, WeaponProfile.CODEC, WeaponProfile.CODEC);
    }

    public static void registerDataMap(RegisterDataMapTypesEvent event) {
        event.register(DATA_MAP);
        event.register(MOB_DATA_MAP);
    }

    public static Optional<Resolved> resolve(ItemStack stack, RegistryAccess access) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Optional<ResourceKey<WeaponProfile>> key = findKey(stack);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return access.registry(REGISTRY_KEY)
                .flatMap(registry -> registry.getOptional(key.get()))
                .map(profile -> new Resolved(key.get(), profile));
    }

    /**
     * The profile an entity fights with: its main-hand weapon, or for mobs without one, their natural weapon from the
     * mob data map.
     */
    public static Optional<Resolved> resolveFor(LivingEntity entity) {
        RegistryAccess access = entity.level().registryAccess();
        Optional<Resolved> held = resolve(entity.getMainHandItem(), access);
        if (held.isPresent() || entity instanceof Player) {
            return held;
        }
        WeaponProfileRef ref = entity.getType().builtInRegistryHolder().getData(MOB_DATA_MAP);
        if (ref == null) {
            return Optional.empty();
        }
        return get(ref.profile(), access).map(profile -> new Resolved(ref.profile(), profile));
    }

    public static Optional<WeaponProfile> get(ResourceKey<WeaponProfile> key, RegistryAccess access) {
        return access.registry(REGISTRY_KEY).flatMap(registry -> registry.getOptional(key));
    }

    private static Optional<ResourceKey<WeaponProfile>> findKey(ItemStack stack) {
        WeaponProfileRef ref = stack.getItemHolder().getData(DATA_MAP);
        if (ref != null) {
            return Optional.of(ref.profile());
        }
        if (Compat.SPARTAN_WEAPONRY.isLoaded()) {
            Optional<String> archetype = SpartanWeaponryCompat.profileFor(stack);
            if (archetype.isPresent()) {
                return archetype.map(WeaponProfiles::key);
            }
        }
        if (stack.is(ItemTags.SWORDS)) {
            return Optional.of(key("sword"));
        }
        if (stack.is(ItemTags.AXES)) {
            return Optional.of(key("axe"));
        }
        return Optional.empty();
    }
}
