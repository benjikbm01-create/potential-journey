package dev.underdeep.debug;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.underdeep.Underdeep;
import dev.underdeep.guide.Obtainability;
import dev.underdeep.guide.Sensitivity;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stage 8 checks: the natural-resource census (blocks actually present in generated chunks of every ecosystem) and the
 * obtainability / usefulness audit over every registered item. The census is in-world evidence; the audit combines it
 * with simulated loot and the live recipe graph (static reachability). Both are written to the self-test directory.
 */
final class Stage8Checks {
    private Stage8Checks() {
    }

    /** Vanilla materials a survival player needs underground (brief §9). */
    static final List<String> VANILLA_ESSENTIALS = List.of("cobblestone", "coal", "charcoal", "iron_ingot", "copper_ingot", "gold_ingot", "redstone",
            "lapis_lazuli", "diamond", "emerald", "sand", "gravel", "clay_ball", "glass", "leather", "string", "feather", "bone", "white_wool",
            "paper", "sugar", "water_bucket", "obsidian", "book", "enchanting_table", "brewing_stand", "blaze_rod", "blaze_powder", "nether_wart",
            "glowstone_dust", "ender_pearl", "netherite_ingot", "gunpowder", "flint", "slime_ball", "honey_bottle", "ink_sac", "torch",
            "crafting_table", "furnace", "anvil", "smithing_table", "bucket", "shears", "fishing_rod", "bow", "arrow", "shield", "bread",
            "milk_bucket", "egg", "quartz", "amethyst_shard", "glass_bottle", "stick", "iron_pickaxe", "diamond_pickaxe", "netherite_pickaxe",
            "lead", "name_tag", "ender_eye", "magma_cream", "fermented_spider_eye", "spider_eye");

    /**
     * Vanilla items that are deliberately not part of the underground game (no surface, no Nether, no ocean
     * structures): reported for transparency; Underdeep equipment covers their roles (see docs/RESOURCES.md).
     */
    static final List<String> VANILLA_NOT_IN_UNDERDEEP = List.of("saddle", "elytra", "turtle_helmet", "turtle_scute", "trident",
            "nautilus_shell", "heart_of_the_sea", "experience_bottle", "shulker_shell", "phantom_membrane", "dragon_breath", "chorus_fruit");

    static void run(MinecraftServer server, ServerWorld w, UnderdeepTerrain t, Map<RegionType, int[]> reps, Path dir, Stage15Checks.Reporter r,
                    boolean census) {
        r.section("Stage 8 resource census and obtainability audit (census = generated chunks; audit = simulated loot + live recipe graph)");
        if (census || Obtainability.census().isEmpty()) {
            census(w, t, reps, dir, r);
        }
        long t0 = System.nanoTime();
        Obtainability.invalidate();
        var all = Obtainability.get(server);
        r.info("audit cost", String.format(Locale.ROOT, "%d items, %d natural blocks, %.1f s", all.size(), Obtainability.naturalBlockCount(),
                (System.nanoTime() - t0) / 1e9));
        List<String> modGaps = new ArrayList<>(), vanillaGaps = new ArrayList<>(), noUse = new ArrayList<>(), deferred = new ArrayList<>();
        int mod = 0, modOk = 0, natural = 0;
        JsonArray rows = new JsonArray();
        StringBuilder md = new StringBuilder("# Obtainability audit\n\n| item | reachable | depth | first sources | uses |\n|---|---|---|---|---|\n");
        for (Obtainability.Entry e : all.values()) {
            Identifier id = Registries.ITEM.getId(e.item);
            boolean ours = id.getNamespace().equals(Underdeep.MOD_ID);
            boolean essential = id.getNamespace().equals("minecraft") && VANILLA_ESSENTIALS.contains(id.getPath());
            if (!ours && !essential) {
                continue;
            }
            Sensitivity.Kind kind = Sensitivity.kind(e.item);
            if (kind == Sensitivity.Kind.TECHNICAL) {
                continue;
            }
            if (kind == Sensitivity.Kind.DEFERRED) {
                deferred.add(id.getPath());
                continue;
            }
            if (ours) {
                mod++;
                if (e.reachable) {
                    modOk++;
                } else {
                    modGaps.add(id.getPath());
                }
                if (e.uses.isEmpty()) {
                    noUse.add(id.getPath());
                }
            } else if (!e.reachable) {
                vanillaGaps.add(id.getPath());
            }
            if (e.sources.stream().anyMatch(s -> s.kind().equals("mine") || s.kind().equals("harvest"))) {
                natural++;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", id.toString());
            o.addProperty("reachable", e.reachable);
            o.addProperty("depth", e.depth);
            JsonArray src = new JsonArray();
            e.sources.stream().limit(6).forEach(s -> src.add(s.kind() + ": " + s.detail() + (s.requires().isEmpty() ? "" : " [" + s.requires() + "]")
                    + (s.regions().isEmpty() ? "" : " " + s.regions())));
            o.add("sources", src);
            JsonArray us = new JsonArray();
            e.uses.forEach(us::add);
            o.add("uses", us);
            rows.add(o);
            md.append("| ").append(id).append(" | ").append(e.reachable ? "yes" : "**NO**").append(" | ").append(e.depth).append(" | ")
                    .append(e.sources.isEmpty() ? "-" : e.sources.get(0).kind() + ": " + e.sources.get(0).detail()).append(" | ")
                    .append(String.join(", ", e.uses)).append(" |\n");
        }
        try {
            Files.writeString(dir.resolve("obtainability.json"), new GsonBuilder().setPrettyPrinting().create().toJson(rows));
            Files.writeString(dir.resolve("obtainability.md"), md.toString());
        } catch (Exception ex) {
            Underdeep.LOGGER.warn("could not write obtainability report", ex);
        }
        r.check("every ordinary Underdeep item is obtainable in survival (static reachability over in-world census sources)", modGaps.isEmpty(),
                modOk + "/" + mod + " reachable; gaps: " + modGaps);
        r.check("essential vanilla materials are obtainable underground", vanillaGaps.isEmpty(), VANILLA_ESSENTIALS.size() + " checked; gaps: "
                + vanillaGaps);
        r.check("every ordinary Underdeep item has at least one use", noUse.isEmpty(), "without use: " + noUse);
        r.info("items with a natural (mined or harvested) source", String.valueOf(natural));
        r.info("intentionally deferred items (private list)", String.valueOf(deferred.size()));
        List<String> declaredOnly = new ArrayList<>();
        for (Obtainability.Entry e : all.values()) {
            boolean natSrc = e.sources.stream().anyMatch(s -> s.kind().equals("mine") || s.kind().equals("harvest"));
            boolean onlyDeclared = natSrc && e.sources.stream().filter(s -> s.kind().equals("mine") || s.kind().equals("harvest"))
                    .allMatch(s -> s.detail().contains("declared"));
            boolean other = e.sources.stream().anyMatch(s -> !s.kind().equals("mine") && !s.kind().equals("harvest") && !s.kind().equals("grow"));
            if (onlyDeclared && !other && Registries.ITEM.getId(e.item).getNamespace().equals(Underdeep.MOD_ID)) {
                declaredOnly.add(Registries.ITEM.getId(e.item).getPath());
            }
        }
        r.info("natural sources only declared by worldgen (not seen in the 7x7-chunk census sample)", declaredOnly.toString());
        List<String> absent = new ArrayList<>();
        for (String v : VANILLA_NOT_IN_UNDERDEEP) {
            Obtainability.Entry e = all.get(item("minecraft:" + v));
            if (e != null && !e.reachable) {
                absent.add(v);
            }
        }
        r.info("vanilla items outside the underground premise (not obtainable by design)", absent.toString());
        try {
            Stage8Equipment.run(server, w, r);
        } catch (RuntimeException ex) {
            Underdeep.LOGGER.error("Stage 8 equipment checks crashed", ex);
            r.check("stage 8 equipment/handbook/advancement checks completed without exceptions", false, ex.toString());
        }
    }

    /** Counts every block in 7x7 generated chunks around each ecosystem's representative site, full height. */
    static void census(ServerWorld w, UnderdeepTerrain t, Map<RegionType, int[]> reps, Path dir, Stage15Checks.Reporter r) {
        long t0 = System.nanoTime();
        Map<String, Map<String, Integer>> byRegion = new TreeMap<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (RegionType type : RegionType.values()) {
            int[] p = reps.get(type);
            if (p == null) {
                continue;
            }
            int[] site = Stage15Checks.siteFor(t, type, p);
            Map<String, Integer> counts = new TreeMap<>();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    Chunk c = w.getChunk((site[0] >> 4) + dx, (site[1] >> 4) + dz, ChunkStatus.FULL, true);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int bx = c.getPos().getStartX() + x, bz = c.getPos().getStartZ() + z;
                            dev.underdeep.world.terrain.TerrainColumn col = t.column(bx, bz);
                            for (int y = w.getBottomY(); y < w.getTopY(); y++) {
                                if (col.regionAt(y) != type) {
                                    continue;
                                }
                                m.set(bx, y, bz);
                                BlockState s = c.getBlockState(m);
                                if (!s.isAir()) {
                                    counts.merge(Registries.BLOCK.getId(s.getBlock()).toString(), 1, Integer::sum);
                                }
                            }
                        }
                    }
                }
            }
            byRegion.put(type.id(), counts);
        }
        JsonObject out = new JsonObject();
        out.addProperty("note", "Blocks present in generated chunks (7x7 chunks per ecosystem); written by the Stage 8 self-test census.");
        JsonObject regs = new JsonObject();
        JsonObject cnt = new JsonObject();
        TreeSet<String> allBlocks = new TreeSet<>();
        byRegion.forEach((k, v) -> {
            JsonArray a = new JsonArray();
            v.keySet().forEach(a::add);
            allBlocks.addAll(v.keySet());
            regs.add(k, a);
            JsonObject c = new JsonObject();
            v.forEach(c::addProperty);
            cnt.add(k, c);
        });
        out.add("regions", regs);
        try {
            Files.writeString(dir.resolve("natural_blocks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(out));
            JsonObject full = new JsonObject();
            full.add("counts", cnt);
            Files.writeString(dir.resolve("census_counts.json"), new GsonBuilder().setPrettyPrinting().create().toJson(full));
        } catch (Exception ex) {
            Underdeep.LOGGER.warn("could not write census", ex);
        }
        r.info("resource census", String.format(Locale.ROOT, "%d distinct blocks in %d ecosystems, %.1f s", allBlocks.size(), byRegion.size(),
                (System.nanoTime() - t0) / 1e9));
    }

    static Block block(String id) {
        return Registries.BLOCK.get(Identifier.of(id));
    }

    static Item item(String id) {
        return Registries.ITEM.get(Identifier.of(id));
    }
}
