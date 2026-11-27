package dev.underdeep.world.terrain;

import dev.underdeep.world.noise.FractalNoise;
import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.region.RegionMap;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.region.TerrainProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * The guaranteed-connected skeleton of a cavern layer.
 * <p>
 * The layer is divided into square cells of {@link CavernLayer#graphCell} blocks. Every cell owns
 * one <em>node</em> (a large chamber at a jittered position). Every node is joined by a tunnel to
 * its east and south neighbours, so the graph is a connected grid by construction; extra diagonal
 * tunnels add loops. Because nodes and edges are pure functions of (seed, cell), any column can
 * evaluate the nearby skeleton without knowing what other chunks have been generated, which makes
 * connectivity independent of chunk generation order.
 * <p>
 * Thread-safe: immutable except for thread-local caches.
 */
public final class CavernGraph {
    /** A chamber in the graph. */
    public record Node(int cellX, int cellZ, double x, double z, double radius, double height,
                       double tunnelMul, RegionType region, double cos, double sin, double axis, boolean mega) {
        public long key() {
            return ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
        }
    }

    /** A tunnel between two nodes. */
    public record Edge(Node a, Node b, double width, double height) {
    }

    /** A vertical shaft from a deep-layer node up into the upper layer. */
    public record Shaft(double x, double z, double radius, Node upperNode) {
    }

    /** Scratch result of evaluating the skeleton at a position. */
    public static final class Openness {
        /** Height of open space required by the skeleton at this column (0 = solid rock). */
        public double height;
        /** 0..1 how deep inside a chamber this column is (used to scale hills and pillars). */
        public double chamber;
        /** 0..1 how central to a tunnel this column is. */
        public double tunnel;
        /** Normalised distance to the nearest chamber (d / r); &lt; 1 inside. */
        public double nearestChamber;
        /** Nearest node (for analysis). */
        public Node nearest;
    }

    private final CavernLayer layer;
    private final long seed;
    private final RegionMap regions;
    private final FractalNoise warpX;
    private final FractalNoise warpZ;
    private final FractalNoise shapeNoise;
    private final int cell;
    private final boolean forceOrigin;
    private final ThreadLocal<Node[]> nodeCache;
    /** Stage 10 secondary cave network (null in worlds generated before v5). */
    private final Passages passages;

    public CavernGraph(long worldSeed, CavernLayer layer, RegionMap regions) {
        this(worldSeed, layer, regions, 0);
    }

    /** @param passageFrequency density of the secondary passage network (0 = none, as in generator v4 and older) */
    public CavernGraph(long worldSeed, CavernLayer layer, RegionMap regions, double passageFrequency) {
        this.layer = layer;
        this.seed = Hashing.hash(worldSeed, Hashing.salt("cavern_graph"), layer.ordinal());
        this.regions = regions;
        this.cell = layer.graphCell;
        this.warpX = new FractalNoise(Hashing.hash(seed, 1), 2, 1 / 110.0);
        this.warpZ = new FractalNoise(Hashing.hash(seed, 2), 2, 1 / 110.0);
        this.shapeNoise = new FractalNoise(Hashing.hash(seed, 3), 3, 1 / 70.0);
        this.forceOrigin = layer == CavernLayer.UPPER;
        this.nodeCache = ThreadLocal.withInitial(() -> new Node[1024]);
        this.passages = passageFrequency > 0 ? new Passages(seed, layer, passageFrequency) : null;
    }

    /** Whether this graph has the Stage 10 secondary passage network. */
    public boolean hasPassages() {
        return passages != null;
    }

    /** Passage anchors in a rectangle (x, z, chamber radius, edge count), for diagnostics. */
    public List<double[]> passageAnchors(int minX, int minZ, int maxX, int maxZ) {
        return passages == null ? List.of() : passages.anchorsIn(minX, minZ, maxX, maxZ);
    }

    public CavernLayer layer() {
        return layer;
    }

    public int cellSize() {
        return cell;
    }

    /** Returns the node owned by a graph cell. */
    public Node node(int cx, int cz) {
        Node[] cache = nodeCache.get();
        int slot = (int) (Hashing.mix(((long) cx << 32) ^ (cz & 0xFFFFFFFFL)) & 1023);
        Node n = cache[slot];
        if (n != null && n.cellX == cx && n.cellZ == cz) {
            return n;
        }
        n = computeNode(cx, cz);
        cache[slot] = n;
        return n;
    }

    private Node computeNode(int cx, int cz) {
        double x;
        double z;
        boolean origin = forceOrigin && cx == 0 && cz == 0;
        if (origin) {
            x = 0;
            z = 0;
        } else {
            x = (cx + 0.15 + 0.7 * Hashing.unit(Hashing.hash(seed, cx, cz, 1))) * cell;
            z = (cz + 0.15 + 0.7 * Hashing.unit(Hashing.hash(seed, cx, cz, 2))) * cell;
        }
        RegionType region = regions.typeAt(x, z);
        TerrainProfile p = region.profile();
        double ru = Hashing.unit(Hashing.hash(seed, cx, cz, 3));
        double hu = Hashing.unit(Hashing.hash(seed, cx, cz, 4));
        // Skew towards smaller chambers with occasional colossal ones.
        double radius = layer.chamberRadiusMin + (layer.chamberRadiusMax - layer.chamberRadiusMin) * ru * ru;
        double height = layer.chamberHeightMin + (layer.chamberHeightMax - layer.chamberHeightMin) * (0.35 * hu + 0.65 * ru);
        radius *= p.chamberRadius;
        height *= p.chamberHeight;
        if (origin) {
            // The starting cavern: large, tall, and welcoming.
            radius = Math.max(radius, 104);
            height = Math.max(height, 62);
        }
        double tunnelMul = p.tunnelWidth;
        // Elongated, rotated chambers avoid a repetitive "bubble" look.
        double angle = Hashing.unit(Hashing.hash(seed, cx, cz, 7)) * Math.PI;
        double axis = 0.5 + 0.5 * Hashing.unit(Hashing.hash(seed, cx, cz, 8));
        // Occasional colossal caverns hundreds of blocks across.
        boolean mega = !origin && Hashing.unit(Hashing.hash(seed, cx, cz, 9)) < 0.13;
        if (mega) {
            radius = Math.max(radius, layer.chamberRadiusMax * 0.8) * 2.1;
            height = Math.max(height, layer.chamberHeightMax * 0.85) * 1.35 * p.chamberHeight;
            axis = Math.max(axis, 0.7);
        }
        if (origin) {
            axis = 1;
        }
        return new Node(cx, cz, x, z, radius, height, tunnelMul, region, Math.cos(angle), Math.sin(angle), axis, mega);
    }

    /** Edges owned by a cell (east, south, and optional diagonals). */
    public void ownedEdges(int cx, int cz, List<Edge> out) {
        Node a = node(cx, cz);
        out.add(edge(a, node(cx + 1, cz), 0));
        out.add(edge(a, node(cx, cz + 1), 1));
        if (Hashing.unit(Hashing.hash(seed, cx, cz, 5)) < 0.30) {
            out.add(edge(a, node(cx + 1, cz + 1), 2));
        }
        if (Hashing.unit(Hashing.hash(seed, cx, cz, 6)) < 0.18) {
            out.add(edge(a, node(cx + 1, cz - 1), 3));
        }
    }

    private Edge edge(Node a, Node b, int salt) {
        long h = Hashing.hash(seed, a.cellX * 31L + b.cellX, a.cellZ * 31L + b.cellZ, 40 + salt);
        double u = Hashing.unit(h);
        double v = Hashing.unit(Hashing.mix(h));
        double mul = (a.tunnelMul + b.tunnelMul) * 0.5;
        // Occasional broad "highway" tunnels.
        double width = (5.5 + 5.0 * u + (u > 0.85 ? 8 : 0)) * mul;
        double height = 9 + 8 * v + (u > 0.85 ? 10 : 0);
        return new Edge(a, b, width, height);
    }

    public double warpedX(double x, double z) {
        return x + warpX.sample(x, z) * 26.0;
    }

    public double warpedZ(double x, double z) {
        return z + warpZ.sample(x, z) * 26.0;
    }

    /**
     * Evaluates the skeleton at a column.
     *
     * @param shafts optional shafts whose top chambers and feeder tunnels open this column
     */
    public Openness evaluate(double x, double z, List<Shaft> shafts, Openness out) {
        double qx = warpedX(x, z);
        double qz = warpedZ(x, z);
        int bx = Math.floorDiv((int) Math.floor(qx), cell);
        int bz = Math.floorDiv((int) Math.floor(qz), cell);
        double height = 0;
        double chamber = 0;
        double tunnel = 0;
        double nearestNorm = Double.MAX_VALUE;
        Node nearest = null;
        double lobe = 1 + 0.32 * shapeNoise.sample(x, z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Node n = node(bx + dx, bz + dz);
                double ddx = qx - n.x;
                double ddz = qz - n.z;
                if (Math.abs(ddx) > n.radius * 1.4 + 30 || Math.abs(ddz) > n.radius * 1.4 + 30) {
                    double far = Math.sqrt(ddx * ddx + ddz * ddz) / n.radius;
                    if (far < nearestNorm) {
                        nearestNorm = far;
                        nearest = n;
                    }
                    continue;
                }
                double u = ddx * n.cos + ddz * n.sin;
                double v = (-ddx * n.sin + ddz * n.cos) / n.axis;
                double d = Math.sqrt(u * u + v * v);
                double norm = d / (n.radius * lobe);
                if (norm < nearestNorm) {
                    nearestNorm = norm;
                    nearest = n;
                }
                if (norm < 1) {
                    double s = 1 - norm * norm;
                    double h = n.height * Math.min(1.0, Math.pow(s, 0.42) * 1.08);
                    if (h > height) {
                        height = h;
                    }
                    if (s > chamber) {
                        chamber = s;
                    }
                }
            }
        }
        for (int dx = -2; dx <= 1; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (Edge e : edgesOf(bx + dx, bz + dz)) {
                    double t = segmentDistance(qx, qz, e.a.x, e.a.z, e.b.x, e.b.z, e.width);
                    if (t < 1) {
                        double s = 1 - t * t;
                        double h = e.height * Math.min(1.0, Math.sqrt(s) * 1.35);
                        if (h > height) {
                            height = h;
                        }
                        if (s > tunnel) {
                            tunnel = s;
                        }
                    }
                }
            }
        }
        if (passages != null) {
            out.height = height;
            out.tunnel = tunnel;
            out.chamber = chamber;
            passages.evaluate(qx, qz, out);
            height = out.height;
            tunnel = out.tunnel;
            chamber = out.chamber;
        }
        if (shafts != null) {
            for (Shaft sh : shafts) {
                double ddx = qx - sh.x;
                double ddz = qz - sh.z;
                double d = Math.sqrt(ddx * ddx + ddz * ddz);
                double r = sh.radius + 12;
                if (d < r) {
                    double s = 1 - (d / r) * (d / r);
                    double h = 22 * Math.min(1, Math.sqrt(s) * 1.5);
                    height = Math.max(height, h);
                    tunnel = Math.max(tunnel, s);
                }
                if (sh.upperNode != null) {
                    double t = segmentDistance(qx, qz, sh.x, sh.z, sh.upperNode.x, sh.upperNode.z, 7.5);
                    if (t < 1) {
                        double s = 1 - t * t;
                        height = Math.max(height, 13 * Math.min(1, Math.sqrt(s) * 1.35));
                        tunnel = Math.max(tunnel, s);
                    }
                }
            }
        }
        out.height = height;
        out.chamber = chamber;
        out.tunnel = tunnel;
        out.nearestChamber = nearestNorm;
        out.nearest = nearest;
        return out;
    }

    private final ThreadLocal<Object[]> edgeCache = ThreadLocal.withInitial(() -> new Object[512]);

    private record CachedEdges(int cx, int cz, Edge[] edges) {
    }

    /** Cached edges owned by a cell. */
    public Edge[] edgesOf(int cx, int cz) {
        Object[] cache = edgeCache.get();
        int slot = (int) (Hashing.mix(((long) cx << 32) ^ (cz & 0xFFFFFFFFL) ^ 0x55L) & 511);
        Object o = cache[slot];
        if (o instanceof CachedEdges ce && ce.cx == cx && ce.cz == cz) {
            return ce.edges;
        }
        List<Edge> list = new ArrayList<>(4);
        ownedEdges(cx, cz, list);
        Edge[] arr = list.toArray(new Edge[0]);
        cache[slot] = new CachedEdges(cx, cz, arr);
        return arr;
    }

    /** Distance from point to segment divided by width (returns >= 1 quickly when far). */
    static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz, double width) {
        double minX = Math.min(ax, bx) - width;
        double maxX = Math.max(ax, bx) + width;
        double minZ = Math.min(az, bz) - width;
        double maxZ = Math.max(az, bz) + width;
        if (px < minX || px > maxX || pz < minZ || pz > maxZ) {
            return 2;
        }
        double vx = bx - ax;
        double vz = bz - az;
        double len2 = vx * vx + vz * vz;
        double t = len2 > 0 ? ((px - ax) * vx + (pz - az) * vz) / len2 : 0;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + vx * t - px;
        double cz = az + vz * t - pz;
        return Math.sqrt(cx * cx + cz * cz) / width;
    }

    /** All nodes whose cells intersect a block-space rectangle (for analysis tools). */
    public List<Node> nodesIn(int minX, int minZ, int maxX, int maxZ) {
        List<Node> list = new ArrayList<>();
        for (int cx = Math.floorDiv(minX, cell); cx <= Math.floorDiv(maxX, cell); cx++) {
            for (int cz = Math.floorDiv(minZ, cell); cz <= Math.floorDiv(maxZ, cell); cz++) {
                list.add(node(cx, cz));
            }
        }
        return list;
    }

    /** Nearest node to a position, searching the surrounding 3x3 cells. */
    public Node nearestNode(double x, double z) {
        int bx = Math.floorDiv((int) Math.floor(x), cell);
        int bz = Math.floorDiv((int) Math.floor(z), cell);
        Node best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Node n = node(bx + dx, bz + dz);
                double d = (n.x - x) * (n.x - x) + (n.z - z) * (n.z - z);
                if (d < bestD) {
                    bestD = d;
                    best = n;
                }
            }
        }
        return best;
    }

    public long seed() {
        return seed;
    }
}
