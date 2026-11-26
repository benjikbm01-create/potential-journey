package dev.underdeep.civ.npc;

import dev.underdeep.civ.settlement.SettlementPlan;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;

/** Persistent settlement records of a world ({@code data/underdeep_settlements.dat}). */
public final class SettlementState extends PersistentState {
    public static final String ID = "underdeep_settlements";
    public static final PersistentState.Type<SettlementState> TYPE = new PersistentState.Type<>(SettlementState::new, SettlementState::fromNbt,
            net.minecraft.datafixer.DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<Long, SettlementRecord> records = new HashMap<>();

    public static SettlementState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    private static SettlementState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        SettlementState s = new SettlementState();
        NbtCompound list = nbt.getCompound("settlements");
        for (String k : list.getKeys()) {
            SettlementRecord r = SettlementRecord.fromNbt(list.getCompound(k));
            if (r.civ != null) {
                s.records.put(r.id, r);
            }
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound list = new NbtCompound();
        records.forEach((k, v) -> list.put(Long.toString(k), v.toNbt()));
        nbt.put("settlements", list);
        return nbt;
    }

    public SettlementRecord get(long id) {
        return records.get(id);
    }

    public SettlementRecord getOrCreate(SettlementPlan plan, long worldSeed, long now) {
        SettlementRecord r = records.get(plan.site().id());
        if (r == null) {
            r = SettlementRecord.create(plan, worldSeed, now);
            records.put(r.id, r);
            markDirty();
        }
        return r;
    }

    public Map<Long, SettlementRecord> all() {
        return records;
    }
}
