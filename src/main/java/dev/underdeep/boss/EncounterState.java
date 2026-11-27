package dev.underdeep.boss;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistent Stage 10 world state (append-only keys): per boss — defeats, first defeat time, players credited with a
 * victory (personal rewards and Sign recovery), whether the world's heirloom was claimed; the Signs set into the
 * Heart Engine door; the world completion record; arena blocks changed by a running fight (restored on load, so a
 * crash mid-fight never leaves a damaged arena). Active fights themselves are not saved: they reset.
 */
public final class EncounterState extends PersistentState {
    public static final String KEY = "underdeep_encounters";

    public static final class BossRecord {
        public int defeats;
        public long firstDefeat = -1;
        public final Set<String> credited = new LinkedHashSet<>();
        public boolean heirloomClaimed;
        public int attempts;
    }

    public final Map<String, BossRecord> bosses = new LinkedHashMap<>();
    public final Set<String> doorSigns = new LinkedHashSet<>();
    public boolean doorOpen;
    public long completedAt = -1;
    public final List<String> completers = new ArrayList<>();
    /** Original states of arena blocks changed by an active fight. */
    public final Map<Long, BlockState> temp = new HashMap<>();

    public BossRecord record(String id) {
        return bosses.computeIfAbsent(id, k -> new BossRecord());
    }

    public boolean defeated(String id) {
        BossRecord r = bosses.get(id);
        return r != null && r.defeats > 0;
    }

    public boolean completed() {
        return completedAt >= 0;
    }

    public static EncounterState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                new PersistentState.Type<>(EncounterState::new, EncounterState::read, null), KEY);
    }

    static EncounterState read(NbtCompound n, RegistryWrapper.WrapperLookup lookup) {
        EncounterState s = new EncounterState();
        NbtCompound b = n.getCompound("bosses");
        for (String id : b.getKeys()) {
            NbtCompound r = b.getCompound(id);
            BossRecord rec = s.record(id);
            rec.defeats = r.getInt("defeats");
            rec.firstDefeat = r.contains("first") ? r.getLong("first") : -1;
            rec.heirloomClaimed = r.getBoolean("heirloom");
            rec.attempts = r.getInt("attempts");
            for (NbtElement e : r.getList("credited", NbtElement.STRING_TYPE)) {
                rec.credited.add(e.asString());
            }
        }
        for (NbtElement e : n.getList("doorSigns", NbtElement.STRING_TYPE)) {
            s.doorSigns.add(e.asString());
        }
        s.doorOpen = n.getBoolean("doorOpen");
        s.completedAt = n.contains("completedAt") ? n.getLong("completedAt") : -1;
        for (NbtElement e : n.getList("completers", NbtElement.STRING_TYPE)) {
            s.completers.add(e.asString());
        }
        for (NbtElement e : n.getList("temp", NbtElement.COMPOUND_TYPE)) {
            NbtCompound t = (NbtCompound) e;
            s.temp.put(t.getLong("pos"), NbtHelper.toBlockState(lookup.getWrapperOrThrow(RegistryKeys.BLOCK), t.getCompound("state")));
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound n, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound b = new NbtCompound();
        for (var e : bosses.entrySet()) {
            NbtCompound r = new NbtCompound();
            BossRecord rec = e.getValue();
            r.putInt("defeats", rec.defeats);
            r.putLong("first", rec.firstDefeat);
            r.putBoolean("heirloom", rec.heirloomClaimed);
            r.putInt("attempts", rec.attempts);
            NbtList l = new NbtList();
            rec.credited.forEach(u -> l.add(NbtString.of(u)));
            r.put("credited", l);
            b.put(e.getKey(), r);
        }
        n.put("bosses", b);
        NbtList d = new NbtList();
        doorSigns.forEach(x -> d.add(NbtString.of(x)));
        n.put("doorSigns", d);
        n.putBoolean("doorOpen", doorOpen);
        n.putLong("completedAt", completedAt);
        NbtList c = new NbtList();
        completers.forEach(x -> c.add(NbtString.of(x)));
        n.put("completers", c);
        NbtList t = new NbtList();
        for (var e : temp.entrySet()) {
            NbtCompound x = new NbtCompound();
            x.putLong("pos", e.getKey());
            x.put("state", NbtHelper.fromBlockState(e.getValue()));
            t.add(x);
        }
        n.put("temp", t);
        return n;
    }

    /** Test hook: round-trips the state through NBT. */
    public static EncounterState roundTrip(EncounterState s, RegistryWrapper.WrapperLookup lookup) {
        return read(s.writeNbt(new NbtCompound(), lookup), lookup);
    }

    public static BlockPos pos(long l) {
        return BlockPos.fromLong(l);
    }
}
