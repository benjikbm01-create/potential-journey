package dev.underdeep.client;

import dev.underdeep.Underdeep;
import dev.underdeep.world.gen.UnderdeepChunkGenerator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;

/**
 * Development-only GUI test (enabled by {@code -Dunderdeep.createWorldTest=<world name>}): opens the
 * real Create World screen, selects the "Underdeep" world type from the normal world-type list,
 * presses Create, waits until the player is in the world, records what the integrated server
 * generated, saves a screenshot and quits. Never active in normal play.
 */
public final class CreateWorldTest {
    private static String name;
    private static int stage;
    private static int ticks;

    private CreateWorldTest() {
    }

    static void init() {
        name = System.getProperty("underdeep.createWorldTest");
        if (name != null) {
            ClientTickEvents.END_CLIENT_TICK.register(CreateWorldTest::tick);
        }
    }

    private static void tick(MinecraftClient client) {
        ticks++;
        switch (stage) {
            case 0 -> {
                if (client.currentScreen instanceof TitleScreen && ticks > 40) {
                    CreateWorldScreen.create(client, client.currentScreen);
                    stage = 1;
                    ticks = 0;
                }
            }
            case 1 -> {
                if (client.currentScreen instanceof CreateWorldScreen screen && ticks > 40) {
                    WorldCreator creator = screen.getWorldCreator();
                    WorldCreator.WorldType target = null;
                    StringBuilder offered = new StringBuilder();
                    for (WorldCreator.WorldType type : creator.getNormalWorldTypes()) {
                        offered.append(type.getName().getString()).append(", ");
                        if (type.preset() != null && type.preset().getKey().map(k -> k.getValue().getNamespace().equals(Underdeep.MOD_ID)).orElse(false)) {
                            target = type;
                        }
                    }
                    Underdeep.LOGGER.info("CreateWorldTest: world types offered: {}", offered);
                    if (target == null) {
                        Underdeep.LOGGER.error("CreateWorldTest FAIL: Underdeep world type not offered");
                        client.scheduleStop();
                        stage = 99;
                        return;
                    }
                    creator.setWorldType(target);
                    creator.setWorldName(name);
                    creator.setSeed("777");
                    Underdeep.LOGGER.info("CreateWorldTest: selected world type '{}'", target.getName().getString());
                    ScreenshotRecorder.saveScreenshot(client.runDirectory, "createworld_screen.png", client.getFramebuffer(), m -> { });
                    stage = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                if (client.currentScreen instanceof CreateWorldScreen screen && ticks > 10) {
                    for (var child : screen.children()) {
                        if (child instanceof ButtonWidget b && b.getMessage().equals(Text.translatable("selectWorld.create"))) {
                            b.onPress();
                            stage = 3;
                            ticks = 0;
                            return;
                        }
                    }
                }
            }
            case 3 -> {
                if (client.world != null && client.player != null && client.getServer() != null && ticks > 200) {
                    IntegratedServer server = client.getServer();
                    var gen = server.getOverworld().getChunkManager().getChunkGenerator();
                    boolean ok = gen instanceof UnderdeepChunkGenerator;
                    Underdeep.LOGGER.info("CreateWorldTest {}: generator {}, spawn {}, player at {}, skylight {}, block light at player {}",
                            ok ? "PASS" : "FAIL", gen.getClass().getSimpleName(), server.getOverworld().getSpawnPos().toShortString(),
                            client.player.getBlockPos().toShortString(), server.getOverworld().getDimension().hasSkyLight(),
                            client.world.getLightLevel(net.minecraft.world.LightType.BLOCK, client.player.getBlockPos()));
                    ScreenshotRecorder.saveScreenshot(client.runDirectory, "createworld_spawn.png", client.getFramebuffer(), m -> { });
                    stage = 4;
                    ticks = 0;
                }
            }
            case 4 -> {
                if (ticks > 20) {
                    client.scheduleStop();
                    stage = 99;
                }
            }
            default -> {
            }
        }
    }
}
