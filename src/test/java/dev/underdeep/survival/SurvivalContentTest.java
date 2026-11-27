package dev.underdeep.survival;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure checks of the generated Stage 7 content data (no game bootstrap). */
class SurvivalContentTest {
    static JsonObject root() throws Exception {
        try (var r = new InputStreamReader(SurvivalContentTest.class.getResourceAsStream("/data/underdeep/survival/content.json"),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    @Test
    void countsAreWithinTheBrief() throws Exception {
        JsonObject o = root();
        long drinks = o.getAsJsonObject("foods").entrySet().stream()
                .filter(e -> e.getValue().getAsJsonObject().get("kind").getAsString().equals("bottle")).count();
        int medicines = o.getAsJsonObject("medicines").size() + 5;
        assertTrue(drinks >= 15 && drinks <= 25, "drinks " + drinks);
        assertTrue(medicines >= 20 && medicines <= 30, "medicines " + medicines);
        assertTrue(o.getAsJsonArray("recipes").size() >= 90, "recipes");
    }

    @Test
    void recipesReferenceKnownGroupsAndUniqueIds() throws Exception {
        JsonObject o = root();
        Set<String> groups = o.getAsJsonObject("groups").keySet();
        Set<String> ids = new HashSet<>();
        Set<String> stations = o.getAsJsonObject("stations").keySet();
        for (JsonElement e : o.getAsJsonArray("recipes")) {
            JsonObject r = e.getAsJsonObject();
            assertTrue(ids.add(r.get("id").getAsString()), "duplicate recipe " + r.get("id"));
            assertTrue(stations.contains(r.get("station").getAsString()), "station " + r.get("station"));
            assertTrue(r.get("count").getAsInt() > 0 && r.get("seconds").getAsInt() > 0, r.get("id").getAsString());
            for (JsonElement i : r.getAsJsonArray("ingredients")) {
                String k = i.getAsJsonArray().get(0).getAsString();
                assertTrue(!k.startsWith("#") || groups.contains(k.substring(1)), "group " + k + " in " + r.get("id"));
                assertTrue(i.getAsJsonArray().get(1).getAsInt() > 0, "count in " + r.get("id"));
            }
        }
    }

    @Test
    void mealBuffsAreBounded() throws Exception {
        for (var e : root().getAsJsonObject("foods").entrySet()) {
            for (JsonElement b : e.getValue().getAsJsonObject().getAsJsonArray("buffs")) {
                assertTrue(b.getAsJsonArray().get(1).getAsInt() <= Meals.MAX_FOOD_BUFF_SECONDS, e.getKey());
                assertTrue(b.getAsJsonArray().get(2).getAsInt() <= 1, e.getKey());
            }
        }
    }
}
