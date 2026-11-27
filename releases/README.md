# Releases

Prebuilt development JARs, committed so they can be downloaded without building.

| File | Stage | Minecraft | Requires |
|---|---|---|---|
| `underdeep-0.5.0-stage4.jar` | 4 — civilizations, settlements and architecture (8 cultures, villages to cities, inhabitants, trading) | 1.21.1 | Fabric Loader ≥ 0.17.3, Fabric API 0.116.18+1.21.1 |
| `underdeep-0.6.0-stage5.jar` | 5 — living inhabitants (personalities, needs, memories, relationships, conversations, companions) | 1.21.1 | Fabric Loader ≥ 0.17.3, Fabric API 0.116.18+1.21.1 |
| `underdeep-0.8.0-stage7.jar` | 7 — cooking, preserving, farming, husbandry, fishing, medicine, hazards, camping, homes — **current** | 1.21.1 | Fabric Loader ≥ 0.17.3, Fabric API 0.116.18+1.21.1 |
| `underdeep-0.7.0-stage6.jar` | 6 — economies, trade, caravans, construction, migration, diplomacy, contracts, property, shops | 1.21.1 | Fabric Loader ≥ 0.17.3, Fabric API 0.116.18+1.21.1 |
| `underdeep-0.4.0-stage3.jar` | 3 — wildlife and living ecological simulation (60 creatures, taming, riding, regional populations) | 1.21.1 | Fabric Loader ≥ 0.17.3, Fabric API 0.116.18+1.21.1 |
| `underdeep-0.3.0-stage2.jar` | 2 — living ecosystems (trees, wood sets, plants, fungi, aquatic life, crops, foods, medicines, minerals) | 1.21.1 | Fabric Loader ≥ 0.17.3, Fabric API 0.116.18+1.21.1 |
| `underdeep-0.2.0-stage1.5.jar` | 1.5 — world expansion (15 ecosystems, lighting, spawning) | 1.21.1 | Fabric Loader ≥ 0.17.3, Fabric API 0.116.18+1.21.1 |
| `underdeep-0.1.0-stage1.jar` | 1 — foundation & world generation (old; generator v1) | 1.21.1 | Fabric Loader ≥ 0.17.3, Fabric API 0.116.18+1.21.1 |

Use only one Underdeep JAR at a time. Worlds made with the Stage 1 JAR must be recreated (terrain changed).
Stage 1.5 worlds open with Stage 2, but explored chunks have no Stage 2 content: start a fresh world.
Stage 2 worlds open with Stage 3 unchanged (same blocks); wildlife appears around players, but vanilla
animals already saved in explored chunks remain.
The mod never modifies or deletes existing worlds.

Settlements (Stage 4) generate only in worlds created with the Stage 4 JAR: create a new world.

Stage 5 (`underdeep-0.6.0-stage5.jar`) needs no new world: Stage 4 worlds keep their settlements and every
inhabitant receives a personality and memories on first load (identities unchanged). Install the JAR on both
the server and every client (the dialogue screen is client-side).

Stage 6 (`underdeep-0.7.0-stage6.jar`) needs no new world: settlements near players join the economy automatically.
Install it on the server and every client.

Stage 7 (`underdeep-0.8.0-stage7.jar`) needs no new world (terrain unchanged): new items, blocks and stations work in
existing worlds; hazards and regional fishing apply in Underdeep worlds. Install it on the server and every client
(the station screen is client-side).

Stage 8 (`underdeep-0.9.1-stage8.jar`; 0.9.1 fixes the blurred Handbook screen) needs no new world (terrain unchanged apart from a rare-plant placement fix
in newly generated chunks): equipment, stations, the Handbook (H key) and advancements work in existing worlds;
players receive the Handbook and quiet credit for earlier progress on first join. Install it on the server and every
client (Handbook, accessory and artisan screens are client-side).

Install: put the JAR and Fabric API in `mods/`. See the main `README.md` for world and server setup.
Rebuild from source with `./gradlew build` (output: `build/libs/`).
