package dev.underdeep.civ.npc;

import dev.underdeep.civ.Civilization;
import dev.underdeep.civ.Names;
import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.arch.BuildingKind;
import dev.underdeep.civ.settlement.Professions;
import dev.underdeep.civ.settlement.SettlementCategory;
import dev.underdeep.civ.settlement.SettlementPlan;
import dev.underdeep.world.noise.Hashing;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent record of one settlement and its registered inhabitants (Stage 4 foundation for Stages 5–6).
 * Created deterministically from the plan the first time the settlement becomes relevant; afterwards only
 * events change it (deaths, trades, discoveries). NBT keys are a persistence contract (append-only).
 */
public final class SettlementRecord {
    public long id;
    public String name;
    public Civilization civ;
    public SettlementCategory category;
    public long createdAt;
    public final List<InhabitantRecord> inhabitants = new ArrayList<>();
    public final Set<UUID> discoveredBy = new HashSet<>();
    public int deathsByPlayers;

    /** One registered inhabitant. Positions are world coordinates from the plan's markers. */
    public static final class InhabitantRecord {
        public int index;
        public String name;
        public String profession;
        public long appearance;
        public BlockPos bed;
        public BlockPos work;
        public int home = -1;
        public int workplace = -1;
        public boolean alive = true;
        public long diedAt;
        public int[] tradeUses = new int[0];
        public long restockDay = -1;
        /** Stage 5 hook: per-player standing (unused in Stage 4 beyond trades). */
        public NbtCompound memory = new NbtCompound();

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putInt("i", index);
            n.putString("name", name);
            n.putString("prof", profession);
            n.putLong("look", appearance);
            if (bed != null) {
                n.putLong("bed", bed.asLong());
            }
            if (work != null) {
                n.putLong("work", work.asLong());
            }
            n.putInt("home", home);
            n.putInt("workplace", workplace);
            n.putBoolean("alive", alive);
            n.putLong("died", diedAt);
            n.putIntArray("uses", tradeUses);
            n.putLong("restock", restockDay);
            n.put("memory", memory);
            return n;
        }

        static InhabitantRecord fromNbt(NbtCompound n) {
            InhabitantRecord r = new InhabitantRecord();
            r.index = n.getInt("i");
            r.name = n.getString("name");
            r.profession = n.getString("prof");
            r.appearance = n.getLong("look");
            r.bed = n.contains("bed") ? BlockPos.fromLong(n.getLong("bed")) : null;
            r.work = n.contains("work") ? BlockPos.fromLong(n.getLong("work")) : null;
            r.home = n.getInt("home");
            r.workplace = n.getInt("workplace");
            r.alive = n.getBoolean("alive");
            r.diedAt = n.getLong("died");
            r.tradeUses = n.getIntArray("uses");
            r.restockDay = n.getLong("restock");
            r.memory = n.getCompound("memory");
            return r;
        }
    }

    /** Job slots in a plan: a profession at a marker of a building. */
    private record Job(String profession, int building, BlockPos pos) {
    }

    /** Creates the record of a planned settlement: inhabitants housed in beds, employed at workplaces. */
    public static SettlementRecord create(SettlementPlan plan, long worldSeed, long now) {
        SettlementRecord rec = new SettlementRecord();
        rec.id = plan.site().id();
        rec.name = plan.name();
        rec.civ = plan.site().civ();
        rec.category = plan.site().category();
        rec.createdAt = now;
        long seed = Hashing.hash(worldSeed, rec.id, 0x1A7L);
        Random rnd = new Random(seed);
        List<Job> jobs = new ArrayList<>();
        List<Object[]> beds = new ArrayList<>();
        for (SettlementPlan.Building b : plan.buildings()) {
            for (Blueprint.Marker m : b.blueprint().markers()) {
                BlockPos wp = b.world(m.pos());
                switch (m.type()) {
                    case BED -> beds.add(new Object[]{b.index(), wp});
                    case WORKSTATION, MERCHANT, DOCK -> jobs.add(new Job(professionFor(b, m.tag(), rec.civ), b.index(), wp));
                    case FARM -> jobs.add(new Job(b.kind() == BuildingKind.FARM ? "farmer" : "rancher", b.index(), wp));
                    case GUARD -> jobs.add(new Job("guard", b.index(), wp));
                    default -> {
                    }
                }
            }
        }
        // one worker per distinct job spot type per building is enough; keep order (centre first)
        int people = Math.min(beds.size(), rec.category.maxPeople);
        people = Math.max(Math.min(beds.size(), rec.category.minPeople), people);
        for (int i = 0; i < people; i++) {
            InhabitantRecord r = new InhabitantRecord();
            r.index = i;
            r.appearance = rnd.nextLong();
            r.name = Names.person(rec.civ, r.appearance);
            Object[] bed = beds.get(i);
            r.home = (Integer) bed[0];
            r.bed = (BlockPos) bed[1];
            if (i < jobs.size()) {
                Job j = jobs.get(i);
                r.profession = j.profession;
                r.workplace = j.building;
                r.work = j.pos;
            } else {
                // without a workplace: everyday roles of the culture (they roam their settlement)
                List<String> spec = Professions.specialties(rec.civ);
                r.profession = rnd.nextInt(3) == 0 ? spec.get(rnd.nextInt(spec.size())) : List.of("farmer", "cook", "hunter", "fisher", "merchant")
                        .get(rnd.nextInt(5));
                r.workplace = -1;
            }
            rec.inhabitants.add(r);
        }
        return rec;
    }

    private static String professionFor(SettlementPlan.Building b, String tag, Civilization civ) {
        if (Professions.valid(tag)) {
            return tag;
        }
        return switch (b.kind()) {
            case SHOP, STALL, TRADE_HALL, STOREHOUSE -> "merchant";
            case INN -> "innkeeper";
            case KITCHEN -> "baker";
            case HALL, LANDMARK -> "scholar";
            case LIBRARY -> "scholar";
            case CLINIC -> "healer";
            case DOCK -> "fisher";
            case WORKSHOP -> b.profession().isEmpty() ? Professions.workshopFor(civ, 0) : b.profession();
            default -> "merchant";
        };
    }

    public int alive() {
        int n = 0;
        for (InhabitantRecord r : inhabitants) {
            n += r.alive ? 1 : 0;
        }
        return n;
    }

    // ------------------------------------------------------------------ NBT

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putLong("id", id);
        n.putString("name", name);
        n.putString("civ", civ.id());
        n.putInt("cat", category.ordinal());
        n.putLong("created", createdAt);
        NbtList list = new NbtList();
        for (InhabitantRecord r : inhabitants) {
            list.add(r.toNbt());
        }
        n.put("people", list);
        NbtList disc = new NbtList();
        for (UUID u : discoveredBy) {
            disc.add(NbtString.of(u.toString()));
        }
        n.put("discovered", disc);
        n.putInt("deathsByPlayers", deathsByPlayers);
        return n;
    }

    public static SettlementRecord fromNbt(NbtCompound n) {
        SettlementRecord r = new SettlementRecord();
        r.id = n.getLong("id");
        r.name = n.getString("name");
        r.civ = Civilization.byId(n.getString("civ"));
        r.category = SettlementCategory.values()[Math.min(n.getInt("cat"), SettlementCategory.values().length - 1)];
        r.createdAt = n.getLong("created");
        for (NbtElement e : n.getList("people", NbtElement.COMPOUND_TYPE)) {
            r.inhabitants.add(InhabitantRecord.fromNbt((NbtCompound) e));
        }
        for (NbtElement e : n.getList("discovered", NbtElement.STRING_TYPE)) {
            try {
                r.discoveredBy.add(UUID.fromString(e.asString()));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries
            }
        }
        r.deathsByPlayers = n.getInt("deathsByPlayers");
        return r;
    }
}
