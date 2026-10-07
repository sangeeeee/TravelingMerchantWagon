package com.sange.tm_wagon.compat.jei;

import com.sange.tm_wagon.material.WagonMaterial;
import com.sange.tm_wagon.material.WagonRecipeDisplays;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import com.sange.tm_wagon.material.WoodMaterial;

/** Discovered only by JEI; common startup never loads JEI classes. */
@JeiPlugin
public final class WagonJeiPlugin implements IModPlugin {
    @Override public Identifier getPluginUid() { return Identifier.fromNamespaceAndPath("tm_wagon","components"); }
    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        var interpreter=new ISubtypeInterpreter<ItemStack>() {
            @Override public Object getSubtypeData(ItemStack stack,UidContext context) {
                var material=WagonMaterial.of(stack);
                return new WagonMaterial(WagonMaterial.wooden(stack.getItem())?material.wood():WoodMaterial.OAK,
                    WagonMaterial.dyed(stack.getItem())?material.colour():DyeColor.WHITE);
            }
        };
        for(var item:BuiltInRegistries.ITEM)if(WagonMaterial.wooden(item)||WagonMaterial.dyed(item))registration.registerSubtypeInterpreter(item,interpreter);
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        var level=Minecraft.getInstance().level;
        if(level==null)return;
        // Minecraft 26.3 no longer sends the full recipe manager to clients.
        // JEI supplies the server recipe map (including custom recipe codecs).
        if(!mezz.jei.common.Internal.hasClientRecipes())return;
        var recipes=mezz.jei.common.Internal.getClientSyncedRecipes().byType(net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream()
            .flatMap(source->WagonRecipeDisplays.create(source).stream()).toList();
        registration.addRecipes(RecipeTypes.CRAFTING,recipes);
    }
}
