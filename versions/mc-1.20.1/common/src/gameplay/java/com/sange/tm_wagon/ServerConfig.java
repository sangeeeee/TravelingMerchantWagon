package com.sange.tm_wagon;

import net.minecraftforge.common.ForgeConfigSpec;

/** One server config owns cargo rules and all driving parameters. */
public final class ServerConfig {
    public static final String FILE_NAME="tm_wagon-server.toml";
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue GIVE_MANUAL_ON_FIRST_JOIN;
    static {
        var builder=new ForgeConfigSpec.Builder();
        CargoConfig.define(builder);
        DrivingConfig.define(builder);
        builder.comment("Coachman's Handbook settings.").push("handbook");
        GIVE_MANUAL_ON_FIRST_JOIN=builder.comment("Give each player a handbook when they log in without a recorded previous gift.",
            "Requires Patchouli. Players in existing worlds also receive one after the mod is added.",
            "Reconnects and respawns never grant another copy after receipt.",
            "Disabled logins do not count as receipt; enabling this option later permits the gift.")
            .define("giveOnFirstJoin",true);
        builder.pop();
        SPEC=builder.build();
    }
    private ServerConfig() {}
}
