package com.sange.tm_wagon.client;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.StrawMatSleep;
import com.sange.tm_wagon.compat.maid.WagonMaidExtension;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Optional real-client check of native maid rendering and the shared one-shot mat session packets. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class WagonMaidClientSmoke {
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if(Boolean.getBoolean("tm_wagon.maidClientSmoke"))Scenario.tick(event);
    }
    private static final class Scenario {

    private static boolean opened;
    private static int ticks,loading;
    private static volatile String failure;
    private static WagonEntity wagon;
    private static EntityMaid maid;
    private static AssemblyFrameBlockEntity frame;
    public static void tick(ClientTickEvent.Pre event) {
        if(!Boolean.getBoolean("tm_wagon.maidClientSmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("No maid test world");});return;
        }
        if(failure!=null)throw new IllegalStateException(failure);
        if(mc.player==null||mc.level==null||mc.screen!=null&&!mc.player.isSleeping()) { if(++loading>1600)throw new IllegalStateException("Maid client loading timeout");return; }
        ticks++;
        if(ticks==1)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();
            p.stopRiding();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(5,84,-4);
            var area=new net.minecraft.world.phys.AABB(-10,75,-10,10,95,10);
            for(var w:level.getEntitiesOfClass(WagonEntity.class,area))w.discard();
            for(var m:level.getEntitiesOfClass(EntityMaid.class,area))m.discard();
            for(var at:BlockPos.betweenClosed(-8,81,-8,8,91,8))level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
            for(var at:BlockPos.betweenClosed(-8,80,-8,8,80,8))level.setBlock(at,Blocks.STONE.defaultBlockState(),3);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.setDayTime(1000);
            wagon=WagonContent.WAGON.get().create(level);var parts=WagonEntity.defaultParts();parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
            wagon.configure(parts,Direction.NORTH);wagon.setPos(.5,81,.5);level.addFreshEntity(wagon);
            level.setBlock(wagon.blockPosition(),WagonContent.FRAME.get().defaultBlockState(),3);
            var atMat=wagon.pose().point(wagon.cargo().centreAt(8).add(-2,1,0));p.teleportTo(atMat.x,atMat.y,atMat.z);
            require(wagon.cargo().place(8,new ItemStack(WagonContent.STRAW_MAT.get()),p)==null,"Mat setup failed");
            var tag=wagon.cargo().save(level.registryAccess(),false);var cover=new net.minecraft.nbt.CompoundTag();cover.putBoolean("Installed",true);tag.put("Cover",cover);
            wagon.cargo().load(tag,level.registryAccess());wagon.cargoGeometryChanged();
            maid=EntityMaid.TYPE.create(level);maid.setPos(3,81,-1);maid.tame(p);maid.setHomeModeEnable(false);
            maid.setTask(TaskManager.findTask(WagonMaidExtension.TASK).orElseThrow());maid.setSchedule(MaidSchedule.DAY);level.addFreshEntity(maid);
        });
        if(ticks==45) {
            require(mc.level.getEntity(maid.getId()) instanceof EntityMaid m&&m.getVehicle() instanceof WagonEntity w&&w.passengerSeat(m)==1,"Client did not observe companion boarding");
            server(mc,()->((net.minecraft.server.level.ServerLevel)maid.level()).setDayTime(17000));
        }
        if(ticks==80) {
            require(mc.level.getEntity(maid.getId()) instanceof EntityMaid m&&m.isSleeping()&&StrawMatSleep.sleepingPose(m)!=null,"Client maid sleep/session was not synchronized");
            server(mc,()->{
                require(maid.isSleeping(),"Server maid did not stay asleep");maid.setNoAi(true);
            });
        }
        if(ticks>=81&&ticks<=105) {
            final int step=ticks-80;
            server(mc,()->{
                require(maid.isSleeping(),"Movement briefly woke server maid");
                var pose=new com.sange.tm_wagon.physics.WagonPose(new net.minecraft.world.phys.Vec3(.5+step*.12,81,.5),180+step*2,.2F,.15F);
                wagon.level().setBlock(BlockPos.containing(pose.position()),WagonContent.FRAME.get().defaultBlockState(),3);wagon.applyPose(pose);
            });
        }
        if(ticks==105&&mc.level.getEntity(maid.getId()) instanceof EntityMaid m
            &&mc.level.getEntity(wagon.getId()) instanceof WagonEntity w) {
            var bound=StrawMatSleep.sleepingPoint(m);
            m.lerpTo(bound.x+8,bound.y+2,bound.z-5,35,10,5);
            require(Math.abs(m.lerpTargetX()-m.getX())<1e-9,"Sleep retained independent network interpolation");
            m.setDeltaMovement(.3,-.2,.4);m.aiStep();
            require(m.position().distanceToSqr(bound)<1e-9&&m.getDeltaMovement().lengthSqr()<1e-9,"Sleeping entity still travelled independently");
            String model=m.getModelId();
            try {
                checkRenderedAttachment(mc,m,w);
                m.setModelId("geckolib:winefox");vertices(mc,m,.37F);vertices(mc,m,.37F);
                checkRenderedAttachment(mc,m,w);
            } finally { m.setModelId(model); }
        }
        if(ticks==115) {
            require(mc.level.getEntity(maid.getId()) instanceof EntityMaid m&&m.isSleeping()&&m.position().distanceToSqr(StrawMatSleep.sleepingPoint(m))<.01,"Client did not follow moving mat while asleep");
            server(mc,()->{
                maid.stopSleeping();
                require(!maid.isSleeping()&&maid.getPose()==Pose.STANDING,"Covered mat wake did not restore standing pose");
                require(maid.level().noCollision(maid,maid.getBoundingBox().deflate(.0001)),"Maid wake position obstructed");
            });
        }
        if(ticks==130) {
            require(mc.level.getEntity(maid.getId()) instanceof EntityMaid m&&!m.isSleeping()&&StrawMatSleep.sleepingPose(m)==null,"Client maid wake/session was not cleared");
            var awake=(EntityMaid)mc.level.getEntity(maid.getId());double target=awake.getX()+.25;
            awake.lerpTo(target,awake.getY(),awake.getZ(),awake.getYRot(),0,3);
            require(Math.abs(awake.lerpTargetX()-target)<1e-9,"Wake did not restore network interpolation");
            awake.lerpTo(awake.getX(),awake.getY(),awake.getZ(),awake.getYRot(),0,0);
            server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();
                wagon.discard();maid.discard();p.teleportTo(5,84,-4);
                for(var at:BlockPos.betweenClosed(-8,81,-8,8,91,8))level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
                var root=new BlockPos(0,81,0);level.setBlock(root,WagonContent.FRAME.get().defaultBlockState(),3);
                frame=(AssemblyFrameBlockEntity)level.getBlockEntity(root);require(frame.initializeFrame()==null,"Block fixture failed");
                for(var part:WagonEntity.defaultParts().entrySet())require(frame.install(part.getKey(),part.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(part.getValue()).get()))==null,"Block module failed");
                var at=frame.cargoPose().point(frame.cargo().centreAt(8).add(-2,1,0));p.teleportTo(at.x,at.y,at.z);
                require(frame.cargo().place(8,new ItemStack(WagonContent.STRAW_MAT.get()),p)==null,"Block mat failed");
                maid=EntityMaid.TYPE.create(level);maid.setPos(-2,81,.5);maid.tame(p);maid.setHomeModeEnable(false);maid.setSchedule(MaidSchedule.DAY);maid.setNoAi(true);level.addFreshEntity(maid);
                require(StrawMatSleep.sleepMob(frame.cargo(),8,maid),"Block maid sleep failed");
            });
        }
        if(ticks>=145&&ticks<=170)require(mc.level.getEntity(maid.getId()) instanceof EntityMaid m&&m.isSleeping(),"Client conversion briefly woke maid");
        if(ticks==145)server(mc,()->{
            require(frame.toggleFrame(null)==null,"Sleeping maid conversion failed");
            wagon=maid.level().getEntitiesOfClass(WagonEntity.class,new net.minecraft.world.phys.AABB(frame.getBlockPos()).inflate(6)).getFirst();
        });
        if(ticks==165) {
            require(mc.level.getEntity(wagon.getId()) instanceof WagonEntity w&&mc.level.getEntity(maid.getId()) instanceof EntityMaid m&&StrawMatSleep.sleepingPose(m).equals(w.pose()),"Conversion session was not rebound to new client wagon");
            server(mc,()->{
                wagon.level().setBlock(new BlockPos(2,81,0),WagonContent.FRAME.get().defaultBlockState(),3);
                wagon.applyPose(new com.sange.tm_wagon.physics.WagonPose(new net.minecraft.world.phys.Vec3(2.5,81,.5),240,.2F,-.15F));
            });
        }
        if(ticks==185) {
            require(mc.level.getEntity(maid.getId()) instanceof EntityMaid m&&m.isSleeping()&&m.position().distanceToSqr(StrawMatSleep.sleepingPoint(m))<.01,"Converted client sleeper remained at old frame");
        }
        if(ticks==200)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            wagon.applyPose(new com.sange.tm_wagon.physics.WagonPose(wagon.position(),240,0,0));
            p.serverLevel().getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(101,p.serverLevel().getServer());
            var at=wagon.pose().point(wagon.cargo().centreAt(9).add(-2,1,0));p.teleportTo(at.x,at.y,at.z);
            String error=wagon.cargo().place(9,new ItemStack(WagonContent.STRAW_MAT.get()),p);require(error==null,"Player mat fixture failed: "+error);
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.onUpdateAbilities();
            require(StrawMatSleep.sleep(wagon.cargo(),wagon.cargo().entry(9),p)==null,"Native client-player sleep failed");
        });
        if(ticks>=210&&ticks<=235) {
            final int step=ticks-209;
            server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();require(p.isSleeping()&&maid.isSleeping(),"Movement woke player/maid");
                var pose=new com.sange.tm_wagon.physics.WagonPose(new net.minecraft.world.phys.Vec3(2.5+step*.15,81,.5),240+step*2,.2F,.15F);
                wagon.level().setBlock(BlockPos.containing(pose.position()),WagonContent.FRAME.get().defaultBlockState(),3);wagon.applyPose(pose);
            });
        }
        if(ticks==240&&mc.level.getEntity(wagon.getId()) instanceof WagonEntity w)checkCameraAttachment(mc,w);
        if(ticks==245) {
            require(mc.player.isSleeping()&&mc.player.getPose()==Pose.SLEEPING&&mc.player.position().distanceToSqr(StrawMatSleep.sleepingPoint(mc.player))<.01,"Local sleeping player did not follow wagon");
            server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.stopSleepInBed(true,true);
                require(p.serverLevel().noCollision(p,p.getBoundingBox().deflate(.0001)),"Moving player wake was obstructed");
            });
        }
        if(ticks==260) {
            require(!mc.player.isSleeping()&&StrawMatSleep.sleepingPoint(mc.player)==null,"Local moving-player wake retained sleeping camera");
            checkAwakeCamera(mc);
            com.mojang.logging.LogUtils.getLogger().info("MAID_CLIENT_PASS: attached interpolation and first/third-person camera, safe awake camera; rendered sleeper vertices stay rigidly attached through interpolation, turns and slopes; moving sleep, safe wake and conversion");mc.stop();
        }
    }
    private static void checkCameraAttachment(Minecraft mc,WagonEntity w) {
        var p=mc.player;var saved=w.pose();double xo=p.xo,yo=p.yo,zo=p.zo;
        var camera=new net.minecraft.client.Camera();
        for(int i=0;i<32;i++) { camera.setup(mc.level,p,false,false,1);camera.tick(); }
        try {
            // Cross cardinal boundaries and test combined slopes and an overturned vehicle.
            float[][] angles={{44,.2F,.1F},{46,.2F,.1F},{135,-.4F,.3F},{226,1.3F,-.6F},{359,.3F,3.0F},{361,.3F,3.0F}};
            for(float[] a:angles) {
                w.applyPose(new com.sange.tm_wagon.physics.WagonPose(saved.position().add(.34,.65,-.26),a[0],a[1],a[2]));
                StrawMatSleep.follow(p);
                p.xo-=3;p.yo+=2;p.zo+=5;
                for(float partial:new float[]{0,.25F,.5F,.75F,1}) {
                    var pose=StrawMatSleep.sleepingPose(p,partial);
                    var anchor=StrawMatSleep.sleepingPoint(p,partial);
                    camera.setup(mc.level,p,false,false,partial);
                    var expected=anchor.add(pose.vector(new net.minecraft.world.phys.Vec3(0,p.getEyeHeight()+.3,0)));
                    require(camera.getPosition().distanceToSqr(expected)<1e-9,"Sleeping camera drifted from mat at "+a[0]+" / "+partial);
                    var forward=new org.joml.Vector3f(0,0,-1).rotate(camera.rotation());
                    var up=new org.joml.Vector3f(0,1,0).rotate(camera.rotation());
                    require(new net.minecraft.world.phys.Vec3(forward).distanceToSqr(pose.vector(new net.minecraft.world.phys.Vec3(0,0,1)))<1e-9,"Camera lost continuous sleep yaw/pitch");
                    require(new net.minecraft.world.phys.Vec3(up).distanceToSqr(pose.vector(new net.minecraft.world.phys.Vec3(0,1,0)))<1e-9,"Camera lost wagon roll");
                    for(boolean reverse:new boolean[]{false,true}) {
                        camera.setup(mc.level,p,true,reverse,partial);var before=camera.getPosition();
                        p.xo+=6;p.yo-=4;p.zo+=2;
                        camera.setup(mc.level,p,true,reverse,partial);
                        require(camera.getPosition().distanceToSqr(before)<1e-9,"Detached camera used independent sleeper interpolation");
                    }
                }
            }
        } finally { w.applyPose(saved);StrawMatSleep.follow(p);p.xo=xo;p.yo=yo;p.zo=zo; }
    }
    private static void checkAwakeCamera(Minecraft mc) {
        var p=mc.player;var camera=new net.minecraft.client.Camera();
        float yaw=p.getYRot(),pitch=p.getXRot(),oldYaw=p.yHeadRot;
        try {
            p.setYRot(73);p.setYHeadRot(73);p.setXRot(19);
            for(int i=0;i<32;i++) { camera.setup(mc.level,p,false,false,1);camera.tick(); }
            camera.setup(mc.level,p,false,false,1);
            require(camera.getPosition().distanceToSqr(p.position().add(0,p.getEyeHeight(),0))<1e-9,"Awake camera retained mat position");
            var expected=new org.joml.Quaternionf().rotationYXZ((float)Math.PI-73*net.minecraft.util.Mth.DEG_TO_RAD,-19*net.minecraft.util.Mth.DEG_TO_RAD,0);
            require(Math.abs(camera.rotation().dot(expected))>.99999F,"Awake camera retained sleeping orientation");
        } finally { p.setYRot(yaw);p.setYHeadRot(oldYaw);p.setXRot(pitch); }
    }
    /** Compare actual rendered vertices, not just entity positions after tick corrections. */
    private static void checkRenderedAttachment(Minecraft mc,EntityMaid m,WagonEntity w) {
        double ox=m.xOld,oy=m.yOld,oz=m.zOld,xo=m.xo,yo=m.yo,zo=m.zo;
        var saved=w.pose();
        try {
            var before=vertices(mc,m,.37F);
            require(before.size()>100,"No maid geometry rendered in attachment regression");
            m.xOld+=.72;m.yOld-=.31;m.zOld+=.43;m.xo-=.84;m.yo+=.61;m.zo-=.52;
            sameVertices(before,vertices(mc,m,.37F),"Maid model followed independent position interpolation");
            var local=localVertices(vertices(mc,m,1),w.pose());
            for(float yaw:new float[]{44,91,179,226,315}) {
                w.applyPose(new com.sange.tm_wagon.physics.WagonPose(saved.position().add(.27,.43,-.31),yaw,.3F,-.2F));
                StrawMatSleep.follow(m);
                sameVertices(local,localVertices(vertices(mc,m,1),w.pose()),"Sleeping model left mat when turning/tilting at "+yaw);
            }
        } finally {
            w.applyPose(saved);StrawMatSleep.follow(m);m.xOld=ox;m.yOld=oy;m.zOld=oz;m.xo=xo;m.yo=yo;m.zo=zo;
        }
    }
    private static java.util.List<net.minecraft.world.phys.Vec3> localVertices(java.util.List<net.minecraft.world.phys.Vec3> points,com.sange.tm_wagon.physics.WagonPose pose) {
        return points.stream().map(pose::local).toList();
    }
    private static void sameVertices(java.util.List<net.minecraft.world.phys.Vec3> a,java.util.List<net.minecraft.world.phys.Vec3> b,String message) {
        require(a.size()==b.size(),message+" (vertex count)");
        for(int i=0;i<a.size();i++)require(a.get(i).distanceToSqr(b.get(i))<.000001,message+" at vertex "+i+": "+a.get(i)+" / "+b.get(i));
    }
    private static java.util.List<net.minecraft.world.phys.Vec3> vertices(Minecraft mc,EntityMaid maid,float partial) {
        var result=new java.util.ArrayList<net.minecraft.world.phys.Vec3>();
        var consumer=new com.mojang.blaze3d.vertex.VertexConsumer() {
            public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x,float y,float z) { result.add(new net.minecraft.world.phys.Vec3(x,y,z));return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r,int g,int b,int a) { return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u,float v) { return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u,int v) { return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u,int v) { return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x,float y,float z) { return this; }
        };
        var position=StrawMatSleep.renderPosition(maid,partial);var dispatcher=mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try { dispatcher.render(maid,position.x,position.y,position.z,maid.getYRot(),partial,new com.mojang.blaze3d.vertex.PoseStack(),type->consumer,15728880); }
        finally { dispatcher.setRenderShadow(true); }
        return result;
    }
    private static void server(Minecraft mc,Runnable action) {
        mc.getSingleplayerServer().execute(()->{try { action.run(); }catch(Throwable error) { failure=error.toString(); }});
    }
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
    }
}
