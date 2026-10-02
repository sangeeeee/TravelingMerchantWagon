package com.sange.tm_wagon.network;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid=TravelingMerchantWagon.MODID)
public final class WagonNetwork {
    /** Sleep transitions only; no per-tick messages or persistent moving respawn records. */
    public record MatSleep(int playerId,int wagonId,net.minecraft.core.BlockPos frame,java.util.UUID mat,int anchor,Vec3 position,float yaw,float pitch,float roll,Vec3 head,boolean sleeping) implements CustomPacketPayload {
        public static final Type<MatSleep> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"mat_sleep"));
        public static final StreamCodec<RegistryFriendlyByteBuf,MatSleep> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.playerId);buf.writeVarInt(p.wagonId);buf.writeBlockPos(p.frame);buf.writeUUID(p.mat);buf.writeVarInt(p.anchor);buf.writeDouble(p.position.x);buf.writeDouble(p.position.y);buf.writeDouble(p.position.z);buf.writeFloat(p.yaw);buf.writeFloat(p.pitch);buf.writeFloat(p.roll);buf.writeDouble(p.head.x);buf.writeDouble(p.head.y);buf.writeDouble(p.head.z);buf.writeBoolean(p.sleeping);},
            buf->new MatSleep(buf.readVarInt(),buf.readVarInt(),buf.readBlockPos(),buf.readUUID(),buf.readVarInt(),new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readFloat(),buf.readFloat(),buf.readFloat(),new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Input(int wagonId,byte forward,byte steering) implements CustomPacketPayload {
        public static final Type<Input> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"drive"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.wagonId);buf.writeByte(p.forward);buf.writeByte(p.steering);},
            buf->new Input(buf.readVarInt(),buf.readByte(),buf.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Push(int wagonId,byte forward,byte sideways) implements CustomPacketPayload {
        public static final Type<Push> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"push"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Push> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.wagonId);buf.writeByte(p.forward);buf.writeByte(p.sideways);},
            buf->new Push(buf.readVarInt(),buf.readByte(),buf.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** Local platform position plus the original world position/ground flag for vanilla fallback. */
    public record Standing(int wagonId,float x,float y,float z,float yaw,float pitch,boolean onGround,double worldX,double worldY,double worldZ) implements CustomPacketPayload {
        public static final Type<Standing> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"standing"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Standing> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.wagonId);buf.writeFloat(p.x);buf.writeFloat(p.y);buf.writeFloat(p.z);buf.writeFloat(p.yaw);buf.writeFloat(p.pitch);buf.writeBoolean(p.onGround);buf.writeDouble(p.worldX);buf.writeDouble(p.worldY);buf.writeDouble(p.worldZ);},
            buf->new Standing(buf.readVarInt(),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readBoolean(),buf.readDouble(),buf.readDouble(),buf.readDouble()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3").playToClient(MatSleep.TYPE,MatSleep.CODEC,(input,context)->context.enqueueWork(()->com.sange.tm_wagon.cargo.StrawMatSleep.receive(context.player().level(),input)));
        event.registrar("2").playToServer(Input.TYPE,Input.CODEC,(input,context)->context.enqueueWork(()->{
            if (context.player().getVehicle() instanceof WagonEntity wagon && wagon.getId()==input.wagonId)
                wagon.acceptInput(context.player(),input.forward,input.steering);
        }));
        event.registrar("2").playToServer(Push.TYPE,Push.CODEC,(input,context)->context.enqueueWork(()->{
            if(context.player().level().getEntity(input.wagonId) instanceof WagonEntity wagon)
                wagon.acceptPush(context.player(),input.forward,input.sideways);
        }));
        event.registrar("2").playToServer(Standing.TYPE,Standing.CODEC,(input,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player)handleStanding(player,input);
        }));
    }
    /** A stale platform contact must not swallow the original movement or camera update. */
    public static void handleStanding(ServerPlayer player,Standing input) {
        if(player.level().getEntity(input.wagonId) instanceof WagonEntity wagon
            &&wagon.platform().acceptStandingMovement(player,new Vec3(input.x,input.y,input.z),input.yaw,input.pitch,true))return;
        // Use the ordinary handler: collision, speed, riding and pending-teleport checks still apply.
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
            input.worldX,input.worldY,input.worldZ,input.yaw,input.pitch,input.onGround));
    }
    private WagonNetwork() {}
}
