package dev.underdeep.boss;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A boss's own behaviour: its move set, phases and special mechanics, on top of {@link BossEntity}. */
public abstract class Brain {
    protected final BossEntity b;
    final List<Move> moves = new ArrayList<>();

    protected Brain(BossEntity b) {
        this.b = b;
    }

    /** The move with a synced id (client animation), or null. */
    public Move move(int id) {
        return id >= 1 && id <= moves.size() ? moves.get(id - 1) : null;
    }

    public List<Move> moves() {
        return moves;
    }

    protected Move add(Move m) {
        m.id = (byte) (moves.size() + 1);
        moves.add(m);
        return m;
    }

    /** Health fractions at which the next phase begins (descending). */
    public double[] phaseThresholds() {
        return new double[]{0.5};
    }

    /** Called when a phase begins (0 = the first, on activation). */
    public void onPhase(int phase) {
    }

    /** Every server tick while active. */
    public void tick() {
    }

    /** Whether the boss walks towards its target between moves (false: stationary or self-moving bosses). */
    public boolean walks() {
        return true;
    }

    /** Multiplier of damage taken (weak points, shields, exposed hearts). 0 = immune right now. */
    public double damageTaken(DamageSource src, float amount, LivingEntity attacker) {
        return 1;
    }

    /** A player used an arena device (pylon, brazier, lantern, coil, bell, root heart). */
    public void device(ServerPlayerEntity p, BlockPos pos, String kind) {
    }

    /** Which participant to fight (default: threat with a bias to the nearest). */
    public LivingEntity pickTarget(List<ServerPlayerEntity> players) {
        return b.threatTarget(players);
    }

    public void onReset() {
    }

    // ------------------------------------------------------------------ shared mechanics

    /** A telegraphed ground zone that resolves after a delay (players see the ring and step out). */
    protected record Zone(Vec3d c, double r, long at, double mult, BossDamage kind, ParticleEffect p, double lift) {
    }

    protected final List<Zone> zones = new ArrayList<>();

    protected void zone(Vec3d c, double r, int delay, double mult, BossDamage kind, ParticleEffect p, double lift) {
        double scale = Encounters.cfg().telegraphScale * (dev.underdeep.config.UnderdeepConfig.get().combat.assist ? 1.3 : 1);
        zones.add(new Zone(c, r, b.getWorld().getTime() + Math.max(8, Math.round(delay * scale)), mult, kind, p, lift));
    }

    void tickZones() {
        long now = b.getWorld().getTime();
        for (var it = zones.iterator(); it.hasNext(); ) {
            Zone z = it.next();
            if (now < z.at()) {
                if ((z.at() - now) % 4 == 0) {
                    b.markCircle(z.c(), z.r(), z.p());
                    if (z.at() - now <= 8) {
                        b.markCircle(z.c(), z.r() * 0.5, z.p());
                    }
                }
                continue;
            }
            it.remove();
            b.particles(z.p(), z.c().add(0, 0.5, 0), 24, z.r() * 0.5);
            for (LivingEntity e : b.victims(z.c(), z.r())) {
                double dx = e.getX() - z.c().x, dz = e.getZ() - z.c().z;
                if (dx * dx + dz * dz <= z.r() * z.r() && Math.abs(e.getY() - z.c().y) < 3.5 && b.strike(e, z.mult(), z.kind(), 0) && z.lift() > 0) {
                    e.addVelocity(0, z.lift(), 0);
                    e.velocityModified = true;
                }
            }
        }
    }

    /** Arena devices of a kind (positions from the lair layout). */
    protected List<BlockPos> devices(String kind) {
        var l = b.enc == null ? null : b.enc.layout();
        return l == null ? List.of() : l.marks(kind);
    }

    protected boolean lit(BlockPos p) {
        BlockState s = b.getWorld().getBlockState(p);
        return s.contains(Properties.LIT) && s.get(Properties.LIT);
    }

    /** Lights or darkens a device for the fight (restored when the fight ends). */
    protected void setLit(BlockPos p, boolean on) {
        BlockState s = b.getWorld().getBlockState(p);
        if (s.contains(Properties.LIT) && s.get(Properties.LIT) != on && b.enc != null) {
            b.enc.setTemp(p, s.with(Properties.LIT, on));
        }
    }

    private final Map<BlockPos, int[]> channels = new HashMap<>();

    /** Channelled use of a device: hold use on it; returns true once when complete. */
    protected boolean channel(ServerPlayerEntity p, BlockPos pos, int needed) {
        long now = b.getWorld().getTime();
        int[] c = channels.computeIfAbsent(pos, k -> new int[2]);
        if (now - c[1] > 15) {
            c[0] = 0;
        }
        c[1] = (int) now;
        c[0]++;
        int done = Math.min(needed, c[0]);
        p.sendMessage(Text.literal("■".repeat(done) + "□".repeat(needed - done)).formatted(Formatting.AQUA), true);
        if (b.enc != null) {
            b.enc.participate(p, 0);
        }
        if (c[0] >= needed) {
            c[0] = 0;
            return true;
        }
        return false;
    }

    protected Vec3d at(LivingEntity e) {
        return new Vec3d(e.getX(), e.getY(), e.getZ());
    }

    protected Vec3d centre() {
        return b.enc == null ? b.getPos() : Vec3d.ofBottomCenter(b.enc.center);
    }

    protected List<ServerPlayerEntity> fighters() {
        return b.enc == null ? List.of() : b.enc.fighters();
    }

    public void onDeath() {
    }
}
