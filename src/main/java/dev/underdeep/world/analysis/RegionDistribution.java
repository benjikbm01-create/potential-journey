package dev.underdeep.world.analysis;

import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Measures how ecosystems are distributed in the explorable cavern space of a layer.
 * <p>
 * Only columns that actually contain open cavern space in the layer are counted (so rock between
 * caverns does not inflate or connect regions). Contiguous regions are 4-connected components of
 * same-ecosystem open columns on a grid; size is reported as the equivalent diameter
 * {@code 2*sqrt(area/pi)} and as the larger bounding-box extent.
 * Pure model analysis: no chunks are generated (the chunk generator uses the same functions).
 */
public final class RegionDistribution {
    private RegionDistribution() {
    }

    public record Region(RegionType type, int cells, double equivalentDiameter, int extent, int centerX, int centerZ) {
    }

    public record Report(CavernLayer layer, int radius, int step, List<Region> regions,
                         Map<RegionType, Integer> areaCells, int openCells,
                         Set<RegionType> within500, Set<RegionType> within1000, Set<RegionType> within2500,
                         Map<RegionType, Integer> firstDistance, double meanRunLength, double changesPer1000) {

        /** Ecosystems having a contiguous region >= 60 blocks across whose centre lies within the radius. */
        public Set<RegionType> meaningfulWithin(int r) {
            Set<RegionType> set = EnumSet.noneOf(RegionType.class);
            for (Region reg : regions) {
                if (reg.equivalentDiameter >= 60
                        && (double) reg.centerX * reg.centerX + (double) reg.centerZ * reg.centerZ <= (double) r * r) {
                    set.add(reg.type);
                }
            }
            return set;
        }

        /** Distance from the origin to the centre of the nearest meaningful region of each ecosystem. */
        public Map<RegionType, Integer> meaningfulFirstDistance() {
            Map<RegionType, Integer> m = new EnumMap<>(RegionType.class);
            for (Region reg : regions) {
                if (reg.equivalentDiameter >= 60) {
                    m.merge(reg.type, (int) Math.sqrt((double) reg.centerX * reg.centerX + (double) reg.centerZ * reg.centerZ), Math::min);
                }
            }
            return m;
        }

        public String summary() {
            List<Double> d = regions.stream().filter(r -> r.cells >= 4).map(Region::equivalentDiameter).sorted().toList();
            List<Integer> e = regions.stream().filter(r -> r.cells >= 4).map(Region::extent).sorted().toList();
            if (d.isEmpty()) {
                return "no regions";
            }
            return String.format(Locale.ROOT,
                    "%d regions (>=4 cells); equivalent diameter p10/p50/p90/max = %.0f/%.0f/%.0f/%.0f blocks; "
                            + "extent p50/p90/max = %d/%d/%d; ecosystems touching 500/1000/2500 = %d/%d/%d; "
                            + "with a >=60-block region centred within 500/1000/2500 = %d/%d/%d; "
                            + "mean run length along straight lines %.0f blocks (%.1f changes per 1000 blocks)",
                    d.size(), d.get(d.size() / 10), d.get(d.size() / 2), d.get(d.size() * 9 / 10), d.get(d.size() - 1),
                    e.get(e.size() / 2), e.get(e.size() * 9 / 10), e.get(e.size() - 1),
                    within500.size(), within1000.size(), within2500.size(), meaningfulWithin(500).size(),
                    meaningfulWithin(1000).size(), meaningfulWithin(2500).size(), meanRunLength, changesPer1000);
        }

        /** Size class counts using the brief's categories. */
        public String sizeClasses() {
            int small = 0, standard = 0, large = 0, exceptional = 0, huge = 0, tiny = 0;
            for (Region r : regions) {
                if (r.cells < 4) {
                    continue;
                }
                double s = r.equivalentDiameter;
                if (s < 60) {
                    tiny++;
                } else if (s < 120) {
                    small++;
                } else if (s < 250) {
                    standard++;
                } else if (s < 400) {
                    large++;
                } else if (s < 700) {
                    exceptional++;
                } else {
                    huge++;
                }
            }
            return String.format(Locale.ROOT, "<60: %d, 60-120: %d, 120-250: %d, 250-400: %d, 400-700: %d, >700: %d",
                    tiny, small, standard, large, exceptional, huge);
        }

        public String frequencies() {
            StringBuilder sb = new StringBuilder();
            areaCells.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).forEach(en ->
                    sb.append(en.getKey().id()).append(String.format(Locale.ROOT, " %.1f%%, ", en.getValue() * 100.0 / Math.max(1, openCells))));
            return sb.toString();
        }
    }

    public static Report analyze(UnderdeepTerrain t, CavernLayer layer, int radius, int step) {
        int n = radius * 2 / step + 1;
        byte[] grid = new byte[n * n];
        boolean upper = layer == CavernLayer.UPPER;
        Map<RegionType, Integer> area = new EnumMap<>(RegionType.class);
        Map<RegionType, Integer> first = new EnumMap<>(RegionType.class);
        Set<RegionType> w500 = EnumSet.noneOf(RegionType.class);
        Set<RegionType> w1000 = EnumSet.noneOf(RegionType.class);
        Set<RegionType> w2500 = EnumSet.noneOf(RegionType.class);
        int open = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int x = -radius + i * step;
                int z = -radius + j * step;
                TerrainColumn c = t.column(x, z);
                double gap = upper ? c.upperCeiling - c.upperFloor : c.deepCeiling - c.deepFloor;
                if (gap < 4) {
                    grid[i * n + j] = -1;
                    continue;
                }
                RegionType type = (upper ? c.upperRegion : c.deepRegion).primary;
                grid[i * n + j] = (byte) type.ordinal();
                open++;
                area.merge(type, 1, Integer::sum);
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d <= 500) {
                    w500.add(type);
                }
                if (d <= 1000) {
                    w1000.add(type);
                }
                if (d <= 2500) {
                    w2500.add(type);
                }
                first.merge(type, (int) d, Math::min);
            }
        }
        // Connected components
        List<Region> regions = new ArrayList<>();
        boolean[] seen = new boolean[n * n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        RegionType[] types = RegionType.values();
        for (int s = 0; s < n * n; s++) {
            if (seen[s] || grid[s] < 0) {
                continue;
            }
            byte ty = grid[s];
            int count = 0;
            int minI = n, maxI = 0, minJ = n, maxJ = 0;
            long sumI = 0, sumJ = 0;
            queue.add(s);
            seen[s] = true;
            while (!queue.isEmpty()) {
                int cur = queue.poll();
                int ci = cur / n;
                int cj = cur % n;
                count++;
                sumI += ci;
                sumJ += cj;
                minI = Math.min(minI, ci);
                maxI = Math.max(maxI, ci);
                minJ = Math.min(minJ, cj);
                maxJ = Math.max(maxJ, cj);
                int[] nb = {ci > 0 ? cur - n : -1, ci < n - 1 ? cur + n : -1, cj > 0 ? cur - 1 : -1, cj < n - 1 ? cur + 1 : -1};
                for (int q : nb) {
                    if (q >= 0 && !seen[q] && grid[q] == ty) {
                        seen[q] = true;
                        queue.add(q);
                    }
                }
            }
            // Regions touching the analysis border are truncated; keep them but they count as measured.
            double areaBlocks = (double) count * step * step;
            regions.add(new Region(types[ty], count, 2 * Math.sqrt(areaBlocks / Math.PI),
                    Math.max(maxI - minI + 1, maxJ - minJ + 1) * step,
                    -radius + (int) (sumI / count) * step, -radius + (int) (sumJ / count) * step));
        }
        // Run lengths along straight lines through open space (rock gaps are skipped, not counted as changes).
        double totalLen = 0;
        int changes = 0;
        int runs = 0;
        for (int line = 0; line < n; line += 3) {
            for (int dir = 0; dir < 2; dir++) {
                byte prev = -1;
                int runStart = 0;
                for (int k = 0; k < n; k++) {
                    byte v = dir == 0 ? grid[line * n + k] : grid[k * n + line];
                    if (v < 0) {
                        continue;
                    }
                    if (prev >= 0 && v != prev) {
                        changes++;
                        runs++;
                        totalLen += (k - runStart) * step;
                        runStart = k;
                    } else if (prev < 0) {
                        runStart = k;
                    }
                    prev = v;
                }
            }
        }
        double openLen = 0;
        for (int line = 0; line < n; line += 3) {
            for (int k = 0; k < n; k++) {
                if (grid[line * n + k] >= 0) {
                    openLen += step;
                }
                if (grid[k * n + line] >= 0) {
                    openLen += step;
                }
            }
        }
        double meanRun = runs > 0 ? totalLen / runs : radius * 2;
        double per1000 = openLen > 0 ? changes * 1000.0 / openLen : 0;
        return new Report(layer, radius, step, regions, area, open, w500, w1000, w2500, first, meanRun, per1000);
    }
}
