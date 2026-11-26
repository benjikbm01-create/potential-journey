# Releases

Prebuilt development JARs, committed so they can be downloaded without building.

| File | Stage | Minecraft | Requires |
|---|---|---|---|
| `underdeep-0.3.0-stage2.jar` | 2 — living ecosystems (trees, wood sets, plants, fungi, aquatic life, crops, foods, medicines, minerals) — **current** | 1.21.1 | Fabric Loader ≥ 0.17.3, Fabric API 0.116.18+1.21.1 |
| `underdeep-0.2.0-stage1.5.jar` | 1.5 — world expansion (15 ecosystems, lighting, spawning) | 1.21.1 | Fabric Loader ≥ 0.17.3, Fabric API 0.116.18+1.21.1 |
| `underdeep-0.1.0-stage1.jar` | 1 — foundation & world generation (old; generator v1) | 1.21.1 | Fabric Loader ≥ 0.17.3, Fabric API 0.116.18+1.21.1 |

Use only one Underdeep JAR at a time. Worlds made with the Stage 1 JAR must be recreated (terrain changed).
Stage 1.5 worlds open with Stage 2, but explored chunks have no Stage 2 content: start a fresh world.
The mod never modifies or deletes existing worlds.

Install: put the JAR and Fabric API in `mods/`. See the main `README.md` for world and server setup.
Rebuild from source with `./gradlew build` (output: `build/libs/`).
