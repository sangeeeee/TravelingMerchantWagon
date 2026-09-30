package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.WagonContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(TravelingMerchantWagon.MODID)
public class TravelingMerchantWagon {
    public static final String MODID = "tm_wagon";

    public TravelingMerchantWagon(IEventBus bus) {
        WagonContent.register(bus);
    }
}
