package dev.underdeep.combat;

import dev.underdeep.equipment.EquipmentContent;
import dev.underdeep.equipment.Weapons;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;

import java.util.HashMap;
import java.util.Map;

/**
 * Stage 10 fighting styles. Every melee family keeps vanilla light attacks (with its Stage 8 attributes) and gains a
 * distinct heavy attack (sneak + attack): different wind-up, reach, arc, number of targets, lunge, stagger power and
 * recovery, plus one special property of its light attacks. Bows and crossbows are ranged styles (their charged shots
 * stagger); the shield is the defensive style.
 *
 * @param windup      ticks between committing and the hit (server-timed)
 * @param recovery    ticks after the hit before the next heavy attack, dodge or parry
 * @param mult        damage multiplier of the heavy hit (applied to the normal attack)
 * @param reach       reach of the heavy hit in blocks
 * @param arc         half-angle of the heavy hit in degrees (cone in front)
 * @param targets     maximum targets hit by the heavy attack
 * @param lunge       forward impulse at the moment of the hit
 * @param poise       stagger power of light hits (heavy hits ×2.5)
 * @param stamina     stamina cost of the heavy attack
 */
public enum WeaponStyle {
    SWORD("Sword", 9, 8, 1.7, 3.6, 50, 2, 0.25, 1.0, 22, "longest parry window and the strongest riposte"),
    DAGGER("Dagger", 5, 5, 1.45, 3.0, 35, 1, 0.55, 0.6, 16, "hits from behind or against a staggered foe deal +50%"),
    GREATSWORD("Greatsword", 15, 14, 2.1, 4.4, 95, 4, 0.15, 1.6, 30, "the heavy cleave sweeps everything in a wide arc"),
    SPEAR("Spear", 11, 10, 1.8, 5.4, 18, 2, 0.9, 1.1, 24, "the heavy thrust lunges forward and pierces two foes in a line"),
    WARHAMMER("Warhammer", 17, 16, 2.2, 3.8, 360, 5, 0.0, 2.2, 32, "the heavy blow is a ground slam around you; best at breaking guard"),
    BATTLEAXE("Battleaxe", 13, 12, 2.0, 3.8, 70, 3, 0.3, 1.4, 28, "disables shields and hits armour harder"),
    MACE("Mace", 12, 11, 1.9, 3.4, 45, 2, 0.35, 1.9, 26, "crushing blows: bonus stagger, and more damage the more armour the foe has"),
    BOW("Bow", 0, 0, 1, 0, 0, 0, 0, 1.2, 0, "fully drawn shots stagger"),
    CROSSBOW("Crossbow", 0, 0, 1, 0, 0, 0, 0, 1.8, 0, "bolts stagger hard"),
    SHIELD("Shield", 0, 0, 1, 0, 0, 0, 0, 0.5, 0, "raise it just as a blow lands to parry; holding it blocks at a stamina cost"),
    FIST("Unarmed", 0, 0, 1, 0, 0, 0, 0, 0.3, 0, "");

    public final String title;
    public final int windup, recovery;
    public final double mult, reach, arc, lunge, poise;
    public final int targets, stamina;
    public final String special;

    WeaponStyle(String title, int windup, int recovery, double mult, double reach, double arc, int targets, double lunge, double poise,
                int stamina, String special) {
        this.title = title;
        this.windup = windup;
        this.recovery = recovery;
        this.mult = mult;
        this.reach = reach;
        this.arc = arc;
        this.targets = targets;
        this.lunge = lunge;
        this.poise = poise;
        this.stamina = stamina;
        this.special = special;
    }

    public boolean melee() {
        return windup > 0;
    }

    private static final Map<String, WeaponStyle> BY_ID = new HashMap<>();

    /** The style of the item in hand. */
    public static WeaponStyle of(ItemStack s) {
        if (s.isEmpty()) {
            return FIST;
        }
        String id = Registries.ITEM.getId(s.getItem()).toString();
        if (BY_ID.isEmpty()) {
            for (EquipmentContent.Weapon w : EquipmentContent.WEAPONS) {
                BY_ID.put("underdeep:" + w.id(), switch (w.family()) {
                    case "dagger" -> DAGGER;
                    case "greatsword" -> GREATSWORD;
                    case "spear" -> SPEAR;
                    case "warhammer" -> WARHAMMER;
                    case "battleaxe" -> BATTLEAXE;
                    case "mace" -> MACE;
                    default -> SWORD;
                });
            }
        }
        WeaponStyle st = BY_ID.get(id);
        if (st != null) {
            return st;
        }
        var it = s.getItem();
        if (it instanceof MaceItem) {
            return MACE;
        }
        if (it instanceof TridentItem) {
            return SPEAR;
        }
        if (it instanceof Weapons.BattleAxeItem || it instanceof AxeItem) {
            return BATTLEAXE;
        }
        if (it instanceof SwordItem) {
            return SWORD;
        }
        if (it instanceof CrossbowItem) {
            return CROSSBOW;
        }
        if (it instanceof BowItem) {
            return BOW;
        }
        if (it instanceof ShieldItem) {
            return SHIELD;
        }
        return FIST;
    }
}
