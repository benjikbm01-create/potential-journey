package dev.underdeep.equipment;

import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Stage 8 equipment behaviour, all server-side and bounded:
 * <ul>
 *   <li>weapon specials (material): heat sets alight 3 s, frost slows 2 s, static +3 vs wet targets, radiant +3 vs undead,
 *   heavy +25% of the target's armour (max +4), aquatic +3 in water, glimmer marks; upgrade coatings add one on-hit effect;</li>
 *   <li>hazard protection: worn armour (material per piece, upgrades) and accessories reduce Stage 7 exposure, capped at 90%;
 *   copper armour increases static exposure;</li>
 *   <li>flags: lamp (night vision only while it is dark around you), wayfinder (depth/region readout), runner, healer,
 *   trader, forager, angler, climb.</li>
 * </ul>
 */
public final class EquipmentEffects {
    private EquipmentEffects() {
    }

    public static final double PROTECTION_CAP = 0.9;

    static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
            if (!blocked && taken > 0 && source.getAttacker() instanceof LivingEntity attacker && source.getSource() == attacker) {
                onHit(attacker, target);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 7) {
                return;
            }
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                tickPlayer(p, server.getTicks());
            }
        });
    }

    public static RegistryEntry<StatusEffect> effect(Identifier id) {
        return Registries.STATUS_EFFECT.getEntry(id).map(e -> (RegistryEntry<StatusEffect>) e).orElse(null);
    }

    /** Extra melee damage from the attacker's weapon (called from the damage mixin before armour). */
    public static float bonusDamage(LivingEntity attacker, LivingEntity target, float amount) {
        ItemStack w = attacker.getMainHandStack();
        EquipmentContent.Weapon def = EquipmentItems.WEAPON_DEF.get(w.getItem());
        if (def == null) {
            return amount;
        }
        float bonus = 0;
        switch (def.special()) {
            case "static" -> {
                if (target.isWet()) {
                    bonus += 3;
                }
            }
            case "radiant" -> {
                if (target.getType().isIn(EntityTypeTags.UNDEAD)) {
                    bonus += 3;
                }
            }
            case "heavy" -> bonus += (float) Math.min(4.0, target.getArmor() * 0.25);
            case "aquatic" -> {
                if (attacker.isTouchingWater() || target.isTouchingWater()) {
                    bonus += 3;
                }
            }
            default -> {
            }
        }
        return amount + bonus;
    }

    static void onHit(LivingEntity attacker, LivingEntity target) {
        ItemStack w = attacker.getMainHandStack();
        EquipmentContent.Weapon def = EquipmentItems.WEAPON_DEF.get(w.getItem());
        Set<String> fx = new LinkedHashSet<>();
        if (def != null) {
            fx.add(def.special());
        }
        Set<String> ups = Upgrades.flags(w);
        if (ups.contains("venom")) {
            fx.add("venom");
        }
        if (ups.contains("ember")) {
            fx.add("heat");
        }
        if (ups.contains("frost")) {
            fx.add("frost");
        }
        if (ups.contains("glimmer") || ups.contains("glimmer_short")) {
            fx.add(ups.contains("glimmer") ? "glimmer" : "glimmer_short");
        }
        for (String f : fx) {
            switch (f) {
                case "heat" -> target.setOnFireFor(3);
                case "frost" -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0), attacker);
                case "venom" -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0), attacker);
                case "glimmer" -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0), attacker);
                case "glimmer_short" -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0), attacker);
                default -> {
                }
            }
        }
    }

    /** Total protection (0..0.9, may be negative for copper vs static) against a hazard group: heat, cold, spores, fumes, static, pressure. */
    public static double protection(PlayerEntity p, String hazard) {
        double v = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack a = p.getEquippedStack(s);
            String set = EquipmentItems.ARMOR_SET.get(a.getItem());
            if (set != null) {
                v += EquipmentContent.MATERIALS.get(set).armor().protect().getOrDefault(hazard, 0.0);
                if (set.equals("copper") && hazard.equals("static")) {
                    v -= 0.15;
                }
            }
            v += Upgrades.protection(a, hazard);
        }
        v += Accessories.protection(p, hazard);
        return Math.max(-0.6, Math.min(PROTECTION_CAP, v));
    }

    /** Whether worn accessories or worn/held equipment upgrades give a flag. */
    public static boolean has(PlayerEntity p, String flag) {
        if (Accessories.flags(p).contains(flag)) {
            return true;
        }
        for (EquipmentSlot s : EquipmentSlot.values()) {
            if (Upgrades.flags(p.getEquippedStack(s)).contains(flag)) {
                return true;
            }
        }
        return false;
    }

    static void tickPlayer(ServerPlayerEntity p, int ticks) {
        if (p.isSpectator()) {
            return;
        }
        // lamp: night vision only while it is dark around you (refreshed, never long)
        if (has(p, "lamp")) {
            if (p.getWorld().getLightLevel(p.getBlockPos().up()) <= 4) {
                StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
                if (cur == null || (cur.getDuration() < 260 && !cur.isInfinite())) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 300, 0, true, false, true));
                }
            }
        }
        // wayfinder: depth, region and heading every few seconds
        if (ticks % 60 == 7 && has(p, "wayfinder")) {
            p.sendMessage(Text.literal(locate(p)).formatted(Formatting.GRAY), true);
        }
    }

    /** "Depth y=…, region, heading" for gauges and the wayfinder's charm. */
    public static String locate(PlayerEntity p) {
        String region = "";
        if (p.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            UnderdeepTerrain t = UnderdeepWorlds.terrain(sw);
            if (t != null) {
                RegionType r = t.regionAt(p.getBlockX(), p.getBlockY(), p.getBlockZ());
                region = r == null ? "" : ", " + pretty(r.id());
            }
        }
        Direction d = p.getHorizontalFacing();
        return String.format(Locale.ROOT, "Depth y=%d%s, facing %s", p.getBlockY(), region, d.asString());
    }

    public static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("_")) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static boolean isPlayer(Entity e) {
        return e instanceof PlayerEntity;
    }
}
