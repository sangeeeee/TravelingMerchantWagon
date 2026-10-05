"""Publish authored pixel materials as static resources; no runtime generation.

Wood palettes and grain strokes are hand-authored in materials/wood_art.json.
This asset-only tool does not replace the currently active model textures.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/tm_wagon'
DEST = ASSETS / 'textures/component'
PREVIEW = ROOT / 'modeling/materials'
ART = json.loads((ROOT / 'tools/materials/wood_art.json').read_text(encoding='utf-8'))
COMPONENTS = {
    'cargo_body': ('货物车厢', 'Cargo Body', 'wagon_atlas', 0),
    'long_cargo_body': ('加长货物车厢', 'Extended Cargo Body', 'wagon_atlas', 2),
    'single_seat': ('单人带靠背座椅', 'Single Backrest Driver Seat', 'wagon_atlas', 1),
    'double_seat': ('双人带靠背座椅', 'Double Backrest Driver Seat', 'wagon_atlas', 3),
    'small_wheel': ('小车轮', 'Small Wheel', 'wagon_atlas', 4),
    'large_wheel': ('大车轮', 'Large Wheel', 'wagon_atlas', 6),
    'single_wooden_seat': ('单人木制座位', 'Single Wooden Driver Seat', 'wagon_atlas', 5),
    'double_wooden_seat': ('双人木制座位', 'Double Wooden Driver Seat', 'wagon_atlas', 7),
    'wide_cargo_body': ('加宽货物车厢', 'Wide Cargo Body', 'wagon_atlas', 2),
    'triple_seat': ('三人带靠背座椅', 'Triple Backrest Driver Seat', 'wagon_atlas', 3),
    'triple_wooden_seat': ('三人木制座位', 'Triple Wooden Driver Seat', 'wagon_atlas', 7),
    'wagon_stool': ('马车木凳', 'Wagon Stool', 'wood_tile', 8),
    'wagon_cabinet': ('马车柜', 'Wagon Cabinet', 'cabinet_atlas', 9),
}
WOOL_COMPONENTS = {'single_seat', 'double_seat', 'triple_seat'}
WAGON_LAYOUT = {
    'planks': [0, 0, 32, 32], 'bark': [32, 0, 16, 32],
    'end': [48, 0, 16, 16], 'cushion': [48, 16, 16, 16],
    'iron': [0, 32, 16, 16], 'spoke': [16, 32, 16, 16],
    'leather': [32, 32, 16, 16], 'light': [48, 32, 16, 16],
    'brass': [0, 48, 16, 16], 'rim': [16, 48, 16, 16],
    'stripped': [32, 48, 16, 16], 'end_dark': [48, 48, 16, 16],
}
CABINET_LAYOUT = {
    'planks': [0, 0, 16, 16], 'stripped': [16, 0, 16, 16],
    'interior': [0, 16, 16, 16], 'iron': [16, 16, 16, 16],
}
COLOURS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime',
           'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue',
           'brown', 'green', 'red', 'black']


def rgb(value):
    return tuple(int(value[i:i+2], 16) for i in (1, 3, 5))


def gray(value):
    return value, value, value


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


def save_texture(path, image):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.convert('RGBA').save(path, optimize=True)


def wood_tiles(spec, phase):
    plank = [rgb(c) for c in spec['planks']]
    bark = [rgb(c) for c in spec['bark']]
    stripped = [rgb(c) for c in spec['stripped']]
    p = Image.new('RGB', (16, 16), plank[2]); d = ImageDraw.Draw(p)
    # Horizontal plank courses with staggered joins, authored grain and knots.
    for row in range(4):
        y = row*4
        d.line((0, y, 15, y), fill=plank[0])
        d.line((0, y+1, 15, y+1), fill=plank[3])
        x = (3+row*7+phase) % 16
        d.line((x, y+1, x, y+3), fill=plank[1])
    for x0, y, x1, shade in spec['grain']:
        for x in range(x0, x1+1):
            d.point(((x+phase) % 16, y), fill=plank[shade])
    for x, y in spec['knots']:
        x = (x+phase) % 16
        d.line((max(0, x-1), y, min(15, x+1), y), fill=plank[1])
        d.point((x, y), fill=plank[0])
    b = Image.new('RGB', (16, 16), bark[2]); d = ImageDraw.Draw(b)
    for x, shade in enumerate(spec['bark_columns']):
        d.line((x, 0, x, 15), fill=bark[shade])
    for x0, y0, x1, y1, shade in spec['bark_patches']:
        d.rectangle((x0, y0, x1, y1), fill=bark[shade])
    s = Image.new('RGB', (16, 16), stripped[2]); d = ImageDraw.Draw(s)
    for x in range(16):
        d.line((x, 0, x, 15), fill=stripped[1 if x % 5 == 0 else 3 if x % 4 == 2 else 2])
    for x0, y, x1, shade in spec['grain']:
        x = (y+phase) % 16
        d.line((x, x0, x, x1), fill=stripped[shade])
    e = Image.new('RGB', (16, 16), stripped[2]); d = ImageDraw.Draw(e)
    d.rectangle((0, 0, 15, 15), outline=bark[1])
    for i, inset in enumerate(spec['rings']):
        d.rectangle((inset, inset, 15-inset, 15-inset), outline=stripped[1 if i % 2 == 0 else 3])
    d.rectangle((7, 6, 8, 8), fill=stripped[0])
    return {'planks': p, 'bark': b, 'stripped': s, 'end': e}


def shades(tile, multiplier):
    result = Image.new('RGB', tile.size)
    result.putdata([tuple(round(c*multiplier) for c in pixel) for pixel in tile.get_flattened_data()])
    return result


def fixed_tile(palette, vertical=False):
    colours = [rgb(c) for c in palette]
    im = Image.new('RGB', (16, 16), colours[1]); d = ImageDraw.Draw(im)
    if vertical:
        for x in [1, 5, 10, 14]:
            d.line((x, 0, x, 15), fill=colours[0 if x % 2 else 2])
    else:
        d.rectangle((0, 0, 15, 15), outline=colours[0])
        d.line((1, 1, 14, 1), fill=colours[2]);d.line((1, 2, 1, 14), fill=colours[2])
        d.rectangle((6, 7, 9, 8), fill=colours[0])
    return im


def cushion():
    im = Image.new('RGB', (16, 16), gray(239));d = ImageDraw.Draw(im)
    for y in range(0, 16, 4):
        for x in range(0, 16, 4):
            d.rectangle((x, y, x+2, y+1), fill=gray(248 if (x+y) % 8 == 0 else 231))
            d.line((x+1, y+2, x+3, y+2), fill=gray(222))
            d.point((x+3, y+3), fill=gray(243))
    return im


def cover():
    im = Image.new('RGB', (16, 16), gray(224));d = ImageDraw.Draw(im)
    for y, tone, x0, x1 in [(0,216,1,9),(2,231,5,14),(4,213,0,10),(6,233,3,12),
                            (8,219,4,15),(10,228,1,10),(12,214,0,8),(14,232,6,14)]:
        d.line((0,y,15,y),fill=gray(tone))
        d.line((x0,y+1,x1,y+1),fill=gray(min(250,tone+10)))
    for bounds,tone in [((1,4,5,5),182),((10,11,14,13),193),((6,8,8,10),205),((1,14,4,15),190)]:
        d.rectangle(bounds,fill=gray(tone))
    return im


def spiral(fabric):
    # A hand-stepped square spiral reads clearly on the coarse octagonal roll cap.
    im = fabric.copy();d = ImageDraw.Draw(im)
    points=[(15,13),(2,13),(2,2),(12,2),(12,10),(5,10),(5,5),(9,5),(9,7),(7,7)]
    d.line(points,fill=gray(151),width=1)
    return im


def cover_hem():
    # Two coarse cloth rows: a narrow crease beside each edge tie, widening below.
    # U follows the cargo row; the tie is at the row boundary (both tile ends).
    im = Image.new('RGB', (16, 2), gray(221));d = ImageDraw.Draw(im)
    for y in range(2):
        for x in range(16):
            distance = min(x,15-x)
            tone = ([173,205,239,222] if y == 0 else [162,189,226,239])[min(distance,3)]
            if distance > 3:tone = 216 if x % 4 == 0 else 227
            d.point((x,y),fill=gray(tone))
    return im


def canopy():
    im = Image.new('RGB', (16, 16), gray(238));d = ImageDraw.Draw(im)
    tones=[190,211,250,243,238,238,238,238,238,238,238,238,243,250,211,190]
    for y,tone in enumerate(tones):d.line((0,y,15,y),fill=gray(tone))
    for x in [2,6,10,14]:d.line((x,3,x,12),fill=gray(241))
    for x,y,tone in [(1,5,232),(8,7,246),(12,10,230),(5,11,244),(10,4,233),(3,9,246)]:
        d.point((x,y),fill=gray(tone))
    return im


def curtain():
    im = Image.new('RGB', (16, 16), gray(242));d = ImageDraw.Draw(im)
    # Each half-curtain uses this tile, mirrored: U increases away from the centre.
    for y in range(16):
        drift = [0,0,0,0,0,0,1,1,1,1,1,2,2,2,3,3][y]
        width = 1 if y < 8 else 2 if y < 14 else 3
        for start in [2,6,11]:
            x = min(15,start+drift)
            d.line((x,y,min(15,x+width-1),y),fill=gray(216-y*2))
            if x+width < 16:d.point((x+width,y),fill=gray(251))
    return im


def wagon_atlas(tiles, fabric):
    im = Image.new('RGB', (64, 64))
    for x in [0,16]:
        for y in [0,16]:im.paste(tiles['planks'],(x,y))
    for y in [0,16]:im.paste(tiles['bark'],(32,y))
    im.paste(tiles['end'],(48,0));im.paste(fabric,(48,16))
    im.paste(fixed_tile(['#262A2C','#383E41','#4B5255']),(0,32))
    im.paste(shades(tiles['planks'],.84),(16,32))
    im.paste(fixed_tile(['#47301F','#62452F','#78573B'],True),(32,32))
    im.paste(fixed_tile(['#B98740','#E8BC66','#FFE0A1']),(48,32))
    im.paste(fixed_tile(['#675232','#978054','#B29C71']),(0,48))
    im.paste(shades(tiles['planks'],.92),(16,48))
    im.paste(tiles['stripped'],(32,48));im.paste(shades(tiles['end'],.84),(48,48))
    return im


def cabinet_atlas(tiles):
    im = Image.new('RGB', (32,32))
    im.paste(tiles['planks'],(0,0));im.paste(tiles['stripped'],(16,0))
    im.paste(shades(tiles['planks'],.9),(0,16))
    im.paste(fixed_tile(['#101113','#191B1E','#25282B']),(16,16))
    return im


def preview(materials, dyes):
    PREVIEW.mkdir(parents=True,exist_ok=True)
    font_path = Path('C:/Windows/Fonts/msyh.ttc')
    font = ImageFont.truetype(str(font_path),16) if font_path.exists() else ImageFont.load_default()
    small = ImageFont.truetype(str(font_path),13) if font_path.exists() else ImageFont.load_default()
    width,height = 150+len(COMPONENTS)*140,1540
    sheet=Image.new('RGB',(width,height),'#E8E4DB');d=ImageDraw.Draw(sheet)
    d.text((12,12),f'10 woods × {len(COMPONENTS)} component textures — fixed PNG resources',fill='#2B2926',font=font)
    for col,(component,(name,*_)) in enumerate(COMPONENTS.items()):
        x=140+col*140
        d.text((x,40),name.replace('带靠背','\n带靠背'),fill='#2B2926',font=small)
    for row,(wood,spec) in enumerate(ART.items()):
        y=100+row*142
        d.text((10,y+35),spec['zh_name'],fill='#2B2926',font=font)
        d.text((10,y+60),wood,fill='#5B5750',font=small)
        for col,component in enumerate(COMPONENTS):
            with Image.open(materials[component][wood]) as im:
                sheet.paste(im.convert('RGB').resize((128,128),Image.Resampling.NEAREST),(140+col*140,y))
    sheet.save(PREVIEW/'wood-components.png',optimize=True)
    sheet=Image.new('RGB',(930,240),'#E8E4DB');d=ImageDraw.Draw(sheet)
    d.text((10,10),'Grayscale tint inputs — ropes, metal and wood are not dyed',fill='#2B2926',font=font)
    for i,(name,path) in enumerate(dyes.items()):
        x=10+i*184
        with Image.open(path) as im:sheet.paste(im.convert('RGB').resize((160,160),Image.Resampling.NEAREST),(x,40))
        d.text((x,207),name,fill='#2B2926',font=small)
    sheet.save(PREVIEW/'grayscale-fabrics.png',optimize=True)


def main():
    fabrics={'seat_cushion':cushion(),'cargo_cover_fabric':cover(),'cargo_cover_hem':cover_hem(),
             'canopy_cloth':canopy(),'canopy_curtain':curtain()}
    fabrics['cargo_cover_spiral']=spiral(fabrics['cargo_cover_fabric'])
    dyes={}
    for name,im in fabrics.items():
        size=16 if name=='seat_cushion' else 64
        p=DEST/f'dye/{name}.png'
        save_texture(p,im.resize((size,round(size*im.height/im.width)),Image.Resampling.NEAREST));dyes[name]=p
    components={};published={};assets=[]
    for component,(zh,en,layout,phase) in COMPONENTS.items():
        variants={};published[component]={}
        for wood,spec in ART.items():
            tiles=wood_tiles(spec,phase)
            im=tiles['planks'] if layout=='wood_tile' else cabinet_atlas(tiles) if layout=='cabinet_atlas' else wagon_atlas(tiles,fabrics['seat_cushion'])
            p=DEST/f'{component}/{wood}.png';save_texture(p,im)
            variants[wood]='tm_wagon:component/'+component+'/'+wood
            published[component][wood]=p
            assets.append({'path':str(p.relative_to(ASSETS)).replace('\\','/'),'size':list(im.size),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
        components[component]={'zh_name':zh,'en_name':en,'layout':layout,'textures':variants,
            'dyed_regions':['cushion'] if component in WOOL_COMPONENTS else []}
        if component in WOOL_COMPONENTS:components[component]['dye_texture']='tm_wagon:component/dye/seat_cushion'
    manifest={'format_version':1,'status':'static_component_assets',
        'wood_sources':{wood:{'zh_name':s['zh_name'],'en_name':s['en_name'],
            'planks':'minecraft:'+wood+'_planks',
            'log':'minecraft:'+wood+('_stem' if wood in ['crimson','warped'] else '_log'),
            'stripped_log':'minecraft:stripped_'+wood+('_stem' if wood in ['crimson','warped'] else '_log'),
            'trapdoor':'minecraft:'+wood+'_trapdoor'} for wood,s in ART.items()},
        'allow_bamboo':False,'allow_modded_wood_crafting':False,
        'layouts':{'wagon_atlas':{'size':[64,64],'regions':WAGON_LAYOUT},
                   'wood_tile':{'size':[16,16],'regions':{'planks':[0,0,16,16]}},
                   'cabinet_atlas':{'size':[32,32],'regions':CABINET_LAYOUT}},
        'components':components,
        'dyes':{'source':'crafting_wool_colour','colours':COLOURS,
                'textures':{name:'tm_wagon:component/dye/'+name for name in fabrics},
                'bindings':{'single_seat':['seat_cushion'],'double_seat':['seat_cushion'],'triple_seat':['seat_cushion'],
                            'wagon_cargo_cover':['cargo_cover_fabric','cargo_cover_spiral','cargo_cover_hem'],
                            'wagon_canopy':['canopy_cloth','canopy_curtain']},
                'never_tint':['iron','brass','leather','light','rope','canopy_wood','wood_regions']},
        'files':assets}
    write_json(ASSETS/'component_materials.json',manifest)
    preview(published,dyes)
    print(f'Published {len(assets)} authored wood component textures and {len(dyes)} grayscale dye textures.')


if __name__=='__main__':main()
