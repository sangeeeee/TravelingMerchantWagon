package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.WagonContent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod(TravelingMerchantWagon.MODID)
public class TravelingMerchantWagon {
    public static final String MODID = "tm_wagon";

    public TravelingMerchantWagon() {
        IEventBus bus=net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        com.sange.tm_wagon.network.WagonNetwork.register();
        com.sange.tm_wagon.material.WagonMaterial.register(bus);
        WagonContent.register(bus);
        com.sange.tm_wagon.compat.BackpackCompat.register(bus);
        var container=net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
        container.addConfig(new StartupServerConfig(container));
        bus.addListener((ModConfigEvent.Loading event)->CargoConfig.refresh(event));
        bus.addListener((ModConfigEvent.Reloading event)->CargoConfig.refresh(event));
        bus.addListener(CargoConfig::unload);
        bus.addListener((ModConfigEvent.Loading event)->DrivingConfig.refresh(event));
        bus.addListener((ModConfigEvent.Reloading event)->DrivingConfig.refresh(event));
        bus.addListener(DrivingConfig::unload);
        StartupConfigFiles.ensure();
    }
}
