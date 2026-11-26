package dev.underdeep.civ.npc;

import dev.underdeep.civ.mind.Bond;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.mind.Preferences;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * The "people" pages of the journal (Stage 5): inhabitants the player has met, how they regard the player
 * (a word, never numbers), preferences the player has learned in conversation, and the companion. Derived
 * from the inhabitants' own minds, so it can never claim something the player did not learn.
 */
public final class PeopleJournal {
    private PeopleJournal() {
    }

    record Entry(SettlementRecord rec, SettlementRecord.InhabitantRecord r, Bond b) {
    }

    static List<Entry> met(ServerPlayerEntity p) {
        List<Entry> out = new ArrayList<>();
        ServerWorld w = p.getServer().getOverworld();
        for (SettlementRecord rec : SettlementState.get(w).all().values()) {
            for (SettlementRecord.InhabitantRecord r : rec.inhabitants) {
                Bond b = r.mind == null ? null : r.mind.bondIfAny(p.getUuid());
                if (b != null && b.familiarity > 0) {
                    out.add(new Entry(rec, r, b));
                }
            }
        }
        out.sort((a, b) -> Long.compare(b.b.lastContact, a.b.lastContact));
        return out;
    }

    public static int pages(ServerPlayerEntity p) {
        return Math.max(1, (met(p).size() + 5) / 6);
    }

    public static List<Text> render(ServerPlayerEntity p, int page) {
        List<Entry> met = met(p);
        List<Text> out = new ArrayList<>();
        if (met.isEmpty()) {
            out.add(Text.translatable("message.underdeep.people.none").formatted(Formatting.GRAY));
            return out;
        }
        out.add(Text.translatable("message.underdeep.people.header", met.size()).formatted(Formatting.GOLD));
        for (int i = page * 6; i < Math.min(met.size(), page * 6 + 6); i++) {
            Entry e = met.get(i);
            Mind m = e.r.mind;
            MutableText line = Text.literal(e.r.name).formatted(e.r.alive ? Formatting.AQUA : Formatting.DARK_GRAY);
            line.append(Text.literal(" — " + e.r.profession.replace('_', ' ') + ", " + e.rec.name).formatted(Formatting.GRAY));
            if (!e.r.alive) {
                line.append(Text.literal(" (gone)").formatted(Formatting.DARK_GRAY));
            } else {
                line.append(Text.literal("; " + e.b.playerWord()).formatted(Formatting.WHITE));
                if (p.getUuid().equals(m.companionOf)) {
                    line.append(Text.literal("; your companion").formatted(Formatting.LIGHT_PURPLE));
                }
                int bits = m.revealed.getOrDefault(p.getUuid(), 0);
                List<String> likes = new ArrayList<>();
                for (int k = 0; k < m.likes.size() && k < 3; k++) {
                    if ((bits & 1 << k) != 0) {
                        likes.add(Preferences.name(m.likes.get(k)));
                    }
                }
                if (!likes.isEmpty()) {
                    line.append(Text.literal("; likes " + String.join(", ", likes)).formatted(Formatting.GREEN));
                }
                if ((bits & 8) != 0 && !m.dislike.isEmpty()) {
                    line.append(Text.literal("; dislikes " + Preferences.name(m.dislike)).formatted(Formatting.RED));
                }
                if ((bits & 16) != 0 && !m.favoritePlace.isEmpty()) {
                    line.append(Text.literal("; favourite place: " + m.favoritePlace).formatted(Formatting.YELLOW));
                }
            }
            out.add(line);
        }
        return out;
    }
}
