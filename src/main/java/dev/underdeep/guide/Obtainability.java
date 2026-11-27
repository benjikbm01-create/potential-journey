package dev.underdeep.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.underdeep.Underdeep;
import dev.underdeep.block.plant.CrystalGrowerBlock;
import dev.underdeep.block.plant.HarvestablePlantBlock;
import dev.underdeep.block.plant.ResinKnotBlock;
import dev.underdeep.civ.econ.Recipes;
import dev.underdeep.mixin.SmithingTransformRecipeAccessor;
import dev.underdeep.survival.Fishing;
import dev.underdeep.survival.SurvivalContent;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.WildlifeRegistry;
import dev.underdeep.world.region.RegionType;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ComposterBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.BlastingRecipe;
import net.minecraft.recipe.CampfireCookingRecipe;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.SmithingTransformRecipe;
import net.minecraft.recipe.SmokingRecipe;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stage 8 resource obtainability and usefulness audit, computed from the live game: natural blocks seen in generated
 * Underdeep chunks ({@code data/underdeep/guide/natural_blocks.json}, written by the self-test census), simulated
 * block and creature loot, plant harvests, creature products, fishing tables, settlement trade, every crafting,
 * cooking, stonecutting and smithing recipe in the recipe manager, and the Stage 7+ workstation recipes. A
 * fixpoint over these rules (with tool gates such as "needs an iron-tier pickaxe") gives, for every item, whether a
 * survival player can obtain it, how, and what it is used for. The same result feeds the handbook (sent to
 * clients) and the self-test report. Static rules are distinguished from in-world verification in the report.
 */
public final class Obtainability {
    private Obtainability() {
    }

    /** How an item can be obtained. {@code requires} names the tool or station gate, if any. */
    public record Source(String kind, String detail, List<String> regions, String requires) {
    }

    public static final class Entry {
        public final Item item;
        public boolean reachable;
        public int depth = -1;
        public final List<Source> sources = new ArrayList<>();
        public final Set<String> uses = new LinkedHashSet<>();
        public final Set<String> usedIn = new LinkedHashSet<>();

        Entry(Item item) {
            this.item = item;
        }
    }

    /** A production rule: outputs appear once every input slot (any alternative) and the gate (any item) are met. */
    static void exploration(net.minecraft.server.MinecraftServer server, List<Rule> rules) {
        ServerWorld w = server.getOverworld();
        var reg = server.getReloadableRegistries();
        for (var id : reg.getIds(net.minecraft.registry.RegistryKeys.LOOT_TABLE)) {
            if (!id.getNamespace().equals("underdeep") || !id.getPath().startsWith("exploration/")) {
                continue;
            }
            var table = reg.getLootTable(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.LOOT_TABLE, id));
            Set<Item> found = new java.util.LinkedHashSet<>();
            net.minecraft.util.math.random.Random rnd = net.minecraft.util.math.random.Random.create(id.hashCode());
            for (int i = 0; i < 160; i++) {
                var params = new net.minecraft.loot.context.LootContextParameterSet.Builder(w)
                        .add(net.minecraft.loot.context.LootContextParameters.ORIGIN, net.minecraft.util.math.Vec3d.ZERO)
                        .build(net.minecraft.loot.context.LootContextTypes.CHEST);
                table.generateLoot(params, rnd.nextLong()).forEach(st -> found.add(st.getItem()));
            }
            String where = id.getPath().substring("exploration/".length()).replace('_', ' ');
            for (Item it : found) {
                rules.add(new Rule(List.of(it), List.of(), Set.of(), new Source("explore", "found in old places (" + where + ")", List.of(), "")));
            }
        }
        for (var a : dev.underdeep.explore.ExploreContent.ARTIFACTS.values()) {
            Item it = item("underdeep:" + a.id());
            if (it == Items.AIR) {
                continue;
            }
            if (a.unique()) {
                rules.add(new Rule(List.of(it), List.of(), Set.of(), new Source("explore", "rests in one rare, deep place (once per world)", List.of(), "")));
            } else if (a.weight() > 0) {
                rules.add(new Rule(List.of(it), List.of(Set.of(item("underdeep:encrusted_relic"))), Set.of(Items.EMERALD),
                        new Source("trade", "a scholar or cartographer identifies an encrusted relic", List.of(), "emeralds")));
            }
        }
        rules.add(new Rule(List.of(item("underdeep:vault_key")), List.of(), Set.of(), new Source("explore", "taken from a key pedestal in a vault", List.of(), "")));
        rules.add(new Rule(List.of(item("underdeep:surveyors_chart")), List.of(), Set.of(Items.EMERALD),
                new Source("trade", "drawn by cartographers and navigators", List.of(), "emeralds")));
        rules.add(new Rule(List.of(item("underdeep:star_shard")), List.of(), Set.of(), new Source("explore", "falls from starlit ceilings", List.of("starlight_caverns"), "")));
    }

    record Rule(List<Item> outputs, List<Set<Item>> inputs, Set<Item> gate, Source source) {
    }

    private static Map<Item, Entry> cache;
    private static int naturalBlocks;

    public static synchronized Map<Item, Entry> get(MinecraftServer server) {
        if (cache == null) {
            long t0 = System.nanoTime();
            cache = compute(server);
            Underdeep.LOGGER.info("Obtainability audit: {} items in {} ms", cache.size(), (System.nanoTime() - t0) / 1_000_000);
        }
        return cache;
    }

    public static synchronized void invalidate() {
        cache = null;
    }

    public static int naturalBlockCount() {
        return naturalBlocks;
    }

    // ================================================================== natural data

    /** Region id → natural block ids, from the committed census. */
    public static Map<String, List<String>> census() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try (InputStream in = Obtainability.class.getResourceAsStream("/data/underdeep/guide/natural_blocks.json")) {
            if (in == null) {
                return out;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("regions").entrySet()) {
                List<String> l = new ArrayList<>();
                e.getValue().getAsJsonArray().forEach(x -> l.add(x.getAsString()));
                out.put(e.getKey(), l);
            }
        } catch (Exception e) {
            Underdeep.LOGGER.warn("Obtainability: could not read natural_blocks.json", e);
        }
        return out;
    }

    // ================================================================== computation

    static Map<Item, Entry> compute(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        FakePlayer fp = FakePlayer.get(w);
        List<Rule> rules = new ArrayList<>();
        Map<Item, Entry> entries = new LinkedHashMap<>();
        for (Item i : Registries.ITEM) {
            if (i != Items.AIR) {
                entries.put(i, new Entry(i));
            }
        }
        // ---- natural blocks: mining, harvesting, fluids (census = seen in generated chunks)
        Map<Block, Set<String>> natural = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : census().entrySet()) {
            for (String b : e.getValue()) {
                Block block = Registries.BLOCK.get(Identifier.of(b));
                if (block != Blocks.AIR) {
                    natural.computeIfAbsent(block, k -> new TreeSet<>()).add(e.getKey());
                }
            }
        }
        naturalBlocks = natural.size();
        List<ItemStack> tools = tools(w);
        Set<Block> done = new HashSet<>();
        for (Map.Entry<Block, Set<String>> e : new ArrayList<>(natural.entrySet())) {
            blockRules(e.getKey(), List.copyOf(e.getValue()), null, "", w, fp, tools, rules, done);
        }
        // declared worldgen (ecology registry placements) not seen in the census sample: static evidence only
        for (dev.underdeep.ecology.Species sp : dev.underdeep.ecology.EcologyRegistry.all()) {
            if (sp.habitats().isEmpty() || sp.abundance() <= 0) {
                continue;
            }
            List<String> regs = sp.habitats().stream().map(RegionType::id).sorted().toList();
            for (String bid : sp.blockIds()) {
                Block block = Registries.BLOCK.get(Identifier.of(bid.contains(":") ? bid : "underdeep:" + bid));
                if (block != Blocks.AIR && !done.contains(block)) {
                    blockRules(block, regs, null, " (declared worldgen; not seen in the census sample)", w, fp, tools, rules, done);
                }
            }
        }
        // wildlife builds nests and burrows (Stage 3) wherever ground-nesting species live
        for (String n : List.of("underdeep:twig_nest", "underdeep:burrow_mound")) {
            rules.add(new Rule(List.of(item(n)), List.of(), Set.of(), new Source("special", "built by nesting wildlife; break to collect", List.of(), "")));
        }
        // planting: a reachable seed, sapling or cutting grows into its plant, whose harvest then becomes available
        for (Item i : Registries.ITEM) {
            if (i instanceof BlockItem bi && (bi.getBlock() instanceof net.minecraft.block.PlantBlock || bi.getBlock() instanceof net.minecraft.block.Fertilizable)
                    && !(bi.getBlock() instanceof net.minecraft.block.BlockWithEntity)) {
                blockRules(bi.getBlock(), List.of(), i, " (grown from " + Registries.ITEM.getId(i).getPath() + ")", w, fp, tools, rules, new HashSet<>());
            }
        }
        // stripping logs and wood with an axe
        for (Map.Entry<Block, Block> e : dev.underdeep.mixin.AxeItemAccessor.underdeep$stripped().entrySet()) {
            Item from = e.getKey().asItem(), to = e.getValue().asItem();
            if (from != Items.AIR && to != Items.AIR) {
                rules.add(new Rule(List.of(to), List.of(Set.of(from)), axes(), new Source("special", "strip " + Registries.ITEM.getId(from).getPath()
                        + " with an axe", List.of(), "axe")));
            }
        }
        // obsidian: pour water over lava, mine with a diamond-tier pickaxe
        rules.add(new Rule(List.of(Items.OBSIDIAN), List.of(Set.of(Items.WATER_BUCKET), Set.of(Items.LAVA_BUCKET)),
                gateFor(Blocks.OBSIDIAN.getDefaultState(), Set.of(Items.NETHERITE_PICKAXE), tools),
                new Source("special", "pour water onto a lava source, then mine it", List.of(), "diamond-tier pickaxe or better")));
        // ---- creatures
        Set<EntityType<?>> vanillaMobs = new LinkedHashSet<>();
        Map<EntityType<?>, Set<String>> mobRegions = new HashMap<>();
        var biomes = server.getRegistryManager().get(RegistryKeys.BIOME);
        for (RegistryEntry.Reference<Biome> b : biomes.streamEntries().toList()) {
            if (!b.registryKey().getValue().getNamespace().equals(Underdeep.MOD_ID)) {
                continue;
            }
            for (SpawnGroup g : SpawnGroup.values()) {
                for (var se : b.value().getSpawnSettings().getSpawnEntries(g).getEntries()) {
                    vanillaMobs.add(se.type);
                    mobRegions.computeIfAbsent(se.type, k -> new TreeSet<>()).add(b.registryKey().getValue().getPath());
                }
            }
        }
        for (CreatureSpecies sp : WildlifeRegistry.all()) {
            EntityType<?> t = WildlifeRegistry.type(sp.id);
            vanillaMobs.add(t);
            Set<String> rs = new TreeSet<>();
            sp.regions().forEach(r -> rs.add(r.id()));
            mobRegions.put(t, rs);
            String prod = sp.product();
            if (prod.startsWith("shed:")) {
                rules.add(new Rule(List.of(item("underdeep:" + prod.substring(5))), List.of(), Set.of(),
                        new Source("product", "shed by " + sp.id + " (wild or tamed)", List.copyOf(rs), "")));
            } else if (prod.startsWith("shear:")) {
                rules.add(new Rule(List.of(item("underdeep:" + prod.substring(6))), List.of(), Set.of(Items.SHEARS),
                        new Source("product", "shear a " + sp.id, List.copyOf(rs), "shears")));
            } else if (prod.equals("milk")) {
                rules.add(new Rule(List.of(Items.MILK_BUCKET), List.of(), Set.of(Items.BUCKET),
                        new Source("product", "milk a " + sp.id, List.copyOf(rs), "bucket")));
            }
        }
        for (EntityType<?> t : vanillaMobs) {
            List<String> regs = List.copyOf(mobRegions.getOrDefault(t, Set.of()));
            String name = Registries.ENTITY_TYPE.getId(t).toString();
            Set<Item> got = new LinkedHashSet<>();
            Entity ent;
            try {
                ent = t.create(w);
            } catch (Exception ex) {
                ent = null;
            }
            if (ent instanceof LivingEntity le) {
                RegistryKey<LootTable> key = le.getLootTable();
                LootTable table = server.getReloadableRegistries().getLootTable(key);
                for (int r = 0; r < 40; r++) {
                    LootContextParameterSet ps = new LootContextParameterSet.Builder(w).add(LootContextParameters.THIS_ENTITY, le)
                            .add(LootContextParameters.ORIGIN, Vec3d.ZERO).add(LootContextParameters.DAMAGE_SOURCE, w.getDamageSources().playerAttack(fp))
                            .addOptional(LootContextParameters.ATTACKING_ENTITY, fp).addOptional(LootContextParameters.DIRECT_ATTACKING_ENTITY, fp)
                            .add(LootContextParameters.LAST_DAMAGE_PLAYER, fp).luck(0).build(LootContextTypes.ENTITY);
                    try {
                        table.generateLoot(ps).forEach(s -> got.add(s.getItem()));
                    } catch (Exception ex) {
                        break;
                    }
                }
                ent.discard();
            }
            for (Item i : got) {
                rules.add(new Rule(List.of(i), List.of(), Set.of(), new Source("creature", "dropped by " + name, regs, "")));
            }
            if (t == EntityType.SHEEP) {
                rules.add(new Rule(List.of(Items.WHITE_WOOL), List.of(), Set.of(Items.SHEARS), new Source("product", "shear a sheep", regs, "shears")));
            } else if (t == EntityType.COW || t == EntityType.GOAT || t == EntityType.MOOSHROOM) {
                rules.add(new Rule(List.of(Items.MILK_BUCKET), List.of(), Set.of(Items.BUCKET), new Source("product", "milk a " + name, regs, "bucket")));
            } else if (t == EntityType.CHICKEN) {
                rules.add(new Rule(List.of(Items.EGG), List.of(), Set.of(), new Source("product", "laid by chickens", regs, "")));
            }
        }
        // ground nests of wildlife (Stage 3) give creature eggs
        rules.add(new Rule(List.of(item("underdeep:creature_egg")), List.of(), Set.of(),
                new Source("product", "collected from the nests of ground-nesting wildlife", List.of(), "")));
        // ---- fishing
        for (RegionType rt : RegionType.values()) {
            for (String f : Fishing.tableFor(rt)) {
                Item fi = item(f.contains(":") ? f : "underdeep:" + f);
                rules.add(new Rule(List.of(fi), List.of(), Set.of(Items.FISHING_ROD), new Source("fish", "fishing", List.of(rt.id()), "fishing rod")));
                rules.add(new Rule(List.of(fi), List.of(Set.of(item("underdeep:fishing_bait"))), Set.of(item("underdeep:fish_trap")),
                        new Source("trap", "fish trap with bait", List.of(rt.id()), "fish trap")));
            }
        }
        LootTable fishing = server.getReloadableRegistries().getLootTable(LootTables.FISHING_GAMEPLAY);
        Set<Item> fished = new LinkedHashSet<>();
        for (int r = 0; r < 300; r++) {
            LootContextParameterSet ps = new LootContextParameterSet.Builder(w).add(LootContextParameters.ORIGIN, Vec3d.ZERO)
                    .add(LootContextParameters.TOOL, new ItemStack(Items.FISHING_ROD)).luck(0).build(LootContextTypes.FISHING);
            try {
                fishing.generateLoot(ps).forEach(s -> fished.add(s.getItem()));
            } catch (Exception ex) {
                break;
            }
        }
        for (Item i : fished) {
            rules.add(new Rule(List.of(i), List.of(), Set.of(Items.FISHING_ROD), new Source("fish", "fishing (ordinary catch)", List.of(), "fishing rod")));
        }
        // ---- compost bin
        rules.add(new Rule(List.of(item("underdeep:compost")), List.of(compostables()), Set.of(item("underdeep:compost_bin")),
                new Source("station", "compost bin", List.of(), "compost bin")));
        rules.add(new Rule(List.of(Items.BONE_MEAL), List.of(compostables()), Set.of(Items.COMPOSTER), new Source("station", "composter", List.of(), "composter")));
        // ---- recipes from the recipe manager
        Map<Item, Set<String>> uses = new HashMap<>();
        for (RecipeEntry<?> re : server.getRecipeManager().values()) {
            Recipe<?> r = re.value();
            ItemStack out;
            try {
                out = r.getResult(server.getRegistryManager());
            } catch (Exception ex) {
                continue;
            }
            if (out.isEmpty()) {
                continue;
            }
            List<Ingredient> ings = new ArrayList<>();
            Item gate;
            String kind;
            if (r instanceof SmithingTransformRecipe st) {
                SmithingTransformRecipeAccessor a = (SmithingTransformRecipeAccessor) st;
                ings.add(a.underdeep$template());
                ings.add(a.underdeep$base());
                ings.add(a.underdeep$addition());
                gate = Items.SMITHING_TABLE;
                kind = "smithing";
            } else {
                ings.addAll(r.getIngredients());
                if (r instanceof CraftingRecipe) {
                    gate = Items.CRAFTING_TABLE;
                    kind = "crafting";
                } else if (r instanceof BlastingRecipe) {
                    gate = Items.BLAST_FURNACE;
                    kind = "blasting";
                } else if (r instanceof SmokingRecipe) {
                    gate = Items.SMOKER;
                    kind = "smoking";
                } else if (r instanceof CampfireCookingRecipe) {
                    gate = Items.CAMPFIRE;
                    kind = "campfire";
                } else if (r instanceof SmeltingRecipe) {
                    gate = Items.FURNACE;
                    kind = "smelting";
                } else if (r instanceof StonecuttingRecipe) {
                    gate = Items.STONECUTTER;
                    kind = "stonecutting";
                } else {
                    continue;
                }
            }
            List<Set<Item>> inputs = new ArrayList<>();
            for (Ingredient ing : ings) {
                if (ing.isEmpty()) {
                    continue;
                }
                Set<Item> alts = new LinkedHashSet<>();
                for (ItemStack s : ing.getMatchingStacks()) {
                    alts.add(s.getItem());
                }
                if (!alts.isEmpty()) {
                    inputs.add(alts);
                    for (Item a : alts) {
                        uses.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(kind + ":" + Registries.ITEM.getId(out.getItem()));
                    }
                }
            }
            rules.add(new Rule(List.of(out.getItem()), inputs, kind.equals("crafting") && inputs.size() <= 4 ? Set.of() : Set.of(gate),
                    new Source(kind, re.id().toString(), List.of(), kind.equals("crafting") ? (inputs.size() <= 4 ? "" : "crafting table")
                            : Registries.ITEM.getId(gate).getPath().replace('_', ' '))));
        }
        // ---- workstation recipes (Stage 7+)
        for (SurvivalContent.Recipe r : SurvivalContent.recipes()) {
            List<Set<Item>> inputs = new ArrayList<>();
            for (SurvivalContent.Ingredient ing : r.ingredients()) {
                Set<Item> alts = SurvivalContent.alternatives(ing);
                inputs.add(alts);
                for (Item a : alts) {
                    uses.computeIfAbsent(a, k -> new LinkedHashSet<>()).add("station:" + r.station().id + ":" + r.result());
                }
            }
            if (r.containerItem() != null) {
                inputs.add(Set.of(r.containerItem()));
            }
            Set<Item> gate = new LinkedHashSet<>();
            gate.add(SurvivalContent.stationItem(r.station()));
            List<Set<Item>> in2 = new ArrayList<>(inputs);
            if (r.station().heat) {
                in2.add(Set.of(Items.CAMPFIRE, Items.FURNACE, Items.LAVA_BUCKET, Items.MAGMA_BLOCK));
            }
            rules.add(new Rule(List.of(r.resultItem()), in2, gate, new Source("station", SurvivalContent.stationName(r.station()) + ": " + r.id(), List.of(),
                    SurvivalContent.stationName(r.station()))));
        }
        // ---- settlement trade (goods that settlements gather or make)
        for (Recipes.Recipe er : Recipes.all()) {
            for (String o : er.outputs().keySet()) {
                if (o.startsWith("@")) {
                    continue;
                }
                Item it = item(o);
                if (it != Items.AIR) {
                    rules.add(new Rule(List.of(it), List.of(), Set.of(Items.EMERALD), new Source("trade", "bought from settlements (" + String.join(", ",
                            er.professions().subList(0, Math.min(2, er.professions().size()))) + ")", List.of(), "emeralds")));
                }
            }
        }
        // ---- Stage 9: old places (site loot tables sampled), unique pedestals, keys, identification, charts, encounters
        exploration(server, rules);
        // ---- fixpoint
        Set<Item> reach = new HashSet<>();
        Map<Item, Integer> depth = new HashMap<>();
        boolean changed = true;
        int iter = 0;
        boolean[] fired = new boolean[rules.size()];
        while (changed && iter < 64) {
            changed = false;
            iter++;
            Set<Item> snapshot = new HashSet<>(reach);
            for (int ri = 0; ri < rules.size(); ri++) {
                Rule rule = rules.get(ri);
                if (fired[ri]) {
                    continue;
                }
                if (!rule.gate().isEmpty() && rule.gate().stream().noneMatch(snapshot::contains)) {
                    continue;
                }
                boolean ok = true;
                int d = 0;
                for (Set<Item> in : rule.inputs()) {
                    int best = Integer.MAX_VALUE;
                    for (Item a : in) {
                        if (snapshot.contains(a)) {
                            best = Math.min(best, depth.get(a));
                        }
                    }
                    if (best == Integer.MAX_VALUE) {
                        ok = false;
                        break;
                    }
                    d = Math.max(d, best + 1);
                }
                if (!ok) {
                    continue;
                }
                fired[ri] = true;
                for (Item o : rule.outputs()) {
                    if (o == Items.AIR) {
                        continue;
                    }
                    Entry e = entries.get(o);
                    if (e == null) {
                        continue;
                    }
                    e.sources.add(rule.source());
                    if (reach.add(o)) {
                        depth.put(o, d);
                        changed = true;
                    }
                }
            }
        }
        for (Entry e : entries.values()) {
            e.reachable = reach.contains(e.item);
            e.depth = depth.getOrDefault(e.item, -1);
            e.usedIn.addAll(uses.getOrDefault(e.item, Set.of()));
            usesOf(e);
        }
        return entries;
    }

    /** Rules for breaking/harvesting a block (all relevant states, representative tools). */
    static void blockRules(Block block, List<String> regions, Item planted, String note, ServerWorld w, FakePlayer fp, List<ItemStack> tools,
                           List<Rule> rules, Set<Block> done) {
        if (!done.add(block)) {
            return;
        }
        List<Set<Item>> in = planted == null ? List.of() : List.of(Set.of(planted));
        String bname = Registries.BLOCK.getId(block).toString();
        if (block == Blocks.WATER) {
            rules.add(new Rule(List.of(Items.WATER_BUCKET), in, Set.of(Items.BUCKET), new Source("fluid", "fill a bucket at water", regions, "bucket")));
            return;
        }
        if (block == Blocks.LAVA) {
            rules.add(new Rule(List.of(Items.LAVA_BUCKET), in, Set.of(Items.BUCKET), new Source("fluid", "fill a bucket at lava", regions, "bucket")));
            return;
        }
        String kind = planted == null ? "mine" : "grow";
        if (block instanceof HarvestablePlantBlock hp) {
            rules.add(new Rule(List.of(hp.produce()), in, Set.of(), new Source(planted == null ? "harvest" : "grow", "pick a mature " + bname + note, regions, "")));
        }
        if (block instanceof ResinKnotBlock rk) {
            rules.add(new Rule(List.of(rk.resin()), in, Set.of(), new Source("harvest", "collect from a ripe " + bname + note, regions, "")));
        }
        if (block instanceof CrystalGrowerBlock cg) {
            for (Block st : cg.stages()) {
                blockRules(st, regions, planted, " (grows on " + bname + ")" + note, w, fp, tools, rules, done);
            }
        }
        for (BlockState state : statesOf(block)) {
            Map<Item, Set<Item>> byItem = new LinkedHashMap<>();
            for (ItemStack tool : tools) {
                int rolls = Registries.BLOCK.getId(block).getNamespace().equals("minecraft") ? 8 : 60;
                for (int r = 0; r < rolls; r++) {
                    List<ItemStack> drops;
                    try {
                        drops = Block.getDroppedStacks(state, w, BlockPos.ORIGIN, null, fp, tool);
                    } catch (Exception ex) {
                        break;
                    }
                    for (ItemStack d : drops) {
                        if (!d.isEmpty()) {
                            byItem.computeIfAbsent(d.getItem(), k -> new LinkedHashSet<>()).add(tool.getItem());
                        }
                    }
                }
            }
            for (Map.Entry<Item, Set<Item>> e : byItem.entrySet()) {
                Set<Item> gate = gateFor(state, e.getValue(), tools);
                String req = gate.isEmpty() ? "" : describeGate(state, e.getValue());
                rules.add(new Rule(List.of(e.getKey()), in, gate, new Source(kind, "break " + bname + note, regions, req)));
            }
        }
    }

    static Set<Item> axes() {
        Set<Item> s = new LinkedHashSet<>();
        for (Item i : Registries.ITEM) {
            if (i instanceof net.minecraft.item.AxeItem) {
                s.add(i);
            }
        }
        return s;
    }

    static Item item(String id) {
        return Registries.ITEM.get(Identifier.of(id));
    }

    static Set<Item> compostables() {
        Set<Item> s = new LinkedHashSet<>();
        ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.keySet().forEach(i -> s.add(i.asItem()));
        return s;
    }

    /** States worth simulating: the default and, for growing plants, the fully grown state. */
    static List<BlockState> statesOf(Block block) {
        List<BlockState> out = new ArrayList<>();
        BlockState d = block.getDefaultState();
        if (Registries.BLOCK.getId(block).getNamespace().equals(Underdeep.MOD_ID) && block.getStateManager().getStates().size() <= 16) {
            return block.getStateManager().getStates();
        }
        out.add(d);
        for (Property<?> p : d.getProperties()) {
            if (p instanceof IntProperty ip && (p.getName().equals("age") || p.getName().equals("level"))) {
                int max = ip.getValues().stream().max(Integer::compare).orElse(0);
                out.add(d.with(ip, max));
            }
        }
        return out;
    }

    /** Representative tools: bare hand, top-tier pickaxe/axe/shovel/hoe, shears, a silk-touch pickaxe. */
    static List<ItemStack> tools(ServerWorld w) {
        List<ItemStack> t = new ArrayList<>(List.of(ItemStack.EMPTY, new ItemStack(Items.NETHERITE_PICKAXE), new ItemStack(Items.NETHERITE_AXE),
                new ItemStack(Items.NETHERITE_SHOVEL), new ItemStack(Items.NETHERITE_HOE), new ItemStack(Items.SHEARS)));
        ItemStack silk = new ItemStack(Items.NETHERITE_PICKAXE);
        w.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT).getOptional(Enchantments.SILK_TOUCH).ifPresent(e -> silk.addEnchantment(e, 1));
        t.add(silk);
        return t;
    }

    /** The items that would harvest this drop: any correct tool of the needed tier (or shears / silk touch). */
    static Set<Item> gateFor(BlockState state, Set<Item> worked, List<ItemStack> tools) {
        if (worked.contains(Items.AIR)) {
            return Set.of();
        }
        Set<Item> gate = new LinkedHashSet<>();
        boolean onlySilk = worked.size() == 1 && worked.iterator().next() == Items.NETHERITE_PICKAXE && !tools.get(1).isSuitableFor(state)
                && state.isToolRequired();
        for (Item i : Registries.ITEM) {
            ItemStack s = new ItemStack(i);
            if (worked.contains(Items.SHEARS) && i == Items.SHEARS) {
                gate.add(i);
            } else if (i instanceof ToolItem && (state.isToolRequired() ? s.isSuitableFor(state) : worked.contains(classOf(i)))) {
                gate.add(i);
            }
        }
        if (onlySilk || (!worked.contains(Items.NETHERITE_PICKAXE) && worked.size() == 1 && worked.iterator().next() == Items.NETHERITE_PICKAXE)) {
            gate.add(Items.ENCHANTING_TABLE);
        }
        return gate;
    }

    static Item classOf(Item tool) {
        String p = Registries.ITEM.getId(tool).getPath();
        if (p.endsWith("pickaxe")) {
            return Items.NETHERITE_PICKAXE;
        }
        if (p.endsWith("_axe")) {
            return Items.NETHERITE_AXE;
        }
        if (p.endsWith("shovel")) {
            return Items.NETHERITE_SHOVEL;
        }
        if (p.endsWith("hoe")) {
            return Items.NETHERITE_HOE;
        }
        return Items.AIR;
    }

    static String describeGate(BlockState state, Set<Item> worked) {
        if (worked.contains(Items.SHEARS) && worked.size() == 1) {
            return "shears";
        }
        if (!state.isToolRequired()) {
            return worked.contains(Items.NETHERITE_PICKAXE) ? "pickaxe" : worked.contains(Items.NETHERITE_AXE) ? "axe"
                    : worked.contains(Items.NETHERITE_SHOVEL) ? "shovel" : "tool";
        }
        for (String[] tier : new String[][]{{"wooden", "any pickaxe"}, {"stone", "stone pickaxe or better"}, {"iron", "iron-tier pickaxe or better"},
                {"diamond", "diamond-tier pickaxe or better"}}) {
            if (new ItemStack(item("minecraft:" + tier[0] + "_pickaxe")).isSuitableFor(state)) {
                return tier[1];
            }
        }
        return "a top-tier pickaxe";
    }

    /** Non-recipe uses: food, building, fuel, compost, equipment, trade, creature feed. */
    static void usesOf(Entry e) {
        Item i = e.item;
        ItemStack s = new ItemStack(i);
        if (s.contains(DataComponentTypes.FOOD)) {
            e.uses.add("food");
        }
        if (i instanceof BlockItem) {
            e.uses.add("placeable block");
        }
        Integer fuel = FuelRegistry.INSTANCE.get(i);
        if (fuel != null && fuel > 0) {
            e.uses.add("fuel");
        }
        if (ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.getFloat(i) > 0) {
            e.uses.add("compost");
        }
        if (i instanceof ToolItem || i instanceof ArmorItem || s.isDamageable()) {
            e.uses.add("equipment");
        }
        if (dev.underdeep.civ.econ.Goods.of(i) != null) {
            e.uses.add("trade good");
        }
        for (CreatureSpecies sp : WildlifeRegistry.all()) {
            if (dev.underdeep.wildlife.ai.CreatureGoals.isFood(sp, s)) {
                e.uses.add("animal feed");
                break;
            }
        }
        if (!e.usedIn.isEmpty()) {
            e.uses.add("ingredient");
        }
        e.uses.addAll(Uses.extra(i));
    }

    /** All entries, convenience for reports. */
    public static Collection<Entry> all(MinecraftServer server) {
        return get(server).values();
    }
}
