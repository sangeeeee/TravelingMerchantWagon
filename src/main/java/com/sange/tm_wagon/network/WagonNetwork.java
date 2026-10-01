package com.sange.tm_wagon.network;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid=TravelingMerchantWagon.MODID)
public final class WagonNetwork {
    public record Input(int wagonId,byte forward,byte steering) implements CustomPacketPayload {
        public static final Type<Input> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"drive"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.wagonId);buf.writeByte(p.forward);buf.writeByte(p.steering);},
            buf->new Input(buf.readVarInt(),buf.readByte(),buf.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Input.TYPE,Input.CODEC,(input,context)->context.enqueueWork(()->{
            if (context.player().getVehicle() instanceof WagonEntity wagon && wagon.getId()==input.wagonId)
                wagon.acceptInput(context.player(),input.forward,input.steering);
        }));
    }
    private WagonNetwork() {}
}
