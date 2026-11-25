package dev.underdeep.world.noise;

/**
 * Seeded improved-Perlin gradient noise (2D and 3D). Immutable and thread-safe after construction.
 * Output is roughly in [-1, 1].
 */
public final class PerlinNoise {
    private final int[] perm = new int[512];
    private final double offX;
    private final double offY;
    private final double offZ;

    public PerlinNoise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        long s = seed;
        for (int i = 255; i > 0; i--) {
            s = Hashing.mix(s + 0x9E3779B97F4A7C15L);
            int j = (int) Long.remainderUnsigned(s, i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
        // Random sub-lattice offsets avoid the zero-at-integer-lattice artifact at the origin.
        offX = Hashing.unit(Hashing.mix(seed ^ 0x1234L)) * 256.0;
        offY = Hashing.unit(Hashing.mix(seed ^ 0x5678L)) * 256.0;
        offZ = Hashing.unit(Hashing.mix(seed ^ 0x9ABCL)) * 256.0;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad3(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static double grad2(int hash, double x, double y) {
        switch (hash & 7) {
            case 0: return x + y;
            case 1: return -x + y;
            case 2: return x - y;
            case 3: return -x - y;
            case 4: return x;
            case 5: return -x;
            case 6: return y;
            default: return -y;
        }
    }

    public double sample(double x, double y, double z) {
        x += offX;
        y += offY;
        z += offZ;
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        double xf = x - xi;
        double yf = y - yi;
        double zf = z - zi;
        xi &= 255;
        yi &= 255;
        zi &= 255;
        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);
        int a = perm[xi] + yi;
        int aa = perm[a] + zi;
        int ab = perm[a + 1] + zi;
        int b = perm[xi + 1] + yi;
        int ba = perm[b] + zi;
        int bb = perm[b + 1] + zi;
        double r = lerp(w,
                lerp(v, lerp(u, grad3(perm[aa], xf, yf, zf), grad3(perm[ba], xf - 1, yf, zf)),
                        lerp(u, grad3(perm[ab], xf, yf - 1, zf), grad3(perm[bb], xf - 1, yf - 1, zf))),
                lerp(v, lerp(u, grad3(perm[aa + 1], xf, yf, zf - 1), grad3(perm[ba + 1], xf - 1, yf, zf - 1)),
                        lerp(u, grad3(perm[ab + 1], xf, yf - 1, zf - 1), grad3(perm[bb + 1], xf - 1, yf - 1, zf - 1))));
        return r;
    }

    public double sample(double x, double z) {
        x += offX;
        z += offZ;
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        double xf = x - xi;
        double zf = z - zi;
        xi &= 255;
        zi &= 255;
        double u = fade(xf);
        double v = fade(zf);
        int aa = perm[perm[xi] + zi];
        int ab = perm[perm[xi] + zi + 1];
        int ba = perm[perm[xi + 1] + zi];
        int bb = perm[perm[xi + 1] + zi + 1];
        double r = lerp(v,
                lerp(u, grad2(aa, xf, zf), grad2(ba, xf - 1, zf)),
                lerp(u, grad2(ab, xf, zf - 1), grad2(bb, xf - 1, zf - 1)));
        return r * 0.7071;
    }
}
