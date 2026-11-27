package dev.underdeep.client.explore;

import dev.underdeep.explore.Explorations;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side of Stage 9: the personal exploration journal (shown in the Handbook's "Your Journal" category) and the
 * current exploration audio context (only ever a place the player has discovered; Stage 11 music reads it).
 */
public final class ExploreClient {
    private ExploreClient() {
    }

    public static final List<String[]> JOURNAL = new ArrayList<>();
    public static volatile String CONTEXT = "none";
    public static int journalVersion;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Explorations.Journal.ID, (p, ctx) -> ctx.client().execute(() -> {
            JOURNAL.clear();
            for (String e : p.entries()) {
                JOURNAL.add(e.split("\u0001", -1));
            }
            journalVersion++;
        }));
        ClientPlayNetworking.registerGlobalReceiver(Explorations.Context.ID, (p, ctx) -> CONTEXT = p.context());
    }

    /** Replaces "{at:x,y,z}" / "{near:x,y,z}" with the live distance and direction from the player. */
    public static String live(String text) {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null || !text.contains("{")) {
            return text;
        }
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int s = text.indexOf('{', i);
            if (s < 0) {
                b.append(text.substring(i));
                break;
            }
            int e = text.indexOf('}', s);
            if (e < 0) {
                b.append(text.substring(i));
                break;
            }
            b.append(text, i, s);
            String tok = text.substring(s + 1, e);
            boolean near = tok.startsWith("near:");
            String[] xyz = tok.substring(tok.indexOf(':') + 1).split(",");
            try {
                double x = Double.parseDouble(xyz[0]), y = Double.parseDouble(xyz[1]), z = Double.parseDouble(xyz[2]);
                double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
                double dist = Math.hypot(dx, dz);
                String[] names = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
                String dir = names[(int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8)];
                int dy = (int) Math.round(y - mc.player.getY());
                String v = Math.abs(dy) < 8 ? "" : dy > 0 ? ", " + dy + " blocks up" : ", " + -dy + " blocks down";
                b.append(near ? "about " + Math.max(50, Math.round(dist / 50.0) * 50) + " blocks " + dir
                        : (dist < 8 ? "you are here" : (int) dist + " blocks " + dir + v + " of you"));
            } catch (RuntimeException ex) {
                b.append(tok);
            }
            i = e + 1;
        }
        return b.toString();
    }
}
