"""Generate editable, centred wide wagons; wheels and shafts retain their original size."""
import copy
import json
from pathlib import Path
import shutil
import uuid

ROOT=Path(__file__).resolve().parents[1]
VARIANTS=ROOT/'shared-assets/modeling/open_cargo_wagon/variants'
SEATS=['single_seat','double_seat','single_wooden_seat','double_wooden_seat','triple_seat','triple_wooden_seat']
HORSES=['single_horse','double_horse']
WIDTH=1.75
ROWS=8
ROW_PITCH=.70
DECK_HALF=16*((ROWS-1)*ROW_PITCH/2+.45)
FRONT=24-DECK_HALF
REAR=DECK_HALF-36
TRIPLE_HALF=15.5*WIDTH
TRIPLE_SPACING=(2*TRIPLE_HALF-1.6)/3

def write(path,data):
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def groups(model):
    def walk(items):
        for g in items:
            if isinstance(g,dict):
                yield g
                yield from walk(g['children'])
    return list(walk(model['outliner']))

def bake_position(cube,baked):
    a,b=cube['from'],cube['to']
    baked['origin']=[-b[0],a[1],a[2]]
    baked['size']=[b[i]-a[i] for i in range(3)]
    if 'pivot' in baked:baked['pivot']=[-cube['origin'][0],cube['origin'][1],cube['origin'][2]]

def generate_triple_seats():
    for wooden in [False,True]:
        for horse in HORSES:
            kind='wooden_seat' if wooden else 'seat'
            source=VARIANTS/f'double_{kind}_{horse}'
            name=f'triple_{kind}_{horse}'
            target=VARIANTS/name;target.mkdir(parents=True,exist_ok=True)
            model=json.loads((source/'wagon.bbmodel').read_text(encoding='utf-8'))
            geo=json.loads((source/'wagon.geo.json').read_text(encoding='utf-8'))
            group=next(g for g in groups(model) if g['name']=='seat')
            elements={c['uuid']:c for c in model['elements']}
            bone=next(b for b in geo['minecraft:geometry'][0]['bones'] if b['name']=='seat')
            members=[elements[c] for c in group['children'] if isinstance(c,str)]
            replacements=[]
            for cube,baked in zip(members,bone['cubes']):
                if cube['name'] in ['left_wool_cushion','right_wool_cushion']:
                    if cube['name']=='left_wool_cushion':
                        for seat in range(3):
                            c=copy.deepcopy(cube);b=copy.deepcopy(baked);centre=(seat-1)*TRIPLE_SPACING
                            c['name']=f'triple_wool_cushion_{seat}'
                            c['uuid']=str(uuid.uuid5(uuid.NAMESPACE_URL,'tm_wagon/'+name+'/'+c['name']))
                            half=(TRIPLE_SPACING-.3)/2
                            c['from'][0]=centre-half;c['to'][0]=centre+half;c['origin'][0]=centre
                            bake_position(c,b);replacements.append((c,b))
                    continue
                # End supports translate; long boards extend between them.
                for field in ['from','to','origin']:
                    x=cube[field][0]
                    if abs(x)>.01:cube[field][0]=x+(TRIPLE_HALF-15.5)*(1 if x>0 else -1)
                bake_position(cube,baked);replacements.append((cube,baked))
            old={c['uuid'] for c in members}
            model['elements']=[c for c in model['elements'] if c['uuid'] not in old]+[c for c,b in replacements]
            group['children']=[c['uuid'] for c,b in replacements];bone['cubes']=[b for c,b in replacements]
            model['name']=name;model['model_identifier']='tm_wagon.'+name
            geo['minecraft:geometry'][0]['description']['identifier']='geometry.tm_wagon.'+name
            write(target/'wagon.bbmodel',model);write(target/'wagon.geo.json',geo)
            for file in ['wagon.png','wagon.animation.json']:shutil.copyfile(source/file,target/file)

def generate_wide_variants():
    generate_triple_seats()
    for variant in ['bare_frame',*[f'{seat}_{horse}' for seat in SEATS for horse in HORSES]]:
        source=VARIANTS/variant;name='wide_'+variant
        target=VARIANTS/name;target.mkdir(parents=True,exist_ok=True)
        model=json.loads((source/'wagon.bbmodel').read_text(encoding='utf-8'))
        geo=json.loads((source/'wagon.geo.json').read_text(encoding='utf-8'))
        elements={c['uuid']:c for c in model['elements']}
        bones={b['name']:b for b in geo['minecraft:geometry'][0]['bones']}
        # Centre the deck on the frame, keeping the same margin around the cargo grid.
        def deck_z(z):return -DECK_HALF+(z+24)*2*DECK_HALF/60
        for group in groups(model):
            part=group['name'];bone=bones.get(part)
            if bone is None:continue
            def transform(p):
                x,y,z=p
                if part=='chassis' and label=='steering_pivot':return [x,y,z+FRONT]
                if part in ['chassis','cargo_body']:return [x*WIDTH,y,deck_z(z)]
                if part=='tailgate':return [x*WIDTH,y,z+REAR]
                if part in ['front_axle','rear_axle']:return [x*WIDTH,y,z+(FRONT if part=='front_axle' else REAR)]
                if part=='driver_platform':return [x*WIDTH,y,z+FRONT]
                if part in ['seat','shafts']:return [x,y,z+FRONT]
                if part.endswith('_wheel'):return [x+(1 if 'right' in part else -1)*21*(WIDTH-1),y,z+(FRONT if part.startswith('front') else REAR)]
                return [x,y,z]
            label=''
            group['origin']=transform(group['origin'])
            bone['pivot']=[-group['origin'][0],group['origin'][1],group['origin'][2]]
            members=[elements[c] for c in group['children'] if isinstance(c,str)]
            for cube,baked in zip(members,bone.get('cubes',[])):
                label=cube['name']
                # Posts and rivets retain their thickness while moving with the deck.
                preserve=part in ['chassis','cargo_body'] and not cube['name'].startswith(('floorboard_','sill_','side_rail_','longitudinal_beam_'))
                if preserve:
                    centre=[(a+b)/2 for a,b in zip(cube['from'],cube['to'])]
                    moved=transform(centre);delta=[b-a for a,b in zip(centre,moved)]
                    for field in ['from','to','origin']:cube[field]=[v+delta[i] for i,v in enumerate(cube[field])]
                    # Crossmembers and the front wall span the widened deck.
                    if cube['name'].startswith(('crossmember_','front_rail_','front_bulkhead_')):
                        for field in ['from','to','origin']:cube[field][0]*=WIDTH
                else:
                    for field in ['from','to','origin']:cube[field]=transform(cube[field])
                bake_position(cube,baked)
        model['name']=name;model['model_identifier']='tm_wagon.'+name
        geo['minecraft:geometry'][0]['description']['identifier']='geometry.tm_wagon.'+name
        write(target/'wagon.bbmodel',model);write(target/'wagon.geo.json',geo)
        for file in ['wagon.png','wagon.animation.json']:shutil.copyfile(source/file,target/file)
    print('Generated 13 centred wide Blockbench wagons and both triple driver seats.')

if __name__=='__main__':generate_wide_variants()
