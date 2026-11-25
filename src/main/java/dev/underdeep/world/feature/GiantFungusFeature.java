package dev.underdeep.world.feature;

import dev.underdeep.registry.ModPlants;
import dev.underdeep.registry.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Giant fungi of the Fungal Expanse, three distinct growth forms:
 * <ul>
 *   <li>sporestalk: very tall thin stem, small rounded cap with a glowing gill ring</li>
 *   <li>bellcap: leaning stem under a hollow bell-shaped cap hanging down around it</li>
 *   <li>shelfcap: stem carrying several half-disc shelves on alternating sides</li>
 * </ul>
 */
public final class GiantFungusFeature extends Feature<SpeciesFeatureConfig> {
    public GiantFungusFeature() {
        super(SpeciesFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<SpeciesFeatureConfig> ctx) {
        StructureWorldAccess w = ctx.getWorld();
        BlockPos o = ctx.getOrigin();
        Random r = ctx.getRandom();
        if (!w.getBlockState(o.down()).isIn(ModTags.FUNGAL_SOIL)) {
            return false;
        }
        double s = ctx.getConfig().scale() * (0.85 + r.nextDouble() * 0.3);
        PlantBuilder b = new PlantBuilder(w, o, 14);
        BlockState stem = ModPlants.FUNGAL_STEM.getDefaultState();
        BlockState gill = ModPlants.GLOWGILL.getDefaultState();
        switch (ctx.getConfig().species()) {
            case "sporestalk" -> {
                int h = (int) Math.round((10 + r.nextInt(8)) * s);
                for (int y = 0; y < h; y++) {
                    b.core(o.up(y), stem);
                }
                double rad = 2.2 + 0.8 * s;
                BlockState cap = ModPlants.SPORESTALK_CAP.getDefaultState();
                b.blob(o.up(h), rad, 1.4, rad, 0.8, cap, r);
                b.disc(o.up(h - 1), rad - 0.6, 0.9, gill, r);
            }
            case "bellcap" -> {
                int h = (int) Math.round((6 + r.nextInt(4)) * s);
                Direction lean = Direction.Type.HORIZONTAL.random(r);
                BlockPos top = o;
                for (int y = 0; y < h; y++) {
                    top = o.up(y).offset(lean, y > h * 0.6 ? 1 : 0);
                    b.core(top, stem);
                }
                BlockState cap = ModPlants.BELLCAP_CAP.getDefaultState();
                BlockPos c = top.up(1);
                b.disc(c, 2.0 * s, 0.9, cap, r);
                for (int dy = 1; dy <= (int) Math.round(3 * s); dy++) {
                    double rr = 2.6 * s + dy * 0.25;
                    for (int x = (int) -rr - 1; x <= rr + 1; x++) {
                        for (int z = (int) -rr - 1; z <= rr + 1; z++) {
                            double d = Math.sqrt(x * x + z * z);
                            if (d <= rr && d > rr - 1.2) {
                                b.leaf(c.add(x, -dy + 1, z), cap);
                            }
                        }
                    }
                }
                b.disc(c.down(), 1.6 * s, 0.8, gill, r);
            }
            case "shelfcap" -> {
                int h = (int) Math.round((8 + r.nextInt(6)) * s);
                for (int y = 0; y < h; y++) {
                    b.core(o.up(y), stem);
                }
                BlockState cap = ModPlants.SHELFCAP_CAP.getDefaultState();
                Direction side = Direction.Type.HORIZONTAL.random(r);
                for (int y = (int) (h * 0.35); y <= h; y += 3) {
                    double rad = 2.5 + r.nextDouble() * 1.5 * s;
                    for (int x = (int) -rad; x <= rad; x++) {
                        for (int z = (int) -rad; z <= rad; z++) {
                            BlockPos off = new BlockPos(x, 0, z);
                            double along = x * side.getOffsetX() + z * side.getOffsetZ();
                            if (along >= 0 && Math.sqrt(x * x + z * z) <= rad) {
                                b.leaf(o.up(y).add(off), cap);
                                if (r.nextFloat() < 0.3f && along > 0) {
                                    b.leaf(o.up(y - 1).add(off), gill);
                                }
                            }
                        }
                    }
                    side = side.getOpposite().rotateYClockwise();
                }
                b.blob(o.up(h + 1), 2.0, 1.0, 2.0, 0.7, cap, r);
            }
            default -> {
                return false;
            }
        }
        return b.commit();
    }
}
