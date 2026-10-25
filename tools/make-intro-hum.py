"""Renders Telum's intro hum, timed to the picture, into the app's assets.

    python tools/make-intro-hum.py

Writes app/src/main/assets/intro/hum.ogg (the 7 s intro) and hum-calm.ogg (the 3 s
reduced-motion one), which the app plays. Needs numpy, and ffmpeg with libvorbis on the PATH.

The sound is original and made entirely here: a low, warm drone on D that rises during the push and
settles as the lockup forms, then a soft tone, in tune with it, as the dart completes. It fades in
over about a second and never starts abruptly. Each file is levelled so its loudest moment is
about 10 dB below normal TV dialogue (-24 LUFS): -34 LUFS momentary, measured as ITU-R BS.1770.
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

RATE = 48000
D2 = 73.416           # the drone's root; TV speakers that cannot reproduce it still hear its harmonics
TONE = (587.33, 880.0, 1174.66)  # D5, A5, D6
TARGET_LUFS = -34.0
HERE = os.path.dirname(os.path.abspath(__file__))
# The development tree keeps the app under android/; the public repository has it at the root.
OUT = next((p for p in (os.path.join(HERE, "..", "android", "app", "src", "main", "assets", "intro"),
                        os.path.join(HERE, "..", "app", "src", "main", "assets", "intro")) if os.path.isdir(p)),
           os.path.join(HERE, "..", "app", "src", "main", "assets", "intro"))


def ramp(t, a, b):
    return np.clip((t - a) / (b - a), 0.0, 1.0)


def ease(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)


def drone(t, bright):
    """Harmonics of D2, each with a slightly detuned twin for warmth. [bright] (0 to 1) opens the upper ones."""
    out = np.zeros_like(t)
    # (harmonic, amplitude when dim, amplitude when bright)
    # The root is kept below the next two harmonics: TV speakers barely reproduce 73 Hz, and the
    # loudness target should land on what they can play.
    for k, dim, full in ((1, 0.6, 0.6), (2, 0.6, 0.7), (3, 0.25, 0.45), (4, 0.1, 0.3), (6, 0.02, 0.13), (8, 0.0, 0.06)):
        amp = dim + (full - dim) * bright
        f = D2 * k
        out += amp * (np.sin(2 * np.pi * f * t + k) + 0.5 * np.sin(2 * np.pi * f * 1.0035 * t + 2 * k))
    return np.tanh(out / 3.0)


def air(t, rng):
    """Faint noise between 250 and 900 Hz: the room the drone sits in."""
    spectrum = rng.standard_normal(len(t) // 2 + 1) + 1j * rng.standard_normal(len(t) // 2 + 1)
    f = np.fft.rfftfreq(len(t), 1.0 / RATE)
    spectrum *= np.exp(-0.5 * (np.log(np.maximum(f, 1.0) / 475.0) / 0.45) ** 2)
    noise = np.fft.irfft(spectrum, len(t))
    return noise / np.max(np.abs(noise))


def tone(t, at):
    """A soft tone at [at] seconds: an 80 ms rounded attack, so it never pings, and a long decay."""
    s = t - at
    attack = 0.5 - 0.5 * np.cos(np.pi * np.clip(s / 0.08, 0.0, 1.0))
    env = np.where(s < 0, 0.0, attack * np.exp(-np.maximum(s, 0.0) / 1.1))
    out = np.zeros_like(t)
    for f, amp in zip(TONE, (1.0, 0.35, 0.12)):
        out += amp * (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(2 * np.pi * (f + 0.5) * t))
    return env * out / 1.9


def full(t, rng):
    """The 7 s intro: up with the stars, rising through the push (2.2-5.2 s), settling as the lockup
    forms, the tone as the dart completes (6.0 s), gone with the picture (6.5-7.0 s)."""
    push = ease(ramp(t, 2.2, 5.0))
    settle = ease(ramp(t, 5.2, 6.2))
    fade = ease(ramp(t, 0.0, 1.0)) * (1.0 - ease(ramp(t, 6.5, 7.0)))
    level = 0.5 + 0.5 * push - 0.2 * settle
    bright = 0.15 + 0.85 * push - 0.45 * settle
    sound = level * drone(t, bright) + 0.08 * push * (1.0 - 0.7 * settle) * air(t, rng) + 0.55 * tone(t, 6.0)
    return sound * fade


def calm(t, rng):
    """The 3 s reduced-motion intro: the drone fades in, the tone as the lockup completes (1.2 s),
    gone with the picture (2.5-3.0 s)."""
    fade = ease(ramp(t, 0.0, 1.0)) * (1.0 - ease(ramp(t, 2.5, 3.0)))
    return (0.6 * drone(t, 0.3) + 0.55 * tone(t, 1.2)) * fade


def biquad(x, b, a):
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, v in enumerate(x):
        out = b[0] * v + b[1] * x1 + b[2] * x2 - a[1] * y1 - a[2] * y2
        x2, x1, y2, y1 = x1, v, y1, out
        y[i] = out
    return y


def loudest_momentary(x):
    """The highest momentary loudness (400 ms windows, 100 ms apart), in LUFS, by ITU-R BS.1770 at 48 kHz."""
    k = biquad(x, (1.53512485958697, -2.69169618940638, 1.19839281085285), (1.0, -1.69065929318241, 0.73248077421585))
    k = biquad(k, (1.0, -2.0, 1.0), (1.0, -1.99004745483398, 0.99007225036621))
    window, hop = int(0.4 * RATE), int(0.1 * RATE)
    power = max(np.mean(k[i:i + window] ** 2) for i in range(0, len(k) - window + 1, hop))
    return -0.691 + 10.0 * np.log10(power)


def render(name, seconds, make):
    rng = np.random.default_rng(20261024)
    t = np.arange(int(seconds * RATE)) / RATE
    x = make(t, rng)
    x *= 10.0 ** ((TARGET_LUFS - loudest_momentary(x)) / 20.0)
    peak = 20.0 * np.log10(np.max(np.abs(x)))
    pcm = np.clip(np.round(x * 32767.0 + rng.uniform(-0.5, 0.5, len(x)) + rng.uniform(-0.5, 0.5, len(x))), -32768, 32767)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.astype("<i2").tobytes())
        out = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", out], check=True)
    print(f"{name}.ogg: {seconds} s, loudest moment {loudest_momentary(x):.1f} LUFS, sample peak {peak:.1f} dBFS, "
          f"{os.path.getsize(out)} bytes")


if __name__ == "__main__":
    if not os.path.isdir(OUT):
        sys.exit(f"No assets folder at {OUT}")
    render("hum", 7.0, full)
    render("hum-calm", 3.0, calm)
