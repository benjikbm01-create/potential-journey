# Underdeep — Next Steps

## Immediate: user play-testing of Stage 6, then Stage 7

Do not start Stage 7 until the user assigns it. Stage 6 needs no new world: existing settlements join the economy
when players come within `economyRadius` (1600 blocks) of them; settlements still only exist in worlds created
with generator v3.

### Prerequisites (verify first)
1. `./gradlew build` succeeds (unit tests included).
2. `SUITE=stage4 scripts/selftest.sh 42 runs/check create` → 0 FAIL (includes Stage 5 and 6 checks; ≈3 min).
3. Read `docs/ECONOMY.md`, `docs/NPC_MINDS.md`, `docs/ARCHITECTURE.md` §5e–5g.

### Manual test checklist (v3 world)
- Visit a settlement: talk to sellers ("What do you have in stock?"), trade, compare prices in another settlement;
  the journal's trade notes show what you saw and when.
- Leaders/innkeepers: "What's on the noticeboard?" → take a contract, hand it in; buy or rent property; register a
  shop; `/business stock`, wait a day, `/business status`; hire someone ("Would you keep my shop?").
- `/underdeep econ`, `econ shipments`, `econ projects`, `econ events`, `econ world` to watch the simulation;
  `econ build house` near a settlement to see a construction site; `econ shortage` to see a settlement react.
- Explore routes between settlements to meet caravans (they appear 40–96 blocks away on their route).

### Stage 7/8 hooks
See `docs/ECONOMY.md` §13 (consumption, nutrition, recipes data, tool wear, profession categories).

### Known issues / limitations of Stage 6
- Not hands-on tested: physical caravans, trading screens with dynamic offers, noticeboard/contract/property/shop
  dialogue, hiring, commands (see TESTING.md).
- Water trade is abstract (no boat entities); courier contracts are defined but not posted; establishing new
  outposts is not implemented; caravan cargo is not visible on the animals; construction shows bare building
  progress (no scaffolding); workers do not physically carry goods.
- Settlement stores are abstract (no chests mirror them) by design (D-054).
- Physical caravans follow straight route segments between cavern nodes with stuck-recovery; very rough tunnels may
  make them hop forward when unobserved.
- Prices are per settlement; transport difficulty uses route distance and danger only.
- Earlier known issues of Stages 4–5 still apply.
