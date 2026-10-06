package com.sange.tm_wagon;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Path;
import java.util.function.Function;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.config.*;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge still synchronizes this SERVER config; only its editable location is global. */
final class StartupServerConfig extends ModConfig {
    private static final ConfigFileTypeHandler HANDLER=new ConfigFileTypeHandler() {
        @Override public Function<ModConfig,CommentedFileConfig> reader(Path ignored) { return super.reader(FMLPaths.CONFIGDIR.get()); }
        @Override public void unload(Path ignored,ModConfig config) { super.unload(FMLPaths.CONFIGDIR.get(),config); }
    };
    StartupServerConfig(ModContainer container) { super(Type.SERVER,ServerConfig.SPEC,container,ServerConfig.FILE_NAME); }
    @Override public ConfigFileTypeHandler getHandler() { return HANDLER; }
}
