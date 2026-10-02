"""Export editable wagon geometry into stage-one block/item resources.

Collision uses simple gameplay volumes, then is clipped to world cells by Java.
"""
import copy
import itertools
import json
import math
from pathlib import Path
import shutil
from simplified_collision import volumes
from extend_wagon_model import generate_long_variants
from wooden_seat_models import generate_wooden_seats

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/tm_wagon'
VARIANTS = ROOT / 'modeling/open_cargo_wagon/variants'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf8')


def read_model(variant):
    return json.loads((VARIANTS / variant / 'wagon.bbmodel').read_text(encoding='utf8'))


def grouped(model):
    elements = {c['uuid']: c for c in model['elements']}
    result = {}
    def walk(groups):
        for group in groups:
            if isinstance(group, dict):
                result[group['name']] = [elements[g] for g in group['children'] if isinstance(g, str)]
                walk(group['children'])
    walk(model['outliner'])
    return result


def transformed(point, cube):
    pivot = cube['origin']
    x, y, z = [point[i] - pivot[i] for i in range(3)]
    for axis, degrees in enumerate(cube.get('rotation', [0, 0, 0])):
        c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
        if axis == 0: y, z = y*c-z*s, y*s+z*c
        elif axis == 1: x, z = x*c+z*s, -x*s+z*c
        else: x, y = x*c-y*s, x*s+y*c
    return [x+pivot[0], y+pivot[1], z+pivot[2]]


def boxes(cubes, offset=(0, 0, 0)):
    result = []
    for cube in cubes:
        start, end = cube['from'], cube['to']
        longest = max(range(3), key=lambda i: end[i]-start[i])
        count = max(1, math.ceil((end[longest]-start[longest])/2)) if any(cube.get('rotation', [])) else 1
        for j in range(count):
            lo, hi = start.copy(), end.copy()
            lo[longest] = start[longest] + (end[longest]-start[longest])*j/count
            hi[longest] = start[longest] + (end[longest]-start[longest])*(j+1)/count
            points = [transformed(p, cube) for p in itertools.product(*zip(lo, hi))]
            bounds = [min(p[i] for p in points) for i in range(3)] + [max(p[i] for p in points) for i in range(3)]
            result.append([round((v-offset[i % 3])/16, 6) for i, v in enumerate(bounds)])
    return result


def item_geometry(geometry, names, identifier):
    data = copy.deepcopy(geometry)
    geo = data['minecraft:geometry'][0]
    selected = [b for b in geo['bones'] if b['name'] in names]
    # Retain only selected bones; their coordinates and pivots are absolute.
    for bone in selected:
        if bone.get('parent') not in names: bone.pop('parent', None)
    cubes = [c for b in selected for c in b.get('cubes', [])]
    # A transformed bounds center from Blockbench is supplied by caller below.
    geo['bones'] = selected
    geo['description']['identifier'] = 'geometry.tm_wagon.' + identifier
    geo['description']['visible_bounds_width'] = 8
    geo['description']['visible_bounds_height'] = 8
    geo['description']['visible_bounds_offset'] = [0, 0, 0]
    return data


def main():
    generate_wooden_seats()
    generate_long_variants()
    collision = volumes()
    rig = ROOT/'modeling/wagon_assembly_frame/exports/assets/tm_wagon'
    frame_geo = json.loads((rig/'geo/wagon_assembly_frame.geo.json').read_text())
    frame_bones = frame_geo['minecraft:geometry'][0]['bones']
    frame_animations = json.loads((rig/'animations/wagon_assembly_frame.animation.json').read_text())
    write(ASSETS/'geo/wagon_assembly_frame.geo.json',frame_geo)
    write(ASSETS/'animations/wagon_assembly_frame.animation.json',frame_animations)
    frame_item = copy.deepcopy(frame_geo)
    frame_item['minecraft:geometry'][0]['description']['identifier'] = 'geometry.tm_wagon.frame_item'
    for bone in frame_item['minecraft:geometry'][0]['bones']:
        bone['pivot'][1] -= 10.99
        for cube in bone.get('cubes',[]):
            for field in ('origin','pivot'):
                if field in cube: cube[field][1] -= 10.99
    write(ASSETS/'geo/parts/wagon_assembly_frame.geo.json',frame_item)
    definitions = {}
    specs = {
        'cargo_body': ('double_seat_double_horse', ['chassis','cargo_body','tailgate','front_axle','rear_axle','driver_platform'], (0,0,0)),
        'long_cargo_body': ('long_double_seat_double_horse', ['chassis','cargo_body','tailgate','front_axle','rear_axle','driver_platform'], (0,0,0)),
        'single_horse_shafts': ('double_seat_single_horse', ['shafts'], (0,0,0)),
        'double_horse_shafts': ('double_seat_double_horse', ['shafts'], (0,0,0)),
        'single_seat': ('single_seat_double_horse', ['seat'], (0,0,0)),
        'double_seat': ('double_seat_double_horse', ['seat'], (0,0,0)),
        'single_wooden_seat': ('single_wooden_seat_double_horse', ['seat'], (0,0,0)),
        'double_wooden_seat': ('double_wooden_seat_double_horse', ['seat'], (0,0,0)),
        'small_wheel': ('double_seat_double_horse', ['front_left_wheel'], (-21,0,-20)),
        'large_wheel': ('double_seat_double_horse', ['rear_left_wheel'], (-21,0,20)),
    }
    for name, (variant, groups, offset) in specs.items():
        source = read_model(variant)
        cubes = [cube for group in groups for cube in grouped(source)[group]]
        all_boxes = boxes(cubes)
        low = [min(b[i] for b in all_boxes) for i in range(3)]
        high = [max(b[i+3] for b in all_boxes) for i in range(3)]
        center = [(a+b)*8 for a,b in zip(low, high)]
        geo = item_geometry(json.loads((VARIANTS/variant/'wagon.geo.json').read_text()), groups, name)
        # Bedrock export mirrors the X axis; GeckoLib reverses that mirror on load.
        shift = [-center[0], center[1], center[2]]
        for bone in geo['minecraft:geometry'][0]['bones']:
            bone['pivot'] = [v-shift[i] for i,v in enumerate(bone.get('pivot',[0,0,0]))]
            for cube in bone.get('cubes', []):
                for field in ('origin','pivot'):
                    if field in cube: cube[field] = [v-shift[i] for i,v in enumerate(cube[field])]
        definitions[name] = {'scale': round(.68/max(b-a for a,b in zip(low,high)),6)}
        write(ASSETS/'geo/parts'/f'{name}.geo.json', geo)
        write(ASSETS/'models/item'/f'{name}.json', {
            'parent':'minecraft:builtin/entity',
            'textures':{'particle':'tm_wagon:block/wagon_assembly_frame'},
            'display':{
                'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[1,1,1]},
                'ground':{'translation':[0,3,0],'scale':[.6,.6,.6]},
                'fixed':{'rotation':[0,180,0],'scale':[.8,.8,.8]},
                'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.5,.5,.5]},
                'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,2,0],'scale':[.7,.7,.7]},
            }})
    write(RES/'data/tm_wagon/wagon_geometry.json', collision)
    write(ASSETS/'parts.json', definitions)
    (ASSETS/'textures/entity').mkdir(parents=True,exist_ok=True)
    shutil.copyfile(VARIANTS/'double_seat_double_horse/wagon.png',ASSETS/'textures/entity/wagon.png')
    standard=tuple(f'{seat}_{horse}' for seat in ['single_seat','double_seat','single_wooden_seat','double_wooden_seat'] for horse in ['single_horse','double_horse'])
    for variant in (*standard, *(f'long_{name}' for name in standard)):
        geo = json.loads((VARIANTS/variant/'wagon.geo.json').read_text())
        geo['minecraft:geometry'][0]['description'].update(visible_bounds_width=12,visible_bounds_height=6,visible_bounds_offset=[0,2,-2])
        geo['minecraft:geometry'][0]['bones'].extend(copy.deepcopy(frame_bones))
        write(ASSETS/'geo/assembly'/f'{variant}.geo.json',geo)
    write(ASSETS/'animations/assembly.animation.json',frame_animations)
    # Model/texture remain editable in their dedicated modeling subproject.
    rig = ROOT/'modeling/wagon_assembly_frame/exports/assets/tm_wagon'
    for folder in ('models/block','models/item','textures/block'):
        (ASSETS/folder).mkdir(parents=True,exist_ok=True)
        for file in (rig/folder).iterdir(): shutil.copyfile(file,ASSETS/folder/file.name)
    write(ASSETS/'models/item/wagon_assembly_frame.json', {
        'parent':'minecraft:builtin/entity',
        'textures':{'particle':'tm_wagon:block/wagon_assembly_frame'},
        'display':{'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[1,1,1]},
                   'ground':{'translation':[0,2,0],'scale':[.25,.25,.25]},
                   'fixed':{'rotation':[0,180,0],'scale':[.4,.4,.4]},
                   'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.25,.25,.25]},
                   'firstperson_righthand':{'rotation':[0,45,0],'scale':[.4,.4,.4]}}})
    write(ASSETS/'blockstates/wagon_assembly_frame.json', {'variants':{
        'facing='+f+',extended='+extended:{'model':'tm_wagon:block/wagon_assembly_frame','y':angle}
        for f,angle in [('north',0),('east',90),('south',180),('west',270)] for extended in ('false','true')}})
    write(ASSETS/'models/block/assembly_part.json', {'textures':{'particle':'tm_wagon:block/wagon_assembly_frame'},'elements':[]})
    for name in [*specs, 'assembly_proxy']:
        write(ASSETS/'blockstates'/f'{name}.json', {'variants':{'':{'model':'tm_wagon:block/assembly_part'}}})
    write(ASSETS/'models/item/wagon_icon.json', {'parent':'minecraft:item/generated','textures':{'layer0':'tm_wagon:item/wagon_icon'}})
    # All part drops are issued once per module by the assembly controller.
    for name in [*specs,'assembly_proxy','wagon_assembly_frame']:
        write(RES/'data/tm_wagon/loot_table/blocks'/f'{name}.json', {'type':'minecraft:block','pools':[]})
    print(f'Exported {len(specs)} parts, {2*len(standard)} assembly models, clipped-collision input and item display resources.')


if __name__ == '__main__': main()
