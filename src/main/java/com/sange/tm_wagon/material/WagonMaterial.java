package com.sange.tm_wagon.material;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.*;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Immutable style data is saved by ItemStack and participates in normal stacking. */
public record WagonMaterial(WoodMaterial wood,DyeColor colour) {
    public static final WagonMaterial DEFAULT=new WagonMaterial(WoodMaterial.OAK,DyeColor.WHITE);
    public static final Codec<WagonMaterial> CODEC=RecordCodecBuilder.create(i->i.group(
        WoodMaterial.CODEC.optionalFieldOf("wood",WoodMaterial.OAK).forGetter(WagonMaterial::wood),
        DyeColor.CODEC.optionalFieldOf("colour",DyeColor.WHITE).forGetter(WagonMaterial::colour)).apply(i,WagonMaterial::new));
    public static final StreamCodec<ByteBuf,WagonMaterial> STREAM_CODEC=StreamCodec.of(
        (b,v)->{b.writeByte(v.wood.ordinal());b.writeByte(v.colour.getId());},
        b->{int w=b.readUnsignedByte(),c=b.readUnsignedByte();if(w>=WoodMaterial.values().length||c>=16)throw new IllegalArgumentException("Invalid wagon material");return new WagonMaterial(WoodMaterial.values()[w],DyeColor.byId(c));});
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS=DeferredRegister.create(Registries.DATA_COMPONENT_TYPE,"tm_wagon");
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<WagonMaterial>> TYPE=COMPONENTS.register("material",()->DataComponentType.<WagonMaterial>builder().persistent(CODEC).networkSynchronized(STREAM_CODEC).build());
    public static void register(IEventBus bus) { COMPONENTS.register(bus);WagonComponentRecipe.register(bus); }
    public static WagonMaterial of(ItemStack stack) { return stack.getOrDefault(TYPE.get(),DEFAULT); }
    public ItemStack stack(Item item) { var stack=new ItemStack(item);if(!equals(DEFAULT))stack.set(TYPE.get(),this);return stack; }
    public CompoundTag save() { var tag=new CompoundTag();tag.putByte("Wood",(byte)wood.ordinal());tag.putByte("Colour",(byte)colour.getId());return tag; }
    public static WagonMaterial load(CompoundTag tag) {
        int w=Byte.toUnsignedInt(tag.getByte("Wood")),c=Byte.toUnsignedInt(tag.getByte("Colour"));
        return new WagonMaterial(w<WoodMaterial.values().length?WoodMaterial.values()[w]:WoodMaterial.OAK,c<16?DyeColor.byId(c):DyeColor.WHITE);
    }
    public static CompoundTag save(Map<WagonSlot,WagonMaterial> materials) {
        var tag=new CompoundTag();materials.forEach((slot,value)->{if(!value.equals(DEFAULT))tag.put(slot.name(),value.save());});return tag;
    }
    public static Map<WagonSlot,WagonMaterial> loadSlots(CompoundTag tag) {
        var map=new EnumMap<WagonSlot,WagonMaterial>(WagonSlot.class);
        for(var slot:WagonSlot.values())if(tag.contains(slot.name(),10))map.put(slot,load(tag.getCompound(slot.name())));
        return Map.copyOf(map);
    }
    public static boolean wooden(WagonPart p) { return p!=WagonPart.SINGLE_HORSE_SHAFTS&&p!=WagonPart.DOUBLE_HORSE_SHAFTS; }
    public static boolean cushioned(WagonPart p) { return p==WagonPart.SINGLE_SEAT||p==WagonPart.DOUBLE_SEAT; }
    public static WagonMaterial forPart(WagonPart p,ItemStack stack) { var v=of(stack);return new WagonMaterial(wooden(p)?v.wood:WoodMaterial.OAK,cushioned(p)?v.colour:DyeColor.WHITE); }
    public static boolean wooden(Item item) { return item instanceof WagonPartItem p?wooden(p.part()):item instanceof WagonStoolItem||item instanceof WagonCabinetItem; }
    public static boolean dyed(Item item) { return item instanceof WagonPartItem p?cushioned(p.part()):item instanceof WagonCoverItem||item instanceof WagonCanopyItem; }
    public static Component name(ItemStack stack,Component base) {
        var m=of(stack);boolean wood=wooden(stack.getItem()),colour=dyed(stack.getItem());if(!wood&&!colour)return base;
        var w=Component.translatable("material.tm_wagon.wood."+m.wood.getSerializedName());
        var c=Component.translatable("material.tm_wagon.colour."+m.colour.getName());
        if(wood&&colour)return Component.translatable("item.tm_wagon.wood_colour_name",w,c,base);
        return Component.translatable(wood?"item.tm_wagon.wood_name":"item.tm_wagon.colour_name",wood?w:c,base);
    }
    public static void creative(CreativeModeTab.Output output,Item item) {
        if(!wooden(item)&&!dyed(item)) { output.accept(item);return; }
        for(var w:wooden(item)?WoodMaterial.values():new WoodMaterial[]{WoodMaterial.OAK})
            for(var c:dyed(item)?DyeColor.values():new DyeColor[]{DyeColor.WHITE})output.accept(new WagonMaterial(w,c).stack(item));
    }
}
