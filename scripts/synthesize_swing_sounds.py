#!/usr/bin/env python3
"""合成挥击的破风声，写到 assets/kizuna_players_animation/sounds/swing/。

全部是算出来的：白噪声过一个中心频率随时间滑动的带通滤波器，再乘一个先急后缓的包络。没有用任何录音或第三方素材。
随机数种子写死，重跑得到逐字节相同的 WAV；最后用 ffmpeg 压成 OGG（单声道，游戏按距离衰减要单声道）。
"""
import math
import random
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

RATE = 44100
OUT = Path(__file__).resolve().parent.parent / 'src/main/resources/assets/kizuna_players_animation/sounds/swing'
# 名字: (时长秒, 起始频率, 最高频率, 结束频率, 带通阻尼, 音量峰值在第几成, 版本数)
SOUNDS = {
    'weapon_light': (0.22, 900, 2600, 1200, 0.45, 0.40, 3),
    'weapon_heavy': (0.34, 500, 1800, 700, 0.40, 0.45, 3),
    'limb_light': (0.14, 1400, 3200, 1800, 0.55, 0.35, 3),
    'limb_heavy': (0.20, 900, 2400, 1100, 0.50, 0.40, 3),
    'low': (0.40, 90, 220, 80, 0.30, 0.30, 2),
}
PEAK = 0.7


def sweep(start, top, end, peak_at, progress):
    # 频率先升到最高、再落下来，两段都按指数走，听起来是「嗖」的一声。
    if progress < peak_at:
        return start * (top / start) ** (progress / peak_at)
    return top * (end / top) ** ((progress - peak_at) / (1 - peak_at))


def envelope(peak_at, progress):
    # 音量快起慢落；起落都用正弦的平方，头尾没有咔哒声。
    if progress < peak_at:
        return math.sin(math.pi / 2 * progress / peak_at) ** 2
    return math.cos(math.pi / 2 * (progress - peak_at) / (1 - peak_at)) ** 2


def synthesize(name, variant):
    seconds, start, top, end, damping, peak_at, _ = SOUNDS[name]
    noise = random.Random(f'{name}-{variant}')
    # 各版本的频率整体挪一点，叠上游戏里的音高浮动，连着听不重样。
    shift = 1 + (variant - 1) * 0.06
    count = int(seconds * RATE)
    low = band = 0.0
    samples = []
    for index in range(count):
        progress = index / count
        frequency = sweep(start, top, end, peak_at, progress) * shift
        coefficient = 2 * math.sin(math.pi * frequency / RATE)
        high = noise.uniform(-1, 1) - low - damping * band
        band += coefficient * high
        low += coefficient * band
        samples.append(band * envelope(peak_at, progress))
    loudest = max(abs(sample) for sample in samples)
    return [sample / loudest * PEAK for sample in samples]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as scratch:
        for name, settings in SOUNDS.items():
            for variant in range(1, settings[-1] + 1):
                source = Path(scratch) / f'{name}{variant}.wav'
                with wave.open(str(source), 'wb') as output:
                    output.setnchannels(1)
                    output.setsampwidth(2)
                    output.setframerate(RATE)
                    output.writeframes(b''.join(struct.pack('<h', int(sample * 32767))
                                                for sample in synthesize(name, variant)))
                target = OUT / f'{name}{variant}.ogg'
                subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', str(source), '-c:a', 'libvorbis',
                                '-q:a', '4', '-map_metadata', '-1', '-fflags', '+bitexact', str(target)], check=True)
                print(target.name, f'{settings[0]:.2f} 秒')


if __name__ == '__main__':
    main()
