# PRIVATE — Stage 9 exploration design (SPOILERS)

Never quote this file in public docs, reports, commit messages, Handbook text or advancement text visible
before discovery. Builders live in `src/main/java/dev/underdeep/explore/design/`, metadata in
`tools/assets/stage9.py` (→ `data/underdeep/exploration/content.json`).

## 1. Historical framework (truth)

The underground holds several overlapping histories, not one empire:

1. **Vaultwrights** (oldest; see LORE.md). Deepslate tile and tuff brick, chiseled geometric glyphs, sealed
   doors that answer to symbols. They built the great stairs, gatehouses, vaults and the colossi, and sealed
   the sky. Their writing is the *six-sign script*: Spiral, Eye, Wave, Flame, Star, Key (the `rune_dial` and
   `glyph_tile` symbols 0–5, in that order). They did not die out; they *withdrew* into the vaults and slept,
   then were forgotten. Their crystal engines left the luminite.
2. **The Lantern League** (middle era). Engineers from the eight peoples' ancestors who reopened the old
   roads with copper lantern-posts, pumps and waystations. Oxidised copper, copper grates and bulbs, glass.
   They charted the caverns; their waystones give distances in "lamps" (one lamp = 32 blocks). They dissolved
   after the **Dimming**: the luminite veins they fed their lamps from stopped glowing for a generation (the
   crystal engines below faltered). The League blamed the Vaultwright vaults they had been opening.
3. **The Tide Choir** — ancestors of the Tide Dwellers. Prismarine and nacre shrines sung into the rock; the
   Choir believed water remembers voices. Their temples flooded on purpose: drowning a shrine was a burial.
4. **The Ember Concord** — the first Forgekin oath-keepers. They kept Vaultwright forges burning *without
   knowing they were engines*. Blackstone, basalt, copper, magma.
5. **The Quiet Gardeners** — Rootfolk/Amberfolk ancestors and keepers of seed vaults (ancient seeds, relic
   sprouts: H-07). Amber, moss, mud brick, planters. They stored seeds from the surface tree (Heartwood H-01).
6. **The Star-readers** — Astral precursors who studied the starstone ceilings as if they were sky. Calcite,
   quartz, starstone. Built orreries and observatories; believed the sky-seal was a lens.
7. **The Silent Rim** (Abyss; LORE.md): a deep people who took silence as faith (sculk). Reserved for Stage 10:
   the sanctum's inner arena exists (empty ritual floor) — **no boss in Stage 9**.

### Cultural interpretation vs truth (records are deliberately unreliable)
| Topic | Truth | Lantern League records | Ember Concord | Tide Choir | Gardeners | Star-readers |
|---|---|---|---|---|---|---|
| Why the Vaultwrights vanished | withdrew to sleep in vaults | "they fled, cowards" | "they became the fire" | "they went into the water" | "they are seeds, waiting" | "they climbed through the lens" |
| The Dimming | crystal engines faltered | League blamed the vault openings (partly right) | "the oath was broken" | "the water stopped singing" | "a winter without seasons" | "the lens clouded" |
| The sky-seal | built to keep something out | unknown | unknown | "a lid on the well" | "a roof over the garden" | "a lens" (wrong) |
Rumours from inhabitants follow their culture's interpretation (see `rumors` in stage9.py).

## 2. Content identities

Counts used in public reports come from `content.json` (families with a builder). Ids are visible in code.

### Ruins and abandoned places (30)
Minor: wayshrine, lantern_post (League road), collapsed_hut, miners_camp, cairn_circle (clue for buried
reliquary), broken_bridge, fallen_statue (Vaultwright head), well_head (secret: well_bottom tunnel), watch_niche,
grave_garden (Gardeners), kiln_mound (decorated pots with loot), waystone (League; distance in lamps).
Regional: cottage_row (secret cellar), lantern_waystation, forge_ruin (secret strongbox), tide_shrine (secret
pearl grotto), seed_terrace, star_dial, fungal_hall, ice_hermitage, stilt_village, storm_tower, quarry, silent_ruin
(Abyss: Silent Rim outskirts H-04).
Significant: vault_gatehouse (secret armory), aqueduct (secret channel), market_square, great_library (secret
stacks), amphitheatre, foundry_complex.

### Landmarks (16)
colossus (secret: heart chamber), great_stair (secret landing), sky_bridge, lantern_spire, root_arch,
crystal_cathedral, frozen_falls, ember_caldera, sunken_dome, mushroom_ring, petrified_titan (secret hollow),
star_orrery (secret core), storm_pylons, hanging_gardens, obelisk_field, silent_bell (secret bell chamber).

### Dungeons (20)
Small: sealed_cellar, smugglers_den, drowned_crypt, kiln_vault, spore_warren, frost_cistern, echo_vault.
Medium: lantern_works, warden_barracks, tide_temple, seed_vault, star_observatory, stormworks, ossuary.
Large: wright_vault, sunken_archive (H-03), deep_foundry, singing_hall (H-02).
Exceptional: heartwood (H-01, ≥ 1500 blocks from spawn, Rootwild), silent_sanctum (H-04, Abyss deep layer;
Stage 10 arena reserved, empty).

### Secrets (26)
Standalone (category secret; discovered only inside the hidden room): waterfall_grotto, hollow_pillar,
buried_reliquary, hermit_cell, fungal_heart, ice_tomb, ember_shrine, star_window, drowned_bell, root_hollow.
Embedded (hidden rooms inside other designs, each with its own discovery box): cellar_beneath, library_stacks,
gatehouse_armory, aqueduct_channel, forge_strongbox, pearl_grotto, vault_archive, observatory_crypt,
ossuary_reliquary, lantern_vault, colossus_heart, stair_landing, orrery_core, bell_chamber, titan_hollow,
well_bottom.

## 3. Puzzle mechanics (16) and solutions
| Type | Mechanic | Clue | Solution rule |
|---|---|---|---|
| dial_match | rune dials cycle six signs | glyph tiles (mural) elsewhere in the site show the same count of signs in order | dials equal the mural signs left→right |
| plate_sequence | step on rune plates in order | plates carry 1–4 notches (shape, not colour) | step notches 1,2,3,4; a wrong plate resets with a click |
| brazier_all | light every brazier | dark room, braziers visible | all lit (flint and steel, fire charge, torch, blaze) |
| brazier_order | light braziers in order | numbered tally carvings on the plinths | lit order = tally order; wrong order extinguishes all |
| extinguish | put out all lights to reveal glowing glyphs; then seal opens | the glyphs glow only in darkness | all braziers out (use water bottle/bucket/shovel or punch) |
| offering | place items in sockets | carved relief shows the item (fish / bread / gem / bone) and the record nearby names it | each socket holds its depicted item (common items, never lost: a wrong item is returned) |
| chime_order | strike chimes | chimes are visibly short/medium/tall | strike shortest → tallest (pitch rises too) |
| lever_pattern | ancient levers up/down | a carved row of up/down arrows | levers match arrows |
| basin_fill | fill stone basins | dry channel carvings lead into basins | every basin full (water bucket/bottles) |
| mirror_beam | rotate mirrors to carry a light beam from the lens to the receiver | beam particles show the current path on every rotation | receiver hit |
| twin_plates (coop) | two heavy plates pressed at once | two plates far apart, a mural of two figures | anything heavy on both: players, companions, animals, or a dropped stack of ≥ 8 items (solo route) |
| memory | a pillar shows a sign sequence; repeat it on glyph stones | the pillar replays on demand (use it) | press stones in shown order (length 4) |
| mosaic | place blocks into empty frames, then press the keystone | the surrounding mosaic repeats a pattern | frames contain the pattern's missing material |
| timed_gate | a lever opens bars for 12 s | the bars are visible from the lever | run through (generous; a second lever on the far side for return) |
| power_restore | supply redstone power to a dormant engine | copper wiring carvings lead to the engine | any redstone power (torch, block, lever) on the engine |
| counterweight | load a basket with N items | notches on the beam (N = 4, 8 or 16) | exact count; the basket returns its contents on solve |

Cooperative: twin_plates, timed_gate (one holds, one runs, but 12 s is enough solo), memory (one reads, one presses).
All puzzles are solvable solo without commands. Seals are unbreakable but each seal has a sign telling players
"An ancient seal. Something here opens it." No required item can be lost permanently (offerings use common items,
keys are reissued by the key pedestal per player).

## 4. Traps (12)
dart_vent (trap_plate or tripwire → arrows; click warning, dust), flame_vent (cycle 2 s on / 3 s off, smoke
before), steam_vent (heat exposure), spore_vent (spore exposure), frost_vent (cold exposure), crumbling_floor
(cracks 1 s after a step; drops to a lower room with water or a soft landing, never into lava), loose_ceiling
(dust warning, gravel falls), alarm_gong (rings, wakes 2–4 *restless dead* (vanilla zombies/skeletons in
crypt dress) once), dark_lamps (ancient lamps go out for 30 s), tripwire arrows (vanilla), collapsing_bridge
(crumbling floor in a span), sluice (water floods a corridor slowly; the corridor always has an upward exit).
Disarm: sneak-use a trap plate with a stick or any pickaxe → jammed (permanent). Vents: place a block on them.

## 5. Artifacts and treasure
Defined in stage9.py (`ARTIFACTS`, `RELICS`). Unique per world: the three `heart` artifacts (heartwood, singing
hall, sunken archive) — pedestal, shared, one claim per world. Others come from personal coffers (per player)
and shared chests. Identification: *encrusted relic* → scholar/cartographer/merchant identifies for 3 emeralds
into a random non-unique artifact (weighted). Relic valuables are economy goods (scholars buy them).
Recovery: no artifact is ever required. Vault keys are reissued per player by their key pedestal.

## 6. Encounters (20)
echo_whisper, glowmoth_swarm, rockfall, ore_reveal, lost_pack, abandoned_camp, will_o_light (leads to the
nearest undiscovered minor/regional site ≤ 120 blocks — never a secret), herd_passage, stalker, injured_creature,
spore_bloom, geyser, crystal_song, ice_crack, falling_star, static_storm, ghost_lanterns (rare: a procession of
League lantern-lights walking an old road; following them ends at a lantern_post or waystone), drifting_raft,
tremor, distant_bell (Abyss, rare).

## 7. Hidden advancements
Expedition tab hidden ones: heartwood, singing hall, sunken archive, silent sanctum, 3 heart artifacts, ghost
lanterns, all six cultures' records read.

## 8. Stage 10 reservations
silent_sanctum inner arena (empty), deep_foundry furnace heart room (empty, sealed by a seal with no puzzle:
`stage10_reserved` flag; seal text says "Silent. Not yet."), warden_barracks commander room (empty).
