# Art direction, model architecture and preview workflow (Stage 11)

## Art direction
- **Readable silhouettes first.** Every creature has one silhouette idea, written as the first line of its design in
  `tools/assets/creature_designs.py`: the musk-ox's fur curtain, the ibex's back-sweeping horns, the angler's oversized
  jaw and lure, the heron's stilt legs and S-neck. A species must be recognisable as a shadow, without colour.
- **Believable anatomy, Minecraft scale.**
  - Bodies are built from overlapping masses (chest, barrel, hips), not one box.
  - Necks rise at an angle; heads have a cranium, muzzle and separate jaw.
  - Legs have two segments plus hooves or paws, and digitigrade hind legs bend at the hock.
  - Tails, abdomens and serpents are chains.
  - Wings are hinged in two sections and fold against the flanks at rest.
- **Texel discipline.** Pixel-art faithful cuboids (no smoothing). Textures are painted by rule, not noise:
  - face lighting (top 1.12 / sides 0.94 / bottom 0.7);
  - countershading (dark back, pale belly);
  - a darker rim on the lower edges;
  - deliberate markings: dapples, stripes, saddles, scale rows, chitin plates, fur strokes, feathers, membranes and bark;
  - painted eyes with highlights (slit pupils for ambush predators);
  - glow only where it means something (lures, embers, crystals, night eyes).
- **Regional identities** (the palette and shape language each region's creatures and music share):

  | Region | Identity |
  |---|---|
  | Rootwild | warm browns, moss greens; deer, boar, cat; dorian harp and flute |
  | Golden Grove | amber and gold, round soft shapes; lydian kalimba |
  | Fungal Expanse | lilac, cyan caps, mushroom gardens on backs; marimba and music box |
  | Flooded Depths / Sunken Gardens | teal, coral, nacre; fins, flukes, rays; glass, choir, long reverb |
  | Ashen Hollows | charcoal with ember seams; phrygian drones and taiko |
  | Crystal Deep | pale stone with glowing crystal growths; glass and bells |
  | Stormstone | indigo with electric seams and conductor spines; driving plucked ostinatos |
  | The Abyss | near-black, blind and eyeless forms, lures; the sparsest, darkest music |
  | Everfrost | frost white, shaggy fur, plumed tails |
  | Hollow Peaks | dun and stone; horns, crests, wings |
  | Emerald Wetlands | reed greens; frogs, herons, otters |
  | Petrified Forest | stone-grey armour and amber crystal moss |
  | Crimson Basin | blood red with bone thorns; harmonic minor |
  | Starlight Caverns | silver-blue; luminous horn, mane and jelly bells |

## Creature model architecture
- **Authoring.** `tools/assets/creature_kit.py` provides the `Rig`, a painter, and body-plan builders:
  - `quad` (chest/hips/neck/head/jaw/ears/tail, with legs that always reach the ground for the given knee bends);
  - `lizard`, `bug`, `bird`, `fish`, `eel`, `crab` and `jelly`;
  - decoration helpers in the designs file: chains, antlers, horn pairs, caps, crystals and spines.

  `tools/assets/creature_designs.py` holds one hand-authored design per species. Units are pixels, y is up and the head
  faces −z. Rotations follow Minecraft's model convention:
  - positive pitch drops the front and raises the back;
  - positive roll leans +x parts outward;
  - positive yaw swings +x tips forward.
- **Output.** `assets/underdeep/creatures/<id>.json` with `"rig": 2`, written by `creatures.py` → `build_design`. Each
  part has a role and optional `phase`, `index`, `side`, `chain` and `unfold`.
- **Animation.** `client/creature/CreatureModel` animates each part by its role:
  - `leg_upper` swings and `leg_lower` flexes during the lift phase;
  - `foot` keeps level; `ear` twitches occasionally;
  - `tail`, `segment` and `tentacle` chains lag behind each other (a travelling wave);
  - `wing` and `wing_outer` flap with the outer section trailing, and unfold from a folded rest pose using `unfold`;
  - `fin`, `fin_outer`, `fluke` and `tailfin` move with the swimming;
  - `jaw` chews, gapes or pants; paired mandibles open sideways;
  - `antenna`, `crest`, `float` and the head's idle sway and look-tracking.

  Behaviour states (sleep, eat, alert, warn, attack, flight, swimming) blend per entity with an easing time of about
  0.25 s, so poses never snap. Juveniles get a 1.35× head. The animation is purely visual: no server timing changed.
  Legacy specs (no `rig`) still animate with the Stage 3 rules.
- **Guardians.** These are data-driven rigs from Stage 10 (`stage10.py`). Stage 11 applies `boss_refine.py`, which adds
  detail parts without renaming or moving animated parts, and a structured texture painter. They still need a full
  redesign (see `docs/ASSET_QUALITY.md`). Their previews are private.

## Preview workflow
- `tools/preview/render_models.py` renders the real geometry and textures (every texel a lit quad, painter-sorted) with a
  player-height reference post:
  - `creature <id> [out]`: front, 3/4, side and rear views;
  - `views <out> <id>…`: several creatures, one row each;
  - `sheet creatures <out> [ids]`: a contact sheet (`CELL` and `COLS` environment variables set the layout);
  - `boss <id> <out>`: **private only**, written under `docs/private/previews/`.
- In-game check: `ZOO=true SUITE=stage2 scripts/selftest.sh <seed> <dir> create` builds lit exhibits, then
  `scripts/visual_tour.sh <dir>/world <dir>/selftest/tour_zoo.txt` takes a real-client screenshot of every species.
- Before and after sheets:
  - private: `docs/private/previews/before/creatures_before.png` and `docs/private/previews/after/`;
  - public: `docs/previews/creatures_after_*.png` (ordinary wildlife only).

## Acceptance criteria (models)
A model is accepted only when all of these hold:
1. The silhouette reads correctly in the side and 3/4 views at sheet size.
2. Its proportions fit the hitbox at the design scale.
3. The feet meet the ground.
4. No part is detached or penetrating in the rest pose.
5. The texture has no noise fill, and the markings follow the anatomy.
6. The eyes are visible from the front and 3/4 views.
7. The model renders in-game in the zoo tour.

Animation is accepted after it is watched in motion. That has not happened yet in Stage 11 (see the registry).
