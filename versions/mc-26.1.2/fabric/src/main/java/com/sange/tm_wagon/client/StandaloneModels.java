package com.sange.tm_wagon.client;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
final class StandaloneModels {
    private static final Map<Identifier,ExtraModelKey<BlockStateModelPart>> KEYS=new LinkedHashMap<>();
    static Identifier id(String path) { var id=Identifier.fromNamespaceAndPath("tm_wagon",path);KEYS.computeIfAbsent(id,k->ExtraModelKey.create(k::toString));return id; }
    static void register(ModelLoadingPlugin.Context context,Identifier id) {
        var variant=new net.minecraft.client.renderer.block.dispatch.Variant(id);
        context.addModel(KEYS.get(id),new UnbakedExtraModel<BlockStateModelPart>() {
            @Override public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver){variant.resolveDependencies(resolver);}
            @Override public BlockStateModelPart bake(net.minecraft.client.resources.model.ModelBaker baker){return variant.bake(baker);}
        });
    }
    static BlockStateModelPart get(Identifier id) { return net.minecraft.client.Minecraft.getInstance().getModelManager().getModel(KEYS.get(id)); }
}
