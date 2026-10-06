"""Export a cargo-sized oak stool using the vanilla oak-planks texture."""
from pathlib import Path
import json
import uuid
import zipfile
import base64

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "versions/mc-1.21.1/common/src/main/resources/assets/tm_wagon"
PROJECT = ROOT / "shared-assets/modeling/wagon_stool"
PROJECT.mkdir(parents=True, exist_ok=True)

def box(name, start, end):
    x, y, z = [end[i]-start[i] for i in range(3)]
    uv = {"up": [0, 0, x, z], "down": [0, 0, x, z],
          "north": [0, 0, x, y], "south": [0, 0, x, y],
          "east": [0, 0, z, y], "west": [0, 0, z, y]}
    return {"name": name, "from": start, "to": end,
            "faces": {face: {"texture": "#oak", "uv": values} for face, values in uv.items()}}

parts = []
for i in range(3):
    z = 2.56 + i * (10.88/3)
    parts.append(box(f"seat_plank_{i+1}", [2.56, 6.5, z], [13.44, 8, z+10.88/3-.025]))
for ix, x in enumerate([3.06, 11.44]):
    for iz, z in enumerate([3.06, 11.44]):
        parts.append(box(f"leg_{ix}_{iz}", [x, 0, z], [x+1.5, 6.5, z+1.5]))
for x in [3.26, 11.64]:
    parts.append(box("side_stretcher", [x, 2.4, 4.56], [x+1.1, 3.4, 11.44]))
parts.append(box("cross_stretcher", [4.56, 2.45, 7.45], [11.44, 3.35, 8.55]))
model = {"credit": "TravelingMerchantWagon / tools/export_wagon_stool.py",
         "textures": {"oak": "minecraft:block/oak_planks", "particle": "minecraft:block/oak_planks"},
         "elements": parts,
         "display": {
             "gui": {"rotation": [30, 45, 0], "translation": [0, 3, 0], "scale": [1, 1, 1]},
             "ground": {"translation": [0, 3, 0], "scale": [.5, .5, .5]},
             "fixed": {"translation": [0, 3, 0], "scale": [1, 1, 1]},
             "thirdperson_righthand": {"rotation": [70, 0, 0], "translation": [0, 4, 1], "scale": [.5, .5, .5]},
             "thirdperson_lefthand": {"rotation": [70, 0, 0], "translation": [0, 4, 1], "scale": [.5, .5, .5]},
             "firstperson_righthand": {"rotation": [0, -30, 0], "translation": [0, 4, 0], "scale": [.65, .65, .65]},
             "firstperson_lefthand": {"rotation": [0, 30, 0], "translation": [0, 4, 0], "scale": [.65, .65, .65]}}}
(ASSETS / "models/item/wagon_stool.json").write_text(json.dumps(model, indent=2)+"\n", encoding="utf-8")
# Embed the local vanilla texture so Blockbench can preview without resource-pack setup.
archive = ROOT / "build/moddev/artifacts/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar"
with zipfile.ZipFile(archive) as source:
    texture = source.read("assets/minecraft/textures/block/oak_planks.png")
elements = []
for part in parts:
    element = {**part, "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "tm_wagon/stool/"+part["name"]+str(part["from"]))),
               "type": "cube", "origin": [8, 0, 8], "box_uv": False, "rescale": False}
    element["faces"] = {face: {**data, "texture": 0} for face, data in part["faces"].items()}
    elements.append(element)
bbmodel = {"meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
           "name": "wagon_stool", "resolution": {"width": 16, "height": 16},
           "elements": elements, "outliner": [e["uuid"] for e in elements], "display": model["display"],
           "textures": [{"name": "oak_planks.png", "id": "oak", "namespace": "minecraft", "folder": "block",
                         "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "tm_wagon/stool/oak")),
                         "source": "data:image/png;base64,"+base64.b64encode(texture).decode(), "width": 16, "height": 16}]}
(PROJECT / "wagon_stool.bbmodel").write_text(json.dumps(bbmodel, indent=2)+"\n", encoding="utf-8")
(PROJECT / "wagon_stool.json").write_text(json.dumps(model, indent=2)+"\n", encoding="utf-8")
print(f"Exported oak stool: {len(parts)} cuboids; 0.68 x 0.5 x 0.68 blocks.")
