package dev.underdeep.guide;

import dev.underdeep.wildlife.WildlifeItems;

/** Stage 8 guidance systems: obtainability audit, handbook data, advancements. */
public final class Guide {
    private Guide() {
    }

    public static void init() {
        Uses.add(WildlifeItems.FIELD_JOURNAL, "journal: records your discoveries (wildlife, people, trade, survival notes)");
    }
}
