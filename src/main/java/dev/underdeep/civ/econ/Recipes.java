package dev.underdeep.civ.econ;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.underdeep.Underdeep;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The data-driven production registry (Stage 6, {@code data/underdeep/economy/production.json}). A recipe turns
 * inputs into outputs at a worker's workplace ({@code process}) or brings renewable resources into the economy
 * ({@code gather}), limited by the settlement region's ecology and a sustainability pool. Input keys may name a
 * good, a category ({@code @LUMBER}: any good of that category) or {@code @FUELSOLID}; output keys may be
 * {@code @SPECIES} (yield of a native species of the given kinds, by abundance) or {@code @CIVCROP} (the
 * civilization's staple crop).
 */
public final class Recipes {
    private Recipes() {
    }

    public enum Skill {
        AGRICULTURE, FORESTRY, MINING, SMITHING, CARPENTRY, FISHING, COOKING, TRADING, CONSTRUCTION, ANIMAL_CARE, MEDICINE, TRANSPORT,
        RESEARCH, GUARDING, HUNTING, MASONRY, TAILORING, CRAFTING
    }

    public record Recipe(String id, boolean gather, List<String> professions, Set<String> workplace, Map<String, Double> inputs,
                         Map<String, Double> outputs, Map<String, Double> byproducts, double hours, int maxPerDay, Skill skill,
                         Set<String> habitats, Set<String> regions, Set<String> source, String pool, String tool, double fuel) {
        /** Approximate value added per batch at base prices (used to rank alternatives). */
        public double baseGain() {
            double out = 0, in = 0;
            for (var e : outputs.entrySet()) {
                out += e.getValue() * value(e.getKey());
            }
            for (var e : byproducts.entrySet()) {
                out += e.getValue() * value(e.getKey());
            }
            for (var e : inputs.entrySet()) {
                in += e.getValue() * value(e.getKey());
            }
            return out - in - fuel * 0.1;
        }

        private static double value(String key) {
            if (key.startsWith("@")) {
                return key.equals("@CIVCROP") || key.equals("@SPECIES") ? 0.1 : 0.05;
            }
            Goods.Good g = Goods.get(key);
            return g == null ? 0 : g.value();
        }
    }

    private static final Map<String, Recipe> ALL = new LinkedHashMap<>();
    private static final Map<String, List<Recipe>> BY_PROFESSION = new LinkedHashMap<>();

    private static Map<String, Double> map(JsonObject o) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (var e : o.entrySet()) {
            m.put(e.getKey(), e.getValue().getAsDouble());
        }
        return m;
    }

    private static Set<String> set(JsonArray a) {
        Set<String> s = new TreeSet<>();
        for (JsonElement e : a) {
            s.add(e.getAsString());
        }
        return s;
    }

    private static boolean valid(Map<String, Double> m) {
        for (String k : m.keySet()) {
            if (!k.startsWith("@") && Goods.get(k) == null) {
                return false;
            }
        }
        return true;
    }

    public static synchronized void load() {
        if (!ALL.isEmpty()) {
            return;
        }
        Goods.load();
        try (Reader r = new InputStreamReader(Recipes.class.getResourceAsStream("/data/underdeep/economy/production.json"),
                StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("recipes")) {
                JsonObject o = e.getAsJsonObject();
                List<String> profs = new ArrayList<>(set(o.getAsJsonArray("professions")));
                Recipe rec = new Recipe(o.get("id").getAsString(), o.get("kind").getAsString().equals("gather"), profs,
                        set(o.getAsJsonArray("workplace")), map(o.getAsJsonObject("inputs")), map(o.getAsJsonObject("outputs")),
                        map(o.getAsJsonObject("byproducts")), o.get("hours").getAsDouble(), o.get("maxPerDay").getAsInt(),
                        Skill.valueOf(o.get("skill").getAsString()), set(o.getAsJsonArray("habitats")), set(o.getAsJsonArray("regions")),
                        set(o.getAsJsonArray("source")), o.get("pool").getAsString(), o.get("tool").getAsString(), o.get("fuel").getAsDouble());
                if (!valid(rec.inputs) || !valid(rec.outputs) || !valid(rec.byproducts)) {
                    Underdeep.LOGGER.warn("Economy: recipe {} references unknown goods; skipped", rec.id);
                    continue;
                }
                ALL.put(rec.id, rec);
                for (String p : profs) {
                    BY_PROFESSION.computeIfAbsent(p, k -> new ArrayList<>()).add(rec);
                }
            }
        } catch (Exception e) {
            Underdeep.LOGGER.error("Economy: could not load production recipes", e);
        }
        Underdeep.LOGGER.info("Economy: {} production recipes", ALL.size());
    }

    public static List<Recipe> forProfession(String profession) {
        return BY_PROFESSION.getOrDefault(profession, Collections.emptyList());
    }

    public static java.util.Collection<Recipe> all() {
        return Collections.unmodifiableCollection(ALL.values());
    }

    public static Recipe get(String id) {
        return ALL.get(id);
    }

    /** The skill a profession practises (for wages, efficiency and apprenticeships). */
    public static Skill skillOf(String profession) {
        return switch (profession) {
            case "farmer", "mycologist" -> Skill.AGRICULTURE;
            case "forester" -> Skill.FORESTRY;
            case "miner" -> Skill.MINING;
            case "blacksmith", "smelter", "engineer", "lampwright" -> Skill.SMITHING;
            case "carpenter", "boatwright" -> Skill.CARPENTRY;
            case "fisher", "navigator" -> Skill.FISHING;
            case "cook", "baker", "brewer", "innkeeper" -> Skill.COOKING;
            case "merchant" -> Skill.TRADING;
            case "rancher" -> Skill.ANIMAL_CARE;
            case "healer", "herbalist", "alchemist" -> Skill.MEDICINE;
            case "scholar", "astronomer", "cartographer" -> Skill.RESEARCH;
            case "guard" -> Skill.GUARDING;
            case "hunter" -> Skill.HUNTING;
            case "stonemason" -> Skill.MASONRY;
            case "tailor" -> Skill.TAILORING;
            case "gemcutter", "artisan" -> Skill.CRAFTING;
            default -> Skill.CONSTRUCTION;
        };
    }
}
