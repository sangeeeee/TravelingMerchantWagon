package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.material.WagonMaterial;
import net.fabricmc.api.ModInitializer;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.neoforged.fml.config.ModConfig;

public final class TravelingMerchantWagon implements ModInitializer {
    public static final String MODID="tm_wagon";
    @Override public void onInitialize() {
        WagonMaterial.register();
        WagonContent.register();
        com.sange.tm_wagon.entity.WagonData.register();
        com.sange.tm_wagon.compat.BackpackCompat.register();
        StartupConfigFiles.ensure();
        ModConfigEvents.loading(MODID).register(config->{CargoConfig.refresh(config);DrivingConfig.refresh(config);});
        ModConfigEvents.reloading(MODID).register(config->{CargoConfig.refresh(config);DrivingConfig.refresh(config);});
        ModConfigEvents.unloading(MODID).register(config->{CargoConfig.unload(config);DrivingConfig.unload(config);});
        ConfigRegistry.INSTANCE.register(MODID,ModConfig.Type.SERVER,ServerConfig.SPEC,ServerConfig.FILE_NAME);
        com.sange.tm_wagon.network.WagonNetwork.register();
        com.sange.tm_wagon.platform.FabricEvents.register();
    }
}
