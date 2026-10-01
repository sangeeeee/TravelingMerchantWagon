"""Gameplay collision volumes, deliberately independent of decorative cuboids.

Coordinates are model units (16 per block). Wheel silhouettes are solid discs,
so spokes, hubs, nails and gaps never create extra collision surfaces.
"""
import math


def volumes():
    body = [
        [-17,22,-24,17,24,36],
        [-18.5,24,-25,-16,37,37], [16,24,-25,18.5,37,37],
        [-16,24,-25,16,37,-23], [-16,24,35.5,16,37,37],
        [-16,22,-35,16,24,-24], [-16,22,-41.75,16,26,-35],
        [-22,15.5,-38,-16,17,-32], [16,15.5,-38,22,17,-32],
        [-22,9.5,-21.5,22,11.5,-18.5], [-22,12.5,18.5,22,14.5,21.5],
    ]
    result = {
        'wagon_assembly_frame': [[-8,0,-8,8,20,8],[-11.76,20,-13.72,11.76,22,13.72]],
        'cargo_body': body,
        'single_horse_shafts': [[-11.5,17,-88,-8.5,21.5,-25],[8.5,17,-88,11.5,21.5,-25],[-9,19,-38,9,21,-36]],
        'double_horse_shafts': [[-2,14.5,-99,2,22,-25],[-23,18,-87,23,21,-84],[-22,19,-39,22,21.5,-36]],
    }
    for name, half in [('single_seat',8.5),('double_seat',15.5)]:
        result[name] = [
            [-half,24,-32,half,31.5,-29.5],
            [-half,30,-35.5,half,34.5,-23.5],
            [-half-.5,34.5,-25,half+.5,48,-21.5],
            [-half,34.5,-35,-half+2,40,-23.5],
            [half-2,34.5,-35,half,40,-23.5],
        ]
    for name, radius in [('small_wheel',10.5),('large_wheel',13.5)]:
        result[name] = []
        for i in range(8):
            low, high = 2*radius*i/8, 2*radius*(i+1)/8
            nearest = max(low-radius,0,radius-high)
            width = math.sqrt(radius**2-nearest**2)
            result[name].append([-1.5,low,-width,1.5,high,width])
    return {name:[[round(v/16,6) for v in box] for box in boxes] for name,boxes in result.items()}
