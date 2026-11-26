package dev.underdeep.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.underdeep.Underdeep;
import dev.underdeep.block.plant.BloomingPlantBlock;
import dev.underdeep.block.plant.Env;
import dev.underdeep.block.plant.HarvestablePlantBlock;
import dev.underdeep.block.plant.ResinKnotBlock;
import dev.underdeep.block.plant.SnapjawBlock;
import dev.underdeep.block.plant.SporePuffBlock;
import dev.underdeep.ecology.EcologyRegistry;
import dev.underdeep.ecology.EcologyState;
import dev.underdeep.ecology.Habitats;
import dev.underdeep.ecology.RegionEcology;
import dev.underdeep.ecology.Species;
import dev.underdeep.registry.ModItems;
import dev.underdeep.registry.ModPlants;
import dev.underdeep.registry.ModWood;
import dev.underdeep.registry.WoodSet;
import dev.underdeep.world.analysis.RegionReport;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Stage 2 targeted functional checks, run inside the dedicated-server self-test. Every check builds
 * its own small sealed test plot (stone shell, controlled light and soil) inside the target
 * ecosystem, then drives the real block code with random ticks, a Fabric fake player and real loot
 * tables. Test plots are recorded in {@code stage2_plots.txt} so the reload phase can verify
 * persistence. Nothing here runs outside the property-gated self-test.
 */
final class Stage2Checks {
    private static final Map<String, BlockPos> PLOTS = new TreeMap<>();

    private Stage2Checks() {
    }

    // ================================================================== registry & resources

    static void registries(MinecraftServer server, Stage15Checks.Reporter r) {
        r.section("Stage 2 registries and resources");
        List<Identifier> blocks = Registries.BLOCK.getIds().stream().filter(i -> i.getNamespace().equals(Underdeep.MOD_ID)).sorted().toList();
        List<Identifier> items = Registries.ITEM.getIds().stream().filter(i -> i.getNamespace().equals(Underdeep.MOD_ID)).sorted().toList();
        r.info("registered", blocks.size() + " Underdeep blocks, " + items.size() + " items");
        JsonObject lang = json("/assets/underdeep/lang/en_us.json");
        List<String> missingState = new ArrayList<>(), missingLoot = new ArrayList<>(), missingName = new ArrayList<>(),
                missingItemModel = new ArrayList<>(), missingTexture = new ArrayList<>();
        Set<String> checkedModels = new HashSet<>();
        for (Identifier id : blocks) {
            Block b = Registries.BLOCK.get(id);
            JsonObject state = json("/assets/underdeep/blockstates/" + id.getPath() + ".json");
            if (state == null) {
                missingState.add(id.getPath());
            } else {
                for (String model : modelRefs(state)) {
                    checkModel(model, checkedModels, missingTexture);
                }
            }
            if (b.getLootTableKey() != null && server.getReloadableRegistries().getLootTable(b.getLootTableKey()) == LootTable.EMPTY) {
                missingLoot.add(id.getPath());
            }
            if (lang == null || !lang.has(b.getTranslationKey())) {
                missingName.add(id.getPath());
            }
        }
        for (Identifier id : items) {
            Item it = Registries.ITEM.get(id);
            JsonObject model = json("/assets/underdeep/models/item/" + id.getPath() + ".json");
            if (model == null) {
                missingItemModel.add(id.getPath());
            } else {
                checkModel("underdeep:item/" + id.getPath(), checkedModels, missingTexture);
            }
            if (lang == null || !lang.has(it.getTranslationKey())) {
                missingName.add(id.getPath());
            }
        }
        r.check("every block has a blockstate", missingState.isEmpty(), missingState.isEmpty() ? blocks.size() + " ok" : "missing " + missingState);
        r.check("every block has a loot table", missingLoot.isEmpty(), missingLoot.isEmpty() ? "ok" : "missing " + missingLoot);
        r.check("every item has a model", missingItemModel.isEmpty(), missingItemModel.isEmpty() ? items.size() + " ok" : "missing " + missingItemModel);
        r.check("every model's textures exist", missingTexture.isEmpty(),
                missingTexture.isEmpty() ? checkedModels.size() + " models checked" : "missing " + missingTexture);
        r.check("every block and item has an English name", missingName.isEmpty(), missingName.isEmpty() ? "ok" : "missing " + missingName);
        // ecology registry consistency
        List<String> unknown = new ArrayList<>();
        for (Species s : EcologyRegistry.all()) {
            for (String b : s.blockIds()) {
                if (!Registries.BLOCK.containsId(Identifier.of(Underdeep.MOD_ID, b))) {
                    unknown.add(s.id() + ":" + b);
                }
            }
        }
        r.check("every species maps to registered blocks", unknown.isEmpty(), unknown.isEmpty()
                ? EcologyRegistry.all().size() + " species" : "unknown " + unknown);
        Map<Species.Kind, Integer> kinds = new EnumMap<>(Species.Kind.class);
        for (Species s : EcologyRegistry.all()) {
            kinds.merge(s.kind(), 1, Integer::sum);
        }
        r.info("species by kind", kinds.toString());
        Set<RegionType> covered = EnumSetOf();
        for (Species s : EcologyRegistry.all()) {
            covered.addAll(s.habitats());
        }
        r.check("every ecosystem has Stage 2 species", covered.size() == RegionType.values().length, covered.size() + "/15");
        for (RegionType t : RegionType.values()) {
            r.info("ecology " + t.id(), RegionEcology.of(t, 0).describe());
        }
        r.check("wood sets complete", WoodSet.ALL.size() == 7 && WoodSet.ALL.stream().allMatch(w -> w.leaves != null && w.sapling != null
                && w.hangingSign != null && w.door != null), WoodSet.ALL.size() + " sets");
    }

    private static Set<RegionType> EnumSetOf() {
        return java.util.EnumSet.noneOf(RegionType.class);
    }

    private static JsonObject json(String path) {
        try (InputStream in = Stage2Checks.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> modelRefs(JsonObject state) {
        List<String> out = new ArrayList<>();
        collect(state, out);
        return out;
    }

    private static void collect(JsonElement e, List<String> out) {
        if (e.isJsonObject()) {
            for (var en : e.getAsJsonObject().entrySet()) {
                if (en.getKey().equals("model") && en.getValue().isJsonPrimitive()) {
                    out.add(en.getValue().getAsString());
                } else {
                    collect(en.getValue(), out);
                }
            }
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(x -> collect(x, out));
        }
    }

    private static void checkModel(String model, Set<String> checked, List<String> missing) {
        if (!model.startsWith("underdeep:") || !checked.add(model)) {
            return;
        }
        JsonObject m = json("/assets/underdeep/models/" + model.substring("underdeep:".length()) + ".json");
        if (m == null) {
            missing.add("model " + model);
            return;
        }
        if (m.has("textures")) {
            for (var en : m.getAsJsonObject("textures").entrySet()) {
                String tex = en.getValue().getAsString();
                if (tex.startsWith("underdeep:") && Stage2Checks.class.getResource("/assets/underdeep/textures/"
                        + tex.substring("underdeep:".length()) + ".png") == null) {
                    missing.add(tex);
                }
            }
        }
        if (m.has("parent")) {
            checkModel(m.get("parent").getAsString(), checked, missing);
        }
    }

    // ================================================================== recipes

    static void recipes(ServerWorld world, Stage15Checks.Reporter r) {
        r.section("Stage 2 recipes (real recipe manager)");
        long n = world.getServer().getRecipeManager().values().stream().filter(e -> e.id().getNamespace().equals(Underdeep.MOD_ID)).count();
        r.info("Underdeep recipes loaded", Long.toString(n));
        for (WoodSet w : WoodSet.ALL) {
            craft(world, r, w.name + " log -> planks", 1, 1, List.of(new ItemStack(w.log)), w.planks.asItem(), 4);
        }
        ItemStack p = new ItemStack(ModWood.DUSKOAK.planks);
        craft(world, r, "duskoak planks -> crafting table (vanilla tag)", 2, 2, List.of(p.copy(), p.copy(), p.copy(), p.copy()), Items.CRAFTING_TABLE, 1);
        craft(world, r, "duskoak planks -> sticks (vanilla tag)", 1, 2, List.of(p.copy(), p.copy()), Items.STICK, 4);
        ItemStack e = ItemStack.EMPTY;
        craft(world, r, "lumenwood door", 2, 3, List.of(new ItemStack(ModWood.LUMENWOOD.planks), new ItemStack(ModWood.LUMENWOOD.planks),
                new ItemStack(ModWood.LUMENWOOD.planks), new ItemStack(ModWood.LUMENWOOD.planks), new ItemStack(ModWood.LUMENWOOD.planks),
                new ItemStack(ModWood.LUMENWOOD.planks)), ModWood.LUMENWOOD.door.asItem(), 3);
        craft(world, r, "frostfir sign", 3, 3, List.of(new ItemStack(ModWood.FROSTFIR.planks), new ItemStack(ModWood.FROSTFIR.planks),
                new ItemStack(ModWood.FROSTFIR.planks), new ItemStack(ModWood.FROSTFIR.planks), new ItemStack(ModWood.FROSTFIR.planks),
                new ItemStack(ModWood.FROSTFIR.planks), e, new ItemStack(Items.STICK), e), Registries.ITEM.get(Identifier.of("underdeep", "frostfir_sign")), 3);
        craft(world, r, "gunpowder from sulfur, niter and charcoal", 3, 1, List.of(new ItemStack(ModItems.SULFUR), new ItemStack(ModItems.NITER),
                new ItemStack(Items.CHARCOAL)), Items.GUNPOWDER, 3);
        craft(world, r, "glowgrain bread", 3, 1, List.of(new ItemStack(ModItems.GLOWGRAIN), new ItemStack(ModItems.GLOWGRAIN),
                new ItemStack(ModItems.GLOWGRAIN)), ModItems.GLOWGRAIN_BREAD, 1);
        craft(world, r, "emberwort tonic", 3, 1, List.of(new ItemStack(Items.GLASS_BOTTLE), new ItemStack(ModItems.EMBERWORT_ROOT),
                new ItemStack(ModItems.EMBERPEPPER)), ModItems.EMBERWORT_TONIC, 1);
        craft(world, r, "galvanite -> redstone", 1, 1, List.of(new ItemStack(ModItems.GALVANITE)), Items.REDSTONE, 3);
        smelt(world, r, "duskroot -> baked duskroot", ModItems.DUSKROOT, ModItems.BAKED_DUSKROOT);
        smelt(world, r, "inkcap -> roasted inkcap", ModItems.INKCAP, ModItems.ROASTED_INKCAP);
        smelt(world, r, "raw starsilver -> ingot", ModItems.RAW_STARSILVER, ModItems.STARSILVER_INGOT);
        smelt(world, r, "malachite -> copper ingot", ModItems.MALACHITE, Items.COPPER_INGOT);
        // food components
        List<String> noFood = new ArrayList<>();
        for (Item it : List.of(ModItems.DUSKBERRIES, ModItems.HONEYFRUIT, ModItems.SNOWBERRIES, ModItems.CLIFFBERRIES, ModItems.BRIAR_FRUIT,
                ModItems.SEA_GRAPES, ModItems.DUSKROOT, ModItems.BAKED_DUSKROOT, ModItems.GLOWGRAIN_BREAD, ModItems.CAVE_TRUFFLE,
                ModItems.ROASTED_TRUFFLE, ModItems.RICE_BOWL, ModItems.LAKE_CHESTNUT, ModItems.ROASTED_CHESTNUT, ModItems.EMBERPEPPER,
                ModItems.FROST_TURNIP, ModItems.BAKED_TURNIP, ModItems.SUNBEAN, ModItems.SUNBEAN_STEW, ModItems.PUFFCAP, ModItems.INKCAP,
                ModItems.ROASTED_INKCAP, ModItems.RIMECAP, ModItems.CAVERN_STEW, ModItems.RELIC_FRUIT)) {
            if (it.getComponents().get(DataComponentTypes.FOOD) == null) {
                noFood.add(Registries.ITEM.getId(it).getPath());
            }
        }
        r.check("all foods are edible", noFood.isEmpty(), noFood.isEmpty() ? "25 foods" : "not edible: " + noFood);
    }

    private static void craft(ServerWorld w, Stage15Checks.Reporter r, String name, int width, int height, List<ItemStack> stacks, Item expect, int count) {
        CraftingRecipeInput in = CraftingRecipeInput.create(width, height, stacks);
        var match = w.getServer().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, in, w);
        ItemStack out = match.map(m -> m.value().craft(in, w.getRegistryManager())).orElse(ItemStack.EMPTY);
        r.check("recipe " + name, out.isOf(expect) && out.getCount() == count, out.isEmpty() ? "no match" : out.getCount() + "x " + Registries.ITEM.getId(out.getItem()));
    }

    private static void smelt(ServerWorld w, Stage15Checks.Reporter r, String name, Item in, Item expect) {
        var input = new SingleStackRecipeInput(new ItemStack(in));
        var match = w.getServer().getRecipeManager().getFirstMatch(RecipeType.SMELTING, input, w);
        ItemStack out = match.map(m -> m.value().craft(input, w.getRegistryManager())).orElse(ItemStack.EMPTY);
        r.check("smelting " + name, out.isOf(expect), out.isEmpty() ? "no match" : Registries.ITEM.getId(out.getItem()).toString());
    }

    // ================================================================== natural generation census

    static void census(ServerWorld world, UnderdeepTerrain t, Map<RegionType, int[]> reps, Path dir, Stage15Checks.Reporter r) {
        r.section("Stage 2 natural generation per ecosystem (5x5 generated chunks around an open cavern floor, that cavern layer only)");
        long t0 = System.nanoTime();
        int chunks = 0;
        StringBuilder tour = new StringBuilder("# name x y z yaw pitch [natural]\n");
        for (RegionType type : RegionType.values()) {
            int[] p = reps.get(type);
            if (p == null) {
                r.check("stage 2 content in " + type.id(), false, "no representative region");
                continue;
            }
            int[] site = Stage15Checks.siteFor(t, type, p);
            Map<String, Integer> counts = new TreeMap<>();
            Map<String, BlockPos> nearest = new HashMap<>();
            boolean upper = CavernLayer.values()[p[1]] == CavernLayer.UPPER;
            int yMin = -60, yMax = 312;
            int ccx = site[0] >> 4, ccz = site[1] >> 4;
            BlockPos.Mutable m = new BlockPos.Mutable();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Chunk c = world.getChunk(ccx + dx, ccz + dz, ChunkStatus.FULL, true);
                    chunks++;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            TerrainColumn col = t.column(c.getPos().getStartX() + x, c.getPos().getStartZ() + z);
                            CavernLayer want = CavernLayer.values()[p[1]];
                            for (int y = yMin; y < yMax; y++) {
                                if (col.layerAt(y) != want) {
                                    continue;
                                }
                                m.set(c.getPos().getStartX() + x, y, c.getPos().getStartZ() + z);
                                BlockState s = c.getBlockState(m);
                                if (s.isAir() || s.isOf(Blocks.STONE) || s.isOf(Blocks.WATER)) {
                                    continue;
                                }
                                Species sp = Habitats.speciesOf(s);
                                if (sp != null) {
                                    counts.merge(sp.id(), 1, Integer::sum);
                                    BlockPos prev = nearest.get(sp.id());
                                    if (sp.isBiological() && sp.habitats().contains(type) && world.getBlockState(m.up()).isAir()
                                            && (prev == null || prev.getSquaredDistance(site[0], prev.getY(), site[1]) > m.getSquaredDistance(site[0], m.getY(), site[1]))) {
                                        nearest.put(sp.id(), m.toImmutable());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            long natives = counts.keySet().stream().filter(id -> EcologyRegistry.byId(id).habitats().contains(type)).count();
            long trees = counts.keySet().stream().filter(id -> EcologyRegistry.byId(id).kind() == Species.Kind.TREE).count();
            r.check("stage 2 content in " + type.id(), natives >= 3, String.format(Locale.ROOT,
                    "near %d,%d: %d native species (%d tree species), counts %s", site[0], site[1], natives, trees, counts));
            // ground-level camera views of the most visible native organisms (for the screenshot tour)
            List<String> picks = nearest.keySet().stream().sorted((a, b) -> {
                int ka = EcologyRegistry.byId(a).kind() == Species.Kind.TREE || EcologyRegistry.byId(a).kind() == Species.Kind.GIANT_FUNGUS ? 0 : 1;
                int kb = EcologyRegistry.byId(b).kind() == Species.Kind.TREE || EcologyRegistry.byId(b).kind() == Species.Kind.GIANT_FUNGUS ? 0 : 1;
                return ka != kb ? ka - kb : Integer.compare(counts.get(b), counts.get(a));
            }).limit(2).toList();
            for (String id : picks) {
                BlockPos target = nearest.get(id);
                for (int[] d : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}, {8, 0}, {0, 8}, {-8, 0}, {0, -8}}) {
                    BlockPos cam = target.add(d[0], 2, d[1]);
                    if (world.getBlockState(cam).isAir() && world.getBlockState(cam.up()).isAir()) {
                        double dx = target.getX() - cam.getX(), dz = target.getZ() - cam.getZ(), dy = target.getY() + 1 - cam.getY();
                        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                        tour.append(String.format(Locale.ROOT, "s2_%s_%s %.1f %.1f %.1f %.0f %.0f natural\n", type.id(), id,
                                cam.getX() + 0.5, (double) cam.getY(), cam.getZ() + 0.5, yaw, pitch));
                        break;
                    }
                }
            }
        }
        r.info("census cost", String.format(Locale.ROOT, "%d chunk scans in %.1f s", chunks, (System.nanoTime() - t0) / 1e9));
        try {
            Files.writeString(dir.resolve("tour_stage2.txt"), tour.toString());
        } catch (Exception e) {
            Underdeep.LOGGER.warn("could not write stage 2 tour", e);
        }
    }

    /** Server cost of Stage 2 random ticks: time per randomTick call on representative ticking organisms. */
    static void tickCost(ServerWorld w, Stage15Checks.Reporter r) {
        BlockPos o = w.getSpawnPos().add(18, 24, 18);
        plot(w, o, 4, 5, Blocks.DIRT.getDefaultState(), true, false);
        Block[] blocks = {ModPlants.GLOWFERN, ModPlants.PUFFCAP, ModPlants.MENDLEAF, ModPlants.DUSKBERRY_BUSH, ModPlants.SPOREPUFF,
                ModPlants.LANTERNCAP, ModWood.DUSKOAK.sapling, ModPlants.DUSKROOTS};
        List<BlockPos> ps = new ArrayList<>();
        for (int i = 0; i < blocks.length; i++) {
            BlockPos p = o.add(-3 + (i % 4) * 2, 0, -2 + (i / 4) * 4);
            w.setBlockState(p, blocks[i].getDefaultState(), Block.NOTIFY_ALL);
            ps.add(p);
        }
        int calls = 0;
        long t0 = System.nanoTime();
        for (int k = 0; k < 4000; k++) {
            for (BlockPos p : ps) {
                BlockState s = w.getBlockState(p);
                if (s.hasRandomTicks()) {
                    s.randomTick(w, p, w.random);
                    calls++;
                }
            }
        }
        double us = (System.nanoTime() - t0) / 1e3 / Math.max(1, calls);
        r.info("stage 2 random tick cost", String.format(Locale.ROOT,
                "%.2f µs per random tick over %d calls (mixed plants, fungi, bush, sapling, crop incl. spreading)", us, calls));
        for (BlockPos p : BlockPos.iterate(o.add(-5, -1, -5), o.add(5, 5, 5))) {
            w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    // ================================================================== functional tests

    private static final Set<Long> USED_PLOTS = new HashSet<>();

    /** A free plot location well inside the given ecosystem near its representative site (offset only seeds the order). */
    static BlockPos plotOrigin(UnderdeepTerrain t, RegionType type, Map<RegionType, int[]> reps, int offset) {
        int[] p = reps.get(type);
        int[] site = Stage15Checks.siteFor(t, type, p);
        boolean up = CavernLayer.values()[p[1]] == CavernLayer.UPPER;
        for (int rad = 60; rad <= 300; rad += 20) {
            for (int dx = -rad; dx <= rad; dx += 20) {
                for (int dz = -rad; dz <= rad; dz += 20) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rad) {
                        continue;
                    }
                    int x = site[0] + dx, z = site[1] + dz;
                    long key = ((long) (x >> 4) << 32) ^ (z >> 4);
                    TerrainColumn c = t.column(x, z);
                    var reg = up ? c.upperRegion : c.deepRegion;
                    // keep test plots out of the starter area so they never alter the world spawn
                    var settlements = dev.underdeep.civ.settlement.Settlements.of(t);
                    boolean inSettlement = settlements != null && settlements.siteAt(x, z, 40) != null;
                    if (reg.primary == type && reg.borderDistance > 18 && !USED_PLOTS.contains(key) && Math.hypot(x, z) > 72 && !inSettlement) {
                        USED_PLOTS.add(key);
                        int floor = (int) Math.round(up ? c.upperFloor : c.deepFloor);
                        return new BlockPos(x, floor + 4, z);
                    }
                }
            }
        }
        TerrainColumn c = t.column(site[0] + offset, site[1]);
        return new BlockPos(site[0] + offset, (int) Math.round(up ? c.upperFloor : c.deepFloor) + 4, site[1]);
    }

    /** Sealed plot: shell of stone, floor of the given block, air inside; optional light ring and heat. */
    static BlockPos plot(ServerWorld w, BlockPos o, int half, int height, BlockState floor, boolean light, boolean heat) {
        for (int cx = (o.getX() - half - 1) >> 4; cx <= (o.getX() + half + 1) >> 4; cx++) {
            for (int cz = (o.getZ() - half - 1) >> 4; cz <= (o.getZ() + half + 1) >> 4; cz++) {
                w.getChunk(cx, cz, ChunkStatus.FULL, true);
            }
        }
        BlockPos.Mutable m = new BlockPos.Mutable();
        int f = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        for (int x = -half - 1; x <= half + 1; x++) {
            for (int z = -half - 1; z <= half + 1; z++) {
                for (int y = -2; y <= height; y++) {
                    m.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                    boolean shell = Math.abs(x) == half + 1 || Math.abs(z) == half + 1 || y == -2 || y == height;
                    BlockState s = shell ? Blocks.STONE.getDefaultState() : y == -1 ? floor : Blocks.AIR.getDefaultState();
                    w.setBlockState(m, s, f);
                }
            }
        }
        if (light) {
            for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}}) {
                w.setBlockState(o.add(d[0], -1, d[1]), Blocks.SEA_LANTERN.getDefaultState(), f);
            }
        }
        if (heat) {
            w.setBlockState(o.add(1, -1, 2), Blocks.MAGMA_BLOCK.getDefaultState(), f);
        }
        flushLight(w);
        return o;
    }

    /**
     * The self-test runs inside one server task, so queued light updates would only be processed at
     * the next tick. Drive the light engine explicitly so test plots have real block light.
     */
    static void flushLight(ServerWorld w) {
        var light = w.getChunkManager().getLightingProvider();
        for (int i = 0; i < 400 && light.hasUpdates(); i++) {
            light.tick();
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        for (int k = 0; k < 4; k++) {
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            light.tick();
            for (int i = 0; i < 100 && light.hasUpdates(); i++) {
                light.tick();
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static long itemsNear(ServerWorld w, BlockPos p, Item item) {
        long n = 0;
        for (var e : w.iterateEntities()) {
            if (e instanceof ItemEntity ie && ie.getStack().isOf(item) && e.getBlockPos().isWithinDistance(p, 3)) {
                n++;
            }
        }
        return n;
    }

    private static boolean tickUntil(ServerWorld w, BlockPos pos, int max, Predicate<BlockState> done) {
        for (int i = 0; i < max; i++) {
            BlockState s = w.getBlockState(pos);
            if (done.test(s)) {
                return true;
            }
            if (s.hasRandomTicks()) {
                s.randomTick(w, pos, w.random);
            }
        }
        return done.test(w.getBlockState(pos));
    }

    static void functional(MinecraftServer server, ServerWorld w, UnderdeepTerrain t, Map<RegionType, int[]> reps, Path dir,
                           Stage15Checks.Reporter r) {
        r.section("Stage 2 functional tests (sealed test plots inside each ecosystem, real block code)");
        BlockState dirt = Blocks.DIRT.getDefaultState();
        // ---- saplings grow underground (block light only)
        Object[][] trees = {
                {"duskoak", ModWood.DUSKOAK.sapling, ModWood.DUSKOAK.log, RegionType.ROOTWILD, dirt, false},
                {"spirebark", ModWood.SPIREBARK.sapling, ModWood.SPIREBARK.log, RegionType.ROOTWILD, Blocks.STONE.getDefaultState(), false},
                {"goldbough", ModWood.GOLDWOOD.sapling, ModWood.GOLDWOOD.log, RegionType.GOLDEN_GROVE, dirt, false},
                {"mirewillow", ModWood.MIREWILLOW.sapling, ModWood.MIREWILLOW.log, RegionType.EMERALD_WETLANDS, Blocks.MUD.getDefaultState(), false},
                {"frostfir", ModWood.FROSTFIR.sapling, ModWood.FROSTFIR.log, RegionType.EVERFROST, Blocks.SNOW_BLOCK.getDefaultState(), false},
                {"bloodthorn", ModWood.BLOODTHORN.sapling, ModWood.BLOODTHORN.log, RegionType.CRIMSON_BASIN, Blocks.NETHERRACK.getDefaultState(), false},
                {"lumenwood", ModWood.LUMENWOOD.sapling, ModWood.LUMENWOOD.log, RegionType.STARLIGHT_CAVERNS, dirt, false},
                {"cragpine", ModWood.CRAGPINE_SAPLING, ModWood.SPIREBARK.log, RegionType.HOLLOW_PEAKS, Blocks.STONE.getDefaultState(), false},
                {"cinderbark", ModWood.CINDERBARK_SAPLING, ModWood.CINDERBARK_LOG, RegionType.ASHEN_HOLLOWS, Blocks.BASALT.getDefaultState(), true},
        };
        int off = 0;
        for (Object[] tr : trees) {
            String sp = (String) tr[0];
            BlockPos o = plot(w, plotOrigin(t, (RegionType) tr[3], reps, 40 + off), 8, 24, (BlockState) tr[4], true, (Boolean) tr[5]);
            off += 20;
            w.setBlockState(o, ((Block) tr[1]).getDefaultState(), Block.NOTIFY_ALL);
            Block log = (Block) tr[2];
            boolean grew = tickUntil(w, o, 2000, s -> s.isOf(log));
            int logs = countAround(w, o, 8, 24, s -> s.isOf(log));
            r.check("sapling grows underground: " + sp, grew, String.format(Locale.ROOT, "light %d, sky %d, %d logs placed",
                    w.getLightLevel(net.minecraft.world.LightType.BLOCK, o.up()), w.getLightLevel(net.minecraft.world.LightType.SKY, o.up()), logs));
            PLOTS.put("tree_" + sp, o);
        }
        // dormancy: frostfir next to heat does not grow
        BlockPos fo = plot(w, plotOrigin(t, RegionType.EVERFROST, reps, -60), 6, 18, Blocks.SNOW_BLOCK.getDefaultState(), true, true);
        w.setBlockState(fo, ModWood.FROSTFIR.sapling.getDefaultState(), Block.NOTIFY_ALL);
        boolean grewHot = tickUntil(w, fo, 1500, s -> s.isOf(ModWood.FROSTFIR.log));
        r.check("frostfir sapling stays dormant beside heat", !grewHot, grewHot ? "grew" : "dormant after 1500 random ticks");

        // ---- growth respects player constructions
        BlockPos po = plot(w, plotOrigin(t, RegionType.ROOTWILD, reps, -40), 7, 20, dirt, true, false);
        List<BlockPos> planks = new ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos roof = po.add(x, 5, z);
                planks.add(roof);
                w.setBlockState(roof, ModWood.DUSKOAK.planks.getDefaultState(), Block.NOTIFY_ALL);
            }
            planks.add(po.add(x, 2, 2));
            w.setBlockState(po.add(x, 2, 2), Blocks.CHEST.getDefaultState(), Block.NOTIFY_ALL);
        }
        w.setBlockState(po, ModWood.DUSKOAK.sapling.getDefaultState(), Block.NOTIFY_ALL);
        tickUntil(w, po, 2000, s -> s.isOf(ModWood.DUSKOAK.log));
        long intact = planks.stream().filter(p -> !w.getBlockState(p).isAir() && !w.getBlockState(p).isIn(net.minecraft.registry.tag.BlockTags.LOGS)
                && !w.getBlockState(p).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)).count();
        r.check("growing tree never replaces player blocks", intact == planks.size(), intact + "/" + planks.size()
                + " player blocks intact; sapling " + (w.getBlockState(po).isOf(ModWood.DUSKOAK.log) ? "grew around them" : "did not grow (no room)"));

        // ---- crops
        Object[][] crops = {
                {ModPlants.DUSKROOTS, ModItems.DUSKROOT, RegionType.ROOTWILD, true, false},
                {ModPlants.GLOWGRAIN_CROP, ModItems.GLOWGRAIN, RegionType.STARLIGHT_CAVERNS, true, false},
                {ModPlants.CAVE_TRUFFLES, ModItems.CAVE_TRUFFLE, RegionType.ROOTWILD, false, false},
                {ModPlants.MIRE_RICE_CROP, ModItems.MIRE_RICE, RegionType.EMERALD_WETLANDS, true, false},
                {ModPlants.EMBERPEPPERS, ModItems.EMBERPEPPER, RegionType.ASHEN_HOLLOWS, true, true},
                {ModPlants.FROST_TURNIPS, ModItems.FROST_TURNIP, RegionType.EVERFROST, true, false},
                {ModPlants.SUNBEANS, ModItems.SUNBEAN, RegionType.GOLDEN_GROVE, true, false},
        };
        off = 0;
        for (Object[] c : crops) {
            Block crop = (Block) c[0];
            String id = Registries.BLOCK.getId(crop).getPath();
            BlockPos o = plot(w, plotOrigin(t, (RegionType) c[2], reps, -100 - off), 4, 6, dirt, (Boolean) c[3], (Boolean) c[4]);
            off += 12;
            w.setBlockState(o.down(), Blocks.FARMLAND.getDefaultState(), Block.NOTIFY_ALL);
            w.setBlockState(o.add(1, -1, 0), Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
            w.setBlockState(o, crop.getDefaultState(), Block.NOTIFY_ALL);
            boolean placed = w.getBlockState(o).isOf(crop);
            int[] ticks = {0};
            boolean mature = tickUntil(w, o, 6000, s -> {
                ticks[0]++;
                return s.getBlock() instanceof CropBlock cb && cb.isMature(s);
            });
            List<ItemStack> drops = Block.getDroppedStacks(w.getBlockState(o), w, o, null);
            boolean dropsProduce = drops.stream().anyMatch(st -> st.isOf((Item) c[1]));
            r.check("crop grows and drops produce: " + id, placed && mature && dropsProduce, String.format(Locale.ROOT,
                    "light %d, %d random ticks, drops %s", w.getLightLevel(o), ticks[0], drops.stream().map(st -> st.getCount() + "x"
                            + Registries.ITEM.getId(st.getItem()).getPath()).toList()));
            PLOTS.put("crop_" + id, o);
        }
        // aquatic crop + water restored when broken
        BlockPos ao = plot(w, plotOrigin(t, RegionType.FLOODED_DEPTHS, reps, 70), 3, 5, Blocks.SAND.getDefaultState(), true, false);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = 0; y <= 2; y++) {
                    w.setBlockState(ao.add(x, y, z), Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        w.setBlockState(ao, ModPlants.LAKE_CHESTNUTS.getDefaultState(), Block.NOTIFY_ALL);
        boolean chestnut = tickUntil(w, ao, 6000, s -> s.isOf(ModPlants.LAKE_CHESTNUTS) && ((CropBlock) s.getBlock()).isMature(s));
        r.check("aquatic crop grows underwater: lake chestnuts", chestnut, "mature=" + chestnut);
        w.breakBlock(ao, true);
        r.check("breaking a submerged plant leaves water", w.getFluidState(ao).isStill() && w.getBlockState(ao).isOf(Blocks.WATER),
                w.getBlockState(ao).toString());
        // floating plants spread on still water, bounded
        w.setBlockState(ao.up(3), ModPlants.GLOWPAD.getDefaultState(), Block.NOTIFY_ALL);
        // glowpads need still water below them; the pool surface is at ao.up(2)
        int pads = spreadRounds(w, ao, 3, 6, ModPlants.GLOWPAD, 2500);
        r.check("glowpads spread across still water, bounded", pads > 1 && pads <= 20,
                pads + " glowpads on a 7x7 pool (49 cells) after 2500 rounds; bound: density cap per 7x7 neighbourhood");

        // ---- harvesting with a fake player, regrowth and ecology records
        FakePlayer fake = FakePlayer.get(w);
        // spawn chunks are entity-loaded, so dropped items are observable there (spawn is always in Rootwild)
        BlockPos ho = plot(w, w.getSpawnPos().add(-18, 24, -18), 3, 5, dirt, true, false);
        fake.setPosition(Vec3d.ofCenter(ho.up()));
        w.setBlockState(ho, ModPlants.DUSKBERRY_BUSH.getDefaultState().with(HarvestablePlantBlock.AGE, 3), Block.NOTIFY_ALL);
        long before = itemsNear(w, ho, ModItems.DUSKBERRIES);
        w.getBlockState(ho).onUse(w, fake, new BlockHitResult(Vec3d.ofCenter(ho), Direction.UP, ho, false));
        long dropped = itemsNear(w, ho, ModItems.DUSKBERRIES) - before;
        r.check("bush harvest yields fruit and resets", dropped > 0 && w.getBlockState(ho).get(HarvestablePlantBlock.AGE) == 1,
                dropped + " fruit stack(s), age now " + w.getBlockState(ho).get(HarvestablePlantBlock.AGE));
        boolean regrew = tickUntil(w, ho, 2000, s -> s.get(HarvestablePlantBlock.AGE) == 3);
        r.check("bush regrows after harvest", regrew, "age 3 again=" + regrew);
        RegionReport rr = RegionReport.at(t, ho.getX(), ho.getY(), ho.getZ());
        int harvested = EcologyState.harvests(w, rr.regionId().asLong(), "duskberry");
        r.check("harvest recorded in regional ecology state", harvested > 0, harvested + " duskberry harvest(s) in region " + rr.regionId());
        PLOTS.put("harvest_region", new BlockPos((int) (rr.regionId().asLong() >> 32), (int) rr.regionId().asLong(), 0));
        // resin knot
        w.setBlockState(ho.add(2, 0, 0), ModWood.GOLDWOOD.log.getDefaultState(), Block.NOTIFY_ALL);
        BlockPos knot = ho.add(1, 0, 0);
        w.setBlockState(knot, ModPlants.RESIN_KNOT.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST).with(ResinKnotBlock.AGE, 2), Block.NOTIFY_ALL);
        w.getBlockState(knot).onUse(w, fake, new BlockHitResult(Vec3d.ofCenter(knot), Direction.WEST, knot, false));
        boolean resin = itemsNear(w, knot, ModItems.AMBER_GLOB) > 0;
        r.check("resin knot yields renewable amber", resin && w.getBlockState(knot).get(ResinKnotBlock.AGE) == 0, "resin dropped=" + resin);

        // ---- foods and remedies
        ItemStack ink = new ItemStack(ModItems.INKCAP);
        ink.finishUsing(w, fake);
        r.check("raw inkcap is toxic", fake.hasStatusEffect(StatusEffects.NAUSEA), "nausea=" + fake.hasStatusEffect(StatusEffects.NAUSEA));
        ItemStack antidote = new ItemStack(ModItems.ANTIDOTE_TINCTURE);
        antidote.getItem().finishUsing(antidote, w, fake);
        r.check("antidote cures harmful effects", !fake.hasStatusEffect(StatusEffects.NAUSEA) && !fake.hasStatusEffect(StatusEffects.POISON), "cured");
        ItemStack tonic = new ItemStack(ModItems.EMBERWORT_TONIC);
        ItemStack after = tonic.getItem().finishUsing(tonic, w, fake);
        r.check("emberwort tonic grants fire resistance", fake.hasStatusEffect(StatusEffects.FIRE_RESISTANCE), "returned " + after);
        ItemStack draught = new ItemStack(ModItems.MOONPETAL_DRAUGHT);
        draught.getItem().finishUsing(draught, w, fake);
        r.check("moonpetal draught grants night vision", fake.hasStatusEffect(StatusEffects.NIGHT_VISION), "ok");
        int hungerBefore = fake.getHungerManager().getFoodLevel();
        fake.getHungerManager().setFoodLevel(10);
        new ItemStack(ModItems.BAKED_DUSKROOT).finishUsing(w, fake);
        r.check("baked duskroot restores hunger", fake.getHungerManager().getFoodLevel() == 15, "10 -> " + fake.getHungerManager().getFoodLevel()
                + " (was " + hungerBefore + ")");

        // ---- drops: ores and plants with real loot tables
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        Object[][] ores = {{ModPlants.CINNABAR_ORE, ModItems.CINNABAR}, {ModPlants.SULFUR_ORE, ModItems.SULFUR},
                {ModPlants.BOG_IRON_ORE, Items.RAW_IRON}, {ModPlants.GALVANITE_ORE, ModItems.GALVANITE},
                {ModPlants.UMBRALITE_ORE, ModItems.RAW_UMBRALITE}, {ModPlants.PRISMATIC_OPAL_ORE, ModItems.PRISMATIC_OPAL},
                {ModPlants.STARSILVER_ORE, ModItems.RAW_STARSILVER}, {ModPlants.NACRE_ORE, ModItems.PEARL},
                {ModPlants.EMBERITE_ORE, ModItems.EMBERITE}, {ModPlants.CRYOLITE_ORE, ModItems.CRYOLITE},
                {ModPlants.MALACHITE_ORE, ModItems.MALACHITE}, {ModPlants.JASPER_ORE, ModItems.JASPER},
                {ModPlants.FOSSIL_ORE, ModItems.FOSSIL_FRAGMENT}, {ModPlants.NITER_ORE, ModItems.NITER}};
        List<String> badOres = new ArrayList<>();
        for (Object[] o : ores) {
            Block b = (Block) o[0];
            List<ItemStack> d = Block.getDroppedStacks(b.getDefaultState(), w, ho, null, fake, pick);
            boolean ok = d.stream().anyMatch(s -> s.isOf((Item) o[1])) && pick.isSuitableFor(b.getDefaultState());
            if (!ok) {
                badOres.add(Registries.BLOCK.getId(b).getPath() + "=" + d);
            }
        }
        r.check("every regional ore is mineable and drops its mineral", badOres.isEmpty(), badOres.isEmpty() ? ores.length + " ores" : badOres.toString());
        List<ItemStack> herb = Block.getDroppedStacks(ModPlants.MENDLEAF.getDefaultState().with(HarvestablePlantBlock.AGE, 3), w, ho, null);
        r.check("mature herb drops itself and its leaves", herb.stream().anyMatch(s -> s.isOf(ModPlants.MENDLEAF.asItem()))
                && herb.stream().anyMatch(s -> s.isOf(ModItems.MENDLEAF_SPRIG)), herb.toString());

        // ---- reactive plants
        // spawn chunks: entities there are tracked, so area effects on creatures are observable
        BlockPos co = plot(w, w.getSpawnPos().add(18, 24, -18), 3, 5, Blocks.NETHERRACK.getDefaultState(), true, false);
        w.setBlockState(co, ModPlants.SNAPJAW.getDefaultState(), Block.NOTIFY_ALL);
        PigEntity pig = EntityType.PIG.create(w);
        pig.refreshPositionAndAngles(co.getX() + 0.5, co.getY(), co.getZ() + 0.5, 0, 0);
        w.spawnEntity(pig);
        float hp = pig.getHealth();
        w.getBlockState(co).onEntityCollision(w, co, pig);
        r.check("snapjaw snaps at a creature, then closes", pig.getHealth() < hp && !w.getBlockState(co).get(SnapjawBlock.OPEN),
                String.format(Locale.ROOT, "health %.1f -> %.1f", hp, pig.getHealth()));
        w.setBlockState(co.add(2, 0, 0), ModPlants.SPOREPUFF.getDefaultState(), Block.NOTIFY_ALL);
        pig.refreshPositionAndAngles(co.getX() + 2.5, co.getY(), co.getZ() + 0.5, 0, 0);
        w.getBlockState(co.add(2, 0, 0)).onEntityCollision(w, co.add(2, 0, 0), pig);
        r.check("spore puff bursts when touched", w.getBlockState(co.add(2, 0, 0)).get(SporePuffBlock.SPENT)
                && pig.hasStatusEffect(StatusEffects.WEAKNESS), "spent, creature briefly weakened");
        pig.discard();

        // ---- blooming responds to environment
        BlockPos bo = plot(w, plotOrigin(t, RegionType.ASHEN_HOLLOWS, reps, -190), 9, 5, Blocks.BASALT.getDefaultState(), false, true);
        BlockPos nearHeat = bo.add(1, 0, 1);
        BlockPos farHeat = bo.add(-4, 0, -4);
        w.setBlockState(nearHeat, ModPlants.PYRELILY.getDefaultState(), Block.NOTIFY_ALL);
        w.setBlockState(farHeat, ModPlants.PYRELILY.getDefaultState(), Block.NOTIFY_ALL);
        boolean bloomed = tickUntil(w, nearHeat, 300, s -> s.get(BloomingPlantBlock.BLOOMING));
        tickUntil(w, farHeat, 300, s -> s.get(BloomingPlantBlock.BLOOMING));
        flushLight(w);
        r.check("pyrelily blooms near heat and stays closed away from it", bloomed && !w.getBlockState(farHeat).get(BloomingPlantBlock.BLOOMING),
                String.format(Locale.ROOT, "light near heat %d, away %d; heat detected %s; states %s / %s; magma %s", w.getLightLevel(nearHeat),
                        w.getLightLevel(farHeat), Env.nearHeat(w, nearHeat, 3), w.getBlockState(nearHeat), w.getBlockState(farHeat),
                        w.getBlockState(bo.add(1, -1, 2))));
        BlockPos vo = plot(w, plotOrigin(t, RegionType.STORMSTONE_CAVERNS, reps, -210), 3, 5, Blocks.STONE.getDefaultState(), false, false);
        w.setBlockState(vo, ModPlants.VOLTBLOOM.getDefaultState(), Block.NOTIFY_ALL);
        w.setBlockState(vo.add(1, -1, 0), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        boolean volt = tickUntil(w, vo, 300, s -> s.get(BloomingPlantBlock.BLOOMING));
        r.check("voltbloom reacts to redstone energy", volt, "blooming=" + volt + ", light " + w.getLightLevel(vo));

        // ---- bounded fungal spreading and protection of builds
        BlockPos so = plot(w, plotOrigin(t, RegionType.FUNGAL_EXPANSE, reps, 90), 7, 5, Blocks.DIRT.getDefaultState(), false, false);
        w.setBlockState(so.add(0, 4, 0), Blocks.GLOWSTONE.getDefaultState(), Block.NOTIFY_ALL);
        flushLight(w);
        BlockPos guard = so.add(6, 0, 6);
        w.setBlockState(guard, Blocks.CRAFTING_TABLE.getDefaultState(), Block.NOTIFY_ALL);
        w.setBlockState(so.down().add(-6, 0, 6), Blocks.FARMLAND.getDefaultState(), Block.NOTIFY_ALL);
        w.setBlockState(so.down(), dev.underdeep.registry.ModBlocks.SPOREGLOW_SOIL.getDefaultState(), Block.NOTIFY_ALL);
        w.setBlockState(so, ModPlants.PUFFCAP.getDefaultState(), Block.NOTIFY_ALL);
        long ts = System.nanoTime();
        int caps = spreadRounds(w, so, 7, 0, ModPlants.PUFFCAP, 4000);
        for (int i = 0; i < 4000; i++) {
            for (BlockPos p : BlockPos.iterate(so.add(-7, -1, -7), so.add(7, -1, 7))) {
                BlockState s = w.getBlockState(p);
                if (s.isOf(dev.underdeep.registry.ModBlocks.SPOREGLOW_SOIL) && w.random.nextInt(30) == 0) {
                    s.randomTick(w, p.toImmutable(), w.random);
                }
            }
        }
        double ms = (System.nanoTime() - ts) / 1e6;
        int soil = countAround(w, so.down(), 7, 0, s -> s.isOf(dev.underdeep.registry.ModBlocks.SPOREGLOW_SOIL));
        boolean guarded = !Env.any(w, guard, 2, 1, s -> s.isOf(ModPlants.PUFFCAP)) && w.getBlockState(guard).isOf(Blocks.CRAFTING_TABLE);
        boolean farm = w.getBlockState(so.down().add(-6, 0, 6)).isOf(Blocks.FARMLAND);
        r.check("fungal spreading stays bounded", caps > 1 && caps <= 60, caps + " puffcaps on a 15x15 floor after 4000 rounds (one start cap)");
        r.check("fungal soil creeps slowly but spares farmland", soil > 1 && farm, soil + " fungal soil blocks; farmland intact=" + farm);
        r.check("spreading never reaches player-made blocks", guarded, "no fungi within 2 blocks of the crafting table");
        r.info("spreading test cost", String.format(Locale.ROOT, "%.0f ms for 4000 rounds", ms));

        // ---- crystals regrow in darkness
        BlockPos go = plot(w, plotOrigin(t, RegionType.CRYSTAL_DEEP, reps, 110), 3, 5, Blocks.STONE.getDefaultState(), false, false);
        w.setBlockState(go.up(), ModPlants.BUDDING_GLIMMER.getDefaultState(), Block.NOTIFY_ALL);
        for (int i = 0; i < 3000; i++) {
            w.getBlockState(go.up()).randomTick(w, go.up(), w.random);
        }
        int glimmer = countAround(w, go.up(), 1, 1, s -> s.isOf(ModPlants.GLIMMER_BUD) || s.isOf(ModPlants.GLIMMER_CLUSTER));
        r.check("budding glimmer regrows crystals", glimmer > 0, glimmer + " buds/clusters after 3000 random ticks");
        PLOTS.put("glimmer", go.up());
        // a dark-blooming flower must not be closed by its own light (no flicker)
        BlockPos lily = go.add(-2, 0, -2);
        w.setBlockState(go.add(-2, -1, -2), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        w.setBlockState(lily, ModPlants.PRISM_LILY.getDefaultState(), Block.NOTIFY_ALL);
        flushLight(w);
        boolean opened = tickUntil(w, lily, 300, s -> s.get(BloomingPlantBlock.BLOOMING));
        int flips = 0;
        boolean prev = w.getBlockState(lily).get(BloomingPlantBlock.BLOOMING);
        for (int i = 0; i < 300; i++) {
            if (i % 10 == 0) {
                flushLight(w);
            }
            w.getBlockState(lily).randomTick(w, lily, w.random);
            boolean now = w.getBlockState(lily).get(BloomingPlantBlock.BLOOMING);
            if (now != prev) {
                flips++;
                prev = now;
            }
        }
        r.check("dark-blooming prism lily opens in darkness and stays open", opened && flips == 0 && prev,
                "opened=" + opened + ", state changes afterwards " + flips + ", light " + w.getLightLevel(lily));

        writePlots(dir);
    }

    private static int spreadRounds(ServerWorld w, BlockPos center, int half, int dy, Block block, int rounds) {
        for (int i = 0; i < rounds; i++) {
            List<BlockPos> found = new ArrayList<>();
            for (BlockPos p : BlockPos.iterate(center.add(-half, -1, -half), center.add(half, dy + 3, half))) {
                if (w.getBlockState(p).isOf(block)) {
                    found.add(p.toImmutable());
                }
            }
            for (BlockPos p : found) {
                BlockState s = w.getBlockState(p);
                if (s.hasRandomTicks()) {
                    s.randomTick(w, p, w.random);
                }
            }
        }
        return countAround(w, center, half, dy + 3, s -> s.isOf(block));
    }

    private static int countAround(ServerWorld w, BlockPos c, int half, int height, Predicate<BlockState> p) {
        int n = 0;
        for (BlockPos q : BlockPos.iterate(c.add(-half, -1, -half), c.add(half, height, half))) {
            if (p.test(w.getBlockState(q))) {
                n++;
            }
        }
        return n;
    }

    private static void writePlots(Path dir) {
        StringBuilder b = new StringBuilder();
        PLOTS.forEach((k, v) -> b.append(k).append(' ').append(v.getX()).append(' ').append(v.getY()).append(' ').append(v.getZ()).append('\n'));
        try {
            Files.writeString(dir.resolve("stage2_plots.txt"), b.toString());
        } catch (Exception e) {
            Underdeep.LOGGER.warn("could not write stage2 plots", e);
        }
    }

    /** Reload phase: the grown trees, mature crops, crystals and regional ecology records persisted. */
    static void persistence(ServerWorld w, Path dir, Stage15Checks.Reporter r) {
        r.section("Stage 2 persistence after save and restart");
        Path f = dir.resolve("stage2_plots.txt");
        if (!Files.exists(f)) {
            r.info("stage 2 persistence", "no create-phase plots found");
            return;
        }
        try {
            Map<String, BlockPos> plots = new HashMap<>();
            for (String line : Files.readAllLines(f)) {
                String[] p = line.split(" ");
                plots.put(p[0], new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            }
            int trees = 0, treesOk = 0, crops = 0, cropsOk = 0;
            for (var e : plots.entrySet()) {
                BlockPos p = e.getValue();
                if (e.getKey().startsWith("tree_")) {
                    trees++;
                    w.getChunk(p);
                    if (w.getBlockState(p).isIn(net.minecraft.registry.tag.BlockTags.LOGS)) {
                        treesOk++;
                    }
                } else if (e.getKey().startsWith("crop_")) {
                    crops++;
                    w.getChunk(p);
                    BlockState s = w.getBlockState(p);
                    if (s.getBlock() instanceof CropBlock cb && cb.isMature(s)) {
                        cropsOk++;
                    }
                }
            }
            r.check("grown trees persisted", trees > 0 && treesOk == trees, treesOk + "/" + trees);
            r.check("mature crops persisted", crops > 0 && cropsOk == crops, cropsOk + "/" + crops);
            BlockPos g = plots.get("glimmer");
            if (g != null) {
                w.getChunk(g);
                r.check("regrown crystals persisted", countAround(w, g, 1, 1, s -> s.isOf(ModPlants.GLIMMER_BUD) || s.isOf(ModPlants.GLIMMER_CLUSTER)) > 0, "ok");
            }
            BlockPos h = plots.get("harvest_region");
            if (h != null) {
                long id = ((long) h.getX() << 32) | (h.getY() & 0xffffffffL);
                int n = EcologyState.harvests(w, id, "duskberry");
                r.check("regional ecology state persisted", n > 0, n + " recorded duskberry harvests");
            }
        } catch (Exception e) {
            r.check("stage 2 persistence", false, e.toString());
        }
    }

    /** Vanilla regression: Underdeep spreading never runs in non-Underdeep worlds. */
    static void vanilla(ServerWorld w, Stage15Checks.Reporter r) {
        BlockPos o = w.getSpawnPos().up(40);
        w.getChunk(o);
        for (BlockPos p : BlockPos.iterate(o.add(-4, -1, -4), o.add(4, 3, 4))) {
            w.setBlockState(p, p.getY() == o.getY() - 1 ? Blocks.DIRT.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        w.setBlockState(o, ModPlants.PUFFCAP.getDefaultState(), Block.NOTIFY_ALL);
        int n = spreadRounds(w, o, 4, 0, ModPlants.PUFFCAP, 2000);
        r.check("Underdeep spreading is inactive in vanilla worlds", n == 1 && !Env.spreadingEnabled(w), n + " puffcap(s) after 2000 rounds");
    }
}
