package dev.underdeep.survival;

import com.mojang.serialization.Codec;
import dev.underdeep.registry.Reg;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-player survival state (Stage 7), stored as a persistent Fabric attachment that survives death: recent food
 * groups eaten (variety bonus), recipes known, hazards met (journal), and the last rest time. NBT keys are
 * append-only. Hazard exposure meters are transient and live in {@link Hazards}.
 */
public final class SurvivalData {
    public static final int SCHEMA = 1;
    /** Game time each food group was last eaten (index = FoodGroup ordinal). */
    public final long[] lastAte = new long[SurvivalContent.FoodGroup.values().length];
    public final Set<String> known = new LinkedHashSet<>();
    public final Set<String> hazardsSeen = new LinkedHashSet<>();
    public long lastRest = -1;
    public int lastComfort;
    /** Bumped when known recipes change (client sync). */
    public transient int version;

    public static final Codec<SurvivalData> CODEC = NbtCompound.CODEC.xmap(SurvivalData::fromNbt, SurvivalData::toNbt);
    public static AttachmentType<SurvivalData> TYPE;

    public static void init() {
        TYPE = AttachmentRegistry.<SurvivalData>builder().persistent(CODEC).copyOnDeath().initializer(SurvivalData::new)
                .buildAndRegister(Reg.id("survival"));
    }

    public static SurvivalData of(PlayerEntity p) {
        return p.getAttachedOrCreate(TYPE);
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("schema", SCHEMA);
        n.putLongArray("ate", lastAte);
        n.put("known", list(known));
        n.put("hazards", list(hazardsSeen));
        n.putLong("rest", lastRest);
        n.putInt("comfort", lastComfort);
        return n;
    }

    private static NbtList list(Set<String> s) {
        NbtList l = new NbtList();
        s.forEach(x -> l.add(NbtString.of(x)));
        return l;
    }

    public static SurvivalData fromNbt(NbtCompound n) {
        SurvivalData d = new SurvivalData();
        long[] a = n.getLongArray("ate");
        System.arraycopy(a, 0, d.lastAte, 0, Math.min(a.length, d.lastAte.length));
        n.getList("known", NbtElement.STRING_TYPE).forEach(e -> d.known.add(e.asString()));
        n.getList("hazards", NbtElement.STRING_TYPE).forEach(e -> d.hazardsSeen.add(e.asString()));
        d.lastRest = n.contains("rest") ? n.getLong("rest") : -1;
        d.lastComfort = n.getInt("comfort");
        return d;
    }

    /** Number of distinct food groups eaten within {@code window} ticks of {@code now}. */
    public int variety(long now, long window) {
        int c = 0;
        for (long t : lastAte) {
            if (t > 0 && now - t <= window) {
                c++;
            }
        }
        return c;
    }
}
