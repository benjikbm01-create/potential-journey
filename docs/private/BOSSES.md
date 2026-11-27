# PRIVATE — Stage 10 bosses, final progression and ending (SPOILERS)

Never quote this file in public docs, reports, commit messages, Handbook text or advancement text visible before
discovery. Code: `src/main/java/dev/underdeep/boss/` (framework + brains), lair layouts in `boss/Lairs*.java`,
models/textures/lang in `tools/assets/stage10.py`.

## 1. The truth behind the ending
The Vaultwrights' crystal engines were never only diggers: they draw warmth from **the Sleeper**, a vast being
coiled beneath the Abyss lava sea, and their hum keeps it dreaming. Sculk is the residue of its dreams; the Silent
Rim made silence a faith so as not to wake it. The Dimming (Stage 9) was the engines faltering — the Sleeper stirred,
and the great guardians of the old peoples were set to hold the **Signs**, the keys of the Heart Engine.
The Sleeper's waking dream takes shape as **the Somnarch** in the Heart Engine. Defeating it rekindles the engine:
the Sleeper sinks back into dreamless sleep. The world goes on (postgame), and luminite "sings" again.

## 2. Main progression (the way to beat Underdeep)
1. Survive, gear up (Stage 7–8). 2. Explore; meet inhabitants (rumours about "the guardians of the Signs").
3. **Early guardians** (3, optional, teach mechanics; drop materials for mid gear).
4. **Sign guardians** (4 midgame + 2 late) each give every participant a **Sign-stone**
   (Spiral, Eye, Wave, Flame, Star, Key).
5. **The Silent Warden** (late, Abyss) gives the **Keystone of Silence**. Mandatory.
6. Holding the keystone, right-click: it hums toward the **Heart Engine** (direction + distance; Handbook page unlocks).
7. The Heart Engine door accepts Sign-stones: **the Keystone + any 3 of the 6 Signs** (set once per world, shared).
8. The final boss. Victory: world completion record, ending sequence (chat + title, ~20 s, skippable by moving on),
   postgame rewards.
Not required: early bosses, secret bosses, all six Signs, relationships, businesses, every advancement.

### Group state
- Sign-stones are personal (each participant receives one); setting them into the door is a world fact.
- Anyone may open the door once the world has the required signs; late joiners can attend the final fight.
- Recovery: a player credited with a guardian who lacks that Sign (lost/burnt) can reclaim it at the guardian's
  altar (sneak-use with empty hand) unless the world already set that Sign. Keystone likewise. Admin never needed.
- Defeated guardians can be re-challenged (practice, helping friends): reduced material loot each time; Sign/heirloom
  only once per player / world.

## 3. Roster (13)
Tier | Id | Title shown on awakening | Lair (family `lair_<id>`) | Location rule
---|---|---|---|---
Early | mossback | The Mossback Thunderer | root-pillared hollow | Rootwild/Golden Grove, upper, 300–700 from spawn
Early | sporemother | The Spore Mother | mushroom grove | Fungal Expanse, upper, 350–800
Early | choirwarden | The Drowned Choirwarden | half-flooded nacre temple | Flooded/Sunken, upper, 400–900
Mid | rimefang | Rimefang, Lord of the Pack | frozen lake ring | Everfrost, upper, 700–1300 — Sign of the Spiral
Mid | galvanic | The Galvanic Colossus | pylon yard | Stormstone, upper/deep, 800–1400 — Sign of the Eye
Mid | cinderwurm | The Cinder Wurm | ash pit | Ashen Hollows/Crimson, either, 800–1500 — Sign of the Flame
Mid | lamplighter | The Last Lamplighter | lantern hall | Starlight/Golden, upper, 800–1500 — Sign of the Wave
Late | resonant | The Resonant Colossus | crystal hall | Crystal Deep, deep, 1200–2000 — Sign of the Star
Late | tidemother | The Tide-Mother | island ring over deep water | Flooded/Sunken, either, 1300–2100 — Sign of the Key
Late | silentwarden | The Silent Warden | silent cathedral | The Abyss, deep, 1300–2200 — Keystone of Silence
Secret | rootsovereign | The Root Sovereign | Heartwood root chamber | Rootwild, ≥1500; awakened by offering a relic fruit
Secret | seamwatcher | The Seam Watcher | high chamber under the sky-seal | upper, near roof, ≥1800; star shard
Final | somnarch | The Somnarch | the Heart Engine | The Abyss, deep, 1800–2600

## 4. Encounters (mechanics; all attacks telegraphed; times are designed for 4–6 equipped players)
- **Mossback** (3–5 min; teaches dodge, punish recovery, terrain). Scrape-and-roar then CHARGE (straight line; dodge
  sideways; hitting a root pillar dazes it 4 s = big opening). Tail sweep (behind it), stomp ring. Phase 2 (50 %):
  double charge, calls 2 tuskbriar boarlets once.
- **Spore Mother** (4–6 min; teaches reading ground hazards, blocking). Rooted; spore bursts on telegraphed rings
  (smoke ring 1.2 s), tendril slam (blockable, parryable), puffling minions; after a Great Exhale her heart-cap opens
  (6 s, double damage). Phase 2: uproots and re-roots elsewhere in the grove (moves), wider bursts.
- **Choirwarden** (4–6 min; teaches parry). Knight of nacre: 3-hit combo (parryable; each parry breaks poise), lunging
  thrust (glows red = unparryable → dodge), shield bash (block). Phase 2: sings — water jets on rings.
- **Rimefang** (6–9 min; group fight). Alpha + 3 packmates (respawn once). Pounce, ice breath cone (cold; fur/
  rimesteel help), howl buffs pack. Phase 2: blizzard (blindness pulses), alpha stalks and ambushes from the
  flanks; braziers in the arena (light them: use flint/torch) melt the blizzard near them.
- **Galvanic Colossus** (6–10 min; mechanism + positioning). Shielded while ≥1 of 3 conductor pylons is charged;
  players ground pylons by using them (2 s channel). Lightning on telegraphed circles, slam waves (jump/dodge),
  arm sweep. Copper armour attracts strikes (Stage 8 galvanic armour protects). Phase 2: pylons recharge faster;
  overload burst every 30 s (run to the edge).
- **Cinder Wurm** (6–9 min; mobility). Burrows (dust trail), erupts under a player (ring 1.5 s), surfaces to breathe
  fire (cone) and is vulnerable while surfaced; ranged players hit it when it rears. Phase 2: cracks in the arena
  floor glow and erupt (temporary magma blocks, restored after).
- **Lamplighter** (6–10 min; ranged/positioning). Ghostly lamplighter moving between lit lantern posts (no
  teleport: it walks the lantern light, fast, visible). Will-o-orbs (slow homing, blockable), lamp snuffing
  (darkness ring). Players extinguish posts (use) to deny it; when all are dark it is forced to fight in melee (weak).
  Phase 2: it relights posts itself; summons 2 wisps.
- **Resonant Colossus** (8–12 min; weak points). Crystal titan; 4 resonance nodes (shoulders, back, chest): hitting a
  glowing node deals ×2.5 and breaks it; each broken node ends a phase step. Sonic wave (expanding ring, jump),
  crystal rain (circles), ground slam. Phase 3 (no nodes): core exposed, frenzied.
- **Tide-Mother** (8–14 min; aquatic). Fight on stone islands over deep water. She surfaces between islands:
  grab (unparryable, dodge), wave slam (knock-back ring), spits tide-orbs. Vulnerable when beached on an island
  after a lunge. Phase 2: whirlpool pulls swimmers; breath shell / water breathing helps.
- **Silent Warden** (10–14 min; stealth + sound). Blind; hunts by vibration: sneaking players are hard to find,
  noise (running, fighting, striking the bells in the cathedral) draws it. Sonic boom (line, unblockable,
  through walls — telegraphed by a 1.5 s charge), crushing grab. Striking a cathedral bell makes it rush there
  (use it to set up hits from behind: back ×1.5). Phase 2: sculk pulses darken the hall.
- **Root Sovereign** (secret; 10–15 min). Heartwood treant; root eruption lines, thorn volleys, summons root spirits;
  phase 2 regrows unless its 3 root hearts in the chamber are cut.
- **Seam Watcher** (secret; 10–15 min). Starlit eye-thing descending through the seam; gaze beams (line, sweep),
  star-falls, gravity pull. Phase 2: inverts — pushes players away, beams cross.
- **Somnarch** (final; 12–20 min). Phase 1 *Dream*: sculk colossus; vibration-hunting like the Warden but faster;
  dream-echo copies of earlier guardians' signature attacks (charge, spore ring, sonic line). Phase 2 *Engine*:
  three engine coils around the arena must be re-lit (use, 3 s channel each; solo-possible); while ≥1 coil is dark
  the Somnarch regenerates. Phase 3 *Waking*: the true head rises from the magma; arena edge cracks (temporary),
  falling crystal shards (circles); vulnerable after each Wake Roar. Victory: engines hum, the head sinks.

## 5. Rewards (per boss: personal first-defeat loot, repeat "echo" loot; heirloom once per world)
- Materials: thunderhide, mycelial_heart, choir_nacre, rimefang_pelt, galvanic_core, cinder_scale, everlit_wick,
  resonant_crystal, abyssal_pearl, silent_bellmetal, heartwood_resin, seamglass, somnal_ember.
- Material uses: boss upgrade kits (Stage 8 upgrade system, bounded): thunderhide_lining, mycelial_coating,
  nacre_edge, rimefang_lining, galvanic_coil, cinder_temper, everlit_lens, resonant_core, abyssal_grip,
  silent_padding; heartwood_resin/seamglass/somnal_ember → secret heirlooms' repair and the final reward.
- Heirlooms (unique per world): mossback_horn (accessory), sporemother_censer (accessory), choirwarden_blade (weapon),
  rimefang_mantle (accessory), galvanic_gauntlet (accessory), cinder_fang (weapon), lamplighter_lantern (accessory),
  resonant_hammer (weapon), tidemother_trident? → tide_harpoon (weapon), silent_veil (accessory),
  sovereign_crown (accessory), watcher_lens (accessory), somnarch_heart (accessory, postgame).
- Final victory: every participant: "Kindled" title advancement, somnal_ember ×3; world: completion record,
  luminite resonance message, the sky-seal seam hums (narrative).

## 6. Hidden advancements
Secret bosses, the final destination, the final victory and "the Sleeper dreams no more" text are hidden until done.

## 7. As implemented (v0.11.0)
- All 13 brains in `boss/Brains.java`; lairs and arenas in `boss/Lairs.java`; final door/ending in `boss/Ending.java`.
- Seed 42 lair distances: mossback 628, sporemother 542, choirwarden 649, rimefang 872, galvanic 1099 (deep),
  cinderwurm 1149 (deep), lamplighter 1117, resonant 1631 (deep), tidemother 1764, silentwarden 1654 (deep),
  rootsovereign 1950, seamwatcher 2250, somnarch 1848 (deep).
- Deviations from §3/§4: the Tide-Mother is upper-layer only (no wet deep regions); the Rimefang pack uses rimefox,
  spore minions are pufflings that burst into spore rings, root spirits are shadecats, the Lamplighter's wisps are
  vanilla vexes (limited life). The Somnarch's dream echoes are its own moves (charge, spore rings, sonic line, wave).
- Not implemented from the design: the Root Sovereign "regrows unless root hearts are cut" works as regeneration while
  any root heart is lit (cutting them also staggers it); the Seam Watcher's phase-2 inversion is the push instead of
  pull of its gravity move.
