package dev.underdeep.equipment;

import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.npc.InhabitantEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SwordItem;

/**
 * Companion equipment (Stage 8): a companion wears the best armour and wields the best weapon found in the pack you
 * share with them. The pack stays the only real copy (the worn items are display copies that never drop, and
 * inhabitants are never saved as entities), so dismissal, injury, unloading or reactivation cannot duplicate gear.
 * Companions fight only as Stage 5 allows (defending you); better gear means more damage and protection there.
 */
public final class CompanionGear {
    private CompanionGear() {
    }

    public static void apply(InhabitantEntity e, Mind m) {
        ItemStack bestW = ItemStack.EMPTY;
        double bestDmg = 0;
        ItemStack[] armor = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        int[] prot = new int[4];
        for (ItemStack s : m.companionPack) {
            if (s.isEmpty()) {
                continue;
            }
            if (s.getItem() instanceof SwordItem || s.getItem() instanceof MiningToolItem) {
                double d = damage(s);
                if (d > bestDmg) {
                    bestDmg = d;
                    bestW = s;
                }
            } else if (s.getItem() instanceof ArmorItem a) {
                int i = switch (a.getSlotType()) {
                    case HEAD -> 0;
                    case CHEST -> 1;
                    case LEGS -> 2;
                    case FEET -> 3;
                    default -> -1;
                };
                if (i >= 0 && a.getProtection() > prot[i]) {
                    prot[i] = a.getProtection();
                    armor[i] = s;
                }
            }
        }
        if (!e.isGuard()) {
            set(e, EquipmentSlot.MAINHAND, bestW);
        }
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            set(e, slots[i], armor[i]);
        }
    }

    static void set(InhabitantEntity e, EquipmentSlot slot, ItemStack s) {
        ItemStack cur = e.getEquippedStack(slot);
        if (!ItemStack.areItemsAndComponentsEqual(cur, s)) {
            e.equipStack(slot, s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
            e.setEquipmentDropChance(slot, 0f);
        }
    }

    static double damage(ItemStack s) {
        AttributeModifiersComponent c = s.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        if (c.modifiers().isEmpty()) {
            c = s.getItem().getAttributeModifiers();
        }
        double d = 0;
        for (var en : c.modifiers()) {
            if (en.attribute().equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
                d += en.modifier().value();
            }
        }
        return d;
    }

    /** When a companion leaves, they take off what they were wearing from the shared pack. */
    public static void clear(InhabitantEntity e) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            e.equipStack(s, ItemStack.EMPTY);
        }
        if (!e.isGuard()) {
            e.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }
}
