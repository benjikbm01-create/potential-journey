package dev.underdeep.explore;

import dev.underdeep.guide.Advancements;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Stage 9 reward policies (see Canvas.Policy):
 * <ul>
 *   <li>SHARED — ordinary containers with a site loot table, rolled once for everybody (vanilla behaviour);</li>
 *   <li>PERSONAL — an expedition coffer: every player takes their own share once (claim {@code cache:i:uuid}), so a group
 *   is rewarded fairly and nobody can empty it for the others;</li>
 *   <li>UNIQUE — a relic pedestal: one claim per site, and a unique artifact is only ever awarded once per world;</li>
 *   <li>KEY — a key pedestal: each player may take one key for this site's lock (so a lost key never blocks progress;
 *   the lock opens once, so extra keys give nothing).</li>
 * </ul>
 * Claims are checked and recorded in the same server tick before anything is given, so simultaneous use cannot
 * duplicate a reward. Claims persist in {@link ExplorationState} and are independent of puzzle resets.
 */
public final class Rewards {
    private Rewards() {
    }

    public static void use(ServerWorld w, BlockPos pos, BlockState state, ServerPlayerEntity p) {
        Mechanisms.Ctx c = Mechanisms.ctx(w, pos);
        SiteLayout.Ref r = c == null ? null : c.layout().first(pos, SiteLayout.Kind.CACHE);
        if (r == null) {
            p.sendMessage(Text.literal("Empty. Whatever it held is long gone.").formatted(Formatting.GRAY), true);
            return;
        }
        Canvas.CacheSpec cs = c.layout().canvas.caches.get(r.index());
        String base = "cache:" + r.index();
        switch (cs.policy()) {
            case PERSONAL -> {
                String claim = base + ":" + p.getUuid();
                if (c.site().claims.contains(claim)) {
                    long others = c.site().claims.stream().filter(x -> x.startsWith(base + ":")).count();
                    p.sendMessage(Text.literal("You have already taken your share" + (others > 1 ? " (" + others + " explorers have)." : "."))
                            .formatted(Formatting.GRAY), true);
                    return;
                }
                c.site().claims.add(claim);
                c.state().markDirty();
                List<ItemStack> loot = roll(w, pos, p, cs.loot());
                give(p, loot);
                w.playSound(null, pos, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 1f, 1.1f);
                p.sendMessage(Text.literal("You take your share of the coffer.").formatted(Formatting.GOLD), true);
                Advancements.event(p, "coffer_opened", c.s().family().id());
                for (ItemStack s : loot) {
                    found(p, s);
                }
            }
            case UNIQUE -> {
                String art = cs.loot().startsWith("artifact:") ? cs.loot().substring(9) : cs.loot();
                if (c.site().claims.contains(base) || c.state().uniques.contains(art)) {
                    p.sendMessage(Text.literal("The pedestal is bare.").formatted(Formatting.GRAY), true);
                    if (state.get(ExploreBlocks.FULL)) {
                        w.setBlockState(pos, state.with(ExploreBlocks.FULL, false), Block.NOTIFY_ALL);
                    }
                    return;
                }
                c.site().claims.add(base);
                c.state().uniques.add(art);
                c.state().markDirty();
                w.setBlockState(pos, state.with(ExploreBlocks.FULL, false), Block.NOTIFY_ALL);
                Item it = Registries.ITEM.get(Identifier.of(art));
                ItemStack s = new ItemStack(it);
                give(p, List.of(s));
                w.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1f);
                p.sendMessage(Text.literal("You lift " + s.getName().getString() + " from the pedestal.").formatted(Formatting.LIGHT_PURPLE), false);
                found(p, s);
                Advancements.event(p, "unique_artifact", it == null ? art : Registries.ITEM.getId(it).getPath());
            }
            case KEY -> {
                String claim = base + ":" + p.getUuid();
                if (c.site().claims.contains(claim)) {
                    p.sendMessage(Text.literal("You already took a key from here.").formatted(Formatting.GRAY), true);
                    return;
                }
                c.site().claims.add(claim);
                c.state().markDirty();
                give(p, List.of(ExploreItems.key(c.s())));
                w.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1f, 1.2f);
                p.sendMessage(Text.literal("You take a heavy key from its hook.").formatted(Formatting.GOLD), true);
            }
            default -> {
            }
        }
    }

    public static List<ItemStack> roll(ServerWorld w, BlockPos pos, ServerPlayerEntity p, String table) {
        LootTable t = w.getServer().getReloadableRegistries().getLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(table)));
        LootContextParameterSet params = new LootContextParameterSet.Builder(w).add(LootContextParameters.ORIGIN, pos.toCenterPos())
                .add(LootContextParameters.THIS_ENTITY, p).luck(p.getLuck()).build(LootContextTypes.CHEST);
        return t.generateLoot(params, w.random.nextLong());
    }

    static void give(ServerPlayerEntity p, List<ItemStack> items) {
        for (ItemStack s : items) {
            ItemStack copy = s.copy();
            if (!p.getInventory().insertStack(copy) && !copy.isEmpty()) {
                p.dropItem(copy, false);
            }
        }
    }

    /** Records an artifact or relic in the finder's collection (journal) when it is one. */
    public static void found(ServerPlayerEntity p, ItemStack s) {
        String id = Registries.ITEM.getId(s.getItem()).getPath();
        if (ExploreContent.ARTIFACTS.containsKey(id) && ExplorerData.of(p).artifacts.add(id)) {
            p.sendMessage(Text.literal("Added to your collection: " + s.getName().getString()).formatted(Formatting.LIGHT_PURPLE), false);
            Advancements.event(p, "artifact_found", id);
            Advancements.event(p, "artifact_" + ExploreContent.ARTIFACTS.get(id).rarity(), id);
            dev.underdeep.guide.Handbook.push(p, List.of("item:underdeep:" + id));
            Explorations.sendJournal(p);
        }
    }
}
