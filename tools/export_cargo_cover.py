"""Export reusable cover meshes, an inventory bundle, and complete Blockbench state previews."""
from pathlib import Path
from PIL import Image, ImageDraw
import base64
import copy
import json
import math
import random
import uuid

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/tm_wagon"
PROJECT = ROOT / "modeling/wagon_cargo_cover"
PROJECT.mkdir(parents=True, exist_ok=True)
TEXTURES = {"fabric": "tm_wagon:block/cargo_cover_fabric", "rope": "tm_wagon:block/cargo_cover_rope", "spiral": "tm_wagon:block/cargo_cover_spiral"}

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")

# Deliberately coarse cloth stripes and dirt patches, nearest-neighbour enlarged to 64 pixels.
rng = random.Random(73491)
fabric = Image.new("RGB", (16,16), (127,124,111))
draw = ImageDraw.Draw(fabric)
for y in range(0,16,2):
    tone=rng.choice([(115,112,100),(135,131,116),(122,119,106)])
    draw.line((0,y,15,y),fill=tone)
    draw.line((rng.randrange(0,7),y+1,rng.randrange(10,16),y+1),fill=(142,137,121))
for bounds,tone in [((1,4,5,5),(103,97,80)),((10,11,14,13),(110,103,84)),((6,8,8,10),(117,110,90)),((1,14,4,15),(107,100,81))]:
    draw.rectangle(bounds,fill=tone)
rope=Image.new("RGB", (8,8), (81,66,51))
rope_draw=ImageDraw.Draw(rope)
rope_draw.rectangle((0,0,3,2),fill=(94,77,60));rope_draw.rectangle((4,4,7,7),fill=(68,55,43))
spiral=fabric.copy();spiral_draw=ImageDraw.Draw(spiral)
points=[]
for step in range(110):
    t=step/109;angle=t*math.pi*4.6;radius=.4+6.9*t
    point=(round(7.5+radius*math.cos(angle)),round(7.5+radius*math.sin(angle)))
    if not points or point!=points[-1]:points.append(point)
spiral_draw.line(points,fill=(77,73,61),width=1)
for key,image in [("fabric",fabric),("rope",rope),("spiral",spiral)]:
    path=ASSETS / f"textures/block/cargo_cover_{key}.png";path.parent.mkdir(parents=True,exist_ok=True)
    image.resize((64,64),Image.Resampling.NEAREST).save(path)

def box(name, start, end, texture="fabric", rotation=None):
    part={"name":name,"from":start,"to":end,"faces":{face:{"uv":[0,0,16,16],"texture":"#"+texture} for face in ["north","south","east","west","up","down"]}}
    if rotation:part["rotation"]=rotation
    return part

sheet=[box("canvas",[0,0,0],[16,.25,16]),
       box("left_hanging_edge",[0,-2,0],[.105,0,16]),box("right_hanging_edge",[15.895,-2,0],[16,0,16])]
# All X arms share one height. Trim the second rope at the crossing instead of stacking it.
cord_y=.34;cord_top=.65
sheet.append(box("cross_cord_main",[-3.08,cord_y,7.82],[19.08,cord_top,8.18],"rope",{"origin":[8,cord_y,8],"axis":"y","angle":-45,"rescale":False}))
for suffix,x0,x1 in [("left",-3.08,7.82),("right",8.18,19.08)]:
    sheet.append(box("cross_cord_"+suffix,[x0,cord_y,7.82],[x1,cord_top,8.18],"rope",{"origin":[8,cord_y,8],"axis":"y","angle":45,"rescale":False}))
back_hem=[box("back_hanging_edge",[.105,-2,0],[15.895,0,16])]
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
        part["faces"][face]["uv"]=[0,0,width,height]
    if side or back:part["faces"].pop("up",None)

for part in sheet+back_hem:cloth_edge_uv(part)

def model(parts):return {"credit":"TravelingMerchantWagon / tools/export_cargo_cover.py","ambientocclusion":False,"textures":{**TEXTURES,"particle":TEXTURES["fabric"]},"elements":parts}
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
        file=ASSETS / f"textures/block/cargo_cover_{key}.png"
        result.append({"name":file.name,"id":str(offset+index),"uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,"tm_wagon/cover/texture/"+key)),"width":64,"height":64,"source":"data:image/png;base64,"+base64.b64encode(file.read_bytes()).decode()})
    return result

def bb_cube(part, offset=0):
    result={"name":part["name"],"type":"cube","uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,"tm_wagon/cover/"+part["name"]+str(part["from"]))),"from":part["from"],"to":part["to"],"box_uv":False,"autouv":0,"rescale":False,"faces":{face:{"texture":offset+list(TEXTURES).index(data["texture"].removeprefix("#")),"uv":[v*4 for v in data["uv"]]} for face,data in part["faces"].items()}}
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
    def boundary(row):return -1.34375 if row==0 else back if row==rows else -1.31+row*.7
    for opened in range(rows+1):
        project=copy.deepcopy(base);project["name"]=f"{'extended' if extended else 'standard'}_cargo_cover_open_{opened}"
        offset=len(project["textures"]);project["textures"].extend(textures(offset));parts=[]
        for row in range(opened,rows):
            z0,z1=boundary(row),boundary(row+1)
            parts.append(box(f"canvas_row_{row}",[-19,37.75,z0*16],[19,38,z1*16]))
            for x in [-19,18.75]:parts.append(box(f"edge_{row}_{x}",[x,35.75,z0*16],[x+.25,37.75,z1*16]))
            dz=(z1-z0)*16;length=math.hypot(37,dz);angle=math.degrees(math.atan2(dz,37));centre=[0,38.14,(z0+z1)*8]
            # At non-uniform scale the angle changes, but the coplanar, non-overlapping X is identical.
            parts.append(box(f"tie_{row}_main",[-length/2,38.09,centre[2]-.13],[length/2,38.40,centre[2]+.13],"rope",{"origin":centre,"axis":"y","angle":-angle}))
            # The second rope is trimmed to avoid sharing top faces at the crossing.
            gap=.13/max(.01,math.sin(math.radians(angle*2)))
            for suffix,x0,x1 in [("left",-length/2,-gap),("right",gap,length/2)]:
                parts.append(box(f"tie_{row}_{suffix}",[x0,38.09,centre[2]-.13],[x1,38.40,centre[2]+.13],"rope",{"origin":centre,"axis":"y","angle":angle}))
        if opened<rows:parts.append(box("back_hanging_edge",[-18.75,35.75,back*16-.25],[18.75,37.75,back*16]))
        if opened:
            r=(.055+.047*math.sqrt(opened))/math.sqrt(2);cz=back-r if opened==rows else boundary(opened)
            for part in roll:
                new=copy.deepcopy(part);new["name"]="roll_"+new["name"]
                for key in ["from","to"]:
                    x,y,z=new[key];new[key]=[-19+x*38/16,38+y*r*2,(cz-r)*16+z*r*2]
                parts.append(new)
        for part in parts:cloth_edge_uv(part)
        cubes=[bb_cube(p,offset) for p in parts];project["elements"].extend(cubes)
        project["outliner"].append({"name":"cargo_cover","uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,"tm_wagon/cover/group/"+project["name"])),"origin":[0,38,0],"children":[p["uuid"] for p in cubes]})
        write(PROJECT / ("extended" if extended else "standard") / f"open_{opened}_rows/wagon.bbmodel",project)
print("Exported coarse canvas, brown cords and spiral roll caps, three reusable meshes, an item bundle and 13 complete wagon previews.")
