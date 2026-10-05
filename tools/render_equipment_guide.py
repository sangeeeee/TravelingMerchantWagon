"""Handbook equipment previews from the current render models and textures."""
from pathlib import Path
from array import array
import json
import math
import sys
from PIL import Image
sys.dont_write_bytecode = True
import render_assembly_guide as r

ASSETS = r.ASSETS
DEST = ASSETS / 'textures/gui/handbook/equipment'
REVIEW = r.ROOT / 'docs/handbook/equipment'


def native_mesh(name, origin, scale=(1, 1, 1), tint=(1, 1, 1)):
    """Apply the same model transforms as the accessory's client renderer."""
    model = json.loads((ASSETS / f'models/block/{name}.json').read_text(encoding='utf-8'))
    faces = []
    for cube in model['elements']:
        rotation = cube.get('rotation')
        chain = ()
        if rotation:
            angles = [0, 0, 0]
            angles['xyz'.index(rotation['axis'])] = rotation['angle']
            chain = ((rotation['origin'], angles),)
        for direction, (normal, selectors) in r.FACES.items():
            face = cube['faces'].get(direction)
            if face is None: continue
            norm = r.normalized(tuple(v/s for v, s in zip(r.transform(normal, chain, vector=True), scale)))
            if r.dot(norm, r.EYE) <= .001: continue
            points = []
            for selector in selectors:
                p = r.transform(tuple(cube['to'][i] if selector[i] else cube['from'][i] for i in range(3)), chain)
                points.append(tuple(origin[i]*16+p[i]*scale[i] for i in range(3)))
            tex_id = model['textures'][face['texture'].lstrip('#')]
            while tex_id.startswith('#'): tex_id = model['textures'][tex_id[1:]]
            namespace, path = tex_id.split(':', 1)
            assert namespace == 'tm_wagon', tex_id
            texture = ASSETS / f'textures/{path}.png'
            if texture not in r.TEXTURES: r.TEXTURES[texture] = Image.open(texture).convert('RGBA')
            u0, v0, u1, v1 = face['uv']
            w, h = r.TEXTURES[texture].size
            uv = [(u0*w/16, v0*h/16), (u1*w/16, v0*h/16), (u1*w/16, v1*h/16), (u0*w/16, v1*h/16)]
            turns = int(face.get('rotation', 0)/90) % 4
            if turns: uv = uv[-turns:]+uv[:-turns]
            if face.get('tintindex', -1) >= 0 and tint != (1, 1, 1):
                key = Path(str(texture)+'.tinted')
                if key not in r.TEXTURES:
                    im = r.TEXTURES[texture].copy()
                    im.putdata([(round(a*tint[0]), round(b*tint[1]), round(c*tint[2]), alpha)
                                for a, b, c, alpha in im.get_flattened_data()])
                    r.TEXTURES[key] = im
                texture = key
            faces.append(dict(points=points, normal=norm, uv=uv, texture=texture))
    return faces


def cabinet(opened):
    result = native_mesh('material/cabinet_oak_single_body', (-.5, 1.5, -2.5))
    result += native_mesh('material/cabinet_oak_single_left', (-.5-(.4 if opened else 0), 1.5, -2.5))
    result += native_mesh('material/cabinet_oak_single_right', (-.5, 1.5, -2.5))
    return result


def cover(open_rows):
    half_width, y, top, front, back = 1.1875, 2.359375, 2.375, -1.34375, 2.34375
    # Light grey wool, desaturated using FabricColours' .55 colour strength.
    dye = (157, 157, 151)
    grey = sum(v*w for v, w in zip(dye, (.2126, .7152, .0722)))
    tint = tuple(round(grey+(v-grey)*.55)/255 for v in dye)
    def boundary(row): return front if row == 0 else back if row == 5 else -.96-.35+row*.7
    result = []
    for row in range(open_rows, 5):
        start, end = boundary(row), boundary(row+1)
        result += native_mesh('cargo_cover_sheet', (-half_width, y, start), (half_width*2, 1, end-start), tint)
    result += native_mesh('cargo_cover_back_hem', (-half_width, y, back-.016), (half_width*2, 1, .016), tint)
    if open_rows:
        radius = (.055+.047*math.sqrt(open_rows))/math.sqrt(2)
        result += native_mesh('cargo_cover_roll', (-half_width, top, boundary(open_rows)-radius),
                              (half_width*2, radius*2, radius*2), tint)
    return result


def canopy(closed):
    base, front, back, thick = 2.28125, -1.34375, 2.203125, 1/64
    # The runtime white-wool tint is nearly white.
    tint = (.98, .998, .996)
    def boundary(row): return front if row == 0 else back if row == 5 else -.96-.35+row*.7
    result = []
    for row in range(5):
        start, end = boundary(row), boundary(row+1)
        result += native_mesh('canopy_shell', (-1, base, start), (2, 2, end-start), tint)
    for row in range(6):
        z = boundary(row)+(.065 if row == 0 else -.065 if row == 5 else 0)
        result += native_mesh('canopy_rib', (-1, base, z), (2, 2, 1), tint)
    for z in (front+thick/4, back-thick-thick/4):
        result += native_mesh('canopy_end', (-1, base, z), (2, 2, 1), tint)
    for z in (front+.0234375, back-5/1024-thick):
        result += native_mesh('canopy_curtain_'+('closed' if closed else 'open'), (-1, base, z), (2, 2, 1), tint)
    return result


def render(mesh):
    projected = [(r.dot(p, r.RIGHT), r.dot(p, r.UP)) for f in mesh for p in f['points']]
    xmin, xmax = min(x for x, _ in projected), max(x for x, _ in projected)
    ymin, ymax = min(y for _, y in projected), max(y for _, y in projected)
    r.SCALE = min((r.SIZE-90)/(xmax-xmin), (r.SIZE-100)/(ymax-ymin))
    r.CENTER = ((xmin+xmax)/2, (ymin+ymax)/2)
    image = Image.new('RGBA', (r.SIZE, r.SIZE))
    mask = Image.new('L', image.size)
    depth = array('f', [-math.inf])*(r.SIZE*r.SIZE)
    pixels, masks = image.load(), mask.load()
    for face in mesh:
        points = [r.project(p) for p in face['points']]
        texture = r.shade_texture(face['texture'], face['normal'])
        for ids in ((0, 1, 2), (0, 2, 3)):
            r.triangle(pixels, depth, masks, [points[i] for i in ids], [face['uv'][i] for i in ids], texture, False)
    return image


def main():
    DEST.mkdir(parents=True, exist_ok=True); REVIEW.mkdir(parents=True, exist_ok=True)
    base = [f for f in r.MESH if not f['group'].startswith('frame_')]
    examples = [('cabinet_closed', cabinet(False)), ('cabinet_open', cabinet(True)),
                ('cover_spread', cover(0)), ('cover_rolled', cover(2)),
                ('canopy_closed', canopy(True)), ('canopy_open', canopy(False))]
    sheet = Image.new('RGBA', (1200, 800), (245, 242, 232, 255))
    for i, (name, mesh) in enumerate(examples):
        image = render(base+mesh)
        image.save(REVIEW/f'{name}.png', optimize=True)
        page = Image.new('RGBA', (256, 256))
        page.alpha_composite(image.resize((200, 200), Image.Resampling.LANCZOS))
        page.save(DEST/f'{name}.png', optimize=True)
        sheet.alpha_composite(image.resize((400, 400), Image.Resampling.LANCZOS), ((i//2)*400, (i%2)*400))
        print(name, 'rendered from current accessory models', flush=True)
    sheet.save(REVIEW/'equipment_overview.png', optimize=True)


if __name__ == '__main__': main()
