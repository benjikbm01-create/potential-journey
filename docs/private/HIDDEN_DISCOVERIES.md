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
