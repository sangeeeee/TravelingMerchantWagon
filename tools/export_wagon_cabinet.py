"""Export oak cabinet meshes and self-contained Blockbench seat-accessory previews."""
from pathlib import Path
import base64, copy, json, uuid, zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/tm_wagon'
PROJECT = ROOT / 'modeling/wagon_cabinet'
TEX = {'wood': 'minecraft:block/oak_planks', 'end': 'minecraft:block/stripped_oak_log', 'iron': 'minecraft:block/black_concrete'}
FACES = ['north', 'south', 'east', 'west', 'up', 'down']

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def cube(name, a, b, texture='wood'):
    dx, dy, dz = [b[i]-a[i] for i in range(3)]
    dx, dy, dz = [min(16, v) for v in [dx, dy, dz]]
    uvs = {'north': [0, 0, dx, dy], 'south': [0, 0, dx, dy],
           'east': [0, 0, dz, dy], 'west': [0, 0, dz, dy],
           'up': [0, 0, dx, dz], 'down': [0, 0, dx, dz]}
    return {'name': name, 'from': a, 'to': b, 'faces': {f: {'texture': '#' + texture, 'uv': uvs[f]} for f in FACES}}

def meshes(half):
    y0, y1, z0, z1, t = .04, 5.96, 5.85, 16.48, .48
    body = [cube('oak_bottom', [-half, y0, z0], [half, y0+t, z1]),
            cube('oak_top', [-half, y1-t, z0], [half, y1, z1]),
            cube('oak_front_wall', [-half, y0+t, z0], [half, y1-t, z0+t]),
            cube('oak_rear_wall', [-half, y0+t, z1-t], [half, y1-t, z1])]
    drawers = []
    for side in [-1, 1]:
        # A real open-topped drawer: plank floor and walls, stripped oak end and iron pull.
        parts = [cube('drawer_end', [half-t, y0+t+.06, z0+t+.06], [half, y1-t-.06, z1-t-.06], 'end'),
                 cube('drawer_floor', [.08, y0+t+.06, z0+t+.06], [half-t, y0+t+.40, z1-t-.06]),
                 cube('drawer_front_side', [.08, y0+t+.40, z0+t+.06], [half-t, y1-t-.06, z0+t+.40]),
                 cube('drawer_rear_side', [.08, y0+t+.40, z1-t-.40], [half-t, y1-t-.06, z1-t-.06]),
                 cube('drawer_inner_end', [.08, y0+t+.40, z0+t+.40], [.40, y1-t-.06, z1-t-.40]),
                 cube('iron_pull_front', [half, 2.45, 9.8], [half+.65, 3.05, 10.25], 'iron'),
                 cube('iron_pull_rear', [half, 2.45, 12.08], [half+.65, 3.05, 12.53], 'iron'),
                 cube('iron_pull_bar', [half+.43, 2.45, 10.25], [half+.88, 3.05, 12.08], 'iron')]
        if side == -1:
            for p in parts:
                a, b = p['from'][0], p['to'][0]
                p['from'][0], p['to'][0] = -b, -a
        for p in parts: p['name'] = ('left_' if side == -1 else 'right_') + p['name']
        drawers.append(parts)
    return [body, *drawers]

def model(parts):
    return {'parent': 'minecraft:block/block', 'ambientocclusion': False,
            'textures': {**TEX, 'particle': TEX['wood']}, 'elements': parts}

jar = ROOT / 'build/moddev/artifacts/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar'
with zipfile.ZipFile(jar) as vanilla:
    textures = [{'name': ref.split('/')[-1] + '.png', 'id': str(i),
                 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'tm_wagon/cabinet/' + ref)), 'width': 16, 'height': 16,
                 'source': 'data:image/png;base64,' + base64.b64encode(vanilla.read('assets/minecraft/textures/' + ref.split(':')[1] + '.png')).decode()}
                for i, ref in enumerate(TEX.values())]

def bb(parts, name, offset=0, installed=False, opened=False):
    result = []
    for p in parts:
        q = {'name': p['name'], 'type': 'cube', 'box_uv': False, 'autouv': 0,
             'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'tm_wagon/cabinet/' + name + '/' + p['name'])),
             'from': p['from'].copy(), 'to': p['to'].copy(),
             'faces': {f: {'texture': offset+list(TEX).index(v['texture'][1:]), 'uv': v['uv']} for f, v in p['faces'].items()}}
        if installed:
            for k in ['from', 'to']:
                q[k][1] += 24
                q[k][2] -= 40
                if opened and p['name'].startswith('left_'): q[k][0] -= 6.4
        result.append(q)
    return result

for size, half in [('single', 8.5), ('double', 15.5)]:
    groups = meshes(half)
    for label, parts in zip(['body', 'left', 'right'], groups):
        baked = copy.deepcopy(parts)
        for p in baked:
            for key in ['from', 'to']: p[key][0] += 8
            assert all(-16 <= n <= 32 for key in ['from', 'to'] for n in p[key]), 'Java model outside bake bounds'
        write(ASSETS / f'models/block/cabinet_{size}_{label}.json', model(baked))
    for extended in [False, True]:
        prefix = 'long_' if extended else ''
        source = ROOT / f'modeling/open_cargo_wagon/variants/{prefix}{size}_seat_single_horse/wagon.bbmodel'
        for opened in [False, True]:
            name = f'{prefix}{size}_cabinet_{"open" if opened else "closed"}'
            proj = json.loads(source.read_text(encoding='utf-8'))
            offset = len(proj['textures']);proj['textures'] += copy.deepcopy(textures)
            for i, tex in enumerate(proj['textures'][offset:]): tex['id'] = str(offset+i)
            elements = bb(sum(groups, []), name, offset, True, opened)
            proj['name'] = name;proj['elements'] += elements
            proj['outliner'].append({'name': 'wagon_cabinet', 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, name)),
                                     'children': [p['uuid'] for p in elements]})
            write(PROJECT / name / 'wagon.bbmodel', proj)

# A centered miniature cabinet item. Same item adapts when installed beneath either seat.
parts = copy.deepcopy(sum(meshes(8.5), []))
for p in parts:
    for key in ['from', 'to']:
        p[key][0] += 8;p[key][1] += 5;p[key][2] -= 3.16
item = model(parts)
item['display'] = {'gui': {'rotation': [25, 35, 0], 'translation': [0, 0, 0], 'scale': [.78, .78, .78]},
                   'ground': {'translation': [0, 2, 0], 'scale': [.45, .45, .45]},
                   'fixed': {'scale': [.7, .7, .7]},
                   'thirdperson_righthand': {'rotation': [65, 0, 0], 'scale': [.45, .45, .45]},
                   'firstperson_righthand': {'rotation': [0, -25, 0], 'scale': [.55, .55, .55]}}
write(ASSETS / 'models/item/wagon_cabinet.json', item)
elements = bb(parts, 'cabinet_item')
write(PROJECT / 'cabinet_item.bbmodel', {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
    'name': 'wagon_cabinet_item', 'resolution': {'width': 16, 'height': 16}, 'textures': textures,
    'display': item['display'], 'elements': elements, 'outliner': [p['uuid'] for p in elements]})
print('Exported six cabinet meshes, a miniature item and nine embedded-texture Blockbench projects.')
