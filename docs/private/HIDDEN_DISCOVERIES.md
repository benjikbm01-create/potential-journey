# PRIVATE — Hidden discoveries & unique encounters (SPOILERS)

| Id | Discovery | Where | Stage |
|---|---|---|---|
| H-01 | The Heartwood chamber (root follows upward) | Rootwild, far from spawn | 9 |
| H-02 | A crystal "singing hall": luminite spires that ring in chords when struck in order | Crystal Deep colossal chamber | 9/11 |
| H-03 | A drowned Vaultwright archive visible only when a lake is drained | Flooded Depths | 9 |
| H-04 | The Silent Rim ruins at the edge of the lava sea | The Abyss | 9 |
| H-05 | An NPC hermit who remembers the players' first night (if they died near spawn) | Rootwild | 5 |
| H-06 | The sky-seal: a single bedrock "seam" that hums | upper roof, unique per world | 10 |

Implementation notes:
- H-06 must never allow actual surface access; it is a narrative endpoint.
- Placement of unique discoveries must be deterministic per seed and far from spawn (≥ 1500 blocks).

## Stage 2 — exceptional species and hidden biological mechanics (implemented)

| Id | Discovery | Where / how | Mechanics |
|---|---|---|---|
| H-07 | **Ancient seed → Relic Sprout → Relic Fruit** | Breaking fossil ore (not silk touch) has a 3 % chance to drop an *ancient seed* (Petrified Forest, Abyss fossils) | Plants on farmland or mineral ground (`relic_sprout`, `MINERAL_SOIL`); grows very slowly (4 % of normal chance per random tick, any light). Mature (age 7) glows (light 9); harvest gives a *relic fruit* (6 hunger, 1.2 saturation, always edible, Regeneration II 10 s + Absorption II 60 s) and one seed back (no fortune bonus). Intended as a slow, shareable long-term project for the group. |
| H-08 | **Umbral Bloom** (exceptional) | The Abyss deep layer, mineral ground, ~1 patch per several hundred chunks | Opens (`blooming=true`, light 5) only in darkness (light ≤ 5). Breaking it while open drops an *umbral petal* (alchemical, no use yet — reserved for Stage 8 potions/equipment). Spreads extremely slowly (1 % chance, cap 1). |
| H-09 | **Sagecap on rotting wood** | Rare in Fungal Expanse / Rootwild; rotting logs (fallen trees) occasionally sprout sagecaps among other fungi | Players who keep rotting logs in a dark room get a renewable source of this rare medicinal fungus. |
| H-10 | **Prism lilies open in the dark** | Crystal Deep | Prism lilies only open (light 12) when the surroundings are dark (≤ 5); a lit room closes them. |
| H-11 | **Voltbloom as a redstone indicator** | Stormstone Caverns | Voltblooms open instantly when the block below them receives redstone power (or charged stormstone is within 2 blocks): a natural lamp/indicator for builders. |
| H-12 | **Siltwort antidote shortcut** | Rare, Flooded Depths lake beds | One siltwort frond + bottle makes two antidotes (the common recipe needs bogwort + bracket fungus). |

Notes for later stages:
- Umbral petals, prismatic opal, umbralite, starsilver and galvanite are reserved progression materials
  (Stage 8). Keep their future uses out of public docs.
- Relic sprouts could later anchor a lore thread about the forest that petrified (see LORE.md).
