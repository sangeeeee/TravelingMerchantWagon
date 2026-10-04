package com.sange.tm_wagon;

import net.neoforged.neoforge.common.ModConfigSpec;

/** One server config owns cargo rules and all driving parameters. */
public final class ServerConfig {
    public static final String FILE_NAME="tm_wagon-server.toml";
    public static final ModConfigSpec SPEC;
    static {
        var builder=new ModConfigSpec.Builder();
        CargoConfig.define(builder);
        DrivingConfig.define(builder);
        SPEC=builder.build();
    }
    private ServerConfig() {}
}
