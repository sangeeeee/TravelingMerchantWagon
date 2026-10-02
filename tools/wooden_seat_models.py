"""Derive plain oak inverted-L driver seats without changing the existing padded models."""
import copy
import json
from pathlib import Path
import shutil
import uuid

ROOT=Path(__file__).resolve().parents[1]
VARIANTS=ROOT/'modeling/open_cargo_wagon/variants'

def write(path,data):
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def textured_cube(template,name,start,end,bark=False):
    cube=copy.deepcopy(template)
    cube.update(name=name,uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,'tm_wagon/wooden-seat/'+name)),
                **{'from':start,'to':end,'origin':[(a+b)/2 for a,b in zip(start,end)]})
    dx,dy,dz=[b-a for a,b in zip(start,end)]
    u,v,width,height=(32,0,16,32) if bark else (0,0,32,32)
    # One texture pixel per model unit, twice the old seat's pixel density.
    faces={'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),
           'up':(dx,dz),'down':(dx,dz)}
    cube['faces']={face:{'uv':[u,v,u+min(width,w),v+min(height,h)],'texture':0}
                   for face,(w,h) in faces.items()}
    baked={'origin':[-end[0],start[1],start[2]],'size':[dx,dy,dz],'uv':{}}
    for face,data in cube['faces'].items():
        x0,y0,x1,y1=data['uv']
        baked['uv'][face]={'uv':[x1,y1],'uv_size':[x0-x1,y0-y1]} if face in ['up','down'] else {'uv':[x0,y0],'uv_size':[x1-x0,y1-y0]}
    return cube,baked

def generate_wooden_seats():
    for size in ['single','double']:
        for horse in ['single_horse','double_horse']:
            source=VARIANTS/f'{size}_seat_{horse}'
            name=f'{size}_wooden_seat_{horse}'
            target=VARIANTS/name;target.mkdir(parents=True,exist_ok=True)
            model=json.loads((source/'wagon.bbmodel').read_text(encoding='utf-8'))
            geo=json.loads((source/'wagon.geo.json').read_text(encoding='utf-8'))
            elements={c['uuid']:c for c in model['elements']}
            def find(groups):
                for g in groups:
                    if isinstance(g,dict):
                        if g['name']=='seat':return g
                        found=find(g['children'])
                        if found is not None:return found
            seat=find(model['outliner'])
            members=[elements[cid] for cid in seat['children'] if isinstance(cid,str)]
            bone=next(b for b in geo['minecraft:geometry'][0]['bones'] if b['name']=='seat')
            assert len(members)==len(bone['cubes'])
            board=next(c for c in members if c['name']=='seat_base')
            support=next(c for c in members if c['name']=='seat_front_support')
            x0,y0,z0=board['from'];x1,y1,z1=board['to'];rim=1.6
            # Adjacent cuboids form one flush seat board, without overlapping faces.
            specs=[('seat_front_support',support['from'],support['to'],False),
                   ('seat_base',[x0+rim,y0,z0+rim],[x1-rim,y1,z1-rim],False),
                   ('seat_bark_front',[x0,y0,z0],[x1,y1,z0+rim],True),
                   ('seat_bark_rear',[x0,y0,z1-rim],[x1,y1,z1],True),
                   ('seat_bark_left',[x0,y0,z0+rim],[x0+rim,y1,z1-rim],True),
                   ('seat_bark_right',[x1-rim,y0,z0+rim],[x1,y1,z1-rim],True)]
            new=[textured_cube(board,*spec) for spec in specs]
            removed={c['uuid'] for c in members}
            model['elements']=[c for c in model['elements'] if c['uuid'] not in removed]+[c for c,b in new]
            seat['children']=[c['uuid'] for c,b in new]
            bone['cubes']=[b for c,b in new]
            model['name']=name;model['model_identifier']='tm_wagon.'+name
            geo['minecraft:geometry'][0]['description']['identifier']='geometry.tm_wagon.'+name
            # Oak planks remain horizontal; the perimeter uses the existing oak bark tile.
            write(target/'wagon.bbmodel',model);write(target/'wagon.geo.json',geo)
            for file in ['wagon.png','wagon.animation.json']:
                if (source/file).exists():shutil.copyfile(source/file,target/file)
    print('Generated four oak driver-seat variants; bark frames and denser inset plank UVs, no backrest or wool.')

if __name__=='__main__':generate_wooden_seats()
