package dev.underdeep.boss;

import dev.underdeep.Underdeep;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * How a boss attack may be answered. Every boss attack is telegraphed; its kind tells players (and the combat
 * layer) which defence works:
 * <ul>
 *   <li>STRIKE — block or parry (ordinary blows);</li>
 *   <li>SWEEP — block, not parry (wide or heavy blows: parrying a wall of force does not work);</li>
 *   <li>CRUSH — dodge or move away (grabs, charges, slams: shields do not stop them);</li>
 *   <li>SPELL — block (projectiles, breath), parry deflects only with a raised shield;</li>
 *   <li>AURA — hazard zones: step out (dodging through them does not help).</li>
 * </ul>
 */
public enum BossDamage {
    STRIKE("boss_strike"), SWEEP("boss_sweep"), CRUSH("boss_crush"), SPELL("boss_spell"), AURA("boss_aura");

    public final RegistryKey<DamageType> key;

    BossDamage(String id) {
        key = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Underdeep.MOD_ID, id));
    }

    public DamageSource source(World w, Entity boss) {
        return new DamageSource(w.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(key), boss);
    }

    public static BossDamage of(DamageSource s) {
        for (BossDamage d : values()) {
            if (s.isOf(d.key)) {
                return d;
            }
        }
        return null;
    }

    public static boolean unparryable(DamageSource s) {
        BossDamage d = of(s);
        return d == SWEEP || d == CRUSH || d == AURA || d == SPELL && !(s.getSource() instanceof BossProjectile);
    }

    public static boolean undodgeable(DamageSource s) {
        return of(s) == AURA;
    }

    /** Extra stamina drain when a shield blocks this attack. */
    public static double blockDrain(DamageSource s) {
        BossDamage d = of(s);
        return d == SWEEP ? 2.2 : d == null ? 1 : 1.4;
    }
}
