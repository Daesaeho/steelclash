package com.steelclash.client.anim;

import com.google.gson.JsonParser;
import com.steelclash.SteelClash;
import com.steelclash.core.AnimationSet;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads pose clips from {@code assets/<namespace>/steelclash_animations/<archetype>.json} (resource packs can override
 * or add archetypes; F3+T reloads). Missing archetypes fall back to {@code default}.
 */
public final class AnimationLibrary extends SimplePreparableReloadListener<Map<ResourceLocation, AnimationSet>> {
    private static final String FOLDER = "steelclash_animations";
    public static final AnimationLibrary INSTANCE = new AnimationLibrary();

    private volatile Map<ResourceLocation, AnimationSet> animations = Map.of();
    private volatile long generation;

    private AnimationLibrary() {
    }

    public AnimationSet get(String archetype) {
        Map<ResourceLocation, AnimationSet> current = animations;
        ResourceLocation id = ResourceLocation.tryParse(archetype.contains(":") ? archetype : "steelclash:" + archetype);
        AnimationSet fallback = current.getOrDefault(SteelClash.id("default"), AnimationSet.NONE);
        return id == null ? fallback : current.getOrDefault(id, fallback);
    }

    public long generation() { return generation; }

    @Override
    protected Map<ResourceLocation, AnimationSet> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, AnimationSet> loaded = new HashMap<>();
        Map<ResourceLocation, AnimationSet> previous = animations;
        for (Map.Entry<ResourceLocation, Resource> entry
                : manager.listResources(FOLDER, location -> location.getPath().endsWith(".json")).entrySet()) {
            String path = entry.getKey().getPath();
            String archetype = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(), archetype);
            try (Reader reader = entry.getValue().openAsReader()) {
                loaded.put(id, AnimationSet.parse(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception e) {
                SteelClash.LOGGER.error("Failed to load Steel Clash animation {}", entry.getKey(), e);
                if (previous.containsKey(id)) {
                    loaded.put(id, previous.get(id)); // malformed edits keep the last valid set until repaired
                }
            }
        }
        AnimationSet fallback = loaded.getOrDefault(SteelClash.id("default"), AnimationSet.NONE);
        loaded.replaceAll((id, set) -> id.equals(SteelClash.id("default")) ? set : set.withFallback(fallback));
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, AnimationSet> prepared, ResourceManager manager, ProfilerFiller profiler) {
        animations = Map.copyOf(prepared);
        generation++;
        SteelClash.LOGGER.info("Loaded {} Steel Clash animation sets", prepared.size());
    }
}
