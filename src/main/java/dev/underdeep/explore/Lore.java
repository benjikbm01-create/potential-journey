package dev.underdeep.explore;

import dev.underdeep.guide.Advancements;
import dev.underdeep.guide.Handbook;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Historical records (Stage 9): inscriptions carved into sites and loose fragments found in containers. Reading a
 * record shows it in chat and copies it into the reader's journal (Handbook, "Journal" category). Records speak
 * with the voice of a culture and can contradict each other (deliberately; see private design notes).
 */
public final class Lore {
    private Lore() {
    }

    public static boolean readAt(ServerWorld w, BlockPos pos, ServerPlayerEntity p) {
        Mechanisms.Ctx c = Mechanisms.ctx(w, pos);
        SiteLayout.Ref r = c == null ? null : c.layout().first(pos, SiteLayout.Kind.INSCRIPTION);
        if (r == null) {
            return false;
        }
        read(p, c.layout().inscription(r.index()));
        return true;
    }

    public static void read(ServerPlayerEntity p, String id) {
        ExploreContent.Record r = ExploreContent.RECORDS.get(id);
        if (r == null) {
            p.sendMessage(Text.literal("The writing has faded beyond reading.").formatted(Formatting.GRAY), true);
            return;
        }
        p.sendMessage(Text.literal("— " + r.title() + " —").formatted(Formatting.GOLD), false);
        p.sendMessage(Text.literal(r.text()).formatted(Formatting.GRAY, Formatting.ITALIC), false);
        p.sendMessage(Text.literal("   " + r.voice()).formatted(Formatting.DARK_GRAY), false);
        ExplorerData d = ExplorerData.of(p);
        if (d.records.add(id)) {
            d.cultures.add(r.culture());
            p.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1f, 1f);
            p.sendMessage(Text.literal("Copied into your journal.").formatted(Formatting.DARK_AQUA), true);
            Advancements.event(p, "record_read", id);
            Advancements.event(p, "culture_record", r.culture());
            Handbook.push(p, List.of("record:" + id));
            Explorations.sendJournal(p);
        }
    }

    /** A record suited to where the reader stands, preferring ones they have not read. */
    public static String pickFor(ServerPlayerEntity p) {
        RegionType region = null;
        UnderdeepTerrain t = p.getWorld() instanceof ServerWorld sw ? UnderdeepWorlds.terrain(sw) : null;
        if (t != null) {
            region = t.regionAt(p.getBlockX(), p.getBlockY(), p.getBlockZ());
        }
        ExplorerData d = ExplorerData.of(p);
        List<String> local = new ArrayList<>(), any = new ArrayList<>();
        for (ExploreContent.Record r : ExploreContent.RECORDS.values()) {
            if (r.id().startsWith("inscription_")) {
                continue; // carved texts stay where they were carved
            }
            boolean here = region != null && (r.regions().isEmpty() || r.regions().contains(region.id()));
            if (!d.records.contains(r.id())) {
                (here ? local : any).add(r.id());
            }
        }
        List<String> pool = !local.isEmpty() ? local : !any.isEmpty() ? any : new ArrayList<>(ExploreContent.RECORDS.keySet());
        return pool.get(p.getRandom().nextInt(pool.size()));
    }
}
