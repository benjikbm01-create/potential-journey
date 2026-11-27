#!/usr/bin/env python3
"""
Underdeep Handbook authored pages -> src/main/resources/assets/underdeep/handbook/pages.json

Authored pages explain mechanics; item, creature, ecosystem and civilization pages are generated in game from the
registries, recipes and the server's obtainability audit. Every page here must describe implemented behaviour only.
Page fields: id, cat, title, icon (item id), keys (search words), body (paragraphs), items (item ids shown as icons),
see (related page ids), req (optional discovery requirement: "region:<id>", "civ:<id>", "item:<id>", "species:<id>").
Run: python3 tools/handbook/handbook.py
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "underdeep", "handbook", "pages.json")
CATS = [
    ("start", "Getting Started", "minecraft:crafting_table"), ("world", "World & Ecosystems", "underdeep:glowcap"),
    ("materials", "Materials & Resources", "minecraft:raw_iron"), ("wildlife", "Wildlife", "underdeep:field_journal"),
    ("civs", "Civilizations", "minecraft:bell"), ("people", "People & Companions", "minecraft:emerald"),
    ("economy", "Economy", "minecraft:emerald"), ("farming", "Farming", "underdeep:glowgrain"), ("cooking", "Cooking", "underdeep:cooking_pot"),
    ("medicine", "Medicine", "underdeep:bandage"), ("homes", "Camping & Homes", "underdeep:bedroll"),
    ("equipment", "Equipment", "underdeep:steel_sword"), ("explore", "Exploration", "minecraft:recovery_compass"),
    ("journal", "Your Journal", "underdeep:lore_fragment"), ("controls", "Controls & Settings", "minecraft:comparator")]
PAGES = []


def page(pid, cat, title, icon, body, keys=(), items=(), see=(), req=None):
    PAGES.append({"id": pid, "cat": cat, "title": title, "icon": icon, "keys": list(keys), "body": list(body), "items": list(items),
                  "see": list(see), "req": req or ""})


# ------------------------------------------------------------------------------------------------ Getting started
page("welcome", "start", "Welcome to the Underdeep", "underdeep:glowcap", [
    "The whole world is underground: vast connected caverns with their own forests, lakes, fungi, crystal halls and frozen or volcanic"
    " depths. There is no surface to return to. People live down here in settlements, wildlife roams in herds, and the world keeps"
    " going whether you watch it or not.",
    "You are one more inhabitant. There is no quest you must follow: explore, farm, trade, make friends, craft better gear, or all of it.",
    "This handbook explains how things work. Open it any time with H (change it in Controls). Pages about creatures, places and peoples"
    " fill in as you discover them. The advancements screen (L) suggests things to try next."], keys=["start", "begin", "new", "help", "guide"],
     see=["first_hours", "using_handbook"])
page("first_hours", "start", "Your first hours", "minecraft:wooden_pickaxe", [
    "1. You start in the Rootwild, a mild forested cavern. Its trees give wood; its stone gives stone tools. Look for bare ore"
    " outcrops near the start: coal, copper and iron show in the walls.",
    "2. Make a crafting table, then wooden and stone tools. A stone pickaxe mines iron and most regional ores.",
    "3. Food: pick berries from bushes (right-click a ripe bush), dig duskroots, hunt game, or fish. Cook meat on a campfire or furnace.",
    "4. Light keeps monsters away: they only appear in total darkness. Torches around your camp make it safe.",
    "5. Build or claim a shelter: any enclosed space with light. A bed or a bedroll lets you sleep through the night.",
    "6. Head out along the caverns. When you find a settlement its name appears on screen; talk to people (right-click).",
    "7. Emeralds are the currency: sell surplus to merchants, take on contracts, and buy what you need."],
     keys=["first", "start", "basics", "tools", "food", "shelter"], items=["minecraft:crafting_table", "minecraft:stone_pickaxe", "minecraft:torch"],
     see=["starting_resources", "finding_food", "staying_safe", "finding_people", "making_money", "mining_tiers"])
page("starting_resources", "start", "Basic resources underground", "minecraft:cobblestone", [
    "Wood: every forested cavern has trees (duskoak in the Rootwild; other ecosystems grow their own woods). Logs make planks, sticks,"
    " crafting tables and chests as usual.",
    "Stone, coal, copper, iron, gold, redstone, lapis and diamonds generate in cavern walls throughout the Underdeep, deeper ones more"
    " often in the deep layer. Sand, gravel and clay sit on cavern floors and lake beds.",
    "Cows, sheep and chickens live in the gentle regions (leather, wool, feathers, eggs, milk). Wildlife adds more: hides become leather"
    " on a drying rack or leatherworker's bench, fur tufts felt into wool, plumes give feathers, silk strands and spiders give string."
    " Paper comes from sugar cane.",
    "Some things normally found on the surface or in the Nether have underground routes: blaze rods from emberite and sulfur,"
    " nether wart from bloodcaps, honey from honeyfruit, name tags from paper, netherite scrap from umbralite in an alloy furnace."],
     keys=["wood", "stone", "iron", "coal", "copper", "sand", "clay", "leather", "paper", "nether", "blaze", "netherite"],
     see=["mining_tiers", "processing", "materials_overview"])
page("finding_food", "start", "Finding food", "underdeep:duskberries", [
    "Wild food: berries and fruit from bushes, roots and grains from wild crops, edible mushrooms, game meat and fish.",
    "Cook simple food on a campfire or furnace. A cooking pot on a campfire turns ingredients into hearty stews.",
    "Hunger works as in vanilla Minecraft. Eating from several food groups (grain, vegetable, fruit, fungi, meat, fish, sweet) makes"
    " you Well Fed: you tire more slowly."], keys=["food", "hunger", "eat", "berries", "meat"], see=["cook_stations", "variety", "fishing"])
page("staying_safe", "start", "Staying safe", "minecraft:torch", [
    "Monsters spawn only in complete darkness: light your paths and camps.",
    "Dangerous ecosystems have environmental hazards (heat, cold, spores, fumes, static, pressure). You always get a warning first that"
    " names the hazard and what helps. The starting regions have none.",
    "Some wildlife is territorial or hunts. Watch how creatures behave before approaching.",
    "Carry food, a bandage and the right protection when you travel far."], keys=["danger", "safe", "monsters", "hazard"],
     see=["hazards", "medicine_basics", "protection"])
page("finding_people", "start", "Finding people", "minecraft:bell", [
    "Settlements sit along the cavern network: encampments, villages, towns and cities of eight peoples. When you first see one its name"
    " and people appear on screen and it is added to what you know.",
    "Right-click an inhabitant to talk. Sneak and right-click a trader to trade straight away.",
    "People remember you: be kind, help with their requests, bring gifts they like, and they will trust you more."],
     keys=["settlement", "village", "town", "people", "npc", "villager"], see=["settlements", "talking", "trading"])
page("making_money", "start", "How to make money", "minecraft:emerald", [
    "Emeralds are the currency. Ways to earn them: sell goods to merchants (they buy what their settlement needs and pay more where it"
    " is scarce), take on contracts from a settlement's noticeboard, run a shop, or mine emeralds in the Hollow Peaks.",
    "Prices differ between settlements: buying where something is made and selling where it is short is honest trade."],
     keys=["money", "emerald", "earn", "currency", "rich", "sell"], see=["currency", "contracts", "businesses", "trading"])
page("using_handbook", "start", "Using the handbook and advancements", "underdeep:underdeep_handbook", [
    "Open the handbook with H, or by using the Underdeep Handbook item (you get one when you first arrive; if you lose it, craft one from"
    " a book and a stick, or just use the key).",
    "Search for anything: an item, a material, a mechanic ('how to grow mushrooms', 'repair armour'). Item pages say how to obtain"
    " the item and what it is used for, with recipes. Click an icon to open its page.",
    "Advancements (L) are optional goals that point at things worth trying. Nothing requires them.",
    "Your discoveries are your own: other players' finds do not unlock pages for you."], keys=["handbook", "search", "advancements", "help"],
     see=["controls"])

# ------------------------------------------------------------------------------------------------ World
page("geography", "world", "Underground geography", "minecraft:stone", [
    "The Underdeep has two great layers of caverns, an upper and a deep one, joined by shafts, ramps and flooded passages. Some chambers"
    " are enormous, with their own cliffs, lakes and forests.",
    "Each region belongs to an ecosystem with its own stone, plants, wildlife, resources and (in some) hazards. Ecosystems you have"
    " visited are listed in this category.",
    "Deeper and further from the start, regions are generally more dangerous and richer."], keys=["map", "caves", "layers", "regions"],
     see=["hazards", "ecosystems"])
page("ecosystems", "world", "Ecosystems", "underdeep:glowfern", [
    "There are fifteen ecosystems. Each has a page here once you have been there. Gentle ones (Rootwild, Golden Grove, Emerald Wetlands,"
    " Starlight Caverns) have no environmental hazards; harsher ones reward preparation with valuable materials."],
     keys=["biome", "ecosystem", "region"], see=["geography", "hazards"])
page("hazards", "world", "Environmental hazards", "underdeep:cooling_salve", [
    "Some regions are hostile: volcanic heat, bitter cold and icy water, thin cold wind on the heights, spore clouds among dense fungi,"
    " choking fumes in the depths, static charge in the Stormstone Caverns, deep water pressure and utter darkness in the Abyss.",
    "Exposure builds slowly while you are in the hazard and fades when you leave. At about 40% a message names it and what helps; only"
    " at full exposure does it start to hurt (slowness, weakness, a little damage).",
    "Counterplay: the right food or drink (warming, cooling, spore ward...), medicine (salves, tinctures), protective armour and"
    " accessories, a fire nearby against cold, shade under a camp awning, water against heat, keeping metal away from static.",
    "Hazards you have met are listed in your field journal with their counterplay."], keys=["heat", "cold", "spores", "fumes", "static", "pressure",
                                                                                          "darkness", "hazard", "danger", "protection"],
     see=["protection", "medicine_basics", "meal_effects"])

REGIONS = {
    "rootwild": ("Rootwild", "underdeep:duskoak_log", "A mild forested cavern of duskoaks, moss and streams: where everyone starts. No hazards. Duskberries,"
                 " duskroots, truffles, mendleaf and gentle fungi grow here; coal, copper and iron show in the walls; niter and galvanite"
                 " veins are found too. Home of the Rootfolk."),
    "fungal_expanse": ("Fungal Expanse", "underdeep:puffcap", "Giant mushrooms, sporeglow soil and glowing gills. Dense fungus fills the air"
                       " with spores (spore ward helps). Truffles, puffcaps, inkcaps and many fungi; niter and bog iron. Home of the Sporefolk;"
                       " capbacks roam here."),
    "flooded_depths": ("Flooded Depths", "underdeep:lake_chestnut", "Great underground lakes with islands, glowpads and reed shores. Lake"
                       " chestnuts, sea grapes and siltwort grow in and around the water; nacre ore (pearls) and bog iron in the beds. Glimmerfin"
                       " swim here. Home of the Tide-dwellers."),
    "ashen_hollows": ("Ashen Hollows", "underdeep:emberite", "Volcanic caverns of basalt and ash with lava pools: geothermal heat builds"
                      " exposure (cooling food, salves and embersteel help). Emberite, cinnabar, sulfur, fossils and rich vanilla ores; emberpeppers"
                      " and emberwort grow in the warm ash. Home of the Forgekin."),
    "crystal_deep": ("Crystal Deep", "underdeep:prismatic_opal", "Halls of amethyst, luminite and glimmer crystal. Prismatic opal, starsilver,"
                     " nacre and plenty of diamonds and redstone. Glimmer grows from budding glimmer. Home of the Glimmerkin."),
    "the_abyss": ("The Abyss", "underdeep:raw_umbralite", "The deepest, darkest region, above a sea of lava: choking fumes in its depths, crushing"
                  " pressure in deep water and gloom where no light reaches. Umbralite, the strongest metal, is found here, with fossils,"
                  " sulfur and nacre."),
    "everfrost": ("Everfrost", "underdeep:cryolite", "Frozen caverns of ice and snow: bitter cold and icy water (stay near fire; wear fur or"
                  " rimesteel; eat warming food). Cryolite, frost turnips, snowberries and rimecaps."),
    "golden_grove": ("Golden Grove", "underdeep:honeyfruit", "Warm golden trees, amber resin and honeyfruit. No hazards. Glowgrain and sunbeans grow"
                     " wild; resin knots give amber. Home of the Amberfolk."),
    "hollow_peaks": ("Hollow Peaks", "underdeep:malachite", "Mountains inside the caverns, with cliffs and ledges; biting wind on the heights."
                     " Malachite and emeralds; cliffberries and peakstars. Home of the Stoneborn."),
    "emerald_wetlands": ("Emerald Wetlands", "underdeep:mire_rice", "Marshes and mire pools. No hazards. Mire rice, bogwort, marsh ferns and mire eels;"
                         " bog iron and fossils."),
    "petrified_forest": ("Petrified Forest", "underdeep:fossil_fragment", "A forest turned to stone. Fossils, jasper, amber resin and petrolichen."),
    "crimson_basin": ("Crimson Basin", "underdeep:bloodcap", "Hot crimson caverns: heat, and choking fumes in the depths. Bloodcaps, thornbriar,"
                      " nether quartz and gold, cinnabar and sulfur."),
    "sunken_gardens": ("Sunken Gardens", "underdeep:sea_grapes", "Flooded coral gardens: diving country. Reef darters, shellfish, glowkelp, sea grapes,"
                       " tidecaps and nacre ore."),
    "stormstone_caverns": ("Stormstone Caverns", "underdeep:galvanite", "Charged stone crackles with static: metal you carry draws it (grounding"
                           " paste or galvanic gear help). Galvanite, storm crystals, storm lichen and voltblooms; diamonds and redstone."),
    "starlight_caverns": ("Starlight Caverns", "underdeep:raw_starsilver", "Luminous caverns under a ceiling of light. No hazards. Starsilver,"
                          " moonpetals, glowgrain and starlilies. Home of the Astrals."),
}
for rid, (title, icon, text) in REGIONS.items():
    page("region_" + rid, "world", title, icon, [text], keys=[rid.replace("_", " "), "ecosystem", "region"], see=["hazards"], req="region:" + rid)
CIVS = {
    "rootfolk": ("Rootfolk", "Forest people of the Rootwild: carpenters, herbalists, foresters, brewers and tailors. Their dishes: forest stew and"
                 " glowgrain kvass."),
    "forgekin": ("Forgekin", "Smiths of the Ashen Hollows: blacksmiths, smelters, engineers, miners and masons; the place for metal gear. Their"
                 " dishes: forge goulash, ember flatbread, pickled peppers."),
    "tide_dwellers": ("Tide-dwellers", "Lake and sea people of the Flooded Depths and Sunken Gardens: boatwrights, navigators, tailors, carpenters,"
                      " brewers. Their dishes: tide soup, tide rolls, baked shellfish."),
    "sporefolk": ("Sporefolk", "Fungus-growers of the Fungal Expanse: alchemists, mycologists, herbalists and healers. Their dishes: fungal hotpot,"
                  " mushroom pastry, spore paste, spore kombucha."),
    "glimmerkin": ("Glimmerkin", "Crystal-workers of the Crystal Deep: gemcutters, artisans, lampwrights and scholars; the place for jewellery"
                   " and inlays. Their dish: glimmer broth."),
    "stoneborn": ("Stoneborn", "Mountain people of the Hollow Peaks: masons, blacksmiths, hunters, miners and tailors. Their dishes: marrow broth,"
                  " pickled turnips."),
    "amberfolk": ("Amberfolk", "Warm-hearted people of the Golden Grove: tailors, carpenters, brewers, herbalists and artisans. Their dishes:"
                  " amber hearth stew, honeyfruit tarts and fizz."),
    "astrals": ("Astrals", "Star-watchers of the Starlight Caverns: astronomers, scholars, lampwrights and cartographers. Their dishes: starlit"
                " porridge and starlily nectar."),
}
for cid, (title, text) in CIVS.items():
    page("civ_" + cid, "civs", title, "minecraft:bell", [text], keys=[cid.replace("_", " "), "people", "civilization", "culture"],
         see=["settlements", "regional"], req="civ:" + cid)

# ------------------------------------------------------------------------------------------------ Materials
page("materials_overview", "materials", "Materials overview", "minecraft:raw_iron", [
    "Every material has a page: search its name or click its icon. Pages tell you where it comes from, which tool you need, whether it"
    " is renewable, and every recipe that makes or uses it.",
    "Each ecosystem has something worth the trip: metals and gems for equipment, fibres and hides for clothing, plants for food and"
    " medicine. Combining materials from different regions gives the best gear."], keys=["materials", "resources", "ores"],
     see=["mining_tiers", "processing", "material_families"])
page("mining_tiers", "materials", "Which pickaxe mines what", "minecraft:iron_pickaxe", [
    "Any pickaxe: stone, coal, sulfur, niter, bog iron, fossil ore, copper.",
    "Stone pickaxe or better (also copper): iron, lapis, cinnabar, cryolite, malachite, jasper, nacre.",
    "Iron-tier or better (iron, steel, galvanic, nacre, malachite tools): gold, diamonds, redstone, emeralds, emberite, galvanite,"
    " prismatic opal, starsilver.",
    "Diamond-tier or better (diamond, embersteel, rimesteel, starsilver tools): umbralite and obsidian.",
    "Mining with a tool that is too weak breaks the block but drops nothing; the tool tooltip and this page tell you the tier."],
     keys=["tier", "pickaxe", "mining level", "can't mine", "drops nothing"], items=["minecraft:stone_pickaxe", "underdeep:copper_pickaxe",
                                                                                      "minecraft:iron_pickaxe", "underdeep:steel_pickaxe",
                                                                                      "minecraft:diamond_pickaxe", "underdeep:umbralite_pickaxe"],
     see=["material_families", "tools"])
page("processing", "materials", "Processing: crushing, alloying, cutting, tanning", "underdeep:ore_crusher", [
    "Ore crusher: two raw ores make three crushed ores, which smelt into metal: half again as much metal per ore. It also grinds stone"
    " into gravel and sand, gravel into flint, and bones or fossils into bone meal. It needs no fuel.",
    "Alloy furnace (burns fuel): steel from iron and coal; embersteel and rimesteel from steel with emberite or cryolite; galvanic"
    " bronze from copper and galvanite; netherite scrap from raw umbralite and gold; glass from sand.",
    "Gem cutter: rough gems become cut gems and lenses for jewellery, inlays and lamps.",
    "Leatherworker's bench: hides to leather, fur to linings, scales and shells to plates, silk to cloth, horn to grips, straps, rope.",
    "All workstations show what they can make: open one and look at the recipe list on the right."],
     keys=["crusher", "alloy", "smelt", "steel", "gem", "leather", "process", "refine"],
     items=["underdeep:ore_crusher", "underdeep:alloy_furnace", "underdeep:gem_cutter", "underdeep:leatherworker_bench", "underdeep:steel_ingot"],
     see=["workstations", "material_families"])
page("renewable", "materials", "Renewable and finite resources", "underdeep:compost", [
    "Renewable: crops, bushes, herbs (by cuttings), fungi, trees, fish (waters recover after heavy fishing), shed animal products"
    " (horn, scales, plumes, shells, fur), milk, eggs, and everything settlements produce.",
    "Finite: ores and gems in the rock. They are plentiful, but a region mined out stays mined out: explore further."],
     keys=["renewable", "farm", "infinite", "finite"], see=["farming_basics", "livestock"])

# ------------------------------------------------------------------------------------------------ Wildlife
page("wildlife_basics", "wildlife", "Wildlife", "underdeep:field_journal", [
    "Each ecosystem has its own animals living in herds, pairs or alone. They rest, feed, flee or hunt depending on their nature.",
    "Your field journal records what you learn about each species as you observe it (sneak and use it to turn the pages). Species you"
    " have seen get a page in this category.",
    "Predators and territorial creatures attack; skittish ones flee; most leave you alone if you leave them alone."],
     keys=["animals", "creatures", "journal", "mobs"], items=["underdeep:field_journal"], see=["taming", "riding", "livestock"])
page("taming", "wildlife", "Taming", "underdeep:fungal_feed", [
    "Different species are tamed in different ways (the journal tells you which once you have observed one):",
    "Feeding: feed its favourite food several times. Patience: wait nearby while sneaking and holding its food until it relaxes, then"
    " feed it. Rescue: it only accepts food from you while it is injured. Offering: present a gift it likes, three times, at most once"
    " every 30 seconds.",
    "Hearts mean it has accepted you. Tamed animals follow and are no longer part of the wild herds."],
     keys=["tame", "pet", "befriend", "animal"], see=["riding", "livestock"])
page("riding", "wildlife", "Mounts and pack animals", "underdeep:harness", [
    "Some tamed creatures can be ridden: put a harness on them (use it on the animal), then right-click with an empty hand to ride."
    " Mounts differ: runners, climbers, swimmers and gliders.",
    "Pack animals carry an 18-slot pack: sneak and right-click them to open it."], keys=["ride", "mount", "harness", "pack"],
     items=["underdeep:harness"], see=["taming"])
page("livestock", "wildlife", "Animal care, breeding and products", "underdeep:feeding_trough", [
    "Some animals shed useful materials (horn, scales, plumes, shells, glow glands) over time; some can be sheared for fur or milked.",
    "A feeding trough filled with the right feed (grain feed, fungal feed, fruit mash, meat scraps, mineral lick, bait) feeds hungry"
    " tamed animals within 12 blocks, keeps them productive, and well-fed adults may breed while fewer than 24 tamed animals are"
    " around. Young of tamed parents are tamed.",
    "Make feed at a preparation table."], keys=["breed", "feed", "trough", "milk", "shear", "products", "ranch"],
     items=["underdeep:feeding_trough", "underdeep:grain_feed", "underdeep:fungal_feed", "underdeep:fruit_mash", "underdeep:meat_scraps",
            "underdeep:mineral_lick"], see=["taming"])

# ------------------------------------------------------------------------------------------------ Civilizations
page("settlements", "civs", "Settlements", "minecraft:bell", [
    "Eight peoples live in the Underdeep, each with its own architecture, food, leaders and trades. Their settlements range from small"
    " encampments to cities. Settlements grow, build, trade with each other and react to good and bad times.",
    "Peoples you have met get their own page in this category."], keys=["village", "town", "city", "civilization", "people"],
     see=["talking", "services", "trading"])
page("services", "civs", "Settlement services", "minecraft:emerald", [
    "Merchants trade goods. Cooks, bakers, brewers and innkeepers teach you their dishes. Healers treat your injuries (2 emeralds)."
    " Innkeepers serve a hot meal (1) and rent a bed (2). Smiths repair the item you hold. Leaders and innkeepers keep the"
    " noticeboard of contracts and sell or rent property.",
    "Services use the settlement's own stock: if it has run out of medicine or metal, they cannot help until it is restocked."],
     keys=["heal", "repair", "inn", "bed", "meal", "teach", "recipe", "service"], see=["contracts", "property"])
page("guards", "civs", "Guards and safety", "minecraft:iron_sword", [
    "Guards defend settlements from monsters and dangerous creatures. Monsters do not spawn inside settlements.",
    "Attacking inhabitants makes everyone wary of you and spreads by word of mouth."], keys=["guard", "crime", "attack"], see=["reputation"])

# ------------------------------------------------------------------------------------------------ People
page("talking", "people", "Talking to people", "minecraft:writable_book", [
    "Right-click an inhabitant to talk. Ask about themselves, news, their town and its people, how they feel; they answer from what"
    " they actually know and remember.",
    "Each person has a personality, needs, memories and relationships. Conversations make them more familiar with you."],
     keys=["talk", "conversation", "dialogue", "chat"], see=["gifts", "friendship"])
page("gifts", "people", "Gifts", "minecraft:poppy", [
    "Hold an item and choose 'Give' in conversation. Everyone has likes and dislikes; their people have favourite foods (often their"
    " regional dishes). A good gift raises affection; a gift they dislike does not."], keys=["gift", "give", "present", "likes"],
     see=["friendship"])
page("friendship", "people", "Friendship and reputation", "minecraft:emerald", [
    "Relationships grow from familiarity, trust, affection and respect. Helping with a request ('Do you need anything?') builds trust.",
    "Your reputation in a settlement is what its people think of you on the whole; it affects prices and how they greet you."],
     keys=["friend", "trust", "reputation", "relationship"], see=["companions", "gifts"])
page("companions", "people", "Companions", "minecraft:lead", [
    "When someone knows you well, ask 'Would you travel with me?'. A companion follows you, talks about what you see together, and"
    " can be told to stay close or keep their distance, how to fight (avoid fights, defend themselves, protect you), to rest, to"
    " carry supplies in a shared pack, to go home for now, or to part ways.",
    "Companions eat from the pack, and wear the best armour and wield the best weapon you put in it (only while they travel with"
    " you; the gear stays in the pack). Badly hurt companions withdraw home to recover instead of dying.",
    "Parting ways returns everything in the pack to you."], keys=["companion", "recruit", "follower", "travel", "party", "equipment"],
     see=["friendship"])
page("reputation", "people", "Word of mouth", "minecraft:paper", [
    "News spreads through conversations: people tell each other about events in their town, trade and travellers like you."],
     keys=["news", "gossip"], see=["friendship"])

# ------------------------------------------------------------------------------------------------ Economy
page("currency", "economy", "Currency and prices", "minecraft:emerald", [
    "Emeralds are money. Each settlement has its own stocks and prices: what is plentiful is cheap, what is scarce costs more, and prices"
    " move gradually. Friends of a merchant get a small discount; a trader's token adds a little more."],
     keys=["money", "price", "emerald", "cost"], see=["trading", "making_money"])
page("trading", "economy", "Buying and selling", "minecraft:emerald", [
    "Choose 'Trade' in conversation, or sneak and right-click a merchant. Merchants sell what their settlement makes or imports and buy"
    " what it needs. 'What do you have in stock?' tells you their shelves."], keys=["buy", "sell", "trade", "shop", "merchant"],
     see=["currency"])
page("routes", "economy", "Trade between settlements", "minecraft:chest", [
    "Settlements trade with each other along the caverns: caravans of traders, guards and pack animals carry goods between them, and"
    " they import what they lack. Disruptions (blocked routes, attacks) are real and change prices."], keys=["caravan", "route", "import", "export"],
     see=["currency"])
page("contracts", "economy", "Contracts", "minecraft:paper", [
    "Ask a leader or innkeeper 'What's on the noticeboard?'. Contracts ask for supplies, building materials or an escort; the reward is"
    " held in escrow and paid when you hand in. Accept one, gather the goods, then talk to them again to hand them in."],
     keys=["contract", "job", "quest", "noticeboard", "delivery"], see=["making_money"])
page("property", "economy", "Property", "minecraft:oak_door", [
    "Leaders sell or rent vacant buildings ('Buy' or 'Rent for a week'). Containers and blocks inside your property are protected from"
    " other players. /property shows what you own."], keys=["house", "buy house", "rent", "property", "home"], see=["businesses"])
page("businesses", "economy", "Running a business", "minecraft:barrel", [
    "Inside property you own, ask a leader to 'Register my property as a shop'. Stock it with /business stock; townsfolk buy from it with"
    " their own money. /business status shows sales. You can hire someone to keep the shop ('Would you keep my shop?')."],
     keys=["shop", "business", "hire", "employee", "store"], see=["property"])
page("development", "economy", "How settlements grow", "minecraft:bricks", [
    "Settlements decide what to build (houses, workshops, farms, inns...) and actually build them block by block when the materials are"
    " there. Prosperous towns attract newcomers; hard times make people leave."], keys=["construction", "build", "growth"], see=["settlements"])

# ------------------------------------------------------------------------------------------------ Farming
page("farming_basics", "farming", "Planting and growing", "underdeep:glowgrain_seeds", [
    "Plant crops from their seeds or the produce itself (duskroots, sunbeans, mire rice, frost turnips...); bushes from their fruit;"
    " fungi on fungal soil; herbs from cuttings.",
    "Each plant has growing conditions shown in its tooltip: light, climate, water or heat. Hoed farmland near water helps crops; rich"
    " soil and planter boxes make them grow half again as fast and never dry out.",
    "Crops only grow where someone is nearby (chunks are never kept loaded)."], keys=["plant", "seed", "grow", "crop", "farm"],
     items=["underdeep:rich_soil", "underdeep:planter_box"], see=["soils", "compost", "harvesting"])
page("soils", "farming", "Soils", "underdeep:rich_soil", [
    "Different plants need different ground: plant soil, fungal soil, wet soil, volcanic or frozen ground, mineral ground. Rich soil"
    " (four dirt and compost) and planter boxes count as all of them.",
    "Sporeglow soil (dirt, a puffcap and compost) grows fungi."], keys=["soil", "dirt", "ground"], items=["underdeep:rich_soil", "underdeep:sporeglow_soil"],
     see=["fungi_farming"])
page("compost", "farming", "Compost and fertiliser", "underdeep:compost", [
    "A compost bin takes the same scraps as a composter and gives compost. Compost on a crop advances it one stage (bone meal jumps"
    " several); it is also used for rich soil and herb cuttings."], keys=["compost", "fertilizer", "bone meal"], items=["underdeep:compost_bin"],
     see=["soils"])
page("fungi_farming", "farming", "Growing mushrooms", "underdeep:puffcap", [
    "Edible fungi (puffcaps, inkcaps, rimecaps, truffles) grow on fungal soil in shade. Make sporeglow soil, plant a mushroom on it and"
    " keep it dim."], keys=["mushroom", "fungi", "fungus", "spore"], see=["soils"])
page("aquatic_farming", "farming", "Aquatic farming", "underdeep:sea_grapes", [
    "Sea grapes and siltwort grow underwater on lake and sea beds. Plant them on a submerged bed and pick them when ripe."],
     keys=["water plants", "aquatic", "underwater farm", "sea grapes"], see=["farming_basics"])
page("harvesting", "farming", "Harvesting", "underdeep:steel_sickle", [
    "Right-click a mature crop with an empty hand to harvest it and replant it in one go. Right-click a ripe bush or herb to pick it;"
    " it grows back.",
    "A sickle harvests and replants a 3x3 patch at once."], keys=["harvest", "pick", "replant", "sickle"], see=["farming_basics"])
page("fishing", "farming", "Fishing and fish traps", "minecraft:fishing_rod", [
    "Each region's waters hold their own fish: glimmerfin in crystal and starlit waters, reef darters in the Sunken Gardens, mire eels in"
    " the wetlands, salmon and cod in the cold north. Hold fishing bait in your other hand for better regional catches.",
    "A fish trap placed in water with bait inside catches something every few minutes, using one bait per catch.",
    "Heavily fished water gives less for a few days, then recovers."], keys=["fish", "fishing", "bait", "trap", "rod"],
     items=["underdeep:fishing_bait", "underdeep:fish_trap", "underdeep:glimmerfin"], see=["finding_food"])
page("herb_cuttings", "farming", "Herb cuttings", "underdeep:mendleaf_sprig", [
    "At a herbalist's table, three sprigs (or leaves, roots, petals) and compost make a new herb plant. Herbs never spread by themselves."],
     keys=["herb", "cutting", "propagate", "medicinal plant"], items=["underdeep:herbalist_table"], see=["medicine_basics"])

# ------------------------------------------------------------------------------------------------ Cooking
page("cook_stations", "cooking", "Cooking stations", "underdeep:cooking_pot", [
    "Campfire, furnace and smoker: simple roasts. Cooking pot and kettle: need heat below (a lit campfire, fire, lava, magma or a lit"
    " furnace). Clay oven: burns fuel. Preparation table: no heat. Fermentation crock: slow, no heat. Drying rack: hang items to dry.",
    "Put ingredients in any order. A matching recipe shows its name and progress; anything unrelated in the slots blocks all recipes."
    " Bowls and bottles go in the container slot. Nothing is used up until the dish is done.",
    "The list on the right shows recipes you know: click one to see which ingredients you have (+) or lack (-)."],
     keys=["cook", "pot", "oven", "kettle", "stove", "kitchen"], items=["underdeep:cooking_pot", "underdeep:clay_oven", "underdeep:kettle",
                                                                        "underdeep:preparation_table", "underdeep:fermentation_crock",
                                                                        "underdeep:drying_rack"], see=["recipes_known", "batch"])
page("recipes_known", "cooking", "Learning recipes", "minecraft:book", [
    "You know the basic recipes from the start. Learn others by cooking them (experimenting works), by eating the dish, or by asking a"
    " settlement's cook, baker, brewer or innkeeper to teach you; each people teaches its own regional dishes first."],
     keys=["recipe", "learn", "discover", "regional"], see=["regional"])
page("regional", "cooking", "Regional cuisine", "underdeep:fungal_hotpot", [
    "Every people has its own dishes: Sporefolk hotpots and ferments, Tide-dweller soups and rolls, Forgekin goulash, Stoneborn marrow"
    " broth, Amberfolk tarts and fizz, Rootfolk forest stew, Glimmerkin broth, Astral porridge and nectar. They make good gifts."],
     keys=["regional", "cuisine", "culture", "dish"], see=["recipes_known"])
page("variety", "cooking", "Food groups and Well Fed", "underdeep:fruit_salad", [
    "Foods belong to groups: grain, vegetable, fruit, fungi, meat, fish, sweet (shown in tooltips). Eat three groups within a day and a"
    " half to be Well Fed (you tire 12% more slowly), five for Well Fed II (20%, and hazards build more slowly). A plain diet is never"
    " punished."], keys=["nutrition", "variety", "well fed", "diet"], see=["meal_effects"])
page("meal_effects", "cooking", "Meal effects", "underdeep:ember_chili", [
    "Many dishes and drinks give an effect: warmth or cooling against the climate, spore ward, water breathing, haste, night vision,"
    " regeneration or rest. Effects never add up: a longer one replaces a shorter, warming and cooling cancel, and food effects last at"
    " most ten minutes."], keys=["buff", "effect", "warm", "cool"], see=["hazards"])
page("drinks", "cooking", "Drinks", "underdeep:mendleaf_tea", [
    "Teas and infusions are brewed in a kettle; juices pressed at the preparation table; fizzes, shrubs, kvass and kombucha fermented in"
    " a crock. They come in bottles and give the bottle back. There is no thirst to manage."], keys=["drink", "tea", "juice", "bottle"],
     see=["cook_stations"])
page("preserving", "cooking", "Preserved food and rations", "underdeep:travel_ration", [
    "Drying rack: jerky, dried fish, fruit and mushrooms, dried kelp, leather. Crock: pickles, cheese, vinegar. Oven: hardtack. Rations"
    " combine them into compact, very filling travel food. Food does not spoil."], keys=["preserve", "ration", "jerky", "travel food", "spoil"],
     see=["cook_stations"])
page("batch", "cooking", "Cooking for a group", "underdeep:roast_platter", [
    "Load a station with several portions' worth: it makes up to four batches at once (eight at the preparation and herbal tables) in"
    " the time of one. Hoppers can feed stations from above and take dishes out below."], keys=["batch", "group", "many", "automation", "hopper"],
     see=["cook_stations"])

# ------------------------------------------------------------------------------------------------ Medicine
page("medicine_basics", "medicine", "Medicine", "underdeep:bandage", [
    "Medicine is made at the herbalist's table from herbs, fungi and creature parts. Each has a cooldown so strong remedies cannot be"
    " chained. Salves, bandages and kits can be used on someone else (right-click them): another player, a tamed animal or a person."],
     keys=["medicine", "heal", "potion", "remedy", "cure"], items=["underdeep:herbalist_table", "underdeep:bandage", "underdeep:field_medkit"],
     see=["injuries", "protection"])
page("injuries", "medicine", "Injuries", "underdeep:bandage", [
    "A single heavy blow (7 or more damage) leaves you Wounded: a little slower until it heals (five minutes) or you bandage it."],
     keys=["wound", "injury", "bleeding"], see=["medicine_basics"])
page("protection", "medicine", "Protection against hazards", "underdeep:warming_salve", [
    "Salves and tinctures give protective effects: warmed, cooled, spore ward, clear lungs, grounded, water breathing. Equipment gives"
    " lasting protection: embersteel (heat), rimesteel and furlined (cold), galvanic (static), shellplate (spores and fumes), nacre"
    " (pressure), plus linings, inlays and accessories. Protection adds up to at most 90%."], keys=["protection", "resist", "salve", "insulation"],
     see=["hazards", "armor"])
page("healers", "medicine", "Healers", "minecraft:glistering_melon_slice", [
    "Settlement healers treat you for two emeralds using their own medicine; bring them herbs if they run out."], keys=["healer", "doctor"],
     see=["services"])

# ------------------------------------------------------------------------------------------------ Homes
page("camping", "homes", "Camping", "underdeep:bedroll", [
    "A bedroll is a one-block bed for expeditions. It never moves your respawn point unless you sneak and use it.",
    "A camp awning gives shade and shelter. Put a cooking pot on a campfire for a field kitchen. A supply crate keeps its contents when"
    " broken, so you can carry a whole cache."], keys=["camp", "bedroll", "tent", "expedition", "sleep"], items=["underdeep:bedroll", "underdeep:camp_awning",
                                                                                                                  "underdeep:supply_crate"], see=["comfort"])
page("furniture", "homes", "Furniture and storage", "underdeep:wooden_chair", [
    "Chairs and benches can be sat on (right-click with an empty hand). Storage: cabinet (27), pantry (27, food only), medicine cabinet"
    " (18, medicine and herbs), supply crate (18, portable), kitchen counter (9). They work with hoppers."],
     keys=["chair", "sit", "storage", "chest", "pantry"], see=["comfort"])
page("comfort", "homes", "Comfort and rest", "underdeep:wooden_table", [
    "Where you sleep matters: different kinds of furniture nearby, a kitchen station, a real bed, shelter, light, warmth and company each"
    " add comfort (up to 10). Waking gives Rested for longer the more comfortable you were: you tire more slowly and move a little"
    " faster. Sitting a while also rests you."], keys=["comfort", "rested", "home", "cozy"], see=["furniture"])
page("shared_bases", "homes", "Shared bases", "minecraft:oak_door", [
    "Bought or rented property protects containers and blocks from other players; friends can still visit. Settle down together in a"
    " house or build your own base anywhere."], keys=["base", "shared", "friends", "permissions"], see=["property"])

# ------------------------------------------------------------------------------------------------ Equipment
page("equipment_overview", "equipment", "Equipment progression", "underdeep:steel_sword", [
    "Start with wood, stone and copper; iron and steel are the backbone; regional metals add special strengths; umbralite is the"
    " strongest general metal. There is no single best set: choose for what you do and where you go.",
    "Basic gear is made at the crafting table. Advanced gear is forged at the forge from alloys and regional metals."],
     keys=["equipment", "gear", "progression", "best"], see=["material_families", "weapons", "armor", "tools"])
page("material_families", "equipment", "Material families", "underdeep:embersteel_ingot", [
    "Copper: cheap and early; conducts static. Steel: tough all-rounder. Embersteel: sets foes alight, guards against heat. Rimesteel:"
    " slows foes, guards against cold. Galvanic: very fast, grounds static, shocks wet foes. Starsilver: light, best for enchanting,"
    " smites the undead. Umbralite: heaviest and strongest, pierces armour. Nacre: diving gear and water weapons. Fossil: cheap heavy"
    " blunt weapons, knockback-resistant armour. Malachite: very durable tools. Furlined, scale, shellplate and silkweave: clothing for"
    " cold, swimming, spores and agility. Glimmer: light and marking."], keys=["material", "metal", "alloy", "steel", "umbralite", "starsilver"],
     see=["processing", "weapons", "armor"])
page("weapons", "equipment", "Weapon families", "underdeep:steel_greatsword", [
    "Swords: reliable, sweeping. Daggers: fast and short. Greatswords: slow, heavy, a little more reach. Battleaxes: heavy chops that"
    " disable shields (and chop wood). Spears: a whole block more reach. Warhammers: very slow, crushing, strong knockback. Maces:"
    " steady blunt blows.",
    "Material specials add to this: see Material families."], keys=["weapon", "sword", "dagger", "spear", "hammer", "axe", "mace"],
     see=["ranged", "upgrades"])
page("ranged", "equipment", "Bows and arrows", "underdeep:horn_recurve_bow", [
    "Lumenwood shortbow: quick to draw, weaker. Spirebark longbow: slow, powerful. Horn recurve bow: balanced and very durable.",
    "Heavy arrows hit harder; frost arrows slow; venom arrows poison; ember arrows set alight."], keys=["bow", "arrow", "ranged", "shoot"],
     see=["weapons"])
page("armor", "equipment", "Armour", "underdeep:steel_chestplate", [
    "Mix and match pieces freely: each piece gives its own protection. Steel and umbralite protect most; embersteel, rimesteel, galvanic,"
    " shellplate and furlined also shield you from hazards; starsilver and silkweave make you quicker; nacre is diving gear (long"
    " breath, swimming, underwater mining); scale swims well; fossil resists knockback."], keys=["armor", "armour", "helmet", "chestplate"],
     see=["protection", "diving"])
page("tools", "equipment", "Tools", "underdeep:steel_pickaxe", [
    "Copper tools mine like stone and are cheap. Steel lasts. Malachite tools last very long. Starsilver and galvanic tools are very fast."
    " The embersteel pickaxe smelts ores as it mines them; the nacre pickaxe mines at full speed underwater.",
    "Specialised: hammers mine 3x3 in stone, excavators dig 3x3 in soft ground (sneak to mine a single block), the lumber axe fells a"
    " small tree at once, the sickle harvests 3x3, the prospector's pick senses nearby ores."],
     keys=["tool", "pickaxe", "hammer", "3x3", "vein", "prospect"], see=["mining_tiers"])
page("workstations", "equipment", "Equipment workstations", "underdeep:forge", [
    "Alloy furnace and forge burn fuel. The forge makes advanced weapons, armour, tools and upgrade parts; the gem cutter jewellery and"
    " inlays; the leatherworker's bench clothing parts, charms, packs and linings; the artisan's bench applies upgrades, repairs and"
    " salvages. Workstations with recipes list them on the right of their screen."], keys=["forge", "workstation", "bench", "anvil"],
     items=["underdeep:forge", "underdeep:artisan_bench"], see=["processing", "upgrades"])
page("upgrades", "equipment", "Upgrades", "underdeep:honed_edge_kit", [
    "Each weapon, tool, armour piece or bow takes two upgrades, one of each kind (edge, grip, coating, gem inlay, lining, plating...)."
    " Put the item and a kit in the artisan's bench: the right slot previews the result and the bench explains if it does not fit.",
    "Upgrades stay with the item through repair, enchanting, trading and death."], keys=["upgrade", "kit", "inlay", "coating", "lining", "improve"],
     see=["repair"])
page("repair", "equipment", "Repair and salvage", "underdeep:repair_kit", [
    "At the artisan's bench: the item plus a repair kit or a unit of its own material restores a quarter of its durability. A repair kit"
    " can also be used directly (kit in hand, item in the other hand). The vanilla anvil still works. Smiths repair for emeralds.",
    "Salvage (button, second slot empty) breaks equipment down into at most half of its main material, less if worn; upgrades and"
    " enchantments are lost."], keys=["repair", "fix", "salvage", "recycle", "durability"], items=["underdeep:repair_kit", "underdeep:artisan_bench"],
     see=["upgrades"])
page("enchanting", "equipment", "Enchanting", "minecraft:enchanting_table", [
    "Underdeep equipment takes the usual enchantments at the enchanting table and anvil (starsilver enchants best). Enchantments and"
    " upgrades work together."], keys=["enchant", "enchanting", "anvil", "book"], see=["upgrades"])
page("accessories", "equipment", "Accessories", "underdeep:opal_ring", [
    "Press K to open your accessories: two rings, an amulet and a charm. Using an accessory wears it in a free slot. They give modest"
    " effects (protection from a hazard, longer breath, faster mining, climbing walls, seeing in the dark, better prices...).",
    "Accessories stay with you when you die."], keys=["accessory", "ring", "amulet", "charm", "trinket"], see=["protection"])
page("diving", "equipment", "Diving", "underdeep:nacre_helmet", [
    "Nacre diving gear: the helmet lengthens your breath and lets you mine underwater, leggings and fins make you swim faster. Pearl"
    " rings, nacre amulets, sealed seams and breathing draughts add more. Nacre weapons hit harder in water."],
     keys=["dive", "diving", "underwater", "breath", "swim"], see=["armor"])
page("climbing", "equipment", "Climbing and descending", "underdeep:climbing_claws", [
    "Climbing claws (charm) let you climb walls by pressing into them. A rope coil used on the side of a ledge hangs a rope ladder down"
    " (up to 8 rungs per coil). The grappling hook pulls you toward a block up to 16 away and slows your fall. The cave glider (chest"
    " slot) glides like an elytra. Feather charms and springstep boots soften landings."],
     keys=["climb", "rope", "ladder", "grapple", "glide", "fall"], items=["underdeep:rope_coil", "underdeep:grappling_hook", "underdeep:cave_glider"],
     see=["diving"])
page("navigation", "equipment", "Navigation and light", "underdeep:delvers_compass", [
    "Waymarkers record a place for you and give light. The delver's compass shows the way to your waymarkers, your bed or the world"
    " spawn (sneak-use to change target). The depth gauge reads depth, region and your nearest waymarker; the wayfinder's charm shows"
    " it as you walk. Glimmer lanterns are bright; the miner's lamp and lamp mounts let you see in darkness."],
     keys=["compass", "map", "waypoint", "light", "lamp", "lantern", "lost", "navigation"], items=["underdeep:waymarker", "underdeep:depth_gauge",
                                                                                                 "underdeep:glimmer_lantern"], see=["climbing"])
page("packs", "equipment", "Packs", "underdeep:backpack", [
    "A backpack holds 18 stacks, a herb pouch 9 stacks of plants and herbs, an ore satchel 9 stacks of ores and gems. Use one to open it."
    " Packs cannot hold other packs or shulker boxes."], keys=["backpack", "bag", "pouch", "storage", "inventory"], see=["navigation"])


# ------------------------------------------------------------------------------------------------ Exploration (Stage 9)
page("exploring", "explore", "Old places", "minecraft:recovery_compass", [
    "The Underdeep is old. People lived and worked down here long before today's settlements, and they left a great deal behind:"
    " roadside remains, abandoned homes and workshops, great landmarks, and sealed complexes that go deep into the rock.",
    "Most places are small and quick to look around. Some are big enough for a whole evening, and a few are rare enough that you may"
    " never see them. You discover a place just by walking into it: it goes into your journal (this handbook, 'Your Journal').",
    "Nothing marks them on a map for you. Look for unusual stone, light where there should be none, carved signs, old roads of copper"
    " lamp posts, and anything that looks built rather than grown."], keys=["ruin", "ruins", "dungeon", "landmark", "structure", "explore", "discover"],
     see=["expedition_prep", "old_structures", "journal_help"])
page("expedition_prep", "explore", "Preparing an expedition", "minecraft:bundle", [
    "Before you head for something big: food for a few days, plenty of light, bandages and a remedy or two, and clothing for the region"
    " (warm in the cold, cooling or fire-resistant near heat, spore protection in fungal places, diving gear where it is flooded).",
    "Rope and a grappling hook help with heights; a bedroll and a few torches make a camp; repair kits keep your tools going."
    " Bring friends: some mechanisms are easier with two, and expedition coffers give everyone their own share."],
     keys=["prepare", "supplies", "expedition", "pack", "gear", "travel"], items=["underdeep:bandage", "underdeep:rope_coil", "underdeep:bedroll"],
     see=["exploring", "hazards", "warding"])
page("old_structures", "explore", "Exploring old structures", "minecraft:cracked_stone_bricks", [
    "Old buildings are yours to use: mine them, move in, build on them. Only two things resist you: ancient seals (they open for their"
    " mechanism, never for tools) and the mechanism parts of a seal that has not been opened yet.",
    "Large complexes go down by stair wells. Expect several rooms, side passages and a heart at the end. Every complex has a way back"
    " out that never closes; if you fall somewhere, look for a ladder or a passage up. You can leave and come back: opened seals stay"
    " open, and the world remembers what you took."], keys=["seal", "door", "dungeon", "complex", "stair", "locked"], see=["mechanisms", "traps"])
page("mechanisms", "explore", "Ancient mechanisms", "underdeep:rune_dial", [
    "Seals open when their mechanism is worked the right way. The answer is always nearby: carved signs, marks on the parts themselves,"
    " shapes, sizes, sounds, the layout of the room, or an old record. Nothing requires guessing a number.",
    "The old builders used six signs (spiral, eye, wave, flame, star, key); each looks different, so colour never matters."
    " A wrong step usually just resets with a low note. Mechanisms never eat your things: offerings that are wrong are refused."
    " Some mechanisms are easier for two people, but every one has a way to do it alone."],
     keys=["puzzle", "lever", "dial", "plate", "brazier", "mechanism", "seal", "rune", "chime"], see=["old_structures"])
page("traps", "explore", "Traps", "underdeep:trap_plate", [
    "Old places defended themselves. Traps always warn you: ash at a dart slit, dust under a loose ceiling, smoke or a hiss before a"
    " vent breathes, cracks before a floor gives way. Worn floor tiles are worth stepping around.",
    "Sneak-use a worn tile with a stick or a pickaxe to jam it for good. A block placed on a vent stops it. Crumbling floors drop you"
    " to the level below; there is always a way back up."], keys=["trap", "dart", "vent", "floor", "danger", "disarm"], see=["hazards"])
page("treasure", "explore", "Treasure and fair shares", "underdeep:expedition_cache", [
    "Ordinary chests and pots hold what they hold: whoever opens them first takes it. Expedition coffers are different: each player may"
    " open each coffer once and gets a share of their own, so a group is rewarded together.",
    "A few rare places keep something unique on a pedestal: it is there once, for the whole world. Vault keys hang on key pedestals"
    " (one per person) and open the lock of that vault only."], keys=["loot", "treasure", "chest", "coffer", "reward", "key"], see=["artifacts"])
page("artifacts", "explore", "Artifacts and relics", "underdeep:encrusted_relic", [
    "Encrusted relics are something under a crust: a scholar, astronomer or cartographer will clean and identify one for 3 emeralds."
    " Artifacts are real equipment: rings, amulets and charms (K), weapons, and a few tools with odd powers. They do not burn in lava.",
    "Relics (busts, urns, masks...) can be placed as decoration, kept in a museum, or sold. Ancient coins and star shards are valuables"
    " that merchants buy. Everything you find goes into your collection in the journal."], keys=["artifact", "relic", "identify", "coin", "museum", "collection"],
     see=["treasure", "accessories"])
page("records", "explore", "Old writings", "underdeep:lore_fragment", [
    "Inscriptions are carved into old places; loose writings turn up in chests and on the ground. Use one to read it: it is copied into"
    " your journal. The old peoples did not agree about what happened down here, and neither do their records."],
     keys=["lore", "history", "book", "inscription", "record", "writing"], see=["journal_help"])
page("journal_help", "explore", "Your exploration journal", "underdeep:lore_fragment", [
    "The 'Your Journal' category lists the places you have discovered (with where they are, so you can find them again), leads you"
    " heard about, the writings you read and the artifacts you collected. It is yours alone; friends keep their own.",
    "Getting back: the place pages give coordinates and the distance from where you stand. A homing stone remembers one place for you;"
    " waymarkers and the delver's compass work as before."], keys=["journal", "notes", "return", "coordinates", "find again", "places"],
     see=["exploring", "rumours"])
page("rumours", "explore", "Rumours, charts and reports", "underdeep:surveyors_chart", [
    "Ask inhabitants 'Heard of any old places around here?': they may point you somewhere, roughly, in their own words (once a day each)."
    " Cartographers and navigators draw surveyor's charts with exact directions (6 emeralds); a chart can be passed to a friend.",
    "Scholars and astronomers pay for news: tell them about places you found and writings you read, and their settlement pays once for"
    " each."], keys=["rumour", "rumor", "map", "chart", "cartographer", "scholar", "report"], see=["journal_help"])
page("encounters", "explore", "On the road", "minecraft:glow_ink_sac", [
    "Travelling, you will sometimes see things happen: a rockfall, a spring of steam, a drifting boat, an old camp, lights moving in the"
    " dark. Most are harmless and some are lucky; watch for the warning signs of the others."], keys=["event", "encounter", "travel", "road"],
     see=["exploring"])
page("warding", "explore", "Safe camps", "underdeep:warding_bell", [
    "A warding bell, found in old places, keeps hostile creatures from appearing within 16 blocks of it: perfect for an expedition camp."
    " Torches and walls help too. Companions follow you through ordinary passages; if one gets stuck, it catches up when it can."],
     keys=["camp", "safe", "bell", "spawn", "companion"], see=["expedition_prep"])

# ------------------------------------------------------------------------------------------------ Controls
page("controls", "controls", "Controls", "minecraft:comparator", [
    "H: open this handbook. K: open accessories. L: advancements (vanilla). All keys can be changed in Options > Controls > Underdeep.",
    "Sneak + right-click: quick trade with a merchant, open a pack animal's pack, set a bedroll as your respawn point. Right-click an"
    " empty-handed crop: harvest and replant."], keys=["keys", "keybind", "controls", "hotkey"], see=["settings"])
page("settings", "controls", "Settings", "minecraft:redstone", [
    "Server operators can tune the Underdeep in config/underdeep.json: wildlife, settlements, economy pace, survival rules (hazards,"
    " injuries, weariness, station speed, fishing), equipment limits and handbook behaviour. Most changes apply after a restart; none"
    " need a new world."], keys=["config", "settings", "options", "difficulty"], see=["controls"])
page("multiplayer", "controls", "Playing together", "minecraft:player_head", [
    "Everything is shared world state except your own discoveries, journal, known recipes, accessories, advancements and handbook."
    " Settlements and creatures react to all of you. Property protects your things from other players."],
     keys=["multiplayer", "server", "friends", "together", "coop"], see=["shared_bases"])


def places():
    import sys
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "assets"))
    import stage9 as s9
    kinds = {"ruin": "Abandoned place", "landmark": "Landmark", "dungeon": "Sealed complex", "secret": "Hidden place"}
    icons = {"ruin": "minecraft:mossy_cobblestone", "landmark": "minecraft:recovery_compass", "dungeon": "minecraft:iron_door",
             "secret": "minecraft:tripwire_hook"}
    for f in s9.F:
        page("site_" + f["id"], "journal", f["name"], icons[f["category"]], [f["desc"], kinds[f["category"]] + ". Your journal lists where you found one."],
             keys=["place", kinds[f["category"]].lower()], req="site:" + f["id"], see=["journal_help"])
    for sid, name, desc in s9.SECRETS:
        page("secret_" + sid, "journal", name, "minecraft:tripwire_hook", [desc], keys=["secret", "hidden"], req="secret:" + sid, see=["journal_help"])


def main():
    places()
    ids = [p["id"] for p in PAGES]
    assert len(ids) == len(set(ids)), "duplicate page ids"
    for p in PAGES:
        for s in p["see"]:
            assert s in ids, (p["id"], s)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump({"categories": [{"id": c, "title": t, "icon": i} for c, t, i in CATS], "pages": PAGES}, f, indent=1)
    print(len(CATS), "categories,", len(PAGES), "authored pages")


if __name__ == "__main__":
    main()
