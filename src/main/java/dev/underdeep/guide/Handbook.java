package dev.underdeep.guide;

import dev.underdeep.Underdeep;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.registry.Reg;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.WildlifeJournal;
import dev.underdeep.wildlife.WildlifeRegistry;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Underdeep Handbook, server side (Stage 8): the handbook item (given once on first arrival in an Underdeep
 * world; the H key works without it), and the data the client cannot compute itself: how each item is obtained and
 * used (from the {@link Obtainability} audit over the live server registries and loot tables), and what this player
 * has discovered (creatures seen, peoples met, ecosystems visited, sensitive items obtained). Discoveries are per
 * player; sensitive content is never sent before it is discovered.
 */
public final class Handbook {
    private Handbook() {
    }

    public static Item HANDBOOK;
    /** Client hook: opens the screen (set by the client initializer). */
    public static Runnable openClient = () -> {
    };

    public record ItemInfo(String id, boolean reachable, List<String> sources, List<String> uses) {
    }

    public record Data(List<ItemInfo> items, List<String> discovered) implements CustomPayload {
        public static final Id<Data> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "handbook_data"));
        public static final PacketCodec<RegistryByteBuf, Data> CODEC = PacketCodec.of((d, b) -> {
            b.writeVarInt(d.items.size());
            for (ItemInfo i : d.items) {
                b.writeString(i.id);
                b.writeBoolean(i.reachable);
                b.writeVarInt(i.sources.size());
                i.sources.forEach(x -> b.writeString(x, 512));
                b.writeVarInt(i.uses.size());
                i.uses.forEach(x -> b.writeString(x, 512));
            }
            b.writeVarInt(d.discovered.size());
            d.discovered.forEach(b::writeString);
        }, b -> {
            int n = Math.min(8192, b.readVarInt());
            List<ItemInfo> items = new ArrayList<>(n);
            for (int k = 0; k < n; k++) {
                String id = b.readString();
                boolean r = b.readBoolean();
                int s = Math.min(16, b.readVarInt());
                List<String> src = new ArrayList<>();
                for (int j = 0; j < s; j++) {
                    src.add(b.readString(512));
                }
                int u = Math.min(32, b.readVarInt());
                List<String> uses = new ArrayList<>();
                for (int j = 0; j < u; j++) {
                    uses.add(b.readString(512));
                }
                items.add(new ItemInfo(id, r, src, uses));
            }
            int dn = Math.min(4096, b.readVarInt());
            List<String> disc = new ArrayList<>();
            for (int k = 0; k < dn; k++) {
                disc.add(b.readString());
            }
            return new Data(items, disc);
        });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Incremental discoveries. */
    public record Discover(List<String> keys) implements CustomPayload {
        public static final Id<Discover> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "handbook_discover"));
        public static final PacketCodec<RegistryByteBuf, Discover> CODEC = PacketCodec.of((d, b) -> {
            b.writeVarInt(d.keys.size());
            d.keys.forEach(b::writeString);
        }, b -> {
            int n = Math.min(256, b.readVarInt());
            List<String> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                l.add(b.readString());
            }
            return new Discover(l);
        });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client cache. */
    public static final java.util.Map<String, ItemInfo> CLIENT_ITEMS = new java.util.HashMap<>();
    public static final Set<String> CLIENT_DISCOVERED = new LinkedHashSet<>();

    public static class HandbookItem extends Item {
        public HandbookItem(Settings s) {
            super(s);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            if (world.isClient) {
                openClient.run();
            }
            return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
        }
    }

    private static List<ItemInfo> cached;

    public static void init() {
        HANDBOOK = Reg.item("underdeep_handbook", new HandbookItem(new Item.Settings().maxCount(1)));
        Uses.add(HANDBOOK, "opens the Underdeep Handbook (or press H)");
        PayloadTypeRegistry.playS2C().register(Data.ID, Data.CODEC);
        PayloadTypeRegistry.playS2C().register(Discover.ID, Discover.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            cached = null;
            Obtainability.invalidate();
            items(s);
            dev.underdeep.equipment.Artisan.learnCraftingRecipes(s);
        });
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> onJoin(h.player)); // synchronous: migration before the first advancement sync
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 11) {
                return;
            }
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                watch(p);
            }
        });
    }

    public static List<ItemInfo> itemsForTest(MinecraftServer server) {
        return items(server);
    }

    public static void watchForTest(ServerPlayerEntity p) {
        watch(p);
    }

    static List<ItemInfo> items(MinecraftServer server) {
        if (cached == null) {
            List<ItemInfo> out = new ArrayList<>();
            for (Obtainability.Entry e : Obtainability.get(server).values()) {
                Identifier id = Registries.ITEM.getId(e.item);
                if (!id.getNamespace().equals(Underdeep.MOD_ID) && e.sources.isEmpty() && e.usedIn.isEmpty()) {
                    continue;
                }
                Sensitivity.Kind k = Sensitivity.kind(e.item);
                if (k == Sensitivity.Kind.TECHNICAL || k == Sensitivity.Kind.DEFERRED) {
                    continue;
                }
                List<String> src = new ArrayList<>();
                Set<String> seen = new LinkedHashSet<>();
                for (Obtainability.Source s : e.sources) {
                    String line = describe(s);
                    if (seen.add(line) && src.size() < 8) {
                        src.add(line);
                    }
                }
                List<String> uses = new ArrayList<>(e.uses);
                out.add(new ItemInfo(id.toString(), e.reachable, src, uses.subList(0, Math.min(16, uses.size()))));
            }
            cached = out;
        }
        return cached;
    }

    /** Player-facing source line ("Mine fossil ore (any pickaxe) in Petrified Forest, The Abyss"). */
    static String describe(Obtainability.Source s) {
        String what = switch (s.kind()) {
            case "mine" -> "Mine " + name(s.detail().replace("break ", "").replaceAll(" \\(.*", "")) + (s.detail().contains("declared") ? "" : "");
            case "grow" -> "Grow it: " + s.detail().replace("break ", "harvest ");
            case "harvest" -> s.detail().substring(0, 1).toUpperCase() + s.detail().substring(1);
            case "creature" -> "Dropped by " + name(s.detail().replace("dropped by ", ""));
            case "product" -> s.detail().substring(0, 1).toUpperCase() + s.detail().substring(1);
            case "fish" -> "Fishing";
            case "trap" -> "Fish trap with bait";
            case "trade" -> "Bought from settlements";
            case "special" -> s.detail().substring(0, 1).toUpperCase() + s.detail().substring(1);
            case "fluid" -> s.detail().substring(0, 1).toUpperCase() + s.detail().substring(1);
            case "station" -> "Made at the " + s.requires();
            case "crafting" -> "Crafted" + (s.requires().isEmpty() ? "" : " (crafting table)");
            default -> capital(s.kind()) + (s.requires().isEmpty() ? "" : " (" + s.requires() + ")");
        };
        if (!s.requires().isEmpty() && (s.kind().equals("mine") || s.kind().equals("grow"))) {
            what += " (" + s.requires() + ")";
        }
        if (!s.regions().isEmpty() && !s.kind().equals("crafting")) {
            List<String> r = s.regions().stream().map(dev.underdeep.equipment.EquipmentEffects::pretty).limit(5).toList();
            what += " in " + String.join(", ", r) + (s.regions().size() > 5 ? "..." : "");
        }
        return what;
    }

    static String name(String id) {
        try {
            Identifier i = Identifier.of(id.trim());
            if (Registries.BLOCK.containsId(i)) {
                return Registries.BLOCK.get(i).getName().getString();
            }
            if (Registries.ENTITY_TYPE.containsId(i)) {
                return Registries.ENTITY_TYPE.get(i).getName().getString();
            }
        } catch (Exception ignored) {
            // not an id
        }
        return id;
    }

    static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static void onJoin(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        Advancements.Stats st = Advancements.stats(p);
        if (!st.counts.containsKey("handbook_given") && UnderdeepConfig.get().handbookOnJoin
                && UnderdeepWorlds.isUnderdeep(server.getOverworld())) {
            st.counts.put("handbook_given", 1);
            ItemStack book = new ItemStack(HANDBOOK);
            if (!p.giveItemStack(book)) {
                p.dropItem(book, false);
            }
            p.sendMessage(Text.literal("You carry an Underdeep Handbook. Press H to read it any time.").formatted(Formatting.GOLD), false);
        }
        Migration.migrate(p);
        if (ServerPlayNetworking.canSend(p, Data.ID)) {
            ServerPlayNetworking.send(p, new Data(items(server), new ArrayList<>(discovered(p))));
        }
    }

    /** Everything this player has discovered (keys: species:, civ:, region:, item:). */
    public static Set<String> discovered(ServerPlayerEntity p) {
        Set<String> out = new LinkedHashSet<>();
        MinecraftServer server = p.getServer();
        for (CreatureSpecies sp : WildlifeRegistry.all()) {
            if (WildlifeJournal.knows(p, sp)) {
                out.add("species:" + sp.id);
            }
        }
        for (SettlementRecord r : SettlementState.get(server.getOverworld()).all().values()) {
            if (r.discoveredBy.contains(p.getUuid())) {
                out.add("civ:" + r.civ.id());
            }
        }
        Advancements.Stats st = Advancements.stats(p);
        st.subjects.getOrDefault("region_visited", Set.of()).forEach(r -> out.add("region:" + r));
        st.subjects.getOrDefault("obtained", Set.of()).forEach(i -> out.add("item:" + i));
        // Stage 9: discovered site families, hidden rooms, records and collected artifacts (personal)
        dev.underdeep.explore.ExplorerData xd = dev.underdeep.explore.ExplorerData.of(p);
        dev.underdeep.explore.ExplorationMap xm = p.getWorld() instanceof ServerWorld sw ? dev.underdeep.explore.Explorations.of(sw) : null;
        for (long id : xd.sites.keySet()) {
            dev.underdeep.explore.Site s = xm == null ? null : xm.byId(id);
            if (s != null) {
                out.add("site:" + s.family().id());
            }
        }
        xd.secrets.forEach(k -> out.add("secret:" + k.substring(k.indexOf(':') + 1)));
        xd.records.forEach(r -> out.add("record:" + r));
        xd.artifacts.forEach(a -> out.add("item:underdeep:" + a));
        return out;
    }

    /** Per-second watcher: ecosystem visits and sensitive items obtained. */
    static void watch(ServerPlayerEntity p) {
        if (p.isSpectator()) {
            return;
        }
        Advancements.Stats st = Advancements.stats(p);
        List<String> fresh = new ArrayList<>();
        if (p.getWorld() instanceof ServerWorld sw) {
            UnderdeepTerrain t = UnderdeepWorlds.terrain(sw);
            if (t != null) {
                RegionType r = t.regionAt(p.getBlockX(), p.getBlockY(), p.getBlockZ());
                if (r != null && !st.subjects.getOrDefault("region_visited", Set.of()).contains(r.id())) {
                    Advancements.event(p, "region_visited", r.id());
                    fresh.add("region:" + r.id());
                }
            }
        }
        Set<String> obtained = st.subjects.getOrDefault("obtained", Set.of());
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && Sensitivity.kind(s.getItem()) == Sensitivity.Kind.SENSITIVE) {
                String id = Registries.ITEM.getId(s.getItem()).toString();
                if (!obtained.contains(id)) {
                    Advancements.event(p, "obtained", id);
                    fresh.add("item:" + id);
                    obtained = st.subjects.getOrDefault("obtained", Set.of());
                }
            }
        }
        push(p, fresh);
    }

    public static void push(ServerPlayerEntity p, List<String> keys) {
        if (!keys.isEmpty() && ServerPlayNetworking.canSend(p, Discover.ID)) {
            ServerPlayNetworking.send(p, new Discover(keys));
        }
    }
}
