package com.steelclash.compat;

import net.neoforged.fml.ModList;

/**
 * Optional integrations. Never touch a class from an optional mod unless {@link #isLoaded()} is true,
 * and keep those references inside this package so they're only class-loaded when the mod is present.
 */
public enum Compat {
    SPARTAN_WEAPONRY("spartan_weaponry_unofficial"),
    // Not "spartan_shields_unofficial": the SS jar's mods.toml uses this id
    SPARTAN_SHIELDS("spartanshieldsunofficial"),
    SHOULDER_SURFING("shouldersurfing");

    private final String modId;
    private Boolean loaded;

    Compat(String modId) {
        this.modId = modId;
    }

    public String modId() {
        return modId;
    }

    public boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(modId);
        }
        return loaded;
    }
}
