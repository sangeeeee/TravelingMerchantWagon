package com.sange.tm_wagon.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;


@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class BackpackScreens {
    @SubscribeEvent public static void register(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(()->{if(ModList.get().isLoaded("travelersbackpack"))TravelerScreens.register(event);});
    }
    /** Loaded only when the optional native client API exists. */
    private static final class TravelerScreens {
        static void register(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            net.minecraft.client.gui.screens.MenuScreens.register(com.sange.tm_wagon.compat.backpack.TravelerCargo.MENU.get(),com.tiviacz.travelersbackpack.client.screens.BackpackScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(com.sange.tm_wagon.compat.backpack.TravelerCargo.SETTINGS.get(),com.tiviacz.travelersbackpack.client.screens.BackpackSettingsScreen::new);
        }
    }
}
