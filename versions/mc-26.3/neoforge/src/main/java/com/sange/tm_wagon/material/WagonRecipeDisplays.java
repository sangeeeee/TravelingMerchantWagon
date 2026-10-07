package com.sange.tm_wagon.material;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.assembly.WagonPart;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

/** Client recipe-viewer projections. Never added to the authoritative recipe manager. */
public final class WagonRecipeDisplays {
    public static List<RecipeHolder<CraftingRecipe>> create(RecipeHolder<CraftingRecipe> source) {
        if(!(source.value() instanceof WagonComponentRecipe recipe))return List.of();
        var result=new ArrayList<RecipeHolder<CraftingRecipe>>();
        for(var wood:WagonMaterial.wooden(recipe.output())?WoodMaterial.values():new WoodMaterial[]{WoodMaterial.OAK})
            for(var colour:WagonMaterial.dyed(recipe.output())?DyeColor.values():new DyeColor[]{DyeColor.WHITE}) {
                var material=new WagonMaterial(wood,colour);
                append(result,source,recipe,material);
            }
        return List.copyOf(result);
    }
    /** One handbook example, without generating hundreds of unrelated material variants. */
    public static List<RecipeHolder<CraftingRecipe>> create(RecipeHolder<CraftingRecipe> source,WagonMaterial material) {
        if(!(source.value() instanceof WagonComponentRecipe recipe))return List.of();
        var result=new ArrayList<RecipeHolder<CraftingRecipe>>();
        append(result,source,recipe,material);
        return List.copyOf(result);
    }
    private static void append(List<RecipeHolder<CraftingRecipe>> result,RecipeHolder<CraftingRecipe> source,WagonComponentRecipe recipe,WagonMaterial material) {
        var wood=material.wood();var colour=material.colour();
        var output=material.stack(recipe.output());output.setCount(recipe.count());
        String suffix=wood.getSerializedName()+"/"+colour.getName();
        if(recipe.pattern().isEmpty()) {
            var single=component(recipe.component().equals("double_horse_shafts")?WagonPart.SINGLE_HORSE_SHAFTS:WagonPart.SINGLE_WOODEN_SEAT,wood);
            var inputs=NonNullList.<Ingredient>create();inputs.add(single);inputs.add(single);
            if(recipe.component().equals("triple_wooden_seat")) {
                inputs.add(single);
                add(result,source,suffix+"/double_single",new ShapelessRecipe(new Recipe.CommonInfo(true),new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC,""),ItemStackTemplate.fromNonEmptyStack(output),
                    List.of(component(WagonPart.DOUBLE_WOODEN_SEAT,wood),single)));
            }
            add(result,source,suffix,new ShapelessRecipe(new Recipe.CommonInfo(true),new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC,""),ItemStackTemplate.fromNonEmptyStack(output),inputs));
        } else {
            var key=new HashMap<Character,Ingredient>();
            for(String row:recipe.pattern())for(char token:row.toCharArray())if(token!=' ')
                key.computeIfAbsent(token,t->ingredient(recipe,t,material));
            add(result,source,suffix,new ShapedRecipe(new Recipe.CommonInfo(true),new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC,""),ShapedRecipePattern.of(key,recipe.pattern()),ItemStackTemplate.fromNonEmptyStack(output)));
        }
    }
    private static void add(List<RecipeHolder<CraftingRecipe>> result,RecipeHolder<CraftingRecipe> source,String suffix,CraftingRecipe display) {
        result.add(new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,Identifier.fromNamespaceAndPath("tm_wagon","jei/"+source.id().identifier().getNamespace()+"/"+source.id().identifier().getPath()+"/"+suffix)),display));
    }
    private static Ingredient component(WagonPart part,WoodMaterial wood) {
        // Ingredient.of(ItemStack) ignores components. Strict comparison keeps recipe
        // transfer from choosing a different wood, including the component-free oak default.
        return DataComponentIngredient.of(true,new WagonMaterial(wood,DyeColor.WHITE).stack(WagonContent.PART_ITEMS.get(part).get()));
    }
    private static Ingredient ingredient(WagonComponentRecipe recipe,char token,WagonMaterial material) {
        var wood=material.wood();
        return switch(token) {
            case 'S'->Ingredient.of(Items.STICK);
            case 'P'->recipe.samePlanks()?Ingredient.of(wood.planks()):Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(ItemTags.PLANKS));
            case 'L'->recipe.component().equals("cargo_body")||recipe.component().equals("small_wheel")
                ?Ingredient.of(wood.log(),wood.stripped()):Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(ItemTags.LOGS));
            case 'T'->recipe.component().equals("cargo_body")?Ingredient.of(wood.trapdoor()):Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(ItemTags.WOODEN_TRAPDOORS));
            case 'W'->Ingredient.of(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(material.colour().getName()+"_wool")));
            case 'I'->Ingredient.of(Items.IRON_INGOT);
            case 'R'->Ingredient.of(Items.LEAD,Items.VINE);case 'B'->Ingredient.of(Items.BARREL);
            case 'C'->component(WagonPart.CARGO_BODY,wood);case 'U'->component(WagonPart.LONG_CARGO_BODY,wood);
            case 'E'->component(WagonPart.SINGLE_WOODEN_SEAT,wood);case 'D'->component(WagonPart.DOUBLE_WOODEN_SEAT,wood);
            case 'V'->component(WagonPart.TRIPLE_WOODEN_SEAT,wood);case 'O'->component(WagonPart.SMALL_WHEEL,wood);
            default->throw new IllegalArgumentException("Unknown wagon ingredient: "+token);
        };
    }
    private WagonRecipeDisplays() {}
}
