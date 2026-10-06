package com.sange.tm_wagon.material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

public class WagonRecipeDisplayGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=30)
    public void displayed_variants_match_real_recipes_and_survive_network_encoding(GameTestHelper h) {
        var ids=new HashSet<net.minecraft.resources.ResourceLocation>();int sources=0,variants=0;
        for(var source:h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if(!(source.value() instanceof WagonComponentRecipe))continue;
            sources++;
            for(var display:WagonRecipeDisplays.create(source)) {
                variants++;h.assertTrue(ids.add(display.id()),"Duplicate display recipe id");
                var r=display.value();var ingredients=r.getIngredients();
                int width=r instanceof ShapedRecipe shaped?shaped.getWidth():ingredients.size();
                int height=r instanceof ShapedRecipe shaped?shaped.getHeight():1;
                var stacks=new ArrayList<ItemStack>();
                for(var ingredient:ingredients)stacks.add(ingredient.isEmpty()?ItemStack.EMPTY:ingredient.getItems()[0].copy());
                verify(h,source,r,CraftingInput.of(width,height,stacks));
                // Every alternate (including stripped logs and arbitrary tag ingredients) must work.
                for(int slot=0;slot<ingredients.size();slot++)for(var candidate:ingredients.get(slot).getItems()) {
                    var alternate=new ArrayList<>(stacks);alternate.set(slot,candidate.copy());
                    verify(h,source,r,CraftingInput.of(width,height,alternate));
                }
                var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
                try {
                    Recipe.STREAM_CODEC.encode(buffer,r);var decoded=Recipe.STREAM_CODEC.decode(buffer);
                    h.assertTrue(ItemStack.matches(decoded.getResultItem(h.getLevel().registryAccess()),r.getResultItem(h.getLevel().registryAccess())),"JEI transfer serialization changed output");
                } finally { buffer.release(); }
            }
        }
        h.assertTrue(sources==19&&variants>600,"Missing component recipes or variants: "+sources+" / "+variants);h.succeed();
    }
    private static void verify(GameTestHelper h,RecipeHolder<CraftingRecipe> source,CraftingRecipe display,CraftingInput input) {
        h.assertTrue(source.value().matches(input,h.getLevel()),"Displayed recipe cannot be crafted: "+source.id());
        h.assertTrue(ItemStack.matches(source.value().assemble(input,h.getLevel().registryAccess()),display.getResultItem(h.getLevel().registryAccess())),"Displayed material/count differs: "+source.id());
    }
}
