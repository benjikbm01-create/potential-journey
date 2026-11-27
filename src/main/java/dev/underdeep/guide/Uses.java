package dev.underdeep.guide;

import net.minecraft.item.Item;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Uses of items that come from game systems rather than recipes (medicine, upgrades, accessories, feeds, ...).
 * Systems register them at init; the obtainability audit and the handbook read them.
 */
public final class Uses {
    private Uses() {
    }

    private static final Map<Item, Set<String>> EXTRA = new HashMap<>();

    public static void add(Item item, String use) {
        EXTRA.computeIfAbsent(item, k -> new LinkedHashSet<>()).add(use);
    }

    public static Set<String> extra(Item item) {
        return EXTRA.getOrDefault(item, Set.of());
    }
}
