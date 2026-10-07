package com.sange.tm_wagon.mixin;
import net.neoforged.fml.config.ModConfig;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
/** Carry On 2.12 predates NeoForge's configuration type rename within 26.3. */
@Pseudo
@Mixin(targets="tschipp.carryon.config.neoforge.ConfigLoaderImpl",remap=false)
public abstract class WagonCarryOnConfigMixin {
    @Redirect(method="lambda$initialize$0",at=@At(value="FIELD",target="Lnet/neoforged/fml/config/ModConfig$Type;COMMON:Lnet/neoforged/fml/config/ModConfig$Type;"),require=0,remap=false)
    private static ModConfig.Type tm_wagon$localConfig() { return ModConfig.Type.LOCAL; }
    @Redirect(method="lambda$initialize$0",at=@At(value="FIELD",target="Lnet/neoforged/fml/config/ModConfig$Type;SERVER:Lnet/neoforged/fml/config/ModConfig$Type;"),require=0,remap=false)
    private static ModConfig.Type tm_wagon$syncedConfig() { return ModConfig.Type.SYNCED; }
}
