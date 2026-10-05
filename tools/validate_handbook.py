"""Check handbook resources, cross-references, translations and recipe coverage."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets'
BOOK = ASSETS / 'tm_wagon/patchouli_books/coachmans_manual'
RECIPES = ROOT / 'src/main/resources/data/tm_wagon/recipe'

def read(path):
    return json.loads(path.read_text(encoding='utf-8'))

def walk(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for child in value: yield from walk(child)
    elif isinstance(value, dict):
        for child in value.values(): yield from walk(child)

expected_entries = None
for locale in ('en_us', 'zh_cn', 'zh_tw'):
    folder = BOOK / locale
    categories = {p.stem:read(p) for p in (folder/'categories').glob('*.json')}
    entries = {p.relative_to(folder/'entries').with_suffix('').as_posix():read(p)
               for p in (folder/'entries').rglob('*.json')}
    assert len(categories) == 6 and len(entries) == 24, locale
    if expected_entries is None: expected_entries = entries
    assert entries.keys() == expected_entries.keys(), locale
    covered, images, pages = set(), set(), 0
    for id, entry in entries.items():
        assert entry['category'].split(':',1)[1] in categories, id
        assert entry['name'] and entry['pages'], id
        assert len(entry['pages']) == len(expected_entries[id]['pages']), id
        for i, page in enumerate(entry['pages']):
            pages += 1
            assert page['type'] == expected_entries[id]['pages'][i]['type'], id
            if page['type'] == 'tm_wagon:component_recipe':
                rid = page['recipe'].split(':',1)[1]
                assert (RECIPES/(rid+'.json')).is_file(), (id,rid)
                covered.add(rid)
            for image in page.get('images',[]):
                namespace, path = image.split(':',1)
                assert (ASSETS/namespace/path).is_file(), image
                images.add(image)
            for target in page.get('entries',[]):
                assert target.split(':',1)[1] in entries, target
        for string in walk(entry):
            for target in re.findall(r'\$\(l:([^)]*)\)', string):
                assert target.split(':',1)[1] in entries, (id,target)
                assert not entries[target.split(':',1)[1]].get('flag'), (id,'link to optional entry')
        for item, index in entry.get('extra_recipe_mappings',{}).items():
            assert entry['pages'][index]['recipe'] == item, (id,item)
    required = {p.stem for p in RECIPES.glob('*.json')}
    assert covered == required, (locale,'missing recipes',required-covered)
    assembly_images = {image for image in images if '/assembly/' in image}
    equipment_images = {image for image in images if '/equipment/' in image}
    assert len(assembly_images) == 6 and len(equipment_images) == 6, locale
    for id in ('cabinet','cover','canopy'):
        preview = entries['equipment/'+id]['pages'][-1]
        assert preview['type'] == 'patchouli:image' and len(preview['images']) == 2, id
    for id in ('carryon','maid','sable'):
        assert entries['help/'+id]['flag'].startswith('mod:'), id
    print(f'{locale}: {len(categories)} categories, {len(entries)} entries, {pages} pages, {len(covered)} recipes, {len(assembly_images)} diagrams, {len(equipment_images)} previews; references valid')

template = read(BOOK/'en_us/templates/component_recipe.json')
assert any(p.get('class') == 'com.sange.tm_wagon.handbook.client.WagonRecipeComponent' for p in template['components'])
print('All handbook resource checks passed.')
