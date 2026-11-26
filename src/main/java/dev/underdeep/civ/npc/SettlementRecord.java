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
        /** Stage 4 placeholder compound (kept for compatibility; Stage 5 state lives in {@link #mind}). */
        public NbtCompound memory = new NbtCompound();
        /** Stage 5 individual mind (personality, needs, memories, relationships ...). Never null after load/creation. */
        public dev.underdeep.civ.mind.Mind mind;

        NbtCompound toNbt(net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
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
            if (mind != null) {
                n.put("mind", mind.toNbt(lookup));
            }
            return n;
        }

        static InhabitantRecord fromNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
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
            if (n.contains("mind")) {
                r.mind = dev.underdeep.civ.mind.Mind.fromNbt(n.getCompound("mind"), lookup);
            }
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
        // round-robin over buildings: every building's first job before any building's second job, so each
        // stall, shop, farm and workshop has its keeper even in small settlements
        java.util.Map<Integer, List<Job>> byBuilding = new java.util.LinkedHashMap<>();
        for (Job j : jobs) {
            byBuilding.computeIfAbsent(j.building, k -> new ArrayList<>()).add(j);
        }
        List<Job> ordered = new ArrayList<>();
        for (int round = 0; ordered.size() < jobs.size(); round++) {
            for (List<Job> l : byBuilding.values()) {
                if (round < l.size()) {
                    ordered.add(l.get(round));
                }
            }
        }
        jobs = ordered;
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
        families(rec, plan, rnd);
        ensureMinds(rec, now);
        return rec;
    }

    /**
     * New settlements: residents of the same home form a household; in family homes the first two are partners and
     * the others their children or siblings, sharing a family name (general-audience family content).
     */
    private static void families(SettlementRecord rec, SettlementPlan plan, Random rnd) {
        java.util.Map<Integer, List<InhabitantRecord>> homes = new java.util.LinkedHashMap<>();
        for (InhabitantRecord r : rec.inhabitants) {
            homes.computeIfAbsent(r.home, k -> new ArrayList<>()).add(r);
        }
        for (List<InhabitantRecord> h : homes.values()) {
            if (h.size() < 2 || h.get(0).home < 0 || h.get(0).home >= plan.buildings().size()) {
                continue;
            }
            BuildingKind kind = plan.buildings().get(h.get(0).home).kind();
            boolean family = kind == BuildingKind.HOUSE || kind == BuildingKind.HOUSE_SMALL || kind == BuildingKind.HOUSE_LARGE
                    || kind == BuildingKind.TENT || kind == BuildingKind.BARN;
            if (!family || rnd.nextInt(5) == 0) {
                continue; // friends or workers sharing a home
            }
            String familyName = h.get(0).name.substring(h.get(0).name.indexOf(' ') + 1);
            for (int i = 1; i < h.size(); i++) {
                InhabitantRecord r = h.get(i);
                r.name = r.name.substring(0, r.name.indexOf(' ')) + " " + familyName;
            }
            rec.pendingKin.add(h);
        }
    }

    /** Family groups created this session (consumed by {@link #ensureMinds}). */
    final transient List<List<InhabitantRecord>> pendingKin = new ArrayList<>();

    /**
     * Gives every inhabitant a mind (new records, and Stage 4 records migrated deterministically from their
     * appearance seed), households from shared homes, coworker and mentor bonds, and family bonds for new families.
     */
    public static void ensureMinds(SettlementRecord rec, long now) {
        boolean changed = false;
        for (InhabitantRecord r : rec.inhabitants) {
            if (r.mind == null) {
                r.mind = dev.underdeep.civ.mind.Mind.create(rec.civ, r.profession, r.appearance, now);
                r.mind.household = r.home;
                r.mind.origin = rec.name;
                changed = true;
            }
        }
        if (!changed && rec.pendingKin.isEmpty()) {
            return;
        }
        for (InhabitantRecord a : rec.inhabitants) {
            for (InhabitantRecord b : rec.inhabitants) {
                if (a == b) {
                    continue;
                }
                if (a.home >= 0 && a.home == b.home) {
                    dev.underdeep.civ.mind.Bond x = a.mind.bond(b.index);
                    if (!x.household) {
                        x.household = true;
                        x.familiarity = Math.max(x.familiarity, 60);
                        x.affection = Math.max(x.affection, 20);
                        x.trust = Math.max(x.trust, 30);
                    }
                }
                if (a.workplace >= 0 && a.workplace == b.workplace) {
                    dev.underdeep.civ.mind.Bond x = a.mind.bond(b.index);
                    if (!x.coworker) {
                        x.coworker = true;
                        x.familiarity = Math.max(x.familiarity, 40);
                        x.respect = Math.max(x.respect, 10);
                        if (a.mind.skill + 25 <= b.mind.skill && a.profession.equals(b.profession)) {
                            x.mentor = true;
                            b.mind.bond(a.index).apprentice = true;
                        }
                    }
                }
            }
        }
        for (List<InhabitantRecord> h : rec.pendingKin) {
            for (int i = 0; i < h.size(); i++) {
                for (int j = 0; j < h.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    dev.underdeep.civ.mind.Bond x = h.get(i).mind.bond(h.get(j).index);
                    x.kin = i < 2 && j < 2 ? dev.underdeep.civ.mind.Bond.Kin.PARTNER
                            : i < 2 ? dev.underdeep.civ.mind.Bond.Kin.CHILD : j < 2 ? dev.underdeep.civ.mind.Bond.Kin.PARENT
                            : dev.underdeep.civ.mind.Bond.Kin.SIBLING;
                    x.affection = Math.max(x.affection, 55);
                    x.trust = Math.max(x.trust, 55);
                    x.familiarity = 90;
                }
                if (i >= 2) {
                    h.get(i).mind.age = dev.underdeep.civ.mind.Mind.Age.YOUNG;
                }
            }
        }
        rec.pendingKin.clear();
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

    public NbtCompound toNbt(net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        NbtCompound n = new NbtCompound();
        n.putLong("id", id);
        n.putString("name", name);
        n.putString("civ", civ.id());
        n.putInt("cat", category.ordinal());
        n.putLong("created", createdAt);
        NbtList list = new NbtList();
        for (InhabitantRecord r : inhabitants) {
            list.add(r.toNbt(lookup));
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

    public static SettlementRecord fromNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        SettlementRecord r = new SettlementRecord();
        r.id = n.getLong("id");
        r.name = n.getString("name");
        r.civ = Civilization.byId(n.getString("civ"));
        r.category = SettlementCategory.values()[Math.min(n.getInt("cat"), SettlementCategory.values().length - 1)];
        r.createdAt = n.getLong("created");
        for (NbtElement e : n.getList("people", NbtElement.COMPOUND_TYPE)) {
            r.inhabitants.add(InhabitantRecord.fromNbt((NbtCompound) e, lookup));
        }
        for (NbtElement e : n.getList("discovered", NbtElement.STRING_TYPE)) {
            try {
                r.discoveredBy.add(UUID.fromString(e.asString()));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries
            }
        }
        r.deathsByPlayers = n.getInt("deathsByPlayers");
        ensureMinds(r, 0); // Stage 4 records gain minds deterministically on first load (migration)
        return r;
    }
}
