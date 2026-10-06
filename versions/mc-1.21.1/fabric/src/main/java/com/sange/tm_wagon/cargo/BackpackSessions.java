package com.sange.tm_wagon.cargo;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Only open viewers are tracked. No cargo scan or inventory serialization each tick. */
public final class BackpackSessions {
    public interface Context { UUID wagonBackpackId(); }
    public interface Menu extends Context {}
    public record Session(CargoHold hold,CargoEntry entry) {}
    private static final Map<Player,Session> VIEWERS=new IdentityHashMap<>();
    private static final Map<Player,Map<UUID,ItemStack>> CLIENT=new IdentityHashMap<>();
    private static final Map<Player,Long> CLIENT_EXPIRY=new IdentityHashMap<>();
    public static void track(Player player,CargoHold hold,CargoEntry entry) { VIEWERS.put(player,new Session(hold,entry)); }
    public static Session session(Player player,UUID id) {
        var session=VIEWERS.get(player);
        return session!=null&&session.entry.id.equals(id)&&session.hold.valid(session.entry,player)?session:null;
    }
    public static ItemStack stack(Player player,UUID id) {
        if(id==null)return ItemStack.EMPTY;
        if(player.level().isClientSide)return CLIENT.getOrDefault(player,Map.of()).getOrDefault(id,ItemStack.EMPTY);
        var session=session(player,id);return session==null?ItemStack.EMPTY:session.entry.item;
    }
    public static void receive(Player player,UUID id,ItemStack stack) {
        var values=CLIENT.computeIfAbsent(player,p->new java.util.HashMap<>());
        var old=values.get(id);
        if(old!=null&&old.is(stack.getItem())) {
            for(var type:java.util.List.copyOf(old.getComponents().keySet()))if(!stack.has(type))old.remove(type);
            old.applyComponents(stack.getComponentsPatch());
        }
        else values.put(id,stack);
        CLIENT_EXPIRY.put(player,player.level().getGameTime()+40);
    }
    public static boolean valid(Player player,UUID id) {
        return player.level().isClientSide?!stack(player,id).isEmpty():session(player,id)!=null;
    }
    public static void changed(Player player,UUID id,boolean visible) {
        var session=session(player,id);if(session!=null)session.hold.changed(visible);
    }
    public static UUID id(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if(menu instanceof Context context)return context.wagonBackpackId();
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sophisticatedbackpacks"))
            return com.sange.tm_wagon.compat.backpack.SophisticatedCargo.menuId(menu);
        return null;
    }
    public static void close(CargoHold hold,CargoEntry entry) {
        if(hold.owner().cargoLevel()==null||hold.owner().cargoLevel().isClientSide)return;
        for(var value:java.util.List.copyOf(VIEWERS.entrySet())) {
            var view=value.getValue();if(view.hold!=hold||entry!=null&&view.entry!=entry)continue;
            // removed() flushes native upgrade/crafting slots before the cargo source is revoked.
            if(view.entry.id.equals(id(value.getKey().containerMenu)))com.sange.tm_wagon.platform.FabricMenus.close(value.getKey());
            VIEWERS.remove(value.getKey());hold.changed(false);
        }
    }
    public static void tick(Player player) {

        if(player.level().isClientSide) {
            if(CLIENT.containsKey(player)) {
                if(id(player.containerMenu)!=null)CLIENT_EXPIRY.put(player,player.level().getGameTime()+40);
                else if(player.level().getGameTime()>CLIENT_EXPIRY.getOrDefault(player,0L)) {
                    CLIENT.remove(player);CLIENT_EXPIRY.remove(player);
                }
            }
            return;
        }
        var view=VIEWERS.get(player);
        if(view!=null&&(!view.entry.id.equals(id(player.containerMenu))||!view.hold.valid(view.entry,player))) {
            if(view.entry.id.equals(id(player.containerMenu)))com.sange.tm_wagon.platform.FabricMenus.close(player);
            VIEWERS.remove(player);view.hold.changed(false);
        }
    }
    public static void logout(Player player) {
if(player.level().isClientSide)return;
        var view=VIEWERS.get(player);if(view!=null)close(view.hold,view.entry);
    }
    public static void unload(net.minecraft.world.level.Level level) {
        if(!level.isClientSide)return;
        CLIENT.keySet().removeIf(player->player.level()==level);
        CLIENT_EXPIRY.keySet().removeIf(player->player.level()==level);
    }
    private BackpackSessions() {}
}
