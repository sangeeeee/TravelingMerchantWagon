package com.sange.tm_wagon.client;

import com.google.gson.JsonParser;
import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.assembly.WagonPartItem;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class PartItemRenderer extends GeoItemRenderer<WagonPartItem> {
    public PartItemRenderer(WagonPart part) {
        super(new Model());
        try (var stream = PartItemRenderer.class.getResourceAsStream("/assets/tm_wagon/parts.json")) {
            if (stream == null) throw new IllegalStateException("Missing part item dimensions");
            float scale = JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject(part.id).get("scale").getAsFloat();
            withScale(scale);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static class Model extends GeoModel<WagonPartItem> {
        private ResourceLocation resource(String path) { return ResourceLocation.fromNamespaceAndPath(TravelingMerchantWagon.MODID,path); }
        @Override public ResourceLocation getModelResource(WagonPartItem item) { return resource("geo/parts/"+item.part().id+".geo.json"); }
        @Override public ResourceLocation getTextureResource(WagonPartItem item) { return resource("textures/entity/wagon.png"); }
        @Override public ResourceLocation getAnimationResource(WagonPartItem item) { return resource("animations/assembly.animation.json"); }
    }
}
