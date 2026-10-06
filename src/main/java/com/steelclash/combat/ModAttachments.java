package com.steelclash.combat;

import com.steelclash.SteelClash;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SteelClash.MOD_ID);

    /** Not serialized: an attack in progress is simply dropped on save/unload. */
    public static final Supplier<AttachmentType<CombatData>> COMBAT =
            ATTACHMENT_TYPES.register("combat", () -> AttachmentType.builder(CombatData::new).build());

    private ModAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
