# Underdeep — Connected cave network (Stage 10)

## 1. What existed (audit, Stage 10A)
- Each layer (upper, deep) has a `CavernGraph`: one great chamber per grid cell (176 / 224 blocks), every chamber joined
  to its east and south neighbours by a tunnel (plus random diagonals), in warped coordinates. So each layer was
  already one connected network by construction; ground-truth flood fills (Stage 1.5) confirmed it.
- Terrain is a 2.5D model per column (floor and ceiling per layer) plus 3D noise (wall roughness, "spaghetti" tunnels).
- The two layers met only through vertical shafts: rare, only 65 % with a spiral ledge, often too narrow to walk.
- Measured on the v4 model (seed 42, 2400 × 2400 blocks): 82 % of upper and 87 % of deep open area in the largest
  walkable network; 95 % of great chambers reachable on foot; 16 shaft links; 0 walkable layer-to-layer caves.
- What makes chambers feel isolated: few openings in very large chamber walls (one tunnel per cell edge), steep floors
  inside chambers and around lakes, and practically no walkable way between the layers.

## 2. What Stage 10 adds (generator v5)
- **Secondary passages** (`world/terrain/Passages`): a finer grid (half the chamber grid) whose cells own an anchor,
  winding passages to neighbouring anchors (62 % east, 62 % south, 16 % diagonal) and dead-end side branches (35 %)
  ending in alcoves. Three size classes: side passages (≈4–6 wide), ordinary connecting caves (≈6–12), major corridors
  (≈13–24, taller). Width and height vary along each passage; centre lines meander (two sine terms enveloped so
  endpoints stay fixed). A third of anchors are **intermediate chambers** (radius 10–24, hills and pillars apply).
  Passages are evaluated in the same warped space and add open height above the layer floor exactly like the
  chamber tunnels, so floors stay continuous with caverns; regional palettes, vegetation, ores, wildlife spawning and
  lighting all come from the existing systems (passages crossing an ecosystem border show the normal blended transition).
- **Descent caves** (`world/terrain/Descents`): walkable spiral caves between the layers. Per deep cell (60 %, overlap
  resolved by priority with neighbours): the centre is chosen beside the deep chamber where the deep layer is solid; a
  lead tunnel leaves a dry upper cavern at floor level, a helix (radius 12–20, ≈1 block down per 3.5 forward,
  stacked turns separated by rock) winds down, and a second lead enters the deep chamber at its floor. Where the helix
  crosses open space it continues as a natural rock ledge; where it would meet water, lava or a pond it is closed by
  rock (never flooded; the band is classified dry). Descent floors are exact (no noise holes); wall relief is gentle.
- **Shafts**: in v5 every shaft has a spiral ledge and is wider (radius 8–13).
- **Protection**: settlements and exploration sites (incl. guardian lairs) are never placed where a descent runs; the
  spawn area (280 blocks) has none. Passages only open rock above floors, so authored buildings below floors are safe.
- **Determinism**: all of it is a pure function of (seed, cell) on the terrain model; caches are thread-local or
  shared immutable (bounded). Chunk generation order does not matter.

## 3. Existing worlds
The chunk generator stores its version; v3/v4 worlds keep generating their original terrain (no seams, no change).
The cave network, guardian lairs and everything new in worldgen exist only in **worlds created with v5**.

## 4. Configuration (`config/underdeep.json`, section `caves`)
`passageFrequency` (0–3, default 1) and `descentFrequency` (0–1.6, default 1). They change future terrain: set them
before creating a world.

## 5. Measurements (see docs/TESTING.md for runs)
- Model connectivity (v4 → v5, seed 42 / 12345 / 2024): open area +7–8 %; largest walkable network upper 82→85 % /
  89→90 % / 94→94 %; great chambers reachable 95→96 % / 96→97 % / 99→99 %; layer links 16→24 / 24→43 / 19→28.
- Terrain-model column cost: 8.3 → 9.2 µs per column contiguous, 8.5 → 9.4 µs strided (+10 %).
- Descents on the terrain model (voxel survival walk): 16/16 walkable down and back up near spawn (seed 42).
- Known limitation: steep chamber floors and some lake shores still block on-foot travel between a few neighbouring
  chambers (10/12 neighbour pairs physically walkable on the model, same in v4 and v5): a passage cannot fix a cliff
  inside a chamber. Stage 12 candidates: natural ramps at steep floor edges; shore shelves around lakes.

## 6. Pregeneration (Chunky etc.)
The generator needs no players and no other mod. Lairs and descents are computed from the seed, so pregeneration
order is irrelevant. Recommended once worldgen is final (Stage 12): create the world with the release JAR, pregenerate a
radius around spawn (e.g. 3000 blocks covers every guardian lair), then start the server. Not yet verified with Chunky
in this project (no test ran with it).
