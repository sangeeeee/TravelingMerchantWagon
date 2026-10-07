package com.sange.tm_wagon;

import net.neoforged.neoforge.common.ModConfigSpec;

/** One server config owns cargo rules and all driving parameters. */
public final class ServerConfig {
    public static final String FILE_NAME="tm_wagon-server.toml";
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue GIVE_MANUAL_ON_FIRST_JOIN;
    static {
        var builder=new ModConfigSpec.Builder();
        CargoConfig.define(builder);
        DrivingConfig.define(builder);
        builder.comment("Coachman's Manual. Requires Patchouli to be installed.").push("handbook");
        GIVE_MANUAL_ON_FIRST_JOIN=builder.comment("Give the manual once to each player, including players in existing worlds.").define("giveManualOnFirstJoin",true);
        builder.pop();
        SPEC=builder.build();
    }
    private ServerConfig() {}
}
