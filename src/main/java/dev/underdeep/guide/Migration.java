package dev.underdeep.guide;

import dev.underdeep.civ.econ.EconomyState;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.survival.SurvivalData;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.WildlifeJournal;
import dev.underdeep.wildlife.WildlifeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;

/**
 * One-time, quiet recognition of what a player had already done before Stage 8 (verifiable records only: journal
 * discoveries and tamings, settlements discovered, companions, property and businesses, recipes known, hazards met).
 * Runs inside the join handler, before the player's first advancement sync, so no toasts appear; chat announcements
 * are suppressed while it runs. Never fabricates: everything comes from saved state.
 */
public final class Migration {
    private Migration() {
    }

    public static void migrate(ServerPlayerEntity p) {
        Advancements.Stats st = Advancements.stats(p);
        if (st.migrated || !UnderdeepConfig.get().advancementMigration) {
            st.migrated = true;
            return;
        }
        MinecraftServer server = p.getServer();
        GameRules.BooleanRule announce = server.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS);
        boolean was = announce.get();
        announce.set(false, server);
        try {
            WildlifeJournal j = WildlifeJournal.get(server);
            for (CreatureSpecies sp : WildlifeRegistry.all()) {
                if (j.knows(p.getUuid(), sp.id, WildlifeJournal.Fact.SEEN)) {
                    Advancements.event(p, "creature_discovered", sp.id);
                }
                if (j.knows(p.getUuid(), sp.id, WildlifeJournal.Fact.TAMING)) {
                    Advancements.event(p, "tamed", sp.id);
                }
            }
            for (SettlementRecord r : SettlementState.get(server.getOverworld()).all().values()) {
                if (r.discoveredBy.contains(p.getUuid())) {
                    Advancements.event(p, "settlement_discovered", r.civ.id() + ":" + r.category.id());
                    Advancements.event(p, "civ_met", r.civ.id());
                    Advancements.event(p, "settlement_size_" + r.category.id(), String.valueOf(r.id));
                }
                for (SettlementRecord.InhabitantRecord ir : r.inhabitants) {
                    if (ir.mind != null && p.getUuid().equals(ir.mind.companionOf)) {
                        Advancements.event(p, "companion_recruited", ir.name);
                    }
                }
            }
            EconomyState es = EconomyState.get(server.getOverworld());
            for (EconomyState.Property pr : es.properties.values()) {
                if (p.getUuid().equals(pr.owner)) {
                    Advancements.event(p, "property_bought", String.valueOf(pr.id));
                }
            }
            for (EconomyState.Business b : es.businesses.values()) {
                if (p.getUuid().equals(b.owner)) {
                    Advancements.event(p, "business_opened", String.valueOf(b.id));
                }
            }
            SurvivalData sd = SurvivalData.of(p);
            sd.known.forEach(r -> Advancements.event(p, "recipe_learned", r));
            sd.hazardsSeen.forEach(h -> Advancements.event(p, "hazard_met", h));
        } finally {
            announce.set(was, server);
            st.migrated = true;
        }
    }
}
