package com.sange.tm_wagon.client;

import net.fabricmc.loader.api.FabricLoader;

public final class BackpackScreens {
    public static void register() {
        if(FabricLoader.getInstance().isModLoaded("travelersbackpack"))TravelerScreens.register();
    }
    /** Loaded only when the optional native client API exists. */
    private static final class TravelerScreens {
        static void register() {
            net.minecraft.client.gui.screens.MenuScreens.register(com.sange.tm_wagon.compat.backpack.TravelerCargo.MENU.get(),com.tiviacz.travelersbackpack.client.screens.BackpackScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(com.sange.tm_wagon.compat.backpack.TravelerCargo.SETTINGS.get(),com.tiviacz.travelersbackpack.client.screens.BackpackSettingsScreen::new);
        }
    }
}
