"""Underdeep soundtrack synthesizer (Stage 11): original instruments, effects and mastering in numpy.

Everything here is synthesized from first principles (additive, FM, Karplus-Strong, modal and noise synthesis); no
samples, soundfonts or third-party recordings are used, so every track generated from it is original and fully owned.
"""
import math

import numpy as np
from scipy import signal

SR = 44100


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


def env_adsr(n, a, d, s, r, sr=SR):
    """ADSR envelope of n samples (times in seconds); release starts at n - r."""
    a_n, d_n, r_n = int(a * sr), int(d * sr), int(r * sr)
    e = np.full(n, s, dtype=np.float32)
    a_n = min(a_n, n)
    if a_n:
        e[:a_n] = np.linspace(0, 1, a_n, endpoint=False) ** 1.6
    d_end = min(n, a_n + d_n)
    if d_end > a_n:
        e[a_n:d_end] = np.linspace(1, s, d_end - a_n)
    r_n = min(r_n, n)
    if r_n:
        e[n - r_n:] *= np.linspace(1, 0, r_n) ** 1.5
    return e


def t_axis(n):
    return np.arange(n, dtype=np.float32) / SR


def additive(f, n, amps, vib=0.0, vib_rate=5.0, detune=0.0, phase_seed=0, inharm=0.0):
    """Sum of partials k*f (amps[k-1]); partials above 16 kHz are dropped (no aliasing)."""
    t = t_axis(n)
    rng = np.random.default_rng(phase_seed)
    vib_curve = 1 + vib * np.sin(2 * np.pi * vib_rate * t + rng.uniform(0, 6)) * np.clip(t / 0.6, 0, 1)
    out = np.zeros(n, dtype=np.float32)
    base_phase = 2 * np.pi * np.cumsum(f * (1 + detune) * vib_curve) / SR
    for k, a in enumerate(amps, start=1):
        if a == 0:
            continue
        fk = f * k * (1 + inharm * k * k)
        if fk > 16000:
            break
        out += a * np.sin(base_phase * k * (1 + inharm * k * k) + rng.uniform(0, 6.28))
    return out


def saw_amps(count, tilt=1.0):
    return [1.0 / (k ** tilt) for k in range(1, count + 1)]


# ============================================================================ instruments (mono note renderers)

def pad(f, dur, vel, seed, bright=0.5):
    n = int((dur + 2.2) * SR)
    tilt = 1.9 - bright
    amps = saw_amps(24, tilt)
    x = sum(additive(f, n, amps, vib=0.0015, vib_rate=0.3 + 0.1 * i, detune=d, phase_seed=seed + i)
            for i, d in enumerate((-0.004, 0.0, 0.0045)))
    return x * env_adsr(n, 1.4, 0.8, 0.85, 2.2) * vel * 0.12


def strings(f, dur, vel, seed, bright=0.5):
    n = int((dur + 0.9) * SR)
    amps = saw_amps(30, 1.55 - 0.5 * bright)
    x = additive(f, n, amps, vib=0.0045, vib_rate=5.2, phase_seed=seed) + 0.6 * additive(f, n, amps, vib=0.004, vib_rate=4.7, detune=0.003,
                                                                                            phase_seed=seed + 9)
    return x * env_adsr(n, 0.28, 0.3, 0.8, 0.9) * vel * 0.13


def choir(f, dur, vel, seed, vowel="a"):
    n = int((dur + 1.4) * SR)
    forms = {"a": [(730, 90), (1090, 110), (2440, 160)], "o": [(570, 80), (840, 100), (2410, 160)], "u": [(300, 70), (870, 100), (2240, 150)]}[vowel]
    amps = []
    for k in range(1, 40):
        fk = f * k
        g = sum(math.exp(-((fk - c) / w) ** 2 / 2) * (1.0 if i == 0 else 0.6 / i) for i, (c, w) in enumerate(forms))
        amps.append((0.25 / k + g) * (1 if fk < 5000 else 0))
    x = sum(additive(f, n, amps, vib=0.004, vib_rate=4.5 + i * 0.4, detune=d, phase_seed=seed + i) for i, d in enumerate((-0.003, 0.0025)))
    return x * env_adsr(n, 0.9, 0.5, 0.8, 1.4) * vel * 0.06


def flute(f, dur, vel, seed):
    n = int((dur + 0.35) * SR)
    x = additive(f, n, [1, 0.32, 0.12, 0.05], vib=0.006, vib_rate=5.0, phase_seed=seed)
    rng = np.random.default_rng(seed)
    noise = rng.standard_normal(n).astype(np.float32)
    b, a = signal.butter(2, [min(0.95, f * 0.9 / (SR / 2)), min(0.99, f * 3 / (SR / 2))], btype="band")
    breath = signal.lfilter(b, a, noise) * 0.12
    e = env_adsr(n, 0.07, 0.15, 0.78, 0.3)
    return (x + breath) * e * vel * 0.22


def fm_bell(f, dur, vel, seed, ratio=3.5, index=3.0, decay=2.5):
    n = int((dur + decay) * SR)
    t = t_axis(n)
    idx = index * np.exp(-t * 2.2)
    mod = np.sin(2 * np.pi * f * ratio * t)
    x = np.sin(2 * np.pi * f * t + idx * mod)
    e = np.exp(-t / (decay * 0.45)) * np.clip(t / 0.003, 0, 1)
    return (x * e * vel * 0.2).astype(np.float32)


def kalimba(f, dur, vel, seed):
    return fm_bell(f, dur, vel, seed, ratio=2.0, index=1.6, decay=1.4) * 1.1


def glass(f, dur, vel, seed):
    n = int((dur + 3.0) * SR)
    t = t_axis(n)
    x = np.zeros(n, dtype=np.float32)
    for k, (r, a, d) in enumerate([(1.0, 1.0, 3.2), (2.76, 0.35, 1.6), (5.40, 0.16, 0.9), (8.93, 0.07, 0.5)]):
        x += a * np.sin(2 * np.pi * f * r * t + k) * np.exp(-t / d)
    return x * np.clip(t / 0.01, 0, 1) * vel * 0.16


def marimba(f, dur, vel, seed):
    n = int((dur + 1.0) * SR)
    t = t_axis(n)
    x = (np.sin(2 * np.pi * f * t) * np.exp(-t * 3.2) + 0.35 * np.sin(2 * np.pi * f * 3.93 * t) * np.exp(-t * 9) +
         0.12 * np.sin(2 * np.pi * f * 9.2 * t) * np.exp(-t * 20))
    return (x * np.clip(t / 0.002, 0, 1) * vel * 0.3).astype(np.float32)


def pluck(f, dur, vel, seed, damp=0.996, bright=0.5):
    """Karplus-Strong string, computed one delay-line period at a time (vectorised per period)."""
    n = int((dur + 1.5) * SR)
    N = max(2, int(SR / f))
    rng = np.random.default_rng(seed)
    burst = rng.uniform(-1, 1, N).astype(np.float32)
    # soften the excitation for a warmer tone
    k = max(1, int(3 * (1 - bright)) + 1)
    burst = np.convolve(burst, np.ones(k) / k, mode="same")
    out = np.zeros(n + N + 1, dtype=np.float32)
    out[:N] = burst
    pos = N
    while pos < n:
        end = min(pos + N, n)
        prev = out[pos - N:end - N]
        prev2 = out[pos - N - 1:end - N - 1] if pos - N - 1 >= 0 else np.concatenate(([0], out[pos - N:end - N - 1]))
        out[pos:end] = damp * 0.5 * (prev + prev2)
        pos = end
    x = out[:n]
    x *= env_adsr(n, 0.002, 0.0, 1.0, 0.25)
    return x * vel * 0.45


def harp(f, dur, vel, seed):
    return pluck(f, dur, vel, seed, damp=0.998, bright=0.35)


def bass(f, dur, vel, seed, pluck_env=False):
    n = int((dur + 0.4) * SR)
    x = additive(f, n, [1, 0.35, 0.12, 0.05], phase_seed=seed)
    e = env_adsr(n, 0.01, 0.4, 0.55, 0.35) if pluck_env else env_adsr(n, 0.08, 0.2, 0.9, 0.35)
    return x * e * vel * 0.28


def drone(f, dur, vel, seed):
    n = int((dur + 3.0) * SR)
    t = t_axis(n)
    x = additive(f, n, saw_amps(10, 2.2), phase_seed=seed) + 0.6 * additive(f * 1.5, n, saw_amps(6, 2.4), phase_seed=seed + 1)
    swell = 0.75 + 0.25 * np.sin(2 * np.pi * 0.07 * t + seed)
    return x * swell * env_adsr(n, 2.5, 1.0, 0.9, 3.0) * vel * 0.1


def music_box(f, dur, vel, seed):
    return fm_bell(f, dur, vel, seed, ratio=4.0, index=1.2, decay=1.6) * 0.9


def brass(f, dur, vel, seed):
    n = int((dur + 0.5) * SR)
    t = t_axis(n)
    # brightness opens with the attack, like a lip-reed swell
    x = np.zeros(n, dtype=np.float32)
    swell = np.clip(t / 0.25, 0, 1)
    for k in range(1, 16):
        if f * k > 12000:
            break
        x += (1.0 / k) * (swell ** (0.3 * k)) * np.sin(2 * np.pi * f * k * t + k)
    return x * env_adsr(n, 0.12, 0.2, 0.85, 0.45) * vel * 0.12


INSTR = {"pad": pad, "strings": strings, "choir": choir, "flute": flute, "bell": fm_bell, "kalimba": kalimba, "glass": glass,
         "marimba": marimba, "pluck": pluck, "harp": harp, "bass": bass, "drone": drone, "musicbox": music_box, "brass": brass}


# ============================================================================ percussion

def perc(kind, vel, seed):
    rng = np.random.default_rng(seed)
    if kind == "taiko":
        n = int(1.4 * SR)
        t = t_axis(n)
        f = 62 + 46 * np.exp(-t * 18)
        x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 4.5)
        nz = rng.standard_normal(n).astype(np.float32) * np.exp(-t * 40) * 0.3
        b, a = signal.butter(2, 900 / (SR / 2))
        return (x + signal.lfilter(b, a, nz)) * vel * 0.9
    if kind == "frame":
        n = int(0.7 * SR)
        t = t_axis(n)
        f = 110 + 60 * np.exp(-t * 30)
        x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9)
        nz = rng.standard_normal(n).astype(np.float32) * np.exp(-t * 60) * 0.4
        b, a = signal.butter(2, [300 / (SR / 2), 3000 / (SR / 2)], btype="band")
        return (x * 0.7 + signal.lfilter(b, a, nz)) * vel * 0.6
    if kind == "shaker":
        n = int(0.18 * SR)
        t = t_axis(n)
        nz = rng.standard_normal(n).astype(np.float32)
        b, a = signal.butter(2, 5000 / (SR / 2), btype="high")
        return signal.lfilter(b, a, nz) * np.exp(-t * 35) * np.clip(t / 0.01, 0, 1) * vel * 0.18
    if kind == "metal":
        n = int(2.0 * SR)
        t = t_axis(n)
        x = sum(np.sin(2 * np.pi * f0 * t) * np.exp(-t * d) for f0, d in ((211, 1.5), (577, 2.2), (931, 3.0), (1489, 4.0), (2287, 6.0)))
        return x * np.clip(t / 0.002, 0, 1) * vel * 0.12
    if kind == "stick":
        n = int(0.12 * SR)
        t = t_axis(n)
        x = np.sin(2 * np.pi * 1800 * t) * np.exp(-t * 70) + rng.standard_normal(n) * np.exp(-t * 90) * 0.3
        return x.astype(np.float32) * vel * 0.25
    raise ValueError(kind)


# ============================================================================ effects and mastering

def reverb_ir(seconds, seed, damp=0.5):
    """Stereo decaying-noise impulse response (diffuse hall), darker as it decays."""
    n = int(seconds * SR)
    t = t_axis(n)
    rng = np.random.default_rng(seed)
    out = []
    for ch in range(2):
        nz = rng.standard_normal(n).astype(np.float32)
        early = np.zeros(n, dtype=np.float32)
        for k in range(8):
            early[int(rng.uniform(0.005, 0.06) * SR)] += rng.uniform(0.2, 0.6) * (1 if rng.random() > 0.5 else -1)
        env = np.exp(-6.9 * t / seconds)
        x = nz * env
        # progressive darkening: blend a low-passed copy in over time
        b, a = signal.butter(1, (2500 + 4000 * (1 - damp)) / (SR / 2))
        low = signal.lfilter(b, a, x)
        mixk = np.clip(t / (seconds * 0.5), 0, 1)
        out.append((x * (1 - mixk) + low * mixk + early) * 0.08)
    return np.stack(out)


def apply_reverb(stereo, ir, wet):
    out = np.zeros((2, stereo.shape[1] + ir.shape[1] - 1), dtype=np.float32)
    for ch in range(2):
        out[ch] = signal.fftconvolve(stereo[ch], ir[ch])
    dry = np.zeros_like(out)
    dry[:, :stereo.shape[1]] = stereo
    return dry * (1 - wet * 0.5) + out * wet


def pan(mono, p):
    """Equal-power pan, p in [-1, 1]."""
    a = (p + 1) * math.pi / 4
    return np.stack([mono * math.cos(a), mono * math.sin(a)])


def master(stereo, target_rms_db=-20.0, fade_in=2.0, fade_out=6.0):
    """Tone-shape, loudness-normalise to an RMS target, soft-limit at -1 dBFS and fade the ends."""
    b, a = signal.butter(2, 38 / (SR / 2), btype="high")          # remove sub-rumble / DC
    x = signal.lfilter(b, a, stereo, axis=1)
    b, a = signal.butter(1, 11000 / (SR / 2))                      # gentle top-end smoothing
    x = 0.7 * x + 0.3 * signal.lfilter(b, a, x, axis=1)
    # loudness on the gated loud parts (ignores intro/outro silence)
    block = SR // 2
    mono = x.mean(axis=0)
    rms_blocks = np.array([np.sqrt(np.mean(mono[i:i + block] ** 2)) for i in range(0, len(mono) - block, block)])
    gate = rms_blocks[rms_blocks > rms_blocks.max() * 0.1] if len(rms_blocks) else np.array([1e-3])
    cur = 20 * np.log10(max(1e-9, float(np.sqrt(np.mean(gate ** 2)))))
    x *= 10 ** ((target_rms_db - cur) / 20)
    ceiling = 10 ** (-1.0 / 20)
    x = np.tanh(x / ceiling) * ceiling
    n = x.shape[1]
    fi, fo = int(fade_in * SR), int(fade_out * SR)
    x[:, :fi] *= np.linspace(0, 1, fi) ** 2
    x[:, n - fo:] *= np.linspace(1, 0, fo) ** 2
    return x.astype(np.float32)
