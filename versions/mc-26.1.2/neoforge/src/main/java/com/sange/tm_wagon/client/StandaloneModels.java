package com.sange.tm_wagon.client;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.neoforged.neoforge.client.model.standalone.*;
import net.neoforged.neoforge.client.event.ModelEvent;
final class StandaloneModels {
    private static final Map<Identifier,StandaloneModelKey<BlockStateModelPart>> KEYS=new LinkedHashMap<>();
    static Identifier id(String path) { var id=Identifier.fromNamespaceAndPath("tm_wagon",path);KEYS.computeIfAbsent(id,k->new StandaloneModelKey<>(k::toString));return id; }
    static void register(ModelEvent.RegisterStandalone event,Identifier id) { event.register(KEYS.get(id),SimpleUnbakedStandaloneModel.simpleModelWrapper(id)); }
    static BlockStateModelPart get(Identifier id) { return net.minecraft.client.Minecraft.getInstance().getModelManager().getStandaloneModel(KEYS.get(id)); }
}
