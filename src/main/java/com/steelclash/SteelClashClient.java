package com.steelclash;

import com.steelclash.client.ClientInput;
import com.steelclash.client.ControlSchemes;
import com.steelclash.client.ProceduralSwingAnimation;
import com.steelclash.client.StaminaHud;
import com.steelclash.client.SoldierRenderer;
import com.steelclash.client.TrainingDummyRenderer;
import com.steelclash.client.anim.AnimationLibrary;
import com.steelclash.entity.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Never loaded on dedicated servers, so client-only classes are safe to reference from here.
@Mod(value = SteelClash.MOD_ID, dist = Dist.CLIENT)
public class SteelClashClient {
    public SteelClashClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(ClientInput::registerKeys);
        modEventBus.addListener(ControlSchemes::onConfigReloaded);
        modEventBus.addListener(StaminaHud::register);
        modEventBus.addListener(SteelClashClient::registerRenderers);
        modEventBus.addListener(SteelClashClient::registerReloadListeners);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        ProceduralSwingAnimation.register();
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(AnimationLibrary.INSTANCE);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TRAINING_DUMMY.get(), TrainingDummyRenderer::new);
        event.registerEntityRenderer(ModEntities.THROWN_WEAPON.get(), context -> new ThrownItemRenderer<>(context, 1.0f, true));
        event.registerEntityRenderer(ModEntities.FOOTMAN.get(), context -> new SoldierRenderer(context, "footman"));
        event.registerEntityRenderer(ModEntities.KNIGHT.get(), context -> new SoldierRenderer(context, "knight"));
        event.registerEntityRenderer(ModEntities.ARCHER.get(), context -> new SoldierRenderer(context, "archer"));
    }
}
