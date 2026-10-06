package com.sange.tm_wagon.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Test real mouse input and server outcomes with optional mods loaded. */
public final class WorldInteractionClientSmoke {
    private static boolean opened;
    private static int ticks, loading;
    private static volatile String failure;
    private static final BlockPos BASE = new BlockPos(64,100,64);

    public static void tick() throws ReflectiveOperationException {
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if(ticks<115) mc.mouseHandler.releaseMouse();
        if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen && mc.getOverlay()==null) {
            mc.setScreen(new TitleScreen()); return;
        }
        if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
            opened=true;
            mc.createWorldOpenFlows().openWorld(System.getProperty("tm_wagon.fabricSmokeWorld","repro"),
                ()->{throw new IllegalStateException("Missing interaction test world");});
            return;
        }
        if (failure!=null) throw new IllegalStateException(failure);
        if (mc.player==null || mc.level==null) {
            if (++loading>1600) throw new IllegalStateException("Interaction world loading timeout");
            return;
        }
        if (ticks==0 && mc.screen!=null) return;
        ticks++;
        if (ticks==1) server(mc,()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            player.stopRiding(); player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent(); player.getInventory().selected=0;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
            for (var pos:BlockPos.betweenClosed(60,100,60,68,106,68))
                player.serverLevel().setBlock(pos,pos.getY()==100?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
            player.teleportTo(64.5,101,62.5);
        });
        if (ticks==30) {
            require(mc.gameMode.hasInfiniteItems(),"Creative mode not synced");
            mc.setScreen(null);
            equip(mc,new ItemStack(Items.OAK_PLANKS));
            assertBinding(mc.options.keyUse,1);
            assertBinding(mc.options.keyAttack,0);
        }
        if (ticks==40) {
            mc.player.setYRot(0); mc.player.setXRot(39);
            mc.hitResult=new BlockHitResult(new Vec3(64.5,101,64.5),Direction.UP,BASE,false);
            mouse(mc,1,1);
        }
        if (ticks==43) mouse(mc,1,0);
        if (ticks==50) server(mc,()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            require(player.serverLevel().getBlockState(BASE.above()).is(Blocks.OAK_PLANKS),"Mouse right-click did not place oak planks on server");
        });
        if (ticks==60) equip(mc,new ItemStack(Items.BOW));
        if (ticks==70) {
            mc.player.setXRot(-90);
            mc.hitResult=BlockHitResult.miss(mc.player.getEyePosition().add(0,4,0),Direction.DOWN,mc.player.blockPosition().above(4));
            mouse(mc,1,1);
        }
        if (ticks==75) {
            require(mc.player.isUsingItem(),"Mouse right-click did not start using bow");
            server(mc,()->require(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().isUsingItem(),"Bow use did not reach server"));
            mouse(mc,1,0);
        }
        if (ticks==90) {
            equip(mc,ItemStack.EMPTY);
            server(mc,()->mc.getSingleplayerServer().overworld().setBlock(BASE.above(),Blocks.CHEST.defaultBlockState(),3));
        }
        if (ticks==100) {
            mc.player.setXRot(29);
            mc.hitResult=new BlockHitResult(new Vec3(64.5,101.5,64),Direction.NORTH,BASE.above(),false);
            mouse(mc,1,1);
        }
        if (ticks==103) mouse(mc,1,0);
        if (ticks==115) {
            require(mc.screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen,"Mouse right-click did not open chest GUI");
            mc.player.closeContainer();
        }
        if (ticks==120) equip(mc,new ItemStack(Items.IRON_AXE));
        if (ticks==130) {
            mc.player.setYRot(0);mc.player.setXRot(29);
            mc.hitResult=new BlockHitResult(new Vec3(64.5,101.5,64),Direction.NORTH,BASE.above(),false);
            mouse(mc,0,1);
        }
        if (ticks==133) mouse(mc,0,0);
        if (ticks==140) server(mc,()->require(mc.getSingleplayerServer().overworld().getBlockState(BASE.above()).isAir(),"Mouse left-click did not break chest on server"));
        if (ticks==150) {
            verifyGunCancellation(mc);
            LogUtils.getLogger().info("WORLD_INTERACTION_PASS: real mouse placement, bow use, chest GUI and block breaking confirmed with server");
            mc.stop();
        }
    }
    private static void assertBinding(KeyMapping binding,int button) {
        var key=InputConstants.Type.MOUSE.getOrCreate(button);
        KeyMapping.set(key,true); KeyMapping.click(key);
        require(binding.isDown() && binding.consumeClick(),"Physical mouse input did not reach "+binding.getName());
        KeyMapping.set(key,false);
        require(!binding.isDown(),"Mouse release did not reach "+binding.getName());
    }
    private static void verifyGunCancellation(Minecraft mc) throws ReflectiveOperationException {
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("tacz")) return;
        var gun=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("tacz:modern_kinetic_gun"));
        require(gun!=Items.AIR,"TACZ gun item missing");
        var previous=mc.player.getMainHandItem();
        mc.player.getInventory().setItem(0,new ItemStack(gun));
        try {
            var type=Class.forName("cn.sh1rocu.tacz.api.event.InputEvent$InteractionKeyMappingTriggered");
            var constructor=type.getConstructor(int.class,KeyMapping.class,net.minecraft.world.InteractionHand.class);
            var event=(net.fabricmc.fabric.api.event.Event<?>)type.getField("EVENT").get(null);
            var callback=Class.forName("cn.sh1rocu.tacz.api.event.InputEvent$IKMTCallback");
            for(int button=0;button<2;button++) {
                var input=constructor.newInstance(button,button==0?mc.options.keyAttack:mc.options.keyUse,net.minecraft.world.InteractionHand.MAIN_HAND);
                callback.getMethod("onInteractionKeyMappingTriggered",type).invoke(event.invoker(),input);
                require((boolean)type.getMethod("isCanceled").invoke(input),"TACZ no longer suppresses vanilla gun interaction");
            }
        } finally {mc.player.getInventory().setItem(0,previous);}
    }
    private static void equip(Minecraft mc,ItemStack stack) {
        mc.player.getInventory().setItem(0,stack);
        mc.gameMode.handleCreativeModeItemAdd(stack.copy(),36);
    }
    private static void mouse(Minecraft mc,int button,int action) throws ReflectiveOperationException {
        // Vanilla consumes the first left click to grab a released cursor.
        if(button==0 && action==1 && mc.screen==null) mc.mouseHandler.grabMouse();
        var method=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress",long.class,int.class,int.class,int.class);
        method.setAccessible(true); method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),button,action,0);
    }
    private static void require(boolean condition,String message) {
        if(!condition) throw new IllegalStateException(message);
    }
    private static void server(Minecraft mc,Runnable action) {
        mc.getSingleplayerServer().execute(()->{
            try {action.run();} catch(Throwable error) {
                failure=error.toString(); LogUtils.getLogger().error("World interaction test failed",error);
            }
        });
    }
    private WorldInteractionClientSmoke() {}
}
