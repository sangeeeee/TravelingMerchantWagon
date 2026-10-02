"""Pack static materials and generate native model variants and compact recipes."""
from pathlib import Path
from PIL import Image
import copy,json

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tm_wagon'
DATA=ROOT/'src/main/resources/data/tm_wagon'
manifest=json.loads((ASSETS/'component_materials.json').read_text(encoding='utf-8'))
woods=list(manifest['wood_sources'])
manifest['status']='active'
manifest['rendering']={'atlas':'tm_wagon:textures/entity/component_atlas.png','atlas_size':[1024,1024],'tile_size':64,'fixed_parts_tile':130,'item_component':'tm_wagon:material','recipe_serializer':'tm_wagon:component'}

def write(p,data):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

atlas=Image.new('RGBA',(1024,1024))
for i,(component,definition) in enumerate(manifest['components'].items()):
    for j,wood in enumerate(woods):
        index=i*10+j
        with Image.open(ASSETS/f'textures/component/{component}/{wood}.png') as im:
            atlas.paste(im,(index%16*64,index//16*64))
with Image.open(ASSETS/'textures/entity/wagon.png') as im:atlas.paste(im,(128,512))
atlas.save(ASSETS/'textures/entity/component_atlas.png',optimize=True)
write(ASSETS/'component_materials.json',manifest)

# Vanilla's block atlas scans block/ and item/ only; add our native model textures.
write(ROOT/'src/main/resources/assets/minecraft/atlases/blocks.json',{'sources':[
    {'type':'minecraft:directory','source':'component/'+name,'prefix':'component/'+name+'/'}
    for name in ['wagon_stool','wagon_cabinet','dye']]})

# ModelRenderer applies the supplied RGB only to faces with a tint index.
for path in [*ASSETS.glob('models/block/cargo_cover_*.json'),*ASSETS.glob('models/block/canopy_*.json'),
             ASSETS/'models/item/wagon_cargo_cover.json',ASSETS/'models/item/wagon_canopy.json']:
    model=json.loads(path.read_text())
    for key,value in model['textures'].items():
        for name in ['cargo_cover_fabric','cargo_cover_spiral','canopy_cloth','canopy_curtain']:
            if value.endswith('/'+name):model['textures'][key]='tm_wagon:component/dye/'+name
    for element in model['elements']:
        for face in element['faces'].values():
            if face['texture'] in ['#fabric','#spiral','#cloth','#curtain']:face['tintindex']=0
    write(path,model)

def cabinet(model,wood):
    result=copy.deepcopy(model);result.pop('overrides',None)
    texture='tm_wagon:component/wagon_cabinet/'+wood
    result['textures']={key:texture for key in result['textures']}
    for element in result['elements']:
        for face in element['faces'].values():
            kind=face['texture'].lstrip('#');x,y=(8,0) if kind=='end' else (8,8) if kind=='iron' else (0,0)
            u0,v0,u1,v1=face['uv'];face['uv']=[x+u0/2,y+v0/2,x+u1/2,y+v1/2]
    return result

for size in ['single','double']:
    for part in ['body','left','right']:
        template=json.loads((ASSETS/f'models/block/cabinet_{size}_{part}.json').read_text())
        for wood in woods:write(ASSETS/f'models/block/material/cabinet_{wood}_{size}_{part}.json',cabinet(template,wood))
for name in ['wagon_cabinet','wagon_stool']:
    root=ASSETS/f'models/item/{name}.json';model=json.loads(root.read_text());model.pop('overrides',None)
    for wood in woods:
        variant=cabinet(model,wood) if name=='wagon_cabinet' else copy.deepcopy(model)
        if name=='wagon_stool':variant['textures']={key:'tm_wagon:component/wagon_stool/'+wood for key in variant['textures']}
        write(ASSETS/f'models/item/material/{name}_{wood}.json',variant)
    model['overrides']=[{'predicate':{'tm_wagon:wood':i},'model':f'tm_wagon:item/material/{name}_{wood}'} for i,wood in enumerate(woods)]
    write(root,model)

recipes={
    'wagon_assembly_frame':['SPS',' S ','SSS'],
    'cargo_body':['PPP','PLT','PPP'],
    'long_cargo_body':[' PP','CLT',' PP'],
    'single_horse_shafts':['SSS',' L ','SSS'],
    'double_horse_shafts':[],
    'wide_cargo_body':['PPP',' U ','PPP'],
    'triple_wooden_seat':[],
    'triple_seat':['PPP','WWW',' V '],
    'single_wooden_seat':['SPS','P P'],
    'double_wooden_seat':[],
    'single_seat':['P','W','E'],
    'double_seat':['PPP','WWW',' D '],
    'small_wheel':['ISI','SLS','ISI'],
    'large_wheel':['PSP','SOS','PSP'],
    'wagon_straw_mat':['HHH','PPP'],
    'wagon_stool':['PPP','SSS','S S'],
    'wagon_cabinet':['PPP',' B ','PPP'],
    'wagon_cargo_cover':['RWR','WWW','RWR'],
    'wagon_canopy':['WWW','S S','I I'],
}
for name,pattern in recipes.items():
    recipe={'type':'tm_wagon:component','component':name}
    if pattern:recipe['pattern']=pattern
    if name=='small_wheel':recipe['count']=2
    write(DATA/f'recipe/{name}.json',recipe)

tags={'cargo_bodies':['cargo_body','long_cargo_body','wide_cargo_body'],
      'wooden_driver_seats':['single_wooden_seat','double_wooden_seat','triple_wooden_seat'],
      'backrest_driver_seats':['single_seat','double_seat','triple_seat'],
      'driver_seats':['#tm_wagon:wooden_driver_seats','#tm_wagon:backrest_driver_seats'],
      'wheels':['small_wheel','large_wheel'],
      'cargo_accessories':['wagon_stool','wagon_straw_mat','wagon_cabinet','wagon_cargo_cover','wagon_canopy']}
for name,values in tags.items():write(DATA/f'tags/item/{name}.json',{'values':[v if v.startswith('#') else 'tm_wagon:'+v for v in values]})

colours_zh=['白色','橙色','品红色','淡蓝色','黄色','黄绿色','粉红色','灰色','淡灰色','青色','紫色','蓝色','棕色','绿色','红色','黑色']
for locale in ['zh_cn','en_us']:
    path=ASSETS/f'lang/{locale}.json';lang=json.loads(path.read_text(encoding='utf-8'))
    for wood,spec in manifest['wood_sources'].items():lang['material.tm_wagon.wood.'+wood]=spec['zh_name' if locale=='zh_cn' else 'en_name']
    for colour,zh in zip(manifest['dyes']['colours'],colours_zh):lang['material.tm_wagon.colour.'+colour]=zh if locale=='zh_cn' else colour.replace('_',' ').title()
    for name,n in [('wood_name',2),('colour_name',2),('wood_colour_name',3)]:lang['item.tm_wagon.'+name]=('' if locale=='zh_cn' else ' ').join(['%s']*n)
    new_names={'wide_cargo_body':('加宽货物车厢','Wide Cargo Body'), 'triple_seat':('三人带靠背车夫座椅','Triple Backrest Driver Seat'), 'triple_wooden_seat':('三人木制车夫座椅','Triple Wooden Driver Seat')}
    for name,names in new_names.items():
        lang['block.tm_wagon.'+name]=names[0 if locale=='zh_cn' else 1]
        lang['tooltip.tm_wagon.'+name]=('4列×8行货位；以装配架为中心放置。' if locale=='zh_cn' else 'Four columns and eight rows; centred on the assembly frame.') if name=='wide_cargo_body' else ('仅适用于加宽货物车厢；中间为驾驶位。' if locale=='zh_cn' else 'Wide cargo bodies only; the middle seat is the driver.')
    write(path,lang)
print('Packed one shared 1024x1024 atlas, tinted fabric models, 80 native wood models, 19 material-aware recipes and 6 item tags.')
