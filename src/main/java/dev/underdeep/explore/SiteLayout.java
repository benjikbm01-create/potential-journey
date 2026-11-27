package dev.underdeep.explore;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A site's authored canvas plus a world-space index of its gameplay features, so a block interaction finds its
 * puzzle, trap, cache or inscription from the position alone (no block entities, no per-structure controllers).
 */
public final class SiteLayout {
    public enum Kind { ELEMENT, SEAL, TRIGGER, EFFECTOR, CACHE, INSCRIPTION }

    /** A feature reference: kind, feature index (puzzle/trap/cache) and element index within it. */
    public record Ref(Kind kind, int index, int element) {
    }

    public final Site site;
    public final Canvas canvas;
    private final Long2ObjectOpenHashMap<List<Ref>> index = new Long2ObjectOpenHashMap<>();
    public final List<int[]> secretBoxes = new ArrayList<>();
    public final List<int[]> guardBoxes = new ArrayList<>();
    @Nullable
    public final int[] heartBox;
    @Nullable
    public final int[] reservedBox;
    public final int[] bounds;

    public SiteLayout(Site site, Canvas canvas) {
        this.site = site;
        this.canvas = canvas;
        for (int i = 0; i < canvas.puzzles.size(); i++) {
            Canvas.PuzzleSpec p = canvas.puzzles.get(i);
            for (int e = 0; e < p.elements().size(); e++) {
                put(p.elements().get(e), new Ref(Kind.ELEMENT, i, e));
            }
            for (int e = 0; e < p.seals().size(); e++) {
                put(p.seals().get(e), new Ref(Kind.SEAL, i, e));
            }
        }
        for (int i = 0; i < canvas.traps.size(); i++) {
            Canvas.TrapSpec t = canvas.traps.get(i);
            for (int e = 0; e < t.triggers().size(); e++) {
                put(t.triggers().get(e), new Ref(Kind.TRIGGER, i, e));
            }
            for (int e = 0; e < t.effectors().size(); e++) {
                put(t.effectors().get(e), new Ref(Kind.EFFECTOR, i, e));
            }
        }
        for (int i = 0; i < canvas.caches.size(); i++) {
            put(canvas.caches.get(i).pos(), new Ref(Kind.CACHE, i, 0));
        }
        int i = 0;
        for (long p : canvas.inscriptions.keySet()) {
            put(p, new Ref(Kind.INSCRIPTION, i++, 0));
        }
        for (Canvas.SecretSpec s : canvas.secrets) {
            secretBoxes.add(Canvas.worldBox(site, s.box()));
        }
        for (Canvas.GuardSpec g : canvas.guards) {
            guardBoxes.add(Canvas.worldBox(site, g.box()));
        }
        heartBox = canvas.heart == null ? null : Canvas.worldBox(site, canvas.heart);
        reservedBox = canvas.reserved == null ? null : Canvas.worldBox(site, canvas.reserved);
        BlockPos o = site.origin();
        bounds = new int[]{o.getX(), site.y() + canvas.minY(), o.getZ(), o.getX() + canvas.n - 1, site.y() + canvas.maxY(), o.getZ() + canvas.n - 1};
    }

    private void put(long local, Ref r) {
        long w = Canvas.world(site, local).asLong();
        index.computeIfAbsent(w, k -> new ArrayList<>(1)).add(r);
    }

    public List<Ref> at(BlockPos worldPos) {
        List<Ref> l = index.get(worldPos.asLong());
        return l == null ? List.of() : l;
    }

    @Nullable
    public Ref first(BlockPos worldPos, Kind kind) {
        for (Ref r : at(worldPos)) {
            if (r.kind() == kind) {
                return r;
            }
        }
        return null;
    }

    public BlockPos world(long local) {
        return Canvas.world(site, local);
    }

    public String inscription(int index) {
        int i = 0;
        for (String r : canvas.inscriptions.values()) {
            if (i++ == index) {
                return r;
            }
        }
        return "";
    }

    public boolean inside(double x, double y, double z, int margin) {
        return x >= bounds[0] - margin && x <= bounds[3] + 1 + margin && y >= bounds[1] - margin && y <= bounds[4] + 1 + margin
                && z >= bounds[2] - margin && z <= bounds[5] + 1 + margin;
    }
}
