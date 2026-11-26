package dev.underdeep.civ.mind;

import dev.underdeep.civ.Civilization;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Personal preferences: three liked items (one cultural, one professional, one personal), one disliked item,
 * a favourite kind of place and a favourite conversation topic. Only real registered items are used.
 */
public final class Preferences {
    private Preferences() {
    }

    private static final Map<Civilization, List<String>> CULTURE = Map.of(
            Civilization.ROOTFOLK, List.of("underdeep:duskberries", "underdeep:baked_duskroot", "underdeep:mendleaf_sprig", "minecraft:honey_bottle"),
            Civilization.FORGEKIN, List.of("underdeep:emberpepper", "underdeep:emberite", "minecraft:iron_ingot", "underdeep:cooked_game_meat"),
            Civilization.TIDE_DWELLERS, List.of("underdeep:pearl", "underdeep:cooked_fish_fillet", "underdeep:sea_grapes", "underdeep:rice_bowl"),
            Civilization.SPOREFOLK, List.of("underdeep:roasted_truffle", "underdeep:puffcap", "underdeep:moonpetal_petal", "underdeep:roasted_inkcap"),
            Civilization.GLIMMERKIN, List.of("underdeep:prismatic_opal", "underdeep:glimmer_shard", "minecraft:amethyst_shard", "underdeep:jasper"),
            Civilization.STONEBORN, List.of("underdeep:baked_turnip", "underdeep:cliffberries", "underdeep:hide", "underdeep:malachite"),
            Civilization.AMBERFOLK, List.of("underdeep:honeyfruit", "underdeep:sunbean_stew", "underdeep:amber_glob", "minecraft:cake"),
            Civilization.ASTRALS, List.of("underdeep:starsilver_ingot", "underdeep:moonpetal_draught", "minecraft:book", "underdeep:glow_gland"));

    private static List<String> professional(String p) {
        return switch (p) {
            case "blacksmith", "smelter", "engineer", "miner" -> List.of("minecraft:iron_ingot", "minecraft:raw_iron", "underdeep:umbralite_ingot", "minecraft:coal");
            case "farmer", "rancher", "herbalist" -> List.of("minecraft:bone_meal", "underdeep:glowgrain_seeds", "underdeep:sunleaf_blossom", "underdeep:mendleaf_sprig");
            case "fisher", "boatwright", "navigator" -> List.of("underdeep:pearl", "underdeep:fish_fillet", "minecraft:string", "minecraft:compass");
            case "scholar", "cartographer", "astronomer" -> List.of("minecraft:book", "minecraft:paper", "minecraft:spyglass", "minecraft:map");
            case "healer", "alchemist", "mycologist" -> List.of("underdeep:mendleaf_sprig", "underdeep:moonpetal_petal", "minecraft:glass_bottle", "underdeep:inkcap");
            case "gemcutter", "artisan", "lampwright" -> List.of("underdeep:prismatic_opal", "minecraft:amethyst_shard", "underdeep:glow_gland", "minecraft:gold_ingot");
            case "guard", "hunter" -> List.of("minecraft:arrow", "underdeep:cooked_game_meat", "minecraft:shield", "underdeep:horn");
            case "cook", "baker", "innkeeper", "brewer" -> List.of("underdeep:honeyfruit", "underdeep:glowgrain", "minecraft:sugar", "underdeep:sunbean");
            case "tailor" -> List.of("underdeep:fur_tuft", "underdeep:silk_strand", "minecraft:white_wool", "minecraft:string");
            case "carpenter", "forester" -> List.of("minecraft:oak_log", "minecraft:iron_axe", "underdeep:amber_glob", "minecraft:stick");
            case "stonemason" -> List.of("minecraft:chiseled_stone_bricks", "underdeep:jasper", "minecraft:iron_pickaxe", "underdeep:fossil_fragment");
            default -> List.of("minecraft:emerald", "minecraft:gold_ingot", "underdeep:pearl", "minecraft:cake");
        };
    }

    private static final List<String> PERSONAL = List.of("minecraft:cake", "minecraft:cookie", "minecraft:pumpkin_pie", "minecraft:glow_berries",
            "underdeep:duskberries", "underdeep:honeyfruit", "underdeep:snowberries", "underdeep:cave_truffle", "minecraft:emerald",
            "underdeep:pearl", "minecraft:amethyst_shard", "minecraft:lantern", "minecraft:book", "underdeep:glowpad", "minecraft:flower_pot",
            "underdeep:relic_fruit");

    private static final List<String> DISLIKES = List.of("minecraft:rotten_flesh", "minecraft:spider_eye", "minecraft:poisonous_potato",
            "underdeep:emberpepper", "underdeep:cave_truffle", "minecraft:raw_cod", "minecraft:bone", "underdeep:venom_gland");

    public static final List<String> PLACES = List.of("garden", "plaza", "tavern", "workshop", "home", "market", "landmark", "water");
    public static final List<String> TOPICS = List.of("work", "food", "wildlife", "places", "news", "family", "crafts", "history");

    static boolean exists(String id) {
        if (id.equals("underdeep:relic_fruit")) {
            return false; // exceptional content is never a preference
        }
        Item i = Registries.ITEM.get(Identifier.of(id));
        return i != Items.AIR;
    }

    private static String pick(List<String> pool, Random r, List<String> avoid) {
        List<String> ok = new ArrayList<>();
        for (String s : pool) {
            if (!avoid.contains(s) && exists(s)) {
                ok.add(s);
            }
        }
        return ok.isEmpty() ? "" : ok.get(r.nextInt(ok.size()));
    }

    static void assign(Mind m, Civilization civ, String profession, Random r) {
        String a = pick(CULTURE.getOrDefault(civ, PERSONAL), r, m.likes);
        if (!a.isEmpty()) {
            m.likes.add(a);
        }
        String b = pick(professional(profession), r, m.likes);
        if (!b.isEmpty()) {
            m.likes.add(b);
        }
        String c = pick(PERSONAL, r, m.likes);
        if (!c.isEmpty()) {
            m.likes.add(c);
        }
        m.dislike = pick(DISLIKES, r, m.likes);
        m.favoritePlace = PLACES.get(r.nextInt(PLACES.size()));
        m.favoriteTopic = TOPICS.get(r.nextInt(TOPICS.size()));
    }

    /** How much the inhabitant values a gift: 2 loved, 1 liked (food/valuable), 0 neutral, −1 disliked. */
    public static int value(Mind m, ItemStack stack) {
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        if (m.likes.contains(id)) {
            return 2;
        }
        if (id.equals(m.dislike)) {
            return -1;
        }
        if (stack.getComponents().contains(net.minecraft.component.DataComponentTypes.FOOD)) {
            return 1;
        }
        if (stack.isOf(Items.EMERALD) || stack.isOf(Items.GOLD_INGOT) || stack.isOf(Items.DIAMOND)) {
            return 1 + (m.trait(Trait.FRUGALITY) > 60 ? 1 : 0);
        }
        return 0;
    }

    /** Readable item name for dialogue. */
    public static String name(String id) {
        Item i = Registries.ITEM.get(Identifier.of(id));
        String n = i == Items.AIR ? "" : i.getName().getString();
        if (n.isEmpty() || n.startsWith("item.") || n.startsWith("block.")) {
            // a dedicated server has no mod translations: derive a readable name from the id
            n = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        }
        return n.toLowerCase(java.util.Locale.ROOT);
    }
}
