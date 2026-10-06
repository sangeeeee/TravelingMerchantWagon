package com.sange.tm_wagon.material;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sange.tm_wagon.assembly.*;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/** One serializer checks material relationships and derives the result from real inputs. */
public final class WagonComponentRecipe extends CustomRecipe {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,"tm_wagon");
    public static final RegistryObject<RecipeSerializer<WagonComponentRecipe>> SERIALIZER=SERIALIZERS.register("component",Serializer::new);
    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }
    private final String component;
    private final List<String> pattern;
    private final int count;
    public WagonComponentRecipe(ResourceLocation id,String component,List<String> pattern,int count) {
        super(id,CraftingBookCategory.MISC);
        boolean known=java.util.Arrays.stream(WagonPart.values()).anyMatch(p->p.id.equals(component))
            ||List.of("wagon_assembly_frame","wagon_straw_mat","wagon_stool","wagon_cabinet","wagon_cargo_cover","wagon_canopy").contains(component);
        boolean shapeless=component.equals("double_horse_shafts")||component.equals("double_wooden_seat")||component.equals("triple_wooden_seat");
        if(!known||count<1||count>64||pattern.size()>3||pattern.isEmpty()!=shapeless)
            throw new IllegalArgumentException("Invalid wagon component recipe: "+component);
        if(!shapeless) {
            int width=pattern.get(0).length();
            if(width<1||width>3||pattern.stream().anyMatch(row->row.length()!=width||row.chars().anyMatch(c->" SPLWIHRTBCEDOUV".indexOf(c)<0)))
                throw new IllegalArgumentException("Invalid wagon recipe pattern: "+component);
        }
        this.component=component;this.pattern=List.copyOf(pattern);this.count=count;
    }
    Item output() {
        for(var part:WagonPart.values())if(part.id.equals(component))return WagonContent.PART_ITEMS.get(part).get();
        return switch(component) {
            case "wagon_assembly_frame"->WagonContent.FRAME_ITEM.get();case "wagon_straw_mat"->WagonContent.STRAW_MAT.get();
            case "wagon_stool"->WagonContent.STOOL.get();case "wagon_cabinet"->WagonContent.CABINET.get();
            case "wagon_cargo_cover"->WagonContent.CARGO_COVER.get();case "wagon_canopy"->WagonContent.CANOPY.get();
            default->throw new IllegalArgumentException("Unknown wagon recipe: "+component);
        };
    }
    private static Item part(WagonPart p) { return WagonContent.PART_ITEMS.get(p).get(); }
    List<String> pattern() { return pattern; }
    String component() { return component; }
    int count() { return count; }
    boolean samePlanks() {
        return component.equals("cargo_body")||component.equals("single_wooden_seat")||component.equals("wagon_stool")||component.equals("wagon_cabinet");
    }
    private boolean accepts(char token,ItemStack stack) {
        if(token==' ')return stack.isEmpty();if(stack.isEmpty())return false;
        return switch(token) {
            case 'S'->stack.is(Items.STICK);case 'P'->stack.is(ItemTags.PLANKS);
            case 'L'->stack.is(ItemTags.LOGS);case 'W'->stack.is(ItemTags.WOOL);
            case 'I'->stack.is(Items.IRON_INGOT);case 'H'->stack.is(Items.WHEAT);
            case 'R'->stack.is(Items.LEAD)||stack.is(Items.VINE);case 'B'->stack.is(Items.BARREL);
            case 'T'->stack.is(ItemTags.WOODEN_TRAPDOORS);
            case 'C'->stack.is(part(WagonPart.CARGO_BODY));case 'E'->stack.is(part(WagonPart.SINGLE_WOODEN_SEAT));
            case 'U'->stack.is(part(WagonPart.LONG_CARGO_BODY));case 'V'->stack.is(part(WagonPart.TRIPLE_WOODEN_SEAT));
            case 'D'->stack.is(part(WagonPart.DOUBLE_WOODEN_SEAT));case 'O'->stack.is(part(WagonPart.SMALL_WHEEL));
            default->false;
        };
    }
    private static DyeColor wool(ItemStack stack) {
        for(var dye:DyeColor.values())if(stack.is(BuiltInRegistries.ITEM.get(new ResourceLocation(dye.getName()+"_wool"))))return dye;
        return null;
    }
    private WagonMaterial style(net.minecraft.world.inventory.CraftingContainer input,int ox,int oy,boolean mirror) {
        WoodMaterial chosen=null;DyeColor colour=null;
        boolean samePlanks=samePlanks();
        int width=pattern.get(0).length();
        for(int y=0;y<input.getHeight();y++)for(int x=0;x<input.getWidth();x++) {
            int px=x-ox,py=y-oy;
            char token=px>=0&&px<width&&py>=0&&py<pattern.size()?pattern.get(py).charAt(mirror?width-1-px:px):' ';
            var stack=input.getItem(x+y*input.getWidth());if(!accepts(token,stack))return null;
            WoodMaterial wood=null;
            if(token=='P'&&samePlanks) { wood=WoodMaterial.ofPlanks(stack);if(wood==null)return null; }
            if(token=='L'&&(component.equals("cargo_body")||component.equals("small_wheel"))) { wood=WoodMaterial.ofLog(stack);if(wood==null)return null; }
            if(token=='T'&&component.equals("cargo_body")) {
                for(var value:WoodMaterial.values())if(stack.is(value.trapdoor()))wood=value;
                if(wood==null)return null;
            }
            if(token=='C'||token=='E'||token=='D'||token=='O'||token=='U'||token=='V')wood=WagonMaterial.of(stack).wood();
            if(wood!=null) { if(chosen!=null&&chosen!=wood)return null;chosen=wood; }
            if(token=='W') { var dye=wool(stack);if(dye==null||colour!=null&&colour!=dye)return null;colour=dye; }
        }
        return new WagonMaterial(chosen==null?WoodMaterial.OAK:chosen,colour==null?DyeColor.WHITE:colour);
    }
    private WagonMaterial match(net.minecraft.world.inventory.CraftingContainer input) {
        if(component.equals("triple_wooden_seat")) {
            int places=0;WoodMaterial wood=null;
            for(int i=0;i<input.getContainerSize();i++)if(!input.getItem(i).isEmpty()) {
                var s=input.getItem(i);
                int seats=s.is(part(WagonPart.SINGLE_WOODEN_SEAT))?1:s.is(part(WagonPart.DOUBLE_WOODEN_SEAT))?2:0;
                if(seats==0||(places+=seats)>3)return null;
                var current=WagonMaterial.of(s).wood();if(wood!=null&&wood!=current)return null;wood=current;
            }
            return places==3?new WagonMaterial(wood,DyeColor.WHITE):null;
        }
        if(component.equals("double_horse_shafts")||component.equals("double_wooden_seat")) {
            Item required=part(component.equals("double_horse_shafts")?WagonPart.SINGLE_HORSE_SHAFTS:WagonPart.SINGLE_WOODEN_SEAT);
            int n=0;WoodMaterial wood=null;
            for(int i=0;i<input.getContainerSize();i++)if(!input.getItem(i).isEmpty()) {
                var s=input.getItem(i);if(!s.is(required)||++n>2)return null;
                var current=WagonMaterial.of(s).wood();if(wood!=null&&wood!=current)return null;wood=current;
            }
            return n==2?new WagonMaterial(wood,DyeColor.WHITE):null;
        }
        if(pattern.isEmpty()||pattern.get(0).isEmpty())return null;
        int width=pattern.get(0).length(),height=pattern.size();
        for(int oy=0;oy<=input.getHeight()-height;oy++)for(int ox=0;ox<=input.getWidth()-width;ox++)for(boolean mirror:new boolean[]{false,true}) {
            var style=style(input,ox,oy,mirror);if(style!=null)return style;
        }return null;
    }
    @Override public boolean matches(net.minecraft.world.inventory.CraftingContainer input,Level level) { return match(input)!=null; }
    @Override public ItemStack assemble(net.minecraft.world.inventory.CraftingContainer input,net.minecraft.core.RegistryAccess lookup) {
        var material=match(input);if(material==null)return ItemStack.EMPTY;
        var item=output();material=new WagonMaterial(WagonMaterial.wooden(item)?material.wood():WoodMaterial.OAK,WagonMaterial.dyed(item)?material.colour():DyeColor.WHITE);
        var result=material.stack(item);result.setCount(count);return result;
    }
    @Override public boolean canCraftInDimensions(int width,int height) { return pattern.isEmpty()?width*height>=2:width>=pattern.get(0).length()&&height>=pattern.size(); }
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER.get(); }
    private static final class Serializer implements RecipeSerializer<WagonComponentRecipe> {
        @Override public WagonComponentRecipe fromJson(ResourceLocation id,com.google.gson.JsonObject json) {
            String component=net.minecraft.util.GsonHelper.getAsString(json,"component");
            var pattern=new java.util.ArrayList<String>();
            if(json.has("pattern"))for(var row:json.getAsJsonArray("pattern"))pattern.add(row.getAsString());
            return new WagonComponentRecipe(id,component,pattern,net.minecraft.util.GsonHelper.getAsInt(json,"count",1));
        }
        @Override public WagonComponentRecipe fromNetwork(ResourceLocation id,net.minecraft.network.FriendlyByteBuf b) {
            String c=b.readUtf();int n=b.readVarInt();if(n<0||n>3)throw new IllegalArgumentException("Invalid recipe height");
            var p=new java.util.ArrayList<String>();for(int j=0;j<n;j++)p.add(b.readUtf(3));return new WagonComponentRecipe(id,c,p,b.readVarInt());
        }
        @Override public void toNetwork(net.minecraft.network.FriendlyByteBuf b,WagonComponentRecipe r) {
            b.writeUtf(r.component);b.writeVarInt(r.pattern.size());r.pattern.forEach(b::writeUtf);b.writeVarInt(r.count);
        }
    }
}
