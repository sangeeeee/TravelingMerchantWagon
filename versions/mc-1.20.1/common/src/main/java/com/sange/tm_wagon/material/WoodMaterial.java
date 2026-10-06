package com.sange.tm_wagon.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Only these vanilla wood families can supply material-aware recipes. */
public enum WoodMaterial implements StringRepresentable {
    OAK, SPRUCE, BIRCH, JUNGLE, ACACIA, DARK_OAK, MANGROVE, CHERRY, CRIMSON, WARPED;
    public static final StringRepresentable.EnumCodec<WoodMaterial> CODEC=StringRepresentable.fromEnum(WoodMaterial::values);
    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    public Item planks() { return item(getSerializedName()+"_planks"); }
    public Item log() { return item(getSerializedName()+(this==CRIMSON||this==WARPED?"_stem":"_log")); }
    public Item stripped() { return item("stripped_"+getSerializedName()+(this==CRIMSON||this==WARPED?"_stem":"_log")); }
    public Item trapdoor() { return item(getSerializedName()+"_trapdoor"); }
    private static Item item(String name) { return BuiltInRegistries.ITEM.get(new ResourceLocation(name)); }
    public static WoodMaterial ofPlanks(ItemStack stack) { for(var wood:values())if(stack.is(wood.planks()))return wood;return null; }
    public static WoodMaterial ofLog(ItemStack stack) { for(var wood:values())if(stack.is(wood.log())||stack.is(wood.stripped()))return wood;return null; }
}
