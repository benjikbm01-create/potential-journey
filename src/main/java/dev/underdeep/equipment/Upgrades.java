package dev.underdeep.equipment;

import dev.underdeep.guide.Uses;
import dev.underdeep.registry.Reg;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Equipment upgrades (Stage 8). Applied upgrades are stored as a list of ids in the {@code underdeep:upgrades} item
 * component; everything they change is <i>derived</i> from that list: attribute modifiers are rebuilt from the item's
 * defaults plus each upgrade (stable ids, so nothing accumulates), maximum durability from the item's base, and
 * protection/flags are read at use. Rules: two upgrades per item, one per group (edge, grip, coating, gem, lining,
 * plating, head, brace, string, limbs), only on matching equipment categories.
 */
public final class Upgrades {
    private Upgrades() {
    }

    public static final int SLOTS = 2;

    /** A kit: consumed at the artisan's bench to apply its upgrade. */
    public static class KitItem extends Item {
        public final EquipmentContent.Upgrade def;

        public KitItem(EquipmentContent.Upgrade def, Settings settings) {
            super(settings);
            this.def = def;
        }
    }

    static void init() {
        for (EquipmentContent.Upgrade u : EquipmentContent.UPGRADES.values()) {
            Uses.add(EquipmentItems.get(u.kit()), "equipment upgrade (artisan's bench): " + u.desc());
        }
    }

    public static List<String> of(ItemStack s) {
        List<String> l = s.get(EquipmentItems.UPGRADES);
        return l == null ? List.of() : l;
    }

    public static List<EquipmentContent.Upgrade> defs(ItemStack s) {
        List<EquipmentContent.Upgrade> out = new ArrayList<>();
        for (String id : of(s)) {
            EquipmentContent.Upgrade u = EquipmentContent.UPGRADES.get(id);
            if (u != null) {
                out.add(u);
            }
        }
        return out;
    }

    public static Set<String> flags(ItemStack s) {
        Set<String> f = new LinkedHashSet<>();
        for (EquipmentContent.Upgrade u : defs(s)) {
            f.addAll(u.flags());
        }
        return f;
    }

    public static double protection(ItemStack s, String hazard) {
        double p = 0;
        for (EquipmentContent.Upgrade u : defs(s)) {
            p += u.protect().getOrDefault(hazard, 0.0);
        }
        return p;
    }

    /** Why an upgrade cannot be applied, or null if it can. */
    @Nullable
    public static String reject(ItemStack target, EquipmentContent.Upgrade u) {
        if (target.isEmpty()) {
            return "Put equipment in the first slot.";
        }
        String cat = EquipmentItems.category(target.getItem());
        if (cat == null || !u.applies().contains(cat)) {
            return "This upgrade does not fit " + (cat == null ? "this item" : "a " + cat) + ".";
        }
        List<String> cur = of(target);
        if (cur.contains(u.id())) {
            return "Already applied.";
        }
        for (EquipmentContent.Upgrade o : defs(target)) {
            if (o.group().equals(u.group())) {
                return "Only one " + u.group() + " upgrade per item (has " + o.id().replace('_', ' ') + ").";
            }
        }
        if (cur.size() >= SLOTS) {
            return "No free upgrade slot (" + SLOTS + " per item).";
        }
        return null;
    }

    /** A copy of {@code target} with the upgrade applied (the caller has validated it with {@link #reject}). */
    public static ItemStack applied(ItemStack target, EquipmentContent.Upgrade u) {
        ItemStack out = target.copy();
        List<String> l = new ArrayList<>(of(target));
        l.add(u.id());
        out.set(EquipmentItems.UPGRADES, List.copyOf(l));
        recompute(out);
        return out;
    }

    /** Rebuilds the derived data (attributes, max durability) from the upgrade list; idempotent. */
    public static void recompute(ItemStack s) {
        List<EquipmentContent.Upgrade> ups = defs(s);
        Item item = s.getItem();
        String cat = EquipmentItems.category(item);
        // durability
        Integer base = item.getComponents().get(DataComponentTypes.MAX_DAMAGE);
        if (base != null) {
            double mult = 1;
            for (EquipmentContent.Upgrade u : ups) {
                mult += u.durability();
            }
            int max = (int) Math.round(base * mult);
            s.set(DataComponentTypes.MAX_DAMAGE, max);
            if (s.getDamage() >= max) {
                s.setDamage(max - 1);
            }
        }
        // attributes: item defaults + upgrades
        AttributeModifiersComponent defaults = item.getComponents().getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        if (defaults.modifiers().isEmpty()) {
            defaults = item.getAttributeModifiers();
        }
        if (ups.stream().allMatch(u -> u.attrs().isEmpty())) {
            s.remove(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            return;
        }
        AttributeModifiersComponent.Builder b = AttributeModifiersComponent.builder();
        defaults.modifiers().forEach(e -> b.add(e.attribute(), e.modifier(), e.slot()));
        AttributeModifierSlot slot = cat == null ? AttributeModifierSlot.MAINHAND : AttributeModifierSlot.forEquipmentSlot(EquipmentItems.slotOf(cat));
        boolean armor = cat != null && List.of("helmet", "chestplate", "leggings", "boots").contains(cat);
        for (EquipmentContent.Upgrade u : ups) {
            int n = 0;
            for (EquipmentContent.Attr a : u.attrs()) {
                n++;
                if (!a.scope().isEmpty() && !(a.scope().equals("armor") == armor)) {
                    continue;
                }
                var entry = a.entry();
                if (entry != null) {
                    b.add(entry, new EntityAttributeModifier(Reg.id("upgrade." + u.id() + "." + n), a.amount(), a.op()), slot);
                }
            }
        }
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, b.build());
    }

    /** Tooltip lines for applied upgrades (client tooltip callback). */
    public static void tooltip(ItemStack s, List<Text> lines) {
        List<EquipmentContent.Upgrade> ups = defs(s);
        String cat = EquipmentItems.category(s.getItem());
        if (cat == null) {
            return;
        }
        lines.add(Text.literal("Upgrades " + ups.size() + "/" + SLOTS).formatted(Formatting.GOLD));
        for (EquipmentContent.Upgrade u : ups) {
            lines.add(Text.literal(" + " + u.id().replace('_', ' ') + ": " + u.desc()).formatted(Formatting.YELLOW));
        }
    }

    /** All upgrade groups with their kit counts (handbook). */
    public static Map<String, EquipmentContent.Upgrade> all() {
        return EquipmentContent.UPGRADES;
    }
}
