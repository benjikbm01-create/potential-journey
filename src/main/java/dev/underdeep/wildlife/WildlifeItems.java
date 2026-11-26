package dev.underdeep.wildlife;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.underdeep.registry.Reg;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Animal resources (Stage 3), riding harness, field journal and spawn eggs. Many resources are
 * renewable without killing: shed antlers/plumes/shells/husks/scales, shearing, milking, eggs.
 */
public final class WildlifeItems {
    private static Item plain(String n) {
        return Reg.item(n, new Item(new Item.Settings()));
    }

    private static Item food(String n, int nut, float sat, StatusEffectInstance fx, float chance) {
        FoodComponent.Builder b = new FoodComponent.Builder().nutrition(nut).saturationModifier(sat);
        if (fx != null) {
            b.statusEffect(fx, chance);
        }
        return Reg.item(n, new Item(new Item.Settings().food(b.build())));
    }

    public static final Item GAME_MEAT = food("game_meat", 3, 0.3f, null, 0);
    public static final Item COOKED_GAME_MEAT = food("cooked_game_meat", 8, 0.8f, null, 0);
    public static final Item FISH_FILLET = food("fish_fillet", 2, 0.2f, null, 0);
    public static final Item COOKED_FISH_FILLET = food("cooked_fish_fillet", 5, 0.6f, null, 0);
    public static final Item SHELLMEAT = food("shellmeat", 2, 0.2f, new StatusEffectInstance(StatusEffects.HUNGER, 200, 0), 0.4f);
    public static final Item COOKED_SHELLMEAT = food("cooked_shellmeat", 6, 0.7f, null, 0);
    public static final Item CREATURE_EGG = food("creature_egg", 2, 0.3f, null, 0);
    public static final Item HIDE = plain("hide");
    public static final Item FUR_TUFT = plain("fur_tuft");
    public static final Item PLUME = plain("plume");
    public static final Item SHELL_FRAGMENT = plain("shell_fragment");
    public static final Item HORN = plain("horn");
    public static final Item SCALE = plain("scale");
    public static final Item SILK_STRAND = plain("silk_strand");
    public static final Item VENOM_GLAND = plain("venom_gland");
    public static final Item CREATURE_OIL = plain("creature_oil");
    public static final Item GLOW_GLAND = plain("glow_gland");
    public static final Item CRYSTAL_HUSK = plain("crystal_husk");
    public static final Item HARNESS = Reg.item("harness", new Item(new Item.Settings().maxCount(1)));
    public static final Item FIELD_JOURNAL = Reg.item("field_journal", new JournalItem(new Item.Settings().maxCount(1)));

    private WildlifeItems() {
    }

    public static void init() {
        for (CreatureSpecies s : WildlifeRegistry.all()) {
            int[] c = eggColors(s.id);
            Reg.item(s.id + "_spawn_egg", new SpawnEggItem(WildlifeRegistry.type(s.id), c[0], c[1], new Item.Settings()));
        }
    }

    private static int[] eggColors(String id) {
        try (InputStream in = WildlifeItems.class.getResourceAsStream("/assets/underdeep/creatures/" + id + ".json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                var a = o.getAsJsonArray("egg");
                return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()};
            }
        } catch (Exception ignored) {
            // fall through to defaults
        }
        return new int[]{0x806040, 0x403020};
    }

    /** Right-click to read your wildlife discoveries; sneak-right-click for the next page. */
    private static final java.util.Map<java.util.UUID, Integer> PAGES = new java.util.concurrent.ConcurrentHashMap<>();

    public static final class JournalItem extends Item {
        public JournalItem(Settings s) {
            super(s);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (user instanceof ServerPlayerEntity sp) {
                int page = PAGES.getOrDefault(sp.getUuid(), 0);
                if (user.isSneaking()) {
                    page++;
                }
                var lines = WildlifeJournal.render(sp, page);
                if (lines.size() <= 1 && page > 0) {
                    page = 0;
                    lines = WildlifeJournal.render(sp, 0);
                }
                PAGES.put(sp.getUuid(), page);
                for (Text t : lines) {
                    sp.sendMessage(t, false);
                }
            }
            return TypedActionResult.success(stack, world.isClient);
        }
    }
}
