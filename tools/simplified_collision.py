"""Gameplay collision volumes, deliberately independent of decorative cuboids.

Coordinates are model units (16 per block). Wheel silhouettes are solid discs,
so spokes, hubs, nails and gaps never create extra collision surfaces.
"""
import math
import copy


def volumes():
    forward_extension = 8  # Level driver platform grows by half a block.
    body = [
        [-17,22,-24,17,24,36],
        [-18.5,24,-25,-16,37,37], [16,24,-25,18.5,37,37],
        [-16,24,-25,16,37,-23], [-16,24,35.5,16,37,37],
        [-16,22,-35-forward_extension,16,24,-24], [-16,22,-41.75-forward_extension,16,26,-35-forward_extension],
        [-22,15.5,-38,-16,17,-32], [16,15.5,-38,22,17,-32],
        [-22,9.5,-21.5,22,11.5,-18.5], [-22,12.5,18.5,22,14.5,21.5],
    ]
    result = {
        'wagon_assembly_frame': [[-8,0,-8,8,20,8],[-11.76,20,-13.72,11.76,22,13.72]],
        'cargo_body': body,
        'single_horse_shafts': [[-9.9,17,-88,-6.9,21.5,-25],[6.9,17,-88,9.9,21.5,-25],[-7.4,19,-38,7.4,21,-36]],
        'double_horse_shafts': [[-2,14.5,-99,2,22,-25],[-16,17.35,-91.45,16,20.15,-88.8],[-22,19,-39,22,21.5,-36]],
    }
    extended = copy.deepcopy(body)
    for i in [0,1,2]: extended[i][5] += 11.2
    for i in [4,10]:
        extended[i][2] += 11.2
        extended[i][5] += 11.2
    result['long_cargo_body'] = extended
    for name, half in [('single_seat',8.5),('double_seat',15.5)]:
        result[name] = [
            # The inaccessible cabinet recess is solid for gameplay collision.
            # One box covers the support, underside, seat board and cushion.
            [-half,24,-35.5,half,34.5,-23.5],
            [-half-.5,34.5,-25,half+.5,48,-21.5],
            [-half,34.5,-35,-half+2,40,-23.5],
            [half-2,34.5,-35,half,40,-23.5],
        ]
    for name,half in [('single_wooden_seat',8.5),('double_wooden_seat',15.5)]:
        # The cabinet recess and plain seat board share one simple solid box.
        result[name]=[[-half,24,-35.5,half,31.5,-23.5]]
    for name, radius in [('small_wheel',10.5),('large_wheel',13.5)]:
        result[name] = []
        for i in range(8):
            low, high = 2*radius*i/8, 2*radius*(i+1)/8
            nearest = max(low-radius,0,radius-high)
            width = math.sqrt(radius**2-nearest**2)
            result[name].append([-1.5,low,-width,1.5,high,width])
    return {name:[[round(v/16,6) for v in box] for box in boxes] for name,boxes in result.items()}
