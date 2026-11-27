package dev.underdeep.explore.design;

import dev.underdeep.explore.Canvas;
import dev.underdeep.explore.ExploreContent;
import dev.underdeep.explore.Site;
import dev.underdeep.explore.Style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Registry of authored site designs (family id → builder). Every family in content.json must have a builder and
 * vice versa (checked at load and by the self-test). Builders are pure: same site → same canvas.
 */
public final class Designs {
    private Designs() {
    }

    public static final Map<String, BiConsumer<Kit, Site>> BUILDERS = new LinkedHashMap<>();

    static {
        // ruins
        BUILDERS.put("wayshrine", Ruins::wayshrine);
        BUILDERS.put("lantern_post", Ruins::lanternPost);
        BUILDERS.put("collapsed_hut", Ruins::collapsedHut);
        BUILDERS.put("miners_camp", Ruins::minersCamp);
        BUILDERS.put("cairn_circle", Ruins::cairnCircle);
        BUILDERS.put("broken_bridge", Ruins::brokenBridge);
        BUILDERS.put("fallen_statue", Ruins::fallenStatue);
        BUILDERS.put("well_head", Ruins::wellHead);
        BUILDERS.put("watch_niche", Ruins::watchNiche);
        BUILDERS.put("grave_garden", Ruins::graveGarden);
        BUILDERS.put("kiln_mound", Ruins::kilnMound);
        BUILDERS.put("waystone", Ruins::waystone);
        BUILDERS.put("cottage_row", Ruins::cottageRow);
        BUILDERS.put("lantern_waystation", Ruins::lanternWaystation);
        BUILDERS.put("forge_ruin", Ruins::forgeRuin);
        BUILDERS.put("tide_shrine", Ruins::tideShrine);
        BUILDERS.put("seed_terrace", Ruins::seedTerrace);
        BUILDERS.put("star_dial", Ruins::starDial);
        BUILDERS.put("fungal_hall", Ruins::fungalHall);
        BUILDERS.put("ice_hermitage", Ruins::iceHermitage);
        BUILDERS.put("stilt_village", Ruins::stiltVillage);
        BUILDERS.put("storm_tower", Ruins::stormTower);
        BUILDERS.put("quarry", Ruins::quarry);
        BUILDERS.put("silent_ruin", Ruins::silentRuin);
        BUILDERS.put("vault_gatehouse", Ruins::vaultGatehouse);
        BUILDERS.put("aqueduct", Ruins::aqueduct);
        BUILDERS.put("market_square", Ruins::marketSquare);
        BUILDERS.put("great_library", Ruins::greatLibrary);
        BUILDERS.put("amphitheatre", Ruins::amphitheatre);
        BUILDERS.put("foundry_complex", Ruins::foundryComplex);
        // landmarks
        BUILDERS.put("colossus", Landmarks::colossus);
        BUILDERS.put("great_stair", Landmarks::greatStair);
        BUILDERS.put("sky_bridge", Landmarks::skyBridge);
        BUILDERS.put("lantern_spire", Landmarks::lanternSpire);
        BUILDERS.put("root_arch", Landmarks::rootArch);
        BUILDERS.put("crystal_cathedral", Landmarks::crystalCathedral);
        BUILDERS.put("frozen_falls", Landmarks::frozenFalls);
        BUILDERS.put("ember_caldera", Landmarks::emberCaldera);
        BUILDERS.put("sunken_dome", Landmarks::sunkenDome);
        BUILDERS.put("mushroom_ring", Landmarks::mushroomRing);
        BUILDERS.put("petrified_titan", Landmarks::petrifiedTitan);
        BUILDERS.put("star_orrery", Landmarks::starOrrery);
        BUILDERS.put("storm_pylons", Landmarks::stormPylons);
        BUILDERS.put("hanging_gardens", Landmarks::hangingGardens);
        BUILDERS.put("obelisk_field", Landmarks::obeliskField);
        BUILDERS.put("silent_bell", Landmarks::silentBell);
        // dungeons
        BUILDERS.put("sealed_cellar", Dungeons::sealedCellar);
        BUILDERS.put("smugglers_den", Dungeons::smugglersDen);
        BUILDERS.put("drowned_crypt", Dungeons::drownedCrypt);
        BUILDERS.put("kiln_vault", Dungeons::kilnVault);
        BUILDERS.put("spore_warren", Dungeons::sporeWarren);
        BUILDERS.put("frost_cistern", Dungeons::frostCistern);
        BUILDERS.put("echo_vault", Dungeons::echoVault);
        BUILDERS.put("lantern_works", Dungeons::lanternWorks);
        BUILDERS.put("warden_barracks", Dungeons::wardenBarracks);
        BUILDERS.put("tide_temple", Dungeons::tideTemple);
        BUILDERS.put("seed_vault", Dungeons::seedVault);
        BUILDERS.put("star_observatory", Dungeons::starObservatory);
        BUILDERS.put("stormworks", Dungeons::stormworks);
        BUILDERS.put("ossuary", Dungeons::ossuary);
        BUILDERS.put("wright_vault", Dungeons::wrightVault);
        BUILDERS.put("sunken_archive", Dungeons::sunkenArchive);
        BUILDERS.put("deep_foundry", Dungeons::deepFoundry);
        BUILDERS.put("singing_hall", Dungeons::singingHall);
        BUILDERS.put("heartwood", Dungeons::heartwood);
        BUILDERS.put("silent_sanctum", Dungeons::silentSanctum);
        // standalone secrets
        BUILDERS.put("waterfall_grotto", Secrets::waterfallGrotto);
        BUILDERS.put("hollow_pillar", Secrets::hollowPillar);
        BUILDERS.put("buried_reliquary", Secrets::buriedReliquary);
        BUILDERS.put("hermit_cell", Secrets::hermitCell);
        BUILDERS.put("fungal_heart", Secrets::fungalHeart);
        BUILDERS.put("ember_shrine", Secrets::emberShrine);
        BUILDERS.put("star_window", Secrets::starWindow);
        BUILDERS.put("drowned_bell", Secrets::drownedBell);
        BUILDERS.put("root_hollow", Secrets::rootHollow);
        BUILDERS.put("crystal_geode", Secrets::crystalGeode);
    }

    public static Canvas build(Site s) {
        Style style = Style.of(s.family().culture(), s.region());
        Canvas c = new Canvas(s.size(), s.region(), style, s.seed());
        BiConsumer<Kit, Site> b = BUILDERS.get(s.family().id());
        if (b != null) {
            b.accept(new Kit(c), s);
        }
        return c;
    }

    /** Families without a builder and builders without a family (both must be empty). */
    public static List<String> problems() {
        List<String> out = new ArrayList<>();
        for (String f : ExploreContent.FAMILIES.keySet()) {
            if (!BUILDERS.containsKey(f)) {
                out.add("no builder: " + f);
            }
        }
        for (String b : BUILDERS.keySet()) {
            if (!ExploreContent.FAMILIES.containsKey(b)) {
                out.add("no family: " + b);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ records

    /** A carved inscription of the site's culture. */
    static String carved(Kit k, Site s) {
        String culture = s.family().culture().name().toLowerCase();
        List<String> pool = new ArrayList<>();
        for (ExploreContent.Record r : ExploreContent.RECORDS.values()) {
            if (r.id().startsWith("inscription_") && r.culture().equals(culture)) {
                pool.add(r.id());
            }
        }
        return pool.isEmpty() ? "" : pool.get(k.ri(pool.size()));
    }

    /** A written record (journal page, letter) found loose in a site, preferring the site's culture and region. */
    static String loose(Kit k, Site s) {
        String culture = s.family().culture().name().toLowerCase();
        List<String> local = new ArrayList<>(), any = new ArrayList<>();
        for (ExploreContent.Record r : ExploreContent.RECORDS.values()) {
            if (r.id().startsWith("inscription_")) {
                continue;
            }
            if (r.culture().equals(culture) && (r.regions().isEmpty() || r.regions().contains(s.region().id()))) {
                local.add(r.id());
            } else {
                any.add(r.id());
            }
        }
        List<String> pool = local.isEmpty() ? any : local;
        return pool.isEmpty() ? "" : pool.get(k.ri(pool.size()));
    }

    /** Waystones carry League road notes. */
    static String waystoneRecord(Kit k, Site s) {
        List<String> pool = new ArrayList<>();
        for (ExploreContent.Record r : ExploreContent.RECORDS.values()) {
            if (r.id().startsWith("inscription_lantern")) {
                pool.add(r.id());
            }
        }
        return pool.isEmpty() ? carved(k, s) : pool.get(k.ri(pool.size()));
    }
}
