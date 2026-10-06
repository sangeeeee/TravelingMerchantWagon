package com.sange.tm_wagon.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric's key map has one binding per key. TACZ's shared mouse bindings must
 * also reach vanilla; TACZ's interaction event already suppresses gun clicks. */
@Mixin(KeyMapping.class)
public abstract class WagonVanillaInputMixin {
    @Shadow @Final private static Map<InputConstants.Key,KeyMapping> MAP;

    @Inject(method="set",at=@At("TAIL"))
    private static void tm_wagon$sharedPress(InputConstants.Key key,boolean down,CallbackInfo callback) {
        var binding=tm_wagon$vanillaBinding(key);
        if(binding!=null) binding.setDown(down);
    }

    @Inject(method="click",at=@At("TAIL"))
    private static void tm_wagon$sharedClick(InputConstants.Key key,CallbackInfo callback) {
        var binding=tm_wagon$vanillaBinding(key);
        if(binding!=null) {
            var access=(WagonKeyMappingAccess)binding;
            access.tm_wagon$clickCount(access.tm_wagon$clickCount()+1);
        }
    }

    @Unique
    private static KeyMapping tm_wagon$vanillaBinding(InputConstants.Key key) {
        var winner=MAP.get(key);
        if(winner==null || !winner.getName().startsWith("key.tacz.")) return null;
        var mc=Minecraft.getInstance();
        if(mc==null || mc.options==null) return null;
        if(winner!=mc.options.keyUse && KeyBindingHelper.getBoundKeyOf(mc.options.keyUse).equals(key)) return mc.options.keyUse;
        if(winner!=mc.options.keyAttack && KeyBindingHelper.getBoundKeyOf(mc.options.keyAttack).equals(key)) return mc.options.keyAttack;
        return null;
    }
}
