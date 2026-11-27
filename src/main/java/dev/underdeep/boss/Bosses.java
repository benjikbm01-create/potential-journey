package dev.underdeep.boss;

import dev.underdeep.Underdeep;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Stage 10 boss roster (13). Identities, mechanics and locations are private (docs/private/BOSSES.md); public
 * documentation only states counts per tier. Health values are for one player and are scaled by the number of
 * participants at awakening ({@link Encounters}); fights are long because of their mechanics, not their health.
 */
public final class Bosses {
    private Bosses() {
    }

    public static final Map<String, BossDef> DEFS = new LinkedHashMap<>();
    private static final Map<String, EntityType<BossEntity>> TYPES = new HashMap<>();
    private static final Map<EntityType<?>, BossDef> BY_TYPE = new HashMap<>();

    static void def(BossDef d) {
        DEFS.put(d.id(), d);
    }

    static {
        def(new BossDef("mossback", Tier.EARLY, "The Mossback Thunderer", 520, 7, 6, 0.26, 1.15, 2.6f, 2.4f, 40,
                null, "underdeep:thunderhide", "underdeep:mossback_horn", SoundEvents.ENTITY_RAVAGER_ROAR, false, false, Brains.Mossback::new));
        def(new BossDef("sporemother", Tier.EARLY, "The Spore Mother", 560, 6.5, 4, 0.18, 1.0, 3.4f, 4.6f, 45,
                null, "underdeep:mycelial_heart", "underdeep:sporemother_censer", SoundEvents.ENTITY_SNIFFER_SCENTING, false, false, Brains.SporeMother::new));
        def(new BossDef("choirwarden", Tier.EARLY, "The Drowned Choirwarden", 500, 7.5, 10, 0.27, 1.2, 1.2f, 3.0f, 36,
                null, "underdeep:choir_nacre", "underdeep:choirwarden_blade", SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, false, false, Brains.Choirwarden::new));
        def(new BossDef("rimefang", Tier.MID, "Rimefang, Lord of the Pack", 780, 9, 8, 0.33, 1.3, 1.9f, 1.9f, 55,
                "underdeep:sign_spiral", "underdeep:rimefang_pelt", "underdeep:rimefang_mantle", SoundEvents.ENTITY_WOLF_HOWL, false, false, Brains.Rimefang::new));
        def(new BossDef("galvanic", Tier.MID, "The Galvanic Colossus", 900, 10, 14, 0.22, 1.0, 3.0f, 5.4f, 70,
                "underdeep:sign_eye", "underdeep:galvanic_core", "underdeep:galvanic_gauntlet", SoundEvents.ENTITY_IRON_GOLEM_HURT, false, false, Brains.Galvanic::new));
        def(new BossDef("cinderwurm", Tier.MID, "The Cinder Wurm", 820, 10, 10, 0.3, 1.2, 2.4f, 2.4f, 60,
                "underdeep:sign_flame", "underdeep:cinder_scale", "underdeep:cinder_fang", SoundEvents.ENTITY_BLAZE_AMBIENT, false, true, Brains.CinderWurm::new));
        def(new BossDef("lamplighter", Tier.MID, "The Last Lamplighter", 720, 9, 6, 0.3, 1.2, 1.0f, 2.8f, 50,
                "underdeep:sign_wave", "underdeep:everlit_wick", "underdeep:lamplighter_lantern", SoundEvents.ENTITY_VEX_AMBIENT, true, true, Brains.Lamplighter::new));
        def(new BossDef("resonant", Tier.LATE, "The Resonant Colossus", 1250, 13, 16, 0.22, 1.0, 3.6f, 6.6f, 90,
                "underdeep:sign_star", "underdeep:resonant_crystal", "underdeep:resonant_hammer", SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, false, false, Brains.Resonant::new));
        def(new BossDef("tidemother", Tier.LATE, "The Tide-Mother", 1150, 12, 10, 0.3, 1.2, 3.2f, 2.2f, 80,
                "underdeep:sign_key", "underdeep:abyssal_pearl", "underdeep:tide_harpoon", SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, false, false, Brains.TideMother::new));
        def(new BossDef("silentwarden", Tier.LATE, "The Silent Warden", 1300, 14, 12, 0.3, 1.25, 1.6f, 4.2f, 85,
                "underdeep:keystone_silence", "underdeep:silent_bellmetal", "underdeep:silent_veil", SoundEvents.ENTITY_WARDEN_ROAR, false, true, Brains.SilentWarden::new));
        def(new BossDef("rootsovereign", Tier.SECRET, "The Root Sovereign", 1450, 13, 12, 0.2, 1.0, 3.4f, 6.2f, 95,
                null, "underdeep:heartwood_resin", "underdeep:sovereign_crown", SoundEvents.ENTITY_RAVAGER_ROAR, false, false, Brains.RootSovereign::new));
        def(new BossDef("seamwatcher", Tier.SECRET, "The Seam Watcher", 1350, 13, 8, 0.25, 1.0, 2.4f, 2.4f, 90,
                null, "underdeep:seamglass", "underdeep:watcher_lens", SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, true, true, Brains.SeamWatcher::new));
        def(new BossDef("somnarch", Tier.FINAL, "The Somnarch", 2100, 15, 14, 0.28, 1.2, 3.2f, 7.2f, 110,
                null, "underdeep:somnal_ember", "underdeep:somnarch_heart", SoundEvents.ENTITY_WARDEN_EMERGE, false, true, Brains.Somnarch::new));
    }

    public static void register() {
        for (BossDef d : DEFS.values()) {
            EntityType.Builder<BossEntity> b = EntityType.Builder.<BossEntity>create(BossEntity::new, SpawnGroup.MONSTER)
                    .dimensions(d.width(), d.height()).maxTrackingRange(16).trackingTickInterval(2);
            if (d.fireImmune()) {
                b = b.makeFireImmune();
            }
            EntityType<BossEntity> t = Registry.register(Registries.ENTITY_TYPE, Identifier.of(Underdeep.MOD_ID, "boss_" + d.id()), b.build());
            TYPES.put(d.id(), t);
            BY_TYPE.put(t, d);
            FabricDefaultAttributeRegistry.register(t, HostileEntity.createHostileAttributes()
                    .add(EntityAttributes.GENERIC_MAX_HEALTH, d.health())
                    .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, d.damage())
                    .add(EntityAttributes.GENERIC_ARMOR, d.armor())
                    .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, d.speed())
                    .add(EntityAttributes.GENERIC_FLYING_SPEED, d.speed())
                    .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                    .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48)
                    .add(EntityAttributes.GENERIC_STEP_HEIGHT, d.height() > 3 ? 1.6 : 1.1));
        }
    }

    public static BossDef def(EntityType<?> t) {
        return BY_TYPE.get(t);
    }

    public static BossDef byId(String id) {
        return DEFS.get(id);
    }

    public static EntityType<BossEntity> type(String id) {
        return TYPES.get(id);
    }

    public static java.util.Collection<EntityType<BossEntity>> types() {
        return TYPES.values();
    }
}
