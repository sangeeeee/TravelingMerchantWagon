package com.sange.tm_wagon.compat;

import com.sange.tm_wagon.cargo.CargoEntry;
import com.sange.tm_wagon.cargo.CargoHold;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/** Keeps optional API classes out of the normal cargo loading path. */
public final class CarryOnCompat {
    public static boolean pickup(CargoHold hold,CargoEntry entry,Player player) {
        return entry!=null&&ModList.get().isLoaded("carryon")
            &&com.sange.tm_wagon.cargo.CarryOnCargo.pickup(hold,entry,player);
    }
    private CarryOnCompat() {}
}
