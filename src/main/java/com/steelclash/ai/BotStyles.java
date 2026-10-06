package com.steelclash.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steelclash.SteelClash;
import com.steelclash.core.BotStyle;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** Assigns {@link BotStyle}s to entity types: {@code data/steelclash/data_maps/entity_type/bot_style.json}. */
public final class BotStyles {
    public record Ref(String style) {
        public static final Codec<Ref> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("style").forGetter(Ref::style)
        ).apply(i, Ref::new));
    }

    public static final DataMapType<EntityType<?>, Ref> DATA_MAP =
            DataMapType.builder(SteelClash.id("bot_style"), Registries.ENTITY_TYPE, Ref.CODEC).build();

    private BotStyles() {
    }

    public static void registerDataMap(RegisterDataMapTypesEvent event) {
        event.register(DATA_MAP);
    }

    public static BotStyle of(LivingEntity entity) {
        @SuppressWarnings("deprecation")
        Ref ref = entity.getType().builtInRegistryHolder().getData(DATA_MAP);
        return ref == null ? BotStyle.DUELIST : BotStyle.byName(ref.style());
    }
}
