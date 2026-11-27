package dev.underdeep.equipment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure checks of the generated Stage 8 equipment data and the handbook/advancement data (no game bootstrap). */
class EquipmentContentTest {
    static JsonObject json(String path) throws Exception {
        try (var r = new InputStreamReader(EquipmentContentTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    static int size(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e.isJsonArray() ? e.getAsJsonArray().size() : e.getAsJsonObject().size();
    }

    static int stage8(JsonObject o, String key, Set<String> artifacts) {
        int n = 0;
        for (JsonElement e : o.getAsJsonArray(key)) {
            if (!artifacts.contains(e.getAsJsonObject().get("id").getAsString())) {
                n++;
            }
        }
        return n;
    }

    @Test
    void countsAreWithinTheBrief() throws Exception {
        JsonObject o = json("/data/underdeep/equipment/content.json");
        // Stage 9 artifacts are appended to the same lists; the Stage 8 brief counts exclude them
        Set<String> artifacts = new HashSet<>();
        if (o.has("artifacts")) {
            o.getAsJsonArray("artifacts").forEach(e -> artifacts.add(e.getAsString()));
        }
        int weapons = stage8(o, "weapons", artifacts) + size(o, "bows");
        int tools = size(o, "tools") + size(o, "utility");
        assertTrue(size(o, "materials") >= 12 && size(o, "materials") <= 18, "materials");
        assertTrue(weapons >= 35 && weapons <= 50, "weapons " + weapons);
        assertTrue(size(o, "armorSets") >= 12 && size(o, "armorSets") <= 18, "armour sets");
        assertTrue(tools >= 25 && tools <= 40, "tools " + tools);
        int acc = stage8(o, "accessories", artifacts);
        assertTrue(acc >= 20 && acc <= 30, "accessories " + acc);
        assertTrue(size(o, "upgrades") >= 30 && size(o, "upgrades") <= 50, "upgrades");
    }

    @Test
    void weaponFamiliesAreDistinct() throws Exception {
        Map<String, double[]> byFamily = new HashMap<>();
        for (JsonElement e : json("/data/underdeep/equipment/content.json").getAsJsonArray("weapons")) {
            JsonObject w = e.getAsJsonObject();
            if (w.get("material").getAsString().equals("steel")) {
                byFamily.put(w.get("family").getAsString(), new double[]{w.get("damage").getAsDouble(), w.get("speed").getAsDouble(),
                        w.get("reach").getAsDouble()});
            }
        }
        assertTrue(byFamily.get("dagger")[1] > byFamily.get("sword")[1] && byFamily.get("sword")[1] > byFamily.get("greatsword")[1]);
        assertTrue(byFamily.get("greatsword")[0] > byFamily.get("sword")[0] && byFamily.get("dagger")[0] < byFamily.get("sword")[0]);
        assertTrue(byFamily.get("spear")[2] > byFamily.get("sword")[2]);
        // damage per second must not make one family strictly best at everything
        double swordDps = byFamily.get("sword")[0] * byFamily.get("sword")[1], hammerDps = byFamily.get("warhammer")[0] * byFamily.get("warhammer")[1];
        assertTrue(Math.abs(swordDps - hammerDps) / swordDps < 0.5, "sword " + swordDps + " vs warhammer " + hammerDps);
    }

    @Test
    void upgradesHaveKitsGroupsAndCategories() throws Exception {
        Set<String> cats = Set.of("weapon", "tool", "bow", "helmet", "chestplate", "leggings", "boots");
        Set<String> ids = new HashSet<>();
        for (JsonElement e : json("/data/underdeep/equipment/content.json").getAsJsonArray("upgrades")) {
            JsonObject u = e.getAsJsonObject();
            assertTrue(ids.add(u.get("id").getAsString()));
            assertTrue(!u.get("group").getAsString().isEmpty() && !u.get("kit").getAsString().isEmpty(), u.get("id").getAsString());
            assertTrue(u.get("durability").getAsDouble() < 1.0, "durability bonus bounded");
            for (JsonElement a : u.getAsJsonArray("applies")) {
                assertTrue(cats.contains(a.getAsString()), u.get("id") + " applies to " + a);
            }
        }
    }

    @Test
    void processingRecipesAreUniqueAndPositive() throws Exception {
        JsonObject o = json("/data/underdeep/equipment/content.json");
        Set<String> stations = new HashSet<>(o.getAsJsonObject("stations").keySet());
        stations.addAll(json("/data/underdeep/survival/content.json").getAsJsonObject("stations").keySet()); // kits use Stage 7 stations
        Set<String> ids = new HashSet<>();
        for (JsonElement e : o.getAsJsonArray("recipes")) {
            JsonObject r = e.getAsJsonObject();
            assertTrue(ids.add(r.get("id").getAsString()), "duplicate " + r.get("id"));
            assertTrue(stations.contains(r.get("station").getAsString()), r.get("id").getAsString());
            assertTrue(r.get("count").getAsInt() > 0 && r.get("seconds").getAsInt() > 0);
            String result = r.get("result").getAsString();
            for (JsonElement i : r.getAsJsonArray("ingredients")) {
                // no recipe consumes its own product (no circular step)
                assertTrue(!i.getAsJsonArray().get(0).getAsString().equals(result), r.get("id").getAsString());
            }
        }
    }

    @Test
    void handbookLinksResolve() throws Exception {
        JsonObject h = json("/assets/underdeep/handbook/pages.json");
        Set<String> pages = new HashSet<>(), cats = new HashSet<>();
        h.getAsJsonArray("categories").forEach(c -> cats.add(c.getAsJsonObject().get("id").getAsString()));
        h.getAsJsonArray("pages").forEach(p -> pages.add(p.getAsJsonObject().get("id").getAsString()));
        assertEquals(pages.size(), h.getAsJsonArray("pages").size(), "unique page ids");
        for (JsonElement e : h.getAsJsonArray("pages")) {
            JsonObject p = e.getAsJsonObject();
            assertTrue(cats.contains(p.get("cat").getAsString()));
            p.getAsJsonArray("see").forEach(s -> assertTrue(pages.contains(s.getAsString()), p.get("id") + " -> " + s));
        }
    }
}
