package dev.underdeep.explore;

import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.civ.settlement.Settlements;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.guide.Advancements;
import dev.underdeep.registry.ModBlocks;
import dev.underdeep.survival.Hazards;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Travel encounters (Stage 9): small events while exploring. Per player at most one every {@code 6 / frequency}
 * minutes, and each type has its own cooldown (content.json). Eligibility depends on region, nearby sites and the
 * spot found (a dry floor 6–16 blocks away, outside settlements and away from containers, beds and doors, so homes are
 * never touched). No world-wide searches: every check is bounded around one player. Running effects (moving lights)
 * are short-lived entries ticked each second.
 */
public final class TravelEncounters {
    private TravelEncounters() {
    }

    private static final Map<UUID, Map<String, Long>> LAST = new HashMap<>();
    private static final List<Moving> MOVING = new ArrayList<>();

    /** A moving light (will-o-light, ghost lanterns) that walks toward a target for a while. */
    static final class Moving {
        ServerWorld w;
        Vec3d pos;
        Vec3d target;
        int seconds;
        boolean ghost;
        UUID player;
    }

    public static void tick(ServerWorld w, ServerPlayerEntity p, ExplorationMap map) {
        tickMoving(w);
        double freq = UnderdeepConfig.get().exploration.encounterFrequency;
        if (freq <= 0 || p.isSpectator() || p.isCreative()) {
            return;
        }
        ExplorerData d = ExplorerData.of(p);
        long now = w.getTime();
        long gap = (long) (20 * 60 * 6 / freq);
        if (d.lastEncounter >= 0 && now - d.lastEncounter < gap) {
            return;
        }
        if (p.getRandom().nextInt(150) != 0) {
            return; // on average a few minutes after the gap
        }
        UnderdeepTerrain t = UnderdeepWorlds.terrain(w);
        RegionType region = t == null ? null : t.regionAt(p.getBlockX(), p.getBlockY(), p.getBlockZ());
        if (region == null || insideSettlement(w, p.getBlockPos())) {
            return;
        }
        List<ExploreContent.EncounterDef> pool = new ArrayList<>();
        double total = 0;
        Map<String, Long> last = LAST.computeIfAbsent(p.getUuid(), k -> new HashMap<>());
        for (ExploreContent.EncounterDef e : ExploreContent.ENCOUNTERS.values()) {
            if (!e.regions().isEmpty() && !e.regions().contains(region)) {
                continue;
            }
            Long l = last.get(e.id());
            if (l != null && now - l < e.cooldownMinutes() * 1200L / freq) {
                continue;
            }
            pool.add(e);
            total += e.weight();
        }
        double pick = p.getRandom().nextDouble() * total;
        for (ExploreContent.EncounterDef e : pool) {
            pick -= e.weight();
            if (pick <= 0) {
                if (run(w, p, map, e.id(), region)) {
                    last.put(e.id(), now);
                    d.lastEncounter = now;
                    d.lastEncounterId = e.id();
                    Advancements.event(p, "encounter", e.id());
                }
                return;
            }
        }
    }

    /** Runs an encounter now (also used by the self-test and /underdeep explore encounter). */
    public static boolean run(ServerWorld w, ServerPlayerEntity p, @Nullable ExplorationMap map, String id, RegionType region) {
        BlockPos spot = spot(w, p.getBlockPos(), 6, 16);
        switch (id) {
            case "echo_whisper" -> {
                Site s = map == null ? null : map.sitesNear(p.getBlockX(), p.getBlockZ(), 90).stream()
                        .filter(x -> x.family().category() == ExploreContent.Category.RUIN).findFirst().orElse(null);
                if (s == null) {
                    return false;
                }
                w.spawnParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1.5, p.getZ(), 6, 1.5, 0.6, 1.5, 0.01);
                p.playSoundToPlayer(SoundEvents.AMBIENT_CAVE.value(), SoundCategory.AMBIENT, 1f, 0.7f);
                String line = ExploreContent.RUMORS.stream().filter(r -> r.kind().equals("echo")).map(ExploreContent.Rumor::text)
                        .skip(p.getRandom().nextInt(Math.max(1, (int) ExploreContent.RUMORS.stream().filter(r -> r.kind().equals("echo")).count())))
                        .findFirst().orElse("...");
                p.sendMessage(Text.literal("A whisper, from no one: \"" + line + "\"").formatted(Formatting.DARK_AQUA, Formatting.ITALIC), false);
                return true;
            }
            case "glowmoth_swarm" -> {
                for (int i = 0; i < 6; i++) {
                    w.spawnParticles(ParticleTypes.GLOW, p.getX() + p.getRandom().nextGaussian() * 3, p.getY() + 2, p.getZ() + p.getRandom().nextGaussian() * 3,
                            8, 0.8, 0.8, 0.8, 0.02);
                }
                p.sendMessage(Text.literal("A cloud of glowing moths drifts around you, then away.").formatted(Formatting.AQUA), true);
                return true;
            }
            case "rockfall" -> {
                if (spot == null) {
                    return false;
                }
                BlockPos ceil = ceiling(w, spot, 14);
                if (ceil == null) {
                    return false;
                }
                w.playSound(null, spot, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 2f, 0.4f);
                w.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.getDefaultState()),
                        ceil.getX() + 0.5, ceil.getY() - 0.2, ceil.getZ() + 0.5, 30, 1.5, 0.1, 1.5, 0);
                p.sendMessage(Text.literal("Dust trickles from the ceiling nearby... then the rock gives way.").formatted(Formatting.YELLOW), true);
                for (int i = 0; i < 4; i++) {
                    BlockPos f = ceil.add(p.getRandom().nextInt(3) - 1, 0, p.getRandom().nextInt(3) - 1);
                    if (w.getBlockState(f).isAir()) {
                        FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(w, f, Blocks.GRAVEL.getDefaultState());
                        fb.setHurtEntities(1f, 4);
                    }
                }
                return true;
            }
            case "ore_reveal" -> {
                BlockPos wall = wallNear(w, p.getBlockPos(), 9);
                if (wall == null) {
                    return false;
                }
                Block ore = switch (region) {
                    case ASHEN_HOLLOWS, CRIMSON_BASIN -> dev.underdeep.registry.ModPlants.EMBERITE_ORE;
                    case EVERFROST -> dev.underdeep.registry.ModPlants.CRYOLITE_ORE;
                    case STORMSTONE_CAVERNS -> dev.underdeep.registry.ModPlants.GALVANITE_ORE;
                    case HOLLOW_PEAKS -> dev.underdeep.registry.ModPlants.MALACHITE_ORE;
                    case PETRIFIED_FOREST -> dev.underdeep.registry.ModPlants.FOSSIL_ORE;
                    case CRYSTAL_DEEP -> dev.underdeep.registry.ModPlants.PRISMATIC_OPAL_ORE;
                    default -> Blocks.COPPER_ORE;
                };
                int n = 0;
                for (BlockPos q : BlockPos.iterate(wall.add(-1, -1, -1), wall.add(1, 1, 1))) {
                    if (natural(w.getBlockState(q)) && p.getRandom().nextInt(3) == 0 && n < 5) {
                        w.setBlockState(q, ore.getDefaultState());
                        n++;
                    }
                }
                if (n == 0) {
                    return false;
                }
                w.playSound(null, wall, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.BLOCKS, 1.5f, 0.5f);
                w.spawnParticles(ParticleTypes.CLOUD, wall.getX() + 0.5, wall.getY() + 0.5, wall.getZ() + 0.5, 10, 0.6, 0.6, 0.6, 0.02);
                p.sendMessage(Text.literal("A slab of rock sloughs off a wall nearby, baring a glint of ore.").formatted(Formatting.YELLOW), true);
                return true;
            }
            case "lost_pack", "abandoned_camp" -> {
                if (spot == null || !clear(w, spot)) {
                    return false;
                }
                boolean camp = id.equals("abandoned_camp");
                if (camp) {
                    w.setBlockState(spot, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, false));
                    BlockPos b = spot.east(2);
                    if (w.getBlockState(b).isAir() && w.getBlockState(b.down()).isSideSolidFullSquare(w, b.down(), Direction.UP)) {
                        w.setBlockState(b, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
                        loot(w, b, "underdeep:exploration/abandoned_camp");
                    }
                } else {
                    w.setBlockState(spot, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
                    loot(w, spot, "underdeep:exploration/lost_pack");
                    BlockPos sk = spot.north();
                    if (w.getBlockState(sk).isAir()) {
                        w.setBlockState(sk, Blocks.SKELETON_SKULL.getDefaultState().with(Properties.ROTATION, p.getRandom().nextInt(16)));
                    }
                }
                w.spawnParticles(ParticleTypes.WAX_OFF, spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5, 12, 0.3, 0.6, 0.3, 0.01);
                p.sendMessage(Text.literal(camp ? "You smell old smoke. Someone camped near here, not so long ago."
                        : "Something half-buried catches your eye a little way off.").formatted(Formatting.GOLD), true);
                return true;
            }
            case "will_o_light", "ghost_lanterns" -> {
                boolean ghost = id.equals("ghost_lanterns");
                Site target = map == null ? null : nearestUndiscovered(map, p, ghost ? 220 : 120, ghost);
                if (target == null) {
                    return false;
                }
                Moving mv = new Moving();
                mv.w = w;
                mv.pos = p.getEyePos().add(p.getRotationVector().multiply(4));
                mv.target = new Vec3d(target.x() + 0.5, target.y() + 2, target.z() + 0.5);
                mv.seconds = ghost ? 45 : 30;
                mv.ghost = ghost;
                mv.player = p.getUuid();
                MOVING.add(mv);
                Discovery.lead(p, target, "lights");
                p.sendMessage(Text.literal(ghost ? "Lights are moving in the dark: a slow procession of lanterns, carried by no one."
                        : "A pale light drifts ahead, as if waiting for you.").formatted(Formatting.AQUA, Formatting.ITALIC), false);
                return true;
            }
            case "spore_bloom" -> {
                w.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, p.getX(), p.getY() + 1, p.getZ(), 60, 4, 1.5, 4, 0.01);
                Hazards.burst(p, Hazards.Hazard.SPORE_CLOUDS, 15);
                int grown = 0;
                for (int i = 0; i < 12 && grown < 4; i++) {
                    BlockPos q = spot(w, p.getBlockPos(), 2, 7);
                    if (q != null && clear(w, q)) {
                        w.setBlockState(q, dev.underdeep.registry.ModPlants.PUFFCAP.getDefaultState());
                        grown++;
                    }
                }
                p.sendMessage(Text.literal("The fungi around you release a great breath of spores. New caps push up from the ground.").formatted(Formatting.GREEN), true);
                return true;
            }
            case "geyser" -> {
                if (spot == null) {
                    return false;
                }
                for (int y = 0; y < 8; y++) {
                    w.spawnParticles(ParticleTypes.CLOUD, spot.getX() + 0.5, spot.getY() + y, spot.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
                }
                w.playSound(null, spot, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 2f, 0.5f);
                if (p.getBlockPos().getSquaredDistance(spot) < 9) {
                    Hazards.burst(p, Hazards.Hazard.GEOTHERMAL, 25);
                }
                p.sendMessage(Text.literal("With a roar, steam bursts from a crack in the floor.").formatted(Formatting.GOLD), true);
                return true;
            }
            case "crystal_song" -> {
                p.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 1.5f, 0.5f);
                p.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 1.5f, 0.75f);
                if (spot != null && clear(w, spot)) {
                    w.setBlockState(spot, ModBlocks.LUMINITE_CLUSTER.getDefaultState());
                }
                p.sendMessage(Text.literal("The crystals hum a long chord. Nearby, a new cluster has grown.").formatted(Formatting.LIGHT_PURPLE), true);
                return true;
            }
            case "ice_crack" -> {
                p.playSoundToPlayer(SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.AMBIENT, 1f, 0.5f);
                w.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 0.2, p.getZ(), 30, 2, 0.1, 2, 0.02);
                p.sendMessage(Text.literal("The ice groans under your weight. Somewhere, it splits.").formatted(Formatting.AQUA), true);
                return true;
            }
            case "falling_star" -> {
                if (spot == null) {
                    return false;
                }
                for (int i = 0; i < 10; i++) {
                    w.spawnParticles(ParticleTypes.END_ROD, spot.getX() + 0.5, spot.getY() + 10 - i, spot.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0);
                }
                w.spawnEntity(new ItemEntity(w, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, new ItemStack(ExploreItems.STAR_SHARD)));
                w.playSound(null, spot, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.BLOCKS, 1.5f, 1.4f);
                p.sendMessage(Text.literal("A shard of light drops from the starlit ceiling and lands nearby.").formatted(Formatting.LIGHT_PURPLE), true);
                return true;
            }
            case "static_storm" -> {
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 40, 3, 1.5, 3, 0.1);
                Hazards.burst(p, Hazards.Hazard.STATIC_CHARGE, 15);
                p.playSoundToPlayer(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 0.4f, 1.6f);
                p.sendMessage(Text.literal("The air prickles; sparks crawl over the stone.").formatted(Formatting.YELLOW), true);
                return true;
            }
            case "drifting_raft" -> {
                BlockPos water = waterNear(w, p.getBlockPos(), 14);
                if (water == null) {
                    return false;
                }
                ChestBoatEntity boat = EntityType.CHEST_BOAT.create(w);
                if (boat == null) {
                    return false;
                }
                boat.refreshPositionAndAngles(water.getX() + 0.5, water.getY() + 1, water.getZ() + 0.5, p.getRandom().nextFloat() * 360, 0);
                boat.setLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of("underdeep:exploration/raft")));
                boat.setLootTableSeed(w.random.nextLong());
                w.spawnEntity(boat);
                p.sendMessage(Text.literal("A small boat drifts out of the dark, riding low with cargo. Nobody aboard.").formatted(Formatting.AQUA), true);
                return true;
            }
            case "tremor" -> {
                p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_DIG, SoundCategory.AMBIENT, 0.8f, 0.5f);
                w.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.STONE.getDefaultState()), p.getX(),
                        p.getY() + 4, p.getZ(), 40, 5, 0.5, 5, 0);
                p.sendMessage(Text.literal("The ground trembles. Dust drifts down from above.").formatted(Formatting.GRAY), true);
                return true;
            }
            case "distant_bell" -> {
                Site bell = map == null ? null : map.sitesNear(p.getBlockX(), p.getBlockZ(), 400).stream()
                        .filter(s -> s.family().id().equals("silent_bell") || s.family().id().equals("silent_sanctum")).findFirst().orElse(null);
                if (bell == null) {
                    return false;
                }
                Vec3d dir = new Vec3d(bell.x() - p.getX(), 0, bell.z() - p.getZ()).normalize().multiply(12);
                w.playSound(null, p.getX() + dir.x, p.getY(), p.getZ() + dir.z, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.AMBIENT, 1.5f, 0.5f);
                p.sendMessage(Text.literal("Far off, to the " + Discovery.compass(dir.x, dir.z) + ", a bell tolls once.").formatted(Formatting.DARK_GRAY, Formatting.ITALIC),
                        false);
                return true;
            }
            case "lost_page" -> {
                if (spot == null) {
                    return false;
                }
                ItemEntity it = new ItemEntity(w, spot.getX() + 0.5, spot.getY() + 0.3, spot.getZ() + 0.5, ExploreItems.fragment(Lore.pickFor(p)));
                it.setNeverDespawn();
                w.spawnEntity(it);
                w.spawnParticles(ParticleTypes.ENCHANT, spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5, 20, 0.3, 0.5, 0.3, 0.3);
                p.sendMessage(Text.literal("A scrap of old writing lies caught between the stones nearby.").formatted(Formatting.GOLD), true);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    static void tickMoving(ServerWorld w) {
        for (Iterator<Moving> it = MOVING.iterator(); it.hasNext(); ) {
            Moving mv = it.next();
            if (mv.w != w) {
                continue;
            }
            ServerPlayerEntity p = (ServerPlayerEntity) w.getPlayerByUuid(mv.player);
            if (--mv.seconds <= 0 || p == null) {
                it.remove();
                continue;
            }
            // advance up to 3 blocks, waiting while the player falls behind
            if (p.getPos().distanceTo(mv.pos) < 9) {
                Vec3d step = mv.target.subtract(mv.pos);
                double len = step.length();
                if (len < 4) {
                    it.remove();
                    continue;
                }
                mv.pos = mv.pos.add(step.multiply(Math.min(3, len) / len));
            }
            if (mv.ghost) {
                for (int i = 0; i < 3; i++) {
                    w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, mv.pos.x + i * 0.8, mv.pos.y, mv.pos.z, 3, 0.05, 0.1, 0.05, 0.005);
                }
            } else {
                w.spawnParticles(new DustParticleEffect(new Vector3f(0.7f, 0.95f, 1f), 1.6f), mv.pos.x, mv.pos.y, mv.pos.z, 6, 0.1, 0.1, 0.1, 0);
                w.spawnParticles(ParticleTypes.END_ROD, mv.pos.x, mv.pos.y, mv.pos.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    // ------------------------------------------------------------------ helpers (bounded around one player)

    @Nullable
    static Site nearestUndiscovered(ExplorationMap map, ServerPlayerEntity p, int radius, boolean lanternOnly) {
        ExplorerData d = ExplorerData.of(p);
        for (Site s : map.sitesNear(p.getBlockX(), p.getBlockZ(), radius)) {
            ExploreContent.Family f = s.family();
            if (d.sites.containsKey(s.id()) || f.category() == ExploreContent.Category.SECRET || f.category() == ExploreContent.Category.DUNGEON) {
                continue;
            }
            if (lanternOnly != (f.id().equals("lantern_post") || f.id().equals("waystone") || f.id().equals("lantern_spire")
                    || f.id().equals("lantern_waystation"))) {
                continue;
            }
            return s;
        }
        return null;
    }

    static boolean insideSettlement(ServerWorld w, BlockPos pos) {
        SettlementMap sm = Settlements.of(w);
        return sm != null && sm.siteAt(pos.getX(), pos.getZ(), 24) != null;
    }

    /** A dry floor spot at distance [min,max] near the same height, outside settlements and away from homes. */
    @Nullable
    static BlockPos spot(ServerWorld w, BlockPos around, int min, int max) {
        for (int i = 0; i < 14; i++) {
            double a = w.random.nextDouble() * Math.PI * 2, r = min + w.random.nextDouble() * (max - min);
            int x = around.getX() + (int) Math.round(Math.cos(a) * r), z = around.getZ() + (int) Math.round(Math.sin(a) * r);
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos q = new BlockPos(x, around.getY() + dy, z);
                if (w.getBlockState(q).isAir() && w.getBlockState(q.up()).isAir() && w.getBlockState(q.down()).isSideSolidFullSquare(w, q.down(), Direction.UP)
                        && w.getFluidState(q).isEmpty() && !insideSettlement(w, q) && !homely(w, q)) {
                    return q;
                }
            }
        }
        return null;
    }

    static boolean clear(ServerWorld w, BlockPos q) {
        return w.getBlockState(q).isAir() && natural(w.getBlockState(q.down())) || w.getBlockState(q.down()).isIn(BlockTags.BASE_STONE_OVERWORLD)
                || w.getBlockState(q.down()).isIn(BlockTags.DIRT);
    }

    /** Containers, beds, doors or warding bells nearby mean someone lives here: never place things there. */
    static boolean homely(ServerWorld w, BlockPos q) {
        if (Explorations.warded(w, q)) {
            return true;
        }
        for (BlockPos b : BlockPos.iterate(q.add(-3, -2, -3), q.add(3, 2, 3))) {
            BlockState s = w.getBlockState(b);
            if (s.isIn(BlockTags.BEDS) || s.isIn(BlockTags.DOORS) || s.getBlock() instanceof net.minecraft.block.BlockWithEntity) {
                return true;
            }
        }
        return false;
    }

    static boolean natural(BlockState s) {
        return s.isIn(BlockTags.BASE_STONE_OVERWORLD) || s.isOf(Blocks.CALCITE) || s.isOf(Blocks.SMOOTH_BASALT) || s.isOf(Blocks.BASALT)
                || s.isOf(Blocks.BLACKSTONE) || s.isOf(Blocks.PACKED_ICE) || s.isOf(ModBlocks.STORMSTONE) || s.isOf(ModBlocks.STARSTONE);
    }

    @Nullable
    static BlockPos ceiling(ServerWorld w, BlockPos spot, int max) {
        for (int y = 2; y <= max; y++) {
            BlockPos q = spot.up(y);
            if (!w.getBlockState(q).isAir()) {
                return natural(w.getBlockState(q)) ? q.down() : null;
            }
        }
        return null;
    }

    @Nullable
    static BlockPos wallNear(ServerWorld w, BlockPos p, int r) {
        for (int i = 0; i < 20; i++) {
            BlockPos q = p.add(w.random.nextInt(r * 2 + 1) - r, w.random.nextInt(5) - 1, w.random.nextInt(r * 2 + 1) - r);
            if (natural(w.getBlockState(q))) {
                for (Direction d : Direction.Type.HORIZONTAL) {
                    if (w.getBlockState(q.offset(d)).isAir() && !insideSettlement(w, q) && !homely(w, q)) {
                        return q;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    static BlockPos waterNear(ServerWorld w, BlockPos p, int r) {
        for (int i = 0; i < 24; i++) {
            BlockPos q = p.add(w.random.nextInt(r * 2 + 1) - r, w.random.nextInt(7) - 4, w.random.nextInt(r * 2 + 1) - r);
            if (w.getFluidState(q).isStill() && w.getBlockState(q).isOf(Blocks.WATER) && w.getBlockState(q.up()).isAir() && w.getBlockState(q.down()).isOf(Blocks.WATER)) {
                return q;
            }
        }
        return null;
    }

    static void loot(ServerWorld w, BlockPos pos, String table) {
        if (w.getBlockEntity(pos) instanceof LootableContainerBlockEntity lc) {
            lc.setLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(table)), w.random.nextLong());
        }
    }
}
