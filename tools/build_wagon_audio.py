"""Convert a user-supplied recording into Minecraft's positional rolling loop.

Usage: python tools/build_wagon_audio.py path/to/source.mp3
Requires numpy and soundfile for development only. No downloads or extra layers.
"""
from pathlib import Path
import argparse
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'build/audio-tools'))
import numpy as np
import soundfile as sf


def build(source):
    samples, rate = sf.read(source)
    if samples.ndim > 1:
        samples = samples.mean(axis=1)
    if len(samples) < rate:
        raise ValueError('Recording must be at least one second long')
    # Blend the final quarter-second into the beginning. Keep the source's tone,
    # pacing and dynamics; mono allows normal in-world positional attenuation.
    overlap = round(.25*rate)
    length = len(samples)-overlap
    weight = np.linspace(0, 1, overlap)
    loop = np.concatenate((samples[length:]*(1-weight)+samples[:overlap]*weight,
                           samples[overlap:length]))
    # Apply the requested 1.3x base gain, with encoding headroom.
    loop *= 1.3
    peak = max(abs(loop))
    if peak > .85:
        loop *= .85/peak
    output = ROOT / 'src/main/resources/assets/tm_wagon/sounds/wagon_roll.ogg'
    sf.write(output, loop, rate, format='OGG', subtype='VORBIS')
    decoded, decoded_rate = sf.read(output)
    assert decoded_rate == rate and decoded.ndim == 1 and len(decoded) == length
    assert max(abs(decoded)) < 1
    print(f'{output.name}: {len(decoded)/rate:.2f}s mono {rate} Hz; '
          f'peak={max(abs(decoded)):.3f}; seam={abs(decoded[0]-decoded[-1]):.5f}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    build(parser.parse_args().source)
