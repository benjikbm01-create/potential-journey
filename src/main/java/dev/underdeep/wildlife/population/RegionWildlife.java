package dev.underdeep.wildlife.population;

import dev.underdeep.ecology.RegionEcology;
import dev.underdeep.ecology.Species.Rarity;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.WildlifeRegistry;
import dev.underdeep.world.region.RegionType;
import net.minecraft.nbt.NbtCompound;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Abstract wildlife population of one region instance (Tier 3 state). Pure logic + NBT, no world
 * access, so it is unit-tested directly.
 * <p>
 * {@code counts} is the authoritative number of <i>wild</i> individuals per species, including any
 * that are currently materialised as entities. Entities never add to it by loading; only births,
 * deaths, migrations, taming/naming and the aggregate step change it.
 * <p>
 * Persistent contract (append-only), compound {@code wildlife} inside the region's
 * {@code UnderdeepWorldState} entry: {@code init} (byte), {@code t} (long, last simulated game time),
 * {@code dist} (double, disturbance from player hunting), {@code p} (compound species → int),
 * {@code kills}, {@code pred}, {@code births}, {@code deaths}, {@code migIn}, {@code migOut} (ints).
 */
public final class RegionWildlife {
    public static final String KEY = "wildlife";
    public final Map<String, Integer> counts = new TreeMap<>();
    public boolean initialized;
    public long lastTime;
    public double disturbance;
    public int playerKills, predation, births, deaths, migIn, migOut;

    /** Carrying capacity of a species in a region (0 if the region is not its habitat). */
    public static int capacity(CreatureSpecies s, RegionType type, long regionId, double densityMul, double rareMul, double disturbance) {
        if (!s.regions().contains(type)) {
            return 0;
        }
        double d = s.density() * densityMul;
        if (s.rarity() == Rarity.RARE || s.rarity() == Rarity.EXCEPTIONAL) {
            // rare species: present in a small deterministic fraction of suitable regions, one individual
            return unit(regionId, s.id.hashCode()) < s.density() * rareMul ? 1 : 0;
        }
        RegionEcology eco = RegionEcology.of(type, regionId);
        double food = switch (s.diet()) {
            case CARNIVORE, SCAVENGER -> 0.8;
            default -> 0.6 + 0.6 * eco.foodAvailability();
        };
        double cap = d * food * (1.0 - Math.min(0.6, disturbance));
        // deterministic regional variation ±25 %
        cap *= 0.75 + 0.5 * unit(regionId, s.id.hashCode() * 31L);
        return (int) Math.max(0, Math.round(cap));
    }

    public void initialize(RegionType type, long regionId, double densityMul, double rareMul, long now) {
        counts.clear();
        for (CreatureSpecies s : WildlifeRegistry.all()) {
            int cap = capacity(s, type, regionId, densityMul, rareMul, 0);
            if (cap > 0) {
                int n = (int) Math.round(cap * (0.6 + 0.4 * unit(regionId, s.id.hashCode() * 7L)));
                counts.put(s.id, Math.max(s.rarity() == Rarity.COMMON ? 1 : 0, n));
            }
        }
        initialized = true;
        lastTime = now;
    }

    public int count(String species) {
        return counts.getOrDefault(species, 0);
    }

    public void add(String species, int delta) {
        int n = Math.max(0, count(species) + delta);
        if (n == 0) {
            counts.remove(species);
        } else {
            counts.put(species, n);
        }
    }

    public int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Aggregate step for an unloaded region over {@code days} in-game days: logistic recovery toward
     * capacity, predation by resident predators, decline above capacity, and decay of disturbance.
     *
     * @return species → individuals that would like to emigrate (overcrowding or no prey); the caller
     *         moves them to neighbouring regions or leaves them (conservation is the caller's job)
     */
    public Map<String, Integer> step(double days, RegionType type, long regionId, double densityMul, double rareMul,
                                     double reproductionMul, double predatorMul, Random rnd) {
        Map<String, Integer> emigrants = new LinkedHashMap<>();
        if (days <= 0) {
            return emigrants;
        }
        // predation first (predators eat prey proportional to both populations, bounded)
        for (CreatureSpecies pred : WildlifeRegistry.all()) {
            int p = count(pred.id);
            if (p == 0 || !pred.isPredator()) {
                continue;
            }
            int preyTotal = 0;
            for (String prey : pred.preyIds()) {
                preyTotal += count(prey);
            }
            double meals = Math.min(preyTotal * 0.15, p * 0.6) * days * predatorMul;
            int kills = probRound(meals, rnd);
            for (int k = 0; k < kills && preyTotal > 0; k++) {
                int pick = rnd.nextInt(preyTotal);
                for (String prey : pred.preyIds()) {
                    int c = count(prey);
                    if (pick < c) {
                        add(prey, -1);
                        predation++;
                        preyTotal--;
                        break;
                    }
                    pick -= c;
                }
            }
            if (preyTotal == 0 && p > 0) {
                emigrants.merge(pred.id, Math.max(1, p / 2), Integer::sum); // hungry predators move on
            }
        }
        // logistic growth / decline toward capacity
        for (CreatureSpecies s : WildlifeRegistry.all()) {
            int cap = capacity(s, type, regionId, densityMul, rareMul, disturbance);
            int n = count(s.id);
            if (cap == 0 && n == 0) {
                continue;
            }
            if (s.rarity() == Rarity.RARE || s.rarity() == Rarity.EXCEPTIONAL) {
                continue; // rare creatures neither breed nor die out in the abstract model
            }
            double r = (s.sizeClass() < 0.7 ? 0.35 : s.sizeClass() < 1.3 ? 0.2 : 0.08) * reproductionMul;
            if (n == 0) {
                // natural recolonisation of an empty but suitable region is handled by migration
                continue;
            }
            if (n < cap) {
                int born = probRound(r * n * (1 - (double) n / cap) * days, rnd);
                add(s.id, born);
                births += born;
            } else if (n > cap) {
                int excess = n - cap;
                int leave = probRound(excess * 0.5 * Math.min(1, days), rnd);
                int die = probRound(excess * 0.2 * Math.min(1, days), rnd);
                if (leave > 0) {
                    emigrants.merge(s.id, leave, Integer::sum);
                }
                add(s.id, -die);
                deaths += die;
            }
        }
        disturbance = Math.max(0, disturbance - 0.4 * days);
        return emigrants;
    }

    static int probRound(double v, Random rnd) {
        int base = (int) Math.floor(v);
        return base + (rnd.nextDouble() < v - base ? 1 : 0);
    }

    static double unit(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putBoolean("init", initialized);
        n.putLong("t", lastTime);
        n.putDouble("dist", disturbance);
        NbtCompound p = new NbtCompound();
        counts.forEach(p::putInt);
        n.put("p", p);
        n.putInt("kills", playerKills);
        n.putInt("pred", predation);
        n.putInt("births", births);
        n.putInt("deaths", deaths);
        n.putInt("migIn", migIn);
        n.putInt("migOut", migOut);
        return n;
    }

    public static RegionWildlife fromNbt(NbtCompound n) {
        RegionWildlife w = new RegionWildlife();
        w.initialized = n.getBoolean("init");
        w.lastTime = n.getLong("t");
        w.disturbance = n.getDouble("dist");
        NbtCompound p = n.getCompound("p");
        for (String k : p.getKeys()) {
            w.counts.put(k, p.getInt(k));
        }
        w.playerKills = n.getInt("kills");
        w.predation = n.getInt("pred");
        w.births = n.getInt("births");
        w.deaths = n.getInt("deaths");
        w.migIn = n.getInt("migIn");
        w.migOut = n.getInt("migOut");
        return w;
    }
}
