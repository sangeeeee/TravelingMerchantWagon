"""Render assembly illustrations from the existing Blockbench meshes and game textures.

No generated concept geometry: all cuboids, pivots and UVs come from the source
models. A small orthographic, textured depth-buffer renderer keeps the six views
identical. Pillow is used for raster output and handbook-page sizing only.
"""
from pathlib import Path
from array import array
import json
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'versions/mc-1.21.1/common/src/main/resources/assets/tm_wagon'
OUTPUT = ROOT / 'docs/handbook/assembly'
BOOK = ASSETS / 'textures/gui/handbook/assembly'
MODEL = ROOT / 'shared-assets/modeling/open_cargo_wagon/variants/single_wooden_seat_single_horse/wagon.bbmodel'
FRAME = ROOT / 'shared-assets/modeling/wagon_assembly_frame/wagon_assembly_frame.bbmodel'
SIZE = 800
CYAN = (30, 164, 190, 255)


def dot(a, b): return sum(x*y for x, y in zip(a, b))
def subtract(a, b): return tuple(x-y for x, y in zip(a, b))
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def normalized(v):
    length = math.sqrt(dot(v, v))
    return tuple(x/length for x in v)


EYE = normalized((-6, 6.2, -8))  # wagon's front-left, above the assembly
RIGHT = normalized(cross((0, 1, 0), EYE))
UP = cross(EYE, RIGHT)
LIGHT = normalized((-3, 8, -5))


def rotate(vector, angles):
    x, y, z = vector
    for axis, angle in enumerate(angles):
        c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        if axis == 0: y, z = y*c-z*s, y*s+z*c
        elif axis == 1: x, z = x*c+z*s, -x*s+z*c
        else: x, y = x*c-y*s, x*s+y*c
    return (x, y, z)


def transform(point, transforms, vector=False):
    for pivot, angles in transforms:
        point = rotate(point if vector else subtract(point, pivot), angles)
        if not vector: point = tuple(a+b for a, b in zip(point, pivot))
    return point


# Face corners are ordered as a UV rectangle. Winding is independent of lighting.
FACES = {
    'north': ((0, 0, -1), [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)]),
    'south': ((0, 0, 1), [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)]),
    'west': ((-1, 0, 0), [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)]),
    'east': ((1, 0, 0), [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)]),
    'up': ((0, 1, 0), [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)]),
    'down': ((0, -1, 0), [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)]),
}


def texture(group):
    component = {'seat': 'single_wooden_seat', 'front_left_wheel': 'small_wheel',
                 'front_right_wheel': 'small_wheel', 'rear_left_wheel': 'large_wheel',
                 'rear_right_wheel': 'large_wheel'}.get(group, 'cargo_body')
    if group.startswith('frame_'): path = ASSETS / 'textures/block/wagon_assembly_frame.png'
    elif group == 'shafts': path = ASSETS / 'textures/entity/wagon.png'
    else: path = ASSETS / f'textures/component/{component}/oak.png'
    return path


def load_mesh(path):
    model = json.loads(path.read_text(encoding='utf-8'))
    cubes = {c['uuid']: c for c in model['elements']}
    faces = []
    def walk(children, ancestors=(), group=''):
        for child in children:
            if isinstance(child, dict):
                walk(child['children'], ancestors+((child.get('origin', [0, 0, 0]), child.get('rotation', [0, 0, 0])),), child['name'])
                continue
            cube = cubes[child]
            if not cube.get('visibility', True): continue
            chain = ((cube['origin'], cube.get('rotation', [0, 0, 0])),) + tuple(reversed(ancestors))
            for name, (normal, selectors) in FACES.items():
                face = cube['faces'].get(name)
                if face is None or face.get('texture') is None: continue
                points = [transform(tuple(cube['to'][i] if selector[i] else cube['from'][i] for i in range(3)), chain) for selector in selectors]
                norm = normalized(transform(normal, chain, vector=True))
                if dot(norm, EYE) <= .001: continue
                u0, v0, u1, v1 = face['uv']
                uv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
                turns = int(face.get('rotation', 0)/90) % 4
                uv = uv[-turns:] + uv[:-turns] if turns else uv
                faces.append({'points': points, 'normal': norm, 'uv': uv, 'group': group, 'texture': texture(group)})
    walk(model['outliner'])
    return faces


MESH = load_mesh(FRAME) + load_mesh(MODEL)
TEXTURES = {p: Image.open(p).convert('RGBA') for p in {f['texture'] for f in MESH}}
SHADES = {}
projected = [(dot(p, RIGHT), dot(p, UP)) for f in MESH for p in f['points']]
MIN_X, MAX_X = min(x for x, _ in projected), max(x for x, _ in projected)
MIN_Y, MAX_Y = min(y for _, y in projected), max(y for _, y in projected)
SCALE = min((SIZE-110)/(MAX_X-MIN_X), (SIZE-160)/(MAX_Y-MIN_Y))
CENTER = ((MIN_X+MAX_X)/2, (MIN_Y+MAX_Y)/2)


def project(point):
    return (SIZE/2+(dot(point, RIGHT)-CENTER[0])*SCALE,
            SIZE/2-25-(dot(point, UP)-CENTER[1])*SCALE, dot(point, EYE))


def shade_texture(path, normal):
    shade = round(min(1, .55+.3*max(0, normal[1])+.22*max(0, dot(normal, LIGHT))), 2)
    key = (path, shade)
    if key not in SHADES:
        im = TEXTURES[path]
        colours = im.get_flattened_data() if hasattr(im, 'get_flattened_data') else im.getdata()
        SHADES[key] = (im.size, [(round(r*shade), round(g*shade), round(b*shade), a) for r, g, b, a in colours])
    return SHADES[key]


def triangle(image, depth, mask, points, uv, tex, highlighted):
    (x0,y0,z0), (x1,y1,z1), (x2,y2,z2) = points
    determinant = (y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
    if abs(determinant) < 1e-8: return
    left, right = max(0, math.floor(min(x0,x1,x2))), min(SIZE-1, math.ceil(max(x0,x1,x2)))
    top, bottom = max(0, math.floor(min(y0,y1,y2))), min(SIZE-1, math.ceil(max(y0,y1,y2)))
    (width, height), colours = tex
    inv = 1/determinant
    for y in range(top, bottom+1):
        yy = y+.5
        for x in range(left, right+1):
            xx = x+.5
            a = ((y1-y2)*(xx-x2)+(x2-x1)*(yy-y2))*inv
            b = ((y2-y0)*(xx-x2)+(x0-x2)*(yy-y2))*inv
            c = 1-a-b
            if min(a,b,c) < -1e-6: continue
            z = a*z0+b*z1+c*z2
            index = y*SIZE+x
            if z < depth[index]-1e-5: continue
            u = a*uv[0][0]+b*uv[1][0]+c*uv[2][0]
            v = a*uv[0][1]+b*uv[1][1]+c*uv[2][1]
            colour = colours[min(height-1,max(0,int(v)))*width+min(width-1,max(0,int(u)))]
            if colour[3] < 128: continue
            depth[index] = z
            image[x,y] = colour
            mask[x,y] = 255 if highlighted else 0


def line(draw, start, end, colour, width=3, dashed=False):
    if not dashed: draw.line([start,end], fill=colour, width=width); return
    length = math.dist(start,end)
    for i in range(0, int(length), 13):
        a, b = i/max(1,length), min(1,(i+7)/max(1,length))
        draw.line([(start[0]+(end[0]-start[0])*a,start[1]+(end[1]-start[1])*a),
                   (start[0]+(end[0]-start[0])*b,start[1]+(end[1]-start[1])*b)], fill=colour, width=width)


def arrow(draw, start, end, colour=CYAN, width=4):
    line(draw,start,end,colour,width)
    angle = math.atan2(end[1]-start[1],end[0]-start[0])
    draw.polygon([end,(end[0]-16*math.cos(angle-.45),end[1]-16*math.sin(angle-.45)),
                  (end[0]-16*math.cos(angle+.45),end[1]-16*math.sin(angle+.45))], fill=colour)


def render(groups, new, stage):
    im = Image.new('RGBA', (SIZE,SIZE))
    mask = Image.new('L', im.size)
    depth = array('f', [-math.inf])*(SIZE*SIZE)
    pix, highlights = im.load(), mask.load()
    for face in MESH:
        group = face['group']
        if group not in groups: continue
        projected = [project(p) for p in face['points']]
        tex = shade_texture(face['texture'], face['normal'])
        for ids in [(0,1,2),(0,2,3)]:
            triangle(pix,depth,highlights,[projected[i] for i in ids],[face['uv'][i] for i in ids],tex,group in new)
    # Only the visible silhouette is outlined, rather than every decorative cube.
    outline = ImageChops.subtract(mask.filter(ImageFilter.MaxFilter(7)), mask)
    im.alpha_composite(Image.composite(Image.new('RGBA',im.size,CYAN),Image.new('RGBA',im.size),outline))
    draw = ImageDraw.Draw(im)
    if stage in (4,5):
        # The right-side wheel is occluded by the body from this fixed left-front view.
        # A dashed ring identifies its true location without moving it for visibility.
        z, radius = (-20,10.5) if stage==4 else (20,13.5)
        ring = [project((21,radius+radius*math.sin(i*math.tau/16),z+radius*math.cos(i*math.tau/16)))[:2] for i in range(17)]
        for a,b in zip(ring,ring[1:]): line(draw,a,b,CYAN,3,True)
        for x,y,far in [(-21,radius,False),(21,radius,True)]:
            end = project((x,y,z))[:2]
            start = (end[0]+(85 if far else -85),end[1]+85)
            arrow(draw,start,end)
    else:
        targets = [(0,22,0),(0,24,2),(0,31.5,-30),(0,20,-47)]
        end = project(targets[stage])[:2]
        arrow(draw,(end[0]-30,end[1]-85),end)
    return im


STEPS = [
    ('01_frame','放置展开的装配架','普通右键放置；平台应处于升起状态。'),
    ('02_body','放置货物车厢','手持车厢，右键装配架顶面。'),
    ('03_seat','安装单人木制座位','对准车头脚板上方的安装位置。'),
    ('04_shafts','安装单马辕','对准车厢前方正中间、脚板下方。'),
    ('05_front_wheels','安装两个小前轮','前轮轴两侧各一个；虚线标出另一侧。'),
    ('06_rear_wheels','安装两个大后轮','后轮轴两侧各一个；虚线标出另一侧。'),
]


def main():
    OUTPUT.mkdir(parents=True,exist_ok=True); BOOK.mkdir(parents=True,exist_ok=True)
    frame = {f['group'] for f in MESH if f['group'].startswith('frame_')}
    body = {'chassis','cargo_body','tailgate','front_axle','rear_axle','driver_platform'}
    additions = [frame,body,{'seat'},{'shafts'},{'front_left_wheel','front_right_wheel'},{'rear_left_wheel','rear_right_wheel'}]
    installed = set()
    font_path = Path('C:/Windows/Fonts/msyh.ttc')
    font = ImageFont.truetype(str(font_path),23)
    small = ImageFont.truetype(str(font_path),17)
    sheet = Image.new('RGB',(1440,1160),(245,242,232)); layout=ImageDraw.Draw(sheet)
    layout.text((35,22),'马车装配示意 · 左前上方视角',font=ImageFont.truetype(str(font_path),32),fill=(55,45,32))
    layout.text((35,70),'青色轮廓 = 本步新增部件    虚线 = 被遮挡的另一侧轮子    车头朝左下方',font=small,fill=(80,77,68))
    for i,(name,title,caption) in enumerate(STEPS):
        installed |= additions[i]
        image = render(installed,additions[i],i)
        image.save(OUTPUT/f'{name}.png',optimize=True)
        # Patchouli image pages display the upper-left 200x200 region of a 256px texture.
        page = Image.new('RGBA',(256,256)); page.alpha_composite(image.resize((200,200),Image.Resampling.LANCZOS),(0,0))
        page.save(BOOK/f'{name}.png',optimize=True)
        x,y = 20+(i%3)*475,115+(i//3)*495
        layout.rounded_rectangle((x,y,x+460,y+480),radius=12,fill=(255,253,245),outline=(214,208,190),width=2)
        layout.text((x+18,y+16),f'{i+1:02d}  {title}',font=font,fill=(55,45,32))
        thumbnail = image.resize((400,400),Image.Resampling.LANCZOS)
        sheet.paste(thumbnail,(x+30,y+50),thumbnail)
        layout.text((x+15,y+450),caption,font=small,fill=(80,77,68))
        print(name,'rendered from',len([f for f in MESH if f['group'] in installed]),'visible model faces',flush=True)
    layout.text((35,1110),'安装齐全后：右键装配架，等待平台收缩，马车将转为实体。',font=font,fill=(55,45,32))
    sheet.save(OUTPUT/'assembly_overview_zh_cn.png',optimize=True)
    (OUTPUT/'manifest.json').write_text(json.dumps({'view':{'eye':EYE,'projection':'orthographic','fixed_scale':SCALE},
        'model':str(MODEL.relative_to(ROOT)).replace('\\','/'),'steps':[{'image':n+'.png','title':t,'caption':c,
        'book_texture':f'tm_wagon:textures/gui/handbook/assembly/{n}.png'} for n,t,c in STEPS]},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


if __name__ == '__main__': main()
