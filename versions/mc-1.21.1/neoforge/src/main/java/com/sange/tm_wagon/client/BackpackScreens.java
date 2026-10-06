package com.sange.tm_wagon.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class BackpackScreens {
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        if(ModList.get().isLoaded("travelersbackpack"))TravelerScreens.register(event);
    }
    /** Loaded only when the optional native client API exists. */
    private static final class TravelerScreens {
        static void register(RegisterMenuScreensEvent event) {
            event.register(com.sange.tm_wagon.compat.backpack.TravelerCargo.MENU.get(),com.tiviacz.travelersbackpack.client.screens.BackpackScreen::new);
            event.register(com.sange.tm_wagon.compat.backpack.TravelerCargo.SETTINGS.get(),com.tiviacz.travelersbackpack.client.screens.BackpackSettingsScreen::new);
        }
    }
}
