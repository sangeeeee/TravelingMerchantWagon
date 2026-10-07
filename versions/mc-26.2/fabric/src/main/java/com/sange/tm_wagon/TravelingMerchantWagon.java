package com.sange.tm_wagon;

import net.fabricmc.api.ModInitializer;

/** Loader entry point; gameplay will be ported in the next implementation stage. */
public final class TravelingMerchantWagon implements ModInitializer {
    public static final String MOD_ID = "tm_wagon";
    @Override public void onInitialize() {
        System.getLogger(MOD_ID).log(System.Logger.Level.INFO,
            "Minecraft " + TargetVersion.MINECRAFT + " Fabric port environment initialized.");
    }
}
