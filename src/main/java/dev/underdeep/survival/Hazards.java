package dev.underdeep.survival;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.survival.home.SeatEntity;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Environmental hazards (Stage 7). Each hazard belongs to the dangerous regions where it makes sense, never to the
 * gentle starting regions. Exposure builds slowly (a meter per hazard and player, 0-100), a warning names the
 * hazard and its counterplay at 40, and only full exposure applies the hazard effect (mild, bounded, optionally a
 * little damage). Leaving the area, the right protection effect, shelter, heat or the right gear stops it. Checked
 * once per second per player; nothing runs for players outside the Underdeep.
 */
public final class Hazards {
    private Hazards() {
    }

    /** Hazard kinds (ids appear in the journal; append-only). */
    public enum Hazard {
        LAVA_HEAT("Searing heat", "Lava close by scorches the air. Back away, or use a cooling salve or drink."),
        GEOTHERMAL("Geothermal heat", "The rock itself is hot here. Cooling food, drinks or salves, water and shade help."),
        EXTREME_COLD("Bitter cold", "The cold seeps in. Stand by a fire, wear leather or fur, eat warming food or use a warming salve."),
        ICY_WATER("Icy water", "This water is freezing. Get out quickly; warming salves help."),
        BITING_WIND("Biting wind", "Thin, cold wind on the heights. Warm clothes, warming food or a sheltered spot help."),
        SPORE_CLOUDS("Spore clouds", "Dense spores make you cough. Spore ward tinctures, marsh tea or sporefolk dishes protect you."),
        CHOKING_FUMES("Choking fumes", "Foul air pools in the depths. Climb higher, or drink a clear-air tincture."),
        STATIC_CHARGE("Static charge", "Charged stone sparks at metal. Grounding paste, or putting metal tools away, helps."),
        DEEP_PRESSURE("Deep pressure", "The deep water presses on you. Water breathing or a breathing draught lets you endure it."),
        ABYSSAL_GLOOM("Abyssal gloom", "Utter darkness weighs on the mind. Carry light, or take night vision."),
        WOUNDS("Wounds", "A heavy blow leaves a wound that slows you. Bandages or a field medkit treat it; it heals in time."),
        WEARINESS("Weariness", "Days without rest wear you down. Sleep, or sit a while somewhere comfortable."),
        TOXIC_FLORA("Toxic flora", "Some plants sting or poison. Learn which, and carry an antidote or antiserum.");

        public final String title, advice;

        Hazard(String title, String advice) {
            this.title = title;
            this.advice = advice;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static final class Meter {
        final EnumMap<Hazard, Float> level = new EnumMap<>(Hazard.class);
        final EnumMap<Hazard, Long> warned = new EnumMap<>(Hazard.class);
        int sitSeconds;
    }

    private static final Map<UUID, Meter> METERS = new HashMap<>();
    public static final float WARN = 40, FULL = 100;

    public static float exposure(ServerPlayerEntity p, Hazard h) {
        Meter m = METERS.get(p.getUuid());
        return m == null ? 0 : m.level.getOrDefault(h, 0f);
    }

    public static void reset(ServerPlayerEntity p) {
        METERS.remove(p.getUuid());
    }

    public static void forget(UUID id) {
        METERS.remove(id);
    }

    /** Once per second for each player. */
    public static void tick(ServerPlayerEntity p) {
        UnderdeepConfig.Survival cfg = UnderdeepConfig.get().survival;
        if (!cfg.enabled || p.isSpectator()) {
            return;
        }
        Meter m = METERS.computeIfAbsent(p.getUuid(), k -> new Meter());
        ServerWorld w = p.getServerWorld();
        restTick(p, m);
        if (cfg.weariness && SurvivalData.of(p).lastRest >= 0 && w.getTime() - SurvivalData.of(p).lastRest > 3 * 24000L) {
            Meals.apply(p, SurvivalEffects.WEARY, 20 * 30, 0, false);
            seen(p, Hazard.WEARINESS);
        }
        if (!cfg.hazards || p.isCreative()) {
            m.level.clear();
            return;
        }
        UnderdeepTerrain t = UnderdeepWorlds.terrain(w);
        RegionType r = t == null ? null : t.regionAt(p.getBlockX(), p.getBlockY(), p.getBlockZ());
        EnumMap<Hazard, Float> rate = new EnumMap<>(Hazard.class);
        if (r != null) {
            exposures(p, w, r, rate);
        }
        double mult = cfg.hazardRate;
        StatusEffectInstance wf = p.getStatusEffect(SurvivalEffects.WELL_FED);
        if (wf != null && wf.getAmplifier() >= 1) {
            mult *= 0.75;
        }
        for (Hazard h : Hazard.values()) {
            if (h == Hazard.WOUNDS || h == Hazard.WEARINESS || h == Hazard.TOXIC_FLORA) {
                continue;
            }
            float cur = m.level.getOrDefault(h, 0f);
            float gain = rate.getOrDefault(h, 0f);
            float next = gain > 0 ? Math.min(FULL, cur + (float) (gain * mult)) : Math.max(0, cur - 6f);
            if (next <= 0) {
                m.level.remove(h);
            } else {
                m.level.put(h, next);
            }
            if (gain > 0 && next >= WARN && cur < WARN) {
                long now = w.getTime();
                if (now - m.warned.getOrDefault(h, -100000L) > 20 * 60) {
                    m.warned.put(h, now);
                    p.sendMessage(Text.literal(h.title + ": " + h.advice).formatted(Formatting.GOLD), false);
                }
                seen(p, h);
            }
            if (next >= FULL && gain > 0) {
                apply(p, w, h, cfg.hazardDamage);
            }
        }
    }

    static void seen(ServerPlayerEntity p, Hazard h) {
        if (!SurvivalData.of(p).hazardsSeen.contains(h.id())) {
            dev.underdeep.guide.Advancements.event(p, "hazard_met", h.id());
        }
        SurvivalData.of(p).hazardsSeen.add(h.id());
    }

    /** Exposure gain per second for each hazard active at the player's position (protection already applied). */
    public static void exposures(ServerPlayerEntity p, ServerWorld w, RegionType r, EnumMap<Hazard, Float> out) {
        BlockPos pos = p.getBlockPos();
        boolean inWater = p.isSubmergedIn(FluidTags.WATER) || p.isTouchingWater();
        boolean shelter = near(w, pos, 3, s -> s.isOf(SurvivalBlocks.CAMP_AWNING));
        switch (r) {
            case ASHEN_HOLLOWS, CRIMSON_BASIN -> {
                float heat = inWater ? 0 : 2.5f;
                if (near(w, pos, 3, s -> s.isOf(Blocks.LAVA) || s.isOf(Blocks.MAGMA_BLOCK))) {
                    out.put(Hazard.LAVA_HEAT, protect(p, SurvivalEffects.COOLED, 6f, shelter ? 0.3f : 0));
                    heat *= 0.5f;
                }
                if (heat > 0) {
                    out.put(Hazard.GEOTHERMAL, protect(p, SurvivalEffects.COOLED, heat, shelter ? 0.4f : 0));
                }
                if (r == RegionType.CRIMSON_BASIN && pos.getY() < -30) {
                    out.put(Hazard.CHOKING_FUMES, protect(p, SurvivalEffects.CLEAR_LUNGS, 2f, 0));
                }
            }
            case EVERFROST -> {
                if (inWater) {
                    out.put(Hazard.ICY_WATER, protect(p, SurvivalEffects.WARMED, 12f, 0));
                } else if (!warmth(w, pos)) {
                    out.put(Hazard.EXTREME_COLD, protect(p, SurvivalEffects.WARMED, 2.5f * clothing(p), shelter ? 0.4f : 0));
                }
            }
            case HOLLOW_PEAKS -> {
                if (pos.getY() > 60 && w.getLightLevel(LightType.SKY, pos) >= 0 && !warmth(w, pos)) {
                    out.put(Hazard.BITING_WIND, protect(p, SurvivalEffects.WARMED, 1.5f * clothing(p), shelter ? 0.5f : 0));
                }
            }
            case FUNGAL_EXPANSE -> {
                if (!inWater && dense(w, pos)) {
                    out.put(Hazard.SPORE_CLOUDS, protect(p, SurvivalEffects.SPORE_WARD, 3f, 0));
                    if (w.random.nextInt(3) == 0) {
                        w.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, p.getX(), p.getY() + 1.2, p.getZ(), 3, 1.0, 0.6, 1.0, 0.0);
                    }
                }
            }
            case THE_ABYSS -> {
                if (pos.getY() < -40 && !inWater) {
                    out.put(Hazard.CHOKING_FUMES, protect(p, SurvivalEffects.CLEAR_LUNGS, 2f, 0));
                }
                if (p.isSubmergedIn(FluidTags.WATER) && pos.getY() < -30) {
                    out.put(Hazard.DEEP_PRESSURE, p.hasStatusEffect(StatusEffects.WATER_BREATHING) || p.hasStatusEffect(StatusEffects.CONDUIT_POWER)
                            ? 0f : 5f);
                }
                if (w.getLightLevel(pos.up()) == 0 && !holdingLight(p) && !p.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                    out.put(Hazard.ABYSSAL_GLOOM, 1.5f);
                }
            }
            case STORMSTONE_CAVERNS -> {
                float metal = metal(p);
                if (metal > 0) {
                    out.put(Hazard.STATIC_CHARGE, protect(p, SurvivalEffects.GROUNDED, 2.5f * metal, 0));
                    if (w.random.nextInt(4) == 0) {
                        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 4, 0.4, 0.5, 0.4, 0.05);
                    }
                }
            }
            default -> {
            }
        }
        // Stage 8: worn armour, upgrades and accessories reduce (copper: increase) exposure
        out.replaceAll((h, v) -> (float) (v * (1 - dev.underdeep.equipment.EquipmentEffects.protection(p, group(h)))));
        out.values().removeIf(v -> v <= 0);
    }

    /** Equipment protection group of a hazard: heat, cold, spores, fumes, static, pressure. */
    public static String group(Hazard h) {
        return switch (h) {
            case LAVA_HEAT, GEOTHERMAL -> "heat";
            case EXTREME_COLD, ICY_WATER, BITING_WIND -> "cold";
            case SPORE_CLOUDS -> "spores";
            case CHOKING_FUMES -> "fumes";
            case STATIC_CHARGE -> "static";
            case DEEP_PRESSURE -> "pressure";
            default -> "other";
        };
    }

    static float protect(ServerPlayerEntity p, RegistryEntry<StatusEffect> ward, float rate, float extra) {
        if (ward != null && p.hasStatusEffect(ward)) {
            return 0;
        }
        return rate * (1 - extra);
    }

    /** 1 with no warm clothing, down to 0.2 in a full set of leather (or fur-lined) armour. */
    static float clothing(ServerPlayerEntity p) {
        int warm = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack a = p.getEquippedStack(s);
            if (a.getItem() instanceof ArmorItem ai && ai.getMaterial() == ArmorMaterials.LEATHER) {
                warm++;
            }
        }
        return 1f - 0.2f * warm;
    }

    /** Metal held or worn (0 = none): iron, gold, copper or netherite tools/armour attract static. */
    static float metal(ServerPlayerEntity p) {
        float m = 0;
        for (EquipmentSlot s : EquipmentSlot.values()) {
            ItemStack st = p.getEquippedStack(s);
            if (st.isEmpty()) {
                continue;
            }
            String id = Registries.ITEM.getId(st.getItem()).getPath();
            if (id.contains("iron") || id.contains("gold") || id.contains("copper") || id.contains("netherite") || id.contains("chainmail")) {
                m += s.isArmorSlot() ? 0.25f : 0.5f;
            }
        }
        return Math.min(1.5f, m);
    }

    static boolean holdingLight(ServerPlayerEntity p) {
        for (ItemStack s : new ItemStack[]{p.getMainHandStack(), p.getOffHandStack()}) {
            if (s.isOf(Items.TORCH) || s.isOf(Items.LANTERN) || s.isOf(Items.SOUL_TORCH) || s.isOf(Items.SOUL_LANTERN) || s.isOf(Items.GLOWSTONE)
                    || s.isOf(Items.SEA_LANTERN) || s.isOf(Items.SHROOMLIGHT) || s.isOf(Items.GLOW_BERRIES)) {
                return true;
            }
        }
        return false;
    }

    /** A fire, lit campfire, furnace, lava or a hot station within 4 blocks. */
    public static boolean warmth(ServerWorld w, BlockPos pos) {
        return near(w, pos, 4, Hazards::hot);
    }

    static boolean hot(BlockState s) {
        if (s.isIn(BlockTags.CAMPFIRES)) {
            return s.contains(CampfireBlock.LIT) && s.get(CampfireBlock.LIT);
        }
        if (s.getBlock() instanceof AbstractFurnaceBlock) {
            return s.get(AbstractFurnaceBlock.LIT);
        }
        return s.isIn(BlockTags.FIRE) || s.isOf(Blocks.LAVA) || s.isOf(Blocks.MAGMA_BLOCK) || s.isIn(SurvivalBlocks.HEAT_SOURCES);
    }

    static boolean near(ServerWorld w, BlockPos c, int r, java.util.function.Predicate<BlockState> test) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (test.test(w.getBlockState(m))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Dense fungal growth around: at least 6 fungal blocks in a small sample. */
    static boolean dense(ServerWorld w, BlockPos c) {
        int n = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState s = w.getBlockState(m);
                    Identifier id = Registries.BLOCK.getId(s.getBlock());
                    String path = id.getPath();
                    if (s.isIn(BlockTags.WART_BLOCKS) || s.isOf(Blocks.MUSHROOM_STEM) || s.isOf(Blocks.RED_MUSHROOM_BLOCK)
                            || s.isOf(Blocks.BROWN_MUSHROOM_BLOCK) || ("underdeep".equals(id.getNamespace())
                            && (path.contains("cap") || path.contains("spore") || path.contains("mycel")))) {
                        if (++n >= 6) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Full exposure: refresh the hazard effect, and deal a little damage every few seconds when enabled. */
    static void apply(ServerPlayerEntity p, ServerWorld w, Hazard h, boolean damage) {
        int every = 4;
        switch (h) {
            case LAVA_HEAT, GEOTHERMAL -> {
                Meals.apply(p, SurvivalEffects.HEAT_STRESS, 200, 0, false);
                if (damage && w.getTime() / 20 % every == 0) {
                    p.damage(w.getDamageSources().hotFloor(), 1f);
                }
            }
            case EXTREME_COLD, ICY_WATER, BITING_WIND -> {
                Meals.apply(p, SurvivalEffects.COLD_STRESS, 200, 0, false);
                p.setFrozenTicks(Math.min(p.getMinFreezeDamageTicks() - 1, p.getFrozenTicks() + 30));
                if (damage && w.getTime() / 20 % every == 0) {
                    p.damage(w.getDamageSources().freeze(), 1f);
                }
            }
            case SPORE_CLOUDS -> Meals.apply(p, SurvivalEffects.SPORE_IRRITATION, 200, 0, false);
            case CHOKING_FUMES -> {
                Meals.apply(p, SurvivalEffects.CHOKING_FUMES, 200, 0, false);
                if (damage && w.getTime() / 20 % every == 0) {
                    p.damage(w.getDamageSources().drown(), 1f);
                }
            }
            case STATIC_CHARGE -> {
                Meals.apply(p, SurvivalEffects.STATIC_SHOCK, 200, 0, false);
                if (damage && w.getTime() / 20 % 6 == 0) {
                    p.damage(w.getDamageSources().lightningBolt(), 1f);
                }
            }
            case DEEP_PRESSURE -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 200, 0, false, true, true));
            case ABYSSAL_GLOOM -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0, false, true, true));
            default -> {
            }
        }
    }

    /** A heavy single hit leaves a wound (configurable). */
    public static void afterDamage(ServerPlayerEntity p, float taken) {
        UnderdeepConfig.Survival cfg = UnderdeepConfig.get().survival;
        if (cfg.enabled && cfg.injuries && taken >= 7f && !p.isCreative()) {
            Meals.apply(p, SurvivalEffects.WOUNDED, 20 * 60 * 5, 0, false);
            seen(p, Hazard.WOUNDS);
            p.sendMessage(Text.literal("You are wounded. A bandage would help.").formatted(Formatting.RED), true);
        }
    }

    /** Sitting on furniture for 30 seconds counts as rest. */
    static void restTick(ServerPlayerEntity p, Meter m) {
        if (p.getVehicle() instanceof SeatEntity) {
            if (++m.sitSeconds == 30) {
                SurvivalData.of(p).lastRest = p.getServerWorld().getTime();
                p.removeStatusEffect(SurvivalEffects.WEARY);
                int score = Comfort.score(p.getServerWorld(), p.getBlockPos(), p);
                Meals.apply(p, SurvivalEffects.RESTED, 20 * 60 * Math.min(6, 1 + score / 2), 0, false);
                p.sendMessage(Text.literal("You rest a while.").formatted(Formatting.AQUA), true);
                dev.underdeep.guide.Advancements.event(p, "sat", "");
            }
        } else {
            m.sitSeconds = 0;
        }
    }
}
