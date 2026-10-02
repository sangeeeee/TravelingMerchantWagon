package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.WagonContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

@Mod(TravelingMerchantWagon.MODID)
public class TravelingMerchantWagon {
    public static final String MODID = "tm_wagon";

    public TravelingMerchantWagon(IEventBus bus, ModContainer container) {
        WagonContent.register(bus);
        container.registerConfig(ModConfig.Type.SERVER,CargoConfig.SPEC,"tm_wagon-server.toml");
        bus.addListener((ModConfigEvent.Loading event)->CargoConfig.refresh(event));
        bus.addListener((ModConfigEvent.Reloading event)->CargoConfig.refresh(event));
        bus.addListener(CargoConfig::unload);
    }
}
