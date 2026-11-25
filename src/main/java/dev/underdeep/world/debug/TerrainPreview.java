package dev.underdeep.world.debug;

import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Renders diagnostic images straight from the pure terrain model (no chunks required). Used by the
 * {@code /underdeep debug render} command and by unit tests to inspect cavern layouts.
 * <p>
 * These images are approximations: they show terrain shape and fluids, not features (trees,
 * vegetation, ores), which are only placed during real chunk generation.
 */
public final class TerrainPreview {
    private TerrainPreview() {
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) * f));
        int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) * f));
        int b = (int) Math.max(0, Math.min(255, (rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static int brighten(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 255) * 2 + 60);
        int g = Math.min(255, ((rgb >> 8) & 255) * 2 + 60);
        int b = Math.min(255, (rgb & 255) * 2 + 60);
        return (r << 16) | (g << 8) | b;
    }

    /** Region map of a layer (one pixel = {@code scale} blocks). */
    public static BufferedImage regionMap(UnderdeepTerrain t, CavernLayer layer, int cx, int cz, int size, int scale) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int px = 0; px < size; px++) {
            for (int pz = 0; pz < size; pz++) {
                int x = cx + (px - size / 2) * scale;
                int z = cz + (pz - size / 2) * scale;
                RegionType r = t.regions(layer).typeAt(x, z);
                img.setRGB(px, pz, brighten(r.mapColor()));
            }
        }
        mark(img, size / 2, size / 2, 0xFFFFFF);
        return img;
    }

    /**
     * Top-down view of one layer: open floor areas coloured by region and shaded by floor height,
     * water/lava shown in blue/orange, solid rock black.
     */
    public static BufferedImage layerMap(UnderdeepTerrain t, CavernLayer layer, int cx, int cz, int size, int scale) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        boolean upper = layer == CavernLayer.UPPER;
        for (int px = 0; px < size; px++) {
            for (int pz = 0; pz < size; pz++) {
                int x = cx + (px - size / 2) * scale;
                int z = cz + (pz - size / 2) * scale;
                TerrainColumn c = t.column(x, z);
                double f = upper ? c.upperFloor : c.deepFloor;
                double ce = upper ? c.upperCeiling : c.deepCeiling;
                int rgb;
                if (ce - f < 2) {
                    rgb = 0x101010;
                } else {
                    int y = (int) Math.floor(f) + 1;
                    UnderdeepTerrain.Material m = t.classify(c, y, 1);
                    RegionType r = upper ? c.upperRegion.primary : c.deepRegion.primary;
                    if (m == UnderdeepTerrain.Material.WATER) {
                        rgb = 0x2860C8;
                    } else if (m == UnderdeepTerrain.Material.LAVA) {
                        rgb = 0xE06010;
                    } else {
                        double shadeF = 0.55 + (f - (upper ? 80 : -40)) / (upper ? 140.0 : 120.0);
                        rgb = shade(brighten(r.mapColor()), Math.max(0.35, Math.min(1.25, shadeF)));
                    }
                    double gap = ce - f;
                    if (gap > 60) {
                        rgb = shade(rgb, 1.1);
                    }
                }
                if (c.shaftDist < c.shaftRadius) {
                    rgb = 0xFFFF00;
                }
                img.setRGB(px, pz, rgb);
            }
        }
        mark(img, size / 2, size / 2, 0xFF00FF);
        return img;
    }

    /** Vertical cross-section along X at fixed Z (one pixel per {@code scale} blocks horizontally, 1 vertically). */
    public static BufferedImage crossSection(UnderdeepTerrain t, int z, int minX, int width, int scale) {
        int h = UnderdeepTerrain.HEIGHT;
        BufferedImage img = new BufferedImage(width, h, BufferedImage.TYPE_INT_RGB);
        for (int px = 0; px < width; px++) {
            int x = minX + px * scale;
            TerrainColumn c = t.column(x, z);
            for (int y = UnderdeepTerrain.MIN_Y; y < UnderdeepTerrain.TOP_Y; y++) {
                UnderdeepTerrain.Material m = t.classify(c, y,
                        t.density(c, y, t.roughness(x, y, z), t.tunnels(x, y, z)));
                int rgb = switch (m) {
                    case AIR -> brighten(c.regionAt(y).mapColor());
                    case WATER -> 0x2860C8;
                    case LAVA -> 0xE06010;
                    case BEDROCK -> 0x303030;
                    case STONE -> y < c.separation ? 0x3A3A44 : 0x6A6A6A;
                };
                img.setRGB(px, UnderdeepTerrain.TOP_Y - 1 - y, rgb);
            }
        }
        return img;
    }

    /** Horizontal slice at a fixed height. */
    public static BufferedImage slice(UnderdeepTerrain t, int y, int cx, int cz, int size, int scale) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int px = 0; px < size; px++) {
            for (int pz = 0; pz < size; pz++) {
                int x = cx + (px - size / 2) * scale;
                int z = cz + (pz - size / 2) * scale;
                TerrainColumn c = t.column(x, z);
                UnderdeepTerrain.Material m = t.classify(c, y,
                        t.density(c, y, t.roughness(x, y, z), t.tunnels(x, y, z)));
                int rgb = switch (m) {
                    case AIR -> brighten(c.regionAt(y).mapColor());
                    case WATER -> 0x2860C8;
                    case LAVA -> 0xE06010;
                    case BEDROCK -> 0x303030;
                    case STONE -> 0x202020;
                };
                img.setRGB(px, pz, rgb);
            }
        }
        mark(img, size / 2, size / 2, 0xFF00FF);
        return img;
    }

    private static void mark(BufferedImage img, int x, int y, int rgb) {
        for (int d = -3; d <= 3; d++) {
            set(img, x + d, y, rgb);
            set(img, x, y + d, rgb);
        }
    }

    private static void set(BufferedImage img, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, rgb);
        }
    }

    public static void write(BufferedImage img, File file) throws IOException {
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
    }
}
