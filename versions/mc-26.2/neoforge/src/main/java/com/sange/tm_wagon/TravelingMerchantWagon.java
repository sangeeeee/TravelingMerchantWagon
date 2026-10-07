package com.sange.tm_wagon;

import net.neoforged.fml.common.Mod;

/** Loader entry point; gameplay will be ported in the next implementation stage. */
@Mod(TravelingMerchantWagon.MOD_ID)
public final class TravelingMerchantWagon {
    public static final String MOD_ID = "tm_wagon";
    public TravelingMerchantWagon() {
        System.getLogger(MOD_ID).log(System.Logger.Level.INFO,
            "Minecraft " + TargetVersion.MINECRAFT + " NeoForge port environment initialized.");
    }
}
