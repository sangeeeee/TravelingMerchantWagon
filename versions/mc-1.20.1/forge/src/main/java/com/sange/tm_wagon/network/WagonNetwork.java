package com.sange.tm_wagon.network;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
/** Forge 1.20.1 messages are directional and applied on the main game thread. */
public final class WagonNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("tm_wagon","main"),()->"1","1"::equals,"1"::equals);
    public record BackpackView(java.util.UUID entry,net.minecraft.world.item.ItemStack stack) {
        public void encode(FriendlyByteBuf buf) {var p=this;buf.writeUUID(p.entry);buf.writeItem(p.stack);}
        public static BackpackView decode(FriendlyByteBuf buf) {return new BackpackView(buf.readUUID(),buf.readItem());}
    }
    public record BackpackUpdate(java.util.UUID entry,net.minecraft.nbt.CompoundTag patch) {
        public void encode(FriendlyByteBuf buf) {var p=this;buf.writeUUID(p.entry);buf.writeNbt(p.patch);}
        public static BackpackUpdate decode(FriendlyByteBuf buf) {return new BackpackUpdate(buf.readUUID(),buf.readNbt());}
    }
    public record MatSleep(int playerId,int wagonId,net.minecraft.core.BlockPos frame,java.util.UUID mat,int anchor,Vec3 position,float yaw,float pitch,float roll,Vec3 head,boolean sleeping,boolean reversed) {
        public void encode(FriendlyByteBuf buf) {var p=this;buf.writeVarInt(p.playerId);buf.writeVarInt(p.wagonId);buf.writeBlockPos(p.frame);buf.writeUUID(p.mat);buf.writeVarInt(p.anchor);buf.writeDouble(p.position.x);buf.writeDouble(p.position.y);buf.writeDouble(p.position.z);buf.writeFloat(p.yaw);buf.writeFloat(p.pitch);buf.writeFloat(p.roll);buf.writeDouble(p.head.x);buf.writeDouble(p.head.y);buf.writeDouble(p.head.z);buf.writeBoolean(p.sleeping);buf.writeBoolean(p.reversed);}
        public static MatSleep decode(FriendlyByteBuf buf) {return new MatSleep(buf.readVarInt(),buf.readVarInt(),buf.readBlockPos(),buf.readUUID(),buf.readVarInt(),new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readFloat(),buf.readFloat(),buf.readFloat(),new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readBoolean(),buf.readBoolean());}
    }
    public record Input(int wagonId,byte forward,byte steering,boolean sprint) {
        public void encode(FriendlyByteBuf buf) {var p=this;buf.writeVarInt(p.wagonId);buf.writeByte(p.forward);buf.writeByte(p.steering);buf.writeBoolean(p.sprint);}
        public static Input decode(FriendlyByteBuf buf) {return new Input(buf.readVarInt(),buf.readByte(),buf.readByte(),buf.readBoolean());}
    }
    public record Push(int wagonId,byte forward,byte sideways) {
        public void encode(FriendlyByteBuf buf) {var p=this;buf.writeVarInt(p.wagonId);buf.writeByte(p.forward);buf.writeByte(p.sideways);}
        public static Push decode(FriendlyByteBuf buf) {return new Push(buf.readVarInt(),buf.readByte(),buf.readByte());}
    }
    public static void register() {
        CHANNEL.messageBuilder(BackpackView.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder(BackpackView::encode).decoder(BackpackView::decode).consumerMainThread((p,c)->client(p)).add();
        CHANNEL.messageBuilder(BackpackUpdate.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(BackpackUpdate::encode).decoder(BackpackUpdate::decode).consumerMainThread((p,c)->client(p)).add();
        CHANNEL.messageBuilder(MatSleep.class,2,NetworkDirection.PLAY_TO_CLIENT).encoder(MatSleep::encode).decoder(MatSleep::decode).consumerMainThread((p,c)->client(p)).add();
        CHANNEL.messageBuilder(Input.class,3,NetworkDirection.PLAY_TO_SERVER).encoder(Input::encode).decoder(Input::decode).consumerMainThread((p,c)->{
            var player=c.get().getSender();if(player!=null&&player.getVehicle() instanceof WagonEntity wagon&&wagon.getId()==p.wagonId)wagon.acceptInput(player,p.forward,p.steering,p.sprint);
        }).add();
        CHANNEL.messageBuilder(Push.class,4,NetworkDirection.PLAY_TO_SERVER).encoder(Push::encode).decoder(Push::decode).consumerMainThread((p,c)->{
            var player=c.get().getSender();if(player!=null&&player.level().getEntity(p.wagonId) instanceof WagonEntity wagon)wagon.acceptPush(player,p.forward,p.sideways);
        }).add();
    }
    private static void client(Object packet) {
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->ClientPackets.receive(packet));
    }
    private static final class ClientPackets {
        static void receive(Object packet) {
            var player=net.minecraft.client.Minecraft.getInstance().player;if(player==null)return;
            if(packet instanceof BackpackView p)com.sange.tm_wagon.cargo.BackpackSessions.receive(player,p.entry,p.stack);
            else if(packet instanceof BackpackUpdate p&&net.minecraftforge.fml.ModList.get().isLoaded("travelersbackpack"))com.sange.tm_wagon.compat.backpack.TravelerCargo.receive(player,p.entry,p.patch);
            else if(packet instanceof MatSleep p)com.sange.tm_wagon.cargo.StrawMatSleep.receive(player.level(),p);
        }
    }
    public static void sendToServer(Object packet) {CHANNEL.sendToServer(packet);}
    public static void sendToPlayer(ServerPlayer player,Object packet) {CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);}
    public static void sendToPlayersTrackingEntity(net.minecraft.world.entity.Entity entity,Object packet) {CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(()->entity),packet);}
    public static void sendToPlayersTrackingEntityAndSelf(net.minecraft.world.entity.Entity entity,Object packet) {CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->entity),packet);}
    private WagonNetwork() {}
}
