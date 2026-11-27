# Underdeep — Next Steps

## Immediate: user play-testing of Stage 9, then Stage 10

Do not start Stage 10 until the user assigns it. **Stage 9 needs a new world** (generator v4): exploration sites
generate only in worlds created with this version (config `exploration.inOlderWorlds` adds them to new chunks of
older worlds, with cut sites at the border of explored land). All other Stage 9 items work in existing worlds.

### Prerequisites (verify first)
1. `./gradlew build` succeeds (45 unit tests).
2. `SUITE=stage9 scripts/selftest.sh 42 runs/s9 create` → 0 FAIL (~2–3 min); `SUITE=audit …` → 0 FAIL (~1.5 min).
3. Read `docs/EXPLORATION.md`, then `docs/ARCHITECTURE.md` §5j. Spoiler-sensitive design is in `docs/private/`.

### Manual test checklist (spoiler-free)
- Walk out from spawn: small ruins should appear along the way; check the discovery message and that the place
  appears in the Handbook journal (H → Journal) with a live distance and direction.
- Read an inscription stone and a lore fragment; check the journal copy.
- Find a dungeon: solve its puzzles, watch seals open, check traps are telegraphed and can be jammed, take a
  coffer share with two players (each gets one), reach the heart.
- Ask inhabitants about rumours, buy a surveyor's chart, have a relic identified, report findings.
- Delver's compass: discovered places appear in its target list.
- Travel encounters while walking; place a warding bell at home.
- Advancements: the Expedition tab; toasts on discovery.
- Look for visual problems in structures (floating blocks, cut walls, misplaced ladders) and report coordinates
  (`/underdeep explore here` identifies the site).

### Stage 10 hooks
See `docs/EXPLORATION.md` §14: reserved rooms in some dungeons are sealed empty space for later encounters; guards
are vanilla undead woken through `Traps.wake`; completion events exist for every dungeon scale.

### Stage 11 hooks (permanent requirement)
Continuous soundtrack (~60–80 tracks, crossfades, option for vanilla-style gaps) — see `docs/MASTER_DESIGN.md`.
The exploration audio context is already sent to the client (`underdeep:explore_context`).

### Known issues / limitations of Stage 9
- Not hands-on tested in a real client (see TESTING.md "Not tested"); structure appearance was judged from the
  static audit and ASCII slices, not from screenshots.
- Textures of mechanisms, relics and artifacts are procedural placeholders.
- Three planned encounter kinds that need creature behaviour are deferred to Stage 10.
- The rarest families need a wide search area to appear (60 of 76 within 2000 blocks at seed 42).
- Sites are not generated in chunks that existed before the update (by design).
- Earlier known issues of Stages 4–8 still apply.
