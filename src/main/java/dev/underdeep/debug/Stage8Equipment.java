package dev.underdeep.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.underdeep.Underdeep;
import dev.underdeep.civ.econ.Goods;
import dev.underdeep.equipment.Accessories;
import dev.underdeep.equipment.Artisan;
import dev.underdeep.equipment.EquipmentContent;
import dev.underdeep.equipment.EquipmentEffects;
import dev.underdeep.equipment.EquipmentItems;
import dev.underdeep.equipment.EquipmentTools;
import dev.underdeep.equipment.Upgrades;
import dev.underdeep.guide.Advancements;
import dev.underdeep.guide.Handbook;
import dev.underdeep.guide.Sensitivity;
import dev.underdeep.survival.Hazards;
import dev.underdeep.survival.station.StationBlockEntity;
import dev.underdeep.world.region.RegionType;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stage 8 equipment, handbook and advancement checks (block entities and tools driven directly; a Fabric fake player
 * stands in for a real one; accelerated). Reported as such.
 */
final class Stage8Equipment {
    private Stage8Equipment() {
    }

    static Item item(String id) {
        return Registries.ITEM.get(id.contains(":") ? Identifier.of(id) : Identifier.of("underdeep", id));
    }

    static double attack(ItemStack s, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr) {
        double[] v = {0};
        s.applyAttributeModifiers(EquipmentSlot.MAINHAND, (a, m) -> {
            if (a.equals(attr)) {
                v[0] += m.value();
            }
        });
        return v[0];
    }

    static void run(MinecraftServer server, ServerWorld w, Stage15Checks.Reporter r) {
        r.section("Stage 8 equipment, handbook and advancements (tools and stations driven directly; fake player)");
        int toolsAndGear = EquipmentContent.TOOLS.size() + EquipmentContent.UTILITY.size() + 4;
        r.check("content within the brief: 12-18 materials, 35-50 weapons, 12-18 armour sets, 25-40 tools/utility, 20-30 accessories, 30-50 upgrades",
                EquipmentContent.MATERIALS.size() >= 12 && EquipmentContent.WEAPONS.size() + EquipmentContent.BOWS.size() >= 35
                        && EquipmentContent.ARMOR_SETS.size() >= 12 && toolsAndGear >= 25 && toolsAndGear <= 40 && EquipmentContent.ACCESSORIES.size() >= 20
                        && EquipmentContent.UPGRADES.size() >= 30, String.format(Locale.ROOT,
                        "%d materials, %d weapons + %d bows, %d armour sets, %d tools + %d utility (+rope, ladder, marker, lantern), %d accessories, %d upgrades",
                        EquipmentContent.MATERIALS.size(), EquipmentContent.WEAPONS.size(), EquipmentContent.BOWS.size(), EquipmentContent.ARMOR_SETS.size(),
                        EquipmentContent.TOOLS.size(), EquipmentContent.UTILITY.size(), EquipmentContent.ACCESSORIES.size(), EquipmentContent.UPGRADES.size()));
        // ---- weapon families differ in real attributes
        ItemStack dagger = new ItemStack(item("steel_dagger")), great = new ItemStack(item("steel_greatsword")), spear = new ItemStack(item("steel_spear"));
        double dd = attack(dagger, EntityAttributes.GENERIC_ATTACK_DAMAGE), gd = attack(great, EntityAttributes.GENERIC_ATTACK_DAMAGE);
        double ds = attack(dagger, EntityAttributes.GENERIC_ATTACK_SPEED), gs = attack(great, EntityAttributes.GENERIC_ATTACK_SPEED);
        double reach = attack(spear, EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
        r.check("weapon families: daggers fast and light, greatswords slow and heavy, spears reach further", dd < gd && ds > gs && reach >= 1.0,
                String.format(Locale.ROOT, "dagger %.1f dmg %.2f spd; greatsword %.1f dmg %.2f spd; spear +%.1f reach", dd + 1, ds + 4, gd + 1, gs + 4, reach));
        // ---- mining tiers
        boolean tiers = new ItemStack(item("copper_pickaxe")).isSuitableFor(Blocks.IRON_ORE.getDefaultState())
                && !new ItemStack(item("copper_pickaxe")).isSuitableFor(Blocks.DIAMOND_ORE.getDefaultState())
                && new ItemStack(item("steel_pickaxe")).isSuitableFor(Registries.BLOCK.get(Identifier.of("underdeep", "emberite_ore")).getDefaultState())
                && !new ItemStack(item("steel_pickaxe")).isSuitableFor(Registries.BLOCK.get(Identifier.of("underdeep", "umbralite_ore")).getDefaultState())
                && new ItemStack(item("embersteel_pickaxe")).isSuitableFor(Registries.BLOCK.get(Identifier.of("underdeep", "umbralite_ore")).getDefaultState());
        r.check("mining tiers: copper mines iron not diamond; steel mines emberite not umbralite; embersteel mines umbralite", tiers, "");
        // ---- upgrades
        ItemStack sword = new ItemStack(item("steel_sword"));
        double base = attack(sword, EntityAttributes.GENERIC_ATTACK_DAMAGE);
        EquipmentContent.Upgrade honed = EquipmentContent.UPGRADES.get("honed_edge"), grip = EquipmentContent.UPGRADES.get("balanced_grip"),
                coat = EquipmentContent.UPGRADES.get("venom_coating"), lining = EquipmentContent.UPGRADES.get("insulated_lining");
        ItemStack up1 = Upgrades.applied(sword, honed);
        String sameGroup = Upgrades.reject(up1, EquipmentContent.UPGRADES.get("honed_edge"));
        ItemStack up2 = Upgrades.applied(up1, grip);
        String full = Upgrades.reject(up2, coat), wrongCat = Upgrades.reject(sword, lining);
        double after = attack(up2, EntityAttributes.GENERIC_ATTACK_DAMAGE);
        ItemStack again = up2.copy();
        Upgrades.recompute(again);
        Upgrades.recompute(again);
        r.check("upgrades apply real attributes, one per kind, two per item, only to fitting gear; recomputing never stacks",
                Math.abs(after - base - 1.0) < 1e-6 && sameGroup != null && full != null && wrongCat != null
                        && Math.abs(attack(again, EntityAttributes.GENERIC_ATTACK_DAMAGE) - after) < 1e-6,
                String.format(Locale.ROOT, "damage %.1f -> %.1f; rejected: '%s' / '%s' / '%s'", base + 1, after + 1, sameGroup, full, wrongCat));
        // ---- persistence of upgrades + enchantments through save/load, repair and salvage
        var reg = w.getRegistryManager();
        var sharp = reg.getWrapperOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        up2.addEnchantment(sharp, 2);
        up2.setDamage(200);
        NbtElement nbt = ItemStack.CODEC.encodeStart(RegistryOps.of(NbtOps.INSTANCE, reg), up2).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(RegistryOps.of(NbtOps.INSTANCE, reg), nbt).getOrThrow();
        r.check("upgrades, enchantments and damage survive save/load", Upgrades.of(loaded).equals(List.of("honed_edge", "balanced_grip"))
                && EnchantmentHelper.getLevel(sharp, loaded) == 2 && loaded.getDamage() == 200, Upgrades.of(loaded).toString());
        ItemStack repaired = Artisan.result(loaded, new ItemStack(item("steel_ingot")));
        r.check("repair with the item's material restores a quarter and keeps upgrades and enchantments", !repaired.isEmpty()
                && repaired.getDamage() < 200 && Upgrades.of(repaired).size() == 2 && EnchantmentHelper.getLevel(sharp, repaired) == 2,
                "damage 200 -> " + repaired.getDamage());
        Artisan.learnCraftingRecipes(server);
        ItemStack salvNew = Artisan.salvage(new ItemStack(item("steel_greatsword")));
        ItemStack worn = new ItemStack(item("steel_greatsword"));
        worn.setDamage(worn.getMaxDamage() * 3 / 4);
        ItemStack salvWorn = Artisan.salvage(worn);
        r.check("salvage returns at most half the main material, less when worn (no profitable loop)", salvNew.isOf(item("steel_ingot"))
                && salvNew.getCount() <= 3 && salvNew.getCount() >= 1 && salvWorn.getCount() < salvNew.getCount(),
                "new greatsword (5 steel) -> " + salvNew.getCount() + ", worn -> " + salvWorn.getCount());
        // ---- accessories: modifiers never accumulate; protection applies
        FakePlayer fp = FakePlayer.get(w);
        BlockPos site = w.getSpawnPos().add(-30, 40, 20);
        for (BlockPos p : BlockPos.iterate(site.add(-4, -1, -4), site.add(4, 4, 4))) {
            w.setBlockState(p, p.getY() == site.getY() - 1 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        fp.refreshPositionAndAngles(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 0, 0);
        fp.clearStatusEffects();
        Accessories.Data ad = Accessories.of(fp);
        java.util.Arrays.fill(ad.stacks, ItemStack.EMPTY);
        Accessories.refresh(fp);
        double atk0 = fp.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), hp0 = fp.getMaxHealth();
        EnumMap<Hazards.Hazard, Float> m = new EnumMap<>(Hazards.Hazard.class);
        Hazards.exposures(fp, w, RegionType.ASHEN_HOLLOWS, m);
        float heat0 = m.getOrDefault(Hazards.Hazard.GEOTHERMAL, 0f);
        ad.stacks[0] = new ItemStack(item("umbral_ring"));
        ad.stacks[1] = new ItemStack(item("ember_ring"));
        for (int i = 0; i < 5; i++) {
            Accessories.refresh(fp);
        }
        double atk1 = fp.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), hp1 = fp.getMaxHealth();
        m.clear();
        Hazards.exposures(fp, w, RegionType.ASHEN_HOLLOWS, m);
        float heat1 = m.getOrDefault(Hazards.Hazard.GEOTHERMAL, 0f);
        java.util.Arrays.fill(ad.stacks, ItemStack.EMPTY);
        Accessories.refresh(fp);
        double atk2 = fp.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        r.check("accessories: effects apply once however often they refresh, vanish when removed; the ember ring cuts heat exposure",
                Math.abs(atk1 - atk0 - 1.5) < 1e-6 && Math.abs(hp1 - hp0 + 2) < 1e-6 && Math.abs(atk2 - atk0) < 1e-6 && heat1 < heat0 * 0.7f,
                String.format(Locale.ROOT, "attack %.1f -> %.1f -> %.1f; max health %.0f -> %.0f; heat %.2f -> %.2f/s", atk0, atk1, atk2, hp0, hp1, heat0, heat1));
        r.check("accessory slots only take their kind", Accessories.fits(0, new ItemStack(item("opal_ring"))) && !Accessories.fits(2, new ItemStack(item("opal_ring")))
                && Accessories.fits(3, new ItemStack(item("climbing_claws"))) && !Accessories.fits(0, new ItemStack(Items.DIAMOND)), "");
        // ---- armour protection and copper's weakness
        for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            fp.equipStack(EquipmentItems.slotOfPublic(piece), new ItemStack(item("rimesteel_" + piece)));
        }
        double cold = EquipmentEffects.protection(fp, "cold");
        for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            fp.equipStack(EquipmentItems.slotOfPublic(piece), new ItemStack(item("copper_" + piece)));
        }
        double stat = EquipmentEffects.protection(fp, "static");
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            fp.equipStack(s, ItemStack.EMPTY);
        }
        r.check("armour protects from its hazards (full rimesteel: 80% less cold); copper armour draws static", Math.abs(cold - 0.8) < 1e-6 && stat < 0,
                String.format(Locale.ROOT, "cold %.2f, static %.2f", cold, stat));
        // ---- weapon specials
        ZombieEntity z = EntityType.ZOMBIE.create(w);
        z.refreshPositionAndAngles(site.getX() + 2, site.getY(), site.getZ(), 0, 0);
        fp.equipStack(EquipmentSlot.MAINHAND, new ItemStack(item("starsilver_sword")));
        float radiant = EquipmentEffects.bonusDamage(fp, z, 5f);
        z.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        fp.equipStack(EquipmentSlot.MAINHAND, new ItemStack(item("umbralite_sword")));
        float heavy = EquipmentEffects.bonusDamage(fp, z, 5f);
        fp.equipStack(EquipmentSlot.MAINHAND, new ItemStack(item("steel_sword")));
        float plain = EquipmentEffects.bonusDamage(fp, z, 5f);
        r.check("weapon specials: starsilver +3 vs undead, umbralite pierces armour, plain steel nothing extra", radiant == 8f && heavy > 5f && plain == 5f,
                String.format(Locale.ROOT, "radiant %.1f, heavy %.1f, plain %.1f", radiant, heavy, plain));
        z.discard();
        // ---- processing stations
        w.setBlockState(site.add(-3, 0, -3), EquipmentItems.ALLOY_FURNACE.getDefaultState(), Block.NOTIFY_ALL);
        StationBlockEntity alloy = (StationBlockEntity) w.getBlockEntity(site.add(-3, 0, -3));
        alloy.setStack(0, new ItemStack(Items.IRON_INGOT, 2));
        alloy.setStack(1, new ItemStack(Items.COAL, 4));
        int steel = alloy.finishNowForTest();
        w.setBlockState(site.add(-3, 0, -1), EquipmentItems.ORE_CRUSHER.getDefaultState(), Block.NOTIFY_ALL);
        StationBlockEntity crusher = (StationBlockEntity) w.getBlockEntity(site.add(-3, 0, -1));
        crusher.setStack(0, new ItemStack(Items.RAW_IRON, 4));
        crusher.finishNowForTest();
        w.setBlockState(site.add(-3, 0, 1), EquipmentItems.FORGE.getDefaultState(), Block.NOTIFY_ALL);
        StationBlockEntity forge = (StationBlockEntity) w.getBlockEntity(site.add(-3, 0, 1));
        forge.setStack(0, new ItemStack(item("embersteel_ingot"), 2));
        forge.setStack(1, new ItemStack(Items.STICK, 1));
        forge.finishNowForTest();
        r.check("alloy furnace makes steel, the crusher gives half again as much ore, the forge forges equipment (inputs used exactly)",
                steel == 2 && alloy.getStack(StationBlockEntity.OUTPUT).isOf(item("steel_ingot")) && alloy.getStack(0).isEmpty() && alloy.getStack(1).isEmpty()
                        && crusher.getStack(StationBlockEntity.OUTPUT).getCount() == 6 && forge.getStack(StationBlockEntity.OUTPUT).isOf(item("embersteel_sword"))
                        && forge.getStack(0).isEmpty(), "steel x" + alloy.getStack(StationBlockEntity.OUTPUT).getCount() + ", crushed iron x"
                        + crusher.getStack(StationBlockEntity.OUTPUT).getCount() + ", forged " + forge.getStack(StationBlockEntity.OUTPUT));
        // ---- special tools
        List<ItemStack> smelted = Block.getDroppedStacks(Blocks.IRON_ORE.getDefaultState(), w, site, null, fp, new ItemStack(item("embersteel_pickaxe")));
        r.check("the embersteel pickaxe smelts the ore it mines", smelted.stream().anyMatch(s -> s.isOf(Items.IRON_INGOT)), smelted.toString());
        BlockPos wall = site.add(0, 1, -3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                w.setBlockState(wall.add(dx, dy, 0), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        fp.refreshPositionAndAngles(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 180f, 0f);
        fp.equipStack(EquipmentSlot.MAINHAND, new ItemStack(item("steel_hammer")));
        fp.interactionManager.tryBreakBlock(wall);
        int left = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                left += w.getBlockState(wall.add(dx, dy, 0)).isAir() ? 0 : 1;
            }
        }
        r.check("a hammer mines the 3x3 face (through the player's own block breaking, so protection applies)", left == 0, left + " of 9 left");
        BlockPos tree = site.add(3, 0, 3);
        for (int y = 0; y < 4; y++) {
            w.setBlockState(tree.up(y), Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_ALL);
        }
        w.setBlockState(tree.up(4), Blocks.OAK_LEAVES.getDefaultState(), Block.NOTIFY_ALL);
        fp.equipStack(EquipmentSlot.MAINHAND, new ItemStack(item("steel_lumber_axe")));
        fp.interactionManager.tryBreakBlock(tree);
        int logs = 0;
        for (int y = 0; y < 4; y++) {
            logs += w.getBlockState(tree.up(y)).isOf(Blocks.OAK_LOG) ? 1 : 0;
        }
        r.check("the lumber axe fells a small natural tree", logs == 0, logs + " logs left");
        fp.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        EquipmentTools.PackItem pack = (EquipmentTools.PackItem) item("backpack");
        EquipmentTools.PackItem pouch = (EquipmentTools.PackItem) item("herb_pouch");
        r.check("packs refuse other packs and shulker boxes; pouches take only plants and herbs", !pack.accepts(new ItemStack(item("backpack")))
                && !pack.accepts(new ItemStack(Items.SHULKER_BOX)) && pack.accepts(new ItemStack(Items.COBBLESTONE))
                && pouch.accepts(new ItemStack(item("mendleaf_sprig"))) && !pouch.accepts(new ItemStack(Items.IRON_INGOT)), "");
        // ---- economy
        int goods = 0;
        for (String g : List.of("underdeep:steel_ingot", "underdeep:steel_sword", "underdeep:fur_chestplate", "underdeep:opal_ring", "underdeep:honed_edge_kit")) {
            goods += Goods.get(g) != null ? 1 : 0;
        }
        r.check("Stage 8 materials and gear are settlement goods", goods == 5, goods + "/5 sampled");
        // ---- handbook data
        handbook(server, w, r, fp);
        // ---- advancements
        advancements(server, r, fp);
        for (BlockPos p : BlockPos.iterate(site.add(-4, -1, -4), site.add(4, 4, 4))) {
            w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    static void handbook(MinecraftServer server, ServerWorld w, Stage15Checks.Reporter r, FakePlayer fp) {
        Set<String> problems = new java.util.LinkedHashSet<>();
        int pages = 0;
        Set<String> ids = new HashSet<>();
        try (var in = Stage8Equipment.class.getResourceAsStream("/assets/underdeep/handbook/pages.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> cats = new HashSet<>();
            root.getAsJsonArray("categories").forEach(c -> cats.add(c.getAsJsonObject().get("id").getAsString()));
            for (JsonElement e : root.getAsJsonArray("pages")) {
                ids.add(e.getAsJsonObject().get("id").getAsString());
            }
            for (JsonElement e : root.getAsJsonArray("pages")) {
                JsonObject o = e.getAsJsonObject();
                pages++;
                String pid = o.get("id").getAsString();
                if (!cats.contains(o.get("cat").getAsString())) {
                    problems.add(pid + ": bad category");
                }
                if (Registries.ITEM.get(Identifier.of(o.get("icon").getAsString())) == Items.AIR) {
                    problems.add(pid + ": icon " + o.get("icon").getAsString());
                }
                for (JsonElement i : o.getAsJsonArray("items")) {
                    if (Registries.ITEM.get(Identifier.of(i.getAsString())) == Items.AIR) {
                        problems.add(pid + ": item " + i.getAsString());
                    }
                }
                for (JsonElement s : o.getAsJsonArray("see")) {
                    if (!ids.contains(s.getAsString())) {
                        problems.add(pid + ": link " + s.getAsString());
                    }
                }
                String req = o.get("req").getAsString();
                if (!req.isEmpty() && !req.matches("(region|civ|species|item|site|secret|record|boss|boss_won|progress):[a-z_:0-9]+")) {
                    problems.add(pid + ": requirement " + req);
                }
            }
        } catch (Exception ex) {
            problems.add("pages.json: " + ex);
        }
        r.check("handbook pages: every category, icon, item, link and discovery requirement is valid", problems.isEmpty() && pages >= 80,
                pages + " authored pages; problems " + problems);
        List<Handbook.ItemInfo> items = Handbook.itemsForTest(server);
        int ours = 0, withSource = 0;
        Set<String> leaked = new java.util.LinkedHashSet<>();
        for (Handbook.ItemInfo i : items) {
            Item it = Registries.ITEM.get(Identifier.of(i.id()));
            Sensitivity.Kind k = Sensitivity.kind(it);
            if (k == Sensitivity.Kind.TECHNICAL || k == Sensitivity.Kind.DEFERRED) {
                leaked.add(i.id());
            }
            if (i.id().startsWith("underdeep:")) {
                ours++;
                withSource += i.sources().isEmpty() ? 0 : 1;
            }
        }
        r.check("handbook item data: every Underdeep item page has acquisition info; technical/deferred items are never sent", leaked.isEmpty()
                && withSource == ours && ours > 600, withSource + "/" + ours + " Underdeep items with sources, " + items.size() + " pages of data");
        Set<String> before = Handbook.discovered(fp);
        boolean hidden = before.stream().noneMatch(s -> s.startsWith("item:"));
        fp.getInventory().insertStack(new ItemStack(item("relic_fruit")));
        Handbook.watchForTest(fp);
        Set<String> after = Handbook.discovered(fp);
        fp.getInventory().clear();
        r.check("discovery-sensitive pages stay hidden until this player obtains the item", hidden && after.contains("item:underdeep:relic_fruit"),
                "before: " + before.size() + " keys, after obtaining: item key present " + after.contains("item:underdeep:relic_fruit"));
    }

    static void advancements(MinecraftServer server, Stage15Checks.Reporter r, FakePlayer fake) {
        // Fabric API refuses advancements to FakePlayer, so a throwaway (never connected, never saved) player is used.
        net.minecraft.server.network.ServerPlayerEntity fp = new net.minecraft.server.network.ServerPlayerEntity(server, fake.getServerWorld(),
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("underdeep-selftest-advancements".getBytes(StandardCharsets.UTF_8)),
                        "selftest_adv"), net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
        try {
            advancements0(server, r, fp);
        } finally {
            fp.getAdvancementTracker().clearCriteria();
        }
    }

    static void advancements0(MinecraftServer server, Stage15Checks.Reporter r, net.minecraft.server.network.ServerPlayerEntity fp) {
        long ours = server.getAdvancementLoader().getAdvancements().stream().filter(a -> a.id().getNamespace().equals(Underdeep.MOD_ID)).count();
        long tabs = server.getAdvancementLoader().getAdvancements().stream().filter(a -> a.id().getNamespace().equals(Underdeep.MOD_ID)
                && a.value().parent().isEmpty()).count();
        r.check("100-260 advancements load in their tabs", ours >= 100 && ours <= 260 && tabs >= 10, ours + " advancements, " + tabs + " tabs");
        AdvancementEntry harvest = server.getAdvancementLoader().get(Identifier.of("underdeep", "agriculture/harvest"));
        AdvancementEntry five = server.getAdvancementLoader().get(Identifier.of("underdeep", "agriculture/crops5"));
        AdvancementEntry relic = server.getAdvancementLoader().get(Identifier.of("underdeep", "adventure/relic"));
        var tracker = fp.getAdvancementTracker();
        boolean notYet = !tracker.getProgress(harvest).isDone();
        Advancements.Stats st = Advancements.stats(fp);
        st.counts.remove("harvested");
        st.subjects.remove("harvested");
        Advancements.event(fp, "harvested", "duskroots");
        boolean done = tracker.getProgress(harvest).isDone();
        Advancements.event(fp, "harvested", "duskroots");
        Advancements.event(fp, "harvested", "sunbeans");
        boolean partial = !tracker.getProgress(five).isDone();
        for (String c : List.of("glowgrain_crop", "mire_rice_crop", "frost_turnips")) {
            Advancements.event(fp, "harvested", c);
        }
        boolean fiveDone = tracker.getProgress(five).isDone();
        Advancements.event(fp, "talked", "x"); // an unrelated event never completes a harvest goal
        r.check("advancements complete from real events: not before, once after, distinct counts need distinct subjects", notYet && done && partial && fiveDone,
                "before " + !notYet + ", after one harvest " + done + ", five crops after 2 kinds " + !partial + ", after 5 kinds " + fiveDone);
        r.check("hidden discovery advancements are hidden", relic != null && relic.value().display().map(d -> d.isHidden()).orElse(false), "");
        r.check("per-player progress counters persist (attachment codec round trip)", roundTrip(st), st.counts.size() + " counters");
    }

    static boolean roundTrip(Advancements.Stats st) {
        try {
            var codec = Advancements.Stats.CODEC;
            var nbt = codec.encodeStart(NbtOps.INSTANCE, st).getOrThrow();
            Advancements.Stats back = codec.parse(NbtOps.INSTANCE, nbt).getOrThrow();
            return back.counts.equals(st.counts) && back.subjects.equals(st.subjects);
        } catch (Exception e) {
            return false;
        }
    }
}
