"""Check native Minecraft GUI captures at normal and enlarged inventory scale."""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
image = Image.open(ROOT/'run/screenshots/stage-one-items.png').convert('RGB')
assert image.size == (1200,800), 'Run the smoke client with its default window size'
background = {(237,230,215),(208,197,175),(92,76,56)}
names = ['frame','cargo_body','single_horse_shafts','double_horse_shafts',
         'single_seat','double_seat','small_wheel','large_wheel','icon']
report = []
for index,name in enumerate(names):
    x,y = (100+(index%3)*200)*2,(60+(index//3)*100)*2
    for kind,rect in [('enlarged',(x-48,y,x+48,y+96)),
                      ('normal',(x-16,y+104,x+16,y+136))]:
        left,top,right,bottom = rect
        pixels = [(px,py) for py in range(top-2,bottom+2) for px in range(left-2,right+2)
                  if image.getpixel((px,py)) not in background]
        assert pixels, f'{name} has no visible model at {kind} scale'
        bounds = [min(p[0] for p in pixels),min(p[1] for p in pixels),
                  max(p[0] for p in pixels)+1,max(p[1] for p in pixels)+1]
        assert bounds[0]>=left and bounds[1]>=top and bounds[2]<=right and bounds[3]<=bottom, \
            f'{name} overflows the {kind} inventory cell: {bounds}, expected {rect}'
        offset = [((bounds[0]+bounds[2])/2-(left+right)/2)/(right-left),
                  ((bounds[1]+bounds[3])/2-(top+bottom)/2)/(bottom-top)]
        assert max(map(abs,offset))<.16, f'{name} is visibly off-center: {offset}'
        report.append({'item':name,'scale':kind,'pixel_bounds':bounds,'center_offset_fraction':offset})
# The new compartment is previewed separately to keep the original nine-cell grid stable.
extended = Image.open(ROOT/'run/screenshots/wagon-extended-cargo.png').convert('RGB')
assert extended.size == (1200,800)
for kind,rect in [('enlarged',(552,80,648,176)),('normal',(660,112,692,144))]:
    left,top,right,bottom = rect
    pixels = [(x,y) for y in range(top-2,bottom+2) for x in range(left-2,right+2)
              if extended.getpixel((x,y)) not in background]
    assert pixels, f'Extended compartment has no visible model at {kind} scale'
    bounds = [min(x for x,y in pixels),min(y for x,y in pixels),
              max(x for x,y in pixels)+1,max(y for x,y in pixels)+1]
    assert bounds[0]>=left and bounds[1]>=top and bounds[2]<=right and bounds[3]<=bottom, \
        f'Extended compartment overflows the {kind} inventory cell: {bounds}'
    offset = [((bounds[0]+bounds[2])/2-(left+right)/2)/(right-left),
              ((bounds[1]+bounds[3])/2-(top+bottom)/2)/(bottom-top)]
    assert max(map(abs,offset))<.16, f'Extended compartment is off-center: {offset}'
    report.append({'item':'long_cargo_body','scale':kind,'pixel_bounds':bounds,'center_offset_fraction':offset})

target = ROOT/'build/item-render-verification.json'
target.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(f'All {len(names)+1} items fit their normal and enlarged inventory cells and are centered.')
