# Underdeep — Combat, enemies, guardians and the endgame (Stage 10)

Public, spoiler-free. Guardian identities, appearances, locations, mechanics, rewards and the ending are private
(`docs/private/BOSSES.md`).

## 1. Player combat (`combat/`)
Layered on vanilla combat (attack cooldown, shields, armour, enchantments, durability, health unchanged). Everything
that matters is decided on the server; clients send intents only (`underdeep:combat_dodge` with a clamped movement
vector, `underdeep:combat_heavy`) and receive `underdeep:combat_hud` (stamina, flags) and `underdeep:combat_windup`
(animation).
- **Stamina** 0–100: only dodge (22), heavy attacks (16–32 by style), weapon parry (10) and shield blocks
  (≈4 per blocked damage) spend it; regenerates ≈22/s after 0.6 s (×1.25 well fed, ×0.5 hungry). Guard breaks at 0.
- **Dodge** (Left Alt): grounded dash (velocity, normal collision: no clipping), 7 ticks of invulnerability against
  attacks/projectiles/explosions (not falls, fire, hazards, aura zones), 14-tick cooldown, no dodge during a wind-up.
  Avoiding a blow opens a counter.
- **Heavy attack** (sneak + attack with a melee weapon at ≥80 % charge; sneak-mining still mines): server-timed
  wind-up (movement −55 %), server hit detection in the style's shape, vanilla attack pipeline ×multiplier, recovery.
  A solid hit during the wind-up interrupts it.
- **Parry**: shield raised within the window (6 ticks + up to 4 for latency), or right-click with a melee weapon (no
  shield in off hand; sword +2 ticks; 18-tick cooldown). Parries negate melee blows (shields also deflect projectiles),
  stagger the attacker and open a counter (30 ticks, sword 40). Unparryable attacks: creature heavies and charges,
  guardian sweeps/crushes/auras.
- **Counter** ×1.6 (sword ×1.9) and forced stagger. **Stagger** (`Poise`): style poise damage wears a mob down;
  staggered mobs skip AI for 1.5 s and take +30 %; then immune for 2.5 s (no stagger-locking).
- **Styles** (10, `WeaponStyle`): sword, dagger (back/staggered +50 %), greatsword (wide sweep), spear (lunging line
  thrust), warhammer (360° slam, best guard break), battleaxe (shield disable, armour bonus), mace (stagger, armour
  bonus), bow (drawn shots stagger), crossbow, shield.
- Config `combat`: enabled, dodge, heavyAttacks, weaponParry, dodgeStamina, dodgeInvulnerableTicks, parryWindowTicks,
  latencyCompensation, counterWindowTicks, staminaRegenPerSecond, shieldStaminaPerDamage, heavyWindupScale, assist,
  mobStagger, enemyBehaviours.

## 2. Enemy behaviour (`combat/CombatGoal`, `Archetype`)
Fighting creatures (species with attack > 1) use one reusable goal with 15 archetypes assigned from ecology data:
brawler, skirmisher (hit and run), stalker (circles, pounces), ambusher, pack flanker, pack leader (howl buffs),
charger (warns, rushes straight, dazes itself on walls), bruiser (slow ground slam), territorial warner, spitter
(ranged venom projectile), swooper, aquatic dasher, burrower (eruption ring), defender (armoured front ×0.5) and swarm.
Every attack: approach → telegraph (posture state, sound, particles, ground rings) → strike decided at that moment →
recovery. Bounded: path updates ≤ every 10 ticks, pack queries every 40, no world scans. Peaceful species are unchanged.

## 3. Guardians (`boss/`)
- Roster: 13 — 3 early, 4 midgame, 3 late, 2 secret optional, 1 final. One per world, in a guaranteed lair placed
  deterministically (`Lairs`) in suitable ecosystems at increasing distance/depth, never in settlements or on descents.
- Framework: `BossEntity` (move state machine idle → wind-up → active → recovery; poise/stagger; phases at health
  thresholds with a short transition; defeat sequence; leash back to its lair; ranged answers to unreachable players,
  no teleporting players), `Brain` per guardian (moves, phases, devices, weak points), `BossProjectile`, `BossDamage`
  kinds (strike: block/parry; sweep: block; crush: dodge; spell: block/deflect; aura: step out), shared helpers for
  cones, circles, lines, jumpable ground waves, delayed telegraphed zones, channelled arena devices.
- Encounters (`Encounters`): awakening at an altar (confirm twice; some need an offering); participants = players in
  the arena; scaling fixed at awakening (health ×(1 + 0.55 per extra player), damage ×(1 + 0.08)), rising only for
  joiners in the first 90 s; arena protection (no digging/building/buckets while awake); downed instead of killed when a
  friend fights (once a minute; crouch 2.5 s to revive; 15 s limit); reset after 30 s without fighters; guardians and
  their minions are never saved — a crash or restart resets the fight; arena block changes are recorded and restored
  (also at start-up).
- Rewards: credited participants (any action, or 30 s in the fight) each get a personal loot roll; first victory per
  player gives the guardian's Sign where it has one; repeat victories give smaller "echo" loot; the heirloom pedestal
  holds one unique item per world (claimable after the defeat). Claims are recorded before items are given.
- Audio/presentation hooks: S2C `underdeep:boss_audio` (prepare, activate, phase, victory, defeat, retreat), boss
  bars, titles. Lair families carry audio context `lair` / `lair_final` (Stage 9 context payload).
- Models: data-driven articulated rigs (`assets/underdeep/bosses/<id>.json`, 168 parts total) with pose animation per
  move stage and an emissive layer (`client/boss`). Stage 11 replaces/refines rigs without touching mechanics.
- Config `bosses`: healthMultiplier, damageMultiplier, healthPerExtraPlayer, maxScaledPlayers, telegraphScale, revive,
  arenaProtection, resetAfterSeconds.

## 4. The way to beat Underdeep
Clear structure without spoilers: grow equipment (Stages 7–8) → explore → find guardians (Handbook and journal list
lairs once found) → defeat Sign keepers (each participant receives a Sign) → obtain the keystone → follow it to the old
door → set the keystone and any three Signs (world state, shared) → the final encounter → completion record, ending
sequence, postgame. Not required: early or secret guardians, all Signs, relationships, businesses, all advancements.
Recovery: lost Signs/keystone can be reclaimed at the guardian's altar by anyone it credited, until that Sign is set.
Guidance: Handbook "Beating Underdeep" (one page always visible, the rest unlocked by the step reached), guardian
pages unlocked by fighting them, the "Guardians" advancement tab.

## 5. Stage 11 visual and audio backlog (top priority: models)
1. Rebuild every creature, boss, humanoid NPC, armour, weapon, tool, plant, furniture, workstation and decorative model
   with handcrafted Blockbench-style geometry (distinctive silhouettes, anatomy, articulation), verified in-game model
   by model with screenshots — registration is not proof of quality. Spoiler-sensitive previews stay private.
2. Proper keyframed animations for bosses and creatures (wind-up, strike, recovery, stagger, phase, defeat), first-person
   heavy-attack and parry animations, better hit feedback (optional, accessible screen effects).
3. Textures (procedural placeholders today), UI polish, environmental sound.
4. Continuous original soundtrack (60–80 tracks, crossfades, vanilla-gaps option) using the context hooks, unique music
   per major guardian, final encounter and victory music.
