package dev.underdeep.explore;

import com.mojang.serialization.Codec;
import dev.underdeep.registry.Reg;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Personal exploration record of one player (attachment {@code underdeep:explorer}, persistent, kept on death;
 * schema 1, append-only keys): discovered sites (in order, with time), discovered secret rooms, read records,
 * artifacts found, rumours heard and charted sites (with source), completed expeditions, personal notes per site.
 */
public final class ExplorerData {
    /** site id → game time discovered (insertion order = discovery order). */
    public final Map<Long, Long> sites = new LinkedHashMap<>();
    public final Set<String> secrets = new LinkedHashSet<>();
    public final Set<String> records = new LinkedHashSet<>();
    public final Set<String> artifacts = new LinkedHashSet<>();
    /** site id → how it is known ("rumour:<speaker>", "chart", "lights"). Only for sites not yet discovered. */
    public final Map<Long, String> leads = new LinkedHashMap<>();
    public final Set<Long> completed = new LinkedHashSet<>();
    public final Map<Long, String> notes = new LinkedHashMap<>();
    public final Set<String> cultures = new LinkedHashSet<>();
    public long lastEncounter = -1;
    public String lastEncounterId = "";

    public static final Codec<ExplorerData> CODEC = NbtCompound.CODEC.xmap(ExplorerData::read, ExplorerData::write);
    public static AttachmentType<ExplorerData> TYPE;

    public static void init() {
        TYPE = AttachmentRegistry.<ExplorerData>builder().persistent(CODEC).copyOnDeath().initializer(ExplorerData::new)
                .buildAndRegister(Reg.id("explorer"));
    }

    public static ExplorerData of(ServerPlayerEntity p) {
        return p.getAttachedOrCreate(TYPE);
    }

    static ExplorerData read(NbtCompound n) {
        ExplorerData d = new ExplorerData();
        NbtCompound s = n.getCompound("sites");
        for (String k : s.getKeys()) {
            d.sites.put(Long.parseLong(k), s.getLong(k));
        }
        n.getList("secrets", NbtElement.STRING_TYPE).forEach(e -> d.secrets.add(e.asString()));
        n.getList("records", NbtElement.STRING_TYPE).forEach(e -> d.records.add(e.asString()));
        n.getList("artifacts", NbtElement.STRING_TYPE).forEach(e -> d.artifacts.add(e.asString()));
        n.getList("cultures", NbtElement.STRING_TYPE).forEach(e -> d.cultures.add(e.asString()));
        NbtCompound l = n.getCompound("leads");
        for (String k : l.getKeys()) {
            d.leads.put(Long.parseLong(k), l.getString(k));
        }
        for (long id : n.getLongArray("completed")) {
            d.completed.add(id);
        }
        NbtCompound notes = n.getCompound("notes");
        for (String k : notes.getKeys()) {
            d.notes.put(Long.parseLong(k), notes.getString(k));
        }
        d.lastEncounter = n.contains("lastEncounter") ? n.getLong("lastEncounter") : -1;
        d.lastEncounterId = n.getString("lastEncounterId");
        return d;
    }

    NbtCompound write() {
        NbtCompound n = new NbtCompound();
        NbtCompound s = new NbtCompound();
        sites.forEach((k, v) -> s.putLong(Long.toString(k), v));
        n.put("sites", s);
        n.put("secrets", list(secrets));
        n.put("records", list(records));
        n.put("artifacts", list(artifacts));
        n.put("cultures", list(cultures));
        NbtCompound l = new NbtCompound();
        leads.forEach((k, v) -> l.putString(Long.toString(k), v));
        n.put("leads", l);
        n.putLongArray("completed", completed.stream().mapToLong(Long::longValue).toArray());
        NbtCompound no = new NbtCompound();
        notes.forEach((k, v) -> no.putString(Long.toString(k), v));
        n.put("notes", no);
        n.putLong("lastEncounter", lastEncounter);
        n.putString("lastEncounterId", lastEncounterId);
        n.putInt("schema", 1);
        return n;
    }

    private static NbtList list(Set<String> set) {
        NbtList l = new NbtList();
        set.forEach(x -> l.add(NbtString.of(x)));
        return l;
    }
}
