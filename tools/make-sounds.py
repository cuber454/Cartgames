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


def note(semitones):
    """Частота в полутонах от ля первой октавы (440 Гц)."""
    return 440.0 * 2 ** (semitones / 12.0)


def tone(freq, seconds, decay=8.0, harmonics=(1.0, 0.30, 0.10)):
    """Нота: синус с обертонами и затуханием — колокольчик, а не гудок."""
    out = []
    count = int(RATE * seconds)
    for i in range(count):
        t = i / RATE
        value = sum(
            amp * math.sin(2 * math.pi * freq * (n + 1) * t)
            for n, amp in enumerate(harmonics)
        )
        out.append(value * math.exp(-decay * t))
    return out


def melody(notes, gap=0.01):
    """Мелодия: ноты подряд, каждая со своим затуханием."""
    out = []
    for freq, seconds in notes:
        out += tone(freq, seconds)
        out += silence(gap)
    return out


def glide(start_hz, end_hz, seconds, decay=5.0):
    """Скользящий тон: падение или подъём высоты за одну ноту."""
    out = []
    count = int(RATE * seconds)
    phase = 0.0
    for i in range(count):
        freq = start_hz + (end_hz - start_hz) * (i / count)
        phase += 2 * math.pi * freq / RATE
        out.append(math.sin(phase) * math.exp(-decay * i / RATE))
    return out


def signal_start():
    """Начало: восходящая квинта — «садимся играть»."""
    return normalize(melody([(note(0), 0.16), (note(7), 0.30)], gap=0.0), 0.6)


def signal_turn():
    """Твой ход: одна тихая нота. Звучит чаще всех — быть нежнее некуда."""
    return normalize(tone(note(12), 0.09, decay=20.0, harmonics=(1.0, 0.15)), 0.45)


def signal_win():
    """Победа: мажорное трезвучие вверх."""
    return normalize(melody(
        [(note(0), 0.13), (note(4), 0.13), (note(7), 0.13), (note(12), 0.45)],
        gap=0.0,
    ), 0.6)


def signal_lose():
    """Проигрыш: две ноты вниз, мажор — в минор."""
    return normalize(melody([(note(3), 0.18), (note(0), 0.45)], gap=0.0), 0.6)


def signal_bolt():
    """Болт: низкое падение — «свалился»."""
    return normalize(glide(300.0, 90.0, 0.30), 0.6)


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
    # Сигналы: не шум стола, а короткие ноты — о событиях, а не о картах.
    save("start.wav", signal_start())
    save("turn.wav", signal_turn())
    save("win.wav", signal_win())
    save("lose.wav", signal_lose())
    save("bolt.wav", signal_bolt())


if __name__ == "__main__":
    main()
