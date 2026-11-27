package dev.underdeep.explore;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure checks of the generated Stage 9 exploration data (no game bootstrap). */
class ExploreContentTest {
    static JsonObject content() throws Exception {
        try (var r = new InputStreamReader(ExploreContentTest.class.getResourceAsStream("/data/underdeep/exploration/content.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    @Test
    void familyCountsMeetTheBrief() throws Exception {
        Map<String, Integer> byCat = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement e : content().getAsJsonArray("families")) {
            JsonObject f = e.getAsJsonObject();
            assertTrue(ids.add(f.get("id").getAsString()), "duplicate family " + f.get("id"));
            byCat.merge(f.get("category").getAsString(), 1, Integer::sum);
            assertTrue(f.get("weight").getAsDouble() > 0, "weight " + f.get("id"));
            assertTrue(f.get("radius").getAsInt() >= 2 && f.get("radius").getAsInt() <= 40, "radius " + f.get("id"));
            assertTrue(f.getAsJsonArray("layers").size() > 0, "layers " + f.get("id"));
        }
        assertTrue(byCat.getOrDefault("dungeon", 0) >= 20, "dungeons " + byCat);
        assertTrue(byCat.getOrDefault("ruin", 0) >= 30, "ruins " + byCat);
        assertTrue(byCat.getOrDefault("landmark", 0) >= 15, "landmarks " + byCat);
    }

    @Test
    void recordsArtifactsAndEncountersAreComplete() throws Exception {
        JsonObject o = content();
        Set<String> ids = new HashSet<>();
        for (JsonElement e : o.getAsJsonArray("records")) {
            JsonObject r = e.getAsJsonObject();
            assertTrue(ids.add(r.get("id").getAsString()), "duplicate record");
            assertTrue(!r.get("title").getAsString().isBlank() && !r.get("text").getAsString().isBlank(), "record text " + r.get("id"));
            assertNotNull(r.get("culture"));
        }
        assertTrue(ids.size() >= 40, "records " + ids.size());
        int uniques = 0;
        for (JsonElement e : o.getAsJsonArray("artifacts")) {
            JsonObject a = e.getAsJsonObject();
            assertTrue(Set.of("common", "uncommon", "rare", "epic", "legendary").contains(a.get("rarity").getAsString()), "rarity " + a.get("id"));
            uniques += a.get("unique").getAsBoolean() ? 1 : 0;
        }
        assertTrue(o.getAsJsonArray("artifacts").size() >= 35, "artifacts");
        assertTrue(uniques >= 1, "unique artifacts");
        assertTrue(o.getAsJsonArray("encounters").size() >= 15, "encounters");
        for (JsonElement e : o.getAsJsonArray("encounters")) {
            assertTrue(e.getAsJsonObject().get("cooldown").getAsInt() > 0, "cooldown " + e.getAsJsonObject().get("id"));
        }
        assertEquals(o.getAsJsonArray("secrets").size(), new HashSet<>(o.getAsJsonArray("secrets").asList().stream()
                .map(x -> x.getAsJsonObject().get("id").getAsString()).toList()).size(), "secret ids unique");
    }
}
