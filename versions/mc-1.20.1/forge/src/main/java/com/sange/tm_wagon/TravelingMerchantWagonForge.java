package com.sange.tm_wagon;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;

/** Forge bootstrap; gameplay registration will be added during the 1.20.1 port. */
@Mod(TravelingMerchantWagonForge.MOD_ID)
public final class TravelingMerchantWagonForge {
    public static final String MOD_ID = "tm_wagon";

    public TravelingMerchantWagonForge() {
        LogUtils.getLogger().info("TravelingMerchantWagon Forge 1.20.1 dependency scaffold loaded; gameplay port is pending.");
    }
}
