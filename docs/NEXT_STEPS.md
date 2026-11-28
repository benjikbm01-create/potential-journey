# Underdeep — Next Steps

## Stage 12 continuation (checkpoint reached 2026-11-28)

Remaining, in priority order:
1. **UD-008 (private):** audit every mandatory puzzle for recovery when an element is mined. Either make required
   elements unbreakable or reset them from `SiteLayout`.
2. **Real multiplayer:**
   - 2–6 production clients joining the production server (`scripts/prod_client.sh` can launch several clients;
     `--join-server` needs a dedicated server running);
   - simultaneous trading and crafting;
   - disconnects during guardian fights.
3. **Soak:** a 1–2 hour production server run with a client walking a long tour. Watch heap, MSPT, entity counts and
   save growth.
4. **Exploration density:** the owner had trouble finding dungeons. Options:
   - raise `exploration.dungeonDensity` for fresh worlds (it changes placement, so existing worlds would show partial
     sites at the edge of explored land);
   - more rumours and charts.
5. **Handbook and advancement walk-through** in the production client; **EMI spoiler exposure** decision (documented in
   MODPACK).
6. **Worldgen CPU** (UD-006): profile `SurfacePainter.paint`, `CavernGraph.node`, `RegionMap.cellAt`, and
   `generateFeatures`.

## Previous: user play-testing of Stage 11 (and Stage 10)

Do not start Stage 12 until the user assigns it. Stage 11 needs no new world. Stage 10 content still needs a generator v5 world.

### Listen and look (the user, spoiler-free)
- Music: walk between regions, enter a settlement, stand near your bed, start a fight, ride, enter a ruin. The music
  should change after a few seconds with crossfades, never stop, and never play vanilla music. Try `/underdeep_music`,
  `/underdeep_music gaps`, `/underdeep_music next`, `/underdeep_music volume 60`. Report any track that sounds bad by
  its title (shown by `/underdeep_music`). Each track is one line in `tools/music/compose.py`.
- Creatures: watch them walk, run, eat, sleep, fly and swim. The animation has never been watched in motion.

### Stage 11.5 textures: done
See `docs/TEXTURE_STANDARDS.md`. Remaining texture work:
- NPC skins and outfits.
- GUI textures.
- Check armour worn in game.
- Optionally redraw the 222 remastered unique item icons.

### Stage 11 work remaining (in priority order; see docs/ASSET_QUALITY.md)
1. Guardian models: full redesign (private previews in docs/private/previews/after/). Keep part names and poses, or
   update the poses together with the rig.
2. Animation verification in motion: a recorded client run (or the user's report); tune gait speeds and amplitudes per
   species.
3. NPC appearance: civilization clothing silhouettes (hoods, aprons, robes), profession props, stable variation.
4. Sound effects and ambience: none of our own yet (creatures use vanilla sounds). The synthesizer in
   `tools/music/synth.py` can produce them.
5. Equipment and item icons, plants and blocks, furniture and workstations, UI/Handbook/HUD: not audited in Stage 11.
6. An in-game music settings screen (the command exists).

### Stage 12 handoff (testing, optimisation, balance)
- Lag: the user reported singleplayer spikes; not yet profiled with Spark/JFR. Measured: terrain-stage fill ≈13 ms per
  chunk single-threaded (v5 +2–3 % vs v4); column cost +10 %. Candidates to profile: chunk decoration and site
  placement, settlement simulation ticks, wildlife AI, NPC pathfinding, inhabitant embodiment, entity rendering.
- Cave network: steep chamber floors and lake shores still block some on-foot routes (see docs/CAVES.md §5).
- Guardian balance and fun (only mechanics are tested), real multiplayer (4–6 players), scaling numbers, reward
  values, pregeneration with Chunky, memory over long sessions, save growth, Handbook accuracy, obtainability and
  progression integrity in a real playthrough.

### Known issues / limitations of Stage 11
- The music was never listened to by a person. It was validated only by analysis (decode, loudness, spectra, no gaps).
- Creature animation is not verified in motion. Rest poses are verified in a real client.
- Guardians are still blocky. NPCs, items, blocks and UI are unchanged.
- The Stage 2/3 self-test suite (`SUITE=stage2`) shows 3 stale failures that predate Stage 11:
  - loot tables for Stage 9/10 technical blocks;
  - one predator-hunt check;
  - an ArrayIndexOutOfBounds in a Stage 3 check, from `RegionId.fromLong` via `WildlifeManager.typeOf`.

  These need fixing in Stage 12.

### Known issues / limitations of Stage 10
- Not hands-on tested in a real client: all combat feel, animations, boss presentation, HUD, keybinds.
- Boss models are functional articulated rigs with procedural textures (Stage 11 rebuild).
- Companions do not get special boss-fight behaviour beyond their existing combat (they are not targeted specially).
- Earlier known issues of Stages 4–9 still apply.
