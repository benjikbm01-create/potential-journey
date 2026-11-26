package dev.underdeep.civ.settlement;

import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.arch.BuildingKind;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * The full layout of one settlement, computed purely from the site and the terrain model
 * ({@link SettlementPlanner}). Every chunk that intersects the settlement reads the same plan.
 */
public record SettlementPlan(SettlementSite site, String name, String layout, List<Building> buildings, List<Road> roads,
                             List<Plaza> plazas, List<BlockPos> lamps, BlockBox bounds) {

    /** A building placed in the world. {@code origin} is the rotated footprint's min corner at floor level. */
    public record Building(int index, BuildingKind kind, int variant, String profession, String district, Blueprint blueprint,
                           BlockRotation rotation, BlockPos origin, BlockPos entrance, BlockBox box) {
        /** World position of a blueprint-local marker. */
        public BlockPos world(BlockPos local) {
            BlockPos o = blueprint.transform(local.getX(), local.getY(), local.getZ(), rotation);
            return origin.add(o);
        }
    }

    /**
     * A straight road segment with a height profile: {@code heights[i]} is the surface (path block) height at
     * distance i * {@link #STEP} from (x1, z1). Bridges are segments where the profile rises above the ground.
     */
    public record Road(double x1, double z1, double x2, double z2, double width, double[] heights, boolean main) {
        public static final double STEP = 4.0;

        public double length() {
            return Math.hypot(x2 - x1, z2 - z1);
        }

        /** Parameter t in [0, 1] of the closest point and the squared distance to it. */
        public double[] project(double px, double pz) {
            double vx = x2 - x1, vz = z2 - z1;
            double len2 = vx * vx + vz * vz;
            double t = len2 > 0 ? ((px - x1) * vx + (pz - z1) * vz) / len2 : 0;
            t = Math.max(0, Math.min(1, t));
            double cx = x1 + vx * t - px, cz = z1 + vz * t - pz;
            return new double[]{t, cx * cx + cz * cz};
        }

        /** Surface height (fractional) at parameter t. */
        public double heightAt(double t) {
            double pos = t * length() / STEP;
            int i = (int) Math.floor(pos);
            if (i >= heights.length - 1) {
                return heights[heights.length - 1];
            }
            double f = pos - i;
            return heights[i] * (1 - f) + heights[i + 1] * f;
        }
    }

    public record Plaza(int x, int z, int radius, int y) {
    }

    public int buildingCount() {
        int n = 0;
        for (Building b : buildings) {
            n += b.kind().countsAsBuilding() ? 1 : 0;
        }
        return n;
    }

    public int beds() {
        int n = 0;
        for (Building b : buildings) {
            for (Blueprint.Marker m : b.blueprint().markers()) {
                n += m.type() == Blueprint.MarkerType.BED ? 1 : 0;
            }
        }
        return n;
    }
}
