package com.steelclash.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;

/** Data map value assigning an item to a weapon profile: {@code {"profile": "steelclash:sword"}}. */
public record WeaponProfileRef(ResourceKey<WeaponProfile> profile) {
    public static final Codec<WeaponProfileRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(WeaponProfiles.REGISTRY_KEY).fieldOf("profile").forGetter(WeaponProfileRef::profile)
    ).apply(i, WeaponProfileRef::new));
}
