# Running Underdeep on a server (Stage 12)

Everything here was tested on the official Fabric server launcher with the production JAR (see `docs/TESTING.md`,
Stage 12). Numbers marked "measured" come from this container: 4 vCPUs, no GPU. Your host will differ, so measure with
Spark.

## 1. Install (dedicated server)
1. Java 21, Minecraft 1.21.1, Fabric Loader 0.17.3 (server launcher from fabricmc.net).
2. Put these in `mods/`: `underdeep-<version>.jar` and `fabric-api-0.116.18+1.21.1.jar`, plus the server mods from
   `docs/MODPACK.md` that you want.
3. Set these in `server.properties`:
   ```
   level-type=underdeep\:underdeep
   view-distance=8
   simulation-distance=5
   sync-chunk-writes=false
   ```
   `level-type` makes the world Underdeep; it must be set before the world is first created. The other three are
   recommended starting points. Raise the view distance if the CPU allows; the big caverns look better with more.
4. Start the server once, accept the EULA, and check that the log line `Underdeep world loaded` appears.

Every player needs the same Underdeep version and Fabric API. Client-only mods are optional per player.

## 2. Memory and JVM flags
- A host limit of "10 GB" usually covers the whole process. Give the Java heap about 7–8 GB and leave the rest for
  metaspace, threads, direct buffers (networking, voice chat) and the OS.
- Suggested Java 21 flags (plain G1, no outdated tuning):
  ```
  java -Xms7G -Xmx7G -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ParallelRefProcEnabled -jar fabric-server-launch.jar nogui
  ```
- Measured heap use during pregeneration is in `docs/TESTING.md` (Stage 12). Use Spark to watch it:
  `spark health --memory` and `spark gc`.

## 3. Simple Voice Chat
- The server listens on **UDP 24454** by default (measured: `Voice chat server started at port 24454`).
- Your host must open or forward that UDP port. TCP is not enough. Set the same port in
  `config/voicechat/voicechat-server.properties` if the host assigns a different one.
- Every player who wants voice needs the client mod too.

## 4. Pregeneration with Chunky (do this only for the final Stage 13 world)
1. Make a backup first (section 5).
2. In the console, run:
   ```
   chunky world minecraft:overworld
   chunky center 0 0
   chunky radius <blocks>
   chunky start
   ```
   Underdeep's caverns live in the overworld dimension. Pause with `chunky pause`, resume with `chunky continue`; the
   task survives restarts.
3. Throughput and disk use per chunk were measured here (TESTING, Stage 12). Estimate your total time from your host's
   speed. Start with a radius of 1500–2000 blocks; players reach further than that only after days.
4. Afterwards, check the world with a walk around spawn, then `spark tps` while players explore.

Pregeneration is optional. It moves generation cost out of play sessions. It does not fix lag that comes from entities
or other simulation (see the profiling results in TESTING).

## 5. Backups and recovery
- **What to back up:** the whole `world/` folder (it includes Underdeep's saved data in `world/data/` and player data),
  `config/`, `server.properties` and `mods/`.
- **When to back up:** before every mod update, configuration change, pregeneration and launch, and daily while the
  group is playing.
- **How:** stop the server (or run `save-off` then `save-all flush`), copy the folders, then `save-on`.
- **Updating Underdeep:**
  1. Back up.
  2. Replace the JAR on the server and on every client.
  3. Read the release notes in `releases/README.md`. Some versions need a new world for new terrain.
- **Restoring:** stop the server and replace `world/` with the backup copy.
- **Crash or lag diagnosis:**
  - Crash reports are in `crash-reports/`; the last log is `logs/latest.log`.
  - For lag, run `spark profiler start`, wait for the lag, then run `spark profiler stop --save-to-file` and share
    the file.
