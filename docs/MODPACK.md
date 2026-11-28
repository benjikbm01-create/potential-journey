# Modpack compatibility (Stage 12)

All versions are the newest Modrinth releases for Minecraft 1.21.1 Fabric as of 2026-11-28. They were downloaded by
the Stage 12 test scripts into `run-tests/mods-cache` (not committed; third-party JARs are not redistributed here).
Fabric API 0.116.18+1.21.1 is the only required dependency of any of them.

## Dedicated server results
Each group ran on the official Fabric server launcher with the production Underdeep JAR
(`scripts/server_compat.sh <group> audit <mods>`). Every group then ran the Underdeep self-test suite `audit`:
registries, the obtainability audit of 751 items, equipment, handbook and advancement checks.

| Group | Mods | Result |
|---|---|---|
| A | Underdeep + Fabric API only | 76/76, 0 unexpected errors, 43 mods loaded |
| B | + Lithium, FerriteCore, ModernFix, Chunky, Spark | 76/76, 0 errors (49 mods) |
| C | + Jade, AppleSkin, EMI, Better Advancements | 76/76, 0 errors (49 mods) |
| D | + Simple Voice Chat, Ping Wheel, Sound Physics Remastered | 76/76, 0 errors (47 mods) |
| E | Every server-loadable mod together (13 mods) | 76/76, 0 errors (62 mods) |
| F | + Distant Horizons (server side) | 76/76, 0 errors (44 mods) |

Observations:
- **ModernFix + Lithium** log `Method overwrite conflict for removeIf ... SortedArraySetMixin` at startup. Both
  patch the same vanilla method; it is a warning, not an error. All tests passed with both installed.
- **Simple Voice Chat** starts its UDP server on port 24454 (see `docs/SERVER_GUIDE.md`).
- **Chunky** pregenerated Underdeep terrain correctly. Measurements are in TESTING (Stage 12) and SERVER_GUIDE.

## Versions and placement
| Mod | Tested version (Modrinth, 1.21.1 Fabric) | File | Required deps | Install on | Dedicated-server result |
|---|---|---|---|---|---|
| Sodium | mc1.21.1-0.8.13-fabric | `sodium-fabric-0.8.13+mc1.21.1.jar` | — | client | client-only: not loadable on a server (environment=client) |
| Lithium | mc1.21.1-0.15.4-fabric | `lithium-fabric-0.15.4+mc1.21.1.jar` | — | both (server benefit) | Group B: loads; Underdeep audit 76/76; no errors |
| FerriteCore | 7.0.3-fabric | `ferritecore-7.0.3-fabric.jar` | — | both | Group B: loads; Underdeep audit 76/76; no errors |
| ModernFix | 5.25.1+mc1.21.1 | `modernfix-fabric-5.25.1+mc1.21.1.jar` | — | both | Group B: loads; Underdeep audit 76/76; no errors |
| Entity Culling | 1.12.2 | `entityculling-fabric-1.12.2-mc1.21.1.jar` | Fabric API | client | Group E: loads; Underdeep audit 76/76; no errors |
| Chunky | 1.4.23 | `Chunky-Fabric-1.4.23.jar` | Fabric API | server | Group B: loads; Underdeep audit 76/76; no errors |
| Spark | 1.10.109-fabric | `spark-1.10.109-fabric.jar` | — | server (client optional) | Group B: loads; Underdeep audit 76/76; no errors |
| EMI | 1.1.24+1.21.1+fabric | `emi-1.1.24+1.21.1+fabric.jar` | — | client (server optional) | Group C: loads; Underdeep audit 76/76; no errors |
| Jade | 15.10.6+fabric | `Jade-1.21.1-Fabric-15.10.6.jar` | — | client (server optional for extra data) | Group C: loads; Underdeep audit 76/76; no errors |
| AppleSkin | 3.0.6+mc1.21 | `appleskin-fabric-mc1.21-3.0.6.jar` | Fabric API | client (server recommended for saturation sync) | Group C: loads; Underdeep audit 76/76; no errors |
| Better Advancements | 0.4.3.21 | `BetterAdvancements-Fabric-1.21.1-0.4.3.21.jar` | — | client | Group C: loads; Underdeep audit 76/76; no errors |
| Mouse Tweaks | 1.21-2.26-fabric | `MouseTweaks-fabric-mc1.21-2.26.jar` | Fabric API | client | client-only: not loadable on a server (environment=client) |
| Simple Voice Chat | fabric-1.21.1-2.6.22 | `voicechat-fabric-1.21.1-2.6.22.jar` | — | both (required on server for voice) | Group D: loads; Underdeep audit 76/76; no errors |
| Sound Physics Remastered | fabric-1.21.1-1.5.2 | `sound-physics-remastered-fabric-1.21.1-1.5.2.jar` | — | client | Group D: loads; Underdeep audit 76/76; no errors |
| Ping Wheel | 1.12.2 | `Ping-Wheel-1.12.2-fabric-1.21.1.jar` | Fabric API | both (server relays pings) | Group D: loads; Underdeep audit 76/76; no errors |
| Distant Horizons | 3.3.3-1.21.1 | `DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar` | — | client (server optional) | Group F: loads; Underdeep audit 76/76; no errors |

## Client-side status
- Client mods (Sodium, Entity Culling, EMI and Jade overlays, AppleSkin HUD, Better Advancements, Mouse Tweaks, Sound
  Physics, Ping Wheel UI, Distant Horizons) are covered in TESTING (Stage 12, client groups), with exactly what was and
  wasn't checked.
- **Not testable here:**
  - real voice (no microphones or second client);
  - Distant Horizons rendering quality on real GPUs;
  - FPS on real hardware (the container renders with software OpenGL).

## Recommendations
- **Server:** Fabric API, Underdeep, Lithium, FerriteCore, ModernFix, Chunky, Spark, Simple Voice Chat, Ping Wheel.
  Optional: Jade (extra server data) and AppleSkin (saturation sync).
- **Every client:** Fabric API and Underdeep, plus Simple Voice Chat and Ping Wheel if the server uses them.
- **Recommended client extras:** Sodium, Entity Culling, FerriteCore, ModernFix, AppleSkin, Jade, EMI, Better
  Advancements, Mouse Tweaks, Sound Physics Remastered.
- **Distant Horizons:** optional, client only. Underground worlds gain little from it, and LOD rendering can reveal
  cavern shapes far away, which is a mild exploration spoiler. Leave it out by default.
- **EMI** shows every registered recipe, including ones the Underdeep Handbook hides until discovery. Players who want
  the full discovery experience should rely on the Handbook. EMI cannot reliably hide client-side registered recipes.
