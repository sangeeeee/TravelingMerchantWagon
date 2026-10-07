package com.sange.tm_wagon.platform;
public final class FabricPortServerSmoke implements net.fabricmc.api.ModInitializer {
    public void onInitialize(){
        if(!Boolean.getBoolean("tm_wagon.portServerSmoke"))return;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server->{
            var level=server.overworld();
            var pos=new net.minecraft.core.BlockPos(0,-59,0);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk(x,z);
            level.setBlock(pos,com.sange.tm_wagon.assembly.WagonContent.FRAME.get().defaultBlockState(),3);
            var frame=(com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity)level.getBlockEntity(pos);
            var error=frame.initializeFrame();if(error!=null)throw new IllegalStateException("Server assembly frame placement: "+error);
            if(!java.nio.file.Files.isRegularFile(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(com.sange.tm_wagon.ServerConfig.FILE_NAME)))throw new IllegalStateException("Startup server config missing");
            com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_1_2_SERVER_PASS: dedicated server started, assembly frame registered, unified configuration loaded");
            server.halt(false);
        });
    }
}
