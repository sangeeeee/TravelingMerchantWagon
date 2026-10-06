package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.material.*;
import java.util.ArrayList;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class BreakGameTests {
    @GameTest(template="assembly_test")
    public static void native_component_and_frame_break_broadcast_one_effect_and_drop_once(GameTestHelper h) {
        var f=InteractionGameTests.body(h,WagonPart.CARGO_BODY,Direction.NORTH);
        var p=InteractionGameTests.player(h);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_AXE));
        var style=new WagonMaterial(WoodMaterial.SPRUCE,DyeColor.WHITE);
        h.assertTrue(f.install(WagonSlot.SEAT,WagonPart.DOUBLE_WOODEN_SEAT,null,style.stack(WagonContent.PART_ITEMS.get(WagonPart.DOUBLE_WOODEN_SEAT).get()))==null,"Seat fixture failed");
        var packets=new ArrayList<ClientboundLevelEventPacket>();
        var previousConnection=p.connection;
        p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p) {
            @Override public void send(Packet<?> packet) { if(packet instanceof ClientboundLevelEventPacket effect)packets.add(effect); }
        };
        java.util.List<net.minecraft.server.level.ServerPlayer> players;
        try {
            // getPlayers() is read-only. Register just this packet recipient in
            // the test server's backing list, without a real login/handshake.
            var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");field.setAccessible(true);
            players=(java.util.List<net.minecraft.server.level.ServerPlayer>)field.get(h.getLevel().getServer().getPlayerList());
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
        players.add(p);
        try {
            var point=f.cargoPose().point(new Vec3(.4,1.8,-2));
            var seat=BlockPos.containing(point);
            InteractionGameTests.look(p,point,point.add(2,1,0));
            h.assertTrue(f.hitSlot(seat,p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(6)))==WagonSlot.SEAT,"Seat break fixture ray missed the seat");
            h.assertTrue(p.gameMode.destroyBlock(seat)&&!f.has(WagonSlot.SEAT)&&f.has(WagonSlot.BODY),"Native seat break failed");
            h.assertTrue(packets.size()==1&&packets.get(0).getType()==2001&&packets.get(0).getPos().equals(seat)
                &&Block.stateById(packets.get(0).getData()).is(Blocks.SPRUCE_PLANKS),"Missing/duplicate break effect or wrong wood");
            var items=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(f.getBlockPos()).inflate(6));
            h.assertTrue(items.stream().filter(e->e.getItem().is(WagonContent.PART_ITEMS.get(WagonPart.DOUBLE_WOODEN_SEAT).get())&&WagonMaterial.of(e.getItem()).wood()==WoodMaterial.SPRUCE).mapToInt(e->e.getItem().getCount()).sum()==1,"Seat drop duplicated/lost material");
            h.assertTrue(p.gameMode.destroyBlock(f.getBlockPos()),"Native frame break failed");
            h.assertTrue(packets.size()==2&&Block.stateById(packets.get(1).getData()).is(Blocks.OAK_PLANKS),"Frame teardown emitted effects per proxy or excluded breaker");
            items=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(f.getBlockPos()).inflate(6));
            h.assertTrue(items.stream().filter(e->e.getItem().is(WagonContent.FRAME_ITEM.get())).mapToInt(e->e.getItem().getCount()).sum()==1,"Frame drop duplicated");
            h.assertTrue(items.stream().filter(e->e.getItem().is(WagonContent.PART_ITEMS.get(WagonPart.CARGO_BODY).get())).mapToInt(e->e.getItem().getCount()).sum()==1,"Body drop duplicated");
            h.succeed();
        } finally {players.remove(p);p.connection=previousConnection;}
    }
}
