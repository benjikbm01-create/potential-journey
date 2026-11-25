package dev.underdeep.world.noise;

/**
 * Fractal (fBm) wrapper around {@link PerlinNoise} octaves with a base frequency.
 * Output is normalised to roughly [-1, 1]. Immutable and thread-safe.
 */
public final class FractalNoise {
    private final PerlinNoise[] octaves;
    private final double frequency;
    private final double norm;
    private final double persistence;

    public FractalNoise(long seed, int octaveCount, double frequency, double persistence) {
        this.octaves = new PerlinNoise[octaveCount];
        for (int i = 0; i < octaveCount; i++) {
            octaves[i] = new PerlinNoise(Hashing.hash(seed, i, 0x0C7A));
        }
        this.frequency = frequency;
        this.persistence = persistence;
        double n = 0;
        double amp = 1;
        for (int i = 0; i < octaveCount; i++) {
            n += amp;
            amp *= persistence;
        }
        this.norm = 1.0 / n;
    }

    public FractalNoise(long seed, int octaveCount, double frequency) {
        this(seed, octaveCount, frequency, 0.5);
    }

    public double sample(double x, double z) {
        double sum = 0;
        double amp = 1;
        double f = frequency;
        for (PerlinNoise o : octaves) {
            sum += o.sample(x * f, z * f) * amp;
            amp *= persistence;
            f *= 2.03;
        }
        return sum * norm * 1.35;
    }

    public double sample(double x, double y, double z) {
        double sum = 0;
        double amp = 1;
        double f = frequency;
        for (PerlinNoise o : octaves) {
            sum += o.sample(x * f, y * f, z * f) * amp;
            amp *= persistence;
            f *= 2.03;
        }
        return sum * norm * 1.2;
    }

    /** Ridged variant: 1 at noise zero-crossings, falling towards 0. */
    public double ridged(double x, double z) {
        return 1.0 - Math.abs(sample(x, z));
    }
}
