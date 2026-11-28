# Underdeep issue registry (Stage 12)

Severity:
- **P0**: release blocker. Crash, save corruption, major duplication or impossible progression.
- **P1**: major. A broken core mechanic, severe lag, or inaccessible important content.
- **P2**: moderate. A localized defect.
- **P3**: minor.

Spoiler-sensitive details are kept in `docs/private/BUGS_PRIVATE.md`. None so far.

| Id | Sev | System | Summary | Reproduction | Root cause | Fix | Status | Regression test |
|---|---|---|---|---|---|---|---|---|
| UD-001 | P3 | Wildlife / tests | `ArrayIndexOutOfBoundsException` in `RegionId.fromLong` from `WildlifeManager.typeOf` | `SUITE=stage2 scripts/selftest.sh 42 <dir> create` (Stage 3 behaviour checks) | A fixture creature had no home region yet (`Long.MIN_VALUE`); decoding it read layer index 8 | `typeOf` returns null for an unset region; the fixture assigns its region from the terrain report | Fixed (retest: see TESTING) | `SUITE=stage2`, Stage 3 checks |
| UD-002 | P3 | Tests | "every block has a loot table" fails for 44 mechanism blocks | Same suite | The blocks are declared `dropsNothing()` (unbreakable or technical), so having no table is intended. The check did not account for this | The check skips blocks whose loot key is `LootTables.EMPTY` | Fixed (retest: see TESTING) | `SUITE=stage2` |
| UD-003 | P2 | Wildlife AI | "predator hunts suitable prey" check: prey took no damage in the 600-tick window | Same suite | Under investigation. Since Stage 10, a predator's attack is the archetype combat goal, which has wind-up and recovery timings | — | Investigating | `SUITE=stage2` |
| UD-004 | P2 | Music | About 4 s of silence after teleports or chunk loading | Stage 11 in-game audio capture | The biome under the player read as non-Underdeep while its chunk loaded, so the music director stopped | Underdeep-world detection is now sticky, and the last region is kept | Fixed in 0.12.0 (verified by audio capture) | Audio capture run, documented in TESTING |
