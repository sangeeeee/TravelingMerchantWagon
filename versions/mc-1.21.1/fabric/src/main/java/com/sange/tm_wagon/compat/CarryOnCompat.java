package com.sange.tm_wagon.compat;

import com.sange.tm_wagon.cargo.CargoEntry;
import com.sange.tm_wagon.cargo.CargoHold;
import net.minecraft.world.entity.player.Player;
import net.fabricmc.loader.api.FabricLoader;

/** Keeps optional API classes out of the normal cargo loading path. */
public final class CarryOnCompat {
    public static boolean pickup(CargoHold hold,CargoEntry entry,Player player) {
        return entry!=null&&FabricLoader.getInstance().isModLoaded("carryon")
            &&com.sange.tm_wagon.cargo.CarryOnCargo.pickup(hold,entry,player);
    }
    public static boolean place(CargoHold hold,int slot,Player player) {
        return FabricLoader.getInstance().isModLoaded("carryon")
            &&com.sange.tm_wagon.cargo.CarryOnCargo.place(hold,slot,player);
    }
    private CarryOnCompat() {}
}
