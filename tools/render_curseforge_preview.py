"""Render nine real wagon assemblies for the CurseForge gallery.

Uses the current Blockbench geometry and runtime accessory meshes. UV repetition,
fabric tint, and accessory placement follow the game's rendering rules. Requires
Pillow; does not change game assets. Output is limited to 2,000,000 bytes.
"""
from pathlib import Path
from array import array
import json
import math
import sys
from PIL import Image, ImageDraw, ImageFilter

sys.dont_write_bytecode = True
import render_assembly_guide as r

ROOT = r.ROOT
ASSETS = r.ASSETS
DEST = ROOT / 'publishing'
VARIANTS = ROOT / 'shared-assets/modeling/open_cargo_wagon/variants'
LIMIT = 2_000_000
EXAMPLES = [
    dict(body='standard', seat='single_wooden_seat', horses='single', wood='oak', roof='none',
         cabinet=True, title='Oak utility wagon', caption='Wooden bench / single-horse shafts'),
    dict(body='standard', seat='double_seat', horses='double', wood='spruce', roof='cover',
         colour='cyan', open_rows=2, cushion='light_blue', title='Spruce covered wagon', caption='Two-seat backrest bench / partly rolled cover'),
    dict(body='standard', seat='double_seat', horses='double', wood='birch', roof='canopy',
         colour='white', cushion='red', cabinet=True, title='Birch canvas wagon', caption='Two-seat backrest bench / open curtains'),
    dict(body='extended', seat='double_wooden_seat', horses='double', wood='acacia', roof='none',
         cabinet=True, drawer=True, title='Acacia merchant wagon', caption='Wooden bench / under-seat cabinet'),
    dict(body='extended', seat='double_seat', horses='double', wood='dark_oak', roof='cover',
         colour='red', open_rows=0, cushion='brown', title='Dark oak cargo wagon', caption='Two-seat backrest bench / closed cover'),
    dict(body='extended', seat='single_seat', horses='single', wood='mangrove', roof='canopy',
         colour='light_blue', cushion='white', closed=True, title='Mangrove touring wagon', caption='Single backrest seat / closed front curtains'),
    dict(body='wide', seat='triple_wooden_seat', horses='double', wood='cherry', roof='none',
         cabinet=True, title='Cherry wide-bed wagon', caption='Three-seat wooden bench / under-seat cabinet'),
    dict(body='wide', seat='triple_seat', horses='double', wood='warped', roof='cover',
         colour='light_gray', open_rows=3, cushion='light_gray', title='Warped covered wagon', caption='Three-seat backrest bench / partly rolled cover'),
    dict(body='wide', seat='triple_seat', horses='double', wood='oak', seat_wood='spruce', wheel_wood='dark_oak',
         roof='canopy', colour='white', cushion='blue', cabinet=True,
         title='Mixed-wood caravan', caption='Three-seat backrest bench / open curtains'),
]
DYES = dict(white=16383998, light_blue=3847130, light_gray=10329495,
            cyan=1481884, blue=3949738, brown=8606770, red=11546150)


def tint(name):
    value = DYES[name]
    rgb = tuple((value >> shift) & 255 for shift in (16, 8, 0))
    grey = sum(c * w for c, w in zip(rgb, (.2126, .7152, .0722)))
    return tuple(math.floor(grey + (c - grey) * .55 + .5) / 255 for c in rgb)


def register_texture(path, dye=None, cushion=False):
    key = (str(path), dye, cushion)
    if key not in r.TEXTURES:
        image = Image.open(path).convert('RGBA')
        if dye:
            rgb = tint(dye)
            pixels = image.load()
            bounds = (48, 16, 64, 32) if cushion else (0, 0, *image.size)
            for y in range(bounds[1], bounds[3]):
                for x in range(bounds[0], bounds[2]):
                    red, green, blue, alpha = pixels[x, y]
                    pixels[x, y] = (round(red * rgb[0]), round(green * rgb[1]), round(blue * rgb[2]), alpha)
        r.TEXTURES[key] = image
    return key


def bilerp(points, u, v):
    return tuple((1-u)*(1-v)*a + u*(1-v)*b + u*v*c + (1-u)*v*d
                 for a, b, c, d in zip(*points))


def tiled_faces(points, normal, uv, texture, reference):
    """Repeat the original face UV rectangle, clipping only the final repeat."""
    ru = max(1e-6, math.dist(points[0], points[1]) / max(1e-6, math.dist(reference[0], reference[1])))
    rv = max(1e-6, math.dist(points[0], points[3]) / max(1e-6, math.dist(reference[0], reference[3])))
    result = []
    for v in range(math.ceil(rv - 1e-7)):
        for u in range(math.ceil(ru - 1e-7)):
            du, dv = min(1, ru-u), min(1, rv-v)
            corners = [(u/ru, v/rv), ((u+du)/ru, v/rv), ((u+du)/ru, (v+dv)/rv), (u/ru, (v+dv)/rv)]
            tex_corners = [(0, 0), (du, 0), (du, dv), (0, dv)]
            result.append(dict(points=[bilerp(points, *p) for p in corners], normal=normal,
                               uv=[bilerp(uv, *p) for p in tex_corners], texture=texture))
    return result


def cubes_by_group(model):
    cubes = {c['uuid']: c for c in model['elements']}
    result = {}
    def walk(children, group=''):
        for child in children:
            if isinstance(child, dict):
                walk(child['children'], child['name'])
            else:
                result[(group, cubes[child]['name'])] = cubes[child]
    walk(model['outliner'])
    return result


def base_mesh(example):
    prefix = dict(standard='', extended='long_', wide='wide_')[example['body']]
    variant = prefix + example['seat'] + '_' + example['horses'] + '_horse'
    model = json.loads((VARIANTS / variant / 'wagon.bbmodel').read_text(encoding='utf8'))
    # The ordinary body and two-person seats define runtime texel density.
    reference_seat = example['seat'].replace('triple', 'double')
    ref_model = json.loads((VARIANTS / (reference_seat + '_' + example['horses'] + '_horse') / 'wagon.bbmodel').read_text(encoding='utf8'))
    references = cubes_by_group(ref_model)
    cubes = {c['uuid']: c for c in model['elements']}
    faces = []
    body_component = dict(standard='cargo_body', extended='long_cargo_body', wide='wide_cargo_body')[example['body']]
    def walk(children, ancestors=(), group=''):
        for child in children:
            if isinstance(child, dict):
                walk(child['children'], ancestors + ((child.get('origin', [0, 0, 0]), child.get('rotation', [0, 0, 0])),), child['name'])
                continue
            cube = cubes[child]
            if not cube.get('visibility', True):
                continue
            chain = ((cube['origin'], cube.get('rotation', [0, 0, 0])),) + tuple(reversed(ancestors))
            if group == 'shafts':
                path = ASSETS / 'textures/entity/wagon.png'
                texture = register_texture(path)
            else:
                component = example['seat'] if group == 'seat' else 'small_wheel' if group.startswith('front_') and group.endswith('wheel') else 'large_wheel' if group.startswith('rear_') and group.endswith('wheel') else body_component
                wood = example.get('seat_wood', example['wood']) if group == 'seat' else example.get('wheel_wood', example['wood']) if group.endswith('wheel') else example['wood']
                path = ASSETS / f'textures/component/{component}/{wood}.png'
                texture = register_texture(path, example.get('cushion') if group == 'seat' else None, True)
            label = 'left_wool_cushion' if cube['name'].startswith('triple_wool_cushion_') else cube['name']
            reference_cube = references.get((group, label), cube)
            for direction, (normal, selectors) in r.FACES.items():
                face = cube['faces'].get(direction)
                if face is None or face.get('texture') is None:
                    continue
                normal = r.normalized(r.transform(normal, chain, vector=True))
                if r.dot(normal, r.EYE) <= .001:
                    continue
                points = [r.transform(tuple(cube['to'][i] if p[i] else cube['from'][i] for i in range(3)), chain) for p in selectors]
                reference = [r.transform(tuple(reference_cube['to'][i] if p[i] else reference_cube['from'][i] for i in range(3)), chain) for p in selectors]
                u0, v0, u1, v1 = face['uv']
                uv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
                turns = int(face.get('rotation', 0)/90) % 4
                if turns:
                    uv = uv[-turns:] + uv[:-turns]
                faces.extend(tiled_faces(points, normal, uv, texture, reference))
    walk(model['outliner'])
    return faces


def native_mesh(name, origin, scale=(1, 1, 1), reference_scale=None, dye=None):
    model = json.loads((ASSETS / f'models/block/{name}.json').read_text(encoding='utf8'))
    reference_scale = reference_scale or scale
    faces = []
    for cube in model['elements']:
        chain = ()
        if rotation := cube.get('rotation'):
            angles = [0, 0, 0]
            angles['xyz'.index(rotation['axis'])] = rotation['angle']
            chain = ((rotation['origin'], angles),)
        for direction, (normal, selectors) in r.FACES.items():
            face = cube['faces'].get(direction)
            if face is None:
                continue
            normal = r.normalized(tuple(v/s for v, s in zip(r.transform(normal, chain, vector=True), scale)))
            if r.dot(normal, r.EYE) <= .001:
                continue
            points, reference = [], []
            for selector in selectors:
                point = r.transform(tuple(cube['to'][i] if selector[i] else cube['from'][i] for i in range(3)), chain)
                points.append(tuple(origin[i]*16 + point[i]*scale[i] for i in range(3)))
                reference.append(tuple(point[i]*reference_scale[i] for i in range(3)))
            identifier = model['textures'][face['texture'].lstrip('#')]
            while identifier.startswith('#'):
                identifier = model['textures'][identifier[1:]]
            namespace, path = identifier.split(':', 1)
            assert namespace == 'tm_wagon'
            texture = register_texture(ASSETS / f'textures/{path}.png', dye if face.get('tintindex', -1) >= 0 else None)
            w, h = r.TEXTURES[texture].size
            u0, v0, u1, v1 = face['uv']
            uv = [(u0*w/16, v0*h/16), (u1*w/16, v0*h/16), (u1*w/16, v1*h/16), (u0*w/16, v1*h/16)]
            turns = int(face.get('rotation', 0)/90) % 4
            if turns:
                uv = uv[-turns:] + uv[:-turns]
            faces.extend(tiled_faces(points, normal, uv, texture, reference))
    return faces


def equipment_mesh(example):
    wide = example['body'] == 'wide'
    rows = 8 if wide else 6 if example['body'] == 'extended' else 5
    width = 1.75 if wide else 1
    first = -(rows-1)*.35 if wide else -.96
    front_offset = first + 1.05 if wide else 0
    rear_extension = -first-1.8 if wide else .7 if rows == 6 else 0
    front = -1.34375 + front_offset
    result = []
    if example.get('cabinet'):
        size = 'single' if example['seat'].startswith('single') else 'double'
        cabinet_width = width if example['seat'].startswith('triple') else 1
        for part in ('body', 'left', 'right'):
            opened = -.4 if part == 'left' and example.get('drawer') else 0
            result += native_mesh(f'material/cabinet_{example["wood"]}_{size}_{part}',
                                  (-.5*cabinet_width + opened, 1.5, -2.5 + front_offset),
                                  (cabinet_width, 1, 1), (1, 1, 1))
    if example['roof'] == 'cover':
        opened = example['open_rows']
        half, y, top, back = 1.1875*width, 2.359375, 2.375, 2.34375 + rear_extension
        def boundary(row):
            return front if row <= 0 else back if row >= rows else first-.35+row*.7
        for row in range(opened, rows):
            start, end = boundary(row), boundary(row+1)
            reference_depth = .73375 if row == 0 else .85375 if row == rows-1 else .7
            result += native_mesh('cargo_cover_sheet', (-half, y, start), (half*2, 1, end-start),
                                  (2.375, 1, reference_depth), example['colour'])
        if opened < rows:
            result += native_mesh('cargo_cover_back_hem', (-half, y, back-.016), (half*2, 1, .016),
                                  (2.375, 1, .016), example['colour'])
        if opened:
            radius = (.055+.047*math.sqrt(opened))/math.sqrt(2)
            z = back-radius if opened == rows else boundary(opened)
            result += native_mesh('cargo_cover_roll', (-half, top, z-radius), (half*2, radius*2, radius*2),
                                  (2.375, radius*2, radius*2), example['colour'])
    elif example['roof'] == 'canopy':
        base, back, thick = 2.28125, 2.203125+rear_extension, 1/64
        def boundary(row):
            return front if row == 0 else back if row == rows else first-.35+row*.7
        def append(name, z, depth=1, reference_depth=1):
            return native_mesh(name, (-width, base, z), (2*width, 2, depth),
                               (2, 2, reference_depth), example['colour'])
        for row in range(rows):
            start, end = boundary(row), boundary(row+1)
            ref_depth = .73375 if row == 0 else .713125 if row == rows-1 else .7
            result += append('canopy_shell', start, end-start, ref_depth)
        for row in range(rows+1):
            result += append('canopy_rib', boundary(row)+(.065 if row == 0 else -.065 if row == rows else 0))
        for z in (front+thick/4, back-thick-thick/4):
            result += append('canopy_end', z)
        result += append('canopy_curtain_'+('closed' if example.get('closed') else 'open'), front+.0234375)
        result += append('canopy_curtain_open', back-5/1024-thick)
    return result


def render(mesh):
    image = Image.new('RGBA', (r.SIZE, r.SIZE))
    mask = Image.new('L', image.size)
    depth = array('f', [-math.inf])*(r.SIZE*r.SIZE)
    pixels, masks = image.load(), mask.load()
    for face in mesh:
        points = [r.project(p) for p in face['points']]
        texture = r.shade_texture(face['texture'], face['normal'])
        for ids in ((0, 1, 2), (0, 2, 3)):
            r.triangle(pixels, depth, masks, [points[i] for i in ids], [face['uv'][i] for i in ids], texture, False)
    return image.crop((0, 175, r.SIZE, 825)).resize((736, 478), Image.Resampling.LANCZOS)


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    meshes = [base_mesh(e) + equipment_mesh(e) for e in EXAMPLES]
    projected = [(r.dot(p, r.RIGHT), r.dot(p, r.UP)) for mesh in meshes for face in mesh for p in face['points']]
    xmin, xmax = min(p[0] for p in projected), max(p[0] for p in projected)
    ymin, ymax = min(p[1] for p in projected), max(p[1] for p in projected)
    r.SIZE = 1000
    r.SCALE = min(920/(xmax-xmin), 600/(ymax-ymin))
    r.CENTER = ((xmin+xmax)/2, (ymin+ymax)/2)
    sheet = Image.new('RGB', (2400, 1600), (239, 235, 223))
    draw = ImageDraw.Draw(sheet)
    for i, (example, mesh) in enumerate(zip(EXAMPLES, meshes)):
        x, y = 48+(i%3)*782, 48+(i//3)*511
        draw.rounded_rectangle((x, y, x+740, y+482), radius=18, fill=(249, 247, 240), outline=(215, 212, 198), width=2)
        image = render(mesh)
        shadow = Image.new('RGBA', image.size)
        ImageDraw.Draw(shadow).ellipse((80, 349, 671, 437), fill=(41, 44, 32, 22))
        shadow = shadow.filter(ImageFilter.GaussianBlur(15))
        sheet.paste(shadow, (x+2, y+2), shadow)
        sheet.paste(image, (x+2, y+2), image)
        print(f'{i+1}/9: {example["title"]} ({len(mesh)} textured faces)', flush=True)
    output = DEST / 'curseforge-wagon-assemblies.png'
    sheet.save(output, optimize=True, compress_level=9)
    if output.stat().st_size >= LIMIT:
        # Quantize a delivery copy only; geometry, source textures and rendered canvas remain untouched.
        for colours in (256, 192, 128):
            sheet.quantize(colors=colours, method=Image.Quantize.MEDIANCUT).save(output, optimize=True, compress_level=9)
            if output.stat().st_size < LIMIT:
                break
    assert output.stat().st_size < LIMIT, 'Preview exceeds the upload size limit'
    manifest = dict(image=output.name, size=list(sheet.size), bytes=output.stat().st_size,
                    projection='orthographic', camera=list(r.EYE), same_scale=True, examples=EXAMPLES)
    (DEST / 'curseforge-wagon-assemblies.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf8')
    print(f'Saved {output}: {sheet.width} x {sheet.height}, {output.stat().st_size:,} bytes', flush=True)


if __name__ == '__main__':
    main()
