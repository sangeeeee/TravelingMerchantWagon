"""Export reusable cover meshes, an inventory bundle, and complete Blockbench state previews."""
from pathlib import Path
from PIL import Image
import base64
import copy
import json
import math
import sys
import uuid
sys.dont_write_bytecode = True
from prepare_component_materials import cover_hem

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "versions/mc-1.21.1/common/src/main/resources/assets/tm_wagon"
PROJECT = ROOT / "shared-assets/modeling/wagon_cargo_cover"
PROJECT.mkdir(parents=True, exist_ok=True)
TEXTURES = {"fabric": "tm_wagon:component/dye/cargo_cover_fabric", "rope": "tm_wagon:block/cargo_cover_rope", "spiral": "tm_wagon:component/dye/cargo_cover_spiral", "hem": "tm_wagon:component/dye/cargo_cover_hem"}

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")

# Keep the active authored fabric, spiral and rope palettes; only add the hem tile.
hem_path=ASSETS / "textures/component/dye/cargo_cover_hem.png"
cover_hem().resize((64,8),Image.Resampling.NEAREST).convert("RGBA").save(hem_path,optimize=True)

def texture_path(key):return ASSETS / ("textures/"+TEXTURES[key].split(":",1)[1]+".png")

def box(name, start, end, texture="fabric", rotation=None):
    part={"name":name,"from":start,"to":end,"faces":{face:{"uv":[0,0,16,16],"texture":"#"+texture} for face in ["north","south","east","west","up","down"]}}
    if texture!="rope":
        for face in part["faces"].values():face["tintindex"]=0
    if rotation:part["rotation"]=rotation
    return part

sheet=[box("canvas",[0,0,0],[16,.25,16]),
       box("left_hanging_edge",[0,-2,0],[.105,0,16],"hem"),box("right_hanging_edge",[15.895,-2,0],[16,0,16],"hem")]
# Reach beyond the geometric corner so adjacent rows meet in a joined V.
# The tiny height separation is 1/200 of a model pixel, invisible as a layer,
# but keeps intersecting top faces from sharing the same depth.
cord_y=.34;cord_top=.65;cord_epsilon=.005;half_length=8*math.sqrt(2)+.10
for suffix,angle,lift in [("main",-45,0),("cross",45,cord_epsilon)]:
    sheet.append(box("cross_cord_"+suffix,[8-half_length,cord_y+lift,7.82],[8+half_length,cord_top+lift,8.18],"rope",{"origin":[8,cord_y,8],"axis":"y","angle":angle,"rescale":False}))
# One pair of downward ties per row boundary: neighbouring sheets never duplicate them.
for suffix,x0,x1 in [("left",-.16,.08),("right",15.92,16.16)]:
    sheet.append(box("hanging_cord_"+suffix,[x0,-2.2,-.27],[x1,.42,.27],"rope"))
back_hem=[box("back_hanging_edge",[.105,-2,0],[15.895,0,16])]
for suffix,x0,x1 in [("left",-.16,.08),("right",15.92,16.16)]:
    back_hem.append(box("back_hanging_cord_"+suffix,[x0,-2.2,0],[x1,.42,16],"rope"))
levels=[-1,-.70710678,-.41421356,.41421356,.70710678,1]
widths=[.41421356,.70710678,1,.70710678,.41421356]
roll=[]
for i, width in enumerate(widths):
    part=box(f"rolled_canvas_{i}",[0,8+8*levels[i],8-8*width],[16,8+8*levels[i+1],8+8*width])
    z0,z1=part["from"][2],part["to"][2];y0,y1=part["from"][1],part["to"][1]
    part["faces"]["west"]={"texture":"#spiral","uv":[z0,16-y1,z1,16-y0]}
    part["faces"]["east"]={"texture":"#spiral","uv":[16-z1,16-y1,16-z0,16-y0]}
    roll.append(part)
    for x in [3.55,11.75]:
        roll.append(box(f"roll_cord_{i}_{x}",[x,8+8*levels[i]*1.008,8-8*width*1.008],[x+.48,8+8*levels[i+1]*1.008,8+8*width*1.008],"rope"))

def cloth_edge_uv(part):
    """Keep a quarter-pixel sheet edge and two-pixel hem from squeezing a full texture into their height."""
    name=part["name"];height=part["to"][1]-part["from"][1]
    side=name.startswith(("left_hanging", "right_hanging", "edge_"))
    back=name=="back_hanging_edge"
    if not (side or back or name.startswith("canvas")):return
    for face in ["north","south","east","west"]:
        width=.105 if (side and face in ["north","south"]) or (back and face in ["east","west"]) else 16
        part["faces"][face]["uv"]=[0,0,width,16 if part["faces"][face]["texture"]=="#hem" else height]
    if side or back:part["faces"].pop("up",None)

for part in sheet+back_hem:cloth_edge_uv(part)

def model(parts):
    used={face["texture"][1:] for part in parts for face in part["faces"].values()}
    for part in parts:
        for face in part["faces"].values():
            if face["texture"]!="#rope":face["tintindex"]=0
    return {"credit":"TravelingMerchantWagon / tools/export_cargo_cover.py","ambientocclusion":False,"textures":{**{key:value for key,value in TEXTURES.items() if key in used},"particle":TEXTURES["fabric"]},"elements":parts}
write(ASSETS / "models/block/cargo_cover_sheet.json",model(sheet))
write(ASSETS / "models/block/cargo_cover_back_hem.json",model(back_hem))
write(ASSETS / "models/block/cargo_cover_roll.json",model(roll))
item_parts=copy.deepcopy(roll)
for part in item_parts:
    for name in ["from","to"]:
        x,y,z=part[name];part[name]=[x,8+(y-8)*.375/math.sqrt(2),8+(z-8)*.375/math.sqrt(2)]
item=model(item_parts)
item["display"]={"gui":{"rotation":[25,35,0],"translation":[0,.5,0],"scale":[.9,.9,.9]},
    "ground":{"translation":[0,1.5,0],"scale":[.5,.5,.5]},"fixed":{"scale":[.8,.8,.8]},
    "thirdperson_righthand":{"rotation":[60,0,0],"translation":[0,2,0],"scale":[.6,.6,.6]},
    "firstperson_righthand":{"rotation":[0,-25,0],"translation":[0,1,0],"scale":[.75,.75,.75]}}
write(ASSETS / "models/item/wagon_cargo_cover.json",item)

def textures(offset=0):
    result=[]
    for index,key in enumerate(TEXTURES):
        file=texture_path(key)
        with Image.open(file) as im:width,height=im.size
        result.append({"name":file.name,"id":str(offset+index),"uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,"tm_wagon/cover/texture/"+key)),"width":width,"height":height,"source":"data:image/png;base64,"+base64.b64encode(file.read_bytes()).decode()})
    return result

def bb_cube(part, offset=0):
    faces={}
    for face,data in part["faces"].items():
        key=data["texture"].removeprefix("#")
        with Image.open(texture_path(key)) as im:w,h=im.size
        faces[face]={"texture":offset+list(TEXTURES).index(key),"uv":[v*(w if i%2==0 else h)/16 for i,v in enumerate(data["uv"])]}
    result={"name":part["name"],"type":"cube","uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,"tm_wagon/cover/"+part["name"]+str(part["from"]))),"from":part["from"],"to":part["to"],"box_uv":False,"autouv":0,"rescale":False,"faces":faces}
    if "rotation" in part:
        rot=part["rotation"];result["origin"]=rot["origin"];result["rotation"]=[rot["angle"] if a==rot["axis"] else 0 for a in ["x","y","z"]]
    return result
item_bb={"meta":{"format_version":"4.10","model_format":"java_block","box_uv":False},"name":"wagon_cargo_cover_bundle","resolution":{"width":64,"height":64},"textures":textures(),"display":item["display"]}
item_bb["elements"]=[bb_cube(p) for p in item_parts];item_bb["outliner"]=[p["uuid"] for p in item_bb["elements"]]
write(PROJECT / "cargo_cover_item.bbmodel",item_bb)

# Full wagon previews use actual game-local dimensions, including every row and rope.
# No generated project is required to build the mod: only the exported resources above are consumed.
for size,rows,width_scale,front_offset,extension in [("standard",5,1,0,0),("extended",6,1,0,.7),("wide",8,1.75,-1.4,.65)]:
    prefix={"standard":"","extended":"long_","wide":"wide_"}[size]
    source=ROOT / f"shared-assets/modeling/open_cargo_wagon/variants/{prefix}single_seat_single_horse/wagon.bbmodel"
    base=json.loads(source.read_text(encoding="utf-8"))
    half_width=19*width_scale;back=2.34375+extension
    def boundary(row):return -1.34375+front_offset if row==0 else back if row==rows else (-2.45-.35 if size=="wide" else -1.31)+row*.7
    for opened in range(rows+1):
        project=copy.deepcopy(base);project["name"]=f"{size}_cargo_cover_open_{opened}"
        offset=len(project["textures"]);project["textures"].extend(textures(offset));parts=[]
        for row in range(opened,rows):
            z0,z1=boundary(row),boundary(row+1)
            parts.append(box(f"canvas_row_{row}",[-half_width,37.75,z0*16],[half_width,38,z1*16]))
            for x in [-half_width,half_width-.25]:parts.append(box(f"edge_{row}_{x}",[x,35.75,z0*16],[x+.25,37.75,z1*16],"hem"))
            dz=(z1-z0)*16;length=math.hypot(half_width*2,dz);angle=math.degrees(math.atan2(dz,half_width*2));centre=[0,38.14,(z0+z1)*8]
            for suffix,sign,lift in [("main",-1,0),("cross",1,cord_epsilon)]:
                parts.append(box(f"tie_{row}_{suffix}",[-length/2-.12,38.09+lift,centre[2]-.13],[length/2+.12,38.40+lift,centre[2]+.13],"rope",{"origin":centre,"axis":"y","angle":sign*angle}))
            for suffix,x0,x1 in [("left",-half_width-.38,-half_width+.19),("right",half_width-.19,half_width+.38)]:
                parts.append(box(f"hanging_cord_{row}_{suffix}",[x0,35.55,z0*16-.19],[x1,38.17,z0*16+.19],"rope"))
        if opened<rows:
            parts.append(box("back_hanging_edge",[-half_width+.25,35.75,back*16-.25],[half_width-.25,37.75,back*16]))
            for suffix,x0,x1 in [("left",-half_width-.38,-half_width+.19),("right",half_width-.19,half_width+.38)]:
                parts.append(box(f"back_hanging_cord_{suffix}",[x0,35.55,back*16-.25],[x1,38.17,back*16],"rope"))
        if opened:
            r=(.055+.047*math.sqrt(opened))/math.sqrt(2);cz=back-r if opened==rows else boundary(opened)
            for part in roll:
                new=copy.deepcopy(part);new["name"]="roll_"+new["name"]
                for key in ["from","to"]:
                    x,y,z=new[key];new[key]=[-half_width+x*half_width*2/16,38+y*r*2,(cz-r)*16+z*r*2]
                parts.append(new)
        for part in parts:cloth_edge_uv(part)
        cubes=[bb_cube(p,offset) for p in parts];project["elements"].extend(cubes)
        project["outliner"].append({"name":"cargo_cover","uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,"tm_wagon/cover/group/"+project["name"])),"origin":[0,38,0],"children":[p["uuid"] for p in cubes]})
        write(PROJECT / size / f"open_{opened}_rows/wagon.bbmodel",project)
print("Exported joined rope cuboids, downward ties and creased hems, three reusable meshes, an item bundle and 22 complete wagon previews.")
