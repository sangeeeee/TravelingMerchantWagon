package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.material.WagonMaterial;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;
import com.geckolib.cache.model.*;
import com.geckolib.cache.model.cuboid.*;


/** One atlas and immutable local meshes per material/bone; animated bone poses remain dynamic. */
public final class MaterialRenderer {
    public static final Identifier ATLAS=Identifier.fromNamespaceAndPath("tm_wagon","textures/entity/component_atlas.png");
    private record Baked(GeoCube cube,boolean wool) {}
    private static final int WOOD_COUNT=com.sange.tm_wagon.material.WoodMaterial.values().length;
    private static final int FIXED_TILE=13*WOOD_COUNT;
    @SuppressWarnings("unchecked") private static final Map<GeoBone,CachedMesh>[] CACHE=new Map[FIXED_TILE+1];
    private static Map<WagonPart,Map<String,List<Vec3>>> references=Map.of();
    public static void reload(net.minecraft.server.packs.resources.ResourceManager manager) {
        java.util.Arrays.fill(CACHE,null);
        var result=new EnumMap<WagonPart,Map<String,List<Vec3>>>(WagonPart.class);
        try(var reader=manager.openAsReader(Identifier.fromNamespaceAndPath("tm_wagon","parts.json"))) {
            var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for(var part:WagonPart.values()) {
                var definition=json.getAsJsonObject(part.id);if(definition==null||!definition.has("uv_reference"))continue;
                var bones=new HashMap<String,List<Vec3>>();
                for(var entry:definition.getAsJsonObject("uv_reference").entrySet()) {
                    var sizes=new ArrayList<Vec3>();
                    for(var value:entry.getValue().getAsJsonArray()) {
                        var size=value.getAsJsonArray();sizes.add(new Vec3(size.get(0).getAsDouble(),size.get(1).getAsDouble(),size.get(2).getAsDouble()));
                    }
                    bones.put(entry.getKey(),List.copyOf(sizes));
                }
                result.put(part,Map.copyOf(bones));
            }
        } catch(java.io.IOException error) { throw new IllegalStateException("Cannot load wagon UV references",error); }
        references=Map.copyOf(result);
    }
    public static WagonSlot slot(String bone) {
        return switch(bone) {
            case "seat"->WagonSlot.SEAT;case "shafts"->WagonSlot.SHAFTS;
            case "front_left_wheel"->WagonSlot.FRONT_LEFT;case "front_right_wheel"->WagonSlot.FRONT_RIGHT;
            case "rear_left_wheel"->WagonSlot.REAR_LEFT;case "rear_right_wheel"->WagonSlot.REAR_RIGHT;
            default->bone.startsWith("frame_")?null:WagonSlot.BODY;
        };
    }
    private static int index(WagonPart part,WagonMaterial material) {
        if(part==null||!WagonMaterial.wooden(part))return FIXED_TILE;
        int kind=switch(part) {
            case CARGO_BODY->0;case LONG_CARGO_BODY->1;case SINGLE_SEAT->2;case DOUBLE_SEAT->3;
            case SMALL_WHEEL->4;case LARGE_WHEEL->5;case SINGLE_WOODEN_SEAT->6;case DOUBLE_WOODEN_SEAT->7;
            case WIDE_CARGO_BODY->8;case TRIPLE_SEAT->9;case TRIPLE_WOODEN_SEAT->10;
            default->throw new IllegalArgumentException("Unmapped material part");
        };return kind*WOOD_COUNT+material.wood().ordinal();
    }
    public static void cubes(PoseStack poses,GeoBone bone,VertexConsumer buffer,int light,int overlay,int colour,WagonPart part,WagonMaterial material) {
        if((bone.frameSnapshot!=null&&bone.frameSnapshot.isHidden())||(!(bone instanceof CuboidGeoBone)||((CuboidGeoBone)bone).cubes.length==0))return;
        int tile=index(part,material);var cache=CACHE[tile];if(cache==null)CACHE[tile]=cache=new IdentityHashMap<>();
        var mesh=cache.get(bone);
        if(mesh==null) {
            var builder=new CachedMesh.Builder();
            List<Vec3> sizes=part==null?List.of():references.getOrDefault(part,Map.of()).getOrDefault(bone.name(),List.of());int index=0;
            for(GeoCube cube:((CuboidGeoBone)bone).cubes) {
                var baked=bake(cube,tile,part!=null&&WagonMaterial.cushioned(part),index<sizes.size()?sizes.get(index):cube.size());
                builder.cube(baked.cube,baked.wool);index++;
            }
            mesh=builder.build();cache.put(bone,mesh);
        }
        mesh.render(poses.last(),buffer,light,overlay,colour,FabricColours.tint(material.colour()));
    }
    private static Baked bake(GeoCube cube,int tile,boolean dyeable,Vec3 reference) {
        var quads=new ArrayList<GeoQuad>();boolean wool=dyeable;
        var size=cube.size();var ratio=new Vec3(size.x==0?1:reference.x/size.x,size.y==0?1:reference.y/size.y,size.z==0?1:reference.z/size.z);
        float x=(tile%16)*64,y=(tile/16)*64;
        for(var quad:cube.quads()) {
            if(quad==null)continue;var vertices=new GeoVertex[quad.vertices().length];
            for(int v=0;v<vertices.length;v++) {
                var original=quad.vertices()[v];float u=original.texU()*64,t=original.texV()*64;
                wool&=u>=48-.001&&u<=64+.001&&t>=16-.001&&t<=32+.001;
                vertices[v]=original.withUVs((x+u)/1024,(y+t)/1024);
            }
            quads.addAll(TextureTiling.geo(new GeoQuad(vertices,quad.normalX(),quad.normalY(),quad.normalZ(),quad.direction()),new Vec3(1,1,1),ratio));
        }
        return new Baked(new GeoCube(quads.toArray(GeoQuad[]::new),cube.pivot(),cube.rotation(),cube.size()),wool);
    }
    private MaterialRenderer() {}
}
