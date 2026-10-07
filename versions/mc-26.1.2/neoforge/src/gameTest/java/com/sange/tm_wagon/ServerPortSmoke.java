package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.material.WoodMaterial;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Isolated dedicated-server startup check; excluded from published jars. */
@EventBusSubscriber(modid="tm_wagon")
public final class ServerPortSmoke {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!Boolean.getBoolean("tm_wagon.serverSmoke"))return;
        var server=event.getServer();
        try {
            if(WoodMaterial.values().length!=11)throw new IllegalStateException("Wrong 26.1.2 wood inventory");
            for(var wood:WoodMaterial.values())if(wood.planks()==net.minecraft.world.item.Items.AIR)throw new IllegalStateException("Missing wood: "+wood);
            if(!BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("tm_wagon","wagon_straw_mat")))throw new IllegalStateException("Missing wagon mat");
            var wagon=WagonContent.WAGON.get().create(server.overworld(),EntitySpawnReason.TRIGGERED);
            if(wagon==null||wagon.cargo().capacity()!=10)throw new IllegalStateException("Wagon entity initialization");
            if(!java.nio.file.Files.isRegularFile(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(ServerConfig.FILE_NAME)))throw new IllegalStateException("Missing startup configuration");
            com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_1_2_SERVER_PASS: dedicated startup, woods, mat, wagon and startup configuration");
        } catch(Throwable failure) { com.mojang.logging.LogUtils.getLogger().error("TM_WAGON_26_1_2_SERVER_FAIL",failure); }
        finally { server.halt(false); }
    }
}
