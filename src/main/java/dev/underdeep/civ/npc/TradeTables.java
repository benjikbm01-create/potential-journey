package dev.underdeep.civ.npc;

import dev.underdeep.civ.Civilization;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Civilization- and profession-specific trade inventories (Stage 4 basic trading). Currency is the emerald;
 * each merchant sells goods of their trade and culture and buys raw materials their settlement uses.
 * Offers are deterministic per inhabitant (seeded) and restock daily (record-backed uses).
 */
public final class TradeTables {
    private TradeTables() {
    }

    /** One offer template: buy {@code cost} emeralds for {@code count} of item, or sell items for emeralds. */
    private record T(String item, int count, int emeralds, boolean sells, int maxUses) {
    }

    private static T sell(String item, int count, int emeralds) {
        return new T(item, count, emeralds, true, 12);
    }

    private static T buy(String item, int count, int emeralds) {
        return new T(item, count, emeralds, false, 16);
    }

    private static List<T> general(String prof) {
        return switch (prof) {
            case "farmer" -> List.of(buy("underdeep:duskroot", 18, 1), buy("underdeep:glowgrain", 16, 1), buy("underdeep:sunbean", 16, 1),
                    sell("underdeep:glowgrain_bread", 4, 1), sell("underdeep:glowgrain_seeds", 8, 1), sell("minecraft:bone_meal", 8, 1));
            case "cook", "innkeeper" -> List.of(sell("underdeep:cavern_stew", 1, 1), sell("underdeep:sunbean_stew", 1, 1),
                    sell("underdeep:cooked_game_meat", 4, 1), buy("underdeep:game_meat", 10, 1), buy("underdeep:cave_truffle", 6, 1));
            case "baker" -> List.of(sell("underdeep:glowgrain_bread", 5, 1), sell("minecraft:cake", 1, 2), sell("minecraft:cookie", 10, 1),
                    buy("underdeep:glowgrain", 16, 1), buy("underdeep:honeyfruit", 10, 1));
            case "guard" -> List.of(sell("minecraft:iron_sword", 1, 6), sell("minecraft:shield", 1, 5), sell("minecraft:arrow", 16, 1),
                    buy("minecraft:rotten_flesh", 24, 1), buy("minecraft:bone", 16, 1));
            case "rancher" -> List.of(sell("minecraft:lead", 2, 1), sell("underdeep:harness", 1, 6), sell("minecraft:leather", 4, 1),
                    buy("underdeep:hide", 6, 1), buy("underdeep:fur_tuft", 10, 1));
            case "hunter" -> List.of(sell("minecraft:bow", 1, 3), sell("minecraft:arrow", 16, 1), sell("underdeep:cooked_game_meat", 5, 1),
                    buy("underdeep:hide", 6, 1), buy("underdeep:horn", 4, 1), buy("underdeep:plume", 8, 1));
            case "fisher" -> List.of(sell("underdeep:cooked_fish_fillet", 6, 1), sell("minecraft:fishing_rod", 1, 2),
                    buy("underdeep:fish_fillet", 10, 1), buy("underdeep:shellmeat", 8, 1), buy("minecraft:string", 16, 1));
            default -> List.of(sell("minecraft:torch", 16, 1), sell("minecraft:map", 1, 2), buy("minecraft:stick", 32, 1));
        };
    }

    private static List<T> specialist(Civilization civ, String prof) {
        return switch (prof) {
            case "herbalist" -> List.of(sell("underdeep:mendleaf_poultice", 1, 2), sell("underdeep:mendleaf_sprig", 4, 1),
                    sell("underdeep:sunleaf_blossom", 3, 1), buy("underdeep:bogwort_leaf", 8, 1), buy("underdeep:duskberries", 16, 1));
            case "forester", "carpenter" -> List.of(sell(woodFor(civ) + "_planks", 24, 1), sell(woodFor(civ) + "_log", 12, 1),
                    sell(woodFor(civ) + "_door", 2, 1), sell("minecraft:chest", 2, 1), buy("minecraft:stick", 32, 1));
            case "brewer" -> List.of(sell("minecraft:glass_bottle", 6, 1), sell("underdeep:antidote_tincture", 1, 3),
                    buy("underdeep:honeyfruit", 12, 1), buy("underdeep:sea_grapes", 12, 1));
            case "tailor" -> List.of(sell(clothFor(civ) + "_wool", 6, 1), sell(clothFor(civ) + "_bed", 1, 3), sell(clothFor(civ) + "_banner", 1, 2),
                    sell("minecraft:leather_chestplate", 1, 3), buy("underdeep:fur_tuft", 12, 1), buy("underdeep:silk_strand", 10, 1));
            case "blacksmith" -> List.of(sell("minecraft:iron_pickaxe", 1, 5), sell("minecraft:iron_axe", 1, 4), sell("minecraft:iron_chestplate", 1, 9),
                    sell("minecraft:anvil", 1, 8), buy("minecraft:coal", 16, 1), buy("minecraft:raw_iron", 6, 1));
            case "smelter", "miner" -> List.of(sell("minecraft:iron_ingot", 4, 1), sell("minecraft:copper_ingot", 10, 1),
                    sell("underdeep:emberite", 2, 1), buy("underdeep:sulfur", 12, 1), buy("underdeep:niter", 12, 1), buy("minecraft:raw_copper", 12, 1));
            case "engineer" -> List.of(sell("minecraft:rail", 16, 2), sell("minecraft:minecart", 1, 3), sell("minecraft:piston", 2, 2),
                    sell("minecraft:redstone", 12, 1), buy("underdeep:galvanite", 4, 1));
            case "boatwright" -> List.of(sell("minecraft:oak_boat", 1, 2), sell("minecraft:oak_chest_boat", 1, 3), sell("minecraft:lead", 2, 1),
                    buy(woodFor(civ) + "_planks", 24, 1));
            case "navigator", "cartographer" -> List.of(sell("minecraft:map", 1, 2), sell("minecraft:compass", 1, 3), sell("minecraft:spyglass", 1, 4),
                    buy("minecraft:paper", 18, 1), buy("underdeep:pearl", 3, 1));
            case "alchemist", "healer" -> List.of(sell("underdeep:antidote_tincture", 1, 3), sell("underdeep:moonpetal_draught", 1, 3),
                    sell("underdeep:emberwort_tonic", 1, 3), sell("underdeep:mendleaf_poultice", 2, 2), buy("underdeep:moonpetal_petal", 6, 1),
                    buy("underdeep:emberwort_root", 6, 1));
            case "mycologist" -> List.of(sell("underdeep:puffcap", 16, 1), sell("underdeep:roasted_inkcap", 6, 1), sell("underdeep:cave_truffle", 4, 1),
                    buy("underdeep:inkcap", 12, 1), buy("underdeep:rimecap", 8, 1));
            case "gemcutter", "artisan" -> List.of(sell("underdeep:prismatic_opal", 1, 6), sell("underdeep:glimmer_shard", 4, 1),
                    sell("minecraft:amethyst_shard", 8, 1), sell("underdeep:jasper", 2, 1), buy("underdeep:malachite", 4, 1),
                    buy("underdeep:cryolite", 4, 1));
            case "lampwright" -> List.of(sell("minecraft:lantern", 4, 1), sell("minecraft:soul_lantern", 4, 1), sell("underdeep:luminite_block", 2, 2),
                    buy("underdeep:glow_gland", 4, 1), buy("underdeep:creature_oil", 6, 1));
            case "scholar" -> List.of(sell("minecraft:book", 3, 1), sell("minecraft:bookshelf", 1, 2), sell("minecraft:writable_book", 1, 1),
                    buy("minecraft:paper", 24, 1), buy("minecraft:feather", 12, 1));
            case "stonemason" -> List.of(sell("minecraft:stone_bricks", 16, 1), sell("minecraft:polished_andesite", 16, 1),
                    sell("minecraft:chiseled_stone_bricks", 8, 1), buy("minecraft:cobblestone", 32, 1));
            case "astronomer" -> List.of(sell("minecraft:spyglass", 1, 3), sell("underdeep:starsilver_ingot", 1, 5), sell("minecraft:clock", 1, 3),
                    buy("underdeep:raw_starsilver", 3, 1), buy("underdeep:glimmer_shard", 6, 1));
            default -> general(prof);
        };
    }

    private static String woodFor(Civilization c) {
        return switch (c) {
            case ROOTFOLK, SPOREFOLK -> "underdeep:duskoak";
            case FORGEKIN -> "underdeep:bloodthorn";
            case TIDE_DWELLERS -> "underdeep:mirewillow";
            case GLIMMERKIN -> "underdeep:spirebark";
            case STONEBORN -> "underdeep:frostfir";
            case AMBERFOLK -> "underdeep:goldwood";
            case ASTRALS -> "underdeep:lumenwood";
        };
    }

    private static String clothFor(Civilization c) {
        return "minecraft:" + switch (c) {
            case ROOTFOLK -> "green";
            case FORGEKIN -> "orange";
            case TIDE_DWELLERS -> "cyan";
            case SPOREFOLK -> "purple";
            case GLIMMERKIN -> "blue";
            case STONEBORN -> "red";
            case AMBERFOLK -> "yellow";
            case ASTRALS -> "light_blue";
        };
    }

    private static Item item(String id) {
        Item i = Registries.ITEM.get(Identifier.of(id));
        return i == Items.AIR ? null : i;
    }

    /** Builds the offers of one inhabitant (4–6 offers, deterministic for the seed). */
    public static TradeOfferList offers(Civilization civ, String profession, long seed) {
        Random r = new Random(seed);
        List<T> pool = new ArrayList<>(specialist(civ, profession));
        if (pool.size() < 5) {
            pool.addAll(general("merchant"));
        }
        if (profession.equals("merchant")) {
            // general merchants carry a sample of the culture's specialties
            pool = new ArrayList<>();
            for (String s : dev.underdeep.civ.settlement.Professions.specialties(civ)) {
                List<T> sp = specialist(civ, s);
                pool.add(sp.get(r.nextInt(sp.size())));
            }
            pool.addAll(general("merchant"));
        }
        TradeOfferList list = new TradeOfferList();
        int n = Math.min(pool.size(), 4 + r.nextInt(3));
        for (int i = 0; i < n; i++) {
            T t = pool.get(i);
            Item it = item(t.item);
            if (it == null) {
                continue;
            }
            TradeOffer o = t.sells
                    ? new TradeOffer(new TradedItem(Items.EMERALD, t.emeralds), Optional.empty(), new ItemStack(it, t.count), t.maxUses, 2, 0.05f)
                    : new TradeOffer(new TradedItem(it, t.count), Optional.empty(), new ItemStack(Items.EMERALD, t.emeralds), t.maxUses, 2, 0.05f);
            list.add(o);
        }
        return list;
    }

    /** Whether a profession trades with players (merchant interface on interaction). */
    public static boolean trades(String profession) {
        return true;
    }
}
