package com.sange.tm_wagon.handbook;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.PacketDistributor;

/** Synchronize only wagon recipe definitions when opening the handbook, including datapack edits. */
public final class HandbookRecipes {
    private static Map<Identifier,RecipeHolder<?>> recipes=Map.of();
    public static void send(ServerPlayer player) {
        var selected=player.level().getServer().getRecipeManager().recipeMap().byType(RecipeType.CRAFTING).stream()
            .filter(holder->holder.id().identifier().getNamespace().equals("tm_wagon")).<RecipeHolder<?>>map(holder->holder).toList();
        PacketDistributor.sendToPlayer(player,new com.sange.tm_wagon.network.WagonNetwork.HandbookRecipes(selected));
    }
    public static void receive(List<RecipeHolder<?>> supplied) {
        var updated=new java.util.HashMap<Identifier,RecipeHolder<?>>();
        for(var holder:supplied)updated.put(holder.id().identifier(),holder);
        recipes=Map.copyOf(updated);
    }
    public static RecipeHolder<?> recipe(Identifier id) { return recipes.get(id); }
    private HandbookRecipes() {}
}
