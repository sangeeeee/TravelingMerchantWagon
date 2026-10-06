package com.sange.tm_wagon.mixin;

import java.nio.file.Files;
import net.minecraftforge.fml.loading.FMLPaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No old configuration needs migration on a fresh installation. */
@Pseudo
@Mixin(targets="com.tiviacz.travelersbackpack.TravelersBackpack",remap=false)
public abstract class WagonTravelerConfigMixin {
    @Inject(method="readOldCommonConfig",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void tm_wagon$skipMissingLegacyConfig(CallbackInfo ci) {
        if(Files.notExists(FMLPaths.CONFIGDIR.get().resolve("travelersbackpack-common.toml")))ci.cancel();
    }
}
