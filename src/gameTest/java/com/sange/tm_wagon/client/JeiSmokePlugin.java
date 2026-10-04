package com.sange.tm_wagon.client;

import com.mojang.logging.LogUtils;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.material.*;
import java.util.List;
import mezz.jei.api.*;
import mezz.jei.api.constants.*;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

@JeiPlugin
public final class JeiSmokePlugin implements IModPlugin {
    private static IJeiRuntime runtime;
    private static int stage,ticks;
    private static volatile boolean serverChecked;
    private static volatile Throwable failure;
    private static RecipeHolder<CraftingRecipe> selected;
    private static final WagonMaterial SPRUCE=new WagonMaterial(WoodMaterial.SPRUCE,DyeColor.WHITE);
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("tm_wagon","jei_smoke"); }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime=value; }
    public static void tick() {
        if(!Boolean.getBoolean("tm_wagon.jeiSmokeTest")||runtime==null)return;
        if(failure!=null)throw new IllegalStateException("JEI server transfer failed",failure);
        var mc=Minecraft.getInstance();ticks++;
        if(stage==0) {
            var manager=runtime.getRecipeManager();var focuses=runtime.getJeiHelpers().getFocusFactory();int count=0;
            var category=manager.getRecipeCategory(RecipeTypes.CRAFTING);
            for(var source:mc.level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING))
                for(var display:WagonRecipeDisplays.create(source)) {
                    var output=display.value().getResultItem(mc.level.registryAccess());
                    var focus=focuses.createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,output);
                    var found=manager.createRecipeLookup(RecipeTypes.CRAFTING).limitFocus(List.of(focus)).get().toList();
                    require(found.stream().anyMatch(r->r.id().equals(display.id())),"JEI missing variant "+display.id());
                    require(found.stream().allMatch(r->ItemStack.isSameItemSameComponents(r.value().getResultItem(mc.level.registryAccess()),output)),"JEI mixed material outputs");
                    require(manager.createRecipeLayoutDrawable(category,display,focuses.createFocusGroup(List.of(focus))).isPresent(),"Broken recipe layout "+display.id());count++;
                }
            require(count>600,"Recipe display variants missing");
            var hammer=focuses.createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,new ItemStack(WagonContent.DISMANTLING_HAMMER.get()));
            require(manager.createRecipeLookup(RecipeTypes.CRAFTING).limitFocus(List.of(hammer)).get().findAny().isPresent(),"Hammer recipe missing");
            var output=SPRUCE.stack(WagonContent.PART_ITEMS.get(WagonPart.DOUBLE_WOODEN_SEAT).get());
            var focus=focuses.createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,output);
            selected=manager.createRecipeLookup(RecipeTypes.CRAFTING).limitFocus(List.of(focus)).get().findFirst().orElseThrow();
            LogUtils.getLogger().info("JEI_SMOKE: {} variant lookups/layouts and hammer passed",count);
            mc.getSingleplayerServer().execute(()->{
                try {
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.stopRiding();p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
                    p.teleportTo(0,81,0);p.setNoGravity(true);var pos=new BlockPos(1,81,0);p.serverLevel().setBlock(pos,Blocks.CRAFTING_TABLE.defaultBlockState(),3);
                    var single=WagonContent.PART_ITEMS.get(WagonPart.SINGLE_WOODEN_SEAT).get();
                    p.getInventory().setItem(0,new ItemStack(single,2));var correct=SPRUCE.stack(single);correct.setCount(2);p.getInventory().setItem(5,correct);
                    p.openMenu(Blocks.CRAFTING_TABLE.defaultBlockState().getMenuProvider(p.serverLevel(),pos));p.containerMenu.broadcastChanges();
                } catch(Throwable t) { failure=t; }
            });stage=1;ticks=0;
        } else if(stage==1&&ticks>25&&mc.player.containerMenu instanceof CraftingMenu menu) {
            var manager=runtime.getRecipeManager();var category=manager.getRecipeCategory(RecipeTypes.CRAFTING);
            var layout=manager.createRecipeLayoutDrawable(category,selected,runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            var transfer=runtime.getRecipeTransferManager().getRecipeTransferHandler(menu,category).orElseThrow();
            require(transfer.transferRecipe(menu,selected,layout.getRecipeSlotsView(),mc.player,false,false)==null,"JEI transfer validation failed");
            require(transfer.transferRecipe(menu,selected,layout.getRecipeSlotsView(),mc.player,false,true)==null,"JEI transfer failed");stage=2;ticks=0;
        } else if(stage==2&&ticks>25) {
            var output=mc.player.containerMenu.getSlot(0).getItem();
            require(output.is(WagonContent.PART_ITEMS.get(WagonPart.DOUBLE_WOODEN_SEAT).get())&&WagonMaterial.of(output).equals(SPRUCE),"Server rejected JEI transfer or selected wrong wood");
            mc.getSingleplayerServer().execute(()->{
                try {
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var menu=p.containerMenu;
                    require(WagonMaterial.of(menu.getSlot(0).getItem()).equals(SPRUCE),"Server output not spruce");
                    require(p.getInventory().getItem(0).getCount()==2,"JEI consumed oak instead of spruce");
                    menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,p);
                    require(menu.getCarried().getCount()==1&&WagonMaterial.of(menu.getCarried()).equals(SPRUCE),"Craft result wrong");
                    for(int slot=1;slot<=9;slot++)require(menu.getSlot(slot).getItem().isEmpty(),"Craft did not consume exactly two seats");
                    serverChecked=true;
                } catch(Throwable t) { failure=t; }
            });stage=3;ticks=0;
        } else if(stage==3&&serverChecked) {
            var red=new WagonMaterial(WoodMaterial.CHERRY,DyeColor.RED).stack(WagonContent.PART_ITEMS.get(WagonPart.TRIPLE_SEAT).get());
            runtime.getRecipesGui().show(List.of(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,red)));
            LogUtils.getLogger().info("JEI_SMOKE: native transfer and server crafting passed; displaying cherry/red triple seat");stage=4;ticks=0;
        } else if(stage==4&&ticks==30) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->{});
        } else if(stage==4&&ticks>=45) { LogUtils.getLogger().info("JEI_SMOKE: PASS");mc.stop();stage=5; }
    }
    private static void require(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
}
