package dev.underdeep.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.underdeep.Underdeep;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spoiler and audit visibility of items ({@code data/underdeep/guide/visibility.json}): ordinary items are always
 * documented; sensitive ones appear in the handbook only after the player has obtained them; deferred ones belong to
 * a later stage (hidden, excluded from the survival audit); technical ones are not survival content. Loaded on both
 * sides (the file is part of the mod jar).
 */
public final class Sensitivity {
    private Sensitivity() {
    }

    public enum Kind { ORDINARY, SENSITIVE, DEFERRED, TECHNICAL }

    private static Set<String> technical, sensitive, deferred;
    private static List<String> patterns;

    private static synchronized void load() {
        if (technical != null) {
            return;
        }
        technical = new HashSet<>();
        sensitive = new HashSet<>();
        deferred = new HashSet<>();
        patterns = new ArrayList<>();
        try (InputStream in = Sensitivity.class.getResourceAsStream("/data/underdeep/guide/visibility.json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            o.getAsJsonArray("technical").forEach(e -> technical.add(e.getAsString()));
            o.getAsJsonArray("sensitive").forEach(e -> sensitive.add(e.getAsString()));
            o.getAsJsonArray("deferred").forEach(e -> deferred.add(e.getAsString()));
            o.getAsJsonArray("technical_patterns").forEach(e -> patterns.add(e.getAsString()));
        } catch (Exception e) {
            Underdeep.LOGGER.warn("visibility.json missing or invalid", e);
        }
    }

    public static Kind kind(Item item) {
        load();
        String id = Registries.ITEM.getId(item).toString();
        if (technical.contains(id) || patterns.stream().anyMatch(id::contains)) {
            return Kind.TECHNICAL;
        }
        if (deferred.contains(id)) {
            return Kind.DEFERRED;
        }
        return sensitive.contains(id) ? Kind.SENSITIVE : Kind.ORDINARY;
    }
}
