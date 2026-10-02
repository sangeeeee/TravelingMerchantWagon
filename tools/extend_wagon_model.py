"""Build rear-extended Blockbench/GeckoLib variants from the existing wagons.

One cargo row is 11.2 model units (0.7 blocks). The front and frame origin stay
fixed; the rear axle, wheels, gate and gate pivot move back by that amount.
"""
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
VARIANTS = ROOT / 'modeling/open_cargo_wagon/variants'
EXTENSION = 11.2


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def generate_long_variants():
    for variant in ['bare_frame', 'single_seat_single_horse', 'single_seat_double_horse',
                    'double_seat_single_horse', 'double_seat_double_horse']:
        source = VARIANTS / variant
        target = VARIANTS / ('long_' + variant)
        target.mkdir(parents=True, exist_ok=True)
        model = json.loads((source / 'wagon.bbmodel').read_text(encoding='utf-8'))
        geo = json.loads((source / 'wagon.geo.json').read_text(encoding='utf-8'))
        model['name'] = 'long_' + variant
        model['model_identifier'] = 'tm_wagon.long_' + variant
        geo['minecraft:geometry'][0]['description']['identifier'] = 'geometry.' + model['model_identifier']
        cubes = {c['uuid']: c for c in model['elements']}
        bones = {b['name']: b for b in geo['minecraft:geometry'][0]['bones']}

        def walk(groups):
            for group in groups:
                if not isinstance(group, dict):
                    continue
                name = group['name']
                moving = name in ['tailgate', 'rear_axle', 'rear_left_wheel', 'rear_right_wheel']
                if moving:
                    group['origin'][2] += EXTENSION
                    bones[name]['pivot'][2] += EXTENSION
                members = [cubes[cid] for cid in group['children'] if isinstance(cid, str)]
                exported = bones.get(name, {}).get('cubes', [])
                assert len(members) == len(exported), f'Cube order mismatch: {variant}/{name}'
                for cube, baked in zip(members, exported):
                    label = cube['name']
                    shift = 0
                    stretch = False
                    if moving:
                        shift = EXTENSION
                    elif name == 'chassis':
                        stretch = label.startswith('longitudinal_beam_')
                        if label == 'crossmember_34':
                            shift = EXTENSION
                    elif name == 'cargo_body':
                        stretch = label.startswith(('floorboard_', 'sill_', 'side_rail_'))
                        if label.startswith(('side_post_', 'post_strap_', 'post_cap_')):
                            if label.endswith('_36'):
                                shift = EXTENSION
                            elif label.endswith('_5'):
                                shift = EXTENSION / 2
                        elif label.startswith('rail_rivet_') and label.endswith('_35.7'):
                            shift = EXTENSION
                    if stretch:
                        cube['to'][2] += EXTENSION
                        cube['origin'][2] += EXTENSION / 2
                    if shift:
                        for field in ['from', 'to', 'origin']:
                            cube[field][2] += shift
                    baked['origin'][2] = cube['from'][2]
                    baked['size'][2] = cube['to'][2] - cube['from'][2]
                    if 'pivot' in baked:
                        baked['pivot'][2] = cube['origin'][2]
                walk(group['children'])

        walk(model['outliner'])
        write(target / 'wagon.bbmodel', model)
        write(target / 'wagon.geo.json', geo)
        for filename in ['wagon.png', 'wagon.animation.json']:
            if (source / filename).exists():
                shutil.copyfile(source / filename, target / filename)
    print('Generated five rear-extended wagon model projects, including animated gate/wheels.')


if __name__ == '__main__':
    generate_long_variants()
