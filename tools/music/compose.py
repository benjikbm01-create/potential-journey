#!/usr/bin/env python3
"""Underdeep original soundtrack composer (Stage 11).

Composes and renders every track of the soundtrack from its specification in TRACKS: key, mode, tempo, metre, form,
chord progressions, instrumentation and mood. Music is written by deterministic rules (seeded), not sampled: harmony
from curated modal progressions with nearest-voice leading, melodies built from 2-bar motifs answered and varied across
A / B / A' sections with cadences on chord tones, bass lines, arpeggio/ostinato layers, percussion grooves and
textures. Instruments, reverb and mastering are synthesized by synth.py. Output: OGG Vorbis (streamed in game).

Usage:
  python3 tools/music/compose.py                 render all tracks (4 processes)
  python3 tools/music/compose.py <id> [<id>...]  render selected tracks
  python3 tools/music/compose.py --list          print the track list
Outputs: src/main/resources/assets/underdeep/sounds/music/<context>/<id>.ogg and tools/music/out/manifest.json
"""
import json
import math
import os
import random
import subprocess
import sys
import tempfile
import zlib
from multiprocessing import Pool

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import synth  # noqa: E402
from synth import SR  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "underdeep", "sounds", "music")
MANIFEST = os.path.join(os.path.dirname(__file__), "out")

MODES = {
    "ionian": [0, 2, 4, 5, 7, 9, 11], "dorian": [0, 2, 3, 5, 7, 9, 10], "phrygian": [0, 1, 3, 5, 7, 8, 10],
    "lydian": [0, 2, 4, 6, 7, 9, 11], "mixolydian": [0, 2, 4, 5, 7, 9, 10], "aeolian": [0, 2, 3, 5, 7, 8, 10],
    "harmonic": [0, 2, 3, 5, 7, 8, 11],
}
# progressions as scale degrees (0 = tonic); each list is one section's cycle
PROGS = {
    "ionian": [[0, 4, 5, 3], [0, 5, 3, 4], [3, 0, 4, 0], [0, 3, 1, 4], [5, 3, 0, 4]],
    "dorian": [[0, 3, 0, 6], [0, 2, 3, 0], [0, 6, 3, 0], [3, 0, 6, 4], [0, 3, 4, 3]],
    "phrygian": [[0, 1, 0, 6], [0, 5, 1, 0], [0, 1, 5, 6], [3, 1, 0, 0]],
    "lydian": [[0, 1, 0, 6], [0, 1, 4, 0], [5, 1, 0, 0], [0, 4, 1, 3]],
    "mixolydian": [[0, 6, 3, 0], [0, 3, 6, 0], [0, 4, 6, 3], [6, 3, 0, 0]],
    "aeolian": [[0, 5, 2, 6], [0, 3, 5, 4], [5, 6, 0, 0], [0, 6, 5, 6], [3, 5, 0, 4]],
    "harmonic": [[0, 5, 3, 4], [0, 3, 4, 0], [5, 3, 1, 4], [0, 5, 1, 4]],
}
RHYTHM_CELLS = {  # one bar of 4 beats each; calm cells favour long values
    "calm": [[2, 2], [3, 1], [1, 1, 2], [4], [1.5, 0.5, 2], [2, 1, 1]],
    "flow": [[1, 1, 2], [1.5, 0.5, 2], [0.5, 0.5, 1, 2], [1, 0.5, 0.5, 2], [2, 1, 1], [1, 1, 1, 1]],
    "drive": [[0.5, 0.5, 1, 1, 1], [1, 0.5, 0.5, 1, 1], [0.75, 0.25, 1, 2], [0.5, 0.5, 0.5, 0.5, 2], [1, 1, 2]],
}
RHYTHM_CELLS_3 = {"calm": [[2, 1], [3], [1, 1, 1]], "flow": [[1, 1, 1], [1.5, 0.5, 1], [2, 1]], "drive": [[1, 0.5, 0.5, 1], [0.5, 0.5, 1, 1]]}


class Track:
    def __init__(self, tid, context, title, key, mode, bpm, seconds, arr, mood="calm", beats=4, reverb=2.6, wet=0.35, loud=-20.0,
                 bright=0.5, perc=None, density=0.6, melody_sections="ABA", vowel="a", register=72):
        self.__dict__.update(locals())
        del self.__dict__["self"]


TRACKS = []
# mix balance (measured with stem RMS analysis): the melody sits on top, pads and bass support it, percussion stays under
ROLE_GAIN = {"pad": 0.7, "drone": 0.8, "bass": 0.5, "arp": 1.4, "tex": 2.0, "mel": 1.7, "mel2": 1.4}
PERC_GAIN = {"taiko": 0.45, "frame": 0.9, "metal": 1.6, "shaker": 1.0, "stick": 1.0}


def T(*a, **k):
    TRACKS.append(Track(*a, **k))


# ------------------------------------------------------------------------------------------------ the soundtrack
# arrangement keys: pad / harm (sustained chords), bass, arp (+ arp_rate), mel (melody), mel2 (second melody/answer),
# tex (sparkle texture), drone
R = "ecosystem"
T("rootwild_a", R, "Under Old Roots", 62, "dorian", 76, 126, dict(pad="strings", bass="bass", arp="harp", arp_rate=1, mel="flute"))
T("rootwild_b", R, "Rootlight Path", 65, "dorian", 84, 120, dict(pad="pad", bass="bass", arp="pluck", arp_rate=2, mel="flute", mel2="strings"),
  mood="flow", perc="frame_soft")
T("goldengrove_a", R, "Honey in the Dark", 67, "lydian", 88, 118, dict(pad="pad", bass="bass", arp="kalimba", arp_rate=2, mel="flute"), mood="flow",
  bright=0.7, perc="shaker")
T("goldengrove_b", R, "Amber Afternoon", 64, "ionian", 80, 124, dict(pad="strings", bass="bass", arp="marimba", arp_rate=1, mel="kalimba",
                                                                    mel2="flute"), bright=0.65)
T("fungalexpanse_a", R, "Spore Lanterns", 63, "dorian", 72, 128, dict(pad="choir", bass="bass_pluck", arp="marimba", arp_rate=1, mel="musicbox"),
  vowel="o")
T("fungalexpanse_b", R, "The Mycelium Hums", 60, "mixolydian", 82, 120, dict(pad="pad", bass="bass_pluck", arp="musicbox", arp_rate=2, mel="marimba",
                                                                            mel2="choir"), mood="flow", vowel="o")
T("floodeddepths_a", R, "Still Water Cathedral", 57, "aeolian", 62, 132, dict(pad="choir", drone="drone", arp="harp", arp_rate=1, mel="glass"),
  reverb=4.0, wet=0.5, vowel="u", density=0.4)
T("floodeddepths_b", R, "Drowned Bells", 62, "aeolian", 66, 124, dict(pad="pad", bass="bass", arp="glass", arp_rate=1, mel="strings"), reverb=3.6,
  wet=0.48)
T("emeraldwetlands_a", R, "Reed and Mist", 64, "mixolydian", 80, 122, dict(pad="pad", bass="bass_pluck", arp="pluck", arp_rate=2, mel="flute"),
  mood="flow", perc="frame_soft")
T("emeraldwetlands_b", R, "Frogsong Evening", 69, "dorian", 74, 126, dict(pad="strings", bass="bass", arp="kalimba", arp_rate=1, mel="flute",
                                                                         mel2="kalimba"))
T("ashenhollows_a", R, "Ember Fields", 52, "phrygian", 64, 124, dict(drone="drone", pad="strings", bass="bass", mel="brass"), perc="taiko_slow",
  density=0.45, register=64)
T("ashenhollows_b", R, "Cinders Drifting", 57, "phrygian", 70, 118, dict(pad="pad", bass="bass", arp="pluck", arp_rate=1, mel="strings"),
  perc="frame_soft", bright=0.35)
T("petrifiedforest_a", R, "Stone That Was Wood", 59, "aeolian", 70, 126, dict(pad="strings", drone="drone", arp="harp", arp_rate=1, mel="bell"),
  density=0.45)
T("petrifiedforest_b", R, "Rings of Ages", 62, "dorian", 66, 120, dict(pad="pad", bass="bass", arp="harp", arp_rate=1, mel="strings",
                                                                     mel2="flute"))
T("crystaldeep_a", R, "Facets", 66, "lydian", 64, 128, dict(pad="pad", arp="glass", arp_rate=1, mel="bell", tex="musicbox"), reverb=4.5, wet=0.55,
  bright=0.75, density=0.5)
T("crystaldeep_b", R, "Resonance", 61, "lydian", 70, 122, dict(pad="choir", bass="bass", arp="musicbox", arp_rate=2, mel="glass"), reverb=4.0,
  wet=0.5, vowel="o")
T("stormstonecaverns_a", R, "Static in the Stone", 62, "dorian", 92, 116, dict(pad="strings", bass="bass_pluck", arp="pluck", arp_rate=2,
                                                                              mel="strings", tex="bell"), mood="flow", perc="storm")
T("stormstonecaverns_b", R, "Conductor Rain", 57, "aeolian", 86, 120, dict(pad="pad", bass="bass", arp="marimba", arp_rate=2, mel="brass"),
  mood="flow", perc="frame_soft")
T("theabyss_a", R, "Where the Light Ends", 50, "phrygian", 52, 136, dict(drone="drone", pad="choir", mel="glass"), reverb=5.0, wet=0.6,
  vowel="u", density=0.3, loud=-22, register=67)
T("theabyss_b", R, "Echoes Below", 53, "aeolian", 56, 128, dict(drone="drone", pad="pad", bass="bass", mel="strings", tex="glass"), reverb=4.5,
  wet=0.55, density=0.35, loud=-22, bright=0.3)
T("everfrost_a", R, "Rime", 64, "aeolian", 60, 128, dict(pad="pad", arp="glass", arp_rate=1, mel="flute", tex="bell"), reverb=4.0, wet=0.5,
  bright=0.6)
T("everfrost_b", R, "Breath on the Ice", 59, "dorian", 66, 122, dict(pad="strings", bass="bass", arp="harp", arp_rate=1, mel="glass"), reverb=3.5,
  wet=0.45)
T("hollowpeaks_a", R, "High Ledges", 60, "mixolydian", 76, 124, dict(pad="strings", bass="bass", arp="harp", arp_rate=1, mel="brass", mel2="flute"),
  register=67)
T("hollowpeaks_b", R, "Wind Through the Peaks", 65, "ionian", 70, 126, dict(pad="pad", bass="bass", arp="harp", arp_rate=2, mel="flute"),
  density=0.5)
T("crimsonbasin_a", R, "Red Basin", 62, "harmonic", 70, 122, dict(pad="strings", bass="bass_pluck", arp="pluck", arp_rate=1, mel="choir"),
  perc="frame_soft", vowel="a")
T("crimsonbasin_b", R, "Thorn and Bloom", 57, "harmonic", 76, 118, dict(pad="pad", bass="bass", arp="harp", arp_rate=2, mel="strings"),
  mood="flow")
T("sunkengardens_a", R, "Garden Under Glass", 65, "lydian", 68, 126, dict(pad="choir", bass="bass", arp="kalimba", arp_rate=1, mel="glass"),
  vowel="o", reverb=3.5, wet=0.45)
T("sunkengardens_b", R, "Coral Lullaby", 60, "ionian", 64, 124, dict(pad="pad", arp="harp", arp_rate=1, mel="flute", tex="glass"), reverb=3.5,
  wet=0.45, beats=3)
T("starlightcaverns_a", R, "Starlight", 67, "lydian", 62, 130, dict(pad="pad", arp="musicbox", arp_rate=1, mel="strings", tex="glass"), reverb=4.5,
  wet=0.55, bright=0.7)
T("starlightcaverns_b", R, "The Sky-Seal", 62, "ionian", 66, 124, dict(pad="choir", bass="bass", arp="glass", arp_rate=1, mel="bell"), reverb=4.5,
  wet=0.55, vowel="o")

C = "civilization"
T("civ_rootfolk", C, "Hearths of the Rootfolk", 62, "dorian", 96, 108, dict(pad="strings", bass="bass_pluck", arp="harp", arp_rate=2, mel="flute",
                                                                           mel2="pluck"), mood="flow", perc="frame_dance")
T("civ_forgekin", C, "Anvils and Ale", 57, "mixolydian", 104, 104, dict(pad="strings", bass="bass_pluck", arp="marimba", arp_rate=2, mel="brass"),
  mood="drive", perc="forge")
T("civ_tide_dwellers", C, "Tidehouse", 60, "aeolian", 84, 110, dict(pad="choir", bass="bass", arp="kalimba", arp_rate=2, mel="flute"), mood="flow",
  vowel="o", perc="shaker")
T("civ_sporefolk", C, "Market of Caps", 65, "dorian", 92, 106, dict(pad="pad", bass="bass_pluck", arp="marimba", arp_rate=2, mel="musicbox",
                                                                   mel2="flute"), mood="flow", perc="frame_soft")
T("civ_glimmerkin", C, "Lantern Workshops", 67, "lydian", 88, 108, dict(pad="pad", bass="bass", arp="glass", arp_rate=2, mel="bell", mel2="strings"),
  mood="flow", reverb=3.5, wet=0.45)
T("civ_stoneborn", C, "Halls of the Stoneborn", 55, "aeolian", 80, 110, dict(pad="strings", bass="bass", arp="harp", arp_rate=1, mel="brass"),
  perc="taiko_slow", register=67)
T("civ_amberfolk", C, "Amber Fair", 64, "ionian", 108, 104, dict(pad="strings", bass="bass_pluck", arp="kalimba", arp_rate=2, mel="flute",
                                                                mel2="marimba"), mood="drive", perc="shaker", bright=0.7)
T("civ_astrals", C, "Observatory", 66, "lydian", 72, 112, dict(pad="choir", bass="bass", arp="musicbox", arp_rate=2, mel="strings", tex="glass"),
  vowel="o", reverb=4.0, wet=0.5)

H = "home"
T("home_hearth", H, "Hearthlight", 60, "ionian", 72, 110, dict(pad="strings", bass="bass", arp="harp", arp_rate=1, mel="flute"), density=0.55)
T("home_rest", H, "A Place to Rest", 65, "ionian", 66, 112, dict(pad="pad", arp="musicbox", arp_rate=1, mel="kalimba"), beats=3, density=0.5)
T("home_lantern", H, "Lantern by the Bed", 62, "mixolydian", 70, 108, dict(pad="pad", bass="bass", arp="pluck", arp_rate=1, mel="strings"))
T("home_workbench", H, "At the Workbench", 67, "ionian", 84, 104, dict(pad="strings", bass="bass_pluck", arp="marimba", arp_rate=2, mel="flute"),
  mood="flow")
T("home_friends", H, "Friends Underground", 64, "dorian", 78, 110, dict(pad="pad", bass="bass", arp="harp", arp_rate=2, mel="flute", mel2="strings"))

D = "dungeon"
T("dungeon_depths", D, "Forgotten Halls", 57, "phrygian", 60, 104, dict(drone="drone", pad="strings", mel="glass", tex="bell"), perc="tick",
  density=0.35, loud=-21, reverb=4.0, wet=0.5)
T("dungeon_traps", D, "Mechanisms", 55, "harmonic", 72, 100, dict(drone="drone", bass="bass_pluck", arp="pluck", arp_rate=2, mel="strings"),
  perc="tick", density=0.45, loud=-21)
T("dungeon_vault", D, "Sealed Vault", 52, "aeolian", 58, 104, dict(drone="drone", pad="choir", mel="bell"), vowel="u", density=0.35, loud=-21,
  reverb=4.5, wet=0.55)
T("dungeon_secret", D, "Something Hidden", 60, "lydian", 56, 100, dict(pad="pad", arp="glass", arp_rate=1, mel="musicbox"), density=0.35,
  reverb=4.5, wet=0.55, loud=-21)
T("ruins_a", D, "Old Stones Remember", 62, "dorian", 66, 104, dict(pad="strings", arp="harp", arp_rate=1, mel="flute"), density=0.45)
T("ruins_b", D, "Names Worn Away", 59, "aeolian", 62, 104, dict(pad="pad", bass="bass", arp="harp", arp_rate=1, mel="strings"), density=0.45)

F = "combat"
T("combat_a", F, "Blades in the Dark", 57, "harmonic", 128, 84, dict(pad="strings", bass="bass_pluck", arp="pluck", arp_rate=4, mel="brass"),
  mood="drive", perc="battle", loud=-17)
T("combat_b", F, "Teeth and Claws", 52, "phrygian", 136, 80, dict(pad="strings", bass="bass_pluck", arp="marimba", arp_rate=4, mel="strings"),
  mood="drive", perc="battle", loud=-17)
T("combat_c", F, "Hold the Line", 55, "aeolian", 120, 84, dict(pad="choir", bass="bass_pluck", arp="pluck", arp_rate=4, mel="brass"), mood="drive",
  perc="battle", loud=-17)
T("combat_d", F, "Ambush", 50, "phrygian", 140, 78, dict(pad="strings", bass="bass_pluck", arp="pluck", arp_rate=4, mel="brass"), mood="drive",
  perc="battle", loud=-17)
T("combat_e", F, "Back to Back", 59, "dorian", 124, 82, dict(pad="strings", bass="bass_pluck", arp="harp", arp_rate=4, mel="strings",
                                                          mel2="brass"), mood="drive", perc="battle", loud=-17)

B = "boss"
for i, (bid, key, mode, bpm, extra) in enumerate([
        ("mossback", 55, "dorian", 120, {}), ("sporemother", 52, "phrygian", 112, {"pad": "choir"}),
        ("choirwarden", 57, "harmonic", 116, {"pad": "choir"}), ("rimefang", 50, "aeolian", 132, {}),
        ("galvanic", 53, "phrygian", 128, {"tex": "bell"}), ("cinderwurm", 48, "phrygian", 124, {}),
        ("lamplighter", 59, "harmonic", 108, {"tex": "musicbox"}), ("resonant", 54, "lydian", 118, {"tex": "glass"}),
        ("tidemother", 51, "aeolian", 112, {"pad": "choir"}), ("silentwarden", 49, "phrygian", 96, {"drone": "drone"}),
        ("rootsovereign", 56, "dorian", 112, {"pad": "choir"}), ("seamwatcher", 58, "lydian", 104, {"tex": "glass"}),
        ("somnarch", 48, "harmonic", 120, {"pad": "choir", "drone": "drone"})]):
    arr = dict(pad="strings", bass="bass_pluck", arp="pluck", arp_rate=4, mel="brass", mel2="choir")
    arr.update(extra)
    T(f"boss_{bid}", B, f"Guardian {i + 1:02d}", key, mode, bpm, 112 if bid != "somnarch" else 140, arr, mood="drive",
      perc="boss_final" if bid == "somnarch" else "boss", loud=-16.5, vowel="a", reverb=3.0, wet=0.35, register=67)

V = "travel"
T("travel_a", V, "The Long Passage", 62, "mixolydian", 96, 100, dict(pad="strings", bass="bass_pluck", arp="pluck", arp_rate=2, mel="flute"),
  mood="flow", perc="frame_soft")
T("travel_b", V, "Descending", 57, "dorian", 90, 104, dict(pad="pad", bass="bass", arp="harp", arp_rate=2, mel="strings"), mood="flow",
  perc="frame_soft")
T("travel_c", V, "Caravan Road", 64, "ionian", 100, 100, dict(pad="strings", bass="bass_pluck", arp="kalimba", arp_rate=2, mel="flute",
                                                             mel2="pluck"), mood="flow", perc="shaker")

W = "victory"
T("victory_a", W, "The Guardian Falls", 60, "ionian", 84, 52, dict(pad="strings", bass="bass", arp="harp", arp_rate=2, mel="brass", mel2="choir"),
  perc="taiko_slow", loud=-18, density=0.8)
T("victory_b", W, "Spoils of the Deep", 65, "lydian", 88, 48, dict(pad="choir", bass="bass", arp="harp", arp_rate=2, mel="brass"), loud=-18,
  density=0.8)
T("victory_c", W, "Kindled", 62, "ionian", 72, 64, dict(pad="choir", bass="bass", arp="harp", arp_rate=2, mel="strings", mel2="brass", drone="drone"),
  loud=-18, density=0.8, reverb=4.0, wet=0.45)


# ------------------------------------------------------------------------------------------------ composition

class Score:
    def __init__(self):
        self.events = []  # (t, instr, midi, dur, vel, pan, send)

    def note(self, t, instr, midi, dur, vel, pan=0.0, send=1.0, role="perc"):
        self.events.append((t, instr, midi, dur, vel * ROLE_GAIN.get(role, 1.0) * PERC_GAIN.get(instr, 1.0), pan, send))


def scale_note(key, mode, degree, octave_base=0):
    iv = MODES[mode]
    o, d = divmod(degree, 7)
    return key + octave_base + 12 * o + iv[d]


def chord_tones(key, mode, root_deg, seventh=False):
    degs = [root_deg, root_deg + 2, root_deg + 4] + ([root_deg + 6] if seventh else [])
    return [scale_note(key, mode, d) for d in degs]


def voice(prev, tones, low=55, high=74):
    """Close voicing of the chord tones nearest to the previous voicing (smooth voice leading)."""
    pcs = sorted({t % 12 for t in tones})
    best, best_cost = None, 1e9
    for base in range(low, high - 6):
        cand = []
        for pc in pcs:
            m = base + ((pc - base) % 12)
            cand.append(m)
        cand.sort()
        if cand[-1] > high:
            continue
        cost = sum(min(abs(c - p) for p in prev) for c in cand) if prev else abs(sum(cand) / len(cand) - (low + high) / 2)
        if cost < best_cost:
            best, best_cost = cand, cost
    return best


def make_motif(rng, cells, beats, bars=2):
    return [rng.choice(cells) for _ in range(bars)]


def melody_line(rng, key, mode, chords, rhythm, register, beats, final=False, start_deg=None):
    """Pitches for a rhythm over a list of chord degrees (one per bar). Strong beats land on chord tones, steps dominate,
    leaps are recovered by a step the other way; the last note resolves (tonic if final, else a chord tone)."""
    iv = MODES[mode]
    centre = (register - key) / 12 * 7
    deg = start_deg if start_deg is not None else int(round(centre)) + rng.choice([0, 2, 4])
    notes = []
    last_leap = 0
    for bar, cell in enumerate(rhythm):
        chord = chords[bar % len(chords)]
        ct = {(chord + k) % 7 for k in (0, 2, 4)}
        pos = 0.0
        for i, dur in enumerate(cell):
            strong = pos == 0 or (pos == 2 and beats == 4)
            last_note = bar == len(rhythm) - 1 and i == len(cell) - 1
            if last_leap:
                step = -int(math.copysign(1, last_leap))
                last_leap = 0
            else:
                r = rng.random()
                step = rng.choice([-1, 1]) if r < 0.62 else (rng.choice([-2, 2]) if r < 0.85 else rng.choice([-4, -3, 3, 4]))
                if abs(step) >= 3:
                    last_leap = step
            # pull towards the register centre
            if deg - centre > 5:
                step = -abs(step)
            elif centre - deg > 5:
                step = abs(step)
            deg += step
            if strong or last_note:
                # snap to the nearest chord tone
                for off in (0, -1, 1, -2, 2):
                    if (deg + off) % 7 in ct:
                        deg += off
                        break
            if last_note:
                target = {0} if final else ct
                for off in (0, -1, 1, -2, 2, -3, 3):
                    if (deg + off) % 7 in target:
                        deg += off
                        break
            notes.append((bar * beats + pos, dur, deg))
            pos += dur
    return notes


def to_midi(key, mode, deg):
    o, d = divmod(deg, 7)
    return key + 12 * o + MODES[mode][d]


def compose(tr: Track):
    rng = random.Random(zlib.crc32(tr.tid.encode()))
    sc = Score()
    beat = 60.0 / tr.bpm
    beats = tr.beats
    bar_s = beat * beats
    total_bars = max(12, int(round((tr.seconds - 6) / bar_s)))
    cells = (RHYTHM_CELLS_3 if beats == 3 else RHYTHM_CELLS)[tr.mood]
    a = tr.arr
    progs = PROGS[tr.mode]
    prog_a, prog_b = rng.sample(progs, 2)
    # form: intro (texture only) / A / B / A' / (B' or A'') / outro, filled to the length
    intro = 4 if tr.context not in ("combat", "boss", "victory") else 2
    outro = 4 if tr.context != "victory" else 3
    body = total_bars - intro - outro
    sec_len = 8
    order = []
    pattern = ["A", "B", "A2", "C", "A3", "B2"]
    while sum(sec_len for _ in order) + sec_len <= body:
        order.append(pattern[len(order) % len(pattern)])
    rem = body - sec_len * len(order)
    sections = [("I", intro)] + [(s, sec_len) for s in order] + ([("X", rem)] if rem else []) + [("O", outro)]
    motif_a = make_motif(rng, cells, beats)
    motif_b = make_motif(rng, cells, beats)
    prev_voicing = None
    t_bar = 1.0  # a second of pre-roll before the first downbeat
    bar_index = 0
    mel_a_cache = {}
    for name, nbars in sections:
        prog = prog_b if name.startswith("B") or name == "C" else prog_a
        energy = {"I": 0.5, "O": 0.45, "X": 0.55, "A": 0.75, "A2": 0.85, "A3": 0.95, "B": 0.85, "B2": 0.9, "C": 0.7}[name]
        has_melody = name not in ("I", "O", "X") and (name != "C" or tr.density > 0.5)
        if tr.density < 0.45 and name in ("B", "A3"):
            has_melody = rng.random() < 0.5
        for b in range(nbars):
            chord = prog[(bar_index + b) % len(prog)] if name not in ("O",) else (prog[0] if b < nbars - 1 else 0)
            if name == "O" and b >= nbars - 2:
                chord = 0
            t0 = t_bar + b * bar_s
            tones = chord_tones(tr.key, tr.mode, chord, seventh=rng.random() < 0.25 and tr.mode in ("dorian", "lydian", "mixolydian"))
            v = voice(prev_voicing, tones, tr.key - 7 + 12, tr.key + 12 + 9)
            prev_voicing = v
            hold = bar_s * (1.0 if name != "O" or b < nbars - 1 else 2.5)
            if "pad" in a:
                for i, m in enumerate(v):
                    sc.note(t0, a["pad"], m, hold * 1.02, 0.55 * energy + 0.2, pan=(i - 1) * 0.35, send=1.0, role="pad")
            if "drone" in a and b % 2 == 0:
                sc.note(t0, "drone", tr.key - 12, bar_s * 2.05, 0.7, pan=0, send=0.8, role="drone")
            root = to_midi(tr.key, tr.mode, chord) - 24
            while root < tr.key - 24:
                root += 12
            if "bass" in a and name != "I":
                if a["bass"] == "bass_pluck":
                    steps = [0, 2] if tr.mood != "drive" else [0, 1, 2, 3] if beats == 4 else [0, 1, 2]
                    for k, st in enumerate(steps):
                        mm = root + (7 if k % 2 and tr.mood == "drive" and rng.random() < 0.3 else 0)
                        sc.note(t0 + st * beat, "bass_pluck", mm, beat * (2 if tr.mood != "drive" else 0.9), 0.85, pan=0, send=0.3, role="bass")
                else:
                    sc.note(t0, "bass", root, hold * 0.98, 0.8, pan=0, send=0.3, role="bass")
            if "arp" in a:
                rate = a.get("arp_rate", 1)
                if name == "I" and tr.context in ("ecosystem", "home", "civilization"):
                    rate = max(1, rate // 2)
                step = beat / rate
                count = int(beats * rate)
                arp_tones = sorted(v) + [v[0] + 12, v[1] + 12]
                shape = rng.choice(["up", "updown", "broken"]) if b == 0 or rng.random() < 0.15 else locals().get("shape", "up")
                for k in range(count):
                    if tr.density < 0.5 and k % 2 == 1 and rng.random() < 0.5:
                        continue
                    if shape == "up":
                        idx = k % len(arp_tones)
                    elif shape == "updown":
                        cyc = list(range(len(arp_tones))) + list(range(len(arp_tones) - 2, 0, -1))
                        idx = cyc[k % len(cyc)]
                    else:
                        idx = [0, 2, 1, 3, 2, 4, 3, 1][k % 8] % len(arp_tones)
                    m = arp_tones[idx] + (12 if a["arp"] in ("glass", "musicbox", "kalimba") else 0)
                    accent = 1.0 if k % rate == 0 else 0.75
                    sc.note(t0 + k * step, a["arp"], m, step * 1.6, (0.45 + 0.35 * energy) * accent, pan=rng.uniform(-0.45, 0.45), send=0.9,
                            role="arp")
            if "tex" in a and rng.random() < 0.55 * tr.density + 0.15:
                for k in range(rng.randint(1, 3)):
                    m = rng.choice(v) + 24
                    sc.note(t0 + rng.uniform(0, bar_s), a["tex"], m, 1.5, 0.35, pan=rng.uniform(-0.8, 0.8), send=1.0, role="tex")
        # melody per 8-bar section: phrase 1 (motif + answer) / phrase 2 (motif + cadence)
        if has_melody and "mel" in a:
            inst = a["mel"]
            if name in ("A2", "A3") and "mel2" in a:
                inst = a["mel2"] if name == "A3" else a["mel"]
            motif = motif_a if name.startswith("A") else motif_b if name.startswith("B") else make_motif(rng, cells, beats)
            chords = [prog[(bar_index + k) % len(prog)] for k in range(nbars)]
            key_name = name[0]
            if key_name in mel_a_cache and rng.random() < 0.8:
                line = mel_a_cache[key_name]  # recapitulate (with ornament / octave changes below)
            else:
                resp = make_motif(rng, cells, beats)
                p1 = melody_line(rng, tr.key, tr.mode, chords[:4], motif + resp, tr.register, beats)
                start = p1[0][2]
                p2 = melody_line(rng, tr.key, tr.mode, chords[4:8], motif + [[beats]] if beats == 4 else motif + [[3]], tr.register, beats,
                                 final=True, start_deg=start)
                p2 = [(s + 4 * beats, d, g) for s, d, g in p2]
                # last bar of p2 is a held cadence note
                line = p1 + p2
                mel_a_cache.setdefault(key_name, line)
            octave = 12 if name == "A3" and inst in ("flute", "bell", "glass", "musicbox") else 0
            for s, d, g in line:
                if s >= nbars * beats:
                    continue
                m = to_midi(tr.key, tr.mode, g) + octave
                phrase_pos = (s % (4 * beats)) / (4 * beats)
                vel = 0.55 + 0.3 * math.sin(math.pi * phrase_pos) + 0.1 * energy
                tt = t_bar + s * beat + rng.uniform(-0.008, 0.008)
                sc.note(tt, inst, m, d * beat * 0.95, vel, pan=0.08, send=0.85, role="mel")
            # a countermelody in thirds below for the climactic section
            if name == "A3" and "mel2" in a and tr.mood != "calm":
                for s, d, g in line[::2]:
                    if s < nbars * beats:
                        sc.note(t_bar + s * beat, a["mel2"], to_midi(tr.key, tr.mode, g - 2) - 12, d * beat * 1.8, 0.4, pan=-0.3, send=0.9, role="mel2")
        # percussion
        if tr.perc and name not in ("I",) or tr.perc and tr.context in ("combat", "boss"):
            groove(sc, tr, t_bar, nbars, beat, beats, name, rng)
        t_bar += nbars * bar_s
        bar_index += nbars
    return sc, t_bar + 2.0


def groove(sc, tr, t0, nbars, beat, beats, name, rng):
    p = tr.perc
    for b in range(nbars):
        tb = t0 + b * beat * beats
        last = name == "O" and b == nbars - 1
        if p == "frame_soft":
            sc.note(tb, "frame", 0, 0, 0.55, pan=-0.2, send=0.6)
            if beats == 4:
                sc.note(tb + 2.5 * beat, "frame", 0, 0, 0.35, pan=0.2, send=0.6)
        elif p == "frame_dance":
            for k, v in ((0, 0.7), (1.5, 0.4), (2, 0.6), (3, 0.4), (3.5, 0.3)):
                sc.note(tb + k * beat, "frame", 0, 0, v, pan=0.1 * (k - 2), send=0.5)
            for k in range(beats * 2):
                sc.note(tb + k * beat / 2, "shaker", 0, 0, 0.5 if k % 2 else 0.3, pan=0.4, send=0.3)
        elif p == "shaker":
            for k in range(beats * 2):
                sc.note(tb + k * beat / 2, "shaker", 0, 0, 0.45 if k % 2 else 0.25, pan=0.35, send=0.3)
        elif p == "taiko_slow":
            if b % 2 == 0:
                sc.note(tb, "taiko", 0, 0, 0.6, send=0.7)
        elif p == "tick":
            for k in range(beats):
                if rng.random() < 0.7:
                    sc.note(tb + k * beat, "stick", 0, 0, 0.35 if k else 0.5, pan=rng.uniform(-0.5, 0.5), send=0.8)
        elif p == "storm":
            sc.note(tb, "taiko", 0, 0, 0.45, send=0.7)
            if b % 4 == 3:
                sc.note(tb + 2 * beat, "metal", 0, 0, 0.4, pan=rng.uniform(-0.5, 0.5), send=1.0)
            for k in range(beats * 2):
                sc.note(tb + k * beat / 2, "shaker", 0, 0, 0.3, pan=0.3, send=0.3)
        elif p == "forge":
            sc.note(tb, "taiko", 0, 0, 0.7, send=0.5)
            sc.note(tb + 2 * beat, "metal", 0, 0, 0.45, pan=0.3, send=0.6)
            sc.note(tb + 3.5 * beat, "metal", 0, 0, 0.25, pan=-0.3, send=0.6)
            sc.note(tb + 1 * beat, "frame", 0, 0, 0.4, send=0.4)
        elif p in ("battle", "boss", "boss_final"):
            if name == "I":
                sc.note(tb, "taiko", 0, 0, 0.8, send=0.6)
                continue
            pat = [(0, 1.0), (0.75, 0.5), (1.5, 0.7), (2, 0.9), (3, 0.6), (3.5, 0.5)]
            for k, v in pat:
                sc.note(tb + k * beat, "taiko", 0, 0, v * 0.85, send=0.5)
            for k in range(beats * 2):
                sc.note(tb + k * beat / 2, "frame", 0, 0, 0.35 if k % 2 else 0.5, pan=rng.uniform(-0.3, 0.3), send=0.4)
            if p != "battle" and b % 4 == 0 or p == "boss_final" and b % 2 == 0:
                sc.note(tb, "metal", 0, 0, 0.55, pan=rng.uniform(-0.4, 0.4), send=1.0)
        if last and p in ("battle", "boss", "boss_final", "forge", "taiko_slow"):
            sc.note(tb + beats * beat, "taiko", 0, 0, 0.9, send=1.0)


# ------------------------------------------------------------------------------------------------ rendering

def render(tr: Track):
    sc, length = compose(tr)
    n = int((length + 5.0) * SR)
    dry = np.zeros((2, n), dtype=np.float32)
    wet = np.zeros((2, n), dtype=np.float32)
    cache = {}
    for i, (t, inst, m, dur, vel, pn, send) in enumerate(sc.events):
        if inst in ("taiko", "frame", "shaker", "metal", "stick"):
            key = (inst, i % 6)
            if key not in cache:
                cache[key] = synth.perc(inst, 1.0, zlib.crc32(f"{tr.tid}{inst}{i % 6}".encode()))
            x = cache[key] * vel
        else:
            q = round(dur * 8) / 8
            key = (inst, m, q)
            if key not in cache:
                f = synth.midi_hz(m)
                seed = zlib.crc32(f"{tr.tid}{inst}{m}".encode()) & 0xFFFF
                if inst == "bass_pluck":
                    cache[key] = synth.bass(f, q, 1.0, seed, pluck_env=True)
                elif inst == "pad":
                    cache[key] = synth.pad(f, q, 1.0, seed, bright=tr.bright)
                elif inst == "strings":
                    cache[key] = synth.strings(f, q, 1.0, seed, bright=tr.bright)
                elif inst == "choir":
                    cache[key] = synth.choir(f, q, 1.0, seed, vowel=tr.vowel)
                else:
                    cache[key] = synth.INSTR[inst](f, q, 1.0, seed)
            x = cache[key] * vel
        s0 = int(t * SR)
        e0 = min(n, s0 + len(x))
        if e0 <= s0:
            continue
        st = synth.pan(x[:e0 - s0], pn)
        dry[:, s0:e0] += st
        wet[:, s0:e0] += st * send
    ir = synth.reverb_ir(tr.reverb, zlib.crc32(tr.tid.encode()) & 0xFFFF, damp=1 - tr.bright)
    verb = synth.apply_reverb(wet, ir, 1.0)[:, :n]
    mix = dry * (1 - tr.wet * 0.4) + verb * tr.wet * 1.1
    # trim to the composed length + reverb tail, then master
    tail = int((length + min(3.5, tr.reverb)) * SR)
    mix = mix[:, :tail]
    fo = 6.0 if tr.context not in ("victory",) else 3.5
    out = synth.master(mix, tr.loud, fade_in=1.0 if tr.context in ("combat", "boss") else 2.5, fade_out=fo)
    return out, sc


def encode(tr, audio):
    d = os.path.join(OUT, tr.context)
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, tr.tid + ".ogg")
    with tempfile.NamedTemporaryFile(suffix=".f32", delete=False) as f:
        f.write(np.ascontiguousarray(audio.T).astype("<f4").tobytes())
        raw = f.name
    try:
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", raw, "-ar", "32000", "-c:a", "libvorbis",
                        "-b:a", "40k", "-metadata", f"title={tr.title}", "-metadata", "artist=Underdeep (original, procedurally composed)",
                        "-metadata", "copyright=Part of the Underdeep mod; see docs/SOUNDTRACK_LICENSE.md", path], check=True)
    finally:
        os.unlink(raw)
    return path


def analyse(audio):
    mono = audio.mean(axis=0)
    block = SR // 2
    rms = np.array([np.sqrt(np.mean(mono[i:i + block] ** 2)) for i in range(0, len(mono) - block, block)])
    db = 20 * np.log10(np.maximum(rms, 1e-9))
    inner = db[int(3 / 0.5):max(int(3 / 0.5) + 1, len(db) - int(7 / 0.5))]
    longest_quiet = 0
    run = 0
    for v in inner:
        run = run + 1 if v < -45 else 0
        longest_quiet = max(longest_quiet, run)
    spec = np.abs(np.fft.rfft(mono[: min(len(mono), SR * 60)]))
    freqs = np.fft.rfftfreq(min(len(mono), SR * 60), 1 / SR)
    centroid = float((spec * freqs).sum() / max(1e-9, spec.sum()))
    return {"seconds": round(audio.shape[1] / SR, 1), "peak_dbfs": round(float(20 * np.log10(np.abs(audio).max() + 1e-9)), 2),
            "rms_dbfs": round(float(20 * np.log10(np.sqrt(np.mean(mono ** 2)) + 1e-9)), 2),
            "longest_quiet_s": longest_quiet * 0.5, "centroid_hz": round(centroid), "clipped": int((np.abs(audio) > 0.999).sum())}


def job(tid):
    tr = next(t for t in TRACKS if t.tid == tid)
    audio, sc = render(tr)
    path = encode(tr, audio)
    info = analyse(audio)
    info.update({"id": tr.tid, "context": tr.context, "title": tr.title, "notes": len(sc.events), "key": tr.key, "mode": tr.mode,
                 "bpm": tr.bpm, "file": os.path.relpath(path, ROOT), "bytes": os.path.getsize(path)})
    return info


def write_registry():
    """sounds.json (one streamed music event per track) and music_tracks.json (the client's track table)."""
    assets = os.path.join(ROOT, "src", "main", "resources", "assets", "underdeep")
    sounds_path = os.path.join(assets, "sounds.json")
    sounds = json.load(open(sounds_path)) if os.path.exists(sounds_path) else {}
    sounds = {k: v for k, v in sounds.items() if not k.startswith("music.")}
    table = []
    for t in TRACKS:
        sounds[f"music.{t.tid}"] = {"sounds": [{"name": f"underdeep:music/{t.context}/{t.tid}", "stream": True}]}
        table.append({"id": t.tid, "context": t.context, "title": t.title, "seconds": t.seconds})
    with open(sounds_path, "w") as f:
        json.dump(dict(sorted(sounds.items())), f, indent=1)
        f.write("\n")
    with open(os.path.join(assets, "music_tracks.json"), "w") as f:
        json.dump(table, f, indent=1)
        f.write("\n")


def main():
    args = sys.argv[1:]
    write_registry()
    if args == ["--list"]:
        for t in TRACKS:
            print(t.context, t.tid, t.title)
        print(len(TRACKS), "tracks")
        return
    ids = args or [t.tid for t in TRACKS]
    os.makedirs(MANIFEST, exist_ok=True)
    with Pool(int(os.environ.get("JOBS", "4"))) as pool:
        infos = pool.map(job, ids, chunksize=1)
    path = os.path.join(MANIFEST, "manifest.json")
    old = {}
    if os.path.exists(path):
        old = {i["id"]: i for i in json.load(open(path))}
    for i in infos:
        old[i["id"]] = i
        print(f'{i["id"]:24s} {i["seconds"]:6.1f}s peak {i["peak_dbfs"]:6.2f} rms {i["rms_dbfs"]:6.2f} quiet {i["longest_quiet_s"]:4.1f}s '
              f'{i["bytes"] // 1024:5d} KiB')
    ordered = [old[t.tid] for t in TRACKS if t.tid in old]
    with open(path, "w") as f:
        json.dump(ordered, f, indent=1)


if __name__ == "__main__":
    main()
