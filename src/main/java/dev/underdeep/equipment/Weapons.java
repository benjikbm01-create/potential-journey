package dev.underdeep.equipment;

import dev.underdeep.registry.Reg;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Stage 8 weapon, bow, arrow and armour item classes. Behaviour beyond attributes lives in {@link EquipmentEffects}. */
public final class Weapons {
    private Weapons() {
    }

    /** Swords, daggers, greatswords, spears, warhammers and maces: sword mechanics with family attributes. */
    public static class WeaponItem extends SwordItem {
        public WeaponItem(ToolMaterial material, Settings settings) {
            super(material, settings);
        }
    }

    /** Battleaxes: heavy weapons that are also axes (disable shields, chop wood). */
    public static class BattleAxeItem extends AxeItem {
        public BattleAxeItem(ToolMaterial material, Settings settings) {
            super(material, settings);
        }
    }

    /** Bows with their own draw time and power; silk strings draw faster, horn limbs shoot harder (upgrades). */
    public static class EquipBowItem extends BowItem {
        public final EquipmentContent.Bow def;

        public EquipBowItem(EquipmentContent.Bow def, Settings settings) {
            super(settings);
            this.def = def;
        }

        public int drawTicks(ItemStack stack) {
            return Upgrades.flags(stack).contains("draw_fast") ? Math.max(6, (int) (def.draw() * 0.85f)) : def.draw();
        }

        public float power(ItemStack stack) {
            return def.power() * (Upgrades.flags(stack).contains("power") ? 1.1f : 1f);
        }

        /** Pull progress with this bow's draw time (same curve as the vanilla bow). */
        public float pull(ItemStack stack, int useTicks) {
            float f = useTicks / (float) drawTicks(stack);
            f = (f * f + f * 2.0f) / 3.0f;
            return Math.min(1f, f);
        }

        @Override
        public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
            if (!(user instanceof PlayerEntity p)) {
                return;
            }
            ItemStack ammo = p.getProjectileType(stack);
            if (ammo.isEmpty()) {
                return;
            }
            float f = pull(stack, getMaxUseTime(stack, user) - remainingUseTicks);
            if (f < 0.1f) {
                return;
            }
            List<ItemStack> list = load(stack, ammo, p);
            if (world instanceof ServerWorld sw && !list.isEmpty()) {
                shootAll(sw, p, p.getActiveHand(), stack, list, f * 3.0f * power(stack), 1.0f, f == 1.0f, null);
            }
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0f,
                    1.0f / (world.getRandom().nextFloat() * 0.4f + 1.2f) + f * 0.5f);
            p.incrementStat(Stats.USED.getOrCreateStat(this));
        }
    }

    /** Arrows with extra damage, a status effect or fire. */
    public static class EquipArrowItem extends ArrowItem {
        public final EquipmentContent.Arrow def;

        public EquipArrowItem(EquipmentContent.Arrow def, net.minecraft.item.Item.Settings settings) {
            super(settings);
            this.def = def;
        }

        @Override
        public PersistentProjectileEntity createArrow(World world, ItemStack stack, LivingEntity shooter, @Nullable ItemStack shotFrom) {
            ItemStack s = stack.copyWithCount(1);
            if (def.effect() != null) {
                var eff = EquipmentEffects.effect(Identifier.of(def.effect()));
                if (eff != null) {
                    s.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.empty(), Optional.of(0xA0D0FF),
                            List.of(new StatusEffectInstance(eff, def.effectSeconds() * 20, def.effectAmp()))));
                }
            }
            ArrowEntity a = new ArrowEntity(world, shooter, s, shotFrom);
            a.setDamage(def.damage());
            if (def.fire() > 0) {
                a.setOnFireFor(def.fire() * 5);
            }
            return a;
        }
    }

    /** Armour with its set's extra attributes (speed, breath, swimming, falls) and per-piece hazard protection. */
    public static class EquipArmorItem extends ArmorItem {
        public final String set;
        private AttributeModifiersComponent cached;

        public EquipArmorItem(RegistryEntry<ArmorMaterial> material, Type type, Settings settings, String set) {
            super(material, type, settings);
            this.set = set;
        }

        @Override
        public AttributeModifiersComponent getAttributeModifiers() {
            if (cached == null) {
                AttributeModifiersComponent base = super.getAttributeModifiers();
                AttributeModifiersComponent.Builder b = AttributeModifiersComponent.builder();
                base.modifiers().forEach(e -> b.add(e.attribute(), e.modifier(), e.slot()));
                EquipmentContent.ArmorStats a = EquipmentContent.MATERIALS.get(set).armor();
                AttributeModifierSlot slot = AttributeModifierSlot.forEquipmentSlot(getSlotType());
                int n = 0;
                for (EquipmentContent.Attr at : a.attrs()) {
                    add(b, at, slot, "armor_set." + getType().getName() + "." + n++);
                }
                for (EquipmentContent.Attr at : a.pieceAttrs().getOrDefault(getType().getName(), List.of())) {
                    add(b, at, slot, "armor_piece." + getType().getName() + "." + n++);
                }
                cached = b.build();
            }
            return cached;
        }

        static void add(AttributeModifiersComponent.Builder b, EquipmentContent.Attr at, AttributeModifierSlot slot, String key) {
            var entry = at.entry();
            if (entry != null) {
                b.add(entry, new EntityAttributeModifier(Reg.id(key), at.amount(), at.op()), slot);
            }
        }
    }

    /** Used by the projectile hook to know a projectile came from an Underdeep arrow (none needed now). */
    static boolean isOurs(ProjectileEntity p) {
        return p instanceof ArrowEntity;
    }
}
