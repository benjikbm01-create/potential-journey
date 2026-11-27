package dev.underdeep.boss;

import dev.underdeep.explore.ExplorationMap;
import dev.underdeep.explore.Explorations;
import dev.underdeep.explore.Site;
import dev.underdeep.guide.Advancements;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The Heart Engine door (shared world progression) and the ending. The door takes the Keystone plus any three of the
 * six Signs; each is set once per world, so a group never needs to repeat a guardian because one player was absent.
 * The ending is a short sequence of titles and lines (players can keep playing during it), a world completion record
 * and a persistent postgame.
 */
public final class Ending {
    private Ending() {
    }

    public static final int SIGNS_NEEDED = 3;

    static void door(ServerPlayerEntity p, BlockPos pos, ItemStack stack) {
        ServerWorld w = p.getServerWorld();
        EncounterState st = EncounterState.get(w.getServer());
        if (st.doorOpen) {
            open(w, p, st);
            return;
        }
        if (BossItems.isSign(stack)) {
            String key = Registries.ITEM.getId(stack.getItem()).getPath();
            if (st.doorSigns.contains(key)) {
                p.sendMessage(Text.literal("That Sign already rests in the door.").formatted(Formatting.GRAY), true);
                return;
            }
            st.doorSigns.add(key);
            st.markDirty();
            stack.decrement(1);
            w.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.5f, 0.7f);
            w.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.05);
            Advancements.event(p, "sign_set", key);
            for (ServerPlayerEntity o : w.getPlayers()) {
                o.sendMessage(Text.literal(p.getName().getString() + " set a Sign into the Heart Engine door.").formatted(Formatting.LIGHT_PURPLE), false);
            }
        }
        long others = st.doorSigns.stream().filter(s -> !s.equals(BossItems.KEYSTONE)).count();
        boolean key = st.doorSigns.contains(BossItems.KEYSTONE);
        if (key && others >= SIGNS_NEEDED) {
            st.doorOpen = true;
            st.markDirty();
            open(w, p, st);
            return;
        }
        p.sendMessage(Text.literal("An ancient door with seven sockets. " + (key ? "The keystone is set. " : "Its central socket is empty. ")
                + others + " of " + SIGNS_NEEDED + " Signs rest around it.").formatted(Formatting.GOLD), false);
        if (!key) {
            p.sendMessage(Text.literal("The middle socket is shaped for a stone that hums with silence.").formatted(Formatting.GRAY, Formatting.ITALIC), false);
        }
    }

    static void open(ServerWorld w, ServerPlayerEntity p, EncounterState st) {
        ExplorationMap map = Explorations.of(w);
        Site s = Lairs.site(map, "somnarch");
        if (s == null) {
            return;
        }
        boolean any = false;
        for (BlockPos d : map.layout(s).marks("door")) {
            if (w.getBlockState(d).isOf(BossBlocks.ENGINE_DOOR)) {
                w.setBlockState(d, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                any = true;
            }
        }
        if (any) {
            w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1.2f, 0.6f);
            for (ServerPlayerEntity o : w.getPlayers()) {
                o.sendMessage(Text.literal("Far below, a door that has been shut for an age grinds open.").formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC), false);
            }
            Advancements.event(p, "engine_door_opened", "somnarch");
        }
    }

    /** Final victory: completion record, ending lines, rewards, postgame. */
    static void victory(Encounters.Active a, List<ServerPlayerEntity> credited, EncounterState st) {
        MinecraftServer server = a.world.getServer();
        boolean first = !st.completed();
        if (first) {
            st.completedAt = a.world.getTime();
            for (ServerPlayerEntity p : credited) {
                st.completers.add(p.getName().getString());
            }
            st.markDirty();
        }
        for (ServerPlayerEntity p : credited) {
            Advancements.event(p, "underdeep_beaten", "somnarch");
        }
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"The Heart Engine sings.", "Below the fire, something vast sighs, and is still."});
        lines.add(new String[]{"The Sleeper dreams no more.", "Its dream is spent; the deep is quiet."});
        lines.add(new String[]{"Underdeep endures.", "The caverns are yours. Build, explore, and remember this day."});
        SEQUENCE.clear();
        long t0 = a.world.getTime();
        for (int i = 0; i < lines.size(); i++) {
            SEQUENCE.add(new Object[]{t0 + 60L + i * 120L, lines.get(i)});
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1f, 0.8f);
            if (!credited.contains(p)) {
                p.sendMessage(Text.literal("The whole of the deep seems to breathe out. Your friends have done it.").formatted(Formatting.GOLD), false);
            }
        }
        if (first) {
            String names = String.join(", ", st.completers);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(Text.literal("Underdeep has been beaten (day " + (st.completedAt / 24000 + 1) + ") by " + names + ".")
                        .formatted(Formatting.GOLD, Formatting.BOLD), false);
            }
        }
    }

    static final List<Object[]> SEQUENCE = new ArrayList<>();

    static void tick(MinecraftServer server) {
        if (SEQUENCE.isEmpty()) {
            return;
        }
        long now = server.getOverworld().getTime();
        for (var it = SEQUENCE.iterator(); it.hasNext(); ) {
            Object[] e = it.next();
            if (now >= (long) e[0]) {
                it.remove();
                String[] l = (String[]) e[1];
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(l[0]).formatted(Formatting.GOLD)));
                    p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(l[1]).formatted(Formatting.GRAY)));
                    p.sendMessage(Text.literal(l[0] + " " + l[1]).formatted(Formatting.GOLD, Formatting.ITALIC), false);
                }
            }
        }
    }
}
