package com.sange.tm_wagon.handbook.client;

import com.sange.tm_wagon.material.WagonMaterial;
import com.sange.tm_wagon.material.WagonRecipeDisplays;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import vazkii.patchouli.api.*;

/** API-only template extension; instantiated by Patchouli on the client, never by the mod loader. */
public final class WagonRecipeComponent implements ICustomComponent {
    public IVariable recipe;
    public IVariable alternative;
    private transient ResourceLocation id;
    private transient int x,y,choice;
    private transient CraftingRecipe display;

    @Override public void onVariablesAvailable(UnaryOperator<IVariable> lookup,HolderLookup.Provider registries) {
        id=ResourceLocation.parse(lookup.apply(recipe).asString());
        choice=lookup.apply(alternative).asNumber(0).intValue();
    }
    @Override public void build(int componentX,int componentY,int pageNum) { x=componentX;y=componentY; }
    @Override public void onDisplayed(IComponentRenderContext context) {
        display=null;
        var level=Minecraft.getInstance().level;if(level==null)return;
        var source=level.getRecipeManager().byKey(id).orElse(null);
        if(source==null||!(source.value() instanceof CraftingRecipe crafting))return;
        var examples=WagonRecipeDisplays.create(new RecipeHolder<>(source.id(),crafting),WagonMaterial.DEFAULT);
        display=examples.isEmpty()?crafting:examples.get(Math.clamp(choice,0,examples.size()-1)).value();
    }
    @Override public void render(GuiGraphics graphics,IComponentRenderContext context,float partialTick,int mouseX,int mouseY) {
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(display==null) {
            graphics.drawWordWrap(mc.font,net.minecraft.network.chat.Component.translatable("book.tm_wagon.recipe_unavailable"),x,y,110,context.getTextColor());return;
        }
        graphics.blit(context.getCraftingTexture(),x-2,y-2,0,0,100,62,128,256);
        int width=display instanceof ShapedRecipe shaped?shaped.getWidth():3;
        var ingredients=display.getIngredients();
        for(int i=0;i<ingredients.size();i++)context.renderIngredient(graphics,x+(i%width)*19+3,y+(i/width)*19+3,mouseX,mouseY,ingredients.get(i));
        context.renderItemStack(graphics,x+79,y+22,mouseX,mouseY,display.getResultItem(mc.level.registryAccess()));
        if(!(display instanceof ShapedRecipe))graphics.blit(context.getCraftingTexture(),x+62,y+2,0,64,11,11,128,256);
    }
}
