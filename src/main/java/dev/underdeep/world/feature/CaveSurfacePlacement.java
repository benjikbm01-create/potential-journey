package dev.underdeep.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.underdeep.registry.ModWorldgen;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Placement modifier that finds a real cavern surface in a multi-level world.
 * <p>
 * Starting at a random height in [min_y, max_y] in the column of the incoming position, it scans up
 * to {@code max_scan} blocks for the requested surface. Because the scan starts at a uniformly random
 * height, tall caverns (long air columns) receive proportionally more decoration than thin tunnels.
 * <p>
 * JSON: {@code {"type": "underdeep:cave_surface", "surface": "floor", "min_y": 100, "max_y": 300}}
 */
public final class CaveSurfacePlacement extends PlacementModifier {
    public enum Surface implements StringIdentifiable {
        /** Air block directly above a solid block. */
        FLOOR("floor"),
        /** Air block directly below a solid block. */
        CEILING("ceiling"),
        /** Water block directly above a solid block (lake and river beds). */
        WATER_FLOOR("water_floor"),
        /** Air block directly above a water source (lily pads). */
        WATER_SURFACE("water_surface"),
        /** Air block horizontally adjacent to a solid block (walls). */
        WALL("wall");

        public static final Codec<Surface> CODEC = StringIdentifiable.createCodec(Surface::values);
        private final String id;

        Surface(String id) {
            this.id = id;
        }

        @Override
        public String asString() {
            return id;
        }
    }

    public static final MapCodec<CaveSurfacePlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Surface.CODEC.fieldOf("surface").forGetter(p -> p.surface),
            Codec.INT.fieldOf("min_y").forGetter(p -> p.minY),
            Codec.INT.fieldOf("max_y").forGetter(p -> p.maxY),
            Codec.intRange(1, 384).optionalFieldOf("max_scan", 96).forGetter(p -> p.maxScan)
    ).apply(i, CaveSurfacePlacement::new));

    private final Surface surface;
    private final int minY;
    private final int maxY;
    private final int maxScan;

    public CaveSurfacePlacement(Surface surface, int minY, int maxY, int maxScan) {
        this.surface = surface;
        this.minY = minY;
        this.maxY = Math.max(minY, maxY);
        this.maxScan = maxScan;
    }

    private static boolean solid(FeaturePlacementContext ctx, BlockPos pos) {
        BlockState s = ctx.getBlockState(pos);
        return s.isSolidBlock(ctx.getWorld(), pos);
    }

    @Override
    public Stream<BlockPos> getPositions(FeaturePlacementContext ctx, Random random, BlockPos pos) {
        return find(ctx, random, pos).filter(p -> clearOfDescents(ctx, p));
    }

    /** Stage 10: nothing grows inside a descent cave (trees, fungi or spires would block the walkway). */
    static boolean clearOfDescents(FeaturePlacementContext ctx, BlockPos p) {
        dev.underdeep.world.terrain.UnderdeepTerrain t = dev.underdeep.world.UnderdeepWorlds.terrain(ctx.getWorld().toServerWorld());
        if (t == null || !t.hasCaveNetwork()) {
            return true;
        }
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                dev.underdeep.world.terrain.TerrainColumn c = t.column(p.getX() + dx, p.getZ() + dz);
                for (int i = 0; i < c.bandCount; i++) {
                    if (p.getY() >= c.bandFloor[i] - 2 && p.getY() <= c.bandCeil[i] + 2) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private Stream<BlockPos> find(FeaturePlacementContext ctx, Random random, BlockPos pos) {
        // Stage 4: no natural decoration on settlement footprints (roads, plazas, buildings + margin), so
        // features of neighbouring chunks can never grow into buildings regardless of generation order
        dev.underdeep.civ.settlement.SettlementMap settlements = dev.underdeep.civ.settlement.Settlements.of(ctx.getWorld());
        if (settlements != null && settlements.reserved(pos.getX(), pos.getZ())) {
            return Stream.empty();
        }
        int y = minY + random.nextInt(maxY - minY + 1);
        BlockPos.Mutable m = new BlockPos.Mutable(pos.getX(), y, pos.getZ());
        int bottom = ctx.getBottomY() + 1;
        int top = ctx.getBottomY() + ctx.getHeight() - 2;
        switch (surface) {
            case FLOOR, WATER_FLOOR, WATER_SURFACE -> {
                for (int i = 0; i < maxScan && m.getY() > bottom; i++, m.move(Direction.DOWN)) {
                    BlockState s = ctx.getBlockState(m);
                    boolean match = switch (surface) {
                        case FLOOR -> s.isAir();
                        case WATER_FLOOR -> s.getFluidState().isOf(Fluids.WATER) && s.getFluidState().isStill();
                        default -> s.isAir();
                    };
                    if (!match) {
                        continue;
                    }
                    BlockPos below = m.down();
                    if (surface == Surface.WATER_SURFACE) {
                        BlockState b = ctx.getBlockState(below);
                        if (b.getFluidState().isOf(Fluids.WATER) && b.getFluidState().isStill()) {
                            return Stream.of(m.toImmutable());
                        }
                    } else if (solid(ctx, below)) {
                        return Stream.of(m.toImmutable());
                    }
                }
            }
            case CEILING -> {
                for (int i = 0; i < maxScan && m.getY() < top; i++, m.move(Direction.UP)) {
                    if (ctx.getBlockState(m).isAir() && solid(ctx, m.up())) {
                        return Stream.of(m.toImmutable());
                    }
                }
            }
            case WALL -> {
                for (int i = 0; i < maxScan && m.getY() > bottom; i++, m.move(Direction.DOWN)) {
                    if (!ctx.getBlockState(m).isAir()) {
                        continue;
                    }
                    for (Direction d : Direction.Type.HORIZONTAL) {
                        if (solid(ctx, m.offset(d))) {
                            return Stream.of(m.toImmutable());
                        }
                    }
                }
            }
        }
        return Stream.empty();
    }

    @Override
    public PlacementModifierType<?> getType() {
        return ModWorldgen.CAVE_SURFACE;
    }
}
