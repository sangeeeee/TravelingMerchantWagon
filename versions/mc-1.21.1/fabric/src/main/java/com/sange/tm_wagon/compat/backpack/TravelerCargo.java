package com.sange.tm_wagon.compat.backpack;

import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.network.WagonNetwork;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.menu.BackpackBaseMenu;
import com.tiviacz.travelersbackpack.inventory.menu.BackpackSettingsMenu;
import com.tiviacz.travelersbackpack.init.ModDataComponents;
import com.tiviacz.travelersbackpack.item.TravelersBackpackItem;
import java.util.UUID;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import com.sange.tm_wagon.network.WagonPackets;
import com.sange.tm_wagon.platform.FabricRegistry;

/** Native slots, upgrades and screens, backed by the actual cargo ItemStack. */
public final class TravelerCargo {
    private static final int CARGO_SCREEN=3;
    private static final FabricRegistry<MenuType<?>> MENUS=FabricRegistry.create(Registries.MENU,"tm_wagon");
    public static final java.util.function.Supplier<MenuType<Menu>> MENU=
        MENUS.register("traveler_cargo",()->new ExtendedScreenHandlerType<>(Menu::new,Opening.CODEC));
    public static final java.util.function.Supplier<MenuType<Settings>> SETTINGS=
        MENUS.register("traveler_cargo_settings",()->new ExtendedScreenHandlerType<>(Settings::new,Opening.CODEC));
    private static final java.util.Map<CargoEntry,java.lang.ref.WeakReference<Wrapper>> WRAPPERS=new java.util.WeakHashMap<>();
    public static void register(){}
    public static boolean sleepingBag(ItemStack stack) {
        return stack.getItem() instanceof com.tiviacz.travelersbackpack.item.SleepingBagItem;
    }
    public static boolean matches(ItemStack stack){return stack.getItem() instanceof TravelersBackpackItem;}
    public static void visual(ItemStack stack) {
        stack.remove(ModDataComponents.BACKPACK_CONTAINER);stack.remove(ModDataComponents.UPGRADES);
        stack.remove(ModDataComponents.TOOLS_CONTAINER);stack.remove(ModDataComponents.SLOTS);
    }
    public static void snapshot(BlockEntity be,ItemStack stack) {
        if(!(be instanceof com.tiviacz.travelersbackpack.blockentity.BackpackBlockEntity backpack))throw new IllegalArgumentException("Wrong traveler BE");
        backpack.setBackpack(stack,be.getLevel().registryAccess());
    }
    public static ItemStack fromBlockEntity(BlockEntity be,ItemStack fallback) {
        return ((com.tiviacz.travelersbackpack.blockentity.BackpackBlockEntity)be).toItemStack(fallback);
    }
    public static void open(CargoHold hold,CargoEntry entry,Player player,boolean settings) {
        if(!(player instanceof ServerPlayer)||!hold.valid(entry,player))return;
        var reference=WRAPPERS.get(entry);Wrapper wrapper=reference==null?null:reference.get();
        if(wrapper==null) {wrapper=new Wrapper(entry.item,entry.id,hold.owner().cargoLevel(),hold);WRAPPERS.put(entry,new java.lang.ref.WeakReference<>(wrapper));}
        wrapper.bind(hold);
        if(com.tiviacz.travelersbackpack.config.TravelersBackpackConfig.getConfig().backpackSettings.preventMultiplePlayersAccess
            &&wrapper.playersUsing.stream().anyMatch(p->p!=player&&!p.level().isClientSide))return;
        var shared=wrapper;
        BackpackSessions.track(player,hold,entry);
        player.openMenu(new ExtendedScreenHandlerFactory<Opening>() {
            @Override public net.minecraft.network.chat.Component getDisplayName(){return entry.item.getHoverName();}
            @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,Inventory inventory,Player p){return settings?new Settings(id,inventory,shared):new Menu(id,inventory,shared);}
            @Override public Opening getScreenOpeningData(ServerPlayer player){return new Opening(entry.id,entry.item.copy());}
        });
        hold.changed(false);
    }
    public static boolean settings(ServerPlayer player,int playerId,boolean open) {
        var id=BackpackSessions.id(player.containerMenu);
        if(!(player.containerMenu instanceof Menu||player.containerMenu instanceof Settings))return false;
        var view=BackpackSessions.session(player,id);
        if(playerId==player.getId()&&view!=null)open(view.hold(),view.entry(),player,open);
        return true;
    }
    public static void receive(Player player,UUID id,DataComponentPatch patch) {
        BackpackWrapper wrapper=player.containerMenu instanceof Menu menu?menu.getWrapper():player.containerMenu instanceof Settings menu?menu.getWrapper():null;
        if(wrapper instanceof Wrapper cargo&&cargo.id.equals(id)) {
            var stack=wrapper.getBackpackStack().copy();stack.applyComponents(patch);wrapper.setBackpackStack(stack);
        }
    }
    public record Opening(UUID id,ItemStack stack) {
        static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=net.minecraft.network.codec.StreamCodec.of(
            (buf,data)->{buf.writeUUID(data.id);ItemStack.STREAM_CODEC.encode(buf,data.stack);},
            buf->new Opening(buf.readUUID(),ItemStack.STREAM_CODEC.decode(buf)));
    }
    private static Wrapper read(Opening data,Inventory inventory) {return new Wrapper(data.stack,data.id,inventory.player.level(),null);}

    public static final class Wrapper extends BackpackWrapper {
        final UUID id;
        private java.lang.ref.WeakReference<CargoHold> cargo;
        Wrapper(ItemStack stack,UUID id,net.minecraft.world.level.Level world,CargoHold hold) {
            super(stack,CARGO_SCREEN,null,world);this.id=id;
            if(hold!=null)bind(hold);
        }
        void bind(CargoHold hold) {
            setLevel(hold.owner().cargoLevel());cargo=new java.lang.ref.WeakReference<>(hold);
            saveHandler=()->{var current=cargo.get();if(current!=null)current.changed(false);};
        }
        @Override public void sendDataToClients(DataComponentType... types) {
            if(level==null||level.isClientSide||id==null)return;
            var patch=DataComponentPatch.builder();
            for(var type:types)add(patch,type);
            var update=new WagonNetwork.BackpackUpdate(id,patch.build());
            for(var player:java.util.List.copyOf(playersUsing))if(player instanceof ServerPlayer server)WagonPackets.sendToPlayer(server,update);
            for(var type:types)if(type==ModDataComponents.RENDER_INFO||type==ModDataComponents.SLEEPING_BAG_COLOR||type==net.minecraft.core.component.DataComponents.DYED_COLOR) {
                var current=cargo==null?null:cargo.get();if(current!=null)current.changed(true);break;
            }
        }
        private <T> void add(DataComponentPatch.Builder builder,DataComponentType<T> type) {
            var value=getBackpackStack().get(type);if(value==null)builder.remove(type);else builder.set(type,value);
        }
    }
    public static final class Menu extends BackpackBaseMenu implements BackpackSessions.Menu {
        public Menu(int id,Inventory inventory,Opening data){this(id,inventory,read(data,inventory));}
        Menu(int id,Inventory inventory,Wrapper wrapper){super(MENU.get(),id,inventory,wrapper);wrapper.addUser(inventory.player);}
        @Override public UUID wagonBackpackId(){return ((Wrapper)getWrapper()).id;}
        @Override public boolean stillValid(Player player){return player.level().isClientSide||BackpackSessions.valid(player,wagonBackpackId());}
        @Override public void removed(Player player){super.removed(player);getWrapper().playersUsing.remove(player);}
    }
    public static final class Settings extends BackpackSettingsMenu implements BackpackSessions.Menu {
        public Settings(int id,Inventory inventory,Opening data){this(id,inventory,read(data,inventory));}
        Settings(int id,Inventory inventory,Wrapper wrapper){super(SETTINGS.get(),id,inventory,wrapper);wrapper.addUser(inventory.player);}
        @Override public UUID wagonBackpackId(){return ((Wrapper)getWrapper()).id;}
        @Override public boolean stillValid(Player player){return player.level().isClientSide||BackpackSessions.valid(player,wagonBackpackId());}
        @Override public void removed(Player player){super.removed(player);getWrapper().playersUsing.remove(player);}
    }
    private TravelerCargo(){}
}
