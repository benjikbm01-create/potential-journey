#!/usr/bin/env python3
"""
Underdeep advancements -> src/main/resources/data/underdeep/advancement/<tab>/<id>.json

Optional goals that point players at real, implemented content. Criteria use real gameplay events (the
underdeep:event trigger, fired by game code only when an action succeeds) or vanilla triggers. Each tab has a root
granted on arrival. Hidden advancements never reveal their subject before they are earned.
Run: python3 tools/advancements/generate.py
"""
import json
import os
import shutil

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "data", "underdeep", "advancement")
NS = "underdeep"
ADV = []
TABS = {}


def ev(event, min=1, distinct=0, subject=None):
    c = {"event": event}
    if min != 1:
        c["min"] = min
    if distinct:
        c["distinct"] = distinct
    if subject:
        c["subject"] = subject
    return {"trigger": f"{NS}:event", "conditions": c}


def item(*ids):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [i if ":" in i else f"{NS}:{i}" for i in ids]}]}}


def consume(*ids):
    return {"trigger": "minecraft:consume_item", "conditions": {"item": {"items": [i if ":" in i else f"{NS}:{i}" for i in ids]}}}


def tab(tid, title, icon, desc, bg):
    TABS[tid] = True
    ADV.append({"tab": tid, "id": "root", "parent": None, "icon": icon, "title": title, "desc": desc, "frame": "task", "crit": {"arrive": {
        "trigger": "minecraft:tick"}}, "hidden": False, "xp": 0, "bg": bg, "toast": False})


def adv(tid, aid, parent, icon, title, desc, crit, frame="task", hidden=False, xp=0, any_of=False):
    if not isinstance(crit, dict) or "trigger" in crit:
        crit = {"done": crit}
    ADV.append({"tab": tid, "id": aid, "parent": parent, "icon": icon, "title": title, "desc": desc, "frame": frame, "crit": crit,
                "hidden": hidden, "xp": xp, "any": any_of})


def each(items_):
    """One criterion per item (partial progress shown)."""
    return {i.split(":")[-1]: item(i) for i in items_}


REGIONS = ["rootwild", "fungal_expanse", "flooded_depths", "ashen_hollows", "crystal_deep", "the_abyss", "everfrost", "golden_grove", "hollow_peaks",
           "emerald_wetlands", "petrified_forest", "crimson_basin", "sunken_gardens", "stormstone_caverns", "starlight_caverns"]
CIVS = ["rootfolk", "forgekin", "tide_dwellers", "sporefolk", "glimmerkin", "stoneborn", "amberfolk", "astrals"]

# ------------------------------------------------------------------------------------------ Getting started
tab("start", "Underdeep", "glowcap", "Life underground. Open the handbook with H.", "minecraft:textures/block/deepslate_tiles.png")
adv("start", "handbook", "root", "underdeep_handbook", "Required Reading", "Carry the Underdeep Handbook", item("underdeep_handbook"))
adv("start", "wood", "root", "duskoak_log", "Roots of Industry", "Gather wood from an underground tree",
    item("duskoak_log", "lumenwood_log", "mirewillow_log", "frostfir_log", "goldwood_log", "spirebark_log", "bloodthorn_log", "cinderbark_log"))
adv("start", "stone_tools", "wood", "minecraft:stone_pickaxe", "Stone Age", "Make a stone pickaxe", item("minecraft:stone_pickaxe"))
adv("start", "copper", "stone_tools", "copper_pickaxe", "Copper Delver", "Make a copper pickaxe", item("copper_pickaxe"))
adv("start", "iron", "stone_tools", "minecraft:iron_ingot", "Iron Underground", "Smelt an iron ingot", item("minecraft:iron_ingot"))
adv("start", "unfamiliar", "stone_tools", "cinnabar", "What Is This?", "Mine a mineral found only in the Underdeep",
    item("cinnabar", "sulfur", "niter", "emberite", "cryolite", "malachite", "galvanite", "prismatic_opal", "jasper", "fossil_fragment", "pearl",
         "raw_starsilver", "raw_umbralite", "glimmer_shard"))
adv("start", "cooked", "root", "minecraft:cooked_beef", "Hot Meal", "Eat something cooked",
    consume("cooked_game_meat", "cooked_fish_fillet", "cooked_shellmeat", "baked_duskroot", "minecraft:cooked_beef", "minecraft:cooked_chicken",
            "minecraft:cooked_porkchop", "minecraft:cooked_mutton", "minecraft:cooked_cod", "minecraft:cooked_salmon", "roasted_truffle"))
adv("start", "shelter", "root", "minecraft:torch", "Light in the Dark", "Carry torches: monsters only appear in darkness", item("minecraft:torch"))
adv("start", "settlement", "root", "minecraft:bell", "Not Alone", "Discover a settlement", ev("settlement_discovered"))
adv("start", "expedition", "settlement", "travel_ration", "Packed and Ready", "Carry a travel ration and a bandage",
    {"ration": item("travel_ration", "ember_ration", "frost_ration"), "bandage": item("bandage")})
adv("start", "first_emerald", "settlement", "minecraft:emerald", "Pocket Money", "Earn or find an emerald", item("minecraft:emerald"))

# ------------------------------------------------------------------------------------------ Exploration
tab("exploration", "Exploration", "minecraft:compass", "Fifteen ecosystems lie in the dark.", "minecraft:textures/block/tuff.png")
adv("exploration", "first_region", "root", "glowfern", "Beyond the Roots", "Visit an ecosystem other than the Rootwild",
    {r: ev("region_visited", subject=r) for r in REGIONS if r != "rootwild"}, any_of=True)
adv("exploration", "five", "first_region", "minecraft:map", "Wanderer", "Visit five ecosystems", ev("region_visited", distinct=5), "goal", xp=50)
adv("exploration", "ten", "five", "minecraft:filled_map", "Cartographer of the Deep", "Visit ten ecosystems", ev("region_visited", distinct=10), "goal",
    xp=100)
adv("exploration", "all", "ten", "delvers_compass", "Every Cavern", "Visit all fifteen ecosystems", {r: ev("region_visited", subject=r) for r in REGIONS},
    "challenge", xp=250)
for r, title, icon, desc in (("ashen_hollows", "Into the Ash", "emberite", "Enter the Ashen Hollows"),
                             ("everfrost", "Cold Feet", "cryolite", "Enter the Everfrost"),
                             ("crystal_deep", "Crystal Halls", "glimmer_shard", "Enter the Crystal Deep"),
                             ("sunken_gardens", "Drowned Gardens", "sea_grapes", "Enter the Sunken Gardens"),
                             ("stormstone_caverns", "Hair on End", "galvanite", "Enter the Stormstone Caverns"),
                             ("the_abyss", "The Bottom of Everything", "raw_umbralite", "Reach the Abyss")):
    adv("exploration", "region_" + r, "first_region", icon, title, desc, ev("region_visited", subject=r), "goal" if r == "the_abyss" else "task")
adv("exploration", "waymarker", "root", "waymarker", "I Was Here", "Set up a waymarker", ev("waymarker"))
adv("exploration", "grapple", "root", "grappling_hook", "Get Over Here", "Swing on a grappling hook", ev("grappled"))
adv("exploration", "glider", "grapple", "cave_glider", "Wings of Silk", "Make a cave glider", item("cave_glider"))
adv("exploration", "diving", "region_sunken_gardens", "nacre_helmet", "Deep Breath", "Make a diving helmet", item("nacre_helmet"))
adv("exploration", "prospect", "root", "prospectors_pick", "Rock Whisperer", "Sense ores with a prospector's pick", ev("prospected"))
adv("exploration", "hazard", "first_region", "cooling_salve", "Fair Warning", "Feel an environmental hazard build", ev("hazard_met"))
adv("exploration", "hazards_all", "hazard", "spore_ward_tincture", "Seasoned Explorer", "Meet six different environmental hazards",
    ev("hazard_met", distinct=6), "challenge", xp=150)

# ------------------------------------------------------------------------------------------ Wildlife
tab("wildlife", "Wildlife", "field_journal", "Herds, hunters and shy things.", "minecraft:textures/block/moss_block.png")
adv("wildlife", "first", "root", "field_journal", "Field Notes", "Record a creature in your journal", ev("creature_discovered"))
adv("wildlife", "ten", "first", "minecraft:writable_book", "Naturalist", "Record ten species", ev("creature_discovered", distinct=10), "goal", xp=50)
adv("wildlife", "thirty", "ten", "minecraft:book", "Living Encyclopedia", "Record thirty species", ev("creature_discovered", distinct=30), "challenge",
    xp=200)
adv("wildlife", "tame", "first", "fungal_feed", "New Friend", "Tame a creature", ev("tamed"))
adv("wildlife", "tame3", "tame", "grain_feed", "Menagerie", "Tame three different species", ev("tamed", distinct=3), "goal", xp=60)
adv("wildlife", "ride", "tame", "harness", "Saddle Up", "Ride a harnessed mount", ev("rode"))
adv("wildlife", "trough", "tame", "feeding_trough", "Feeding Time", "Build a feeding trough", item("feeding_trough"))
adv("wildlife", "products", "first", "horn", "Gifts of the Herd", "Collect three different animal products",
    {k: item(k) for k in ("horn", "scale", "plume", "shell_fragment", "fur_tuft", "glow_gland")}, any_of=False)
adv("wildlife", "milk", "first", "minecraft:milk_bucket", "Fresh From the Cave", "Get a bucket of milk", item("minecraft:milk_bucket"))
adv("wildlife", "fish", "root", "glimmerfin", "Local Catch", "Catch a fish from regional waters", ev("fished_regional"))
adv("wildlife", "fish_all", "fish", "reef_darter", "Angler of the Deep", "Catch glimmerfin, a reef darter and a mire eel",
    {k: item(k) for k in ("glimmerfin", "reef_darter", "mire_eel")}, "goal", xp=60)
adv("wildlife", "trap", "fish", "fish_trap", "Set and Forget", "Make a fish trap", item("fish_trap"))

# ------------------------------------------------------------------------------------------ Civilizations
tab("civilizations", "Civilizations", "minecraft:bell", "Eight peoples of the Underdeep.", "minecraft:textures/block/stone_bricks.png")
adv("civilizations", "first", "root", "minecraft:bell", "Lights Ahead", "Discover a settlement", ev("settlement_discovered"))
adv("civilizations", "village", "first", "minecraft:hay_block", "Village Life", "Discover a village", ev("settlement_size_village"))
adv("civilizations", "town", "village", "minecraft:bricks", "Town Crier", "Discover a town", ev("settlement_size_town"), "goal")
adv("civilizations", "city", "town", "minecraft:lantern", "City Under Stone", "Discover a city", ev("settlement_size_city"), "challenge", xp=100)
adv("civilizations", "peoples3", "first", "minecraft:emerald", "Many Peoples", "Meet three different peoples", ev("civ_met", distinct=3), "goal", xp=50)
adv("civilizations", "peoples_all", "peoples3", "minecraft:beacon", "All Eight", "Meet all eight peoples", {c: ev("civ_met", subject=c) for c in CIVS},
    "challenge", xp=250)
adv("civilizations", "service", "first", "minecraft:potion", "At Your Service", "Use a settlement service (healer, inn)", ev("service"))
adv("civilizations", "inn", "service", "minecraft:red_bed", "A Room for the Night", "Rent a bed at an inn", ev("service", subject="inn_bed"))
adv("civilizations", "smith", "service", "minecraft:anvil", "Good as New", "Have something repaired by a smith", ev("repaired", subject="smith"))
adv("civilizations", "taught", "first", "minecraft:book", "Family Recipe", "Learn a dish from a settlement cook", ev("recipe_learned"))

# ------------------------------------------------------------------------------------------ Relationships
tab("relationships", "People", "minecraft:poppy", "Everyone here has a life of their own.", "minecraft:textures/block/oak_planks.png")
adv("relationships", "talk", "root", "minecraft:writable_book", "Small Talk", "Talk to an inhabitant", ev("talked"))
adv("relationships", "talk20", "talk", "minecraft:paper", "Well Known", "Talk with twenty different people", ev("talked", distinct=20), "goal", xp=50)
adv("relationships", "gift", "talk", "minecraft:poppy", "Thoughtful", "Give someone a gift", ev("gift_given"))
adv("relationships", "help", "talk", "minecraft:chest", "Helping Hand", "Help someone with what they need", ev("helped"))
adv("relationships", "help5", "help", "minecraft:barrel", "Pillar of the Community", "Help five times", ev("helped", min=5), "goal", xp=60)
adv("relationships", "friend", "gift", "minecraft:cake", "A Real Friend", "Become someone's friend", ev("friend_made"), "goal", xp=40)
adv("relationships", "friends5", "friend", "minecraft:cake", "Popular", "Have five friends", ev("friend_made", distinct=5), "challenge", xp=150)
adv("relationships", "companion", "friend", "minecraft:lead", "Travelling Companion", "Ask someone to travel with you", ev("companion_recruited"), "goal",
    xp=50)

# ------------------------------------------------------------------------------------------ Economy
tab("economy", "Economy", "minecraft:emerald", "Emeralds make the caverns go round.", "minecraft:textures/block/emerald_block.png")
adv("economy", "buy", "root", "minecraft:emerald", "Customer", "Buy something from a merchant", ev("bought"))
adv("economy", "sell", "root", "minecraft:wheat", "Trader", "Sell something to a merchant", ev("sold"))
adv("economy", "sell20", "sell", "minecraft:barrel", "Supplier", "Sell twenty times", ev("sold", min=20), "goal", xp=50)
adv("economy", "towns3", "buy", "minecraft:chest_minecart", "Across the Caverns", "Trade in three different settlements", ev("traded_with", distinct=3), "goal",
    xp=60)
adv("economy", "contract", "root", "minecraft:paper", "Signed and Delivered", "Complete a contract", ev("contract_completed"))
adv("economy", "contract5", "contract", "minecraft:writable_book", "Reliable", "Complete five contracts", ev("contract_completed", min=5), "goal", xp=80)
adv("economy", "property", "root", "minecraft:oak_door", "A Place of My Own", "Buy or rent property", ev("property_bought"), "goal", xp=50)
adv("economy", "business", "property", "minecraft:barrel", "Open for Business", "Register a shop", ev("business_opened"), "goal", xp=50)
adv("economy", "sale", "business", "minecraft:emerald_block", "First Customer", "Make a sale in your shop", ev("business_sale"))
adv("economy", "sale50", "sale", "minecraft:emerald_block", "Merchant Prince", "Make fifty sales in your shop", ev("business_sale", min=50), "challenge", xp=200)
adv("economy", "hire", "business", "minecraft:villager_spawn_egg", "Employer", "Hire someone to keep your shop", ev("hired"))

# ------------------------------------------------------------------------------------------ Agriculture
tab("agriculture", "Agriculture", "glowgrain", "Gardens without a sky.", "minecraft:textures/block/rooted_dirt.png")
adv("agriculture", "harvest", "root", "duskroot", "First Harvest", "Harvest and replant a crop (right-click it)", ev("harvested"))
adv("agriculture", "crops5", "harvest", "sunbean", "Diverse Fields", "Harvest five different crops", ev("harvested", distinct=5), "goal", xp=60)
adv("agriculture", "rich_soil", "harvest", "rich_soil", "Good Earth", "Make rich soil", item("rich_soil"))
adv("agriculture", "compost", "root", "compost_bin", "Nothing Wasted", "Empty a full compost bin", ev("composted"))
adv("agriculture", "forage", "root", "duskberries", "Forager", "Pick from a wild bush or herb", ev("foraged"))
adv("agriculture", "herb", "forage", "mendleaf", "Green Thumb", "Take a mendleaf cutting at the herbalist's table", ev("medicine_made", subject="mendleaf"))
adv("agriculture", "fungi", "root", "sporeglow_soil", "Mushroom Grower", "Make sporeglow soil for growing fungi", item("sporeglow_soil"))
adv("agriculture", "aquatic", "root", "sea_grapes", "Underwater Garden", "Gather sea grapes or siltwort", item("sea_grapes", "siltwort_frond"))
adv("agriculture", "sickle", "crops5", "steel_sickle", "Reaper", "Make a sickle", item("steel_sickle"))

# ------------------------------------------------------------------------------------------ Cooking
tab("cooking", "Cooking", "cooking_pot", "Good food makes good explorers.", "minecraft:textures/block/smooth_stone.png")
adv("cooking", "pot", "root", "cooking_pot", "Pot Luck", "Cook something at a workstation", ev("cooked"))
adv("cooking", "dishes10", "pot", "root_stew", "Home Cook", "Cook ten different dishes", ev("cooked", distinct=10), "goal", xp=60)
adv("cooking", "dishes30", "dishes10", "truffle_risotto", "Chef of the Deep", "Cook thirty different dishes", ev("cooked", distinct=30), "challenge", xp=200)
adv("cooking", "batch", "pot", "roast_platter", "Feeding the Crew", "Cook four batches at once", ev("batch_cooked"))
adv("cooking", "regional", "pot", "fungal_hotpot", "Local Flavour", "Cook a regional dish", ev("regional_dish"))
adv("cooking", "regional_all", "regional", "starlit_porridge", "Taste of Every People", "Cook dishes of five peoples", ev("regional_dish", distinct=5),
    "challenge", xp=150)
adv("cooking", "well_fed", "root", "fruit_salad", "Balanced Diet", "Become Well Fed by eating varied food", ev("well_fed"))
adv("cooking", "well_fed2", "well_fed", "amber_hearth_stew", "Feast of Variety", "Become Well Fed II (five food groups)", ev("well_fed_2"), "goal")
adv("cooking", "preserve", "pot", "jerky", "Keeps for Ages", "Preserve food on a drying rack or in a crock",
    {"dried": item("jerky", "dried_fish", "dried_fruit", "dried_mushrooms"), "fermented": ev("preserved")}, any_of=True)
adv("cooking", "drink", "pot", "mendleaf_tea", "Tea Time", "Brew a tea in a kettle", item("mendleaf_tea", "sunleaf_tea", "moonpetal_tea", "emberwort_tea",
                                                                                      "marsh_tea"))
adv("cooking", "ration", "preserve", "travel_ration", "Expedition Ready", "Make travel rations", item("travel_ration"))

# ------------------------------------------------------------------------------------------ Medicine
tab("medicine", "Medicine", "bandage", "Herbs, salves and steady hands.", "minecraft:textures/block/moss_block.png")
adv("medicine", "herbs", "root", "mendleaf_sprig", "Herbalist", "Gather a medicinal herb",
    item("mendleaf_sprig", "sunleaf_blossom", "emberwort_root", "bogwort_leaf", "moonpetal_petal", "siltwort_frond"))
adv("medicine", "made", "herbs", "herbalist_table", "Apothecary", "Prepare medicine at a herbalist's table", ev("medicine_made"))
adv("medicine", "made10", "made", "field_medkit", "Healer's Craft", "Prepare ten different preparations", ev("medicine_made", distinct=10), "goal", xp=60)
adv("medicine", "used", "made", "bandage", "Patched Up", "Use a medicine", ev("medicine_used"))
adv("medicine", "other", "used", "burn_salve", "Bedside Manner", "Treat someone else", ev("treated_other"), "goal", xp=40)
adv("medicine", "protect", "used", "warming_salve", "Prepared", "Use a protective salve or tincture",
    {k: ev("medicine_used", subject=k) for k in ("cooling_salve", "warming_salve", "spore_ward_tincture", "grounding_paste", "clear_air_tincture",
                                                  "breathing_draught")}, any_of=True)
adv("medicine", "healer", "root", "minecraft:glistering_melon_slice", "Second Opinion", "Be treated by a settlement healer", ev("service", subject="healer"))

# ------------------------------------------------------------------------------------------ Homesteading
tab("homesteading", "Homesteading", "bedroll", "A place to come back to.", "minecraft:textures/block/spruce_planks.png")
adv("homesteading", "bedroll", "root", "bedroll", "Sleeping Rough", "Sleep in a bedroll", ev("bedroll_slept"))
adv("homesteading", "camp", "bedroll", "camp_awning", "Base Camp", "Make a camp awning and a supply crate", {"awning": item("camp_awning"),
                                                                                                         "crate": item("supply_crate")})
adv("homesteading", "kitchen", "root", "kitchen_counter", "A Proper Kitchen", "Make a cooking pot, an oven and a preparation table",
    {"pot": item("cooking_pot"), "oven": item("clay_oven"), "prep": item("preparation_table")})
adv("homesteading", "sit", "root", "wooden_chair", "Take a Seat", "Rest a while on a chair or bench", ev("sat"))
adv("homesteading", "rested", "root", "minecraft:red_bed", "Good Night", "Wake up rested", ev("rested"))
adv("homesteading", "comfortable", "rested", "wooden_table", "Home Sweet Home", "Wake up in a very comfortable home (comfort 8+)", ev("comfortable_rest"),
    "goal", xp=80)
adv("homesteading", "storage", "root", "pantry", "Everything in Its Place", "Make a pantry and a medicine cabinet",
    {"pantry": item("pantry"), "medicine": item("medicine_cabinet")})

# ------------------------------------------------------------------------------------------ Equipment
tab("equipment", "Equipment", "steel_sword", "Better tools for deeper places.", "minecraft:textures/block/iron_block.png")
adv("equipment", "steel", "root", "steel_ingot", "Tempered", "Alloy steel in an alloy furnace", ev("alloyed", subject="steel_ingot"))
adv("equipment", "crush", "root", "ore_crusher", "Half Again", "Crush ore in an ore crusher", ev("crushed"))
adv("equipment", "forge", "steel", "forge", "Fires of Industry", "Forge something at the forge", ev("forged"))
adv("equipment", "regional_alloy", "steel", "embersteel_ingot", "Regional Metallurgy", "Alloy embersteel, rimesteel or galvanic bronze",
    {k: ev("alloyed", subject=k) for k in ("embersteel_ingot", "rimesteel_ingot", "galvanic_ingot")}, any_of=True)
adv("equipment", "all_alloys", "regional_alloy", "rimesteel_ingot", "Master Metallurgist", "Alloy all three regional metals",
    {k: ev("alloyed", subject=k) for k in ("embersteel_ingot", "rimesteel_ingot", "galvanic_ingot")}, "goal", xp=80)
adv("equipment", "steel_set", "steel", "steel_chestplate", "Steel Clad", "Make a full set of steel armour",
    {p: item("steel_" + p) for p in ("helmet", "chestplate", "leggings", "boots")}, "goal", xp=60)
adv("equipment", "umbralite", "forge", "umbralite_ingot", "Dark Metal", "Smelt umbralite", item("umbralite_ingot"), "goal")
adv("equipment", "umbral_gear", "umbralite", "umbralite_greatsword", "Heavier Than Night", "Forge umbralite equipment",
    item("umbralite_sword", "umbralite_greatsword", "umbralite_battleaxe", "umbralite_warhammer", "umbralite_pickaxe", "umbralite_chestplate"), "challenge",
    xp=150)
adv("equipment", "weapon_families", "forge", "steel_warhammer", "Weapons Master", "Own a dagger, a spear, a greatsword and a warhammer",
    {"dagger": item(*[f"{m}_dagger" for m in ("copper", "steel", "rimesteel", "galvanic", "starsilver", "iron")], "glimmer_stiletto"),
     "spear": item(*[f"{m}_spear" for m in ("copper", "steel", "rimesteel", "galvanic", "iron", "diamond")], "starsilver_glaive", "nacre_harpoon"),
     "greatsword": item(*[f"{m}_greatsword" for m in ("steel", "embersteel", "umbralite", "iron", "diamond")]),
     "warhammer": item(*[f"{m}_warhammer" for m in ("steel", "embersteel", "umbralite", "iron", "diamond")])}, "goal", xp=80)
adv("equipment", "bow", "root", "horn_recurve_bow", "Fletcher", "Make a shortbow, longbow or recurve bow", item("lumenwood_shortbow", "spirebark_longbow",
                                                                                                             "horn_recurve_bow"))
adv("equipment", "gems", "root", "cut_opal", "Facets", "Cut a gem at the gem cutter", ev("cut_gems"))
adv("equipment", "accessory", "gems", "opal_ring", "Adorned", "Wear an accessory (K opens your slots)", ev("accessory_equipped"))
adv("equipment", "accessories4", "accessory", "heartstone_amulet", "Fully Adorned", "Wear eight different accessories over time",
    ev("accessory_equipped", distinct=8), "goal", xp=60)
adv("equipment", "upgrade", "forge", "honed_edge_kit", "Improved", "Apply an upgrade at the artisan's bench", ev("upgraded"))
adv("equipment", "upgrade10", "upgrade", "reinforced_plating_kit", "Tinkerer", "Upgrade ten items", ev("upgraded", min=10), "goal", xp=80)
adv("equipment", "repair", "root", "repair_kit", "Waste Not", "Repair equipment (bench, kit or smith)", ev("repaired"))
adv("equipment", "salvage", "repair", "artisan_bench", "Recycler", "Salvage equipment you no longer need", ev("salvaged"))
adv("equipment", "protected", "steel", "rimesteel_chestplate", "Dressed for the Weather", "Make armour that protects from a hazard",
    item(*[f"{s}_{p}" for s in ("embersteel", "rimesteel", "galvanic", "fur", "shell", "nacre") for p in ("helmet", "chestplate", "leggings", "boots")]))
adv("equipment", "netherite", "umbralite", "minecraft:netherite_ingot", "From the Deep, Not the Nether", "Make netherite without the Nether",
    item("minecraft:netherite_ingot"), "challenge", xp=150)
adv("equipment", "backpack", "root", "backpack", "Pack Mule", "Make a backpack", item("backpack"))

# ------------------------------------------------------------------------------------------ Adventure (Stage 9-10 will add more)
tab("adventure", "Adventure", "minecraft:iron_sword", "Danger, wonder and rare finds.", "minecraft:textures/block/blackstone.png")
adv("adventure", "hostile", "root", "minecraft:iron_sword", "Monster Hunter", "Defeat an undead monster",
    {"kill": {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [{"condition": "minecraft:entity_properties", "entity": "this",
                                                                                      "predicate": {"type": "#minecraft:undead"}}]}}})
adv("adventure", "special_weapon", "hostile", "embersteel_sword", "Elemental", "Make an embersteel, rimesteel or galvanic weapon",
    item(*[w for w in ("embersteel_sword", "embersteel_greatsword", "embersteel_warhammer", "rimesteel_sword", "rimesteel_spear", "rimesteel_dagger",
                       "galvanic_sword", "galvanic_dagger", "galvanic_spear")]))
adv("adventure", "arrows", "hostile", "frost_arrow", "Trick Shots", "Make special arrows", item("heavy_arrow", "frost_arrow", "venom_arrow", "ember_arrow"))
adv("adventure", "relic", "root", "minecraft:glow_berries", "Something Very Old", "???", item("relic_fruit"), "challenge", hidden=True, xp=100)
adv("adventure", "dark_bloom", "root", "minecraft:spore_blossom", "Bloom in the Dark", "???", item("umbral_petal"), "goal", hidden=True, xp=60)

# ------------------------------------------------------------------------------------------ Mastery
tab("mastery", "Mastery", "minecraft:nether_star", "Long-term goals for dedicated delvers.", "minecraft:textures/block/amethyst_block.png")
adv("mastery", "recipes50", "root", "minecraft:book", "Recipe Collector", "Know fifty recipes beyond the basics", ev("recipe_learned", distinct=50),
    "challenge", xp=200)
adv("mastery", "species40", "root", "field_journal", "Master Naturalist", "Record forty species", ev("creature_discovered", distinct=40), "challenge",
    xp=250)
adv("mastery", "trader", "root", "minecraft:emerald_block", "Trade Baron", "Trade in six different settlements", ev("traded_with", distinct=6), "challenge",
    xp=200)
adv("mastery", "forger", "root", "forge", "Master Smith", "Forge twenty different items", ev("forged", distinct=20), "challenge", xp=200)
adv("mastery", "provider", "root", "roast_platter", "Provider", "Cook 200 times", ev("cooked", min=200), "challenge", xp=150)
adv("mastery", "explorer", "root", "delvers_compass", "Hazard-Proof", "Meet every kind of environmental hazard of the caverns", ev("hazard_met", distinct=9),
    "challenge", xp=250)

# ------------------------------------------------------------------------------------------------ Stage 9: expeditions
tab("expedition", "Expeditions", "minecraft:recovery_compass", "Old places, sealed doors and the things left behind.", "minecraft:textures/block/tuff_bricks.png")
adv("expedition", "first_ruin", "root", "minecraft:mossy_cobblestone", "Someone Lived Here", "Discover an abandoned place", ev("ruin_discovered"))
adv("expedition", "ruins10", "first_ruin", "minecraft:cracked_stone_bricks", "Reader of Ruins", "Discover 10 different kinds of abandoned place",
    ev("ruin_discovered", distinct=10), "goal", xp=40)
adv("expedition", "landmark", "root", "minecraft:spyglass", "Worth the Walk", "Discover a landmark", ev("landmark_discovered"))
adv("expedition", "landmarks5", "landmark", "minecraft:recovery_compass", "Sightseer", "Discover 5 different landmarks", ev("landmark_discovered", distinct=5),
    "goal", xp=50)
adv("expedition", "regions", "landmark", "minecraft:filled_map", "Far and Wide", "Discover old places in 10 different ecosystems",
    ev("explored_region", distinct=10), "challenge", xp=100)
adv("expedition", "dungeon", "first_ruin", "minecraft:iron_door", "Down the Stairs", "Find the entrance to a sealed complex", ev("dungeon_discovered"))
adv("expedition", "puzzle", "dungeon", "rune_dial", "It Moved", "Open an ancient seal by working its mechanism", ev("puzzle_solved"))
adv("expedition", "puzzle_types", "puzzle", "keystone", "Mechanist", "Solve 8 different kinds of ancient mechanism", ev("puzzle_solved", distinct=8), "goal", xp=60)
adv("expedition", "puzzle_all", "puzzle_types", "memory_pillar", "Nothing Stays Shut", "Solve every kind of ancient mechanism",
    ev("puzzle_solved", distinct=16), "challenge", xp=150)
adv("expedition", "trap_sprung", "dungeon", "dart_vent", "Should Have Looked Down", "Set off a trap", ev("trap_sprung"))
adv("expedition", "trap_disarmed", "trap_sprung", "minecraft:stick", "Not Today", "Jam a trap plate with a stick or a pickaxe", ev("trap_disarmed"))
adv("expedition", "coffer", "dungeon", "expedition_cache", "Your Share", "Open an expedition coffer", ev("coffer_opened"))
adv("expedition", "complete", "puzzle", "minecraft:lodestone", "Expedition Complete", "Reach the heart of a sealed complex", ev("expedition_completed"),
    "goal", xp=50)
adv("expedition", "complete_large", "complete", "minecraft:heavy_core", "Into the Deep Vaults", "Complete a large expedition", ev("expedition_large"),
    "challenge", xp=120)
adv("expedition", "together", "complete", "minecraft:bell", "Not Alone Down Here", "Complete an expedition with a friend beside you",
    ev("expedition_together"), "goal", xp=60)
adv("expedition", "complete5", "complete", "minecraft:recovery_compass", "Veteran Delver", "Complete 5 different expeditions", ev("expedition_completed", distinct=5),
    "challenge", xp=150)
adv("expedition", "secret", "first_ruin", "minecraft:tripwire_hook", "Behind the Wall", "Find a hidden room", ev("secret_found"))
adv("expedition", "secrets5", "secret", "minecraft:chiseled_bookshelf", "Curiouser", "Find 5 different hidden places", ev("secret_found", distinct=5), "goal", xp=60)
adv("expedition", "secrets15", "secrets5", "minecraft:echo_shard", "Nothing Is Hidden From You", "Find 15 different hidden places",
    ev("secret_found", distinct=15), "challenge", xp=150)
adv("expedition", "record", "first_ruin", "lore_fragment", "Words in Stone", "Read a historical record", ev("record_read"))
adv("expedition", "records10", "record", "minecraft:writable_book", "Archivist", "Read 10 different historical records", ev("record_read", distinct=10), "goal", xp=40)
adv("expedition", "cultures", "records10", "minecraft:lectern", "Many Voices", "Read records left by 6 different old peoples", ev("culture_record", distinct=6),
    "challenge", xp=100)
adv("expedition", "artifact", "coffer", "encrusted_relic", "Something Under the Crust", "Find an artifact or relic", ev("artifact_found"))
adv("expedition", "identified", "artifact", "minecraft:brush", "Cleaned Up Nicely", "Have a scholar identify an encrusted relic", ev("relic_identified"))
adv("expedition", "artifacts10", "artifact", "minecraft:item_frame", "Collector", "Collect 10 different artifacts and relics", ev("artifact_found", distinct=10),
    "goal", xp=80)
adv("expedition", "artifact_use", "artifact", "tuning_fork", "It Still Works", "Use an exploration artifact", ev("artifact_used"))
adv("expedition", "rumour", "root", "minecraft:emerald", "So They Say", "Hear a rumour of an old place from an inhabitant", ev("rumour_heard"))
adv("expedition", "chart", "rumour", "surveyors_chart", "Mapmaker's Customer", "Buy a surveyor's chart from a cartographer", ev("chart_bought"))
adv("expedition", "report", "rumour", "minecraft:paper", "Field Report", "Be paid by a scholar for what you found", ev("report_paid"))
adv("expedition", "encounters", "root", "minecraft:glow_ink_sac", "Things Happen Down Here", "Witness 6 different events on your travels", ev("encounter", distinct=6),
    "goal", xp=40)
adv("expedition", "first_find", "first_ruin", "minecraft:white_banner", "First Footsteps", "Be the first in your world to discover a place",
    ev("first_discoverer"))
# hidden: rare and exceptional places (titles stay hidden until earned)
adv("expedition", "unique", "artifacts10", "minecraft:nether_star", "One of a Kind", "???", ev("unique_artifact"), "challenge", hidden=True, xp=150)
adv("expedition", "heartwood", "complete", "minecraft:oak_sapling", "The Oldest Root", "???", ev("expedition_completed", subject="heartwood"), "challenge",
    hidden=True, xp=200)
adv("expedition", "singing", "complete", "minecraft:amethyst_cluster", "Chorus", "???", ev("expedition_completed", subject="singing_hall"), "challenge",
    hidden=True, xp=150)
adv("expedition", "archive", "complete", "minecraft:prismarine_crystals", "Dry Pages", "???", ev("expedition_completed", subject="sunken_archive"), "challenge",
    hidden=True, xp=150)
adv("expedition", "sanctum", "complete", "minecraft:sculk_shrieker", "Hush", "???", ev("expedition_completed", subject="silent_sanctum"), "challenge",
    hidden=True, xp=200)
adv("expedition", "procession", "encounters", "minecraft:soul_lantern", "Lights in the Dark", "???", ev("encounter", subject="ghost_lanterns"), "goal",
    hidden=True, xp=60)


# ------------------------------------------------------------------------------------------------ Stage 10: combat
tab("combat", "Combat", "minecraft:iron_sword", "Read the blow, answer it, strike back.", "minecraft:textures/block/polished_blackstone_bricks.png")
adv("combat", "parry", "root", "minecraft:shield", "Not Today", "Parry a blow: raise a shield just in time, or right-click with a weapon", ev("combat_parry"))
adv("combat", "parry25", "parry", "minecraft:shield", "Steady Hands", "Parry 25 blows", ev("combat_parry", min=25), "goal", xp=40)
adv("combat", "dodge", "root", "minecraft:feather", "Too Slow", "Dodge an attack at the last moment", ev("combat_perfect_dodge"))
adv("combat", "dodge25", "dodge", "minecraft:phantom_membrane", "Untouchable", "Dodge 25 attacks at the last moment", ev("combat_perfect_dodge", min=25), "goal", xp=40)
adv("combat", "heavy", "root", "underdeep:steel_warhammer", "Put Your Back Into It", "Land a heavy attack (sneak + attack)", ev("combat_heavy"))
adv("combat", "styles", "heavy", "underdeep:steel_greatsword", "Weapon Master", "Land heavy attacks with 6 different kinds of weapon",
    ev("combat_heavy", distinct=6), "challenge", xp=80)
adv("combat", "multi", "heavy", "underdeep:steel_greatsword", "Clear the Floor", "Hit three foes with one heavy attack", ev("combat_multi_hit"), "goal", xp=30)
adv("combat", "counter", "parry", "minecraft:golden_sword", "Riposte", "Strike back during an opening", ev("combat_counter"))
adv("combat", "counter50", "counter", "minecraft:diamond_sword", "Every Opening", "Land 50 counters", ev("combat_counter", min=50), "challenge", xp=80)
adv("combat", "stagger", "heavy", "minecraft:mace", "Off Balance", "Stagger a foe", ev("combat_stagger"))
adv("combat", "backstab", "stagger", "underdeep:steel_dagger", "From Behind", "Strike a foe from behind with a dagger", ev("combat_backstab"))

# ------------------------------------------------------------------------------------------------ Stage 10: guardians and the way to the end
tab("guardians", "Guardians", "minecraft:wither_skeleton_skull", "Old guardians, old Signs, and a door shut for an age.",
    "minecraft:textures/block/chiseled_deepslate.png")
adv("guardians", "lair", "root", "guardian_altar", "Something Sleeps Here", "Find a guardian's altar", ev("lair_found"))
adv("guardians", "awaken", "lair", "minecraft:bell", "No Turning Back", "Wake a guardian", ev("boss_awakened"))
adv("guardians", "first", "awaken", "minecraft:wither_skeleton_skull", "Guardian Slayer", "Defeat a guardian", ev("boss_defeated"), "goal", xp=50)
adv("guardians", "early", "first", "thunderhide", "Old Ways", "Defeat the three guardians of the old ways", ev("boss_tier_early", distinct=3), "goal", xp=80)
adv("guardians", "sign", "first", "sign_spiral", "A Sign", "Win one of the six Signs", item("sign_spiral", "sign_eye", "sign_wave", "sign_flame", "sign_star", "sign_key"),
    "goal", xp=60, any_of=True)
adv("guardians", "keepers", "sign", "sign_star", "Keepers of the Signs", "Defeat the four lesser Sign keepers", ev("boss_tier_mid", distinct=4), "goal", xp=100)
adv("guardians", "keystone", "sign", "keystone_silence", "A Stone of Silence", "Win the keystone", item("keystone_silence"), "goal", xp=120)
adv("guardians", "listen", "keystone", "keystone_silence", "It Hums", "Listen to the keystone", ev("keystone_listened"))
adv("guardians", "door", "listen", "engine_door", "Set in Stone", "Set a Sign into the old door", ev("sign_set"), "goal", xp=50)
adv("guardians", "open", "door", "engine_door", "Shut for an Age", "Open the old door", ev("engine_door_opened"), "challenge", xp=150)
adv("guardians", "beaten", "open", "minecraft:nether_star", "The End of the Deep", "Defeat what waits beyond the old door", ev("underdeep_beaten"),
    "challenge", xp=500)
adv("guardians", "together", "first", "minecraft:cake", "Shoulder to Shoulder", "Defeat a guardian with friends", ev("boss_together"), "goal", xp=40)
adv("guardians", "revive", "together", "minecraft:golden_apple", "Not On My Watch", "Help a fallen friend back up during a guardian fight",
    ev("boss_revive"), "goal", xp=40)
adv("guardians", "solo", "first", "minecraft:iron_sword", "Alone in the Dark", "Defeat a guardian on your own", ev("boss_solo"), "challenge", xp=80)
adv("guardians", "clean", "first", "minecraft:golden_chestplate", "Without a Scratch", "Defeat a guardian without anyone falling", ev("boss_clean"),
    "challenge", xp=80)
adv("guardians", "late", "keepers", "resonant_crystal", "The Greater Guardians", "Defeat the three greatest Sign keepers", ev("boss_tier_late", distinct=3),
    "challenge", xp=150)
adv("guardians", "sovereign", "first", "heartwood_resin", "The Root of It All", "???", ev("boss_defeated", subject="rootsovereign"), "challenge",
    hidden=True, xp=200)
adv("guardians", "watcher", "first", "seamglass", "What Was Kept Out", "???", ev("boss_defeated", subject="seamwatcher"), "challenge", hidden=True, xp=200)
adv("guardians", "all", "beaten", "somnal_ember", "Last of the Old Guard", "???", ev("boss_defeated", distinct=13), "challenge", hidden=True, xp=400)


def main():
    shutil.rmtree(OUT, ignore_errors=True)
    n = 0
    for a in ADV:
        icon = a["icon"] if ":" in a["icon"] else f"{NS}:{a['icon']}"
        display = {"icon": {"id": icon}, "title": {"text": a["title"]}, "description": {"text": a["desc"]}, "frame": a["frame"],
                   "show_toast": a.get("toast", True), "announce_to_chat": a["frame"] == "challenge", "hidden": a["hidden"]}
        if a["parent"] is None:
            display["background"] = a["bg"]
        obj = {"display": display, "criteria": a["crit"]}
        if a["parent"]:
            obj["parent"] = f"{NS}:{a['tab']}/{a['parent']}"
        if a.get("any"):
            obj["requirements"] = [list(a["crit"].keys())]
        if a["xp"]:
            obj["rewards"] = {"experience": a["xp"]}
        path = os.path.join(OUT, a["tab"], a["id"] + ".json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            json.dump(obj, f, indent=1)
        n += 1
    ids = {(a["tab"], a["id"]) for a in ADV}
    for a in ADV:
        assert a["parent"] is None or (a["tab"], a["parent"]) in ids, (a["tab"], a["id"])
    print(len(TABS), "tabs,", n, "advancements (", n - len(TABS), "excluding roots )")


if __name__ == "__main__":
    main()
