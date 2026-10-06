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
public final class AnimationLibrary extends SimplePreparableReloadListener<Map<String, AnimationSet>> {
    private static final String FOLDER = "steelclash_animations";
    public static final AnimationLibrary INSTANCE = new AnimationLibrary();

    private volatile Map<String, AnimationSet> animations = Map.of();

    private AnimationLibrary() {
    }

    public AnimationSet get(String archetype) {
        Map<String, AnimationSet> current = animations;
        AnimationSet found = current.get(archetype);
        return found != null ? found : current.getOrDefault("default", AnimationSet.NONE);
    }

    @Override
    protected Map<String, AnimationSet> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, AnimationSet> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry
                : manager.listResources(FOLDER, location -> location.getPath().endsWith(".json")).entrySet()) {
            String path = entry.getKey().getPath();
            String archetype = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
            try (Reader reader = entry.getValue().openAsReader()) {
                loaded.put(archetype, AnimationSet.parse(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception e) {
                SteelClash.LOGGER.error("Failed to load Steel Clash animation {}", entry.getKey(), e);
            }
        }
        return loaded;
    }

    @Override
    protected void apply(Map<String, AnimationSet> prepared, ResourceManager manager, ProfilerFiller profiler) {
        animations = Map.copyOf(prepared);
        SteelClash.LOGGER.info("Loaded {} Steel Clash animation sets", prepared.size());
    }
}
