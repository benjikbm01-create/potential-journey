# PRIVATE — Wildlife secrets (SPOILERS)

Stage 3 secret wildlife design. Never quote this in public docs, commit messages or user-facing reports.

## Rare and exceptional creatures

Presence is deterministic per region (`RegionWildlife.capacity`): a rare species occupies a region when
`unit(regionId, hash(species)) < density × rareCreatureFrequency`, and then exactly one individual lives
there. The abstract model never breeds or kills them off; only players (or migration) change that.

| Species | Rarity | Ecosystems | Chance per suitable region | Encounter |
|---|---|---|---|---|
| grand_drifter | exceptional | Flooded Depths, Sunken Gardens | 4 % | Peaceful filter feeder 4 blocks long gliding through deep lakes; sheds rare scales (renewable) near observers. Voice: slow, deep dolphin calls. |
| sky_drifter | exceptional | Starlight Caverns | 5 % | Glowing hovering giant drifting under the "stars"; nectar feeder; never hostile. |
| slag_titan | rare | Ashen Hollows | 8 % | Armoured lava-walking territorial giant (150 HP, armour 16); warns before charging; sheds shell fragments. Fireproof, crosses lava. |
| abyss_strider | rare | The Abyss | 10 % | 5-block-tall stilt-legged scavenger with a lure-like head light; passive, follows the smell of kills. |
| conduit_serpent | rare | Stormstone Caverns | 15 % | Glowing serpent that eats charged mineral; neutral; drops scales and glow glands. |

Uncommon (not secret, but less obvious): petrisloth (burst activity, climbs petrified trunks),
moonhorn (glowing herds in Starlight Caverns).

Designed follow-ups (Stage 9/10): tie grand_drifter sightings to H-03 (drained archive lake), sky_drifter
to the Starlight vista landmarks, and give the abyss_strider a role near H-04 (Silent Rim).

## Secret taming mechanics

Players discover these in play; the field journal only shows a vague hint once the creature has been
observed being fed.

- **FEEDING** — feed the favourite food (3–6 times; curious personalities need fewer).
- **PATIENCE** (cliffstrider) — hold its food while sneaking within 6 blocks, without moving much, for
  ~60 s (1200 ticks of accumulated patience). It shows hearts once relaxed; feed it then. Moving fast or
  hurting anything nearby resets patience.
- **RESCUE** (shadecat, ledgewing) — refuses food from players while healthy; accepts it while below 60 %
  health (e.g. after a predator fight). Healing it with food is what builds the bond.
- **OFFERING** (lumen_kit) — accepts a glimmer shard at most once every 30 s; three accepted gifts tame
  it. Rushing offerings wastes them.

## Hidden biology

- static_beetle discharges static (2 damage) into an attacker within 3 blocks.
- Snufflet companions point toward cave truffles and nearby ore veins (sniff behaviour).
- Puffling and lumen_kit companions give slow regeneration to an injured owner nearby (comfort).
- Rillpup companions occasionally bring fish fillets to their owner (fetch).
- Glowing predators make a tamed companion warn its owner (warn behaviour).
