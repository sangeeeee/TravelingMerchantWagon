package com.sange.tm_wagon;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative placement filter; existing cargo is never filtered on load or removal. */
public final class CargoConfig {
    public enum ListMode { BLACKLIST, WHITELIST }

    public static ModConfigSpec.EnumValue<ListMode> LIST_MODE;
    public static ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST, WHITELIST;
    private static final List<String> DEFAULT_BLACKLIST=List.of("tm_wagon:*");
    private static volatile Policy policy=Policy.from(ListMode.BLACKLIST,DEFAULT_BLACKLIST,List.of());

    static void define(ModConfigSpec.Builder builder) {
        builder.comment("Cargo placement rules. Existing cargo can always be removed or transferred.").push("cargo");
        LIST_MODE=builder.comment("BLACKLIST rejects matching blocks; WHITELIST only accepts matching blocks.",
            "Only the selected list is used. Multi-block structures and the disallowed cargo item tag remain forbidden.")
            .defineEnum("listMode",ListMode.BLACKLIST);
        BLACKLIST=builder.comment("Blocks rejected in BLACKLIST mode.",
            "Entries accept block IDs (minecraft:stone), namespace wildcards (tm_wagon:*), or block tags (#minecraft:logs).",
            "The default excludes all blocks from this mod, including future wagon parts.")
            .defineListAllowEmpty("blacklist",DEFAULT_BLACKLIST,()->"minecraft:stone",CargoConfig::validEntry);
        WHITELIST=builder.comment("Blocks accepted in WHITELIST mode. An empty list rejects all new cargo.",
            "Uses the same block ID, namespace wildcard, and block tag syntax as the blacklist.")
            .defineListAllowEmpty("whitelist",List.<String>of(),()->"minecraft:stone",CargoConfig::validEntry);
        builder.pop();
    }

    public static boolean allows(Block block) { return policy.allows(block); }

    public static void refresh(ModConfigEvent event) {
        if(event.getConfig().getSpec()!=ServerConfig.SPEC)return;
        policy=Policy.from(LIST_MODE.get(),BLACKLIST.get(),WHITELIST.get());
    }

    public static void unload(ModConfigEvent.Unloading event) {
        if(event.getConfig().getSpec()==ServerConfig.SPEC)policy=Policy.from(ListMode.BLACKLIST,DEFAULT_BLACKLIST,List.of());
    }

    public static boolean validEntry(Object value) {
        if(!(value instanceof String entry))return false;
        entry=entry.trim();
        if(entry.isEmpty())return false;
        if(entry.startsWith("#"))entry=entry.substring(1);
        else if(entry.endsWith(":*"))entry=entry.substring(0,entry.length()-1)+"placeholder";
        // Require explicit namespaces so typos cannot silently refer to a different block.
        int colon=entry.indexOf(':');
        return colon>0&&colon<entry.length()-1&&Identifier.tryParse(entry)!=null;
    }

    /** Compile selectors once per config change; tags resolve against the current datapack registry. */
    public record Policy(ListMode mode,Set<Identifier> blocks,Set<String> namespaces,Set<TagKey<Block>> tags) {
        public static Policy from(ListMode mode,List<? extends String> blacklist,List<? extends String> whitelist) {
            var blocks=new HashSet<Identifier>();var namespaces=new HashSet<String>();var tags=new HashSet<TagKey<Block>>();
            for(String value:mode==ListMode.BLACKLIST?blacklist:whitelist) {
                if(!validEntry(value))throw new IllegalArgumentException("Invalid cargo filter entry: "+value);
                String entry=value.trim();
                if(entry.startsWith("#"))tags.add(TagKey.create(Registries.BLOCK,Identifier.parse(entry.substring(1))));
                else if(entry.endsWith(":*"))namespaces.add(entry.substring(0,entry.length()-2));
                else blocks.add(Identifier.parse(entry));
            }
            return new Policy(mode,Set.copyOf(blocks),Set.copyOf(namespaces),Set.copyOf(tags));
        }
        public boolean allows(Block block) {
            Identifier id=BuiltInRegistries.BLOCK.getKey(block);
            boolean matches=blocks.contains(id)||namespaces.contains(id.getNamespace())
                ||tags.stream().anyMatch(block.builtInRegistryHolder()::is);
            return mode==ListMode.BLACKLIST?!matches:matches;
        }
    }

    private CargoConfig() {}
}
