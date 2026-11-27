package dev.underdeep.explore;

import dev.underdeep.guide.Advancements;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeverBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Stage 9 puzzle framework. A puzzle is a {@link Canvas.PuzzleSpec} on a site (type, element positions, solution,
 * seals). Interactions arrive here from mechanism blocks; the puzzle is found from the position through the site
 * layout, state lives in {@link ExplorationState} (per site instance), and solving opens the seals for everybody.
 * Solved state and reward claims are separate: resetting or replaying a puzzle never re-arms a reward.
 * <p>
 * Mechanisms of unsolved puzzles cannot be broken (so a puzzle can never become unsolvable); everything else in a
 * site, including solved mechanisms, can be mined. Offerings use common items and a wrong item is never taken.
 */
public final class Mechanisms {
    private Mechanisms() {
    }

    /** Items a socket may ask for (solution values index this list); each appears on a carved relief clue. */
    public static final List<Item> OFFERINGS = List.of(Items.COD, Items.BREAD, Items.EMERALD, Items.BONE, Items.AMETHYST_SHARD, Items.GLOW_BERRIES);
    /** Materials a mosaic slot may ask for. */
    public static final List<Block> MOSAIC = List.of(Blocks.WHITE_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.BLACK_TERRACOTTA,
            Blocks.YELLOW_TERRACOTTA, Blocks.RED_TERRACOTTA);
    public static final int GATE_TICKS = 240;

    public record Ctx(ServerWorld world, ExplorationMap map, SiteLayout layout, ExplorationState state, ExplorationState.SiteState site) {
        public Site s() {
            return layout.site;
        }
    }

    @Nullable
    public static Ctx ctx(ServerWorld w, BlockPos pos) {
        ExplorationMap map = Explorations.of(w);
        if (map == null) {
            return null;
        }
        Site s = map.siteAt(pos, 2);
        if (s == null) {
            return null;
        }
        ExplorationState st = ExplorationState.get(w.getServer());
        return new Ctx(w, map, map.layout(s), st, st.site(s.id()));
    }

    public static void init() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!(world instanceof ServerWorld sw) || player.isCreative() || !protectedBlock(state)) {
                return true;
            }
            Ctx c = ctx(sw, pos);
            if (c == null) {
                return true;
            }
            SiteLayout.Ref r = c.layout.first(pos, SiteLayout.Kind.ELEMENT);
            if (r != null && !c.site.puzzle(r.index()).solved) {
                player.sendMessage(Text.literal("It is part of a mechanism that has not yet been solved. It will not come loose.").formatted(Formatting.GRAY), true);
                return false;
            }
            return true;
        });
    }

    static boolean protectedBlock(BlockState s) {
        Block b = s.getBlock();
        return b instanceof ExploreBlocks.Mechanism || b instanceof ExploreBlocks.AncientLeverBlock || b == ExploreBlocks.GLYPH_TILE
                || b == ExploreBlocks.MOSAIC_SLOT || MOSAIC.contains(b);
    }

    // ------------------------------------------------------------------ interactions

    /** Use of a mechanism (empty hand or with an item). Returns true when the interaction was consumed. */
    public static boolean use(ServerWorld w, BlockPos pos, BlockState state, ServerPlayerEntity p, ItemStack stack, Hand hand) {
        Ctx c = ctx(w, pos);
        SiteLayout.Ref r = c == null ? null : c.layout.first(pos, SiteLayout.Kind.ELEMENT);
        if (r == null) {
            if (state.isOf(ExploreBlocks.ANCIENT_LEVER)) {
                return false;
            }
            p.sendMessage(Text.literal("The mechanism is inert; whatever it once worked is gone.").formatted(Formatting.GRAY), true);
            return false;
        }
        Canvas.PuzzleSpec spec = c.layout.canvas.puzzles.get(r.index());
        ExplorationState.PuzzleState ps = c.site.puzzle(r.index());
        int e = r.element();
        boolean solvedBefore = ps.solved;
        boolean handled = true;
        switch (spec.type()) {
            case "dial_match" -> {
                int sym = (state.get(ExploreBlocks.SYMBOL) + (p.isSneaking() ? 5 : 1)) % 6;
                w.setBlockState(pos, state.with(ExploreBlocks.SYMBOL, sym), Block.NOTIFY_ALL);
                w.playSound(null, pos, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.5f, 1.2f + sym * 0.08f);
                if (!ps.solved && allMatch(c, spec, (i, s) -> s.isOf(ExploreBlocks.RUNE_DIAL) && s.get(ExploreBlocks.SYMBOL) == spec.solution()[i])) {
                    solve(c, r.index(), p);
                }
            }
            case "brazier_all", "brazier_order", "extinguish" -> handled = brazier(c, spec, ps, r, pos, state, p, stack, hand);
            case "offering" -> {
                if (state.get(ExploreBlocks.FULL)) {
                    p.sendMessage(Text.literal("The socket already holds its offering.").formatted(Formatting.GRAY), true);
                    return true;
                }
                Item want = OFFERINGS.get(spec.solution()[e] % OFFERINGS.size());
                if (stack.isEmpty()) {
                    p.sendMessage(Text.literal("An empty socket. The carving above it shows what belongs here.").formatted(Formatting.GRAY), true);
                } else if (!stack.isOf(want)) {
                    p.sendMessage(Text.literal("The socket will not take that.").formatted(Formatting.GRAY), true);
                    w.playSound(null, pos, SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.6f, 0.6f);
                } else {
                    if (!p.isCreative()) {
                        stack.decrement(1);
                    }
                    w.setBlockState(pos, state.with(ExploreBlocks.FULL, true), Block.NOTIFY_ALL);
                    w.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 0.7f, 1.1f);
                    if (!ps.solved && allMatch(c, spec, (i, s) -> s.isOf(ExploreBlocks.OFFERING_SOCKET) && s.get(ExploreBlocks.FULL))) {
                        solve(c, r.index(), p);
                    }
                }
            }
            case "chime_order" -> {
                int size = state.get(ExploreBlocks.SIZE);
                w.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1f, 0.6f + size * 0.45f);
                w.spawnParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2 + size * 0.3, pos.getZ() + 0.5, 1, 0, 0, 0, size / 2.0);
                sequence(c, spec, ps, r.index(), e, p, pos);
            }
            case "lever_pattern" -> {
                BlockState now = w.getBlockState(pos);
                w.playSound(null, pos, SoundEvents.BLOCK_COPPER_DOOR_CLOSE, SoundCategory.BLOCKS, 0.4f, 1.4f);
                if (!ps.solved && allMatch(c, spec, (i, s) -> s.isOf(ExploreBlocks.ANCIENT_LEVER) && (s.get(LeverBlock.POWERED) ? 1 : 0) == spec.solution()[i])) {
                    solve(c, r.index(), p);
                }
                handled = now.isOf(ExploreBlocks.ANCIENT_LEVER);
            }
            case "basin_fill" -> {
                int lvl = state.get(ExploreBlocks.LEVEL);
                int add = stack.isOf(Items.WATER_BUCKET) ? 3 : isWaterBottle(stack) ? 1 : 0;
                if (add == 0) {
                    p.sendMessage(Text.literal(lvl < 3 ? "A dry stone basin, carved channels lead into it." : "The basin is full.").formatted(Formatting.GRAY), true);
                    return true;
                }
                if (lvl >= 3) {
                    return true;
                }
                w.setBlockState(pos, state.with(ExploreBlocks.LEVEL, Math.min(3, lvl + add)), Block.NOTIFY_ALL);
                w.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 0.8f, 1f);
                if (!p.isCreative()) {
                    p.setStackInHand(hand, stack.isOf(Items.WATER_BUCKET) ? new ItemStack(Items.BUCKET) : new ItemStack(Items.GLASS_BOTTLE));
                }
                if (!ps.solved && allMatch(c, spec, (i, s) -> s.isOf(ExploreBlocks.STONE_BASIN) && s.get(ExploreBlocks.LEVEL) == 3)) {
                    solve(c, r.index(), p);
                }
            }
            case "mirror_beam" -> {
                if (state.isOf(ExploreBlocks.LIGHT_MIRROR)) {
                    w.setBlockState(pos, state.cycle(ExploreBlocks.SLANT), Block.NOTIFY_ALL);
                    w.playSound(null, pos, SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.BLOCKS, 0.6f, 1.3f);
                }
                if (beam(c, spec) && !ps.solved) {
                    solve(c, r.index(), p);
                }
            }
            case "memory" -> {
                if (e == 0) {
                    ps.progress = new int[]{0, 0};
                    c.state.markDirty();
                    w.scheduleBlockTick(pos, ExploreBlocks.MEMORY_PILLAR, 10);
                    w.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.8f, 1.4f);
                } else {
                    flash(w, pos, state);
                    sequence(c, spec, ps, r.index(), e, p, pos);
                }
            }
            case "mosaic" -> {
                if (e == 0 && !ps.solved) {
                    int good = 0;
                    for (int i = 1; i < spec.elements().size(); i++) {
                        Block want = MOSAIC.get(spec.solution()[i] % MOSAIC.size());
                        good += w.getBlockState(c.layout.world(spec.elements().get(i))).isOf(want) ? 1 : 0;
                    }
                    int need = spec.elements().size() - 1;
                    if (good == need) {
                        solve(c, r.index(), p);
                    } else {
                        p.sendMessage(Text.literal("The keystone does not move. " + good + " of " + need + " gaps hold the right colour and shape.")
                                .formatted(Formatting.GRAY), false);
                        w.playSound(null, pos, SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.8f, 0.5f);
                    }
                }
            }
            case "timed_gate" -> {
                for (long l : spec.seals()) {
                    BlockPos b = c.layout.world(l);
                    BlockState bs = w.getBlockState(b);
                    if (bs.isOf(ExploreBlocks.SEAL_BARS)) {
                        w.setBlockState(b, bs.with(ExploreBlocks.OPEN, true), Block.NOTIFY_ALL);
                        w.scheduleBlockTick(b, ExploreBlocks.SEAL_BARS, GATE_TICKS);
                    }
                }
                w.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1f, 0.6f);
                p.sendMessage(Text.literal("Somewhere, bars grind upward. They will not stay up long.").formatted(Formatting.YELLOW), true);
                participant(c, ps, p);
            }
            case "counterweight" -> {
                if (ps.solved) {
                    p.sendMessage(Text.literal("The beam rests level.").formatted(Formatting.GRAY), true);
                    return true;
                }
                if (stack.isEmpty() || p.isSneaking()) {
                    returnHeld(w, pos, ps);
                    c.state.markDirty();
                    p.sendMessage(Text.literal("You empty the basket. The notches on the beam mark " + spec.solution()[0] + ".").formatted(Formatting.GRAY), true);
                    return true;
                }
                ps.held.add(stack.copy());
                stack.setCount(0);
                int total = ps.held.stream().mapToInt(ItemStack::getCount).sum();
                w.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.8f, 0.8f);
                if (total == spec.solution()[0]) {
                    returnHeld(w, pos, ps);
                    solve(c, r.index(), p);
                } else if (total > spec.solution()[0]) {
                    returnHeld(w, pos, ps);
                    p.sendMessage(Text.literal("The beam slams down and the basket spills: too heavy.").formatted(Formatting.GRAY), true);
                } else {
                    p.sendMessage(Text.literal("The beam creaks but does not level (" + total + ").").formatted(Formatting.GRAY), true);
                }
                c.state.markDirty();
            }
            case "key_lock" -> {
                if (ps.solved) {
                    return true;
                }
                if (stack.isOf(ExploreItems.VAULT_KEY) && ExploreItems.keySite(stack) == c.s().id()) {
                    if (!p.isCreative()) {
                        stack.decrement(1);
                    }
                    solve(c, r.index(), p);
                } else if (stack.isOf(ExploreItems.VAULT_KEY)) {
                    p.sendMessage(Text.literal("This key was cut for another lock.").formatted(Formatting.GRAY), true);
                } else {
                    p.sendMessage(Text.literal("A heavy lock. Its key should be somewhere in this place.").formatted(Formatting.GRAY), true);
                }
            }
            default -> handled = false;
        }
        if (!solvedBefore && ps.solved) {
            c.state.markDirty();
        }
        return handled;
    }

    private static boolean isWaterBottle(ItemStack s) {
        if (!s.isOf(Items.POTION)) {
            return false;
        }
        var pc = s.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
        return pc != null && pc.matches(net.minecraft.potion.Potions.WATER);
    }

    private static boolean brazier(Ctx c, Canvas.PuzzleSpec spec, ExplorationState.PuzzleState ps, SiteLayout.Ref r, BlockPos pos, BlockState state,
                                   ServerPlayerEntity p, ItemStack stack, Hand hand) {
        ServerWorld w = c.world;
        boolean lit = state.get(ExploreBlocks.LIT);
        boolean igniter = stack.isOf(Items.FLINT_AND_STEEL) || stack.isOf(Items.FIRE_CHARGE) || stack.isOf(Items.TORCH) || stack.isOf(Items.SOUL_TORCH)
                || stack.isOf(Items.BLAZE_ROD);
        boolean doused = stack.isOf(Items.WATER_BUCKET) || isWaterBottle(stack) || stack.isIn(ItemTags.SHOVELS) || stack.isEmpty() && lit;
        if (!lit && igniter) {
            w.setBlockState(pos, state.with(ExploreBlocks.LIT, true), Block.NOTIFY_ALL);
            w.playSound(null, pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.8f, 1f);
            if (stack.isOf(Items.FLINT_AND_STEEL)) {
                stack.damage(1, p, net.minecraft.entity.LivingEntity.getSlotForHand(hand));
            } else if (stack.isOf(Items.FIRE_CHARGE) && !p.isCreative()) {
                stack.decrement(1);
            }
            if (spec.type().equals("brazier_order") && !ps.solved) {
                sequence(c, spec, ps, r.index(), r.element(), p, pos);
                if (!ps.solved && ps.progress.length > 0 && ps.progress[0] == 0) {
                    // wrong order: every brazier gutters out
                    for (long l : spec.elements()) {
                        BlockPos b = c.layout.world(l);
                        BlockState bs = w.getBlockState(b);
                        if (bs.isOf(ExploreBlocks.BRAZIER) && bs.get(ExploreBlocks.LIT)) {
                            w.setBlockState(b, bs.with(ExploreBlocks.LIT, false), Block.NOTIFY_ALL);
                            w.spawnParticles(ParticleTypes.LARGE_SMOKE, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
                        }
                    }
                }
            } else if (spec.type().equals("brazier_all") && !ps.solved
                    && allMatch(c, spec, (i, s) -> s.isOf(ExploreBlocks.BRAZIER) && s.get(ExploreBlocks.LIT))) {
                solve(c, r.index(), p);
            }
            return true;
        }
        if (lit && doused) {
            w.setBlockState(pos, state.with(ExploreBlocks.LIT, false), Block.NOTIFY_ALL);
            w.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.7f, 1f);
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
            if (spec.type().equals("extinguish") && !ps.solved
                    && allMatch(c, spec, (i, s) -> s.isOf(ExploreBlocks.BRAZIER) && !s.get(ExploreBlocks.LIT))) {
                solve(c, r.index(), p);
            }
            return true;
        }
        if (stack.isEmpty()) {
            p.sendMessage(Text.literal(lit ? "It burns steadily." : "A cold brazier. It could be lit.").formatted(Formatting.GRAY), true);
            return true;
        }
        return false;
    }

    /** Ordered puzzles: progress[0] is the next expected step; a wrong step resets. */
    private static void sequence(Ctx c, Canvas.PuzzleSpec spec, ExplorationState.PuzzleState ps, int index, int element, ServerPlayerEntity p, BlockPos pos) {
        if (ps.solved) {
            return;
        }
        if (ps.progress.length < 2) {
            ps.progress = new int[]{0, 0};
        }
        int step = ps.progress[0];
        int[] sol = spec.solution();
        participant(c, ps, p);
        if (sol[step] == element) {
            ps.progress[0] = step + 1;
            c.world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.6f, 0.8f + step * 0.2f);
            if (ps.progress[0] >= sol.length) {
                solve(c, index, p);
            }
        } else {
            ps.progress[0] = sol[0] == element ? 1 : 0;
            c.world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.7f, 0.5f);
            c.world.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.01);
        }
        c.state.markDirty();
    }

    /** Heavy and rune plates. */
    public static void pressed(ServerWorld w, BlockPos pos, Entity by) {
        Ctx c = ctx(w, pos);
        SiteLayout.Ref r = c == null ? null : c.layout.first(pos, SiteLayout.Kind.ELEMENT);
        if (r == null) {
            return;
        }
        Canvas.PuzzleSpec spec = c.layout.canvas.puzzles.get(r.index());
        ExplorationState.PuzzleState ps = c.site.puzzle(r.index());
        ServerPlayerEntity p = by instanceof ServerPlayerEntity sp ? sp : nearestPlayer(w, pos);
        if (spec.type().equals("plate_sequence") && p != null) {
            sequence(c, spec, ps, r.index(), r.element(), p, pos);
        } else if (spec.type().equals("twin_plates") && !ps.solved) {
            if (p != null) {
                participant(c, ps, p);
            }
            if (allMatch(c, spec, (i, s) -> s.isOf(ExploreBlocks.HEAVY_PLATE) && s.get(ExploreBlocks.PRESSED))) {
                solve(c, r.index(), p);
            } else {
                w.playSound(null, pos, SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.4f, 0.6f);
            }
        }
    }

    public static void released(ServerWorld w, BlockPos pos) {
    }

    public static void powered(ServerWorld w, BlockPos pos) {
        Ctx c = ctx(w, pos);
        SiteLayout.Ref r = c == null ? null : c.layout.first(pos, SiteLayout.Kind.ELEMENT);
        if (r == null) {
            return;
        }
        Canvas.PuzzleSpec spec = c.layout.canvas.puzzles.get(r.index());
        if (spec.type().equals("power_restore") && !c.site.puzzle(r.index()).solved) {
            solve(c, r.index(), nearestPlayer(w, pos));
        }
    }

    /** Memory pillar replay: lights the shown stones one after another. */
    public static void replayTick(ServerWorld w, BlockPos pillar) {
        Ctx c = ctx(w, pillar);
        SiteLayout.Ref r = c == null ? null : c.layout.first(pillar, SiteLayout.Kind.ELEMENT);
        if (r == null) {
            return;
        }
        Canvas.PuzzleSpec spec = c.layout.canvas.puzzles.get(r.index());
        ExplorationState.PuzzleState ps = c.site.puzzle(r.index());
        if (ps.progress.length < 2) {
            ps.progress = new int[]{0, 0};
        }
        int i = ps.progress[1];
        if (i >= spec.solution().length) {
            ps.progress[1] = 0;
            return;
        }
        BlockPos stone = c.layout.world(spec.elements().get(spec.solution()[i]));
        flash(w, stone, w.getBlockState(stone));
        ps.progress[1] = i + 1;
        w.scheduleBlockTick(pillar, ExploreBlocks.MEMORY_PILLAR, 16);
    }

    private static void flash(ServerWorld w, BlockPos pos, BlockState s) {
        if (!s.isOf(ExploreBlocks.MEMORY_STONE)) {
            return;
        }
        w.setBlockState(pos, s.with(ExploreBlocks.LIT, true), Block.NOTIFY_ALL);
        w.scheduleBlockTick(pos, ExploreBlocks.MEMORY_STONE, 10);
        int sym = s.get(ExploreBlocks.SYMBOL);
        w.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), SoundCategory.BLOCKS, 0.8f, 0.7f + sym * 0.15f);
        w.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0.01);
    }

    /** Traces the light beam from the lens; marks the path with particles. True when it reaches the receiver. */
    static boolean beam(Ctx c, Canvas.PuzzleSpec spec) {
        ServerWorld w = c.world;
        BlockPos lens = c.layout.world(spec.elements().get(0));
        Direction d = c.s().rotation().rotate(Direction.fromHorizontal(spec.solution()[0]));
        BlockPos p = lens;
        DustParticleEffect dust = new DustParticleEffect(new Vector3f(1f, 0.92f, 0.5f), 1.0f);
        for (int i = 0; i < 48; i++) {
            p = p.offset(d);
            BlockState s = w.getBlockState(p);
            if (s.isOf(ExploreBlocks.LIGHT_RECEIVER)) {
                if (!s.get(ExploreBlocks.LIT)) {
                    w.setBlockState(p, s.with(ExploreBlocks.LIT, true), Block.NOTIFY_ALL);
                }
                return true;
            }
            if (s.isOf(ExploreBlocks.LIGHT_MIRROR)) {
                // the slant is authored in the site's local frame: a quarter turn swaps "/" and "\\"
                boolean slant = s.get(ExploreBlocks.SLANT) ^ (c.s().rotation() == net.minecraft.util.BlockRotation.CLOCKWISE_90
                        || c.s().rotation() == net.minecraft.util.BlockRotation.COUNTERCLOCKWISE_90);
                // "/" reflects east<->north, west<->south; "\" reflects east<->south, west<->north
                int dx = d.getOffsetX(), dz = d.getOffsetZ();
                d = slant ? Direction.fromVector(dz, 0, dx) : Direction.fromVector(-dz, 0, -dx);
                if (d == null) {
                    return false;
                }
                w.spawnParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0);
                continue;
            }
            if (!s.isAir() && s.isOpaqueFullCube(w, p)) {
                return false;
            }
            Vec3d v = Vec3d.ofCenter(p);
            w.spawnParticles(dust, v.x, v.y, v.z, 1, 0, 0, 0, 0);
        }
        return false;
    }

    // ------------------------------------------------------------------ solving

    public static void solve(Ctx c, int index, @Nullable ServerPlayerEntity solver) {
        Canvas.PuzzleSpec spec = c.layout.canvas.puzzles.get(index);
        ExplorationState.PuzzleState ps = c.site.puzzle(index);
        if (ps.solved) {
            return;
        }
        ps.solved = true;
        ps.solvedAt = c.world.getTime();
        if (solver != null) {
            ps.participants.add(solver.getUuid());
        }
        ServerWorld w = c.world;
        for (long l : spec.seals()) {
            BlockPos b = c.layout.world(l);
            BlockState bs = w.getBlockState(b);
            if (bs.isOf(ExploreBlocks.ANCIENT_SEAL) || bs.isOf(ExploreBlocks.SEAL_BARS) || bs.isOf(ExploreBlocks.KEY_LOCK)) {
                w.setBlockState(b, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                w.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, bs), b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5,
                        10, 0.3, 0.3, 0.3, 0.05);
            }
        }
        BlockPos at = c.layout.world(spec.elements().get(0));
        w.playSound(null, at, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 1.2f, 0.8f);
        w.playSound(null, at, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 0.8f, 1.2f);
        for (ServerPlayerEntity p : w.getPlayers(pl -> pl.squaredDistanceTo(at.toCenterPos()) < 32 * 32)) {
            ps.participants.add(p.getUuid());
            p.sendMessage(Text.literal(spec.seals().isEmpty() ? "Something shifts deep in the stone." : "With a long grinding sound, a seal opens.")
                    .formatted(Formatting.GOLD), true);
            Advancements.event(p, "puzzle_solved", spec.type());
        }
        c.state.markDirty();
    }

    static void participant(Ctx c, ExplorationState.PuzzleState ps, ServerPlayerEntity p) {
        if (ps.participants.add(p.getUuid())) {
            c.state.markDirty();
        }
    }

    private static void returnHeld(ServerWorld w, BlockPos pos, ExplorationState.PuzzleState ps) {
        for (ItemStack s : ps.held) {
            w.spawnEntity(new ItemEntity(w, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, s));
        }
        ps.held.clear();
    }

    public static void sealHint(ServerWorld w, BlockPos pos, ServerPlayerEntity p) {
        Ctx c = ctx(w, pos);
        SiteLayout.Ref r = c == null ? null : c.layout.first(pos, SiteLayout.Kind.SEAL);
        if (c != null && c.layout.reservedBox != null && Canvas.inBox(c.layout.reservedBox, pos.getX(), pos.getY(), pos.getZ())) {
            p.sendMessage(Text.literal("Silent. Not yet.").formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);
            return;
        }
        String hint = r == null ? "An ancient seal. It does not answer to tools." : switch (c.layout.canvas.puzzles.get(r.index()).type()) {
            case "key_lock" -> "A heavy lock. Its key should be somewhere in this place.";
            case "timed_gate" -> "Heavy bars. A lever somewhere must raise them.";
            default -> "An ancient seal. Something in this place opens it.";
        };
        p.sendMessage(Text.literal(hint).formatted(Formatting.GRAY), true);
    }

    @Nullable
    static ServerPlayerEntity nearestPlayer(ServerWorld w, BlockPos pos) {
        var p = w.getClosestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 24, false);
        return p instanceof ServerPlayerEntity sp ? sp : null;
    }

    interface Match {
        boolean test(int i, BlockState s);
    }

    private static boolean allMatch(Ctx c, Canvas.PuzzleSpec spec, Match m) {
        for (int i = 0; i < spec.elements().size(); i++) {
            if (!m.test(i, c.world.getBlockState(c.layout.world(spec.elements().get(i))))) {
                return false;
            }
        }
        return true;
    }

    static Identifier id(Item i) {
        return Registries.ITEM.getId(i);
    }
}
