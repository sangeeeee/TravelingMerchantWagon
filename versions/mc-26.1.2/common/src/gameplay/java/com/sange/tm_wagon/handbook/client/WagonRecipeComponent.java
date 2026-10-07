package com.sange.tm_wagon.handbook.client;

import com.sange.tm_wagon.material.WagonMaterial;
import com.sange.tm_wagon.material.WagonRecipeDisplays;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.*;
import vazkii.patchouli.api.*;

/** API-only template extension; instantiated by Patchouli on the client, never by the mod loader. */
public final class WagonRecipeComponent implements ICustomComponent {
    public IVariable recipe;
    public IVariable alternative;
    private transient Identifier id;
    private transient int x,y,choice;
    private transient CraftingRecipe display;

    @Override public void onVariablesAvailable(UnaryOperator<IVariable> lookup,HolderLookup.Provider registries) {
        id=Identifier.parse(lookup.apply(recipe).asString());
        choice=lookup.apply(alternative).asNumber(0).intValue();
    }
    @Override public void build(int componentX,int componentY,int pageNum) { x=componentX;y=componentY; }
    @Override public void onDisplayed(IComponentRenderContext context) {
        display=null;
        var level=Minecraft.getInstance().level;if(level==null)return;
        var source=com.sange.tm_wagon.handbook.HandbookRecipes.recipe(id);
        if(source==null||!(source.value() instanceof CraftingRecipe crafting))return;
        var examples=WagonRecipeDisplays.create(new RecipeHolder<>(source.id(),crafting),WagonMaterial.DEFAULT);
        display=examples.isEmpty()?crafting:examples.get(Math.clamp(choice,0,examples.size()-1)).value();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,IComponentRenderContext context,float partialTick,int mouseX,int mouseY) {
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(display==null) {
            graphics.textWithWordWrap(mc.font,net.minecraft.network.chat.Component.translatable("book.tm_wagon.recipe_unavailable"),x,y,110,context.getTextColor());return;
        }
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,context.getCraftingTexture(),x-2,y-2,0,0,100,62,128,256);
        int width=display instanceof ShapedRecipe shaped?shaped.getWidth():3;
        var ingredients=display instanceof ShapedRecipe shaped?shaped.getIngredients():display.placementInfo().ingredients().stream().map(java.util.Optional::of).toList();
        for(int i=0;i<ingredients.size();i++)if(ingredients.get(i).isPresent())context.renderIngredient(graphics,x+(i%width)*19+3,y+(i/width)*19+3,mouseX,mouseY,ingredients.get(i).get());
        context.renderItemStack(graphics,x+79,y+22,mouseX,mouseY,display.assemble(CraftingInput.of(1,1,java.util.List.of(net.minecraft.world.item.ItemStack.EMPTY))));
        if(!(display instanceof ShapedRecipe))graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,context.getCraftingTexture(),x+62,y+2,0,64,11,11,128,256);
    }
}
