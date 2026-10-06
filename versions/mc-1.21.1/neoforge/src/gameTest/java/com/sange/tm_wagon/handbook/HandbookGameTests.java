package com.sange.tm_wagon.handbook;

import com.sange.tm_wagon.ServerConfig;
import com.sange.tm_wagon.assembly.WagonContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class HandbookGameTests {
    private static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("tm_wagon","coachmans_manual");
    @GameTest(template="assembly_test")
    public static void registration_recipe_and_gift_require_patchouli(GameTestHelper h) {
        boolean loaded=ModList.get().isLoaded("patchouli");
        h.assertTrue(WagonContent.MANUAL.isPresent()==loaded,"Wrong optional handbook holder");
        h.assertTrue(BuiltInRegistries.ITEM.containsKey(ID)==loaded,"Wrong handbook item registration");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ID).isPresent()==loaded,"Wrong conditional handbook recipe");
        var p=h.makeMockServerPlayerInLevel();
        HandbookGifts.login(new PlayerEvent.PlayerLoggedInEvent(p));
        if(!loaded) {
            h.assertTrue(!p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(HandbookGifts.RECEIVED),"Missing Patchouli consumed the entitlement");
            h.assertTrue(p.getInventory().isEmpty(),"Missing Patchouli gave a handbook");
        }
        h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void first_join_gifts_once_and_persists_across_respawn(GameTestHelper h) {
        if(WagonContent.MANUAL.isEmpty()){h.succeed();return;}
        var manual=WagonContent.MANUAL.orElseThrow().get();
        var p=h.makeMockServerPlayerInLevel();
        h.assertTrue(p.getInventory().countItem(manual)==1,"Login did not give a handbook");
        // Simulate a player saved before this mod was installed, preserving unrelated data.
        p.getInventory().clearContent();
        var oldData=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        oldData.remove(HandbookGifts.RECEIVED);oldData.putInt("other_mod:test",42);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG,oldData);
        HandbookGifts.login(new PlayerEvent.PlayerLoggedInEvent(p));
        h.assertTrue(p.getInventory().countItem(manual)==1,"Existing-world player did not receive a handbook");
        h.assertTrue(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt("other_mod:test")==42,"Unrelated player data changed");
        HandbookGifts.login(new PlayerEvent.PlayerLoggedInEvent(p));
        h.assertTrue(p.getInventory().countItem(manual)==1,"Reconnect gave another handbook");
        var restored=h.makeMockServerPlayerInLevel();restored.getInventory().clearContent();
        // ServerPlayer.restoreFrom copies this exact compound through death; also saved by Entity persistence.
        restored.getPersistentData().put(Player.PERSISTED_NBT_TAG,p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).copy());
        HandbookGifts.giveOnFirstJoin(restored);
        h.assertTrue(restored.getInventory().countItem(manual)==0,"Respawn gave another handbook");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void disabled_first_join_and_full_inventory_are_safe(GameTestHelper h) {
        if(WagonContent.MANUAL.isEmpty()){h.succeed();return;}
        var manual=WagonContent.MANUAL.orElseThrow().get();
        boolean previous=ServerConfig.GIVE_MANUAL_ON_FIRST_JOIN.get();
        try {
            ServerConfig.GIVE_MANUAL_ON_FIRST_JOIN.set(false);
            var p=h.makeMockServerPlayerInLevel();
            h.assertTrue(p.getInventory().countItem(manual)==0,"Disabled option gave a book");
            h.assertTrue(!p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(HandbookGifts.RECEIVED),"Disabled gift recorded receipt");
            ServerConfig.GIVE_MANUAL_ON_FIRST_JOIN.set(true);HandbookGifts.giveOnFirstJoin(p);
            h.assertTrue(p.getInventory().countItem(manual)==1,"Enabling gifts did not grant the unreceived handbook");
            HandbookGifts.giveOnFirstJoin(p);
            h.assertTrue(p.getInventory().countItem(manual)==1,"Repeated login duplicated the gift");
            p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).remove(HandbookGifts.RECEIVED);
            p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1,2,1)));
            for(int slot=0;slot<36;slot++)p.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
            var nearby=p.getBoundingBox().inflate(2);
            int before=h.getLevel().getEntitiesOfClass(ItemEntity.class,nearby,e->e.getItem().is(manual)).size();
            HandbookGifts.giveOnFirstJoin(p);HandbookGifts.giveOnFirstJoin(p);
            int after=h.getLevel().getEntitiesOfClass(ItemEntity.class,nearby,e->e.getItem().is(manual)).size();
            h.assertTrue(after==before+1,"Full inventory lost or duplicated the gift");
        } finally { ServerConfig.GIVE_MANUAL_ON_FIRST_JOIN.set(previous); }
        h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void handbook_recipe_accepts_all_tagged_planks_and_any_order(GameTestHelper h) {
        if(WagonContent.MANUAL.isEmpty()){h.succeed();return;}
        var manual=WagonContent.MANUAL.orElseThrow().get();
        var recipe=h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("tm_wagon","coachmans_manual")).orElseThrow().value();
        h.assertTrue(recipe instanceof ShapelessRecipe,"Manual recipe is not shapeless");
        var crafting=(CraftingRecipe)recipe;
        int tested=0;
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var plank=new ItemStack(item);if(!plank.is(ItemTags.PLANKS))continue;
            for(int shift=0;shift<3;shift++) {
                var ingredients=new ArrayList<>(List.of(new ItemStack(Items.BOOK),new ItemStack(Items.LEAD),plank));
                java.util.Collections.rotate(ingredients,shift);
                var input=CraftingInput.of(3,1,ingredients);
                h.assertTrue(crafting.matches(input,h.getLevel()),"Tagged plank rejected: "+item);
                var output=crafting.assemble(input,h.getLevel().registryAccess());
                h.assertTrue(output.is(manual)&&output.getCount()==1,"Wrong handbook output");
            }tested++;
        }
        h.assertTrue(tested>=11,"Planks tag was not loaded");
        h.assertTrue(!crafting.matches(CraftingInput.of(3,1,List.of(new ItemStack(Items.BOOK),new ItemStack(Items.LEAD),new ItemStack(Items.STONE))),h.getLevel()),"Non-plank recipe accepted");h.succeed();
    }
}
