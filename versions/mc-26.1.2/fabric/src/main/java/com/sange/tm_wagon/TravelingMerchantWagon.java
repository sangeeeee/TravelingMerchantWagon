package com.sange.tm_wagon;

/** Loader entry point only; wagon gameplay will be ported in the next stage. */
public final class TravelingMerchantWagon implements net.fabricmc.api.ModInitializer {
    public static final String MODID = "tm_wagon";
    @Override public void onInitialize() {
        System.getLogger(MODID).log(System.Logger.Level.INFO, "Minecraft 26.1.2 Fabric dependency scaffold loaded; gameplay port pending.");
    }
}
