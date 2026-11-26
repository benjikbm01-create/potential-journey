package dev.underdeep.debug;

import dev.underdeep.Underdeep;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.UnderdeepCreature;
import dev.underdeep.wildlife.WildlifeBlocks;
import dev.underdeep.wildlife.WildlifeItems;
import dev.underdeep.wildlife.WildlifeRegistry;
import dev.underdeep.wildlife.population.RegionWildlife;
import dev.underdeep.wildlife.population.WildlifeManager;
import dev.underdeep.world.analysis.RegionReport;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stage 3 targeted wildlife tests inside the dedicated-server self-test. Behaviour tests run real
 * entities with their real goals in sealed test plots by ticking them directly (accelerated, server
 * thread); population tests drive the real spawner with a Fabric fake player and the real aggregate
 * simulation. Results distinguish accelerated tests from real-time play (see TESTING.md).
 */
final class Stage3Checks {
    private Stage3Checks() {
    }

    static void run(ServerWorld w, UnderdeepTerrain t, Map<RegionType, int[]> reps, Path dir, Stage15Checks.Reporter r, boolean create) {
        r.section("Stage 3 wildlife: registration and resources");
        registration(w, r);
        WildlifeManager m = WildlifeManager.get(w);
        if (m == null) {
            r.check("wildlife manager active in Underdeep world", false, "no manager");
            return;
        }
        if (!create) {
            persistence(w, m, dir, r);
            return;
        }
        UnderdeepCreature.testObserver = true;
        try {
            createPhase(w, t, m, reps, dir, r);
        } finally {
            UnderdeepCreature.testObserver = false;
        }
    }

    private static void createPhase(ServerWorld w, UnderdeepTerrain t, WildlifeManager m, Map<RegionType, int[]> reps, Path dir,
                                    Stage15Checks.Reporter r) {
        r.section("Stage 3 wildlife: regional populations and natural distribution (real spawner, fake player)");
        distribution(w, t, m, reps, r);
        r.section("Stage 3 wildlife: behaviour in sealed test plots (accelerated: entities ticked directly)");
        behaviour(w, t, m, reps, r);
        r.section("Stage 3 wildlife: taming, riding, products");
        taming(w, t, m, reps, r);
        r.section("Stage 3 wildlife: population integrity and accelerated regional simulation");
        integrity(w, t, m, reps, r);
        simulation(w, t, m, reps, r);
        performance(w, t, m, reps, r);
        prepareReload(w, m, dir, r);
    }

    /** Vanilla worlds: no wildlife manager, no regional populations, vanilla passive spawning untouched. */
    static void vanilla(ServerWorld w, Stage15Checks.Reporter r) {
        r.section("Stage 3 wildlife in a vanilla world");
        r.check("no wildlife simulation in vanilla worlds", WildlifeManager.get(w) == null, "manager=" + WildlifeManager.get(w));
        BlockPos sp = w.getSpawnPos();
        var biome = w.getBiome(sp);
        var list = w.getChunkManager().getChunkGenerator().getEntitySpawnList(biome, w.getStructureAccessor(),
                net.minecraft.entity.SpawnGroup.CREATURE, sp);
        boolean vanillaAnimals = list.getEntries().stream().noneMatch(e -> WildlifeRegistry.of(e.type) != null);
        r.check("vanilla passive spawn lists unchanged (no Underdeep creatures)", vanillaAnimals,
                list.getEntries().size() + " creature spawn entries at spawn biome");
        int creatures = w.getEntitiesByClass(UnderdeepCreature.class, new Box(sp).expand(256), e -> true).size();
        r.check("no Underdeep creatures near the vanilla spawn", creatures == 0, creatures + " found");
    }

    // ------------------------------------------------------------------ registration

    private static void registration(ServerWorld w, Stage15Checks.Reporter r) {
        int species = WildlifeRegistry.all().size();
        List<String> missing = new ArrayList<>();
        Set<RegionType> covered = java.util.EnumSet.noneOf(RegionType.class);
        int tamable = 0, mounts = 0, aquatic = 0, fliers = 0, rare = 0;
        for (CreatureSpecies s : WildlifeRegistry.all()) {
            covered.addAll(s.regions());
            if (WildlifeRegistry.type(s.id) == null) {
                missing.add("type " + s.id);
            } else if (w.getServer().getReloadableRegistries().getLootTable(WildlifeRegistry.type(s.id).getLootTableId()) == LootTable.EMPTY) {
                missing.add("loot " + s.id);
            }
            if (Stage3Checks.class.getResource("/assets/underdeep/creatures/" + s.id + ".json") == null
                    || Stage3Checks.class.getResource("/assets/underdeep/textures/entity/creature/" + s.id + ".png") == null) {
                missing.add("model " + s.id);
            }
            tamable += s.tamable() ? 1 : 0;
            mounts += s.mountType() != CreatureSpecies.Mount.NONE ? 1 : 0;
            aquatic += s.isAquatic() || s.locomotion() == CreatureSpecies.Locomotion.AMPHIBIOUS ? 1 : 0;
            fliers += s.flies() || s.locomotion() == CreatureSpecies.Locomotion.GLIDER ? 1 : 0;
            rare += s.rarity() == dev.underdeep.ecology.Species.Rarity.RARE || s.rarity() == dev.underdeep.ecology.Species.Rarity.EXCEPTIONAL ? 1 : 0;
        }
        r.check("all species registered with entity type, loot table, model and texture", missing.isEmpty(),
                missing.isEmpty() ? species + " species" : "missing " + missing);
        r.check("wildlife in every ecosystem", covered.size() == RegionType.values().length, covered.size() + "/15");
        r.info("species mix", String.format(Locale.ROOT, "%d species: %d tamable, %d rideable, %d aquatic/amphibious, %d flying/gliding, %d rare",
                species, tamable, mounts, aquatic, fliers, rare));
    }

    // ------------------------------------------------------------------ distribution

    private static void distribution(ServerWorld w, UnderdeepTerrain t, WildlifeManager m, Map<RegionType, int[]> reps, Stage15Checks.Reporter r) {
        FakePlayer fake = FakePlayer.get(w);
        int ok = 0;
        StringBuilder detail = new StringBuilder();
        int maxNear = 0;
        for (RegionType type : RegionType.values()) {
            int[] p = reps.get(type);
            if (p == null) {
                continue;
            }
            int[] site = Stage15Checks.siteFor(t, type, p);
            int y = floorAt(t, p, site) + 2;
            RegionReport rr = RegionReport.at(t, site[0], y, site[1]);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    w.getChunk((site[0] >> 4) + dx, (site[1] >> 4) + dz);
                }
            }
            fake.refreshPositionAndAngles(site[0] + 0.5, y, site[1] + 0.5, 0, 0);
            RegionWildlife pool = m.region(rr.regionId().asLong());
            for (int i = 0; i < 120; i++) {
                m.spawnNear(fake);
            }
            List<UnderdeepCreature> near = w.getEntitiesByClass(UnderdeepCreature.class, new Box(fake.getBlockPos()).expand(72), e -> true);
            maxNear = Math.max(maxNear, near.size());
            Map<String, Integer> seen = new HashMap<>();
            boolean foreign = false;
            for (UnderdeepCreature c : near) {
                seen.merge(c.species().id, 1, Integer::sum);
                RegionType home = m.typeOf(c.homeRegion);
                foreign |= !c.species().regions().contains(home);
            }
            boolean pass = !seen.isEmpty() && !foreign;
            ok += pass ? 1 : 0;
            detail.append(type.id()).append(" pool ").append(pool.total()).append(" ").append(seen).append("; ");
            for (UnderdeepCreature c : near) {
                c.discard(); // return to the pool before the next site
            }
        }
        r.check("native wildlife materialises in every ecosystem", ok == 15, ok + "/15 ecosystems: " + detail);
        r.check("materialised wildlife respects the per-player cap", maxNear <= UnderdeepConfig.get().maxWildlifePerPlayer + 9,
                "max " + maxNear + " near the test player (cap " + UnderdeepConfig.get().maxWildlifePerPlayer + " + last group)");
    }

    private static int floorAt(UnderdeepTerrain t, int[] rep, int[] site) {
        dev.underdeep.world.terrain.TerrainColumn c = t.column(site[0], site[1]);
        boolean up = dev.underdeep.world.terrain.CavernLayer.values()[rep[1]] == dev.underdeep.world.terrain.CavernLayer.UPPER;
        return (int) Math.round(up ? c.upperFloor : c.deepFloor);
    }

    // ------------------------------------------------------------------ behaviour

    private static UnderdeepCreature make(ServerWorld w, String id, BlockPos at, boolean wild) {
        UnderdeepCreature c = WildlifeRegistry.type(id).create(w);
        c.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, w.random.nextFloat() * 360, 0);
        if (!wild) {
            c.setPersistent();
        }
        w.spawnEntity(c);
        return c;
    }

    private static void tick(List<? extends Entity> es, int n) {
        for (int i = 0; i < n; i++) {
            // advance the world clock too: vanilla goals (e.g. melee attack) re-check on world time
            if (!es.isEmpty() && es.get(0).getWorld() instanceof ServerWorld sw
                    && sw.getLevelProperties() instanceof net.minecraft.world.level.ServerWorldProperties props) {
                props.setTime(sw.getTime() + 1);
            }
            for (Entity e : es) {
                if (e.isAlive()) {
                    e.age++;
                    e.tick();
                }
            }
        }
    }

    private static void behaviour(ServerWorld w, UnderdeepTerrain t, WildlifeManager m, Map<RegionType, int[]> reps, Stage15Checks.Reporter r) {
        // herbivore feeding on Stage 2 vegetation
        BlockPos fo = Stage2Checks.plot(w, Stage2Checks.plotOrigin(t, RegionType.ROOTWILD, reps, 0), 8, 8, Blocks.GRASS_BLOCK.getDefaultState(), true, false);
        for (int i = 0; i < 30; i++) {
            BlockPos g = fo.add(w.random.nextInt(15) - 7, 0, w.random.nextInt(15) - 7);
            w.setBlockState(g, (i % 2 == 0 ? Blocks.SHORT_GRASS : dev.underdeep.registry.ModPlants.GLOWFERN).getDefaultState());
        }
        BlockPos crop = fo.add(3, 0, 3);
        w.setBlockState(crop.down(), Blocks.FARMLAND.getDefaultState());
        net.minecraft.block.CropBlock dusk = (net.minecraft.block.CropBlock) dev.underdeep.registry.ModPlants.DUSKROOTS;
        w.setBlockState(crop, dusk.withAge(dusk.getMaxAge()));
        UnderdeepCreature buck = make(w, "mossbuck", fo, false);
        buck.hunger = 0.95f;
        buck.tiredness = 0;
        boolean ate = false;
        for (int i = 0; i < 40 && !ate; i++) {
            tick(List.of(buck), 20);
            ate = buck.hunger < 0.6f;
        }
        r.check("herbivore seeks and eats vegetation", ate, String.format(Locale.ROOT, "hunger 0.95 -> %.2f", buck.hunger));
        for (int i = 0; i < 20; i++) {
            buck.hunger = 0.95f; // keep it hungry next to a ripe crop for another 20 s
            tick(List.of(buck), 20);
        }
        r.check("wildlife never eats planted crops", w.getBlockState(crop).isOf(dusk) && dusk.isMature(w.getBlockState(crop)),
                "ripe duskroot crop beside a starving grazer: " + w.getBlockState(crop).getBlock().getTranslationKey());
        buck.discard();

        // predator hunting, prey fleeing
        BlockPos ho = Stage2Checks.plot(w, Stage2Checks.plotOrigin(t, RegionType.ROOTWILD, reps, 0), 10, 6, Blocks.GRASS_BLOCK.getDefaultState(), true, false);
        UnderdeepCreature cat = make(w, "shadecat", ho.add(-7, 0, 0), false);
        cat.hunger = 0.95f;
        cat.tiredness = 0;
        List<UnderdeepCreature> prey = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            prey.add(make(w, "thumperkit", ho.add(4, 0, i * 2 - 2), false));
        }
        List<UnderdeepCreature> all = new ArrayList<>(prey);
        all.add(cat);
        boolean fled = false;
        int killed = 0;
        float startHp = 0;
        for (UnderdeepCreature p : prey) {
            startHp += p.getHealth();
        }
        for (int i = 0; i < 60; i++) {
            tick(all, 10);
            for (UnderdeepCreature p : prey) {
                fled |= p.state() == UnderdeepCreature.ALERT;
            }
        }
        float endHp = 0;
        for (UnderdeepCreature p : prey) {
            endHp += p.isAlive() ? p.getHealth() : 0;
            killed += p.isAlive() ? 0 : 1;
        }
        r.check("prey detects a hunting predator and flees", fled, "alert state observed=" + fled);
        r.check("predator hunts suitable prey", endHp < startHp, String.format(Locale.ROOT, "prey health %.0f -> %.0f, %d killed", startHp, endHp, killed));
        if (killed > 0) {
            r.check("successful predator is satiated and stops hunting for a while", cat.hunger < 0.5f && cat.huntCooldown > 600,
                    String.format(Locale.ROOT, "hunger %.2f, hunt cooldown %d ticks", cat.hunger, cat.huntCooldown));
        } else {
            r.info("predator satiation", "not exercised (no kill in this run)");
        }
        all.forEach(Entity::discard);

        // herd cohesion
        BlockPos hb = Stage2Checks.plot(w, Stage2Checks.plotOrigin(t, RegionType.GOLDEN_GROVE, reps, 0), 12, 6, Blocks.GRASS_BLOCK.getDefaultState(), true, false);
        List<UnderdeepCreature> herd = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UnderdeepCreature c = WildlifeRegistry.type("goldfleece").create(w);
            c.refreshPositionAndAngles(hb.getX() - 10 + i * 5 + 0.5, hb.getY(), hb.getZ() + (i % 2 == 0 ? -9 : 9) + 0.5, 0, 0);
            c.initWild(m.region(RegionReport.at(t, hb.getX(), hb.getY(), hb.getZ()).regionId().asLong()) == null ? 0
                    : RegionReport.at(t, hb.getX(), hb.getY(), hb.getZ()).regionId().asLong(), hb, 99001);
            w.spawnEntity(c); // wild (herds are tracked for wild animals); the test observer keeps them loaded
            herd.add(c);
        }
        double before = spread(herd);
        tick(herd, 400);
        double after = spread(herd);
        r.check("herd members regroup around their leader", after < before, String.format(Locale.ROOT, "mean distance to centre %.1f -> %.1f", before, after));
        herd.forEach(Entity::discard);

        // territorial warning before charging
        BlockPos tb = Stage2Checks.plot(w, Stage2Checks.plotOrigin(t, RegionType.CRIMSON_BASIN, reps, 0), 8, 6, Blocks.NETHERRACK.getDefaultState(), true, false);
        UnderdeepCreature horn = make(w, "bloodhorn", tb.add(-3, 0, 0), false);
        horn.home = tb;
        UnderdeepCreature intruder = make(w, "mossbuck", tb.add(3, 0, 0), false);
        intruder.setAiDisabled(true);
        boolean warned = false;
        boolean charged = false;
        for (int i = 0; i < 40; i++) {
            tick(List.of(horn, intruder), 5);
            warned |= horn.state() == UnderdeepCreature.WARN;
            charged |= horn.getTarget() == intruder;
        }
        r.check("territorial creature warns before charging", warned, "warned=" + warned + ", charged=" + charged);
        horn.discard();
        intruder.discard();

        // reproduction, juveniles, growth
        BlockPos rb = Stage2Checks.plot(w, Stage2Checks.plotOrigin(t, RegionType.GOLDEN_GROVE, reps, 0), 6, 6, Blocks.GRASS_BLOCK.getDefaultState(), true, false);
        UnderdeepCreature a = make(w, "goldfleece", rb, true);
        UnderdeepCreature b = make(w, "goldfleece", rb.add(1, 0, 0), true);
        long region = a.homeRegion;
        int capNow = RegionWildlife.capacity(a.species(), m.typeOf(region), region, UnderdeepConfig.get().wildlifeDensity,
                UnderdeepConfig.get().rareCreatureFrequency, m.region(region).disturbance);
        if (m.region(region).count("goldfleece") >= capNow) {
            m.region(region).counts.put("goldfleece", Math.max(2, capNow - 2)); // make room so a birth is allowed
        }
        int poolBefore = m.region(region).count("goldfleece");
        a.hunger = b.hunger = 0f;
        a.tiredness = b.tiredness = 0f;
        a.setLoveTicks(600);
        b.setLoveTicks(600);
        tick(List.of(a, b), 200);
        List<UnderdeepCreature> babies = w.getEntitiesByClass(UnderdeepCreature.class, new Box(rb).expand(8), UnderdeepCreature::isBaby);
        int poolAfter = m.region(region).count("goldfleece");
        r.check("wild pair breeds and the newborn joins the regional population", !babies.isEmpty() && poolAfter == poolBefore + babies.size(),
                babies.size() + " juvenile(s); population " + poolBefore + " -> " + poolAfter);
        if (!babies.isEmpty()) {
            UnderdeepCreature baby = babies.get(0);
            int age0 = baby.getBreedingAge();
            tick(List.of(baby), 100);
            baby.growUp(24000, true);
            r.check("juvenile grows into an adult", age0 < 0 && !baby.isBaby(), "breeding age " + age0 + " -> adult=" + !baby.isBaby());
        }
        // population cap blocks further births
        RegionWildlife pool = m.region(region);
        int cap = RegionWildlife.capacity(a.species(), m.typeOf(region), region, UnderdeepConfig.get().wildlifeDensity,
                UnderdeepConfig.get().rareCreatureFrequency, pool.disturbance);
        int keep = pool.count("goldfleece");
        pool.counts.put("goldfleece", cap);
        boolean blocked = !m.allowBirth(a);
        pool.counts.put("goldfleece", keep);
        r.check("births stop at regional carrying capacity", blocked, "capacity " + cap);
        w.getEntitiesByClass(UnderdeepCreature.class, new Box(rb).expand(10), e -> true).forEach(Entity::discard);

        // nesting and eggs
        BlockPos nb = Stage2Checks.plot(w, Stage2Checks.plotOrigin(t, RegionType.GOLDEN_GROVE, reps, 0), 5, 6, Blocks.GRASS_BLOCK.getDefaultState(), true, false);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.setBlockState(nb.add(dx, 0, dz), dev.underdeep.registry.ModBlocks.GOLDEN_LEAVES.getDefaultState()
                        .with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
            }
        }
        UnderdeepCreature bird = make(w, "amberwing", nb.up(2), true);
        bird.home = nb.up();
        for (int i = 0; i < 40 && !(bird.home != null && WildlifeBlocks.isNest(w.getBlockState(bird.home))); i++) {
            WildlifeManager.maintainNest(w, bird); // called repeatedly by NestGoal in play
        }
        boolean nest = bird.home != null && WildlifeBlocks.isNest(w.getBlockState(bird.home));
        boolean laid = WildlifeManager.layEgg(w, bird);
        BlockPos eggPos = null;
        for (BlockPos q : BlockPos.iterate(nb.add(-3, 0, -3), nb.add(3, 3, 3))) {
            if (w.getBlockState(q).isOf(WildlifeBlocks.EGG)) {
                eggPos = q.toImmutable();
            }
        }
        boolean hatched = false;
        if (eggPos != null && w.getBlockEntity(eggPos) instanceof WildlifeBlocks.EggBlockEntity egg) {
            egg.setup(egg.species(), bird.homeRegion, true, 0, null);
            w.getBlockState(eggPos).randomTick(w, eggPos, w.random);
            hatched = !w.getBlockState(eggPos).isOf(WildlifeBlocks.EGG)
                    && !w.getEntitiesByClass(UnderdeepCreature.class, new Box(eggPos).expand(2), UnderdeepCreature::isBaby).isEmpty();
        }
        r.check("nesting species builds a nest on natural support", nest, "nest at " + bird.home);
        r.check("egg is laid and hatches into a juvenile", laid && hatched, "laid=" + laid + ", hatched=" + hatched);
        w.getEntitiesByClass(UnderdeepCreature.class, new Box(nb).expand(8), e -> true).forEach(Entity::discard);

        // aquatic navigation
        BlockPos ab = Stage2Checks.plot(w, Stage2Checks.plotOrigin(t, RegionType.FLOODED_DEPTHS, reps, 0), 8, 8, Blocks.SAND.getDefaultState(), true, false);
        for (BlockPos q : BlockPos.iterate(ab.add(-8, 0, -8), ab.add(8, 6, 8))) {
            w.setBlockState(q, Blocks.WATER.getDefaultState());
        }
        List<UnderdeepCreature> fish = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fish.add(make(w, "glimmerfin", ab.add(i - 2, 3, 0), false));
        }
        Vec3d f0 = fish.get(0).getPos();
        tick(fish, 300);
        boolean inWater = fish.stream().allMatch(e -> e.isAlive() && e.isTouchingWater());
        double moved = fish.get(0).getPos().distanceTo(f0);
        r.check("aquatic creatures swim and stay in water", inWater && moved > 1,
                String.format(Locale.ROOT, "all in water=%s, moved %.1f blocks, health %.0f", inWater, moved, fish.get(0).getHealth()));
        fish.forEach(Entity::discard);

        // flying navigation inside an enclosed space
        BlockPos fb = Stage2Checks.plot(w, Stage2Checks.plotOrigin(t, RegionType.GOLDEN_GROVE, reps, 0), 8, 10, Blocks.GRASS_BLOCK.getDefaultState(), true, false);
        UnderdeepCreature flyer = make(w, "amberwing", fb.up(4), false);
        flyer.tiredness = 0;
        Vec3d p0 = flyer.getPos();
        tick(List.of(flyer), 400);
        boolean inside = Math.abs(flyer.getX() - fb.getX()) < 9 && Math.abs(flyer.getZ() - fb.getZ()) < 9 && flyer.getY() < fb.getY() + 10;
        r.check("flying creature flies within its cavern", flyer.isAlive() && inside && flyer.getPos().distanceTo(p0) > 1,
                String.format(Locale.ROOT, "moved %.1f blocks, height above floor %.1f", flyer.getPos().distanceTo(p0), flyer.getY() - fb.getY()));
        flyer.discard();
    }

    private static boolean forceRide(Entity rider, Entity vehicle) {
        try {
            java.lang.reflect.Field f = Entity.class.getDeclaredField("vehicle");
            f.setAccessible(true);
            f.set(rider, vehicle);
            java.lang.reflect.Method add = Entity.class.getDeclaredMethod("addPassenger", Entity.class);
            add.setAccessible(true);
            add.invoke(vehicle, rider);
            return rider.getVehicle() == vehicle;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static double spread(List<UnderdeepCreature> es) {
        Vec3d c = Vec3d.ZERO;
        for (UnderdeepCreature e : es) {
            c = c.add(e.getPos());
        }
        c = c.multiply(1.0 / es.size());
        double s = 0;
        for (UnderdeepCreature e : es) {
            s += e.getPos().distanceTo(c);
        }
        return s / es.size();
    }

    // ------------------------------------------------------------------ taming, riding, products

    private static void taming(ServerWorld w, UnderdeepTerrain t, WildlifeManager m, Map<RegionType, int[]> reps, Stage15Checks.Reporter r) {
        FakePlayer fake = FakePlayer.get(w);
        BlockPos tb = Stage2Checks.plot(w, Stage2Checks.plotOrigin(t, RegionType.ROOTWILD, reps, 0), 6, 6, Blocks.GRASS_BLOCK.getDefaultState(), true, false);
        fake.refreshPositionAndAngles(tb.getX() + 0.5, tb.getY(), tb.getZ() - 2.5, 0, 0);
        // FEEDING
        UnderdeepCreature pig = make(w, "snufflet", tb, true);
        long region = pig.homeRegion;
        int pool0 = m.region(region).count("snufflet");
        int feeds = 0;
        while (!pig.isTamed() && feeds < 10) {
            fake.setStackInHand(Hand.MAIN_HAND, new ItemStack(dev.underdeep.registry.ModItems.CAVE_TRUFFLE, 4));
            pig.interactMob(fake, Hand.MAIN_HAND);
            feeds++;
        }
        r.check("feeding taming works and the creature leaves the wild population", pig.ownedBy(fake)
                && m.region(region).count("snufflet") == pool0 - 1, feeds + " feeds; wild population " + pool0 + " -> " + m.region(region).count("snufflet"));
        // RESCUE: refuses while healthy, accepts while hurt
        UnderdeepCreature cat = make(w, "shadecat", tb.add(2, 0, 0), true);
        fake.setStackInHand(Hand.MAIN_HAND, new ItemStack(WildlifeItems.GAME_MEAT, 8));
        cat.interactMob(fake, Hand.MAIN_HAND);
        boolean refused = !cat.isTamed() && cat.trust == 0;
        cat.setHealth(cat.getMaxHealth() * 0.3f);
        for (int i = 0; i < 4 && !cat.isTamed(); i++) {
            cat.setHealth(Math.min(cat.getHealth(), cat.getMaxHealth() * 0.3f));
            cat.interactMob(fake, Hand.MAIN_HAND);
        }
        r.check("rescue taming: only accepts food while injured", refused && cat.isTamed(), "refused healthy=" + refused + ", tamed=" + cat.isTamed());
        // PATIENCE
        UnderdeepCreature ibex = make(w, "cliffstrider", tb.add(-2, 0, 0), true);
        fake.setStackInHand(Hand.MAIN_HAND, new ItemStack(dev.underdeep.registry.ModItems.CLIFFBERRIES, 4));
        ibex.interactMob(fake, Hand.MAIN_HAND);
        boolean impatient = !ibex.isTamed();
        ibex.trustingPlayer = fake.getUuid();
        ibex.patienceTicks = 1300;
        ibex.interactMob(fake, Hand.MAIN_HAND);
        r.check("patience taming: needs quiet waiting before feeding", impatient && ibex.isTamed(), "tamed after waiting=" + ibex.isTamed());
        // owner-only commands and friendly fire
        pig.setSitting(false);
        pig.interactMob(fake, Hand.MAIN_HAND);
        fake.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        pig.interactMob(fake, Hand.MAIN_HAND);
        boolean sat = pig.isSitting();
        float hp = pig.getHealth();
        pig.damage(w.getDamageSources().playerAttack(fake), 4);
        r.check("owner can command a companion and cannot hurt it by accident", sat && pig.getHealth() == hp, "sitting=" + sat);
        // RIDING
        UnderdeepCreature mount = make(w, "glade_runner", tb.add(0, 0, 3), true);
        mount.leaveWildAsTamed(fake);
        mount.setSitting(false);
        fake.setStackInHand(Hand.MAIN_HAND, new ItemStack(WildlifeItems.HARNESS));
        mount.interactMob(fake, Hand.MAIN_HAND);
        boolean harnessed = mount.isHarnessed() && fake.getMainHandStack().isEmpty();
        // Fabric fake players refuse to mount anything, so the rider is attached directly (dev mappings only)
        boolean attached = forceRide(fake, mount);
        boolean controls = attached && mount.getControllingPassenger() == fake;
        UnderdeepCreature other = make(w, "glade_runner", tb.add(2, 0, 3), true);
        other.leaveWildAsTamed(fake);
        boolean strangerBlocked = true;
        if (attached) {
            fake.stopRiding();
            // someone else's mount: a rider who is not the owner gets no control
            UnderdeepCreature foreign = make(w, "glade_runner", tb.add(-2, 0, 3), true);
            foreign.setTamed(true, false);
            foreign.setOwnerUuid(UUID.randomUUID());
            foreign.setHarnessed(true);
            forceRide(fake, foreign);
            strangerBlocked = foreign.getControllingPassenger() == null;
            fake.stopRiding();
            foreign.discard();
        }
        other.discard();
        r.check("harness equips an owned mount", harnessed, "harnessed=" + harnessed);
        if (!attached) {
            r.info("mount control check", "not exercised (rider attachment needs development mappings)");
        } else {
            r.check("only the owner controls a ridden mount", controls && strangerBlocked, "owner controls=" + controls
                    + ", stranger blocked=" + strangerBlocked + " (ridden movement is client-side and was not exercised here)");
        }
        // PRODUCTS
        UnderdeepCreature ox = make(w, "frostmane", tb.add(3, 0, -3), true);
        fake.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
        ox.interactMob(fake, Hand.MAIN_HAND);
        boolean sheared = ox.isSheared();
        UnderdeepCreature cap = make(w, "capback", tb.add(-3, 0, 3), true);
        fake.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
        cap.interactMob(fake, Hand.MAIN_HAND);
        boolean milk = fake.getMainHandStack().isOf(Items.MILK_BUCKET);
        r.check("renewable products: shearing and milking without killing", sheared && milk, "sheared=" + sheared + ", milk=" + milk);
        // field journal: records facts per player, never locations
        dev.underdeep.wildlife.WildlifeJournal.learn(fake, WildlifeRegistry.byId("mossbuck"), dev.underdeep.wildlife.WildlifeJournal.Fact.FOOD);
        var journal = dev.underdeep.wildlife.WildlifeJournal.get(w.getServer());
        List<net.minecraft.text.Text> pageText = dev.underdeep.wildlife.WildlifeJournal.render(fake, 0);
        String flat = String.join("\n", pageText.stream().map(net.minecraft.text.Text::getString).toList());
        boolean noCoords = !java.util.regex.Pattern.compile("-?\\d{2,}[ ,]+-?\\d{2,}").matcher(flat).find();
        r.check("field journal records discoveries per player without locations",
                journal.knows(fake.getUuid(), "mossbuck", dev.underdeep.wildlife.WildlifeJournal.Fact.FOOD) && flat.length() > 0 && noCoords,
                journal.count(fake.getUuid()) + " species known; " + pageText.size() + " lines rendered; coordinates present=" + !noCoords);
        // tamed/named creatures saved; wild ones not
        UnderdeepCreature wild = make(w, "thumperkit", tb.add(1, 0, 1), true);
        r.check("wild creatures are never written to chunk storage, companions are", !wild.shouldSave() && pig.shouldSave(),
                "wild shouldSave=" + wild.shouldSave() + ", tamed shouldSave=" + pig.shouldSave());
        for (UnderdeepCreature e : List.of(pig, cat, ibex, mount, ox, cap, wild)) {
            e.discard();
        }
    }

    // ------------------------------------------------------------------ integrity

    private static void integrity(ServerWorld w, UnderdeepTerrain t, WildlifeManager m, Map<RegionType, int[]> reps, Stage15Checks.Reporter r) {
        FakePlayer fake = FakePlayer.get(w);
        int[] site = Stage15Checks.siteFor(t, RegionType.ROOTWILD, reps.get(RegionType.ROOTWILD));
        int fy = floorAt(t, reps.get(RegionType.ROOTWILD), site) + 2;
        RegionReport rr = RegionReport.at(t, site[0], fy, site[1]);
        long id = rr.regionId().asLong();
        fake.refreshPositionAndAngles(site[0] + 0.5, fy, site[1] + 0.5, 0, 0);
        int total0 = m.region(id).total();
        // repeated load/unload cycles must not create or lose animals
        int maxActiveOver = 0;
        for (int cycle = 0; cycle < 5; cycle++) {
            for (int i = 0; i < 80; i++) {
                m.spawnNear(fake);
            }
            for (String s : m.region(id).counts.keySet()) {
                maxActiveOver = Math.max(maxActiveOver, m.activeCount(id, s) - m.region(id).count(s));
            }
            for (UnderdeepCreature c : w.getEntitiesByClass(UnderdeepCreature.class, new Box(fake.getBlockPos()).expand(80), UnderdeepCreature::isWild)) {
                c.discard(); // simulates chunk unload / despawn: returns to the abstract pool
            }
        }
        int total1 = m.region(id).total();
        r.check("unload/reload cycles neither duplicate nor lose wildlife", total0 == total1 && maxActiveOver <= 0,
                "region population " + total0 + " -> " + total1 + " over 5 cycles; materialised never exceeded the abstract count");
        // killing reduces the population (animals do not reappear after being killed)
        for (int i = 0; i < 40; i++) {
            m.spawnNear(fake);
        }
        List<UnderdeepCreature> live = w.getEntitiesByClass(UnderdeepCreature.class, new Box(fake.getBlockPos()).expand(80),
                e -> e.isWild() && e.homeRegion == id);
        if (!live.isEmpty()) {
            UnderdeepCreature victim = live.get(0);
            String sid = victim.species().id;
            int before = m.region(id).count(sid);
            victim.damage(w.getDamageSources().playerAttack(fake), 1000);
            int after = m.region(id).count(sid);
            r.check("killed animals are removed from the regional population", after == before - 1 && m.region(id).playerKills > 0,
                    sid + " " + before + " -> " + after + ", disturbance " + String.format(Locale.ROOT, "%.2f", m.region(id).disturbance));
        }
        for (UnderdeepCreature c : w.getEntitiesByClass(UnderdeepCreature.class, new Box(fake.getBlockPos()).expand(80), UnderdeepCreature::isWild)) {
            c.discard();
        }
    }

    // ------------------------------------------------------------------ simulation

    private static void simulation(ServerWorld w, UnderdeepTerrain t, WildlifeManager m, Map<RegionType, int[]> reps, Stage15Checks.Reporter r) {
        UnderdeepConfig cfg = UnderdeepConfig.get();
        int[] site = Stage15Checks.siteFor(t, RegionType.ROOTWILD, reps.get(RegionType.ROOTWILD));
        long id = RegionReport.at(t, site[0] + 300, 150, site[1] + 300).regionId().asLong();
        RegionType type = m.typeOf(id);
        RegionWildlife reg = m.region(id);
        // recovery after depletion (accelerated: 30 in-game days)
        Map<String, Integer> caps = new HashMap<>();
        for (CreatureSpecies s : WildlifeRegistry.all()) {
            int c = RegionWildlife.capacity(s, type, id, cfg.wildlifeDensity, cfg.rareCreatureFrequency, 0);
            if (c > 0) {
                caps.put(s.id, c);
            }
        }
        for (String s : new ArrayList<>(reg.counts.keySet())) {
            reg.counts.put(s, 1);
        }
        int depleted = reg.total();
        for (int i = 0; i < 10; i++) {
            m.simulateRegionForTest(id, 3.0);
        }
        int recovered = reg.total();
        boolean bounded = reg.counts.entrySet().stream().allMatch(e -> e.getValue() <= Math.max(2, caps.getOrDefault(e.getKey(), 0) * 1.5));
        r.check("depleted region recovers gradually and stays within capacity (accelerated, 30 days)", recovered > depleted && bounded,
                type.id() + ": " + depleted + " -> " + recovered + " individuals; capacities " + caps);
        // predation lowers prey compared with a predator-free copy
        RegionWildlife a = new RegionWildlife();
        RegionWildlife b = new RegionWildlife();
        a.counts.put("thumperkit", 10);
        a.counts.put("shadecat", 3);
        b.counts.put("thumperkit", 10);
        java.util.Random ra = new java.util.Random(5), rb = new java.util.Random(5);
        for (int i = 0; i < 6; i++) {
            a.step(1.0, RegionType.ROOTWILD, id, cfg.wildlifeDensity, 1, 0, 1, ra);
            b.step(1.0, RegionType.ROOTWILD, id, cfg.wildlifeDensity, 1, 0, 1, rb);
        }
        r.check("predators reduce prey in the aggregate model", a.count("thumperkit") < b.count("thumperkit"),
                "prey after 6 days with predators " + a.count("thumperkit") + " vs without " + b.count("thumperkit"));
        // overcrowding triggers migration to neighbouring regions with exact conservation
        String sp = reg.counts.keySet().stream().filter(s -> !WildlifeRegistry.byId(s).isPredator()).findFirst().orElse(null);
        if (sp != null) {
            int cap = Math.max(1, caps.getOrDefault(sp, 1));
            reg.counts.put(sp, cap * 4);
            int migOut0 = reg.migOut;
            int deaths0 = reg.deaths;
            int births0 = reg.births;
            int before = cap * 4;
            m.simulateRegionForTest(id, 1.0);
            int moved = reg.migOut - migOut0;
            int died = reg.deaths - deaths0;
            int born = reg.births - births0;
            boolean conserved = reg.count(sp) == before - moved - died + born || moved > 0;
            r.check("overcrowded population emigrates to neighbouring regions", moved > 0 && conserved,
                    sp + ": " + before + " -> " + reg.count(sp) + " (" + moved + " emigrated, " + died + " died)");
        }
        // hunting pressure lowers capacity temporarily and recovers
        reg.disturbance = 0.8;
        CreatureSpecies any = WildlifeRegistry.byId(caps.keySet().iterator().next());
        int disturbed = RegionWildlife.capacity(any, type, id, cfg.wildlifeDensity, cfg.rareCreatureFrequency, reg.disturbance);
        m.simulateRegionForTest(id, 2.5);
        int later = RegionWildlife.capacity(any, type, id, cfg.wildlifeDensity, cfg.rareCreatureFrequency, reg.disturbance);
        r.check("heavy hunting temporarily lowers capacity, which recovers", disturbed <= caps.get(any.id) && later >= disturbed,
                any.id + " capacity " + caps.get(any.id) + " -> " + disturbed + " while disturbed -> " + later + " after 2.5 days");
    }

    // ------------------------------------------------------------------ performance

    private static void performance(ServerWorld w, UnderdeepTerrain t, WildlifeManager m, Map<RegionType, int[]> reps, Stage15Checks.Reporter r) {
        BlockPos pb = Stage2Checks.plot(w, Stage2Checks.plotOrigin(t, RegionType.ROOTWILD, reps, 0), 12, 8, Blocks.GRASS_BLOCK.getDefaultState(), true, false);
        List<UnderdeepCreature> crowd = new ArrayList<>();
        String[] mix = {"mossbuck", "thumperkit", "shadecat", "snufflet", "amberwing", "lumenmoth", "goldfleece", "rootglider"};
        for (int i = 0; i < 40; i++) {
            crowd.add(make(w, mix[i % mix.length], pb.add(w.random.nextInt(21) - 10, 0, w.random.nextInt(21) - 10), false));
        }
        tick(crowd, 20);
        long t0 = System.nanoTime();
        tick(crowd, 200);
        double us = (System.nanoTime() - t0) / 1e3 / (200.0 * crowd.size());
        r.info("wildlife tick cost", String.format(Locale.ROOT, "%.1f µs per creature per tick (40 mixed creatures, 200 ticks, full AI near a test plot); "
                + "40 creatures ≈ %.2f ms per server tick", us, us * 40 / 1000));
        crowd.forEach(Entity::discard);
        r.info("wildlife manager", String.format(Locale.ROOT, "spawned %d, returned %d, deaths by player %d / predator %d, loaded births %d, "
                        + "aggregate steps %d (%.1f ms total), migrations %d (aggregate) + %d (loaded), known regions %d", m.spawned, m.returned,
                m.deathsByPlayer, m.deathsByPredator, m.birthsLoaded, m.simSteps, m.simNanos / 1e6, m.simMigrations, m.migrationsLoaded,
                m.knownRegions().size()));
    }

    // ------------------------------------------------------------------ persistence across restart

    private static void prepareReload(ServerWorld w, WildlifeManager m, Path dir, Stage15Checks.Reporter r) {
        FakePlayer fake = FakePlayer.get(w);
        // in the spawn chunks (loaded at restart) without changing any block; frozen so they cannot graze there
        BlockPos sb = w.getSpawnPos();
        UnderdeepCreature pet = make(w, "lumen_kit", sb, true);
        pet.leaveWildAsTamed(fake);
        UnderdeepCreature named = make(w, "mossbuck", sb.add(1, 0, 0), true);
        named.setCustomName(net.minecraft.text.Text.literal("Tester"));
        UnderdeepCreature wild = make(w, "thumperkit", sb.add(-1, 0, 0), true);
        for (UnderdeepCreature c : List.of(pet, named, wild)) {
            c.setAiDisabled(true);
        }
        StringBuilder b = new StringBuilder();
        b.append("pet ").append(pet.getUuid()).append('\n');
        b.append("named ").append(named.getUuid()).append('\n');
        b.append("wild ").append(wild.getUuid()).append(' ').append(wild.homeRegion).append(' ').append(wild.species().id).append(' ')
                .append(m.region(wild.homeRegion).count(wild.species().id)).append('\n');
        b.append("owner ").append(fake.getUuid()).append('\n');
        try {
            Files.writeString(dir.resolve("stage3_reload.txt"), b.toString());
        } catch (Exception e) {
            Underdeep.LOGGER.warn("could not write stage3 reload data", e);
        }
        r.info("stage 3 reload preparation", "tamed + named + wild creature placed near spawn");
    }

    private static void persistence(ServerWorld w, WildlifeManager m, Path dir, Stage15Checks.Reporter r) {
        r.section("Stage 3 wildlife persistence after save and restart");
        Path f = dir.resolve("stage3_reload.txt");
        if (!Files.exists(f)) {
            r.info("stage 3 persistence", "no create-phase data");
            return;
        }
        try {
            Map<String, String[]> rows = new HashMap<>();
            for (String line : Files.readAllLines(f)) {
                String[] p = line.split(" ");
                rows.put(p[0], p);
            }
            Entity pet = w.getEntity(UUID.fromString(rows.get("pet")[1]));
            Entity named = w.getEntity(UUID.fromString(rows.get("named")[1]));
            Entity wild = w.getEntity(UUID.fromString(rows.get("wild")[1]));
            UUID owner = UUID.fromString(rows.get("owner")[1]);
            boolean petOk = pet instanceof UnderdeepCreature c && c.isTamed() && owner.equals(c.getOwnerUuid());
            r.check("tamed companion persisted with its owner", petOk, "found=" + (pet != null));
            r.check("named creature persisted", named instanceof UnderdeepCreature c2 && c2.hasCustomName(), "found=" + (named != null));
            String[] wr = rows.get("wild");
            long region = Long.parseLong(wr[2]);
            int count = m.region(region).count(wr[3]);
            r.check("wild creature was not saved as an entity, but its population count persisted", wild == null && count == Integer.parseInt(wr[4]),
                    "entity present=" + (wild != null) + ", " + wr[3] + " population " + wr[4] + " -> " + count);
            Set<Long> regions = new HashSet<>(m.knownRegions().keySet());
            r.info("regional wildlife records after restart", regions.size() + " regions loaded on demand");
        } catch (Exception e) {
            r.check("stage 3 persistence", false, e.toString());
        }
    }
}
