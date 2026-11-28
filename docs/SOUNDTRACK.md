# Underdeep soundtrack (Stage 11)

## Summary
- **73 original tracks**, about 135 minutes in total: 30 ecosystem tracks (2 for each of the 15 regions), 8 civilization tracks (one per civilization), 13 guardian tracks, 5 home, 6 dungeon/ruin, 5 combat, 3 travel and 3 victory.
- The tracks are streamed OGG Vorbis files (32 kHz stereo, about 34 kbps, ~32 MB in total) under
  `assets/underdeep/sounds/music/<context>/<id>.ogg`. Each track is declared as `underdeep:music.<id>` in `sounds.json`
  with `"stream": true`. `music_tracks.json` is the client's track table.
- Music is continuous by default. The client crossfades between tracks and switches by context with hysteresis. Vanilla
  music is suppressed in Underdeep worlds. An optional vanilla-style mode adds gaps between tracks.

## Production (how the music is made)
`tools/music/compose.py` composes every track from a specification. `tools/music/synth.py` synthesizes it.

- **Composition.** Rule-based and deterministic: the seed is the track id.
  - Modal harmony uses curated progressions for each mode (ionian, dorian, phrygian, lydian, mixolydian, aeolian,
    harmonic minor), with nearest-voice leading.
  - The form is intro / A / B / A′ / C / A″ / outro, fitted to the target length.
  - Melodies grow from 2-bar motifs. Each motif is answered and varied, strong beats land on chord tones, leaps are
    recovered by steps, and phrases end on cadences. Melodies are recapitulated in the A sections, where a countermelody
    is added.
  - Other layers: bass lines, arpeggio and ostinato patterns, sparkle textures, and percussion grooves for each context
    (frame-drum dances, forge rhythms, battle and guardian taiko patterns).
- **Instruments.** All synthesized from scratch, with no samples, soundfonts or recordings:
  - additive pads and strings;
  - a formant-filtered choir;
  - a flute with breath noise;
  - FM bells and kalimba;
  - modal marimba and glass;
  - Karplus–Strong plucked strings and harp;
  - bass, drone and brass swell;
  - synthesized taiko, frame drum, shaker, stick and metal.
- **Mixing and mastering.**
  - Role gains were set from measured stem levels: melody on top, pads and bass supporting, percussion underneath.
  - A stereo reverb (diffuse decaying-noise impulse response, sized for each context), sent per instrument.
  - A 38 Hz high-pass and gentle top-end smoothing.
  - Loudness is normalised to a gated RMS target for each context: calm about −20 dBFS, dungeon/abyss −21 to −22, combat
    −17, guardian −16.5, victory −18.
  - A tanh soft limiter at −1 dBFS, plus fade-in and fade-out.
- **Regenerate:** `python3 tools/music/compose.py` renders every track (about 1.5 minutes on 3 cores);
  `python3 tools/music/compose.py <id>…` renders selected tracks. Either command also rewrites `sounds.json` and
  `music_tracks.json`, and records the analysis in `tools/music/out/manifest.json`. Then re-run
  `tools/assets/stage2_assets.py` so the track titles reach the language file.

## Validation (honest)
I can't listen to audio, and the tracks have **not been listened to by a human yet**. They were validated by
analysis instead (results are in `tools/music/out/manifest.json`):
- Every file decodes with ffmpeg.
- Peak ≤ −1 dBFS, and no clipped samples.
- No silence longer than 2 s inside a track (measured on 0.5 s blocks, below −45 dBFS). This holds for all 73 tracks.
- Loudness falls in the intended range (−22.4 to −17.0 dBFS RMS).
- Octave-band spectral balance was checked. This found sub-bass that was too strong in the percussive tracks, which was
  fixed by raising the taiko pitch, reducing its level, and moving the high-pass to 38 Hz.
- Log-frequency spectrograms were checked (`tools/music/spectrogram.py <ogg> <png>`).

What still needs human ears: whether the melodies are memorable, and whether any synthesized timbre is unpleasant.
Expect to tune per track. Each track's parameters are a single line in `TRACKS`.

## Music context architecture
- **Client** (`client/audio/MusicDirector`): every 10 ticks it detects the highest-priority context.

  | Priority | Context | Source |
  |---|---|---|
  | 1 (highest) | victory | `boss_audio "victory"`; lasts 45 s |
  | 2 | guardian | `boss_audio` prepare / activate / phase, with the guardian id |
  | 3 | combat | hostile mobs attacking or targeting the player within 14 blocks; lingers 9 s |
  | 4 | dungeon / ruin / secret | the Stage 9 `explore_context` payload |
  | 5 | settlement (civilization) | the server `music_context` payload |
  | 6 | home | the server payload: within 28 blocks of the player's respawn point |
  | 7 | travel | riding |
  | 8 | ecosystem | the biome at the player's position |
  | 9 (lowest) | fallback | home tracks |

- **Server** (`audio/MusicContext`): every 2 s it sends `settlement:<civ>`, `home` or an empty string, and only when the
  value changes. The settlement comes from the pure `SettlementMap` (no chunk loading); home is the respawn point.
- **Hysteresis.**
  - Guardian and victory contexts switch immediately.
  - Combat switches after 0.5 s.
  - Returning from combat waits 2 s.
  - Other context changes wait 4 s.
  - A change from one ecosystem to another waits 8 s, so crossing a region border does not flip the music.
- **Selection.** A weighted random choice from the context's pool:
  - The last two tracks played are excluded when the pool allows it.
  - Tracks absent from the recent history (6 deep) weigh double.
- **Crossfades.**
  - 1.5 s into or out of combat and guardian contexts; 4 s otherwise.
  - In continuous mode, the next track starts crossfading 4 s before the current one ends.

## Playback lifecycle
- Tracks are `MovingSoundInstance`s in the MUSIC category, so Minecraft's Music slider applies. They are non-positional,
  and `shouldAlwaysPlay` lets a track start at volume zero and fade in.
- On world disconnect every track stops and the state resets. Leaving an Underdeep world, or disabling music, fades the
  current track out over 2 s. While the game is paused, Minecraft's own sound system pauses streams.
- `MusicTrackerMixin` cancels the vanilla `MusicTracker` tick while the director owns the music. The director also stops
  any vanilla track that is already playing.

## Settings
- `config/underdeep-client.properties`:
  - `music.enabled` (default true)
  - `music.volume` (0–2, multiplies the Music slider; default 1)
  - `music.gaps` (default false; true gives vanilla-style silences of 40–130 s between calm tracks)
- The client command `/underdeep_music [on|off|gaps|continuous|next|volume <0-200>]` changes these settings in game and
  shows what is playing.
- Not done: an in-game options screen (the command is the interface for now).

## Packaging and compatibility
- The soundtrack ships inside the mod JAR, adding about 32 MB. Tracks are streamed, so memory use stays small. A separate
  resource-pack package is possible later, because the track table and `sounds.json` are data.
- The dedicated server never loads client classes: `MusicContext` is common code that only sends a payload.
- Clients without the payload channel simply get no settlement or home context.
- Sodium/Iris have no audio interaction. Other music mods that also drive `MusicTracker` would be suppressed in Underdeep
  worlds while Underdeep music is enabled; `music.enabled=false` hands music back to them.

## Provenance and licensing
See `docs/SOUNDTRACK_LICENSE.md`. In short, every track was generated by code in this repository from parameters in
this repository. No third-party audio was used.
