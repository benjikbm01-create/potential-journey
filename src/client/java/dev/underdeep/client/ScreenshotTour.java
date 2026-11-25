package dev.underdeep.client;

import dev.underdeep.Underdeep;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Development-only automated visual inspection. Disabled unless the JVM is started with
 * {@code -Dunderdeep.tour=<file>}; never active in normal play.
 * <p>
 * The file lists viewpoints, one per line: {@code name x y z yaw pitch}. For each viewpoint the
 * player (switched to spectator in the integrated server) is teleported there, the client waits for
 * terrain to load and render, and two screenshots are saved: natural lighting and with night vision
 * (to show terrain shape). The game then closes. Used by docs/TESTING.md "Visual inspection".
 */
public final class ScreenshotTour {
    private record Shot(String name, double x, double y, double z, float yaw, float pitch) {
    }

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int index = -1;
    private static int wait;
    private static int phase;
    private static int startupDelay = 200;

    private ScreenshotTour() {
    }

    static void init() {
        String file = System.getProperty("underdeep.tour");
        if (file == null) {
            return;
        }
        try {
            for (String line : Files.readAllLines(Path.of(file))) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 6 || line.startsWith("#")) {
                    continue;
                }
                SHOTS.add(new Shot(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                        Float.parseFloat(p[4]), Float.parseFloat(p[5])));
            }
        } catch (IOException e) {
            Underdeep.LOGGER.error("Could not read screenshot tour {}", file, e);
            return;
        }
        Underdeep.LOGGER.info("Screenshot tour enabled with {} viewpoints", SHOTS.size());
        ClientTickEvents.END_CLIENT_TICK.register(ScreenshotTour::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null || client.getServer() == null) {
            return;
        }
        if (startupDelay-- > 0) {
            return;
        }
        IntegratedServer server = client.getServer();
        if (index < 0) {
            client.options.getViewDistance().setValue(12);
            client.options.hudHidden = true;
            index = 0;
            phase = 0;
            teleport(server, client, SHOTS.get(0), false);
            return;
        }
        if (index >= SHOTS.size()) {
            return;
        }
        if (wait-- > 0) {
            return;
        }
        boolean rendered = client.worldRenderer.isTerrainRenderComplete();
        if (!rendered && wait > -400) {
            return;
        }
        Shot s = SHOTS.get(index);
        String suffix = phase == 0 ? "_natural" : "_nightvision";
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "tour_" + s.name + suffix + ".png", client.getFramebuffer(),
                msg -> Underdeep.LOGGER.info("Tour screenshot: {}", msg.getString()));
        if (phase == 0) {
            phase = 1;
            teleport(server, client, s, true);
            wait = 20;
        } else {
            phase = 0;
            index++;
            if (index >= SHOTS.size()) {
                Underdeep.LOGGER.info("Screenshot tour complete");
                client.scheduleStop();
                return;
            }
            teleport(server, client, SHOTS.get(index), false);
        }
    }

    private static void teleport(IntegratedServer server, MinecraftClient client, Shot s, boolean nightVision) {
        server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            p.changeGameMode(GameMode.SPECTATOR);
            if (nightVision) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false));
            } else {
                p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
            p.teleport(p.getServerWorld(), s.x, s.y, s.z, s.yaw, s.pitch);
        });
        wait = nightVision ? 20 : 120;
    }
}
