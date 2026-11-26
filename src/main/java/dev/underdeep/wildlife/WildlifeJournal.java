package dev.underdeep.wildlife;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player wildlife discovery journal. A species is "seen" when a player is near it; further facts
 * unlock through observation and interaction: what it eats (watching it feed or hunt), its temperament
 * (being warned, chased or approached), its resources (harvesting/hunting), and taming (a hint after a
 * failed attempt, the full method once tamed). Nothing reveals locations. Stored as
 * {@code data/underdeep_journal.dat}: compound per player UUID, int bitmask per species id (append-only).
 */
public final class WildlifeJournal extends PersistentState {
    public enum Fact { SEEN, HABITAT, FOOD, TEMPERAMENT, RESOURCES, TAMING_HINT, TAMING }

    private static final PersistentState.Type<WildlifeJournal> TYPE = new PersistentState.Type<>(WildlifeJournal::new,
            WildlifeJournal::fromNbt, net.minecraft.datafixer.DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Map<UUID, Map<String, Integer>> facts = new HashMap<>();

    public static WildlifeJournal get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "underdeep_journal");
    }

    private static WildlifeJournal fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        WildlifeJournal j = new WildlifeJournal();
        for (String k : nbt.getKeys()) {
            try {
                Map<String, Integer> m = new HashMap<>();
                NbtCompound c = nbt.getCompound(k);
                for (String s : c.getKeys()) {
                    m.put(s, c.getInt(s));
                }
                j.facts.put(UUID.fromString(k), m);
            } catch (IllegalArgumentException ignored) {
                // malformed key
            }
        }
        return j;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        facts.forEach((u, m) -> {
            NbtCompound c = new NbtCompound();
            m.forEach(c::putInt);
            nbt.put(u.toString(), c);
        });
        return nbt;
    }

    public static void learn(ServerPlayerEntity p, CreatureSpecies s, Fact f) {
        WildlifeJournal j = get(p.getServer());
        Map<String, Integer> m = j.facts.computeIfAbsent(p.getUuid(), k -> new HashMap<>());
        int before = m.getOrDefault(s.id, 0);
        int after = before | 1 << Fact.SEEN.ordinal() | 1 << Fact.HABITAT.ordinal() | 1 << f.ordinal();
        if (after != before) {
            m.put(s.id, after);
            j.markDirty();
            if ((before & 1) == 0) {
                p.sendMessage(Text.translatable("message.underdeep.journal.new", Text.translatable("entity.underdeep." + s.id))
                        .formatted(Formatting.GREEN), true);
            }
        }
    }

    public boolean knows(UUID player, String species, Fact f) {
        return (facts.getOrDefault(player, Map.of()).getOrDefault(species, 0) & 1 << f.ordinal()) != 0;
    }

    public int count(UUID player) {
        return facts.getOrDefault(player, Map.of()).size();
    }

    /** Sighting: every 2 s, players notice wildlife within 12 blocks they have line of sight to. */
    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            if (w.getTime() % 40 != 21) {
                return;
            }
            for (ServerPlayerEntity p : w.getPlayers()) {
                for (UnderdeepCreature c : w.getEntitiesByClass(UnderdeepCreature.class, p.getBoundingBox().expand(12), e -> p.canSee(e))) {
                    learn(p, c.species(), c.state() == UnderdeepCreature.WARN ? Fact.TEMPERAMENT : Fact.SEEN);
                    if (c.state() == UnderdeepCreature.EAT) {
                        learn(p, c.species(), Fact.FOOD);
                    }
                }
            }
        });
    }

    /** Text rendering of a player's journal (chat), page = 8 species. */
    public static List<Text> render(ServerPlayerEntity p, int page) {
        WildlifeJournal j = get(p.getServer());
        Map<String, Integer> m = j.facts.getOrDefault(p.getUuid(), Map.of());
        List<CreatureSpecies> known = WildlifeRegistry.all().stream().filter(s -> m.containsKey(s.id)).toList();
        java.util.ArrayList<Text> out = new java.util.ArrayList<>();
        out.add(Text.translatable("message.underdeep.journal.header", known.size(), WildlifeRegistry.all().size()).formatted(Formatting.GOLD));
        int from = Math.max(0, page) * 8;
        for (int i = from; i < Math.min(known.size(), from + 8); i++) {
            CreatureSpecies s = known.get(i);
            int f = m.get(s.id);
            MutableText line = Text.translatable("entity.underdeep." + s.id).formatted(Formatting.AQUA);
            line.append(Text.literal(" — " + regionNames(s)).formatted(Formatting.GRAY));
            if ((f & 1 << Fact.TEMPERAMENT.ordinal()) != 0 || (f & 1 << Fact.FOOD.ordinal()) != 0) {
                line.append(Text.literal("; " + temperament(s)).formatted(Formatting.WHITE));
            }
            if ((f & 1 << Fact.FOOD.ordinal()) != 0) {
                line.append(Text.literal("; eats " + diet(s)).formatted(Formatting.WHITE));
            }
            if ((f & 1 << Fact.RESOURCES.ordinal()) != 0) {
                line.append(Text.translatable("journal.underdeep.resources." + s.id).formatted(Formatting.YELLOW));
            }
            if ((f & 1 << Fact.TAMING.ordinal()) != 0 || (f & 1 << Fact.TAMING_HINT.ordinal()) != 0 && s.tamable()) {
                line.append(Text.translatable("journal.underdeep.taming." + s.id).formatted(Formatting.LIGHT_PURPLE));
            }
            out.add(line);
        }
        if (known.size() > from + 8) {
            out.add(Text.translatable("message.underdeep.journal.more", page + 1).formatted(Formatting.DARK_GRAY));
        }
        return out;
    }

    private static String regionNames(CreatureSpecies s) {
        StringBuilder b = new StringBuilder();
        s.regions().forEach(r -> b.append(b.length() > 0 ? ", " : "").append(r.displayName()));
        return b.toString();
    }

    private static String temperament(CreatureSpecies s) {
        return switch (s.temperament()) {
            case PASSIVE -> "peaceful";
            case SKITTISH -> "shy, flees when approached";
            case CURIOUS -> "curious";
            case NEUTRAL -> "defends itself";
            case TERRITORIAL -> "territorial, warns before charging";
            case PREDATOR -> "a hunter";
            case AMBUSH -> "dangerous ambusher";
        };
    }

    private static String diet(CreatureSpecies s) {
        return switch (s.diet()) {
            case GRAZER -> "grasses and low plants";
            case BROWSER -> "fruit and leaves";
            case FUNGIVORE -> "fungi";
            case LITHOVORE -> "minerals and mineral-loving growth";
            case AQUATIC_GRAZER -> "water plants";
            case NECTAR -> "nectar";
            case OMNIVORE -> "a bit of everything";
            case CARNIVORE -> "other animals";
            case SCAVENGER -> "remains and scraps";
            case FILTER -> "drifting food in the water";
        };
    }

    @SuppressWarnings("unused")
    private static ServerWorld unused(ServerWorld w) {
        return w;
    }
}
