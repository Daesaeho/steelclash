package com.steelclash.sound;

import com.steelclash.SteelClash;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Combat sounds. Mapped to vanilla sound files in {@code assets/steelclash/sounds.json} for now, so a resource pack
 * can replace any of them with real recordings without code changes.
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SteelClash.MOD_ID);

    public static final Supplier<SoundEvent> WINDUP = register("windup");
    public static final Supplier<SoundEvent> SWING_LIGHT = register("swing_light");
    public static final Supplier<SoundEvent> SWING_HEAVY = register("swing_heavy");
    public static final Supplier<SoundEvent> HIT = register("hit");
    public static final Supplier<SoundEvent> PARRY = register("parry");
    public static final Supplier<SoundEvent> BLOCK = register("block");
    public static final Supplier<SoundEvent> CLANK = register("clank");
    public static final Supplier<SoundEvent> KICK = register("kick");
    public static final Supplier<SoundEvent> FEINT = register("feint");
    public static final Supplier<SoundEvent> DISARM = register("disarm");
    public static final Supplier<SoundEvent> GUARD_BREAK = register("guard_break");

    private ModSounds() {
    }

    private static Supplier<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SteelClash.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
