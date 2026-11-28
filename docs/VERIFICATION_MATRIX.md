# Feature verification matrix (Stage 12)

## What the classifications mean
- **Verified:** implemented and exercised by an executed test. The method column says how.
- **Partly verified:** implemented, but only some of it was exercised, or only by static or accelerated checks.
- **Unverified:** implemented, but no executed test covers it (code exists, nothing more is claimed).
- **Partial / Broken / Missing:** as stated.

## Test methods
- **U:** unit tests (`./gradlew test`).
- **S:** dedicated-server self-test suites. `S*` means the run used accelerated or ticked fixtures, not real play.
- **P:** production JAR on the official Fabric server launcher (Stage 12).
- **C:** real client under Xvfb with software OpenGL (screenshots, audio capture).
- **J:** JFR or Spark profiling.
- **H:** human play. No feature has been tested by human play yet.

Spoiler-sensitive items (guardians, secrets, the final progression) are tracked privately. Only their counts and
status appear here.

| Area | Feature | Status | Method and evidence | Multiplayer | Persistence | Known issues |
|---|---|---|---|---|---|---|
| World | Underground terrain, 15 ecosystems, two layers | Verified | U, S (full matrix earlier stages), C screenshots | — | Pure function of seed | Generation is CPU-heavy (UD-006) |
| World | Connecting passages, chambers, spiral descents (v5) | Partly verified | S: VoxelWalk on the model plus generated-block walks (`stage10` suite, 64/64) | — | Pure | Some steep chamber floors (docs/CAVES.md §5); no human traversal yet |
| World | Chunky pregeneration | Verified | P: 2,601 chunks in 46–48 s, about 18 KB per chunk, TPS 20 | — | Saved | — |
| World | Bundled cave-stone texture pack | Verified | C: loads (`underdeep:underdeep_caverns`), renders | — | — | — |
| Flora | Plants, crops, trees, fungi, growth | Partly verified | S `stage2`, contact sheets, C screenshots | — | — | Visual only in Stage 11.5 |
| Wildlife | 60 species, regional populations, spawning | Verified | S `stage2` (Stage 3 checks), C zoo (60/60 render) | — | Abstract populations saved | — |
| Wildlife | Predator hunting creature prey | Broken → fixed | S* check failed. Root cause: archetype circling (UD-003). Fix: no circling against prey. Retest recorded in TESTING | — | — | Verify in play |
| Wildlife | Taming, mounts, companions | Partly verified | S* `stage2` / `stage4` | — | Saved | No human test |
| Wildlife | Creature animation (Stage 11 rig) | Unverified in motion | C rest poses only; offline gait frames | — | — | Needs watching in game |
| Civilizations | Settlement placement, plans, generation | Verified | S `stage4` (193/193), gallery tours (earlier stages) | — | Pure + records | Planning moved off the server thread (UD-005) |
| Civilizations | Inhabitant minds, dialogue, relationships | Partly verified | S* `stage4` | Untested with several players | Records saved | — |
| Economy | Production, trade, money audit | Verified | S `stage4` (money audit exact) | Untested | Saved | — |
| Survival | Foods, stations, hazards, farming | Partly verified | S `stage8`-era suites; audit | Untested | Attachment saved | No human test |
| Equipment | Materials, tiers, upgrades, salvage | Verified (static + server) | S `audit`: 751/751 obtainable; equipment checks | Untested | Item components | — |
| Guide | Handbook, advancements | Partly verified | S `audit` (link/id checks); handbook content generated from data | — | Per player | Search/navigation UI not driven by a human |
| Exploration | Ruins, landmarks, dungeons, secrets | Verified (mechanics) | S `stage9`: 228 layouts, 180/180 seals, placement density (778 sites within 2 km) | Untested | Saved | Mineable puzzle elements: softlock risk to check (private) |
| Combat | Stamina, dodge, heavy attacks, parries, archetypes | Partly verified | S* `stage10` (64/64) | Latency untested | — | Feel not tested by a human |
| Guardians | 13 encounters, lairs, final progression, ending | Partly verified | S* `stage10`: every guardian defeated by the harness; door/ending fixtures (private details) | Scaling checked only by fixture | Claims saved | Balance and fun untested; models need redesign |
| Audio | 73-track soundtrack, music director | Verified (region context) | C: audio capture, 175 s continuous, 5 crossfades | — | — | Combat, guardian and settlement music contexts not exercised in game |
| Visuals | Creature models (60) | Verified | C zoo screenshots, previews | — | — | — |
| Visuals | Block and item textures (Stage 11.5) | Verified | Contact sheets, C terrain views | — | — | Armour worn and placed furniture not viewed in game |
| Visuals | NPC skins, guardian models, GUI | Unverified / needs redesign | — | — | — | See ASSET_QUALITY |
| Server | Dedicated server with the production JAR | Verified | P: six mod groups, audit 76/76 each | — | — | — |
| Server | Settlement planning spikes | Fixed | J: max tick 527–581 ms → 108 ms | — | — | — |
| Multiplayer | 4–6 real players | Missing (not testable here) | No second client or players available | — | — | Stage 13 playtest |
| Long-duration | Multi-hour soak with players | Missing | Only short runs (minutes) | — | — | Stage 13 / owner server |
