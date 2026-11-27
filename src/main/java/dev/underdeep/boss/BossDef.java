package dev.underdeep.boss;

import net.minecraft.sound.SoundEvent;

import java.util.function.Function;

/**
 * Static definition of a boss (private identities: see docs/private/BOSSES.md).
 *
 * @param sign       Sign-stone item id granted to every participant on their first victory (null = none)
 * @param material   boss material item id (personal loot), {@code heirloom} unique-per-world reward item id
 */
public record BossDef(String id, Tier tier, String title, double health, double damage, double armor, double speed, double chaseSpeed,
                      float width, float height, double poise, String sign, String material, String heirloom, SoundEvent roar,
                      boolean flying, boolean fireImmune, Function<BossEntity, Brain> brain) {
}
