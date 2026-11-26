package dev.underdeep.civ.econ;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Server-authoritative transactions (Stage 6). Money moves only between accounts and only if the payer has the
 * balance; goods move only out of free (unreserved) stock. Sources (issuance) and sinks (upkeep) are explicit
 * and counted per settlement, so the audit identity
 * {@code money now = initial + issued − sunk + player in − player out + trade in − trade out} holds.
 * A bounded ring of recent transactions is kept for diagnostics.
 */
public final class Tx {
    private Tx() {
    }

    /** Anything that holds money. */
    public interface Account {
        double balance();

        void add(double v);

        String label();
    }

    public record Entry(long id, long time, String kind, String from, String to, double amount, String note) {
    }

    private static final Deque<Entry> LOG = new ArrayDeque<>();
    private static long nextId = 1;
    public static long rejected;

    public static synchronized void log(long time, String kind, String from, String to, double amount, String note) {
        LOG.addFirst(new Entry(nextId++, time, kind, from, to, amount, note));
        while (LOG.size() > 256) {
            LOG.removeLast();
        }
    }

    public static synchronized java.util.List<Entry> recent(int n) {
        return LOG.stream().limit(n).toList();
    }

    public static synchronized long lastId() {
        return nextId - 1;
    }

    /** Moves money if the payer can afford it. Returns false (and changes nothing) otherwise. */
    public static boolean pay(Account from, Account to, double amount, long time, String kind) {
        if (amount <= 0) {
            return amount == 0;
        }
        if (!(amount <= from.balance() + 1e-9) || Double.isNaN(amount)) {
            rejected++;
            return false;
        }
        from.add(-amount);
        to.add(amount);
        log(time, kind, from.label(), to.label(), amount, "");
        return true;
    }

    /** Pays as much as possible up to {@code amount}; returns what was paid. */
    public static double payUpTo(Account from, Account to, double amount, long time, String kind) {
        double a = Math.max(0, Math.min(amount, from.balance()));
        if (a > 0) {
            pay(from, to, a, time, kind);
        }
        return a;
    }

    // ------------------------------------------------------------------ physical emeralds (players)

    public static int emeralds(PlayerEntity p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Goods.currency)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Removes emeralds from a player's inventory; all or nothing. */
    public static boolean takeEmeralds(PlayerEntity p, int n) {
        if (n <= 0) {
            return n == 0;
        }
        if (emeralds(p) < n) {
            rejected++;
            return false;
        }
        int left = n;
        for (int i = 0; i < p.getInventory().size() && left > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Goods.currency)) {
                int t = Math.min(left, s.getCount());
                s.decrement(t);
                left -= t;
            }
        }
        p.getInventory().markDirty();
        return true;
    }

    public static void giveEmeralds(PlayerEntity p, int n) {
        while (n > 0) {
            int k = Math.min(64, n);
            ItemStack s = new ItemStack(Goods.currency, k);
            if (!p.giveItemStack(s)) {
                p.dropItem(s, false);
            }
            n -= k;
        }
    }

    /** Counts items of a good in a player's inventory. */
    public static int count(PlayerEntity p, net.minecraft.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(item) && !s.isDamaged()) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Removes items from a player's inventory; all or nothing. */
    public static boolean takeItems(PlayerEntity p, net.minecraft.item.Item item, int n) {
        if (count(p, item) < n) {
            rejected++;
            return false;
        }
        int left = n;
        for (int i = 0; i < p.getInventory().size() && left > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(item) && !s.isDamaged()) {
                int t = Math.min(left, s.getCount());
                s.decrement(t);
                left -= t;
            }
        }
        p.getInventory().markDirty();
        return true;
    }

    static NbtList save() {
        NbtList l = new NbtList();
        synchronized (Tx.class) {
            NbtCompound c = new NbtCompound();
            c.putLong("next", nextId);
            l.add(c);
        }
        return l;
    }

    static void load(NbtList l) {
        if (!l.isEmpty() && l.get(0) instanceof NbtCompound c) {
            synchronized (Tx.class) {
                nextId = Math.max(nextId, c.getLong("next"));
            }
        }
    }

    static int unused(NbtElement e) {
        return 0;
    }
}
