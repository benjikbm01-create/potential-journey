package dev.underdeep.world.feature;

import dev.underdeep.registry.ModBlocks;
import dev.underdeep.registry.ModPlants;
import dev.underdeep.registry.ModTags;
import dev.underdeep.registry.ModWood;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Underdeep tree families. Each species has its own growth algorithm (not one algorithm with
 * different leaves) plus controlled random variation; sizes young / normal / ancient scale it.
 * <ul>
 *   <li>duskoak: branching broad forest tree; ancient specimens get a 2x2 trunk and root flares</li>
 *   <li>spirebark: tall narrow trunk with stacked narrow foliage rings</li>
 *   <li>goldbough: short trunk splitting into arms under a broad flattened dome, resin knots</li>
 *   <li>mirewillow: buttress roots, rounded crown and hanging leaf curtains</li>
 *   <li>frostfir: conical cold-adapted conifer with tiered skirts</li>
 *   <li>bloodthorn: twisted wandering trunk with crooked limbs and sparse clumps</li>
 *   <li>lumenwood: slender curved trunk under a flat glowing umbrella with dangling leaves</li>
 *   <li>cragpine: wind-bent leaning trunk with flat foliage pads blown downwind</li>
 *   <li>cinderbark: short gnarled volcanic tree with ember tufts</li>
 * </ul>
 * Works both for world generation and for sapling growth; never replaces solid blocks (see {@link PlantBuilder}).
 */
public final class UnderdeepTreeFeature extends Feature<SpeciesFeatureConfig> {
    public UnderdeepTreeFeature() {
        super(SpeciesFeatureConfig.CODEC);
    }

    record Kit(BlockState log, BlockState leaves, TagKey<Block> soil) {
    }

    static Kit kit(String species) {
        return switch (species) {
            case "duskoak" -> new Kit(ModWood.DUSKOAK.log.getDefaultState(), ModWood.DUSKOAK.leaves.getDefaultState(), ModTags.PLANT_SOIL);
            case "spirebark" -> new Kit(ModWood.SPIREBARK.log.getDefaultState(), ModWood.SPIREBARK.leaves.getDefaultState(), ModTags.MINERAL_SOIL);
            case "goldbough" -> new Kit(ModWood.GOLDWOOD.log.getDefaultState(), ModBlocks.GOLDEN_LEAVES.getDefaultState(), ModTags.PLANT_SOIL);
            case "mirewillow" -> new Kit(ModWood.MIREWILLOW.log.getDefaultState(), ModWood.MIREWILLOW.leaves.getDefaultState(), ModTags.WET_SOIL);
            case "frostfir" -> new Kit(ModWood.FROSTFIR.log.getDefaultState(), ModWood.FROSTFIR.leaves.getDefaultState(), ModTags.FROZEN_SOIL);
            case "bloodthorn" -> new Kit(ModWood.BLOODTHORN.log.getDefaultState(), ModWood.BLOODTHORN.leaves.getDefaultState(), ModTags.VOLCANIC_SOIL);
            case "lumenwood" -> new Kit(ModWood.LUMENWOOD.log.getDefaultState(), ModWood.LUMENWOOD.leaves.getDefaultState(), ModTags.PLANT_SOIL);
            case "cragpine" -> new Kit(ModWood.SPIREBARK.log.getDefaultState(), ModWood.CRAGPINE_LEAVES.getDefaultState(), ModTags.MINERAL_SOIL);
            case "cinderbark" -> new Kit(ModWood.CINDERBARK_LOG.getDefaultState(), ModWood.CINDER_LEAVES.getDefaultState(), ModTags.VOLCANIC_SOIL);
            default -> throw new IllegalArgumentException("unknown tree species " + species);
        };
    }

    @Override
    public boolean generate(FeatureContext<SpeciesFeatureConfig> ctx) {
        StructureWorldAccess world = ctx.getWorld();
        BlockPos origin = ctx.getOrigin();
        Random r = ctx.getRandom();
        SpeciesFeatureConfig cfg = ctx.getConfig();
        Kit kit = kit(cfg.species());
        BlockState below = world.getBlockState(origin.down());
        if (!below.isIn(kit.soil())) {
            return false;
        }
        double s = cfg.scale() * (0.85 + r.nextDouble() * 0.3);
        PlantBuilder b = new PlantBuilder(world, origin, 15);
        switch (cfg.species()) {
            case "duskoak" -> duskoak(b, origin, kit, s, r);
            case "spirebark" -> spirebark(b, origin, kit, s, r);
            case "goldbough" -> goldbough(b, origin, kit, s, r);
            case "mirewillow" -> mirewillow(b, origin, kit, s, r);
            case "frostfir" -> frostfir(b, origin, kit, s, r);
            case "bloodthorn" -> bloodthorn(b, origin, kit, s, r);
            case "lumenwood" -> lumenwood(b, origin, kit, s, r);
            case "cragpine" -> cragpine(b, origin, kit, s, r);
            case "cinderbark" -> cinderbark(b, origin, kit, s, r);
            default -> {
                return false;
            }
        }
        if (!hasRoom(world, origin, (int) Math.min(6, 2 + 3 * s))) {
            return false;
        }
        return b.commit();
    }

    /** Requires open space above the base so saplings in cramped tunnels stay saplings. */
    private static boolean hasRoom(StructureWorldAccess w, BlockPos o, int h) {
        for (int i = 1; i <= h; i++) {
            BlockState st = w.getBlockState(o.up(i));
            if (!(st.isAir() || st.isReplaceable())) {
                return false;
            }
        }
        return true;
    }

    private static int ri(Random r, int a, int b) {
        return a + r.nextInt(Math.max(1, b - a + 1));
    }

    // ------------------------------------------------------------------ species

    private static void duskoak(PlantBuilder b, BlockPos o, Kit k, double s, Random r) {
        int h = (int) Math.round((6 + r.nextInt(4)) * s);
        boolean thick = s > 1.3;
        for (int y = 0; y < h; y++) {
            b.core(o.up(y), k.log());
            if (thick) {
                b.wood(o.add(1, y, 0), k.log());
                b.wood(o.add(0, y, 1), k.log());
                b.wood(o.add(1, y, 1), k.log());
            }
        }
        if (thick) {
            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockPos root = o.offset(d, d.getDirection() == Direction.AxisDirection.POSITIVE ? 2 : 1);
                b.line(root.up(ri(r, 1, 3)), root.offset(d, ri(r, 1, 2)).down(1), k.log(), false);
            }
        }
        int branches = 3 + r.nextInt(3) + (thick ? 2 : 0);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < branches; i++) {
            double ang = a0 + i * (Math.PI * 2 / branches) + (r.nextDouble() - 0.5) * 0.6;
            int y0 = (int) (h * (0.45 + r.nextDouble() * 0.45));
            double len = (3 + r.nextInt(3)) * s;
            BlockPos start = o.up(y0);
            BlockPos end = start.add((int) Math.round(Math.cos(ang) * len), (int) Math.round(len * (0.35 + r.nextDouble() * 0.4)),
                    (int) Math.round(Math.sin(ang) * len));
            b.line(start, end, k.log(), false);
            double rad = 2.0 + r.nextDouble() * 1.2 * Math.sqrt(s);
            b.blob(end.up(1), rad, rad * 0.65, rad, 0.65, k.leaves(), r);
        }
        double crown = 2.6 * Math.pow(s, 0.75);
        b.blob(o.up(h), crown, crown * 0.7, crown, 0.6, k.leaves(), r);
    }

    private static void spirebark(PlantBuilder b, BlockPos o, Kit k, double s, Random r) {
        int h = (int) Math.round((13 + r.nextInt(7)) * s);
        for (int y = 0; y < h; y++) {
            b.core(o.up(y), k.log());
        }
        int start = (int) (h * 0.35);
        for (int y = start; y <= h + 1; y++) {
            double t = (double) (y - start) / Math.max(1, h + 1 - start);
            double rad = 0.8 + (1 - t) * 1.9 * Math.sqrt(s);
            if ((y - start) % 3 == 2 && t < 0.9) {
                rad *= 0.55;
            }
            b.disc(o.up(y), rad, 0.55, k.leaves(), r);
            if ((y - start) % 3 == 0 && t < 0.8 && rad > 1.6) {
                Direction d = Direction.Type.HORIZONTAL.random(r);
                b.wood(o.up(y).offset(d), k.log().with(net.minecraft.block.PillarBlock.AXIS, d.getAxis()));
            }
        }
        b.leaf(o.up(h + 2), k.leaves());
    }

    private static void goldbough(PlantBuilder b, BlockPos o, Kit k, double s, Random r) {
        int h0 = (int) Math.round((4 + r.nextInt(3)) * s);
        for (int y = 0; y < h0; y++) {
            b.core(o.up(y), k.log());
        }
        int arms = 2 + r.nextInt(2);
        double a0 = r.nextDouble() * Math.PI * 2;
        int top = h0;
        for (int i = 0; i < arms; i++) {
            double ang = a0 + i * Math.PI * 2 / arms;
            double reach = (2 + r.nextDouble() * 2) * s;
            int rise = (int) Math.round((3 + r.nextInt(3)) * s);
            BlockPos end = o.up(h0).add((int) Math.round(Math.cos(ang) * reach), rise, (int) Math.round(Math.sin(ang) * reach));
            b.line(o.up(h0 - 1), end, k.log(), false);
            top = Math.max(top, end.getY() - o.getY());
        }
        double rx = (4.2 + r.nextDouble() * 1.3) * Math.pow(s, 0.8);
        double ry = 2.2 * Math.pow(s, 0.7);
        BlockPos c = o.up(top + 1);
        b.blob(c, rx, ry, rx, 0.7, k.leaves(), r);
        int hangs = (int) (rx * 3);
        for (int i = 0; i < hangs; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            BlockPos rim = c.add((int) Math.round(Math.cos(ang) * (rx - 0.6)), -1, (int) Math.round(Math.sin(ang) * (rx - 0.6)));
            b.hang(rim, ri(r, 1, 3), k.leaves());
        }
        if (r.nextFloat() < 0.6f) {
            Direction d = Direction.Type.HORIZONTAL.random(r);
            BlockPos knot = o.up(ri(r, 1, Math.max(1, h0 - 1))).offset(d);
            b.leaf(knot, ModPlants.RESIN_KNOT.getDefaultState().with(HorizontalFacingBlock.FACING, d)
                    .with(dev.underdeep.block.plant.ResinKnotBlock.AGE, r.nextInt(3)));
        }
    }

    private static void mirewillow(PlantBuilder b, BlockPos o, Kit k, double s, Random r) {
        int h = (int) Math.round((5 + r.nextInt(3)) * s);
        Direction lean = Direction.Type.HORIZONTAL.random(r);
        BlockPos topPos = o;
        for (int y = 0; y < h; y++) {
            BlockPos p = o.up(y).offset(lean, y > h / 2 ? 1 : 0);
            b.core(p, k.log());
            topPos = p;
        }
        for (Direction d : Direction.Type.HORIZONTAL) {
            if (r.nextFloat() < 0.8f) {
                b.line(o.up(ri(r, 1, 2)), o.offset(d, 2).down(1), k.log(), false);
            }
        }
        double rx = 3.4 * Math.pow(s, 0.85), ry = 2.4 * Math.pow(s, 0.75);
        BlockPos c = topPos.up(1);
        b.blob(c, rx, ry, rx, 0.7, k.leaves(), r);
        for (int x = (int) -rx; x <= rx; x++) {
            for (int z = (int) -rx; z <= rx; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > rx - 1.4 && d <= rx && r.nextFloat() < 0.55f) {
                    b.hang(c.add(x, (int) -(ry * 0.4), z), ri(r, 2, (int) (5 * s)), k.leaves());
                }
            }
        }
    }

    private static void frostfir(PlantBuilder b, BlockPos o, Kit k, double s, Random r) {
        int h = (int) Math.round((8 + r.nextInt(5)) * s);
        for (int y = 0; y < h; y++) {
            b.core(o.up(y), k.log());
        }
        int base = Math.max(2, (int) (h * 0.2));
        for (int y = base; y <= h; y++) {
            double t = (double) (y - base) / Math.max(1, h - base);
            double rad = (1 - t) * (2.2 + 1.3 * s) + 0.6;
            if ((y - base) % 2 == 1) {
                rad *= 0.6;
            }
            b.disc(o.up(y), rad, 0.5, k.leaves(), r);
        }
        b.leaf(o.up(h + 1), k.leaves());
        b.leaf(o.up(h + 2), k.leaves());
    }

    private static void bloodthorn(PlantBuilder b, BlockPos o, Kit k, double s, Random r) {
        int h = (int) Math.round((6 + r.nextInt(4)) * s);
        BlockPos p = o;
        for (int y = 0; y < h; y++) {
            BlockPos next = o.up(y).add(p.getX() - o.getX(), 0, p.getZ() - o.getZ());
            if (y > 1 && r.nextFloat() < 0.4f) {
                Direction d = Direction.Type.HORIZONTAL.random(r);
                b.core(next, k.log());
                next = next.offset(d);
            }
            b.core(next, k.log());
            p = next;
        }
        int limbs = 2 + r.nextInt(3);
        for (int i = 0; i < limbs; i++) {
            BlockPos cur = p.down(r.nextInt(Math.max(1, h / 3)));
            Direction d = Direction.Type.HORIZONTAL.random(r);
            int len = ri(r, 3, (int) (3 + 2 * s));
            for (int j = 0; j < len; j++) {
                cur = r.nextFloat() < 0.45f ? cur.up() : cur.offset(r.nextFloat() < 0.7f ? d : d.rotateYClockwise());
                b.wood(cur, k.log());
            }
            b.blob(cur.up(), 1.6, 1.2, 1.6, 0.55, k.leaves(), r);
        }
        b.blob(p.up(), 1.8, 1.2, 1.8, 0.5, k.leaves(), r);
    }

    private static void lumenwood(PlantBuilder b, BlockPos o, Kit k, double s, Random r) {
        int h = (int) Math.round((7 + r.nextInt(4)) * s);
        double ang = r.nextDouble() * Math.PI * 2;
        double bend = 2.0 + r.nextDouble() * 1.5 * s;
        BlockPos top = o;
        for (int y = 0; y < h; y++) {
            double t = (double) y / h;
            BlockPos p = o.add((int) Math.round(Math.cos(ang) * bend * t * t), y, (int) Math.round(Math.sin(ang) * bend * t * t));
            if (top != o && top.getManhattanDistance(p) > 1) {
                b.core(new BlockPos(p.getX(), top.getY(), p.getZ()), k.log());
            }
            b.core(p, k.log());
            top = p;
        }
        double rad = 3.2 * Math.pow(s, 0.85) + r.nextDouble();
        b.disc(top.up(1), rad, 0.6, k.leaves(), r);
        b.disc(top.up(2), rad * 0.55, 0.6, k.leaves(), r);
        for (int i = 0; i < (int) (rad * 3); i++) {
            double a = r.nextDouble() * Math.PI * 2;
            BlockPos rim = top.up(1).add((int) Math.round(Math.cos(a) * (rad - 0.5)), 0, (int) Math.round(Math.sin(a) * (rad - 0.5)));
            b.hang(rim, ri(r, 1, 2), k.leaves());
        }
    }

    private static void cragpine(PlantBuilder b, BlockPos o, Kit k, double s, Random r) {
        int h = (int) Math.round((5 + r.nextInt(3)) * s);
        Direction wind = Direction.Type.HORIZONTAL.random(r);
        BlockPos top = o;
        int prevOff = 0;
        for (int y = 0; y < h; y++) {
            int off = (int) Math.round(Math.pow((double) y / h, 1.5) * 3 * s);
            BlockPos p = o.up(y).offset(wind, off);
            if (off != prevOff) {
                b.core(o.up(y - 1).offset(wind, off), k.log());
            }
            b.core(p, k.log());
            top = p;
            prevOff = off;
        }
        BlockPos pad = top.up().offset(wind, 1);
        b.blob(pad, 3.0 * Math.sqrt(s), 1.1, 2.2 * Math.sqrt(s), 0.65, k.leaves(), r);
        BlockPos low = o.up((int) (h * 0.6)).offset(wind, 2);
        b.wood(low.offset(wind.getOpposite()), k.log().with(net.minecraft.block.PillarBlock.AXIS, wind.getAxis()));
        b.blob(low, 2.0, 0.9, 1.6, 0.6, k.leaves(), r);
    }

    private static void cinderbark(PlantBuilder b, BlockPos o, Kit k, double s, Random r) {
        int h = (int) Math.round((3 + r.nextInt(3)) * s);
        for (int y = 0; y < h; y++) {
            b.core(o.up(y), k.log());
        }
        for (Direction d : Direction.Type.HORIZONTAL) {
            if (r.nextFloat() < 0.6f) {
                b.wood(o.offset(d), k.log());
            }
        }
        int limbs = 2 + r.nextInt(2);
        for (int i = 0; i < limbs; i++) {
            Direction d = Direction.Type.HORIZONTAL.random(r);
            BlockPos start = o.up(h - 1 - r.nextInt(2));
            BlockPos end = start.offset(d, ri(r, 2, 3)).up(ri(r, 0, 2));
            b.line(start, end, k.log(), false);
            b.blob(end.up(), 1.3, 1.0, 1.3, 0.45, k.leaves(), r);
        }
        b.blob(o.up(h), 1.5, 1.0, 1.5, 0.5, k.leaves(), r);
    }

    static int clampI(int v, int lo, int hi) {
        return MathHelper.clamp(v, lo, hi);
    }
}
