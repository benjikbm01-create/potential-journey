# Texture standards (Stage 11.5)

## Code
All mod textures are produced by code in `tools/textures/`. `remake.py` runs last in `tools/assets/stage2_assets.py`,
so regenerated placeholder art is always replaced, and the pipeline is deterministic.

| File | What it does |
|---|---|
| `texlib.py` | Core: hue-shifted ramps, tileable rock mosaics, strata, ore formations, planks, bark, rings, leaves, sprites, outlines, bevels |
| `sample.py` | The approved sample designs: stones, building blocks, workstations, armour layers |
| `plants.py` | Botanical archetypes: caps, flower heads, grass, ferns, crop stages, bushes, saplings, kelp, vines, crystals, corals |
| `equipment.py` | 17 weapon/tool silhouettes with material signatures; armour families (hide, plate, horned, crested, scale, robe, bone) |
| `blocks_misc.py` | Workstations, furniture, mechanisms, ground covers. Symbol-bearing blocks are remastered pixel-for-pixel |
| `items.py` | Item archetypes (dishes, drinks, jars, breads, ingots, gems, rings, amulets, arrows, pouches) plus a remaster for unique items |
| Bundled pack | `resourcepacks/underdeep_caverns` restyles vanilla cave stone (stone, andesite, diorite, granite, tuff, calcite, deepslate, cobblestone, basalt, blackstone, dripstone). Registered as a built-in pack, on by default; players can turn it off in Resource Packs |

## Rules
- **Palette.**
  - Every material is a ramp of 5–7 tones.
  - Shadows shift toward blue and violet and gain saturation; highlights shift toward warm yellow.
  - No per-pixel random colours.
- **Lighting.**
  - Light comes from the top-left: lit top and left rims, shaded bottom and right rims.
  - Bevels go on crafted blocks.
- **Structure over noise.**
  - Stone is a mosaic of rock chunks with selective cracks.
  - Ores are mineral formations with a cavity rim and a highlight.
  - Wood has boards, staggered seams, grain and knots.
  - Leaves are drawn as leaf clusters.
- **Tiling.**
  - Block textures tile seamlessly: structure is computed on a torus.
  - Big-wall stones are low-contrast, so they don't read as cobblestone when repeated.
- **Items.**
  - Sprites have a dark outline, a clear silhouette and one deliberate signature detail per material.
- **Identity.**
  - Species and item colours come from their originals: sampled once and cached (`plant_identity.json`), or read from git
    commit `c098626`.
  - Puzzle symbols are never redrawn: their pixel positions are preserved exactly.

## Review process
- Every batch writes a contact sheet to `build/texture_sheets/<batch>.png`. Inspect it and revise before committing.
- Check terrain in game with `scripts/visual_tour.sh`, using night-vision views for detail.
