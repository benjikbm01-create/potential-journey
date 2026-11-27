# Underdeep — Next Steps

## Immediate: user play-testing of Stage 10, then Stage 11

Do not start Stage 11 until the user assigns it. **Stage 10 needs a new world** (generator v5): the connected cave
network and the guardian lairs exist only in worlds created with it. Older worlds keep their terrain and still get the
new combat, creature behaviour, items and Handbook pages, but no lairs (so the game cannot be beaten there).

### Prerequisites (verify first)
1. `./gradlew build` (unit tests incl. the descent walk check; slow measurements with `-Dunderdeep.bench=true`).
2. `SUITE=stage10 scripts/selftest.sh 42 runs/s10 create` (~3–5 min), `SUITE=audit …`, `SUITE=stage9 …`.
3. Read `docs/CAVES.md`, `docs/COMBAT.md`, ARCHITECTURE §5k; private design in `docs/private/BOSSES.md`.

### Manual test checklist (spoiler-free)
- Caves: walk out from spawn through side passages; find a spiral descent to the deep layer and walk back up.
- Combat: dodge (Left Alt), heavy attacks with several weapon kinds (sneak + attack), parry with a shield and with a
  sword, counters after a parry, stamina bar, staggering creatures; creature fights (packs, chargers, spitters).
- Guardians: find an altar, wake an early guardian with friends and alone; downed/revive; retreat and retry; rewards.
- Progression: Handbook "Beating Underdeep"; Signs; keystone direction; the door; the final encounter; the ending.

### Stage 11 handoff (visual and audio — model quality is the top priority)
See docs/COMBAT.md §5 and CLAUDE.md. In order: (1) rebuild all models with handcrafted articulated geometry, verified
in-game one by one (creatures, bosses, NPCs, armour, weapons, tools, plants, furniture, workstations, decorations);
(2) creature anatomy and identity; (3) boss models (rigs in assets/underdeep/bosses, poses per move — mechanics do not
depend on them); (4) humanoid NPCs; (5) natural animation; (6) combat animation incl. first person; (7) textures;
(8) equipment models; (9) furniture/workstations; (10) environments; (11) structures; (12) UI; (13) environmental
sound; (14) continuous original soundtrack (60–80 tracks, crossfades, vanilla-gap option; hooks:
`underdeep:explore_context`, `underdeep:boss_audio`); (15) unique guardian music; (16) final encounter presentation.

### Stage 12 handoff (testing, optimisation, balance)
- Lag: the user reported singleplayer spikes; not yet profiled with Spark/JFR. Measured: terrain-stage fill ≈13 ms per
  chunk single-threaded (v5 +2–3 % vs v4); column cost +10 %. Candidates to profile: chunk decoration and site
  placement, settlement simulation ticks, wildlife AI, NPC pathfinding, inhabitant embodiment, entity rendering.
- Cave network: steep chamber floors and lake shores still block some on-foot routes (see docs/CAVES.md §5).
- Guardian balance and fun (only mechanics are tested), real multiplayer (4–6 players), scaling numbers, reward
  values, pregeneration with Chunky, memory over long sessions, save growth, Handbook accuracy, obtainability and
  progression integrity in a real playthrough.

### Known issues / limitations of Stage 10
- Not hands-on tested in a real client: all combat feel, animations, boss presentation, HUD, keybinds.
- Boss models are functional articulated rigs with procedural textures (Stage 11 rebuild).
- Companions do not get special boss-fight behaviour beyond their existing combat (they are not targeted specially).
- Earlier known issues of Stages 4–9 still apply.
