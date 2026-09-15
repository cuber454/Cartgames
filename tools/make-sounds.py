#!/usr/bin/env python3
"""Звуки стола для «Дурака» — генерируются, а не скачиваются.

Готовые звуки из интернета пришлось бы тащить в репозиторий вместе с
лицензией и разбираться, можно ли их вообще класть в приложение. Здесь
звук синтезируется из шума: короткий, свой, без чужих прав.

Запуск: python3 tools/make-sounds.py
Кладёт wav-файлы в app/src/main/res/raw/.
"""

import math
import os
import random
import struct
import wave

RATE = 44100
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def lowpass(samples, cutoff):
    """Однополюсный фильтр: глушит шипение выше cutoff герц."""
    a = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
    out = []
    y = 0.0
    for x in samples:
        y += a * (x - y)
        out.append(y)
    return out


def highpass(samples, cutoff):
    """Убирает гул ниже cutoff герц."""
    a = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
    out = []
    y = 0.0
    for x in samples:
        y += a * (x - y)
        out.append(x - y)
    return out


def noise(seconds, rng):
    return [rng.uniform(-1.0, 1.0) for _ in range(int(RATE * seconds))]


def envelope(count, attack, decay):
    """Плавное появление и затухание: attack и decay — доли длины."""
    attack_len = max(1, int(count * attack))
    decay_len = max(1, int(count * decay))
    out = []
    for i in range(count):
        if i < attack_len:
            out.append(i / attack_len)
        elif i > count - decay_len:
            out.append((count - i) / decay_len)
        else:
            out.append(1.0)
    return out


def mix(*tracks):
    length = max(len(t) for t in tracks)
    out = [0.0] * length
    for track in tracks:
        for i, value in enumerate(track):
            out[i] += value
    return out


def silence(seconds):
    return [0.0] * int(RATE * seconds)


def normalize(samples, peak=0.75):
    top = max(abs(s) for s in samples) or 1.0
    return [s * peak / top for s in samples]


def card_tap(rng):
    """Карта легла на стол: короткий шлепок."""
    body = lowpass(noise(0.07, rng), 2200)
    body = highpass(body, 200)
    env = envelope(len(body), attack=0.01, decay=0.85)

    # Низкий стук — столешница отзывается.
    thump = [math.sin(2 * math.pi * 110 * i / RATE) * math.exp(-i / (RATE * 0.012))
             for i in range(int(RATE * 0.05))]

    mixed = mix([b * e for b, e in zip(body, env)], [t * 0.5 for t in thump])
    return normalize(mixed, 0.7)


def deal_shuffle(rng):
    """Раздача: шесть коротких шорохов подряд."""
    out = silence(0.05)
    for _ in range(6):
        burst = lowpass(noise(0.055, rng), 3500)
        burst = highpass(burst, 500)
        env = envelope(len(burst), attack=0.15, decay=0.6)
        out = mix(out, [0.0] * len(out) + [b * e * rng.uniform(0.7, 1.0)
                                           for b, e in zip(burst, env)])
        out += silence(rng.uniform(0.03, 0.07))
    return normalize(out, 0.6)


def take_cards(rng):
    """Карты сгребли со стола: длинный шорох с всплеском в середине."""
    body = lowpass(noise(0.38, rng), 4000)
    body = highpass(body, 400)
    env = []
    for i in range(len(body)):
        x = i / len(body)
        env.append(math.sin(math.pi * x) ** 0.6)
    return normalize([b * e for b, e in zip(body, env)], 0.65)


def save(name, samples):
    path = os.path.normpath(os.path.join(OUT_DIR, name))
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(
            struct.pack("<h", max(-32768, min(32767, int(s * 32767)))) for s in samples
        ))
    print(f"{path}  {len(samples) / RATE:.2f} с")


def main():
    rng = random.Random(20260915)
    os.makedirs(OUT_DIR, exist_ok=True)
    save("card.wav", card_tap(rng))
    save("deal.wav", deal_shuffle(rng))
    save("take.wav", take_cards(rng))


if __name__ == "__main__":
    main()
