package dev.underdeep.client.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.underdeep.Underdeep;
import dev.underdeep.equipment.Accessories;
import dev.underdeep.equipment.EquipmentContent;
import dev.underdeep.equipment.EquipmentItems;
import dev.underdeep.equipment.Upgrades;
import dev.underdeep.guide.Handbook;
import dev.underdeep.guide.Sensitivity;
import dev.underdeep.survival.SurvivalContent;
import dev.underdeep.survival.SurvivalItems;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.WildlifeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MiningToolItem;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.BlastingRecipe;
import net.minecraft.recipe.CampfireCookingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.SmokingRecipe;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Underdeep Handbook screen: categories on the left with a search box, the pages of the category (or search
 * results) in the middle, the page on the right. Authored pages explain mechanics; item, creature, ecosystem and people
 * pages are built from the registries, recipe data and the server's audit. Pages that need a discovery stay hidden (and
 * out of search) until this player has made it. Click icons to open their pages; Back returns; bookmarks persist.
 */
public class HandbookScreen extends Screen {
    record Cat(String id, String title, ItemStack icon) {
    }

    static final class Page {
        String id, cat, title, req = "";
        ItemStack icon = ItemStack.EMPTY;
        List<String> keys = List.of(), body = List.of(), items = List.of(), see = List.of();
        Item item;
        CreatureSpecies species;
    }

    static List<Cat> cats;
    static Map<String, Page> pages;
    static int builtFor = -1;
    static String current = "welcome", category = "start";
    static final Deque<String> history = new ArrayDeque<>();
    static final Set<String> bookmarks = new LinkedHashSet<>();
    static boolean bookmarksLoaded;

    private TextFieldWidget search;
    private int x0, y0, w, h, listScroll, pageScroll, pageContentH;
    private final List<int[]> hotRects = new ArrayList<>();
    private final List<Runnable> hotActions = new ArrayList<>();
    private ItemStack hoverStack = ItemStack.EMPTY;
    private String hoverText;

    public HandbookScreen() {
        super(Text.literal("Underdeep Handbook"));
    }

    public static void openAt(String pageId) {
        current = pageId;
        MinecraftClient.getInstance().setScreen(new HandbookScreen());
    }

    // ================================================================== data

    static void build() {
        int stamp = Handbook.CLIENT_ITEMS.size() * 31 + Handbook.CLIENT_DISCOVERED.size();
        if (pages != null && builtFor == stamp) {
            return;
        }
        builtFor = stamp;
        cats = new ArrayList<>();
        pages = new LinkedHashMap<>();
        try (var in = HandbookScreen.class.getResourceAsStream("/assets/underdeep/handbook/pages.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("categories")) {
                JsonObject o = e.getAsJsonObject();
                cats.add(new Cat(o.get("id").getAsString(), o.get("title").getAsString(), stack(o.get("icon").getAsString())));
            }
            for (JsonElement e : root.getAsJsonArray("pages")) {
                JsonObject o = e.getAsJsonObject();
                Page p = new Page();
                p.id = o.get("id").getAsString();
                p.cat = o.get("cat").getAsString();
                p.title = o.get("title").getAsString();
                p.icon = stack(o.get("icon").getAsString());
                p.keys = strings(o, "keys");
                p.body = strings(o, "body");
                p.items = strings(o, "items");
                p.see = strings(o, "see");
                p.req = o.get("req").getAsString();
                pages.put(p.id, p);
            }
        } catch (Exception ex) {
            Underdeep.LOGGER.warn("handbook pages missing", ex);
        }
        // item pages
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            boolean ours = id.getNamespace().equals(Underdeep.MOD_ID);
            if (!ours && !Handbook.CLIENT_ITEMS.containsKey(id.toString())) {
                continue;
            }
            Sensitivity.Kind k = Sensitivity.kind(item);
            if (k == Sensitivity.Kind.TECHNICAL || k == Sensitivity.Kind.DEFERRED) {
                continue;
            }
            Page p = new Page();
            p.id = "item:" + id;
            p.item = item;
            p.icon = new ItemStack(item);
            p.title = p.icon.getName().getString();
            p.cat = categoryOf(item);
            p.req = k == Sensitivity.Kind.SENSITIVE ? "item:" + id : "";
            p.keys = List.of(id.getPath().replace('_', ' '));
            pages.put(p.id, p);
        }
        // creature pages
        for (CreatureSpecies s : WildlifeRegistry.all()) {
            Page p = new Page();
            p.id = "species:" + s.id;
            p.species = s;
            p.cat = "wildlife";
            p.title = I18n.translate("entity.underdeep." + s.id);
            p.icon = stack("underdeep:" + s.id + "_spawn_egg");
            p.req = "species:" + s.id;
            p.keys = List.of("creature", "animal", s.diet().name().toLowerCase(Locale.ROOT));
            pages.put(p.id, p);
        }
    }

    static List<String> strings(JsonObject o, String k) {
        List<String> l = new ArrayList<>();
        if (o.has(k)) {
            o.getAsJsonArray(k).forEach(x -> l.add(x.getAsString()));
        }
        return l;
    }

    static ItemStack stack(String id) {
        Item it = Registries.ITEM.get(Identifier.of(id));
        return it == Items.AIR ? new ItemStack(Items.BOOK) : new ItemStack(it);
    }

    static String categoryOf(Item item) {
        if (EquipmentItems.WEAPON_DEF.containsKey(item) || EquipmentItems.TOOL_DEF.containsKey(item) || EquipmentItems.ARMOR_SET.containsKey(item)
                || EquipmentItems.BOW_DEF.containsKey(item) || item instanceof Accessories.AccessoryItem || item instanceof Upgrades.KitItem
                || item instanceof ArmorItem || item instanceof MiningToolItem || item instanceof net.minecraft.item.SwordItem
                || EquipmentContent.UTILITY.containsKey(Registries.ITEM.getId(item).getPath())) {
            return "equipment";
        }
        if (item instanceof SurvivalItems.MedicineItem || item instanceof dev.underdeep.item.RemedyItem) {
            return "medicine";
        }
        if (new ItemStack(item).contains(DataComponentTypes.FOOD)) {
            return "cooking";
        }
        if (item instanceof BlockItem bi && (bi.getBlock() instanceof net.minecraft.block.PlantBlock || bi.getBlock() instanceof net.minecraft.block.CropBlock)) {
            return "farming";
        }
        if (item instanceof BlockItem bi && (dev.underdeep.survival.SurvivalBlocks.FURNITURE.contains(bi.getBlock())
                || dev.underdeep.survival.SurvivalBlocks.STATIONS.contains(bi.getBlock()))) {
            return "homes";
        }
        return "materials";
    }

    static boolean visible(Page p) {
        return p.req.isEmpty() || Handbook.CLIENT_DISCOVERED.contains(p.req);
    }

    static boolean visible(String id) {
        Page p = pages.get(id);
        return p != null && visible(p);
    }

    // ================================================================== layout

    @Override
    protected void init() {
        build();
        loadBookmarks();
        w = Math.min(width - 16, 440);
        h = Math.min(height - 16, 270);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        search = new TextFieldWidget(textRenderer, x0 + 6, y0 + 6, 104, 14, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search...").formatted(Formatting.DARK_GRAY));
        search.setChangedListener(s -> listScroll = 0);
        addDrawableChild(search);
        if (!visible(current)) {
            current = "welcome";
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void hot(int x, int y, int ww, int hh, Runnable r) {
        hotRects.add(new int[]{x, y, ww, hh});
        hotActions.add(r);
    }

    void open(String id) {
        if (!visible(id)) {
            return;
        }
        if (!id.equals(current)) {
            history.push(current);
            while (history.size() > 50) {
                history.removeLast();
            }
        }
        current = id;
        pageScroll = 0;
        Page p = pages.get(id);
        if (p != null && search.getText().isEmpty()) {
            category = p.cat;
        }
    }

    /** The world blur is drawn once at the start of {@link #render}; Screen.render must not blur again over the panel. */
    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        hotRects.clear();
        hotActions.clear();
        hoverStack = ItemStack.EMPTY;
        hoverText = null;
        super.renderBackground(c, mx, my, delta);
        c.fill(x0, y0, x0 + w, y0 + h, 0xF0201A16);
        c.drawBorder(x0, y0, w, h, 0xFF8B6B3C);
        int lw = 114, mw = 118;
        c.fill(x0 + lw, y0 + 4, x0 + lw + 1, y0 + h - 4, 0xFF5A4630);
        c.fill(x0 + lw + mw, y0 + 4, x0 + lw + mw + 1, y0 + h - 4, 0xFF5A4630);
        drawCategories(c, mx, my, x0 + 4, y0 + 26, lw - 8);
        drawList(c, mx, my, x0 + lw + 4, y0 + 6, mw - 8, h - 12);
        drawPage(c, mx, my, x0 + lw + mw + 6, y0 + 6, w - lw - mw - 12, h - 12);
        super.render(c, mx, my, delta);
        if (!hoverStack.isEmpty()) {
            c.drawItemTooltip(textRenderer, hoverStack, mx, my);
        } else if (hoverText != null) {
            c.drawTooltip(textRenderer, Text.literal(hoverText), mx, my);
        }
    }

    private void drawCategories(DrawContext c, int mx, int my, int x, int y, int cw) {
        int rowH = Math.max(12, Math.min(16, (h - 60) / (cats.size() + 1)));
        for (Cat cat : cats) {
            boolean sel = cat.id().equals(category) && search.getText().isEmpty();
            boolean over = mx >= x && mx < x + cw && my >= y && my < y + rowH;
            if (sel || over) {
                c.fill(x - 1, y - 1, x + cw, y + rowH - 1, sel ? 0xFF5A4630 : 0xFF3A2E22);
            }
            c.getMatrices().push();
            c.getMatrices().translate(x, y - 1, 0);
            c.getMatrices().scale(0.75f, 0.75f, 1);
            c.drawItem(cat.icon(), 0, 0);
            c.getMatrices().pop();
            c.drawText(textRenderer, textRenderer.trimToWidth(cat.title(), cw - 15), x + 15, y + 2, sel ? 0xFFFFD27F : 0xFFE0D6C0, false);
            String id = cat.id();
            hot(x, y, cw, rowH, () -> {
                category = id;
                search.setText("");
                listScroll = 0;
            });
            y += rowH;
        }
        // back and bookmarks
        int by = y0 + h - 16;
        drawButton(c, mx, my, x, by, 34, "Back", () -> {
            if (!history.isEmpty()) {
                current = history.pop();
                pageScroll = 0;
            }
        });
        drawButton(c, mx, my, x + 38, by, 34, bookmarks.contains(current) ? "Unmark" : "Mark", () -> {
            if (!bookmarks.remove(current)) {
                bookmarks.add(current);
            }
            saveBookmarks();
        });
        drawButton(c, mx, my, x + 76, by, cw - 76, "Saved", () -> {
            category = "_bookmarks";
            search.setText("");
            listScroll = 0;
        });
    }

    private void drawButton(DrawContext c, int mx, int my, int x, int y, int bw, String label, Runnable r) {
        boolean over = mx >= x && mx < x + bw && my >= y && my < y + 12;
        c.fill(x, y, x + bw, y + 12, over ? 0xFF6E563A : 0xFF4A3A28);
        c.drawText(textRenderer, label, x + (bw - textRenderer.getWidth(label)) / 2, y + 2, 0xFFE0D6C0, false);
        hot(x, y, bw, 12, r);
    }

    List<Page> listed() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        List<Page> out = new ArrayList<>();
        if (!q.isEmpty()) {
            List<Object[]> scored = new ArrayList<>();
            for (Page p : pages.values()) {
                if (!visible(p)) {
                    continue;
                }
                int s = score(p, q);
                if (s > 0) {
                    scored.add(new Object[]{p, s});
                }
            }
            scored.sort((a, b) -> (Integer) b[1] - (Integer) a[1]);
            for (int i = 0; i < Math.min(80, scored.size()); i++) {
                out.add((Page) scored.get(i)[0]);
            }
            return out;
        }
        if (category.equals("_bookmarks")) {
            for (String b : bookmarks) {
                if (visible(b)) {
                    out.add(pages.get(b));
                }
            }
            return out;
        }
        for (Page p : pages.values()) {
            if (p.cat.equals(category) && visible(p)) {
                out.add(p);
            }
        }
        // authored pages first, then generated ones alphabetically
        out.sort(Comparator.comparing((Page p) -> p.item != null || p.species != null).thenComparing(p -> p.item != null || p.species != null
                ? p.title : ""));
        return out;
    }

    static int score(Page p, String q) {
        String t = p.title.toLowerCase(Locale.ROOT);
        int s = 0;
        if (t.equals(q)) {
            s += 50;
        } else if (t.contains(q)) {
            s += 20;
        }
        for (String word : q.split("\\s+")) {
            if (word.length() < 2 || List.of("how", "to", "the", "a", "an", "do", "i", "my", "where", "find", "get", "what").contains(word)) {
                continue;
            }
            String stem = word.endsWith("s") && word.length() > 3 ? word.substring(0, word.length() - 1) : word;
            if (t.contains(stem)) {
                s += 8;
            }
            for (String k : p.keys) {
                if (k.contains(stem)) {
                    s += 6;
                }
            }
            for (String b : p.body) {
                if (b.toLowerCase(Locale.ROOT).contains(stem)) {
                    s += 1;
                }
            }
        }
        if (p.item == null && p.species == null && s > 0) {
            s += 3; // explanations first for questions
        }
        return s;
    }

    private void drawList(DrawContext c, int mx, int my, int x, int y, int lw, int lh) {
        List<Page> list = listed();
        int rowH = 12, rows = lh / rowH;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, list.size() - rows)));
        if (list.isEmpty()) {
            c.drawText(textRenderer, search.getText().isEmpty() ? "Nothing here yet." : "No matching pages.", x, y + 2, 0xFF9A8A70, false);
        }
        for (int i = 0; i < rows && i + listScroll < list.size(); i++) {
            Page p = list.get(i + listScroll);
            int yy = y + i * rowH;
            boolean sel = p.id.equals(current);
            boolean over = mx >= x && mx < x + lw && my >= yy && my < yy + rowH;
            if (sel || over) {
                c.fill(x - 1, yy, x + lw, yy + rowH, sel ? 0xFF5A4630 : 0xFF3A2E22);
            }
            c.getMatrices().push();
            c.getMatrices().translate(x, yy + 1, 0);
            c.getMatrices().scale(0.625f, 0.625f, 1);
            c.drawItem(p.icon, 0, 0);
            c.getMatrices().pop();
            c.drawText(textRenderer, textRenderer.trimToWidth(p.title, lw - 13), x + 12, yy + 2, p.item == null && p.species == null ? 0xFFFFE6B0
                    : 0xFFE0D6C0, false);
            String id = p.id;
            hot(x, yy, lw, rowH, () -> open(id));
        }
        if (list.size() > rows) {
            int bar = Math.max(10, lh * rows / list.size());
            int pos = (lh - bar) * listScroll / Math.max(1, list.size() - rows);
            c.fill(x + lw + 1, y + pos, x + lw + 3, y + pos + bar, 0xFF8B6B3C);
        }
    }

    // ================================================================== page

    private int cy;

    private void drawPage(DrawContext c, int mx, int my, int x, int y, int pw, int ph) {
        Page p = pages.get(current);
        if (p == null || !visible(p)) {
            return;
        }
        c.enableScissor(x - 2, y, x + pw + 2, y + ph);
        cy = y - pageScroll;
        c.drawItem(p.icon, x, cy);
        for (OrderedText line : textRenderer.wrapLines(Text.literal(p.title).formatted(Formatting.BOLD), pw - 22)) {
            c.drawText(textRenderer, line, x + 20, cy + 4, 0xFFFFD27F, false);
            cy += 10;
        }
        cy += 8;
        for (String para : p.body) {
            paragraph(c, para, x, pw, 0xFFE8E0CC);
        }
        if (p.item != null) {
            itemPage(c, mx, my, p.item, x, pw);
        } else if (p.species != null) {
            speciesPage(c, p.species, x, pw);
        }
        if (!p.items.isEmpty()) {
            label(c, "Related items", x);
            iconRow(c, mx, my, p.items.stream().map(HandbookScreen::stack).toList(), x, pw);
        }
        List<String> see = p.see.stream().filter(HandbookScreen::visible).toList();
        if (!see.isEmpty()) {
            label(c, "See also", x);
            for (String s : see) {
                Page sp = pages.get(s);
                boolean over = mx >= x && mx < x + pw && my >= cy && my < cy + 10 && my >= y && my < y + ph;
                c.drawText(textRenderer, "> " + sp.title, x + 4, cy, over ? 0xFFFFFFFF : 0xFF8FC8FF, false);
                if (cy >= y && cy < y + ph) {
                    hot(x, cy, pw, 10, () -> open(s));
                }
                cy += 11;
            }
        }
        pageContentH = cy + pageScroll - y;
        c.disableScissor();
        if (pageContentH > ph) {
            int bar = Math.max(10, ph * ph / pageContentH);
            int pos = (ph - bar) * pageScroll / Math.max(1, pageContentH - ph);
            c.fill(x + pw + 2, y + pos, x + pw + 4, y + pos + bar, 0xFF8B6B3C);
        }
        // clip hot spots of the page to its visible area
        for (int i = hotRects.size() - 1; i >= 0; i--) {
            int[] r = hotRects.get(i);
            if (r[0] >= x - 2 && r[0] < x + pw + 2 && (r[1] < y || r[1] + r[3] > y + ph + 2)) {
                hotRects.remove(i);
                hotActions.remove(i);
            }
        }
    }

    private void paragraph(DrawContext c, String text, int x, int pw, int color) {
        for (OrderedText line : textRenderer.wrapLines(Text.literal(text), pw)) {
            c.drawText(textRenderer, line, x, cy, color, false);
            cy += 10;
        }
        cy += 4;
    }

    private void label(DrawContext c, String text, int x) {
        cy += 2;
        c.drawText(textRenderer, text, x, cy, 0xFFFFC060, false);
        cy += 11;
    }

    private void iconRow(DrawContext c, int mx, int my, List<ItemStack> stacks, int x, int pw) {
        int ix = x;
        for (ItemStack s : stacks) {
            if (ix + 18 > x + pw) {
                ix = x;
                cy += 18;
            }
            icon(c, mx, my, s, ix, cy, true);
            ix += 18;
        }
        cy += 20;
    }

    private void icon(DrawContext c, int mx, int my, ItemStack s, int ix, int iy, boolean link) {
        c.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0xFF3A2E22);
        c.drawItem(s, ix, iy);
        if (s.getCount() > 1) {
            c.drawItemInSlot(textRenderer, s, ix, iy);
        }
        if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16) {
            hoverStack = s;
        }
        if (link) {
            String id = "item:" + Registries.ITEM.getId(s.getItem());
            hot(ix, iy, 16, 16, () -> open(id));
        }
    }

    private void itemPage(DrawContext c, int mx, int my, Item item, int x, int pw) {
        Identifier id = Registries.ITEM.getId(item);
        String key = "tooltip." + id.getNamespace() + "." + id.getPath();
        if (I18n.hasTranslation(key)) {
            paragraph(c, I18n.translate(key), x, pw, 0xFFE8E0CC);
        }
        for (String line : stats(item)) {
            paragraph(c, line, x, pw, 0xFFB8E0B0);
        }
        Handbook.ItemInfo info = Handbook.CLIENT_ITEMS.get(id.toString());
        label(c, "How to get it", x);
        if (info == null || info.sources().isEmpty()) {
            paragraph(c, info != null && !info.reachable() ? "Not obtainable in ordinary play." : "No information.", x, pw, 0xFFB0A890);
        } else {
            for (String s : info.sources()) {
                paragraph(c, "- " + s, x, pw, 0xFFE8E0CC);
            }
        }
        // recipes that make it
        List<Runnable> boxes = new ArrayList<>();
        int made = 0;
        if (client != null && client.world != null) {
            for (RecipeEntry<?> re : client.world.getRecipeManager().values()) {
                ItemStack out = re.value().getResult(client.world.getRegistryManager());
                if (!out.isEmpty() && out.isOf(item) && made < 6) {
                    if (made == 0) {
                        label(c, "Recipes", x);
                    }
                    made++;
                    recipeBox(c, mx, my, re.value(), x, pw);
                }
            }
        }
        for (SurvivalContent.Recipe r : SurvivalContent.recipes()) {
            if (r.resultItem() == item && made < 10) {
                if (made == 0) {
                    label(c, "Recipes", x);
                }
                made++;
                stationBox(c, mx, my, r, x, pw);
            }
        }
        // uses
        List<ItemStack> usedIn = new ArrayList<>();
        Set<Item> seen = new LinkedHashSet<>();
        if (client != null && client.world != null) {
            for (RecipeEntry<?> re : client.world.getRecipeManager().values()) {
                for (Ingredient ing : re.value().getIngredients()) {
                    if (ing.test(new ItemStack(item))) {
                        ItemStack out = re.value().getResult(client.world.getRegistryManager());
                        if (!out.isEmpty() && seen.add(out.getItem()) && visible("item:" + Registries.ITEM.getId(out.getItem()))) {
                            usedIn.add(out.copyWithCount(1));
                        }
                        break;
                    }
                }
            }
        }
        for (SurvivalContent.Recipe r : SurvivalContent.recipes()) {
            for (SurvivalContent.Ingredient ing : r.ingredients()) {
                if (SurvivalContent.matches(ing, new ItemStack(item)) && seen.add(r.resultItem())
                        && visible("item:" + Registries.ITEM.getId(r.resultItem()))) {
                    usedIn.add(new ItemStack(r.resultItem()));
                }
            }
        }
        if (info != null && !info.uses().isEmpty()) {
            label(c, "Uses", x);
            for (String u : info.uses()) {
                if (!u.equals("ingredient")) {
                    paragraph(c, "- " + u, x, pw, 0xFFE8E0CC);
                }
            }
        }
        if (!usedIn.isEmpty()) {
            label(c, "Used to make", x);
            iconRow(c, mx, my, usedIn.subList(0, Math.min(48, usedIn.size())), x, pw);
        }
    }

    /** Equipment statistics in plain words. */
    static List<String> stats(Item item) {
        List<String> out = new ArrayList<>();
        EquipmentContent.Weapon w = EquipmentItems.WEAPON_DEF.get(item);
        if (w != null) {
            out.add(String.format(Locale.ROOT, "%s: %.1f damage, %.1f attacks per second%s%s.", cap(w.family()), w.damage() + 1, w.speed(),
                    w.reach() > 0 ? String.format(Locale.ROOT, ", +%.1f reach", w.reach()) : w.reach() < 0 ? ", shorter reach" : "",
                    w.knockback() > 0 ? ", knockback" : ""));
        }
        EquipmentContent.Bow b = EquipmentItems.BOW_DEF.get(item);
        if (b != null) {
            out.add(String.format(Locale.ROOT, "Full draw in %.1f s, shot power x%.2f.", b.draw() / 20.0, b.power()));
        }
        String set = EquipmentItems.ARMOR_SET.get(item);
        if (set != null) {
            EquipmentContent.ArmorStats a = EquipmentContent.MATERIALS.get(set).armor();
            StringBuilder sb = new StringBuilder(((ArmorItem) item).getProtection() + " armour");
            if (a.toughness() > 0) {
                sb.append(", ").append(a.toughness()).append(" toughness");
            }
            a.protect().forEach((k, v) -> sb.append(", ").append((int) (v * 100)).append("% less ").append(k).append(" exposure"));
            out.add(sb.append(" (this piece).").toString());
        }
        if (item instanceof MiningToolItem || item instanceof ArmorItem || EquipmentItems.WEAPON_DEF.containsKey(item)) {
            int dur = new ItemStack(item).getMaxDamage();
            if (dur > 0) {
                out.add("Durability " + dur + ". Takes up to " + Upgrades.SLOTS + " upgrades at the artisan's bench.");
            }
        }
        if (item instanceof Accessories.AccessoryItem ai) {
            out.add(cap(ai.def.slot()) + ": " + ai.def.desc());
        }
        if (item instanceof Upgrades.KitItem k) {
            out.add("Fits: " + String.join(", ", k.def.applies()) + ". Kind: " + k.def.group() + " (one per item). " + k.def.desc());
        }
        return out;
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void recipeBox(DrawContext c, int mx, int my, Recipe<?> r, int x, int pw) {
        ItemStack out = r.getResult(client.world.getRegistryManager());
        String station = r instanceof BlastingRecipe ? "Blast furnace" : r instanceof SmokingRecipe ? "Smoker" : r instanceof CampfireCookingRecipe
                ? "Campfire" : r instanceof AbstractCookingRecipe ? "Furnace" : r instanceof StonecuttingRecipe ? "Stonecutter"
                : r instanceof net.minecraft.recipe.SmithingRecipe ? "Smithing table" : "Crafting";
        int startY = cy;
        if (r instanceof ShapedRecipe sr) {
            for (int gy = 0; gy < sr.getHeight(); gy++) {
                for (int gx = 0; gx < sr.getWidth(); gx++) {
                    Ingredient ing = sr.getIngredients().get(gx + gy * sr.getWidth());
                    icon(c, mx, my, cycle(ing), x + gx * 18, cy + gy * 18, !ing.isEmpty());
                }
            }
            int gridH = sr.getHeight() * 18;
            c.drawText(textRenderer, "->", x + sr.getWidth() * 18 + 4, cy + gridH / 2 - 4, 0xFFE0D6C0, false);
            icon(c, mx, my, out, x + sr.getWidth() * 18 + 18, cy + gridH / 2 - 8, false);
            cy += Math.max(gridH, 18) + 2;
        } else {
            int ix = x;
            for (Ingredient ing : r.getIngredients()) {
                if (ing.isEmpty()) {
                    continue;
                }
                icon(c, mx, my, cycle(ing), ix, cy, true);
                ix += 18;
                if (ix > x + pw - 40) {
                    break;
                }
            }
            c.drawText(textRenderer, "->", ix + 2, cy + 4, 0xFFE0D6C0, false);
            icon(c, mx, my, out, ix + 16, cy, false);
            cy += 20;
        }
        c.drawText(textRenderer, station, x, cy, 0xFF9A8A70, false);
        cy += 12;
        if (cy - startY < 0) {
            cy = startY;
        }
    }

    private void stationBox(DrawContext c, int mx, int my, SurvivalContent.Recipe r, int x, int pw) {
        int ix = x;
        for (SurvivalContent.Ingredient ing : r.ingredients()) {
            List<Item> alts = new ArrayList<>(SurvivalContent.alternatives(ing));
            if (alts.isEmpty()) {
                continue;
            }
            Item shown = alts.get((int) (System.currentTimeMillis() / 1000 % alts.size()));
            icon(c, mx, my, new ItemStack(shown, ing.count()), ix, cy, true);
            ix += 18;
        }
        if (r.containerItem() != null) {
            icon(c, mx, my, new ItemStack(r.containerItem(), r.count()), ix, cy, true);
            ix += 18;
        }
        c.drawText(textRenderer, "->", ix + 2, cy + 4, 0xFFE0D6C0, false);
        icon(c, mx, my, r.output(1), ix + 16, cy, false);
        cy += 20;
        c.drawText(textRenderer, textRenderer.trimToWidth(SurvivalContent.stationName(r.station()) + ", " + r.seconds() + " s"
                + (r.civ().isEmpty() ? "" : " (" + cap(r.civ().replace('_', ' ')) + " dish)"), pw), x, cy, 0xFF9A8A70, false);
        cy += 12;
    }

    static ItemStack cycle(Ingredient ing) {
        ItemStack[] m = ing.getMatchingStacks();
        if (m.length == 0) {
            return ItemStack.EMPTY;
        }
        return m[(int) (System.currentTimeMillis() / 1000 % m.length)];
    }

    private void speciesPage(DrawContext c, CreatureSpecies s, int x, int pw) {
        List<String> regs = s.regions().stream().map(r -> dev.underdeep.equipment.EquipmentEffects.pretty(r.id())).toList();
        paragraph(c, "Lives in: " + String.join(", ", regs) + ".", x, pw, 0xFFE8E0CC);
        paragraph(c, "Eats: " + s.diet().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ". Temperament: "
                + s.temperament().name().toLowerCase(Locale.ROOT) + ".", x, pw, 0xFFE8E0CC);
        if (s.tameMethod() != CreatureSpecies.TameMethod.NONE) {
            paragraph(c, "Can be tamed (" + s.tameMethod().name().toLowerCase(Locale.ROOT) + "): see Taming.", x, pw, 0xFFE8E0CC);
        }
        if (s.mountType() != CreatureSpecies.Mount.NONE) {
            paragraph(c, "Once tamed and harnessed it is a " + s.mountType().name().toLowerCase(Locale.ROOT) + " mount.", x, pw, 0xFFE8E0CC);
        }
        String prod = s.product();
        if (!prod.isEmpty()) {
            String what = prod.startsWith("shed:") ? "sheds " + prod.substring(5).replace('_', ' ') : prod.startsWith("shear:") ? "can be sheared for "
                    + prod.substring(6).replace('_', ' ') : prod.equals("milk") ? "can be milked" : prod;
            paragraph(c, "Products: it " + what + ".", x, pw, 0xFFE8E0CC);
        }
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        for (int i = hotRects.size() - 1; i >= 0; i--) {
            int[] r = hotRects.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                hotActions.get(i).run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        int lw = 114, mw = 118;
        if (mx < x0 + lw + mw) {
            listScroll -= (int) Math.signum(vAmount) * 3;
        } else {
            pageScroll = Math.max(0, Math.min(Math.max(0, pageContentH - (h - 12)), pageScroll - (int) Math.signum(vAmount) * 20));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!search.isFocused() && HandbookClient.KEY != null && HandbookClient.KEY.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ================================================================== bookmarks (client file)

    static Path bookmarkFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("config").resolve("underdeep_handbook_bookmarks.txt");
    }

    static void loadBookmarks() {
        if (bookmarksLoaded) {
            return;
        }
        bookmarksLoaded = true;
        try {
            if (Files.exists(bookmarkFile())) {
                bookmarks.addAll(Files.readAllLines(bookmarkFile()));
            }
        } catch (Exception ignored) {
            // bookmarks are a convenience
        }
    }

    static void saveBookmarks() {
        try {
            Files.createDirectories(bookmarkFile().getParent());
            Files.write(bookmarkFile(), bookmarks);
        } catch (Exception ignored) {
            // bookmarks are a convenience
        }
    }
}
