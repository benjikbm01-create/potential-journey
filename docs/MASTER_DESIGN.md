# Underdeep — Master Design

This is the long-term game design and roadmap. It describes intent; `ARCHITECTURE.md` describes how
the code realises it, and `PROGRESS.md` records what actually exists. Spoiler-sensitive material
(secret lore, hidden discoveries, unique encounters) is deliberately absent and lives in
`docs/private/`.

## 1. Vision

A cooperative underground survival sandbox for 4–6 friends playing over about two weeks. The whole
world is underground — an enormous subterranean continent of caverns hundreds of blocks across,
underground forests, lakes and rivers, volcanic hollows, crystal halls and abysmal depths. The world
is alive independently of the players: civilizations trade, feud and grow; wildlife hunts, migrates
and competes; events ripple through connected systems. Players are inhabitants, not chosen heroes.
Memorable multiplayer stories should emerge from interacting systems rather than scripted quests.

### Non-negotiable principles

- Keep Minecraft's freedom to build, mine, craft, farm and explore.
- Beautiful environments and meaningful discoveries; reward curiosity and experimentation.
- Cooperation without forcing simultaneous play (asynchronous contributions matter).
- The world feels inhabited; creatures and NPCs have reasons to exist beyond the players.
- No excessive grinding, mandatory quest checklists, real-world time gates or tedious meters.
- Permanent homes and settlements for players.
- Situations that naturally produce funny moments between friends.
- Strong multiplayer performance; a working core over many unfinished features.

## 2. World structure

- Fully underground world (Minecraft heights −64..319), sealed by bedrock floor and roof, no skylight.
- **Cavern layers** stacked vertically, each a continent-scale network of chambers and tunnels:
  - *Upper reaches* (floors ~y 110–180, ceilings up to ~300): the temperate, inhabited band.
  - *Deep layer* (floors ~y −55–20, ceilings up to ~130): darker, more dangerous, richer.
  - Future stages may add further strata (e.g. a mid layer or sub-abyssal pockets) via `CavernLayer`.
- **Regions** are large (≈500–1500 blocks) areas of one ecosystem type within a layer, arranged by a
  slowly varying climate so geography is plausible. Regions differ in terrain shape, not just blocks.
- Connectivity: every chamber of a layer is joined to its neighbours by tunnels; layers join through
  vertical shafts (some with spiral ledges), and winding tunnels thread through the rock.
- Players start in a guaranteed, well-provisioned Rootwild cavern at the world origin.

## 3. Ecosystems (target 12–15)

Stage 1 foundations (terrain + biome + basic flora):

| Region | Character | Terrain signature |
|---|---|---|
| Rootwild | welcoming underground forest, moss, roots, streams | tall chambers, rolling hills, root pillars, rivers, ponds |
| Fungal Expanse | colourful giant fungi, spores | mounded terraced floors, lower domes, nylium/mycelium |
| Flooded Depths | lakes, islands, waterfalls | floors below the water table, island hills, many springs |
| Ashen Hollows | basalt, blackstone, lava channels | sharp terraced cliffs, ridged mountains, lava rivers and pools |
| Crystal Deep | crystal halls, geodes | very tall caverns, spires, bridges, crystal clusters |
| The Abyss | dark colossal depths | huge chambers, enormous pillars, chasms to a lava sea |

Planned additional ecosystems (Stage 2+, names provisional): glowworm grottos, salt flats, frozen
caverns, sunken ruins marsh, bone fields, chasm cliffs, sulphur springs, root-choked hollows,
mirror lakes. Each must have a distinct terrain profile, resources and habitat tags.

## 4. Living world systems (later stages)

### Civilizations (Stages 4–6)
6–8 cultures with named individual inhabitants: personalities, homes, professions (20+), friendships,
rivalries, memories of important player interactions, daily routines (farming, trading, hauling,
building, exploring, guarding, socialising, responding to danger). Settlements grow, suffer
shortages, establish trade routes and react to regional events. Settlement placement uses the
Stage 1 site-analysis contract (villages, cities, markets, industrial, floating aquatic, hanging,
farms, outposts, roads and bridges along cavern-graph tunnels).

### Ecology (Stages 2–3)
40–60 species with ecological roles: predation, territory, nesting, reproduction, migration,
competition, domestication, environmental preferences. Populations change gradually from natural
events and meaningful player activity, simulated abstractly per region when unloaded.

### World simulation (Stage 6)
Regional economies, inter-settlement trade, caravans with persistent route progress, explorers,
faction relationships, rumours, environmental change, 50+ dynamic event types. Systems interact:
e.g. predators on a trade route → shortage → price changes → opportunity for players — emerging
from connected systems, not scripted.

### Tiered simulation model
- Nearby loaded regions: full entity simulation.
- Distant regions: lightweight aggregate simulation.
- Unloaded settlements: persistent abstract state; unloaded wildlife populations change without
  entities; caravans advance along routes as data.
- On approach, abstract state is reconciled with real entities and structures.
- Never permanently force-load chunks for simulation.

## 5. Survival and progression (Stages 7–10)

- Survival: farming adapted to darkness (light-dependent and light-independent crops), fungi, fishing,
  hunting with ecological consequences; no tedious meters.
- Equipment and crafting: 100–150 meaningful items; regional materials. Underdeep worlds disable the
  Nether and End by default, so essential progression resources (blaze-rod, ender-pearl, netherite
  and End equivalents) must have underground alternatives — planned sources: Ashen Hollows and the
  deep layer (heat-forged materials), Crystal Deep (resonant/teleport materials), the Abyss (endgame).
  Config flags exist to re-enable vanilla dimensions.
- Exploration: 60–100 structure designs (ruins, lost outposts, vaults), environmental storytelling.
- Combat: 10–12 major boss encounters, endgame in the deepest places.

## 6. Roadmap

1. Foundation and underground world generation — **this stage**
2. Ecosystems, vegetation and regional resources
3. Wildlife and ecological simulation
4. Civilizations and settlement architecture
5. Individual NPC intelligence, routines and relationships
6. World simulation, economies, trade and faction development
7. Survival, farming and resource acquisition
8. Equipment, crafting and progression
9. Exploration, ruins and adventure content
10. Combat, bosses and endgame
11. Immersion, sounds, animations and polish
12. Final multiplayer testing, performance optimisation and balancing

Each stage builds on the previous ones without rewriting working systems unless justified in
`DECISIONS.md`.

## 7. Long-term content targets

12–15 ecosystems · 6–8 civilizations · 40–60 species · 60–100 structure designs · 100–150 items ·
20+ professions · 10–12 bosses · 50+ dynamic event types · custom sounds, textures, animations.
Depth and quality take precedence over numbers.
