package com.sange.tm_wagon.assembly;

import io.netty.channel.embedded.EmbeddedChannel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Configure GameTest's local connection before optional mods send their login sync. */
@EventBusSubscriber(modid="tm_wagon")
public final class OptionalModTestNetwork {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void beforeSync(OnDatapackSyncEvent event) {
        var player=event.getPlayer();
        if(player!=null&&player.connection.getConnection().channel() instanceof EmbeddedChannel)
            NetworkRegistry.configureMockConnection(player.connection.getConnection());
    }
}
