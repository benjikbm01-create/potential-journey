package dev.underdeep.boss;

import dev.underdeep.explore.ExplorationMap;
import dev.underdeep.explore.Explorations;
import dev.underdeep.explore.Site;
import dev.underdeep.guide.Advancements;
import dev.underdeep.registry.Reg;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stage 10 items: the six Sign-stones, the Keystone of Silence and the guardians' materials. */
public final class BossItems {
    private BossItems() {
    }

    public static final String[] SIGNS = {"sign_spiral", "sign_eye", "sign_wave", "sign_flame", "sign_star", "sign_key"};
    public static final String KEYSTONE = "keystone_silence";
    public static final String[] MATERIALS = {"thunderhide", "mycelial_heart", "choir_nacre", "rimefang_pelt", "galvanic_core", "cinder_scale",
            "everlit_wick", "resonant_crystal", "abyssal_pearl", "silent_bellmetal", "heartwood_resin", "seamglass", "somnal_ember"};
    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    public static void init() {
        for (String s : SIGNS) {
            ITEMS.put(s, Reg.item(s, new SignItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE).fireproof())));
        }
        ITEMS.put(KEYSTONE, Reg.item(KEYSTONE, new KeystoneItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())));
        for (String m : MATERIALS) {
            ITEMS.put(m, Reg.item(m, new Item(new Item.Settings().rarity(Rarity.UNCOMMON).fireproof())));
        }
        for (String sgn : SIGNS) {
            dev.underdeep.guide.Uses.add(ITEMS.get(sgn), "belongs in an old door with seven sockets");
        }
        dev.underdeep.guide.Uses.add(ITEMS.get(KEYSTONE), "use it to hear where it belongs; it opens an old door together with three Signs");
        for (String m : new String[]{"heartwood_resin", "seamglass", "somnal_ember"}) {
            dev.underdeep.guide.Uses.add(ITEMS.get(m), "rare material: merchants and scholars pay well for it; a trophy of a great fight");
        }
    }

    public static class SignItem extends Item {
        public SignItem(Settings s) {
            super(s);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.literal("One of the Signs of an old, old door.").formatted(Formatting.GRAY, Formatting.ITALIC));
        }
    }

    /** The keystone hums towards the place it belongs. */
    public static class KeystoneItem extends Item {
        public KeystoneItem(Settings s) {
            super(s);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.literal("It hums. Hold it up and listen.").formatted(Formatting.GRAY, Formatting.ITALIC));
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack st = user.getStackInHand(hand);
            if (!(world instanceof ServerWorld w) || !(user instanceof ServerPlayerEntity p)) {
                return TypedActionResult.success(st, world.isClient);
            }
            Site s = Lairs.site(Explorations.of(w), "somnarch");
            if (s == null) {
                p.sendMessage(Text.literal("The keystone is silent here.").formatted(Formatting.GRAY), true);
                return TypedActionResult.fail(st);
            }
            Vec3d to = new Vec3d(s.x() - p.getX(), 0, s.z() - p.getZ());
            double dist = to.length();
            Vec3d d = to.normalize();
            for (int i = 1; i <= 12; i++) {
                w.spawnParticles(p, ParticleTypes.END_ROD, true, p.getX() + d.x * i * 0.7, p.getEyeY() - 0.3, p.getZ() + d.z * i * 0.7, 1, 0, 0, 0, 0);
            }
            String depth = s.y() < p.getY() - 30 ? ", far below" : s.y() > p.getY() + 30 ? ", above you" : "";
            p.sendMessage(Text.literal("The keystone hums toward the " + dev.underdeep.explore.Discovery.compass(d.x, d.z) + depth + " — about "
                    + (int) (Math.round(dist / 50.0) * 50) + " blocks.").formatted(Formatting.LIGHT_PURPLE), true);
            w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.8f, 0.5f + (float) Math.min(1, 200 / Math.max(1, dist)));
            p.getItemCooldownManager().set(this, 40);
            Advancements.event(p, "keystone_listened", "somnarch");
            dev.underdeep.guide.Handbook.push(p, List.of("progress:keystone"));
            return TypedActionResult.success(st);
        }
    }

    public static boolean isSign(ItemStack s) {
        return s.getItem() instanceof SignItem || s.getItem() instanceof KeystoneItem;
    }

    public static ExplorationMap map(ServerWorld w) {
        return Explorations.of(w);
    }
}
