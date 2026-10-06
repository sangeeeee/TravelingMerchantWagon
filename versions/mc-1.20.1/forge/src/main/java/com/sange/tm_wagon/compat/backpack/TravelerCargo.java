package com.sange.tm_wagon.compat.backpack;

import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.network.WagonNetwork;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.menu.BackpackBaseMenu;
import com.tiviacz.travelersbackpack.inventory.menu.BackpackSettingsMenu;
import com.tiviacz.travelersbackpack.items.TravelersBackpackItem;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;

/** Native slots, upgrades and screens, backed by the actual cargo ItemStack. */
public final class TravelerCargo {
    private static final int CARGO_SCREEN=3;
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"tm_wagon");
    public static final net.minecraftforge.registries.RegistryObject<MenuType<Menu>> MENU=
        MENUS.register("traveler_cargo",()->IForgeMenuType.create(Menu::new));
    public static final net.minecraftforge.registries.RegistryObject<MenuType<Settings>> SETTINGS=
        MENUS.register("traveler_cargo_settings",()->IForgeMenuType.create(Settings::new));
    private static final java.util.Map<CargoEntry,java.lang.ref.WeakReference<Wrapper>> WRAPPERS=new java.util.WeakHashMap<>();
    public static void register(IEventBus bus){MENUS.register(bus);}
    public static boolean sleepingBag(ItemStack stack) {
        return stack.getItem() instanceof com.tiviacz.travelersbackpack.items.SleepingBagItem;
    }
    public static boolean matches(ItemStack stack){return stack.getItem() instanceof TravelersBackpackItem;}
    public static void visual(ItemStack stack) {
        for(String key:new String[]{"Inventory","Upgrades","ToolsInventory","UnsortableSlots","MemorySlots"})stack.removeTagKey(key);
    }
    public static void snapshot(BlockEntity be,ItemStack stack) {
        if(!(be instanceof com.tiviacz.travelersbackpack.blockentity.BackpackBlockEntity backpack))throw new IllegalArgumentException("Wrong traveler BE");
        backpack.setBackpack(stack);
    }
    public static ItemStack fromBlockEntity(BlockEntity be,ItemStack fallback) {
        return ((com.tiviacz.travelersbackpack.blockentity.BackpackBlockEntity)be).toItemStack(fallback);
    }
    public static void open(CargoHold hold,CargoEntry entry,Player player,boolean settings) {
        if(!(player instanceof ServerPlayer)||!hold.valid(entry,player))return;
        var reference=WRAPPERS.get(entry);Wrapper wrapper=reference==null?null:reference.get();
        if(wrapper==null) {wrapper=new Wrapper(entry.item,entry.id,hold.owner().cargoLevel(),hold);WRAPPERS.put(entry,new java.lang.ref.WeakReference<>(wrapper));}
        wrapper.bind(hold);
        if(com.tiviacz.travelersbackpack.config.TravelersBackpackConfig.SERVER.backpackSettings.preventMultiplePlayersAccess.get()
            &&wrapper.playersUsing.stream().anyMatch(p->p!=player&&!p.level().isClientSide))return;
        var shared=wrapper;
        BackpackSessions.track(player,hold,entry);
        net.minecraftforge.network.NetworkHooks.openScreen((ServerPlayer)player,new net.minecraft.world.MenuProvider() {
            @Override public net.minecraft.network.chat.Component getDisplayName(){return entry.item.getHoverName();}
            @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,Inventory inventory,Player p){return settings?new Settings(id,inventory,shared):new Menu(id,inventory,shared);}
        },buf->{buf.writeUUID(entry.id);buf.writeItem(entry.item);});
        hold.changed(false);
    }
    public static boolean settings(ServerPlayer player,int playerId,boolean open) {
        var id=BackpackSessions.id(player.containerMenu);
        if(!(player.containerMenu instanceof Menu||player.containerMenu instanceof Settings))return false;
        var view=BackpackSessions.session(player,id);
        if(playerId==player.getId()&&view!=null)open(view.hold(),view.entry(),player,open);
        return true;
    }
    public static void receive(Player player,UUID id,net.minecraft.nbt.CompoundTag patch) {
        BackpackWrapper wrapper=player.containerMenu instanceof Menu menu?menu.getWrapper():player.containerMenu instanceof Settings menu?menu.getWrapper():null;
        if(wrapper instanceof Wrapper cargo&&cargo.id.equals(id)) {
            var stack=wrapper.getBackpackStack().copy();stack.setTag(patch.copy());wrapper.setBackpackStack(stack);
        }
    }
    private static Wrapper read(FriendlyByteBuf buf,Inventory inventory) {
        var id=buf.readUUID();var stack=buf.readItem();
        return new Wrapper(stack,id,inventory.player.level(),null);
    }
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
        @Override public void sendDataToClients(String... keys) {
            if(level==null||level.isClientSide||id==null)return;
            var tag=getBackpackStack().getTag();
            var update=new WagonNetwork.BackpackUpdate(id,tag==null?new net.minecraft.nbt.CompoundTag():tag.copy());
            for(var player:java.util.List.copyOf(playersUsing))if(player instanceof ServerPlayer server)WagonNetwork.sendToPlayer(server,update);
            for(String key:keys)if(key.equals("RenderInfo")||key.equals("SleepingBagColor")||key.equals("Color")) {
                var current=cargo==null?null:cargo.get();if(current!=null)current.changed(true);break;
            }
        }
    }

    public static final class Menu extends BackpackBaseMenu implements BackpackSessions.Menu {
        public Menu(int id,Inventory inventory,FriendlyByteBuf buf){this(id,inventory,read(buf,inventory));}
        Menu(int id,Inventory inventory,Wrapper wrapper){super(MENU.get(),id,inventory,wrapper);wrapper.addUser(inventory.player);}
        @Override public UUID wagonBackpackId(){return ((Wrapper)getWrapper()).id;}
        @Override public boolean stillValid(Player player){return player.level().isClientSide||BackpackSessions.valid(player,wagonBackpackId());}
        @Override public void removed(Player player){super.removed(player);getWrapper().playersUsing.remove(player);}
    }
    public static final class Settings extends BackpackSettingsMenu implements BackpackSessions.Menu {
        public Settings(int id,Inventory inventory,FriendlyByteBuf buf){this(id,inventory,read(buf,inventory));}
        Settings(int id,Inventory inventory,Wrapper wrapper){super(SETTINGS.get(),id,inventory,wrapper);wrapper.addUser(inventory.player);}
        @Override public UUID wagonBackpackId(){return ((Wrapper)getWrapper()).id;}
        @Override public boolean stillValid(Player player){return player.level().isClientSide||BackpackSessions.valid(player,wagonBackpackId());}
        @Override public void removed(Player player){super.removed(player);getWrapper().playersUsing.remove(player);}
    }
    private TravelerCargo(){}
}
