"""Generate thin Conestoga canopy meshes, shared collision data and full Blockbench previews."""
from pathlib import Path
from PIL import Image, ImageDraw
import base64, copy, json, math, random, uuid
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tm_wagon'
PROJECT=ROOT/'modeling/wagon_canopy'
BASE=2.28125; HALF=1.1875; THICK=1/64; FRONT=-1.34375
TOP=3.96875+.5*math.tan(math.pi/8)
PROFILE=[(-HALF,BASE),(-HALF,3.65625),(-.875,3.96875),(-.375,TOP),(.375,TOP),(.875,3.96875),(HALF,3.65625),(HALF,BASE)]
TEX={'cloth':'tm_wagon:block/canopy_cloth','wood':'tm_wagon:block/canopy_wood','curtain':'tm_wagon:block/canopy_curtain'}
def write(p,value):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
rng=random.Random(19021)
cloth=Image.new('RGB',(16,16),(226,221,205));d=ImageDraw.Draw(cloth)
for y in range(16):
 shade=[(182,177,159),(202,196,179),(243,237,221),(232,226,211)][min(y,15-y)] if min(y,15-y)<4 else (226,221,205)
 d.line((0,y,15,y),fill=shade)
for x in [2,6,10,14]:d.line((x,3,x,12),fill=(230,224,209))
for _ in range(14):
 x=rng.randrange(16);y=rng.randrange(4,12);d.point((x,y),fill=rng.choice([(217,212,195),(236,230,213)]))
wood=Image.new('RGB',(16,16),(66,47,29));d=ImageDraw.Draw(wood)
for x in [1,4,8,13]:d.line((x,0,x,15),fill=(83,61,38),width=1)
for rect in [(2,3,3,6),(7,9,9,10),(12,1,14,2)]:d.rectangle(rect,fill=(47,34,22))
curtain=Image.new('RGB',(16,16));d=ImageDraw.Draw(curtain)
for x in range(16):d.line((x,0,x,15),fill=[(234,229,215),(219,214,198),(190,183,166),(211,205,187)][x%4])
d.line((0,14,15,14),fill=(182,175,157));d.line((0,15,15,15),fill=(218,210,191))
for name,im in [('cloth',cloth),('wood',wood),('curtain',curtain)]:
 p=ASSETS/f'textures/block/canopy_{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.resize((64,64),Image.Resampling.NEAREST).save(p)
def box(name,a,b,texture='cloth',rotation=None):
 p={'name':name,'from':a,'to':b,'faces':{f:{'uv':[0,0,16,16],'texture':'#'+texture} for f in ['north','south','east','west','up','down']}}
 if rotation:p['rotation']=rotation
 return p
def normalized(x,y):return x*8+8,(y-BASE)*8
# Native X/Y use a uniform half scale so Java block rotations retain their actual angles.
def strip(name,a,b,width,z0,z1,texture='cloth',inward=0):
 dx,dy=b[0]-a[0],b[1]-a[1];length=math.hypot(dx,dy);nx,ny=dy/length,-dx/length
 ax,ay=a[0]+nx*inward,a[1]+ny*inward;bx,by=b[0]+nx*inward,b[1]+ny*inward
 cx,cy=(ax+bx)/2,(ay+by)/2;px,py=normalized(cx,cy)
 if abs(dx)<1e-8:
  start=[px-width*4,(min(ay,by)-BASE)*8,z0*16];end=[px+width*4,(max(ay,by)-BASE)*8,z1*16];p=box(name,start,end,texture)
  for face in ['east','west']:p['faces'][face]['rotation']=90
 else:
  angle=math.degrees(math.atan2(dy,dx));p=box(name,[px-length*4,py-width*4,z0*16],[px+length*4,py+width*4,z1*16],texture,
   {'origin':[px,py,0],'axis':'z','angle':round(angle,6),'rescale':False} if abs(angle)>1e-8 else None)
 # Edge faces use only the thin part of the fabric texture, avoiding squeezed stripe noise.
 for face in ['north','south']:p['faces'][face]['uv']=[0,0,16,max(.125,width*8)]
 return p
shell=[strip(f'white_canvas_{i}',a,b,THICK,0,1) for i,(a,b) in enumerate(zip(PROFILE,PROFILE[1:]))]
ribs=[strip(f'interior_dark_rib_{i}',a,b,.046875,-.03125,.03125,'wood',.046875) for i,(a,b) in enumerate(zip(PROFILE,PROFILE[1:]))]
ends=[strip(f'canvas_arch_border_{i}',a,b,.25,0,THICK,'cloth',.12109375) for i,(a,b) in enumerate(zip(PROFILE,PROFILE[1:]))]
def roof(x):
 x=abs(x)
 if x<=.375:return TOP
 if x<=.875:return TOP-(x-.375)*math.tan(math.pi/8)
 return 3.96875-(x-.875)
closed=[];open_parts=[]
for i in range(8):
 x0=-.953125+i*1.90625/8;x1=x0+1.90625/8;y1=min(roof(x0),roof(x1))-.025
 a=normalized(x0,1.5);b=normalized(x1,y1)
 closed.append(box(f'curtain_{"left" if i<4 else "right"}_{i}',[a[0],a[1],0],[b[0],b[1],THICK*16],'curtain'))
for side in [-1,1]:
 for pleat in range(3):
  x0=(.765625+pleat*.04166667)*side;x1=(.765625+(pleat+1)*.04166667)*side
  a=normalized(min(x0,x1),1.5);b=normalized(max(x0,x1),roof(.890625)-.04)
  z0=(pleat%2)*THICK/2
  open_parts.append(box(f'gathered_curtain_{side}_{pleat}',[a[0],a[1],z0*16],[b[0],b[1],(z0+THICK)*16],'curtain'))
meshes={'shell':shell,'rib':ribs,'end':ends,'curtain_closed':closed,'curtain_open':open_parts}
def model(parts):return {'credit':'TravelingMerchantWagon / tools/export_wagon_canopy.py','ambientocclusion':False,'textures':{**TEX,'particle':TEX['cloth']},'elements':parts}
for name,parts in meshes.items():write(ASSETS/f'models/block/canopy_{name}.json',model(parts))
item=model(copy.deepcopy(shell)+[dict(copy.deepcopy(p),name='item_'+p['name']) for p in ribs])
for part in item['elements'][len(shell):]:
 for k in ['from','to']:part[k][2]+=8
item['display']={'gui':{'rotation':[25,35,0],'translation':[0,.5,0],'scale':[.62,.62,.62]},'ground':{'translation':[0,1,0],'scale':[.4,.4,.4]},'fixed':{'scale':[.6,.6,.6]},'thirdperson_righthand':{'rotation':[60,0,0],'translation':[0,2,0],'scale':[.45,.45,.45]},'firstperson_righthand':{'rotation':[0,-25,0],'translation':[0,1,0],'scale':[.55,.55,.55]}}
write(ASSETS/'models/item/wagon_canopy.json',item)
# Collision strips follow the outside silhouette; ribs are decorative and have no separate colliders.
def bounds_strip(a,b,width,z0,z1,inward=0):
 dx,dy=b[0]-a[0],b[1]-a[1];length=math.hypot(dx,dy);nx,ny=dy/length,-dx/length
 pts=[(x+nx*(inward+s*width/2),y+ny*(inward+s*width/2)) for x,y in [a,b] for s in [-1,1]]
 return [min(p[0] for p in pts),min(p[1] for p in pts),z0,max(p[0] for p in pts),max(p[1] for p in pts),z1]
shell_boxes=[]
for a,b in zip(PROFILE,PROFILE[1:]):
 n=1 if abs(a[0]-b[0])<1e-8 or abs(a[1]-b[1])<1e-8 else 2 if abs(a[0]-b[0])<.4 else 3
 for i in range(n):
  sub=lambda t:(a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t)
  shell_boxes.append(bounds_strip(sub(i/n),sub((i+1)/n),THICK,0,1))
end_boxes=[bounds_strip(a,b,.25,0,THICK,.12109375) for a,b in zip(PROFILE,PROFILE[1:])]
def rect_box(p):
 a,b=p['from'],p['to'];return [a[0]/8-1,a[1]/8+BASE,a[2]/16,b[0]/8-1,b[1]/8+BASE,b[2]/16]
# The open curtain is two thin bundles, rather than every decorative fold.
open_boxes=[[a,1.5,0,b,roof(.890625)-.04,THICK*1.5] for a,b in [(-.890625,-.765625),(.765625,.890625)]]
write(ROOT/'src/main/resources/data/tm_wagon/canopy_geometry.json',{'shell':shell_boxes,'end':end_boxes,'curtain_closed':[rect_box(p) for p in closed],'curtain_open':open_boxes})
def textures(offset=0):
 result=[]
 for i,name in enumerate(TEX):
  p=ASSETS/f'textures/block/canopy_{name}.png';result.append({'name':p.name,'id':str(i+offset),'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'tm_wagon/canopy/texture/'+name)),'width':64,'height':64,'source':'data:image/png;base64,'+base64.b64encode(p.read_bytes()).decode()})
 return result
def bb_cube(p,offset=0):
 q={'name':p['name'],'type':'cube','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'tm_wagon/canopy/'+p['name']+str(p['from']))),'from':p['from'],'to':p['to'],'box_uv':False,'autouv':0,'rescale':False,'faces':{f:{'texture':offset+list(TEX).index(v['texture'][1:]),'uv':[u*4 for u in v['uv']],**({'rotation':v['rotation']} if 'rotation' in v else {})} for f,v in p['faces'].items()}}
 if 'rotation' in p:q['origin']=p['rotation']['origin'];q['rotation']=[p['rotation']['angle'] if axis==p['rotation']['axis'] else 0 for axis in ['x','y','z']]
 return q
item_bb={'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':'wagon_canopy_item','resolution':{'width':64,'height':64},'textures':textures(),'display':item['display'],'elements':[bb_cube(p) for p in item['elements']]};item_bb['outliner']=[p['uuid'] for p in item_bb['elements']];write(PROJECT/'canopy_item.bbmodel',item_bb)
def actual(p,z0,depth=1,prefix=''):
 q=copy.deepcopy(p);q['name']=prefix+p['name']
 for key in ['from','to']:
  x,y,z=q[key];q[key]=[x*2-16,y*2+BASE*16,z*depth+z0*16]
 if 'rotation' in q:
  x,y,z=q['rotation']['origin'];q['rotation']['origin']=[x*2-16,y*2+BASE*16,z*depth+z0*16]
 return q
for extended in [False,True]:
 prefix='long_' if extended else '';source=ROOT/f'modeling/open_cargo_wagon/variants/{prefix}single_seat_single_horse/wagon.bbmodel';base=json.loads(source.read_text(encoding='utf-8'))
 rows=6 if extended else 5;back=2.34375+(.7 if extended else 0)
 def boundary(row):return FRONT if row==0 else back if row==rows else -1.31+row*.7
 for front_closed in [False,True]:
  for rear_closed in [False,True]:
   proj=copy.deepcopy(base);proj['name']=f'{"extended" if extended else "standard"}_canopy_front_{"closed" if front_closed else "open"}_rear_{"closed" if rear_closed else "open"}'
   offset=len(proj['textures']);proj['textures']+=textures(offset);parts=[]
   for row in range(rows):parts += [actual(p,boundary(row),boundary(row+1)-boundary(row),f'row_{row}_') for p in shell]
   for row in range(rows+1):
    z=boundary(row)+(.065 if row==0 else -.065 if row==rows else 0);parts += [actual(p,z,1,f'rib_{row}_') for p in ribs]
   for front in [True,False]:
    end=FRONT+THICK/4 if front else back-THICK-THICK/4;parts += [actual(p,end,1,f'{"front" if front else "rear"}_') for p in ends]
    is_closed=front_closed if front else rear_closed;z=FRONT+.0234375 if front else back-.125-THICK
    parts += [actual(p,z,1,f'{"front" if front else "rear"}_') for p in (closed if is_closed else open_parts)]
   cubes=[bb_cube(p,offset) for p in parts];proj['elements']+=cubes;proj['outliner'].append({'name':'wagon_canopy','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'tm_wagon/'+proj['name'])),'origin':[0,BASE*16,0],'children':[c['uuid'] for c in cubes]})
   write(PROJECT/('extended' if extended else 'standard')/f'front_{"closed" if front_closed else "open"}_rear_{"closed" if rear_closed else "open"}'/'wagon.bbmodel',proj)
print(f'Exported five canopy meshes, 64x64 textures, collision silhouettes and 9 Blockbench projects; apex {TOP:.3f} blocks.')
