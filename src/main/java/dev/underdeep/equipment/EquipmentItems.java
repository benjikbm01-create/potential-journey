package dev.underdeep.equipment;

import com.mojang.serialization.Codec;
import dev.underdeep.registry.Reg;
import dev.underdeep.survival.SurvivalContent.Station;
import dev.underdeep.survival.station.StationBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ToolMaterial;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Registers the Stage 8 equipment, components, utility items and blocks from {@link EquipmentContent}. */
public final class EquipmentItems {
    private EquipmentItems() {
    }

    public static final Map<String, Item> ITEMS = new HashMap<>();
    public static final Map<Item, EquipmentContent.Weapon> WEAPON_DEF = new HashMap<>();
    public static final Map<Item, EquipmentContent.Tool> TOOL_DEF = new HashMap<>();
    public static final Map<Item, EquipmentContent.Bow> BOW_DEF = new HashMap<>();
    public static final Map<Item, String> ARMOR_SET = new HashMap<>();
    public static final Map<String, RegistryEntry<ArmorMaterial>> ARMOR_MATERIALS = new HashMap<>();
    public static ComponentType<List<String>> UPGRADES;
    public static Block ORE_CRUSHER, ALLOY_FURNACE, FORGE, GEM_CUTTER, LEATHERWORKER_BENCH, ARTISAN_BENCH, WAYMARKER, GLIMMER_LANTERN, ROPE_LADDER;
    public static final List<Block> STATION_BLOCKS = new ArrayList<>();

    /** Tool material from a Stage 8 material definition. */
    public record Mat(EquipmentContent.ToolStats s, float speedScale) implements ToolMaterial {
        @Override
        public int getDurability() {
            return s.durability();
        }

        @Override
        public float getMiningSpeedMultiplier() {
            return s.speed() * speedScale;
        }

        @Override
        public float getAttackDamage() {
            return s.damage();
        }

        @Override
        public TagKey<net.minecraft.block.Block> getInverseTag() {
            return switch (s.level()) {
                case "stone" -> BlockTags.INCORRECT_FOR_STONE_TOOL;
                case "iron" -> BlockTags.INCORRECT_FOR_IRON_TOOL;
                case "diamond" -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
                case "netherite" -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
                default -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
            };
        }

        @Override
        public int getEnchantability() {
            return s.enchant();
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.ofItems(Registries.ITEM.get(Identifier.of(s.repair())));
        }
    }

    static Mat mat(String material) {
        return new Mat(EquipmentContent.MATERIALS.get(material).tool(), 1f);
    }

    static Item reg(String id, Item item) {
        ITEMS.put(id, Reg.item(id, item));
        return item;
    }

    /** Weapon attributes: damage, speed, and the family's reach and knockback (stable modifier ids). */
    static AttributeModifiersComponent weaponAttrs(EquipmentContent.Weapon w) {
        AttributeModifiersComponent.Builder b = AttributeModifiersComponent.builder()
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(Identifier.ofVanilla("base_attack_damage"), w.damage(),
                        EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(Identifier.ofVanilla("base_attack_speed"), w.speed() - 4.0,
                        EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        if (w.reach() != 0) {
            b.add(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, new EntityAttributeModifier(Reg.id("weapon_reach"), w.reach(),
                    EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        }
        if (w.knockback() != 0) {
            b.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, new EntityAttributeModifier(Reg.id("weapon_knockback"), w.knockback(),
                    EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        }
        return b.build();
    }

    public static void init() {
        EquipmentContent.load();
        UPGRADES = Registry.register(Registries.DATA_COMPONENT_TYPE, Reg.id("upgrades"), ComponentType.<List<String>>builder()
                .codec(Codec.STRING.listOf()).packetCodec(PacketCodecs.STRING.collect(PacketCodecs.toList())).build());
        for (String c : EquipmentContent.COMPONENTS) {
            reg(c, c.equals("rope_coil") ? new EquipmentTools.RopeCoilItem(new Item.Settings()) : new Item(new Item.Settings()));
        }
        // weapons
        for (EquipmentContent.Weapon w : EquipmentContent.WEAPONS) {
            Mat m = mat(w.material());
            Item.Settings s = new Item.Settings().maxDamage(w.durability()).attributeModifiers(weaponAttrs(w));
            if (EquipmentContent.ARTIFACTS.contains(w.id())) {
                s.rarity(net.minecraft.util.Rarity.EPIC).fireproof();
            }
            if (EquipmentContent.MATERIALS.get(w.material()).id().equals("umbralite")) {
                s.fireproof();
            }
            Item it = w.family().equals("battleaxe") ? new Weapons.BattleAxeItem(m, s) : new Weapons.WeaponItem(m, s);
            reg(w.id(), it);
            WEAPON_DEF.put(it, w);
        }
        // tools
        for (EquipmentContent.Tool t : EquipmentContent.TOOLS) {
            EquipmentContent.ToolStats st = EquipmentContent.MATERIALS.get(t.material()).tool();
            float scale = switch (t.kind()) {
                case "hammer", "excavator" -> 0.45f;
                case "lumber_axe" -> 0.8f;
                default -> 1f;
            };
            Mat m = new Mat(st, scale);
            Item it = EquipmentTools.create(t, m);
            reg(t.id(), it);
            TOOL_DEF.put(it, t);
        }
        // bows and arrows
        for (EquipmentContent.Bow b : EquipmentContent.BOWS) {
            Item it = new Weapons.EquipBowItem(b, new Item.Settings().maxDamage(b.durability()));
            reg(b.id(), it);
            BOW_DEF.put(it, b);
        }
        for (EquipmentContent.Arrow a : EquipmentContent.ARROWS) {
            reg(a.id(), new Weapons.EquipArrowItem(a, new Item.Settings()));
        }
        // armour
        for (String set : EquipmentContent.ARMOR_SETS) {
            EquipmentContent.ArmorStats a = EquipmentContent.MATERIALS.get(set).armor();
            Map<ArmorItem.Type, Integer> def = new EnumMap<>(ArmorItem.Type.class);
            def.put(ArmorItem.Type.HELMET, a.protection()[0]);
            def.put(ArmorItem.Type.CHESTPLATE, a.protection()[1]);
            def.put(ArmorItem.Type.LEGGINGS, a.protection()[2]);
            def.put(ArmorItem.Type.BOOTS, a.protection()[3]);
            def.put(ArmorItem.Type.BODY, a.protection()[1]);
            var sound = set.equals("fur") || set.equals("silk") ? SoundEvents.ITEM_ARMOR_EQUIP_LEATHER : set.equals("scale") || set.equals("shell")
                    ? SoundEvents.ITEM_ARMOR_EQUIP_TURTLE : set.equals("copper") || set.equals("galvanic") ? SoundEvents.ITEM_ARMOR_EQUIP_CHAIN
                    : SoundEvents.ITEM_ARMOR_EQUIP_IRON;
            String repair = a.repair();
            RegistryEntry<ArmorMaterial> am = Registry.registerReference(Registries.ARMOR_MATERIAL, Reg.id(set), new ArmorMaterial(def, a.enchant(), sound,
                    () -> Ingredient.ofItems(Registries.ITEM.get(Identifier.of(repair))), List.of(new ArmorMaterial.Layer(Reg.id(set))), a.toughness(),
                    a.knockback()));
            ARMOR_MATERIALS.put(set, am);
            for (ArmorItem.Type type : new ArmorItem.Type[]{ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS}) {
                String id = set + "_" + type.getName();
                Item.Settings s = new Item.Settings().maxDamage(type.getMaxDamage(a.durability()));
                if (set.equals("umbralite")) {
                    s.fireproof();
                }
                Item it = new Weapons.EquipArmorItem(am, type, s, set);
                reg(id, it);
                ARMOR_SET.put(it, set);
            }
        }
        // accessories and upgrade kits
        for (EquipmentContent.Accessory a : EquipmentContent.ACCESSORIES.values()) {
            Item.Settings as = new Item.Settings().maxCount(1);
            if (EquipmentContent.ARTIFACTS.contains(a.id())) {
                as.rarity(net.minecraft.util.Rarity.EPIC).fireproof();
            }
            reg(a.id(), new Accessories.AccessoryItem(a, as));
        }
        for (EquipmentContent.Upgrade u : EquipmentContent.UPGRADES.values()) {
            reg(u.kit(), new Upgrades.KitItem(u, new Item.Settings().maxCount(16)));
        }
        // utility
        for (Map.Entry<String, String> e : EquipmentContent.UTILITY.entrySet()) {
            reg(e.getKey(), EquipmentTools.utility(e.getKey(), e.getValue()));
            dev.underdeep.guide.Uses.add(ITEMS.get(e.getKey()), "exploration gear (" + e.getValue() + ")");
        }
        for (EquipmentContent.Arrow a : EquipmentContent.ARROWS) {
            dev.underdeep.guide.Uses.add(ITEMS.get(a.id()), "ammunition for any bow");
        }
        // blocks
        ARTISAN_BENCH = Reg.blockItem("artisan_bench", new Artisan.ArtisanBenchBlock(wood()));
        WAYMARKER = Reg.blockItem("waymarker", new EquipmentTools.WaymarkerBlock(AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN)
                .strength(0.5f).noCollision().nonOpaque().luminance(st -> 10).sounds(BlockSoundGroup.WOOD).pistonBehavior(PistonBehavior.DESTROY)));
        GLIMMER_LANTERN = Reg.blockItem("glimmer_lantern", new LanternBlock(AbstractBlock.Settings.create().mapColor(MapColor.PALE_YELLOW)
                .strength(3.5f).sounds(BlockSoundGroup.LANTERN).luminance(st -> 15).nonOpaque().pistonBehavior(PistonBehavior.DESTROY)));
        ROPE_LADDER = Reg.cutout(Reg.block("rope_ladder", new LadderBlock(AbstractBlock.Settings.create().strength(0.4f).nonOpaque()
                .sounds(BlockSoundGroup.WOOL).pistonBehavior(PistonBehavior.DESTROY).burnable())));
        Reg.CUTOUT.add(WAYMARKER);
        Reg.CUTOUT.add(GLIMMER_LANTERN);
        Upgrades.init();
        Accessories.init();
        EquipmentEffects.init();
        EquipmentTools.init();
        Artisan.init();
    }

    /** The Stage 8 processing stations share the Stage 7 station block entity: registered while it is being built. */
    public static List<Block> registerStationBlocks() {
        ORE_CRUSHER = station("ore_crusher", Station.CRUSH, stone());
        ALLOY_FURNACE = station("alloy_furnace", Station.ALLOY, stone());
        FORGE = station("forge", Station.FORGE, stone());
        GEM_CUTTER = station("gem_cutter", Station.GEMS, wood());
        LEATHERWORKER_BENCH = station("leatherworker_bench", Station.LEATHER, wood());
        return STATION_BLOCKS;
    }

    static AbstractBlock.Settings stone() {
        return AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).strength(3f).sounds(BlockSoundGroup.STONE).requiresTool();
    }

    static AbstractBlock.Settings wood() {
        return AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(2f).sounds(BlockSoundGroup.WOOD).nonOpaque().burnable();
    }

    static Block station(String id, Station st, AbstractBlock.Settings s) {
        boolean full = st == Station.CRUSH || st == Station.ALLOY || st == Station.FORGE;
        Block b = Reg.blockItem(id, new StationBlock(full ? s : s.nonOpaque(), st, full ? VoxelShapes.fullCube()
                : Block.createCuboidShape(0, 0, 0, 16, 16, 16), true));
        STATION_BLOCKS.add(b);
        return b;
    }

    public static Item get(String id) {
        Item it = ITEMS.get(id);
        return it != null ? it : Registries.ITEM.get(Reg.id(id));
    }

    /** Equipment slot category of an item for upgrades: weapon, tool, helmet, chestplate, leggings, boots, bow (or null). */
    public static String category(Item item) {
        if (item instanceof ArmorItem ai) {
            return switch (ai.getSlotType()) {
                case HEAD -> "helmet";
                case CHEST -> "chestplate";
                case LEGS -> "leggings";
                case FEET -> "boots";
                default -> null;
            };
        }
        if (item instanceof net.minecraft.item.BowItem || item instanceof net.minecraft.item.CrossbowItem) {
            return "bow";
        }
        if (item instanceof net.minecraft.item.SwordItem || WEAPON_DEF.containsKey(item) || item instanceof net.minecraft.item.TridentItem
                || item instanceof net.minecraft.item.MaceItem) {
            return "weapon";
        }
        if (item instanceof net.minecraft.item.MiningToolItem) {
            return item instanceof net.minecraft.item.AxeItem && !TOOL_DEF.containsKey(item) && !(WEAPON_DEF.containsKey(item)) ? "tool" : "tool";
        }
        return null;
    }

    public static EquipmentSlot slotOfPublic(String category) {
        return slotOf(category);
    }

    static EquipmentSlot slotOf(String category) {
        return switch (category) {
            case "helmet" -> EquipmentSlot.HEAD;
            case "chestplate" -> EquipmentSlot.CHEST;
            case "leggings" -> EquipmentSlot.LEGS;
            case "boots" -> EquipmentSlot.FEET;
            default -> EquipmentSlot.MAINHAND;
        };
    }
}
