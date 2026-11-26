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

## 3. Ecosystems (15 implemented as of Stage 1.5)

Ecosystems are organised as many small, organically shaped regions (typically 60–250 blocks, some
250–700) whose neighbours follow a believable climate geography; giant caverns routinely span several
ecosystems. Each ecosystem has sub-regional variants (open, typical, dense/rugged, lake/wet).

| Ecosystem | Danger | Light | Identity |
|---|---|---|---|
| Rootwild | low | bright | welcoming forest caverns: colossal trees, roots, rivers, meadows, lakes (start) |
| Golden Grove | low | bright | warm golden-leaved groves, meadows, clearings — prime settlement land |
| Emerald Wetlands | low | bright | shallow marshes, channels, mangroves, glowing reeds |
| Fungal Expanse | low | bright | giant mushrooms with glowing undersides, spore soils, mounds |
| Starlight Caverns | low | bright | enormous open caverns under star-studded ceilings, lakes, starbloom meadows |
| Flooded Depths | medium | moderate | underground lakes and seas, islands, waterfalls (boats useful) |
| Sunken Gardens | medium | moderate | submerged valleys with coral gardens and kelp forests |
| Crystal Deep | medium | moderate | towering crystal halls, giant crystal clusters, bridges |
| Everfrost | medium | moderate | frozen lakes, ice caverns, giant icicles, ice bridges |
| Hollow Peaks | medium | moderate | mountain ranges, plateaus and passes inside the tallest caverns |
| Petrified Forest | medium | moderate | stone trees, fossils, amber, ancient terraces |
| Ashen Hollows | high | dark | volcanic mountains, lava rivers and craters, basalt |
| Crimson Basin | high | dark | alien red basins with lava pockets and crimson growth |
| Stormstone Caverns | high | dark | jagged blue-purple mineral caverns with charged veins |
| The Abyss | extreme | pitch | the deep layer: colossal dark chambers, chasms to a lava sea |

Danger classes govern natural hostile spawning (peaceful ecosystems are genuinely peaceful); lighting classes
govern natural light (bright ecosystems are comfortable to build in without torches). Stage 2 deepens each
ecosystem with custom flora and resources; Stage 3 adds native wildlife (incl. ecosystem predators) and reduces
vanilla monster spawning; later stages replace the remaining vanilla monsters.

## 4. Living world systems (later stages)

### Civilizations (Stages 4–6)
6–8 cultures with named individual inhabitants: personalities, homes, professions (20+), friendships,
rivalries, memories of important player interactions, daily routines (farming, trading, hauling,
building, exploring, guarding, socialising, responding to danger). Settlements grow, suffer
shortages, establish trade routes and react to regional events. Settlement placement uses the
Stage 1 site-analysis contract (villages, cities, markets, industrial, floating aquatic, hanging,
farms, outposts, roads and bridges along cavern-graph tunnels).

Stage 4 (implemented) populated the underground with eight civilizations, each recognizable from its
architecture alone:
- **Rootfolk:** forest timber and leaf thatch.
- **Forgekin:** industrial blackstone and copper.
- **Tide Dwellers:** stilted waterfront decks.
- **Sporefolk:** grown mushroom houses.
- **Glimmerkin:** crystalline spires.
- **Stoneborn:** heavy mountain stone.
- **Amberfolk:** warm merchant half-timbering.
- **Astrals:** luminous colonnades and domes.

Settlements come in five sizes (encampment, outpost, village, town, city). They sit in open cavern
chambers along the tunnel network, are planned with plazas, districts, roads, bridges, docks, gates and
lamps, and adapt to the terrain. Every settlement is inhabited from the start by named residents with
homes, professions and a daily routine. Residents trade in their culture's goods. Players discover
settlements as they explore; a welcoming one is always within a short walk of the starting cavern.

Stage 5 adds individual minds (memory, relationships, conversations). Stage 6 adds economies, caravans,
growth and factions. Histories and cultural secrets are private design material.

### Ecology (Stages 2–3)
Stage 2 (implemented) made every ecosystem biologically distinct: nine tree species (seven full wood
sets), ~35 land plants, 16 fungi, 10 aquatic species, 9 crops, 15 regional minerals, simple foods and
medicines, and bounded natural processes (spreading, blooming, spore releases, decay, regrowth,
crystal growth). Plants grow and spread on their own under understandable conditions (light, climate,
water, heat) and never overrun builds. Discoveries are tiered common → uncommon → rare → exceptional;
exceptional ones are documented only in `docs/private/`. Every species carries ecological metadata
(food-web roles, shelter, nesting, habitat) that Stage 3 wildlife uses; see ARCHITECTURE §5c.

Stage 3 (implemented) populated every ecosystem with wildlife: 60 original species (land, climbing,
aquatic, amphibious, flying, gliding, hovering), each with a diet tied to Stage 2 vegetation, a social
structure, a temperament, an activity cycle and a personality per individual. Predators hunt in
recognisable styles, prey flee and alarm their herds, territorial animals warn before charging and never
chase endlessly. Animals eat, drink, rest, sleep, nest, breed (bounded by regional carrying capacity),
play, explore, scavenge and migrate. A dozen species can be befriended in different ways, several can be
ridden or carry loads, and animals give renewable resources. A small number of rare, very large creatures
exist; they are documented only in `docs/private/`.

Wild animals belong to their region, not to chunks: each region keeps an abstract population that is
materialised near players and simulated in aggregate (growth, predation, decline, migration) when nobody
is there. Hunting pressure lowers a region's wildlife temporarily; populations recover. Vanilla farm
animals are replaced in Underdeep worlds by native species that supply the same basic resources.

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

1. Foundation and underground world generation — done (plus Stage 1.5 world expansion)
2. Ecosystems, vegetation and regional resources — **done (Stage 2)**
3. Wildlife and ecological simulation — **done (Stage 3)**
4. Civilizations and settlement architecture — **done (Stage 4)**
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

## Stage 5 status (living inhabitants)
Inhabitants are individuals with personalities, needs, emotions, memories, relationships, households, goals and
preferences; they choose their own activities, talk to each other and to players, remember what players do, ask
for help when they really need it, and some can become companions. The world continues the same way whether or
not a player is watching (abstract tier). Details: `docs/NPC_MINDS.md`.
