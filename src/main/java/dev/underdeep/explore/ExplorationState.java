package dev.underdeep.explore;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared, world-level state of exploration sites, keyed by stable site id (Stage 9). Only sites somebody interacted
 * with have an entry. Keys are append-only (schema 1).
 * <ul>
 *   <li>puzzles: solved flag, progress, items held by counterweights, participants;</li>
 *   <li>traps disarmed, guard rooms woken, shortcuts opened;</li>
 *   <li>reward claims: {@code cache:<i>} (shared / unique) or {@code cache:<i>:<uuid>} (personal / keys) — reward
 *   claims are separate from puzzle state, so resetting a puzzle never re-arms a reward;</li>
 *   <li>discoverers (order), completion (time and participants).</li>
 * </ul>
 */
public final class ExplorationState extends PersistentState {
    public static final String KEY = "underdeep_exploration";

    public static final class PuzzleState {
        public boolean solved;
        public int[] progress = new int[0];
        public final List<ItemStack> held = new ArrayList<>();
        public final Set<UUID> participants = new LinkedHashSet<>();
        public long solvedAt = -1;
    }

    public static final class SiteState {
        public final Map<Integer, PuzzleState> puzzles = new HashMap<>();
        public final Set<Integer> disarmed = new LinkedHashSet<>();
        public final Set<Integer> woken = new LinkedHashSet<>();
        public final Set<String> claims = new LinkedHashSet<>();
        public final Set<UUID> discoverers = new LinkedHashSet<>();
        public final Set<UUID> completedBy = new LinkedHashSet<>();
        public long firstDiscovered = -1, completedAt = -1;

        public PuzzleState puzzle(int i) {
            return puzzles.computeIfAbsent(i, k -> new PuzzleState());
        }
    }

    public final Map<Long, SiteState> sites = new HashMap<>();
    /** Per-world unique artifacts already awarded (never re-awarded, even from other sites). */
    public final Set<String> uniques = new LinkedHashSet<>();
    /** Reports paid per settlement id (keys "site:&lt;id&gt;" / "record:&lt;id&gt;"), so each is paid once per settlement. */
    public final Map<Long, Set<String>> reported = new HashMap<>();
    /** Placed warding bells (overworld block positions). */
    public final it.unimi.dsi.fastutil.longs.LongOpenHashSet wards = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    public static ExplorationState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                new PersistentState.Type<>(ExplorationState::new, ExplorationState::fromNbt, null), KEY);
    }

    public SiteState site(long id) {
        return sites.computeIfAbsent(id, k -> new SiteState());
    }

    public SiteState peek(long id) {
        return sites.get(id);
    }

    public static ExplorationState readForTest(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
        return fromNbt(n, reg);
    }

    static ExplorationState fromNbt(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
        ExplorationState s = new ExplorationState();
        NbtCompound all = n.getCompound("sites");
        for (String k : all.getKeys()) {
            NbtCompound c = all.getCompound(k);
            SiteState st = new SiteState();
            NbtCompound pz = c.getCompound("puzzles");
            for (String pk : pz.getKeys()) {
                NbtCompound pc = pz.getCompound(pk);
                PuzzleState ps = new PuzzleState();
                ps.solved = pc.getBoolean("solved");
                ps.progress = pc.getIntArray("progress");
                ps.solvedAt = pc.getLong("solvedAt");
                NbtList held = pc.getList("held", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < held.size(); i++) {
                    ItemStack.fromNbt(reg, held.getCompound(i)).ifPresent(ps.held::add);
                }
                pc.getList("participants", NbtElement.STRING_TYPE).forEach(e -> ps.participants.add(UUID.fromString(e.asString())));
                st.puzzles.put(Integer.parseInt(pk), ps);
            }
            for (int i : c.getIntArray("disarmed")) {
                st.disarmed.add(i);
            }
            for (int i : c.getIntArray("woken")) {
                st.woken.add(i);
            }
            c.getList("claims", NbtElement.STRING_TYPE).forEach(e -> st.claims.add(e.asString()));
            c.getList("discoverers", NbtElement.STRING_TYPE).forEach(e -> st.discoverers.add(UUID.fromString(e.asString())));
            c.getList("completedBy", NbtElement.STRING_TYPE).forEach(e -> st.completedBy.add(UUID.fromString(e.asString())));
            st.firstDiscovered = c.getLong("firstDiscovered");
            st.completedAt = c.contains("completedAt") ? c.getLong("completedAt") : -1;
            s.sites.put(Long.parseLong(k), st);
        }
        n.getList("uniques", NbtElement.STRING_TYPE).forEach(e -> s.uniques.add(e.asString()));
        NbtCompound rep = n.getCompound("reported");
        for (String k : rep.getKeys()) {
            Set<String> set = new LinkedHashSet<>();
            rep.getList(k, NbtElement.STRING_TYPE).forEach(e -> set.add(e.asString()));
            s.reported.put(Long.parseLong(k), set);
        }
        for (long w : n.getLongArray("wards")) {
            s.wards.add(w);
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
        NbtCompound all = new NbtCompound();
        sites.forEach((id, st) -> {
            NbtCompound c = new NbtCompound();
            NbtCompound pz = new NbtCompound();
            st.puzzles.forEach((i, ps) -> {
                NbtCompound pc = new NbtCompound();
                pc.putBoolean("solved", ps.solved);
                pc.putIntArray("progress", ps.progress);
                pc.putLong("solvedAt", ps.solvedAt);
                NbtList held = new NbtList();
                for (ItemStack st2 : ps.held) {
                    if (!st2.isEmpty()) {
                        held.add(st2.encode(reg));
                    }
                }
                pc.put("held", held);
                pc.put("participants", strings(ps.participants.stream().map(UUID::toString).toList()));
                pz.put(Integer.toString(i), pc);
            });
            c.put("puzzles", pz);
            c.putIntArray("disarmed", st.disarmed.stream().mapToInt(Integer::intValue).toArray());
            c.putIntArray("woken", st.woken.stream().mapToInt(Integer::intValue).toArray());
            c.put("claims", strings(st.claims));
            c.put("discoverers", strings(st.discoverers.stream().map(UUID::toString).toList()));
            c.put("completedBy", strings(st.completedBy.stream().map(UUID::toString).toList()));
            c.putLong("firstDiscovered", st.firstDiscovered);
            c.putLong("completedAt", st.completedAt);
            all.put(Long.toString(id), c);
        });
        n.put("sites", all);
        n.put("uniques", strings(uniques));
        n.putLongArray("wards", wards.toLongArray());
        NbtCompound rep = new NbtCompound();
        reported.forEach((k, v) -> rep.put(Long.toString(k), strings(v)));
        n.put("reported", rep);
        n.putInt("schema", 1);
        return n;
    }

    static NbtList strings(Iterable<String> it) {
        NbtList l = new NbtList();
        it.forEach(s -> l.add(NbtString.of(s)));
        return l;
    }
}
