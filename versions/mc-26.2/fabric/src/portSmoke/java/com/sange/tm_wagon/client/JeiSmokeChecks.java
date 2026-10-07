package com.sange.tm_wagon.client;

import com.sange.tm_wagon.material.WagonRecipeDisplays;

/** Loaded only in the optional JEI test profile. */
final class JeiSmokeChecks {
    static void check() {
                var synced=mezz.jei.common.Internal.getClientSyncedRecipes().byType(net.minecraft.world.item.crafting.RecipeType.CRAFTING);
                int expected=synced.stream().mapToInt(recipe->WagonRecipeDisplays.create(recipe).size()).sum();
                var visible=mezz.jei.common.Internal.getJeiRuntime().getRecipeManager()
                    .createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).get()
                    .filter(recipe->recipe.id().identifier().getNamespace().equals("tm_wagon")&&recipe.id().identifier().getPath().startsWith("jei/"))
                    .toList();
                if(expected<200||visible.size()!=expected||visible.stream().noneMatch(recipe->recipe.id().identifier().getPath().contains("wagon_straw_mat")))
                    throw new IllegalStateException("JEI wagon recipe display missing: expected="+expected+", visible="+visible.size());
                com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_2_JEI_PASS: {} material and colour recipe displays synchronized and visible",visible.size());
    }
}
