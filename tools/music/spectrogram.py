#!/usr/bin/env python3
"""Decodes an OGG track with ffmpeg and draws a log-frequency spectrogram + loudness curve (audio validation without listening)."""
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import signal


def decode(path, sr=22050):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(sr), "-"], check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype="<f4"), sr


def draw(path, out, width=1200, height=360):
    x, sr = decode(path)
    f, t, S = signal.spectrogram(x, sr, nperseg=2048, noverlap=1536)
    S = 10 * np.log10(S + 1e-12)
    # log-frequency rows 40 Hz .. 10 kHz
    rows = np.geomspace(40, 10000, height - 60)
    img = np.zeros((height - 60, len(t)))
    for i, fr in enumerate(rows[::-1]):
        img[i] = S[np.searchsorted(f, fr)]
    img = np.clip((img + 110) / 70, 0, 1)
    pic = Image.fromarray((np.stack([img ** 1.2, img ** 0.8 * 0.9, img ** 0.5 * 0.6], -1) * 255).astype(np.uint8)).resize((width, height - 60))
    canvas = Image.new("RGB", (width, height), (16, 16, 20))
    canvas.paste(pic, (0, 0))
    d = ImageDraw.Draw(canvas)
    block = sr // 4
    rms = [20 * np.log10(np.sqrt(np.mean(x[i:i + block] ** 2)) + 1e-9) for i in range(0, len(x) - block, block)]
    pts = [(int(k * width / len(rms)), height - 5 - int(max(0, r + 60) * 50 / 60)) for k, r in enumerate(rms)]
    d.line(pts, fill=(120, 220, 140))
    d.text((4, 2), f"{path.split('/')[-1]}  {len(x) / sr:.1f}s  (rows: 40 Hz-10 kHz log; green: RMS dB)", fill=(255, 255, 255))
    canvas.save(out)


if __name__ == "__main__":
    draw(sys.argv[1], sys.argv[2])
