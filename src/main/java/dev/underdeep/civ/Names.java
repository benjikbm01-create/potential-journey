package dev.underdeep.civ;

import java.util.Locale;
import java.util.Random;

/**
 * Deterministic name generators per civilization (settlements and inhabitants). Each culture has its own
 * syllable inventory and naming pattern, giving hundreds of settlement names and thousands of personal
 * names per culture.
 */
public final class Names {
    private Names() {
    }

    private record Lang(String[] starts, String[] mids, String[] ends, String[] placeSuffixes, String[] placeWords,
                        String[] familyA, String[] familyB) {
    }

    private static final Lang ROOT = new Lang(
            new String[]{"Ash", "Bri", "Fen", "Hol", "Lin", "Mos", "Ro", "Tam", "Wil", "Elm", "Bar", "Cor", "Dun", "Fal", "Hazel", "Iv", "Ker", "Oak", "Thorn", "Wren"},
            new String[]{"a", "e", "i", "o", "ly", "wy", "ar", "en", "il", "or"},
            new String[]{"n", "th", "wyn", "ric", "dle", "ra", "ma", "ley", "bert", "beth", "win", "lock", "ny"},
            new String[]{"hollow", "glen", "wood", "root", "dell", "bough", "brook", "shade"},
            new String[]{"Hollow", "Glade", "Roots", "Dell", "Thicket"},
            new String[]{"Moss", "Bark", "Fern", "Root", "Leaf", "Briar", "Thistle", "Willow"},
            new String[]{"wood", "weaver", "step", "song", "gather", "bloom", "keeper", "hand"});
    private static final Lang FORGE = new Lang(
            new String[]{"Brak", "Dur", "Gral", "Hag", "Kor", "Mag", "Orm", "Thra", "Vor", "Zan", "Bol", "Dag", "Gor", "Krag", "Tor"},
            new String[]{"a", "o", "u", "ra", "um", "ok", "ar", "ga"},
            new String[]{"din", "grim", "mek", "rak", "dun", "nor", "gar", "thul", "bek", "zor", "rim"},
            new String[]{"forge", "deep", "hold", "anvil", "kiln", "spark", "vault", "gate"},
            new String[]{"Forge", "Furnace", "Smelt", "Bellows", "Anvil"},
            new String[]{"Iron", "Cinder", "Slag", "Ember", "Coal", "Bronze", "Ash", "Flint"},
            new String[]{"hammer", "beard", "fist", "brand", "hearth", "tong", "vein", "bellow"});
    private static final Lang TIDE = new Lang(
            new String[]{"Mar", "Nel", "Sal", "Cor", "Ais", "Lir", "Pel", "Ren", "Siv", "Tal", "Mer", "Ond", "Ys", "Wav", "Bri"},
            new String[]{"a", "e", "i", "ae", "ia", "ou", "el"},
            new String[]{"na", "ra", "lis", "mer", "ven", "sa", "rin", "wen", "dal", "ros", "nos"},
            new String[]{"haven", "port", "mere", "reach", "moor", "quay", "wharf", "shoal"},
            new String[]{"Haven", "Landing", "Moorings", "Harbour", "Quay"},
            new String[]{"Tide", "Pearl", "Salt", "Shell", "Kelp", "Reef", "Drift", "Gull"},
            new String[]{"runner", "caller", "net", "oar", "song", "keel", "wake", "line"});
    private static final Lang SPORE = new Lang(
            new String[]{"Mu", "Pa", "Lo", "Fi", "Hy", "Sio", "Gil", "Bul", "Cep", "Om", "Ru", "Tri", "Vel", "Zu", "Ny"},
            new String[]{"lo", "ma", "pha", "ri", "su", "ce", "mi", "yo", "lu"},
            new String[]{"pha", "cel", "ra", "mo", "lith", "bus", "ella", "ix", "ume", "ola", "spor"},
            new String[]{"cap", "gill", "spore", "bloom", "mire", "stalk", "ring", "veil"},
            new String[]{"Rings", "Caps", "Gardens", "Veil", "Bloom"},
            new String[]{"Gill", "Spore", "Cap", "Stalk", "Mycel", "Puff", "Glow", "Veil"},
            new String[]{"tender", "brew", "drift", "grow", "dream", "root", "light", "cup"});
    private static final Lang GLIMMER = new Lang(
            new String[]{"Ae", "Cy", "Ilu", "Kae", "Lux", "Myr", "Oph", "Qua", "Syl", "Ver", "Zir", "Ama", "Bery", "Cel", "Ira"},
            new String[]{"a", "e", "i", "y", "ae", "io", "ri"},
            new String[]{"ra", "lis", "thys", "ne", "ria", "sel", "lon", "vae", "tis", "dra", "line"},
            new String[]{"spire", "prism", "facet", "glimmer", "crown", "lumen", "vale", "sanctum"},
            new String[]{"Spires", "Prism", "Facets", "Lumen", "Crown"},
            new String[]{"Facet", "Prism", "Quartz", "Glint", "Shard", "Lumen", "Opal", "Beryl"},
            new String[]{"cut", "light", "wright", "song", "glow", "mark", "spark", "lens"});
    private static final Lang STONE = new Lang(
            new String[]{"Bor", "Dar", "Grun", "Hal", "Kel", "Mor", "Rag", "Sten", "Tor", "Ulf", "Varn", "Bj", "Eg", "Ham", "Ol"},
            new String[]{"a", "e", "o", "u", "ri", "da", "ma"},
            new String[]{"ald", "rik", "mund", "gar", "stein", "helm", "dis", "run", "mar", "vik", "olf"},
            new String[]{"crag", "fell", "stair", "watch", "hold", "peak", "cairn", "ledge"},
            new String[]{"Crag", "Stairs", "Watch", "Cairn", "Ledges"},
            new String[]{"Stone", "Crag", "Granite", "Ridge", "Boulder", "Cairn", "Slate", "Peak"},
            new String[]{"step", "carver", "climber", "shield", "rope", "watcher", "mason", "hold"});
    private static final Lang AMBER = new Lang(
            new String[]{"Al", "Bel", "Cal", "Dor", "Em", "Fel", "Hon", "Lor", "Mel", "Pen", "Ros", "Sun", "Tess", "Val", "Wyn"},
            new String[]{"a", "e", "i", "o", "en", "ar", "ey"},
            new String[]{"ric", "lia", "ton", "ford", "ena", "win", "bert", "ella", "son", "ham", "ina"},
            new String[]{"field", "market", "ford", "stead", "gold", "orchard", "cross", "bridge"},
            new String[]{"Market", "Orchards", "Fields", "Crossing", "Square"},
            new String[]{"Honey", "Barley", "Amber", "Apple", "Golden", "Sweet", "Merry", "Penny"},
            new String[]{"field", "cup", "bake", "well", "market", "thread", "harvest", "loaf"});
    private static final Lang ASTRAL = new Lang(
            new String[]{"Ast", "Cae", "Eos", "Hel", "Ith", "Lyr", "Nov", "Ori", "Sel", "Thal", "Vey", "Aur", "Cyg", "Esp", "Nym"},
            new String[]{"a", "e", "i", "ae", "ia", "io", "eu"},
            new String[]{"rae", "lis", "thea", "nor", "vel", "ion", "ys", "ane", "mir", "ora", "eth"},
            new String[]{"vault", "spire", "zenith", "lumen", "aster", "halo", "nadir", "dawn"},
            new String[]{"Observatory", "Halls", "Vault", "Zenith", "Aster"},
            new String[]{"Star", "Silver", "Halo", "Moon", "Dawn", "Comet", "Aurora", "Nadir"},
            new String[]{"gazer", "reader", "keeper", "sight", "light", "chart", "veil", "song"});

    private static Lang lang(Civilization c) {
        return switch (c) {
            case ROOTFOLK -> ROOT;
            case FORGEKIN -> FORGE;
            case TIDE_DWELLERS -> TIDE;
            case SPOREFOLK -> SPORE;
            case GLIMMERKIN -> GLIMMER;
            case STONEBORN -> STONE;
            case AMBERFOLK -> AMBER;
            case ASTRALS -> ASTRAL;
        };
    }

    private static String pick(String[] a, Random r) {
        return a[r.nextInt(a.length)];
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** Settlement name, e.g. "Mossbrook", "Kragforge", "Saltmere Landing". */
    public static String settlement(Civilization c, long seed) {
        Lang l = lang(c);
        Random r = new Random(seed);
        int form = r.nextInt(4);
        String base = form == 0 ? pick(l.starts, r) + pick(l.ends, r) : pick(l.familyA, r);
        String name = form == 0 ? base : base + pick(l.placeSuffixes, r);
        if (r.nextInt(5) == 0) {
            name = cap(name) + " " + pick(l.placeWords, r);
        }
        return cap(name);
    }

    /** Personal name "Given Family". */
    public static String person(Civilization c, long seed) {
        Lang l = lang(c);
        Random r = new Random(seed);
        String given = pick(l.starts, r) + (r.nextInt(3) == 0 ? pick(l.mids, r) : "") + pick(l.ends, r);
        String family = pick(l.familyA, r) + pick(l.familyB, r);
        return cap(given.toLowerCase(Locale.ROOT)) + " " + cap(family.toLowerCase(Locale.ROOT));
    }
}
