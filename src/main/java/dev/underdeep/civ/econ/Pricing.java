package dev.underdeep.civ.econ;

/**
 * Bounded dynamic prices (Stage 6), pure functions so they can be unit-tested.
 * <ul>
 * <li>Target price = base value × scarcity (desired vs free stock, square-root response) × regional factor (goods
 * produced locally are cheaper, imported ones carry transport) × prosperity, bounded to 0.35–3× base (food
 * 0.5–2×).</li>
 * <li>The market price moves 20 % of the way to the target per hour of simulation, so single trades never cause
 * jumps; player trades still move stock and therefore the next target.</li>
 * <li>A merchant sells at {@code ask} and buys at {@code bid}; bid is always at most 75 % of ask, so buying and
 * immediately selling back to the same merchant always loses money (no instant loops).</li>
 * </ul>
 */
public final class Pricing {
    private Pricing() {
    }

    public static double scarcity(double free, double desired, double variation) {
        double ratio = (Math.max(1, desired) + 2) / (Math.max(0, free) + 2);
        double s = Math.pow(ratio, 0.5 * variation);
        return Math.max(0.55, Math.min(2.2, s));
    }

    public static double target(double base, double free, double desired, boolean local, double transport, double prosperity, boolean food,
                                double variation) {
        double regional = local ? 0.9 : 1.08 + Math.min(0.6, transport);
        double t = base * scarcity(free, desired, variation) * regional * (0.95 + 0.1 * prosperity);
        return bound(base, t, food);
    }

    public static double bound(double base, double p, boolean food) {
        double lo = base * (food ? 0.5 : 0.35), hi = base * (food ? 2.0 : 3.0);
        return Math.max(lo, Math.min(hi, p));
    }

    /** Gradual adjustment toward the target over {@code hours}. */
    public static double approach(double price, double target, double hours) {
        double k = 1 - Math.pow(0.8, Math.max(0, hours));
        return price + (target - price) * k;
    }

    /** Sale price to players (merchant margin, relationship discount up to 12 %). */
    public static double ask(double price, double margin, double discount) {
        return price * (1 + margin) * (1 - Math.max(0, Math.min(0.12, discount)));
    }

    /** Price paid to players; never more than 75 % of the ask. */
    public static double bid(double price, double margin, double bonus) {
        double b = price * (1 - margin - 0.04) * (1 + Math.max(0, Math.min(0.05, bonus)));
        return Math.min(b, ask(price, margin, 0.12) * 0.75);
    }

    /**
     * Converts a unit price into a vanilla trade: {@code [emeralds, items]}. Cheap goods are sold in bundles for one
     * emerald (bundle rounded down so the player never pays less than the ask per unit); expensive goods cost whole
     * emeralds (rounded up).
     */
    public static int[] sellBundle(double ask) {
        if (ask < 1) {
            int n = (int) Math.max(1, Math.floor(1 / ask));
            return new int[]{1, Math.min(64, n)};
        }
        return new int[]{(int) Math.min(64, Math.ceil(ask)), 1};
    }

    /** Converts a unit bid into a trade the merchant buys: {@code [items wanted, emeralds paid]} (rounded in the merchant's favour). */
    public static int[] buyBundle(double bid) {
        if (bid < 1) {
            int n = (int) Math.ceil(1 / Math.max(0.01, bid));
            return new int[]{Math.min(64, n), 1};
        }
        return new int[]{1, (int) Math.max(1, Math.floor(bid))};
    }
}
