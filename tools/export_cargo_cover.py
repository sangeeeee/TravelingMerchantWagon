"""Export reusable cover meshes, an inventory bundle, and complete Blockbench state previews."""
from pathlib import Path
from PIL import Image, ImageDraw
import base64
import copy
import io
import json
import math
import random
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/tm_wagon"
PROJECT = ROOT / "modeling/wagon_cargo_cover"
PROJECT.mkdir(parents=True, exist_ok=True)
TEXTURES = {"fabric": "tm_wagon:block/cargo_cover_fabric", "rope": "tm_wagon:block/cargo_cover_rope"}

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")

# Wool-like weave, a warm grey canvas and dark cords, all on the established 64-pixel grid.
rng = random.Random(73491)
archive = ROOT / "build/moddev/artifacts/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar"
with zipfile.ZipFile(archive) as source:
    wool = Image.open(io.BytesIO(source.read("assets/minecraft/textures/block/gray_wool.png"))).convert("RGB").resize((64,64),Image.Resampling.NEAREST)
fabric = Image.new("RGB", (64,64))
for y in range(64):
    for x in range(64):
        grey = sum(wool.getpixel((x,y)))//3
        variation = (grey-75)//2 + rng.randint(-3,3) + (2 if (x+y)%4==0 else 0)
        fabric.putpixel((x,y), (max(0,min(255,132+variation)),max(0,min(255,133+variation)),max(0,min(255,122+variation))))
draw = ImageDraw.Draw(fabric)
for y in [2,61]:
    draw.line((0,y,63,y),fill=(102,105,94))
    for x in range(1,64,4):draw.line((x,y-1,x+1,y-1),fill=(156,157,145))
for x,y,length in [(3,9,10),(52,13,8),(7,46,9),(50,48,12),(24,26,7)]:
    draw.line((x,y,x+length,y+3),fill=(109,112,102));draw.line((x,y+1,x+length,y+4),fill=(146,147,135))
rope = Image.new("RGB", (64,64))
for y in range(64):
    for x in range(64):
        shade = 13 if (x+y//2)%5<2 else -8
        rope.putpixel((x,y),(78+shade,70+shade,52+shade))
for key, image in [("fabric",fabric),("rope",rope)]:
    path=ASSETS / f"textures/block/cargo_cover_{key}.png";path.parent.mkdir(parents=True,exist_ok=True);image.save(path)

def box(name, start, end, texture="fabric", rotation=None):
    part={"name":name,"from":start,"to":end,"faces":{face:{"uv":[0,0,16,16],"texture":"#"+texture} for face in ["north","south","east","west","up","down"]}}
    if rotation:part["rotation"]=rotation
    return part

sheet=[box("canvas",[0,0,0],[16,.5,16]),
       box("left_hanging_hem",[0,-.6,0],[.22,.55,16]),box("right_hanging_hem",[15.78,-.6,0],[16,.55,16])]
for index, angle in enumerate([-45,45]):
    y=.63+index*.4
    sheet.append(box("cross_cord_"+str(angle),[-3.08,y,7.82],[19.08,y+.39,8.18],"rope",{"origin":[8,y+.2,8],"axis":"y","angle":angle,"rescale":False}))
for x in [.2,15.2]:sheet.append(box("edge_knot",[x,.6,7.65],[x+.6,1.3,8.35],"rope"))
levels=[-1,-.70710678,-.41421356,.41421356,.70710678,1]
widths=[.41421356,.70710678,1,.70710678,.41421356]
roll=[]
for i, width in enumerate(widths):
    roll.append(box(f"rolled_canvas_{i}",[0,8+8*levels[i],8-8*width],[16,8+8*levels[i+1],8+8*width]))
    for x in [3.55,11.75]:
        roll.append(box(f"roll_cord_{i}_{x}",[x,8+8*levels[i]*1.008,8-8*width*1.008],[x+.48,8+8*levels[i+1]*1.008,8+8*width*1.008],"rope"))

def model(parts):return {"credit":"TravelingMerchantWagon / tools/export_cargo_cover.py","ambientocclusion":False,"textures":{**TEXTURES,"particle":TEXTURES["fabric"]},"elements":parts}
write(ASSETS / "models/block/cargo_cover_sheet.json",model(sheet))
write(ASSETS / "models/block/cargo_cover_roll.json",model(roll))
item_parts=copy.deepcopy(roll)
for part in item_parts:
    for name in ["from","to"]:
        x,y,z=part[name];part[name]=[x,5+y*.375,5+z*.375]
item=model(item_parts)
item["display"]={"gui":{"rotation":[25,35,0],"translation":[0,.5,0],"scale":[.9,.9,.9]},
    "ground":{"translation":[0,1.5,0],"scale":[.5,.5,.5]},"fixed":{"scale":[.8,.8,.8]},
    "thirdperson_righthand":{"rotation":[60,0,0],"translation":[0,2,0],"scale":[.6,.6,.6]},
    "firstperson_righthand":{"rotation":[0,-25,0],"translation":[0,1,0],"scale":[.75,.75,.75]}}
write(ASSETS / "models/item/wagon_cargo_cover.json",item)

def textures(offset=0):
    result=[]
    for index,key in enumerate(TEXTURES):
        file=ASSETS / f"textures/block/cargo_cover_{key}.png"
        result.append({"name":file.name,"id":str(offset+index),"uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,"tm_wagon/cover/texture/"+key)),"width":64,"height":64,"source":"data:image/png;base64,"+base64.b64encode(file.read_bytes()).decode()})
    return result

def bb_cube(part, offset=0):
    result={"name":part["name"],"type":"cube","uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,"tm_wagon/cover/"+part["name"]+str(part["from"]))),"from":part["from"],"to":part["to"],"box_uv":False,"autouv":0,"rescale":False,"faces":{face:{"texture":offset+(1 if data["texture"]=="#rope" else 0),"uv":[v*4 for v in data["uv"]]} for face,data in part["faces"].items()}}
    if "rotation" in part:
        rot=part["rotation"];result["origin"]=rot["origin"];result["rotation"]=[rot["angle"] if a==rot["axis"] else 0 for a in ["x","y","z"]]
    return result
item_bb={"meta":{"format_version":"4.10","model_format":"java_block","box_uv":False},"name":"wagon_cargo_cover_bundle","resolution":{"width":64,"height":64},"textures":textures(),"display":item["display"]}
item_bb["elements"]=[bb_cube(p) for p in item_parts];item_bb["outliner"]=[p["uuid"] for p in item_bb["elements"]]
write(PROJECT / "cargo_cover_item.bbmodel",item_bb)

# Full wagon previews use actual game-local dimensions, including every row and rope.
# No generated project is required to build the mod: only the exported resources above are consumed.
for extended in [False,True]:
    prefix="long_" if extended else ""
    source=ROOT / f"modeling/open_cargo_wagon/variants/{prefix}single_seat_single_horse/wagon.bbmodel"
    base=json.loads(source.read_text(encoding="utf-8"))
    rows=6 if extended else 5;back=2.34375+(.7 if extended else 0)
    def boundary(row):return -1.59375 if row==0 else back if row==rows else -1.31+row*.7
    for opened in range(rows+1):
        project=copy.deepcopy(base);project["name"]=f"{'extended' if extended else 'standard'}_cargo_cover_open_{opened}"
        offset=len(project["textures"]);project["textures"].extend(textures(offset));parts=[]
        for row in range(opened,rows):
            z0,z1=boundary(row),boundary(row+1)
            # Cloth, side hems and two taut diagonal ties per covered row.
            parts.append(box(f"canvas_row_{row}",[-19,37.5,z0*16],[19,38,z1*16]))
            for x in [-19,18.7]:parts.append(box(f"hem_{row}_{x}",[x,36.9,z0*16],[x+.3,38.05,z1*16]))
            dz=(z1-z0)*16;length=math.hypot(37,dz);angle=math.degrees(math.atan2(dz,37));centre=[0,38.2,(z0+z1)*8]
            for sign in [-1,1]:
                y=38.1 if sign<0 else 38.55
                parts.append(box(f"tie_{row}_{sign}",[-length/2,y,centre[2]-.15],[length/2,y+.4,centre[2]+.15],"rope",{"origin":[0,y+.2,centre[2]],"axis":"y","angle":angle*sign}))
        if opened:
            r=.055+.047*math.sqrt(opened);cz=back-r if opened==rows else boundary(opened)
            for part in roll:
                new=copy.deepcopy(part);new["name"]="roll_"+new["name"]
                for key in ["from","to"]:
                    x,y,z=new[key];new[key]=[-19+x*38/16,38+y*r*2,(cz-r)*16+z*r*2]
                parts.append(new)
        cubes=[bb_cube(p,offset) for p in parts];project["elements"].extend(cubes)
        project["outliner"].append({"name":"cargo_cover","uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,"tm_wagon/cover/group/"+project["name"])),"origin":[0,38,0],"children":[p["uuid"] for p in cubes]})
        write(PROJECT / ("extended" if extended else "standard") / f"open_{opened}_rows/wagon.bbmodel",project)
print("Exported 64x64 canvas and cord textures, two reusable meshes, an item bundle and 13 complete wagon previews.")
