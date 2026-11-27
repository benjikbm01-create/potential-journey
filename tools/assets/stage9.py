"""
Stage 9 exploration content (SPOILER-SENSITIVE: private design notes in docs/private/EXPLORATION.md).

Writes data/underdeep/exploration/content.json (site families with placement metadata, secret rooms, historical
records, artifacts, rumours, encounters), loot tables under data/underdeep/loot_table/exploration/, the artifact
accessories and weapons appended to data/underdeep/equipment/content.json, block and item assets (stage9_art.py),
names, tooltips, tags and the Handbook visibility list. Run through tools/assets/stage2_assets.py
(PYTHONHASHSEED=0); deterministic.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import stage9_art as A  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
DATA = os.path.join(ROOT, "data")
ASSETS = os.path.join(ROOT, "assets", "underdeep")
NS = "underdeep"
LANG = {}

ALL = []  # any region


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1)
        f.write("\n")


# ============================================================================================ site families
# (id, category, scale, public name, discovery text, culture, regions, layers, radius, height, below, weight, aquatic,
#  minSpawn, embedded secrets, audio context)
UP, DEEP, BOTH = ["upper"], ["deep"], ["upper", "deep"]
F = []


def fam(fid, cat, scale, name, desc, culture, regions, layers, radius, height, below, weight, aquatic=False, min_spawn=0, secrets=(), audio=None):
    F.append({"id": fid, "category": cat, "scale": scale, "name": name, "desc": desc, "culture": culture, "regions": list(regions),
              "layers": layers, "radius": radius, "height": height, "below": below, "weight": weight, "aquatic": aquatic,
              "minSpawn": min_spawn, "secrets": list(secrets), "audio": audio or {"ruin": "ruin", "landmark": "landmark", "dungeon": "dungeon",
                                                                                   "secret": "secret"}[cat]})


RW, FE, FD, AH, CD, AB, EF, GG = "rootwild", "fungal_expanse", "flooded_depths", "ashen_hollows", "crystal_deep", "the_abyss", "everfrost", "golden_grove"
HP, EW, PF, CB, SG, SS, SL = "hollow_peaks", "emerald_wetlands", "petrified_forest", "crimson_basin", "sunken_gardens", "stormstone_caverns", "starlight_caverns"

# ---- ruins: minor
fam("wayshrine", "ruin", "minor", "Wayside Shrine", "A small shrine by an old path. Someone still remembered it, once.", "wright", ALL, UP, 4, 7, 0, 9)
fam("lantern_post", "ruin", "minor", "Old Lantern Road", "Copper lantern posts mark a road nobody walks any more.", "lantern", ALL, UP, 7, 7, 0, 8)
fam("collapsed_hut", "ruin", "minor", "Collapsed Dwelling", "A home, long abandoned. The hearth is cold.", "gardener", [RW, GG, EW, FE, HP], UP, 5, 6, 0, 8)
fam("miners_camp", "ruin", "minor", "Abandoned Mining Camp", "Rails, a cold fire, and work left half done.", "lantern", [HP, PF, SS, CD, AH], BOTH, 6, 5, 0, 8)
fam("cairn_circle", "ruin", "minor", "Cairn Circle", "Stacked stones in a ring around a carved sign.", "wright", ALL, UP, 5, 5, 0, 6)
fam("broken_bridge", "ruin", "minor", "Broken Bridge", "Half a bridge, reaching for a far side that fell long ago.", "wright", ALL, UP, 8, 6, 0, 5)
fam("fallen_statue", "ruin", "minor", "Fallen Colossus Head", "A stone face as tall as a house, lying where it fell.", "wright", ALL, BOTH, 6, 8, 0, 5)
fam("well_head", "ruin", "minor", "Old Well", "A well shaft with a rotten windlass. Water glints far below.", "gardener", ALL, UP, 8, 5, 18, 5,
    secrets=["well_bottom"])
fam("watch_niche", "ruin", "minor", "Watch Post", "A sheltered post where someone once kept watch.", "wright", ALL, BOTH, 4, 6, 0, 6)
fam("grave_garden", "ruin", "minor", "Grave Garden", "Quiet stones among flowers. Someone was loved here.", "gardener", [RW, GG, EW, SG, FE], UP, 5, 4, 0, 6)
fam("kiln_mound", "ruin", "minor", "Potters' Kiln", "A brick kiln and the pots it never finished firing.", "ember", [AH, CB, HP, PF, EW], UP, 5, 6, 0, 6)
fam("waystone", "ruin", "minor", "Waystone", "A carved marker stone. Its carvings count distances in lamps.", "lantern", ALL, BOTH, 3, 8, 0, 7)
# ---- ruins: regional
fam("cottage_row", "ruin", "regional", "Abandoned Hamlet", "A row of empty houses. They left in a hurry, or not at all.", "gardener", [RW, GG, EW, FE, HP, PF], UP, 12, 6, 6, 4,
    secrets=["cellar_beneath"])
fam("lantern_waystation", "ruin", "regional", "League Waystation", "A rest-house of the old lantern roads, with a pump outside.", "lantern", ALL, UP, 10, 7, 0, 4)
fam("forge_ruin", "ruin", "regional", "Cold Forge", "A smithy whose great hearth has been dark for an age.", "ember", [AH, CB, SS, HP], BOTH, 8, 7, 5, 4,
    secrets=["forge_strongbox"])
fam("tide_shrine", "ruin", "regional", "Tide Shrine", "Sea-green pillars around an altar that still glows.", "tide", [FD, SG, EW], UP, 8, 8, 6, 4, secrets=["pearl_grotto"])
fam("seed_terrace", "ruin", "regional", "Seed Terraces", "Ringed garden terraces, still growing on their own.", "gardener", [RW, GG, EW, SG], UP, 10, 4, 0, 4)
fam("star_dial", "ruin", "regional", "Star Dial", "A ring of carved stones around a pointer that aims at the ceiling.", "star", [SL, CD], BOTH, 8, 9, 0, 4)
fam("fungal_hall", "ruin", "regional", "Overgrown Hall", "A great hall that a mushroom has slowly claimed.", "gardener", [FE, RW], UP, 9, 13, 0, 4)
fam("ice_hermitage", "ruin", "regional", "Frozen Hermitage", "A hermit's house, half swallowed by ice.", "lantern", [EF], UP, 7, 7, 0, 5)
fam("stilt_village", "ruin", "regional", "Stilt Houses", "Platforms on tall posts above the wet ground. One has fallen.", "tide", [EW, FD, SG], UP, 11, 6, 2, 4)
fam("storm_tower", "ruin", "regional", "Lightning Tower", "A tower crowned with copper, built to call the sparks down.", "lantern", [SS], BOTH, 6, 20, 0, 5)
fam("quarry", "ruin", "regional", "Old Quarry", "Terraced cuts in the rock and a crane that still stands.", "wright", [HP, PF, SS, CD], UP, 11, 9, 5, 4)
fam("silent_ruin", "ruin", "regional", "Silent Ruins", "Broken columns furred with a strange growth. The air feels listened to.", "silent", [AB], DEEP, 9, 8, 0, 6)
# ---- ruins: significant
fam("vault_gatehouse", "ruin", "significant", "Vault Gatehouse", "Twin towers guard a gate into nothing. What did it keep out, or in?", "wright", ALL, BOTH, 20, 15, 0, 1.6,
    secrets=["gatehouse_armory"], audio="ruin_major")
fam("aqueduct", "ruin", "significant", "Aqueduct", "Arches carry a channel across the cavern. Water still runs in places.", "wright", [RW, GG, FD, EW, SG, HP], UP, 20, 13, 0, 1.4,
    secrets=["aqueduct_channel"], audio="ruin_major")
fam("market_square", "ruin", "significant", "Lost Market", "Empty stalls around a dry fountain. It must have been loud, once.", "lantern", ALL, UP, 14, 7, 0, 1.6, audio="ruin_major")
fam("great_library", "ruin", "significant", "Great Library", "Shelves upon shelves. Most books are dust, not all.", "wright", ALL, BOTH, 16, 12, 0, 1.2,
    secrets=["library_stacks"], audio="ruin_major")
fam("amphitheatre", "ruin", "significant", "Amphitheatre", "Tiered stone seats facing an empty stage. Your footsteps carry.", "star", ALL, UP, 17, 9, 0, 1.2, audio="ruin_major")
fam("foundry_complex", "ruin", "significant", "Ruined Foundry", "Furnaces, rails and chimneys. This was a place of work.", "ember", [AH, CB, SS, HP, PF], BOTH, 15, 14, 0, 1.4,
    audio="ruin_major")
# ---- landmarks
fam("colossus", "landmark", "landmark", "The Seated Colossus", "A giant carved from the rock sits with its hands on its knees, watching.", "wright", ALL, BOTH, 11, 32, 0, 0.7,
    min_spawn=300, secrets=["colossus_heart"])
fam("great_stair", "landmark", "landmark", "The Great Stair", "A stair wide enough for a crowd climbs to a landing above the cavern.", "wright", ALL, BOTH, 20, 32, 0, 0.6,
    min_spawn=300, secrets=["stair_landing"])
fam("sky_bridge", "landmark", "landmark", "The High Bridge", "A slender arch leaps between two pillars, high above the floor.", "wright", ALL, BOTH, 18, 22, 0, 0.7, min_spawn=200)
fam("lantern_spire", "landmark", "landmark", "Lantern Spire", "A copper tower, its lamp still burning. You can see it from far away.", "lantern", ALL, BOTH, 6, 34, 0, 0.8, min_spawn=200)
fam("root_arch", "landmark", "landmark", "The Root Arch", "A root as thick as a house rises out of the ground and dives back in.", "gardener", [RW, GG], UP, 13, 14, 0, 1.0, min_spawn=200)
fam("crystal_cathedral", "landmark", "landmark", "Crystal Cathedral", "Spires of living crystal hum around a pale nave.", "star", [CD], BOTH, 14, 22, 0, 1.0, min_spawn=200)
fam("frozen_falls", "landmark", "landmark", "Frozen Falls", "A waterfall stopped mid-fall, blue and silent.", "tide", [EF], UP, 9, 20, 0, 1.0, min_spawn=200, secrets=["ice_tomb"])
fam("ember_caldera", "landmark", "landmark", "Ember Caldera", "Black monoliths stand around a glowing pool.", "ember", [AH, CB], BOTH, 12, 18, 0, 1.0, min_spawn=200)
fam("sunken_dome", "landmark", "landmark", "Sunken Dome", "A great dome of sea-glass, half drowned.", "tide", [FD, SG], UP, 10, 10, 4, 1.0, aquatic=True, min_spawn=200)
fam("mushroom_ring", "landmark", "landmark", "Giants' Ring", "Giant mushrooms stand in a perfect circle around a glowing lawn.", "gardener", [FE], UP, 13, 16, 0, 1.0, min_spawn=200)
fam("petrified_titan", "landmark", "landmark", "The Stone Titan", "A tree turned to stone, older than any forest.", "gardener", [PF], UP, 10, 30, 0, 1.2, min_spawn=200,
    secrets=["titan_hollow"])
fam("star_orrery", "landmark", "landmark", "The Great Orrery", "Rings within rings, frozen mid-turn around a starstone heart.", "star", [SL], BOTH, 16, 28, 0, 1.0, min_spawn=200,
    secrets=["orrery_core"])
fam("storm_pylons", "landmark", "landmark", "Storm Pylons", "Three pylons strung with copper hum before every flash.", "lantern", [SS], BOTH, 12, 26, 0, 1.0, min_spawn=200)
fam("hanging_gardens", "landmark", "landmark", "Hanging Gardens", "Terraced gardens step upward, water spilling from each.", "gardener", [GG, SG, EW], UP, 13, 16, 0, 1.0, min_spawn=200)
fam("obelisk_field", "landmark", "landmark", "Field of Obelisks", "Leaning stones carved with the same six signs.", "wright", ALL, BOTH, 14, 16, 0, 0.6, min_spawn=200)
fam("silent_bell", "landmark", "landmark", "The Silent Bell", "A bell the size of a house hangs over an altar. It has not rung for a long time.", "silent", [AB], DEEP, 10, 28, 8,
    1.4, min_spawn=200, secrets=["bell_chamber"])
# ---- dungeons
fam("sealed_cellar", "dungeon", "small", "Sealed Cellar", "A ruined house with a cellar door that stuck. Something below is shut tight.", "wright", ALL, UP, 7, 6, 9, 2.0,
    min_spawn=220, audio="dungeon")
fam("smugglers_den", "dungeon", "small", "Smugglers' Den", "A hidden cave-mouth with boot-worn steps going down.", "lantern", ALL, BOTH, 12, 6, 9, 1.8, min_spawn=220)
fam("drowned_crypt", "dungeon", "small", "Drowned Crypt", "A crypt whose stairs go down into still water.", "tide", [FD, SG, EW], UP, 12, 6, 10, 2.0, min_spawn=220)
fam("kiln_vault", "dungeon", "small", "Kiln Vault", "Heat rises from a kiln's mouth that leads down, not in.", "ember", [AH, CB], BOTH, 14, 7, 10, 2.0, min_spawn=220)
fam("spore_warren", "dungeon", "small", "Spore Warren", "Tunnels under a mushroom, breathing spores.", "gardener", [FE, RW], UP, 12, 9, 9, 2.0, min_spawn=220)
fam("frost_cistern", "dungeon", "small", "Frost Cistern", "An ice-rimmed stair into a cistern that breathes cold.", "lantern", [EF], UP, 14, 5, 11, 2.0, min_spawn=220)
fam("echo_vault", "dungeon", "small", "Echo Vault", "A stair under a stone ring. Sounds come back changed.", "wright", [CD, SL, HP], BOTH, 13, 7, 10, 2.0, min_spawn=220)
fam("lantern_works", "dungeon", "medium", "Lantern Works", "A League workshop with a lift shaft into the dark.", "lantern", ALL, UP, 15, 8, 18, 1.2, min_spawn=260)
fam("warden_barracks", "dungeon", "medium", "Warden Barracks", "A walled barracks. The gong in the yard is still hung.", "wright", ALL, BOTH, 13, 8, 10, 1.1, min_spawn=260)
fam("tide_temple", "dungeon", "medium", "Tide Temple", "A pillared temple whose lower halls lie under water.", "tide", [FD, SG, EW], UP, 14, 9, 18, 1.2, min_spawn=260)
fam("seed_vault", "dungeon", "medium", "Seed Vault", "A domed hatch in a garden. Under it, a vault.", "gardener", [RW, GG, EW, FE, SG], UP, 15, 5, 11, 1.2, min_spawn=260)
fam("star_observatory", "dungeon", "medium", "Observatory", "A domed tower with a telescope aimed at the stone sky.", "star", [SL, CD, HP], BOTH, 8, 21, 8, 1.2, min_spawn=260)
fam("stormworks", "dungeon", "medium", "Stormworks", "A generator hall over a deep pit. Something here once made lightning.", "lantern", [SS], BOTH, 15, 8, 18, 1.4, min_spawn=260)
fam("ossuary", "dungeon", "medium", "Ossuary", "A chapel whose stair leads to the bones of the old dead.", "silent", ALL, BOTH, 15, 7, 10, 1.1, min_spawn=260)
fam("wright_vault", "dungeon", "large", "The Sealed Vault", "A gate court with statues and a stair into the deep. The great door below is shut.", "wright", ALL, BOTH, 20,
    12, 20, 0.45, min_spawn=500, audio="dungeon_major")
fam("sunken_archive", "dungeon", "large", "Drowned Archive", "A ring of stone above a flooded stair. Shelves sway below the water.", "tide", [FD, SG], UP, 16, 4, 12, 0.6,
    min_spawn=500, audio="dungeon_major")
fam("deep_foundry", "dungeon", "large", "Deep Foundry", "A foundry whose works go far below the floor, still warm.", "ember", [AH, CB], BOTH, 17, 20, 12, 0.6, min_spawn=500,
    audio="dungeon_major")
fam("singing_hall", "dungeon", "large", "Singing Hall", "A stair into crystal. The air hums in chords.", "star", [CD], BOTH, 16, 8, 12, 0.6, min_spawn=500, audio="dungeon_major")
fam("heartwood", "dungeon", "exceptional", "The Heartwood", "The greatest root of all goes down into the floor, and so does a stair.", "gardener", [RW], UP, 20, 20, 14, 0.25,
    min_spawn=1500, audio="dungeon_exceptional")
fam("silent_sanctum", "dungeon", "exceptional", "Silent Sanctum", "A court of black pillars at the edge of the deep. Nothing makes a sound here.", "silent", [AB], DEEP, 20, 13, 14, 0.25,
    min_spawn=900, audio="dungeon_exceptional")
# ---- standalone secrets
fam("waterfall_grotto", "secret", "minor", "Hidden Grotto", "A dry room behind falling water.", "gardener", [RW, GG, EW, FD, SG, HP], UP, 5, 8, 0, 2.0)
fam("hollow_pillar", "secret", "minor", "Hollow Pillar", "A stone column, hollow all the way up.", "wright", ALL, BOTH, 4, 17, 0, 2.0)
fam("buried_reliquary", "secret", "minor", "Buried Reliquary", "Under the cairn, a room of offerings.", "wright", ALL, UP, 6, 5, 5, 1.6)
fam("hermit_cell", "secret", "minor", "Hermit's Cell", "Someone lived inside the rock itself.", "lantern", ALL, BOTH, 5, 7, 0, 1.6)
fam("fungal_heart", "secret", "minor", "Fungal Heart", "Under the great mushroom, the roots make a room.", "gardener", [FE], UP, 5, 12, 7, 2.0)
fam("ember_shrine", "secret", "minor", "Ember Shrine", "A shrine behind a seam of fire.", "ember", [AH, CB], BOTH, 5, 7, 0, 2.0)
fam("star_window", "secret", "minor", "Star Window", "A room under a floor of stars.", "star", [SL], BOTH, 5, 2, 7, 2.0)
fam("drowned_bell", "secret", "minor", "Drowned Bell", "Something rings beneath still water.", "tide", [FD, SG, EW], UP, 4, 2, 7, 2.0)
fam("root_hollow", "secret", "minor", "Root Hollow", "A hollow inside a living root.", "gardener", [RW, GG], UP, 5, 11, 0, 2.0)
fam("crystal_geode", "secret", "minor", "Geode Heart", "A crystal egg the size of a room.", "star", [CD], BOTH, 7, 11, 2, 2.0)

SECRETS = [
    ("well_bottom", "The Well's Hidden Tunnel", "Someone dug sideways at the bottom of the well."),
    ("cellar_beneath", "The Hidden Cellar", "Under the floor, the family kept what mattered most."),
    ("forge_strongbox", "The Smith's Strongbox", "Bricked in beneath the anvil."),
    ("pearl_grotto", "The Pearl Grotto", "A secret room under the shrine."),
    ("gatehouse_armory", "The Hidden Armory", "Behind a false wall, the garrison's spare gear."),
    ("aqueduct_channel", "The Inner Channel", "Inside the pier, a maintenance shaft."),
    ("library_stacks", "The Secret Stacks", "The books they did not want borrowed."),
    ("colossus_heart", "The Colossus' Heart", "A chamber in the giant's chest."),
    ("stair_landing", "The Landing Door", "At the top of the stair, a room waited for those who could read the signs."),
    ("titan_hollow", "The Titan's Crown", "High in the stone tree, a hidden floor."),
    ("orrery_core", "The Orrery's Core", "Inside the starstone heart."),
    ("bell_chamber", "Beneath the Bell", "A chamber under the altar."),
    ("ice_tomb", "The Ice Tomb", "Behind the frozen fall."),
    ("lantern_vault", "The Lamp Vault", "Where the League kept its best lamps."),
    ("observatory_crypt", "The Observers' Crypt", "Under the telescope floor."),
    ("ossuary_reliquary", "The Reliquary", "Behind the bones."),
    ("vault_archive", "The Vault Archive", "The record of the vault, kept apart."),
    ("waterfall_grotto", "The Hidden Grotto", "A dry room behind falling water."),
    ("hollow_pillar", "The Hollow Pillar", "A stone column, hollow all the way up."),
    ("buried_reliquary", "The Buried Reliquary", "Under the cairn, a room of offerings."),
    ("hermit_cell", "The Hermit's Cell", "Someone lived inside the rock itself."),
    ("fungal_heart", "The Fungal Heart", "Under the great mushroom, the roots make a room."),
    ("ember_shrine", "The Ember Shrine", "A shrine behind a seam of fire."),
    ("star_window", "The Star Window", "A room under a floor of stars."),
    ("drowned_bell", "The Drowned Bell", "Something rings beneath still water."),
    ("root_hollow", "The Root Hollow", "A hollow inside a living root."),
    ("crystal_geode", "The Geode Heart", "A crystal egg the size of a room."),
]

# ============================================================================================ historical records
# carved inscriptions (short, in a culture's voice) and loose writings (letters, journals, ledgers)
R = []


def rec(rid, title, culture, voice, text, regions=()):
    R.append({"id": rid, "title": title, "culture": culture, "voice": voice, "text": text, "regions": list(regions)})


W_ = "a carving in the six signs"
rec("inscription_wright_1", "Carved Lintel", "wright", W_, "WHAT IS SEALED IS KEPT. WHAT IS KEPT IS NOT LOST.")
rec("inscription_wright_2", "Carved Plinth", "wright", W_, "Six signs to open, six to close. Spiral, eye, wave, flame, star, key.")
rec("inscription_wright_3", "Worn Relief", "wright", W_, "We closed the sky so the deep could breathe. Do not open what we closed.")
rec("inscription_wright_4", "Step Inscription", "wright", W_, "Count the steps. Each was cut by a hand that knew why.")
rec("inscription_wright_5", "Door Frame", "wright", W_, "The door listens. Speak to it in signs, not in force.")
rec("inscription_wright_6", "Cracked Tablet", "wright", W_, "When the engines falter, the light goes from the stone. Wait. It returns.")
L_ = "a League road-marker"
rec("inscription_lantern_1", "Road Marker", "lantern", L_, "Waystation: 6 lamps north. Water at the pump. Lamp oil: none. Sorry.")
rec("inscription_lantern_2", "Road Marker", "lantern", L_, "THIS ROAD KEPT BY THE LEAGUE. Leave the posts lit for the next walker.")
rec("inscription_lantern_3", "Road Marker", "lantern", L_, "Old vault 11 lamps east. CLOSED BY ORDER. Do not reopen.")
rec("inscription_lantern_4", "Scratched Plate", "lantern", L_, "Lamps dark again. Third time this season. Walk in pairs.")
rec("inscription_lantern_5", "Road Marker", "lantern", L_, "From here the crystal country. Lamps burn brighter. Mind your eyes.")
T_ = "words sung into stone"
rec("inscription_tide_1", "Shell Carving", "tide", T_, "The water remembers every voice. Sing softly, so it remembers you kindly.")
rec("inscription_tide_2", "Shell Carving", "tide", T_, "Here we gave the shrine to the water, so it would never be forgotten.")
rec("inscription_tide_3", "Shell Carving", "tide", T_, "The old ones went down into the deep water and did not come back. We sing for them.")
rec("inscription_tide_4", "Shell Carving", "tide", T_, "Offer what the water gives you, and it will give again.")
E_ = "an oath cut into iron"
rec("inscription_ember_1", "Oath Plate", "ember", E_, "KEEP THE FIRES LIT. SEVEN LINKS, ONE CHAIN, NO BREAK.")
rec("inscription_ember_2", "Oath Plate", "ember", E_, "When the forge goes dark, the world goes cold. So we were told. So we tell.")
rec("inscription_ember_3", "Oath Plate", "ember", E_, "The first smiths found these fires already burning. We have only fed them.")
rec("inscription_ember_4", "Oath Plate", "ember", E_, "Light the hearths in the order of the tally, or not at all.")
G_ = "a garden stone"
rec("inscription_gardener_1", "Garden Stone", "gardener", G_, "Every seed is a door. Plant it, and wait for it to open.")
rec("inscription_gardener_2", "Garden Stone", "gardener", G_, "These seeds came down from the great tree. Keep them dry and keep them dark.")
rec("inscription_gardener_3", "Garden Stone", "gardener", G_, "We buried our dead with seeds in their hands, so they would have something to do.")
rec("inscription_gardener_4", "Garden Stone", "gardener", G_, "A winter without seasons came. The gardens slept. They will wake.")
S_ = "a star-reader's notation"
rec("inscription_star_1", "Star Notation", "star", S_, "The ceiling is a lens. Behind it, the true sky. We have measured its stars.")
rec("inscription_star_2", "Star Notation", "star", S_, "Point the eye at the brightest stone and wait for it to move. (It has not moved.)")
rec("inscription_star_3", "Star Notation", "star", S_, "Light bends. Turn the mirrors and the light will go where you ask.")
rec("inscription_star_4", "Star Notation", "star", S_, "The lens clouded in the year of the Dimming. We kept counting anyway.")
Q_ = "marks that make no sound"
rec("inscription_silent_1", "Silent Marks", "silent", Q_, "...")
rec("inscription_silent_2", "Silent Marks", "silent", Q_, "Below us it sleeps. Above us they forget. Between, we keep quiet.")
rec("inscription_silent_3", "Silent Marks", "silent", Q_, "Put out every flame. In the dark, the signs will show the way.")
rec("inscription_silent_4", "Silent Marks", "silent", Q_, "Not yet. Not yet. Not yet.")
# loose writings
rec("league_journal_1", "Lamplighter's Journal", "lantern", "Oda, lamplighter of the eastern road",
    "Day 41. The lamps went dark again between the third and fourth posts. I walked it with a hand on the wall. Something breathed in the dark, or I did.")
rec("league_journal_2", "Lamplighter's Journal", "lantern", "Oda, lamplighter of the eastern road",
    "The Guild says the luminite is failing because we opened the old vaults. I say the luminite is failing and we opened the vaults. Not the same thing.")
rec("league_ledger", "Waystation Ledger", "lantern", "the keeper of a waystation",
    "Lamp oil: 0. Bread: 4 loaves. Travellers this week: 2. Travellers last week: 11. If no one comes by the turn of the month I will close the station.")
rec("league_letter", "Unsent Letter", "lantern", "someone signing only 'your brother'",
    "I will come home when the road is lit again. Tell mother the crystal country is beautiful. Do not tell her about the dark.")
rec("league_order", "Guild Order", "lantern", "the Lantern League council",
    "By order of the council, the vault at the end of the eleventh road is to be sealed and its records burned. The council regrets the deaths.")
rec("wright_fragment_1", "Vault Record", "wright", "a vault-keeper",
    "Cycle 9. The engines hum lower. We have closed the outer doors. The sleepers are counted: all present.", )
rec("wright_fragment_2", "Vault Record", "wright", "a vault-keeper",
    "To whoever opens this: we are not gone. We are waiting. Do not wake us without reason.")
rec("wright_fragment_3", "Builder's Note", "wright", "a stone-cutter",
    "The colossus took four generations. The face is my grandmother's. Nobody else knows that, and now you do.")
rec("tide_song", "Drowned Hymn", "tide", "a Tide Choir singer",
    "Water over stone, water over bone, carry us home. Water over gate, water over grate, we will wait.", [FD, SG])
rec("tide_letter", "Note in a Bottle", "tide", "a young chorister",
    "They are flooding the temple tomorrow. I hid my pearl in the grotto under the altar. If you find it, it was mine.", [FD, SG, EW])
rec("ember_log", "Furnace Log", "ember", "a furnace-warden",
    "The deep furnace burns without fuel. I have fed it nothing for a week. It burns. I asked the elders why. They told me to keep feeding it.", [AH, CB])
rec("ember_complaint", "Complaint", "ember", "a junior smith",
    "Seven links, one chain. Seven hearths, no rest. When I asked what would happen if a hearth went out, they made me tend it alone for a month.")
rec("gardener_almanac", "Seed Almanac", "gardener", "a seed-keeper",
    "Ancient seed: plant on mineral ground. Patience. Patience. More patience. It will fruit. It always fruits.", [RW, GG, PF])
rec("gardener_letter", "Letter to a Daughter", "gardener", "a gardener",
    "I planted the terraces in rings so you could find your way to the middle with your eyes closed. Count the rings. You'll find me there.")
rec("star_log_1", "Observation Log", "star", "an observer",
    "Night 3,112 (by our count, there are no nights). The bright stone in the north ceiling has dimmed by a hair. We recorded it. No one cares.", [SL])
rec("star_log_2", "Observation Log", "star", "an observer",
    "Mirror test: the beam reached the receiver on the fourth try. The lens is not a lens. It is a lock. Do not tell the others yet.")
rec("silent_tablet", "Clay Tablet", "silent", "no name, only a pressed handprint",
    "We learned to walk without sound. We learned to speak without sound. It still dreams. We are the quiet around its dreams.", [AB])
rec("hermit_diary", "Hermit's Diary", "lantern", "a hermit",
    "Third winter alone. The ice sings at night. I have stopped answering it.", [EF])
rec("smuggler_note", "Crumpled Note", "lantern", "a smuggler",
    "Hoist needs the right weight or the gate won't lift. Count the notches. Don't be greedy, it tips.")
rec("explorer_page_1", "Explorer's Page", "lantern", "an explorer who did not sign",
    "Food for three days, light for two. If you read this, I went on without enough light. Bring more than you think.")
rec("explorer_page_2", "Explorer's Page", "lantern", "an explorer who did not sign",
    "Rule one: the seals don't break. Don't waste your pick. Rule two: everything else does.")
rec("explorer_page_3", "Explorer's Page", "lantern", "an explorer who did not sign",
    "The plates that look worn are worn for a reason. Step around them, or jam them with a stick.")
rec("scholar_letter", "Scholar's Letter", "star", "a scholar of a later age",
    "Every people down here tells the story differently. The Vaultwrights fled, or slept, or became fire, or water, or seeds. Perhaps they are all right.")

# ============================================================================================ artifacts
ARTIFACTS = []
ACC9 = []
WEAP9 = []


def art(aid, kind, rarity, unique, weight, desc):
    ARTIFACTS.append({"id": aid, "kind": kind, "rarity": rarity, "unique": unique, "weight": weight, "desc": desc})


def acc(aid, slot, desc, attrs=(), protect=None, flags=(), rarity="rare", unique=False, weight=10):
    ACC9.append({"id": aid, "slot": slot, "desc": desc, "attrs": [list(a) for a in attrs], "protect": protect or {}, "flags": list(flags)})
    art(aid, "accessory", rarity, unique, 0 if unique else weight, desc)


acc("wright_signet", "ring", "You sense armed trap plates near you. +1 armour toughness.", [("generic.armor_toughness", 1.0, "add_value")],
    flags=["trapsense"])
acc("lamplighters_ring", "ring", "Lets you see in darkness; +0.5 Luck.", [("generic.luck", 0.5, "add_value")], flags=["lamp"])
acc("choir_pendant", "amulet", "Hold your breath much longer and swim faster.", [("generic.oxygen_bonus", 3.0, "add_value"),
                                                                                  ("generic.water_movement_efficiency", 0.4, "add_value")])
acc("oath_band", "ring", "+1 attack damage; burn for half as long.", [("generic.attack_damage", 1.0, "add_value"), ("generic.burning_time", -0.5, "add_multiplied_base")])
acc("gardeners_locket", "amulet", "+2 maximum health; you sometimes gather extra from plants.", [("generic.max_health", 2.0, "add_value")], flags=["forager"])
acc("readers_lens", "charm", "You notice places from further away, and your journal records more.", flags=["keen", "wayfinder"])
acc("hush_charm", "charm", "You move quietly and quickly while sneaking.", [("player.sneaking_speed", 0.3, "add_value")])
acc("compass_rose", "charm", "Step up full blocks and fall 3 blocks further unharmed.", [("generic.step_height", 0.5, "add_value"),
                                                                                         ("generic.safe_fall_distance", 3.0, "add_value")])
acc("warden_seal", "amulet", "+2 armour, resist knockback, but 3% slower.", [("generic.armor", 2.0, "add_value"), ("generic.knockback_resistance", 0.3, "add_value"),
                                                                            ("generic.movement_speed", -0.03, "add_multiplied_base")])
acc("echo_stone", "charm", "Sealed doors and hidden rooms nearby glimmer for you.", flags=["echo"], rarity="epic", weight=4)
acc("hearth_ring", "ring", "+2 maximum health.", [("generic.max_health", 2.0, "add_value")])
acc("stormcaller_band", "ring", "Static builds 50% slower; swing 8% faster.", [("generic.attack_speed", 0.08, "add_multiplied_base")], {"static": 0.5})
acc("frostbound_torc", "amulet", "Cold builds 50% slower.", protect={"cold": 0.5})
acc("ashwalker_anklet", "charm", "Heat builds 40% slower; move 3% faster.", [("generic.movement_speed", 0.03, "add_multiplied_base")], {"heat": 0.4})
acc("sporeward_mask", "amulet", "Spores and fumes build 50% slower.", protect={"spores": 0.5, "fumes": 0.5})
acc("drowned_crown", "amulet", "Breathe long underwater, swim swiftly and mine as if on land.",
    [("generic.oxygen_bonus", 6.0, "add_value"), ("generic.water_movement_efficiency", 0.6, "add_value"), ("player.submerged_mining_speed", 1.0, "add_value")],
    rarity="legendary", unique=True)
acc("resonant_core", "charm", "Hidden things glimmer, traps reveal themselves, and darkness parts for you.", flags=["echo", "trapsense", "lamp"],
    rarity="legendary", unique=True)
acc("heartwood_heart", "amulet", "+4 maximum health and +1 Luck. It is warm, like something alive.",
    [("generic.max_health", 4.0, "add_value"), ("generic.luck", 1.0, "add_value")], rarity="legendary", unique=True)


def weapon(wid, family, material, damage, speed, reach, kb, dur, special, desc):
    WEAP9.append({"id": wid, "family": family, "material": material, "damage": damage, "speed": speed, "reach": reach, "knockback": kb,
                  "durability": dur, "special": special})
    art(wid, "weapon", "epic", False, 5, desc)


weapon("wright_glaive", "spear", "starsilver", 7.0, 1.35, 1.5, 0.0, 900, "radiant", "A Vaultwright guard's glaive. Long reach; bites the restless dead.")
weapon("lamplighter_hook", "dagger", "galvanic", 4.5, 2.5, 0.0, 0.0, 700, "glimmer", "A lamplighter's hook, sharpened. Marks what it strikes.")
weapon("oathkeeper_hammer", "warhammer", "embersteel", 10.0, 0.8, 0.25, 1.2, 1400, "heat", "A Concord oath-hammer. Every blow is a small fire.")
weapon("choir_harpoon", "spear", "nacre", 6.5, 1.35, 1.0, 0.0, 800, "aquatic", "A Choir harpoon, made to sing through water.")
weapon("silent_edge", "sword", "umbralite", 7.0, 1.7, 0.0, 0.0, 1600, "heavy", "A blade that makes no sound, even when it strikes armour.")

for aid, desc in [("tuning_fork", "Rings toward sealed doors and hidden rooms within 24 blocks (cooldown)."),
                  ("breath_shell", "Breathe underwater for 90 seconds (cooldown 3 minutes)."),
                  ("homing_stone", "Sneak-use to remember a place; use to learn the way back to it."),
                  ("sounding_horn", "Its echo reveals lurking hostile creatures for 10 seconds (cooldown).")]:
    art(aid, "utility", "epic", False, 4, desc)
art("warding_bell", "utility", "epic", False, 3, "Place it: hostile creatures do not spawn naturally within 16 blocks. A safe camp.")
RELICS = [("wright_bust", "A stone bust of a Vaultwright face, calm and old."), ("lantern_head", "The glass head of a League road lamp."),
          ("choir_conch", "A conch that hums when held near water."), ("ember_crown", "A blackened circlet from an oath-keeper's grave."),
          ("seed_urn", "A sealed urn. Something rattles inside: seeds?"), ("star_globe", "A sphere engraved with stars that are not ours."),
          ("silent_mask", "A mask without a mouth."), ("gilded_idol", "A small gilded figure, rubbed smooth by hands."),
          ("league_astrolabe", "A League surveying instrument, its dials stuck."), ("petrified_effigy", "A figure carved from petrified wood.")]
for rid, desc in RELICS:
    art(rid, "relic", "rare", False, 6, desc)

# ============================================================================================ rumours and echoes
RUM = []


def rum(culture, kind, *texts):
    for t in texts:
        RUM.append({"culture": culture, "kind": kind, "text": t})


rum("any", "ruin", "There's an old {name} out there, {where}. Nobody goes. You might.",
    "My grandmother used to talk about a {name}, {where}. I never went.")
rum("any", "landmark", "If you go {where}, you'll see something. Can't miss it. A {name}. Take a friend, it's a long way.")
rum("any", "dungeon", "Folk say there's a {name} {where}. Folk also say don't go there. I'd listen to the second part.",
    "A trader told me about a {name}, {where}. He said it was shut. He also said he heard it breathing.")
rum("any", "exceptional", "The scholars won't say it aloud, but I've seen the old maps. There's something called the {name}, {where}. Go prepared.")
rum("any", "none", "Old places? Down here everything's old. But nothing I'd send you to, not near here.",
    "Not round here. Try asking a cartographer, or walking further than sensible people do.")
rum("rootfolk", "ruin", "Beyond the roots, {where}, there's a {name}. The leaves grow funny around it.")
rum("rootfolk", "landmark", "The elders say the ribs of the sky come down somewhere {where}. Look for a {name}.")
rum("rootfolk", "dungeon", "There's a {name} {where}. My uncle went in. He came out quieter.")
rum("forgekin", "ruin", "There's a cold forge, or what's left of one, {where}. A {name}. Shameful, letting a fire die.")
rum("forgekin", "dungeon", "The oath says keep the fires lit. Whoever built the {name} {where} kept them lit underground. Strange people.")
rum("tide_dwellers", "ruin", "The water remembers a {name}, {where}. Sing as you walk, it helps.")
rum("tide_dwellers", "dungeon", "They say the old ones went into the deep water. There's a {name}, {where}. Bring air.")
rum("sporefolk", "ruin", "The spores remember a {name}, {where}. Breathe slow there.")
rum("glimmerkin", "landmark", "If you want to see something that sings, go {where}. A {name}.")
rum("stoneborn", "ruin", "Good stone in the {name}, {where}. Shame about whoever left it.")
rum("amberfolk", "ruin", "There's an old {name}, {where}, with gardens nobody tends. They're growing anyway.")
rum("astrals", "landmark", "The ceiling lies, you know. Go {where} and look at the {name}, and tell me I'm wrong.")
rum("astrals", "dungeon", "The {name}, {where}. The old observers left their best work behind a door that only answers light.")
rum("any", "echo", "...keep the lamps lit...", "...count the signs...", "...not yet...", "...the water remembers...", "...we are only waiting...",
    "...walk in pairs...", "...put out the flames...")

# ============================================================================================ encounters
ENC = [
    ("echo_whisper", ALL, 1.0, 20, False, "A whisper near old ruins."),
    ("glowmoth_swarm", [RW, GG, FE, SL, CD, SG], 1.2, 15, False, "Glowing moths."),
    ("rockfall", [HP, PF, SS, CD, AH, CB], 0.8, 20, False, "Falling rock with a warning."),
    ("ore_reveal", ALL, 0.7, 30, False, "A wall sloughs off and bares ore."),
    ("lost_pack", ALL, 0.6, 40, False, "Remains of an earlier explorer."),
    ("abandoned_camp", ALL, 0.6, 40, False, "A cold camp with supplies."),
    ("will_o_light", [RW, GG, FE, SL, CD, EW, SG], 0.6, 30, False, "A light leads toward something."),
    ("ghost_lanterns", ALL, 0.15, 90, True, "A rare procession of lights."),
    ("spore_bloom", [FE], 1.0, 20, False, "Fungi release spores and new caps grow."),
    ("geyser", [AH, CB], 1.0, 20, False, "Steam bursts from the floor."),
    ("crystal_song", [CD, SL], 1.0, 25, False, "Crystals chime and grow."),
    ("ice_crack", [EF], 1.0, 20, False, "The ice groans."),
    ("falling_star", [SL], 0.8, 30, False, "A shard of light falls."),
    ("static_storm", [SS], 1.0, 20, False, "Sparks crawl over the stone."),
    ("drifting_raft", [FD, SG, EW], 0.6, 45, False, "A drifting boat with cargo."),
    ("tremor", ALL, 0.6, 20, False, "The ground trembles."),
    ("distant_bell", [AB], 0.6, 40, True, "A bell tolls far away."),
    ("lost_page", ALL, 0.7, 30, False, "A scrap of old writing."),
]

# ============================================================================================ loot tables
I = lambda i: i if ":" in i else f"{NS}:{i}"  # noqa: E731


def entry(item, w=1, lo=1, hi=1, extra=None):
    e = {"type": "minecraft:item", "name": I(item), "weight": w}
    fns = []
    if hi > 1 or lo != 1:
        fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if extra:
        fns += extra
    if fns:
        e["functions"] = fns
    return e


def table(name, pools):
    wjson(os.path.join(DATA, NS, "loot_table", "exploration", name + ".json"), {"type": "minecraft:chest", "pools": pools})


def pool(rolls, *entries, bonus=0):
    p = {"rolls": rolls if isinstance(rolls, int) else {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "entries": list(entries)}
    if bonus:
        p["bonus_rolls"] = bonus
    return p


EMPTY = {"type": "minecraft:empty", "weight": 1}
SUPPLIES = [entry("minecraft:torch", 6, 2, 8), entry("minecraft:bread", 5, 1, 3), entry("minecraft:string", 3, 1, 4), entry("minecraft:arrow", 3, 2, 8),
            entry("minecraft:coal", 4, 1, 4), entry("bandage", 3, 1, 2), entry("rope_coil", 2), entry("minecraft:candle", 2, 1, 3)]
VALUABLES = [entry("ancient_coin", 8, 1, 4), entry("minecraft:emerald", 4, 1, 3), entry("minecraft:gold_nugget", 5, 2, 8), entry("encrusted_relic", 3),
             entry("lore_fragment", 3)]
METALS = [entry("minecraft:iron_ingot", 5, 1, 4), entry("steel_ingot", 3, 1, 3), entry("minecraft:copper_ingot", 4, 2, 6), entry("minecraft:gold_ingot", 2, 1, 2)]


def relic_entries(*ids, w=1):
    return [entry(r, w) for r in ids]


def build_loot():
    table("minor_cache", [pool((1, 3), *SUPPLIES, EMPTY), pool(1, entry("ancient_coin", 2, 1, 2), entry("lore_fragment", 1), {**EMPTY, "weight": 4})])
    table("miners", [pool((2, 4), entry("minecraft:raw_iron", 5, 1, 4), entry("minecraft:raw_copper", 5, 2, 6), entry("minecraft:coal", 5, 2, 6),
                          entry("minecraft:iron_pickaxe", 1), entry("minecraft:torch", 4, 4, 10), entry("minecraft:rail", 2, 2, 8), EMPTY),
                     pool(1, entry("lore_fragment", 1), entry("ancient_coin", 1), {**EMPTY, "weight": 4})])
    table("watch", [pool((1, 3), entry("minecraft:arrow", 5, 4, 12), entry("minecraft:bow", 1), entry("minecraft:bread", 4, 1, 3), entry("bandage", 3, 1, 2),
                         entry("minecraft:iron_ingot", 2, 1, 2)), pool(1, entry("ancient_coin", 2, 1, 3), {**EMPTY, "weight": 3})])
    table("kiln_pot", [pool((0, 2), entry("minecraft:clay_ball", 5, 2, 6), entry("minecraft:brick", 4, 1, 4), entry("ancient_coin", 2, 1, 3),
                            entry("minecraft:gold_nugget", 3, 1, 4), entry("minecraft:bone_meal", 2, 1, 3), EMPTY)])
    table("ruin_common", [pool((2, 4), *SUPPLIES, *METALS[:2], EMPTY), pool((1, 2), *VALUABLES, EMPTY)])
    table("league_cache", [pool((2, 4), entry("minecraft:lantern", 4, 1, 2), entry("minecraft:copper_ingot", 5, 2, 8), entry("minecraft:glass", 3, 2, 6),
                                entry("minecraft:paper", 3, 2, 6), entry("minecraft:compass", 1), entry("depth_gauge", 1), entry("minecraft:torch", 4, 4, 10)),
                           pool((1, 2), *VALUABLES, entry("lantern_head", 1), entry("league_astrolabe", 1))])
    table("ruin_forge", [pool((2, 4), *METALS, entry("minecraft:coal", 4, 2, 8), entry("embersteel_ingot", 1), entry("minecraft:chain", 2, 1, 4)),
                         pool(1, *VALUABLES, entry("ember_crown", 1))])
    table("tide", [pool((2, 4), entry("minecraft:prismarine_shard", 5, 2, 6), entry("minecraft:prismarine_crystals", 3, 1, 4), entry("pearl", 3, 1, 2),
                        entry("minecraft:cod", 3, 1, 3), entry("minecraft:nautilus_shell", 1), entry("minecraft:sea_pickle", 2, 1, 3)),
                   pool((1, 2), *VALUABLES, entry("choir_conch", 1))])
    table("gardener", [pool((2, 4), entry("minecraft:bone_meal", 4, 2, 6), entry("compost", 3, 1, 4), entry("sunbean", 3, 1, 4), entry("duskroot", 3, 1, 4),
                            entry("minecraft:honeycomb", 2, 1, 3), entry("ancient_seed", 1)), pool((1, 2), *VALUABLES, entry("seed_urn", 1))])
    table("star", [pool((2, 4), entry("minecraft:amethyst_shard", 5, 2, 6), entry("minecraft:quartz", 4, 2, 6), entry("minecraft:spyglass", 1),
                        entry("minecraft:glowstone_dust", 3, 2, 6), entry("star_shard", 2)), pool((1, 2), *VALUABLES, entry("star_globe", 1))])
    table("fungal", [pool((2, 4), entry("puffcap", 4, 1, 4), entry("glowcap", 3, 1, 4), entry("sagecap", 1), entry("minecraft:brown_mushroom", 3, 1, 4),
                          entry("bandage", 2)), pool(1, *VALUABLES)])
    table("hermit", [pool((2, 4), entry("minecraft:candle", 3, 1, 4), entry("minecraft:book", 3, 1, 2), entry("minecraft:bread", 3, 1, 3), entry("bandage", 3, 1, 2),
                          entry("minecraft:white_wool", 2, 1, 2)), pool((1, 2), entry("lore_fragment", 4), entry("ancient_coin", 2, 1, 3), entry("encrusted_relic", 1))])
    table("storm", [pool((2, 4), entry("minecraft:copper_ingot", 5, 2, 8), entry("galvanic_ingot", 2, 1, 2), entry("minecraft:redstone", 4, 2, 8),
                         entry("minecraft:lightning_rod", 1)), pool((1, 2), *VALUABLES)])
    table("silent", [pool((1, 3), entry("minecraft:echo_shard", 3, 1, 2), entry("minecraft:sculk", 3, 1, 4), entry("minecraft:candle", 3, 1, 3),
                          entry("minecraft:bone", 3, 2, 6)), pool((1, 2), *VALUABLES, entry("silent_mask", 1))])
    table("gatehouse", [pool((2, 4), entry("minecraft:arrow", 4, 4, 16), entry("minecraft:iron_ingot", 4, 1, 4), entry("steel_ingot", 2, 1, 3),
                             entry("minecraft:shield", 1), entry("bandage", 3, 1, 3)), pool((1, 2), *VALUABLES, entry("wright_bust", 1))])
    table("armory", [pool((2, 3), entry("steel_sword", 2), entry("steel_spear", 2), entry("steel_helmet", 1), entry("steel_chestplate", 1),
                          entry("minecraft:arrow", 4, 8, 16), entry("honed_edge_kit", 2), entry("reinforced_plating_kit", 2)),
                     pool(1, *VALUABLES, entry("warden_seal", 1))])
    table("market", [pool((1, 3), entry("minecraft:emerald", 3, 1, 3), entry("ancient_coin", 5, 2, 6), entry("minecraft:paper", 3, 1, 4),
                          entry("minecraft:string", 3, 1, 4), entry("gilded_idol", 1)), pool(1, entry("encrusted_relic", 2), EMPTY)])
    table("library", [pool((2, 4), entry("minecraft:book", 5, 1, 3), entry("minecraft:paper", 4, 2, 6), entry("lore_fragment", 6), entry("minecraft:ink_sac", 2, 1, 3),
                           entry("minecraft:enchanted_book", 1, extra=[{"function": "minecraft:enchant_randomly"}])),
                      pool(1, *VALUABLES)])
    table("library_secret", [pool((2, 3), entry("lore_fragment", 6), entry("minecraft:enchanted_book", 3, extra=[{"function": "minecraft:enchant_randomly"}]),
                                  entry("encrusted_relic", 2)), pool(1, entry("readers_lens", 1), entry("tuning_fork", 1), entry("ancient_coin", 4, 3, 8))])
    table("cellar", [pool((2, 3), *SUPPLIES, entry("minecraft:emerald", 2, 1, 3)), pool(1, entry("hearth_ring", 1), entry("encrusted_relic", 2),
                                                                                         entry("ancient_coin", 3, 2, 6))])
    table("strongbox", [pool((2, 3), *METALS, entry("minecraft:emerald", 2, 2, 4)), pool(1, entry("oath_band", 1), entry("encrusted_relic", 2),
                                                                                         entry("ancient_coin", 3, 3, 8))])
    table("pearl", [pool((1, 3), entry("pearl", 5, 1, 3), entry("minecraft:prismarine_crystals", 3, 1, 3)),
                    pool(1, entry("choir_pendant", 1), entry("breath_shell", 1), entry("choir_conch", 2), entry("encrusted_relic", 2))])
    table("secret_stash", [pool((2, 3), *VALUABLES, *METALS[:2]), pool(1, entry("encrusted_relic", 3), entry("compass_rose", 1), entry("hush_charm", 1),
                                                                         entry("homing_stone", 1), entry("lore_fragment", 2))])
    table("colossus", [pool((2, 3), *VALUABLES), pool(1, entry("wright_bust", 3), entry("wright_signet", 1), entry("echo_stone", 1))])
    table("stair", [pool((2, 3), *VALUABLES), pool(1, entry("wright_glaive", 1), entry("echo_stone", 1), entry("tuning_fork", 1), entry("encrusted_relic", 2))])
    table("ice", [pool((1, 3), entry("minecraft:blue_ice", 3, 1, 4), entry("cryolite", 3, 1, 3), entry("minecraft:snowball", 2, 2, 8)),
                  pool(1, entry("frostbound_torc", 2), entry("encrusted_relic", 2), entry("ancient_coin", 3, 2, 6))])
    table("crystal", [pool((2, 3), entry("luminite_cluster", 4, 1, 3), entry("minecraft:amethyst_shard", 4, 2, 6), entry("cut_opal", 1)),
                      pool(1, entry("star_globe", 1), entry("readers_lens", 1), entry("encrusted_relic", 2))])
    table("lantern_vault", [pool((2, 3), entry("minecraft:lantern", 3, 1, 3), entry("glimmer_lantern", 2), entry("minecraft:copper_ingot", 3, 2, 6)),
                            pool(1, entry("lamplighters_ring", 2), entry("lamplighter_hook", 1), entry("lantern_head", 2))])
    table("seed_vault", [pool((2, 4), entry("ancient_seed", 2), entry("sunbean", 3, 2, 6), entry("glowgrain", 2, 2, 6), entry("minecraft:honeycomb", 2, 1, 3)),
                         pool(1, entry("gardeners_locket", 2), entry("seed_urn", 2), entry("encrusted_relic", 1))])
    table("smuggler", [pool((2, 4), entry("minecraft:emerald", 3, 1, 4), entry("ancient_coin", 4, 2, 6), entry("minecraft:gunpowder", 2, 1, 3),
                            entry("minecraft:string", 3, 2, 6), entry("minecraft:lead", 1)), pool(1, entry("encrusted_relic", 2), entry("gilded_idol", 1), EMPTY)])
    table("dungeon_small", [pool((2, 4), *SUPPLIES, *METALS), pool((1, 2), *VALUABLES)])
    table("dungeon_medium", [pool((3, 5), *SUPPLIES, *METALS, entry("minecraft:diamond", 1)), pool((1, 3), *VALUABLES)])
    table("dungeon_large", [pool((3, 6), *METALS, entry("minecraft:diamond", 2, 1, 2), entry("starsilver_ingot", 1),
                                 entry("minecraft:enchanted_book", 2, extra=[{"function": "minecraft:enchant_randomly"}])), pool((2, 3), *VALUABLES)])
    coffer_art = [entry(a["id"], max(1, a["weight"])) for a in ARTIFACTS if not a["unique"] and a["kind"] in ("accessory", "utility", "weapon")]
    relics = [entry(r[0], 1) for r in RELICS]
    table("coffer_small", [pool((2, 3), *METALS, *SUPPLIES[:4]), pool(1, *VALUABLES), pool(1, entry("encrusted_relic", 3), *relics, {**EMPTY, "weight": 8})])
    table("coffer_medium", [pool((2, 4), *METALS, entry("minecraft:diamond", 1)), pool((1, 2), *VALUABLES),
                            pool(1, entry("encrusted_relic", 6), *relics, *coffer_art, {**EMPTY, "weight": 10})])
    table("coffer_large", [pool((3, 4), *METALS, entry("minecraft:diamond", 2, 1, 2), entry("starsilver_ingot", 1)), pool((2, 3), *VALUABLES),
                           pool(1, entry("encrusted_relic", 4), *relics, *coffer_art)])
    table("coffer_exceptional", [pool((3, 5), *METALS, entry("minecraft:diamond", 3, 1, 3), entry("umbralite_ingot", 1)), pool((2, 4), *VALUABLES),
                                 pool((1, 2), *coffer_art, *relics)])
    table("lost_pack", [pool((2, 4), *SUPPLIES), pool(1, entry("lore_fragment", 3), entry("ancient_coin", 2, 1, 3), entry("minecraft:compass", 1), EMPTY)])
    table("abandoned_camp", [pool((2, 3), *SUPPLIES, entry("minecraft:cooked_cod", 3, 1, 3)), pool(1, entry("lore_fragment", 4), entry("encrusted_relic", 1), EMPTY)])
    table("raft", [pool((2, 4), entry("minecraft:cod", 4, 1, 4), entry("minecraft:string", 3, 2, 6), entry("pearl", 2, 1, 2), entry("minecraft:paper", 2, 1, 4),
                        entry("minecraft:oak_planks", 2, 2, 6)), pool(1, *VALUABLES, EMPTY)])
    colours = ["white", "cyan", "orange", "black", "yellow", "red"]
    for a in range(6):
        for b in range(6):
            if a != b:
                table(f"mosaic_{a}_{b}", [pool(1, entry(f"minecraft:{colours[a]}_terracotta", 1, 3, 4)), pool(1, entry(f"minecraft:{colours[b]}_terracotta", 1, 3, 4)),
                                          pool(1, entry(f"minecraft:{colours[(a + 2) % 6]}_terracotta", 1, 1, 2))])
    # block drops: relic décor and the warding bell drop themselves
    for rid, _ in RELICS + [("warding_bell", "")]:
        wjson(os.path.join(DATA, NS, "loot_table", "blocks", rid + ".json"), {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": I(rid)}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


# ============================================================================================ main
def main(extra_lang=None):
    build_loot()
    for fid, name, desc in SECRETS:
        pass
    families = list(F)
    content = {"families": families, "secrets": [{"id": s[0], "name": s[1], "desc": s[2]} for s in SECRETS], "records": R, "artifacts": ARTIFACTS,
               "rumors": RUM, "encounters": [{"id": e[0], "regions": list(e[1]), "weight": e[2], "cooldown": e[3], "rare": e[4], "desc": e[5]} for e in ENC]}
    wjson(os.path.join(DATA, NS, "exploration", "content.json"), content)
    # artifact equipment appended to the Stage 8 equipment data
    eq_path = os.path.join(DATA, NS, "equipment", "content.json")
    eq = json.load(open(eq_path))
    eq["accessories"] = [a for a in eq["accessories"] if a["id"] not in {x["id"] for x in ACC9}] + ACC9
    eq["weapons"] = [w for w in eq["weapons"] if w["id"] not in {x["id"] for x in WEAP9}] + WEAP9
    eq["artifacts"] = [a["id"] for a in ACC9] + [w["id"] for w in WEAP9]
    wjson(eq_path, eq)
    # visibility: mechanisms are technical (never in the Handbook); artifacts and relics show after they are obtained
    vis_path = os.path.join(DATA, NS, "guide", "visibility.json")
    vis = json.load(open(vis_path))
    tech = set(vis["technical"]) | {I(b) for b in A.TECHNICAL_BLOCKS}
    sens = set(vis["sensitive"]) | {I(a["id"]) for a in ARTIFACTS}
    vis["technical"] = sorted(tech)
    vis["sensitive"] = sorted(sens)
    wjson(vis_path, vis)
    lang = A.assets(ARTIFACTS, ACC9, WEAP9, RELICS)
    LANG.update(lang)
    print(f"stage 9: {len(F)} site families ({sum(1 for f in F if f['category'] == 'ruin')} ruins, {sum(1 for f in F if f['category'] == 'landmark')} landmarks, "
          f"{sum(1 for f in F if f['category'] == 'dungeon')} dungeons, {sum(1 for f in F if f['category'] == 'secret')} standalone secrets), "
          f"{len(SECRETS)} secret rooms, {len(R)} records, {len(ARTIFACTS)} artifacts/relics, {len(RUM)} rumour lines, {len(ENC)} encounters")
    return LANG


if __name__ == "__main__":
    main()
