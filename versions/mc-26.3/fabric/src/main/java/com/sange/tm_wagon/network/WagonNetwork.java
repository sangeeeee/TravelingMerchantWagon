package com.sange.tm_wagon.network;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class WagonNetwork {
    /** Full backpack data is sent only to a viewer when opening, never in wagon render updates. */
    public record BackpackView(java.util.UUID entry,net.minecraft.world.item.ItemStack stack) implements CustomPacketPayload {
        public static final Type<BackpackView> TYPE=new Type<>(Identifier.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"backpack_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf,BackpackView> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeUUID(p.entry);net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(buf,p.stack);},
            buf->new BackpackView(buf.readUUID(),net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record BackpackUpdate(java.util.UUID entry,net.minecraft.core.component.DataComponentPatch patch) implements CustomPacketPayload {
        public static final Type<BackpackUpdate> TYPE=new Type<>(Identifier.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"backpack_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf,BackpackUpdate> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeUUID(p.entry);net.minecraft.core.component.DataComponentPatch.STREAM_CODEC.encode(buf,p.patch);},
            buf->new BackpackUpdate(buf.readUUID(),net.minecraft.core.component.DataComponentPatch.STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    /** Sleep transitions only; no per-tick messages or persistent moving respawn records. */
    public record MatSleep(int playerId,int wagonId,net.minecraft.core.BlockPos frame,java.util.UUID mat,int anchor,Vec3 position,float yaw,float pitch,float roll,Vec3 head,boolean sleeping,boolean reversed) implements CustomPacketPayload {
        public static final Type<MatSleep> TYPE=new Type<>(Identifier.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"mat_sleep"));
        public static final StreamCodec<RegistryFriendlyByteBuf,MatSleep> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.playerId);buf.writeVarInt(p.wagonId);buf.writeBlockPos(p.frame);buf.writeUUID(p.mat);buf.writeVarInt(p.anchor);buf.writeDouble(p.position.x);buf.writeDouble(p.position.y);buf.writeDouble(p.position.z);buf.writeFloat(p.yaw);buf.writeFloat(p.pitch);buf.writeFloat(p.roll);buf.writeDouble(p.head.x);buf.writeDouble(p.head.y);buf.writeDouble(p.head.z);buf.writeBoolean(p.sleeping);buf.writeBoolean(p.reversed);},
            buf->new MatSleep(buf.readVarInt(),buf.readVarInt(),buf.readBlockPos(),buf.readUUID(),buf.readVarInt(),new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readFloat(),buf.readFloat(),buf.readFloat(),new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readBoolean(),buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Input(int wagonId,byte forward,byte steering,boolean sprint) implements CustomPacketPayload {
        public static final Type<Input> TYPE=new Type<>(Identifier.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"drive"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.wagonId);buf.writeByte(p.forward);buf.writeByte(p.steering);buf.writeBoolean(p.sprint);},
            buf->new Input(buf.readVarInt(),buf.readByte(),buf.readByte(),buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Push(int wagonId,byte forward,byte sideways) implements CustomPacketPayload {
        public static final Type<Push> TYPE=new Type<>(Identifier.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"push"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Push> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.wagonId);buf.writeByte(p.forward);buf.writeByte(p.sideways);},
            buf->new Push(buf.readVarInt(),buf.readByte(),buf.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(BackpackView.TYPE,BackpackView.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(BackpackUpdate.TYPE,BackpackUpdate.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(MatSleep.TYPE,MatSleep.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(Input.TYPE,Input.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(Push.TYPE,Push.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(Input.TYPE,(input,context)->{
            if(context.player().getVehicle() instanceof WagonEntity wagon&&wagon.getId()==input.wagonId)
                wagon.acceptInput(context.player(),input.forward,input.steering,input.sprint);
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(Push.TYPE,(input,context)->{
            if(context.player().level().getEntity(input.wagonId) instanceof WagonEntity wagon)
                wagon.acceptPush(context.player(),input.forward,input.sideways);
        });
    }
    private WagonNetwork() {}
}
