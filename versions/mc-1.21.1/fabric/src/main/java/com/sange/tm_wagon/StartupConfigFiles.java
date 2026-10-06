package com.sange.tm_wagon;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;


/** Prepare the single editable file at startup; NeoForge still owns loading and server synchronization. */
final class StartupConfigFiles {
    private static final String LEGACY="tm_wagon-driving-server.toml";
    static void ensure() {
        ensure(FabricLoader.getInstance().getConfigDir(),FabricLoader.getInstance().getGameDir().resolve("defaultconfigs"));
    }
    static void ensure(Path directory,Path templates) {
        Path target=directory.resolve(ServerConfig.FILE_NAME),legacy=directory.resolve(LEGACY);
        try {
            Files.createDirectories(directory);
            boolean exists=Files.exists(target);
            Path source=exists?target:templates.resolve(ServerConfig.FILE_NAME);
            var config=Files.exists(source)?read(source):CommentedConfig.of(LinkedHashMap::new,TomlFormat.instance());
            Path oldSource=Files.exists(legacy)?legacy:(!exists?templates.resolve(LEGACY):legacy);
            boolean merged=false;
            if(Files.exists(oldSource)) {
                var old=read(oldSource);
                for(String section:List.of("speed","boost","acceleration")) {
                    Object value=old.get(section);
                    if(value instanceof UnmodifiableConfig table)merged|=mergeMissing(config,table,section);
                }
            }
            // Existing unified values win over legacy values; add/correct only as the loader would.
            if(!exists||merged||!ServerConfig.SPEC.isCorrect(config)) {
                ServerConfig.SPEC.correct(config);
                new TomlWriter().write(config,target,WritingMode.REPLACE_ATOMIC);
                LogUtils.getLogger().info("Prepared startup configuration {}",target);
            }
            if(Files.exists(legacy)) {
                // Keep a recoverable copy, but no second active TOML file.
                Files.move(legacy,legacy.resolveSibling(LEGACY+".migrated-"+System.currentTimeMillis()+".bak"));
            }
        } catch(IOException error) {
            throw new UncheckedIOException("Cannot prepare startup configuration "+target,error);
        }
    }
    private static CommentedConfig read(Path path) throws IOException {
        try(var reader=Files.newBufferedReader(path)) { return new TomlParser().parse(reader); }
    }
    private static boolean mergeMissing(CommentedConfig target,UnmodifiableConfig source,String prefix) {
        boolean changed=false;
        for(var entry:source.entrySet()) {
            String path=prefix+"."+entry.getKey();Object value=entry.getValue();
            if(value instanceof UnmodifiableConfig table)changed|=mergeMissing(target,table,path);
            else if(!target.contains(path)) { target.set(path,value);changed=true; }
        }
        return changed;
    }
    private StartupConfigFiles() {}
}
