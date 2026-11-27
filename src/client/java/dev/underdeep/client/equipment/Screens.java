package dev.underdeep.client.equipment;

import net.minecraft.client.gui.DrawContext;

/** Plain vanilla-style panel and slot drawing (no textures). */
public final class Screens {
    private Screens() {
    }

    public static void panel(DrawContext c, int x0, int y0, int w, int h) {
        c.fill(x0, y0, x0 + w, y0 + h, 0xFFC6C6C6);
        c.fill(x0, y0, x0 + w, y0 + 1, 0xFFFFFFFF);
        c.fill(x0, y0, x0 + 1, y0 + h, 0xFFFFFFFF);
        c.fill(x0, y0 + h - 1, x0 + w, y0 + h, 0xFF555555);
        c.fill(x0 + w - 1, y0, x0 + w, y0 + h, 0xFF555555);
    }

    public static void slot(DrawContext c, int x, int y) {
        c.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        c.fill(x, y, x + 17, y + 17, 0xFFFFFFFF);
        c.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
    }
}
