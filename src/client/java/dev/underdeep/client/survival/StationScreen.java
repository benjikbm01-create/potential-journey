package dev.underdeep.client.survival;

import dev.underdeep.survival.Recipes;
import dev.underdeep.survival.SurvivalContent;
import dev.underdeep.survival.SurvivalContent.Recipe;
import dev.underdeep.survival.station.StationBlockEntity;
import dev.underdeep.survival.station.StationScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Workstation screen: input, container, fuel and output slots; a progress arrow; heat or fuel state; what the
 * current ingredients make; and a side panel listing the recipes you know for this station. Click a recipe to see
 * its ingredients, each marked as present or missing, and the meal's effects. Drawn with plain fills (no texture).
 */
public class StationScreen extends HandledScreen<StationScreenHandler> {
    private static final int PANEL_W = 128, BG = 0xFFC6C6C6, DARK = 0xFF373737, LIGHT = 0xFFFFFFFF, SLOT = 0xFF8B8B8B;
    private int selected = -1, scroll;

    public StationScreen(StationScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 166;
        playerInventoryTitleY = backgroundHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        x = (width - backgroundWidth - PANEL_W - 4) / 2;
        titleX = 8;
    }

    private List<Recipe> known() {
        List<Recipe> out = new ArrayList<>();
        for (Recipe r : SurvivalContent.recipes(handler.station)) {
            if (client != null && client.player != null && Recipes.knows(client.player, r)) {
                out.add(r);
            }
        }
        return out;
    }

    private List<ItemStack> inputs() {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < handler.station.inputs; i++) {
            l.add(handler.inventory.getStack(i));
        }
        return l;
    }

    private static void panel(DrawContext c, int x0, int y0, int w, int h) {
        c.fill(x0, y0, x0 + w, y0 + h, BG);
        c.fill(x0, y0, x0 + w, y0 + 1, LIGHT);
        c.fill(x0, y0, x0 + 1, y0 + h, LIGHT);
        c.fill(x0, y0 + h - 1, x0 + w, y0 + h, 0xFF555555);
        c.fill(x0 + w - 1, y0, x0 + w, y0 + h, 0xFF555555);
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        panel(c, x, y, backgroundWidth, backgroundHeight);
        for (Slot s : handler.slots) {
            c.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, DARK);
            c.fill(x + s.x, y + s.y, x + s.x + 17, y + s.y + 17, LIGHT);
            c.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, SLOT);
        }
        // progress arrow
        int prog = handler.props.get(0), total = handler.props.get(1);
        int ax = x + 104, ay = y + 38;
        c.fill(ax, ay, ax + 24, ay + 10, 0xFF8B8B8B);
        if (total > 0) {
            c.fill(ax, ay, ax + Math.min(24, prog * 24 / total), ay + 10, 0xFF4FBF4F);
        }
        // heat / fuel indicator under the arrow
        int fx = x + 108, fy = y + 53;
        if (handler.station.heat) {
            boolean warm = handler.props.get(2) > 0;
            c.fill(fx, fy, fx + 14, fy + 14, warm ? 0xFFE07020 : 0xFF6B6B6B);
            c.fill(fx + 4, fy + 3, fx + 10, fy + 11, warm ? 0xFFFFD040 : 0xFF8B8B8B);
        } else if (handler.station.fuel) {
            int burn = handler.props.get(2), max = Math.max(1, handler.props.get(3));
            int fx2 = x + 98 + 18 + 4;
            c.fill(fx2, y + 53, fx2 + 4, y + 69, 0xFF6B6B6B);
            int h = burn * 16 / max;
            c.fill(fx2, y + 69 - h, fx2 + 4, y + 69, 0xFFE07020);
        }
        // side panel
        int px = x + backgroundWidth + 4;
        panel(c, px, y, PANEL_W, backgroundHeight);
    }

    @Override
    protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
        c.drawText(textRenderer, title, titleX, titleY, 0x404040, false);
        c.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0x404040, false);
        // status line
        int idx = handler.props.get(4), batches = handler.props.get(5);
        List<Recipe> all = SurvivalContent.recipes(handler.station);
        String status;
        int color = 0x404040;
        if (idx >= 0 && idx < all.size()) {
            Recipe r = all.get(idx);
            status = r.resultItem().getName().getString() + " x" + r.count() * batches;
            if (handler.station.heat && handler.props.get(2) == 0) {
                status = I18n.translate("message.underdeep.station.heat");
                color = 0xA03020;
            } else if (handler.station.fuel && handler.props.get(2) == 0 && handler.inventory.getStack(StationBlockEntity.FUEL).isEmpty()) {
                status = I18n.translate("message.underdeep.station.fuel");
                color = 0xA03020;
            }
        } else {
            status = I18n.translate("message.underdeep.station.idle");
        }
        drawWrapped(c, status, 46, 60, 56, color, 2);
        // side panel: known recipes
        int px = backgroundWidth + 4 + 4;
        List<Recipe> known = known();
        c.drawText(textRenderer, Text.literal("Known recipes (" + known.size() + ")"), px, 6, 0x404040, false);
        int rows = 8;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, known.size() - rows)));
        List<ItemStack> in = inputs();
        for (int i = 0; i < rows && i + scroll < known.size(); i++) {
            Recipe r = known.get(i + scroll);
            StationBlockEntity.Match m = StationBlockEntity.match(r, in, handler.inventory.getStack(StationBlockEntity.CONTAINER),
                    handler.inventory.getStack(StationBlockEntity.OUTPUT), handler.station.maxBatch);
            boolean ready = m != null && m.batches() > 0;
            boolean sel = i + scroll == selected;
            if (sel) {
                c.fill(px - 2, 17 + i * 10, px + PANEL_W - 10, 27 + i * 10, 0xFF9FA8B0);
            }
            String name = textRenderer.trimToWidth(r.resultItem().getName().getString(), PANEL_W - 14);
            c.drawText(textRenderer, name, px, 18 + i * 10, ready ? 0x1F7F1F : 0x303030, false);
        }
        if (selected >= 0 && selected < known.size()) {
            Recipe r = known.get(selected);
            int yy = 104;
            for (SurvivalContent.Ingredient ing : r.ingredients()) {
                int have = 0;
                for (ItemStack s : in) {
                    if (SurvivalContent.matches(ing, s)) {
                        have += s.getCount();
                    }
                }
                boolean ok = have >= ing.count();
                String line = (ok ? "+ " : "- ") + ing.count() + " " + SurvivalContent.ingredientName(ing);
                c.drawText(textRenderer, textRenderer.trimToWidth(line, PANEL_W - 8), px, yy, ok ? 0x1F7F1F : 0xA02020, false);
                yy += 9;
            }
            if (r.container() != null) {
                boolean ok = handler.inventory.getStack(StationBlockEntity.CONTAINER).isOf(r.containerItem());
                c.drawText(textRenderer, (ok ? "+ " : "- ") + r.containerItem().getName().getString(), px, yy, ok ? 0x1F7F1F : 0xA02020, false);
                yy += 9;
            }
            String fx = effects(r);
            if (!fx.isEmpty() && yy < 156) {
                drawWrapped(c, fx, px, yy, PANEL_W - 8, 0x30508F, (160 - yy) / 9);
            }
        }
    }

    static String effects(Recipe r) {
        Identifier id = r.result();
        SurvivalContent.FoodDef d = SurvivalContent.FOODS.get(id.getPath());
        if (d == null || d.buffs().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SurvivalContent.Buff b : d.buffs()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(I18n.translate("effect." + b.effect().getNamespace() + "." + b.effect().getPath()));
            sb.append(" ").append(b.seconds() / 60).append(":").append(String.format("%02d", b.seconds() % 60));
        }
        return sb.toString();
    }

    private void drawWrapped(DrawContext c, String s, int x0, int y0, int w, int color, int maxLines) {
        var lines = textRenderer.wrapLines(Text.literal(s), w);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            c.drawText(textRenderer, lines.get(i), x0, y0 + i * 9, color, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int px = x + backgroundWidth + 8;
        if (mx >= px - 2 && mx < px + PANEL_W - 10 && my >= y + 17 && my < y + 97) {
            int i = (int) ((my - y - 17) / 10) + scroll;
            selected = i < known().size() ? (selected == i ? -1 : i) : -1;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx >= x + backgroundWidth) {
            scroll -= (int) Math.signum(v);
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        super.render(c, mouseX, mouseY, delta);
        drawMouseoverTooltip(c, mouseX, mouseY);
    }
}
