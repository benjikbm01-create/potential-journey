# Underdeep — Living inhabitants (Stage 5)

This document describes how inhabitants think, feel, remember, relate and talk. It is spoiler-free; cultural
detail that players should discover lives in `docs/private/`. Code: `civ/mind/*` (pure logic, no world access)
and `civ/npc/*` (world integration).

## 1. One authoritative identity

Every inhabitant is a `SettlementRecord.InhabitantRecord` (Stage 4) that now carries a `Mind` (NBT key `mind`,
schema `Mind.SCHEMA` = 1). The entity is only a temporary body: it is never saved, it is created from the
record near players and removed when they leave. The mind is advanced by whichever tier currently owns the
inhabitant; there is never more than one body per record (`InhabitantManager.active` map, checked by the
self-test). Appearance, name, home and workplace come from Stage 4 and never change.

**Migration**: Stage 4 records without a mind get one deterministically from their appearance seed when the
settlement state loads (`SettlementRecord.ensureMinds`), together with housemate/coworker/mentor bonds. Their
identities are untouched. New settlements additionally form families (shared family name, partner/parent/
child/sibling bonds) for family homes.

## 2. Personality (24 traits, `Trait`)

Values 0–100 (Gaussian around 50, σ≈17), biased by civilization (e.g. Stoneborn loyal and wary, Amberfolk
sociable and generous, Astrals curious and independent) and by profession (guards courageous, scholars curious,
healers compassionate). Deterministic per inhabitant. What each trait actually changes:

| Trait | Effect in the simulation |
|---|---|
| Sociability | social need decay, chat/visit/gathering/tavern utility, conversation frequency, small-talk encounters, companion of reserved strangers says less |
| Courage | flee vs. shelter vs. defend in danger, fear gained from threats, companion "protect me" replies, willingness to travel |
| Patience | how long an activity is kept before switching, teaching apprentices, blunt gift refusals when low |
| Generosity | gift giving and food sharing in encounters, thank-you item after being helped |
| Trustfulness | initial trust toward unknown players, refusal wording for companion requests |
| Ambition | purpose need decay, work utility, skill goals and skill growth, frustration when goals expire |
| Curiosity | curiosity need decay, exploring/reading/wildlife watching, news goals, dialogue topics |
| Loyalty | willingness to become a companion |
| Optimism | contentment baseline, mood dialogue |
| Honesty | confidence lost when passing rumours on, blunt gift reactions |
| Independence | phrasing of requests for help, living-alone dialogue |
| Discipline | schedule jitter (punctuality), work utility, tidying, morning routines |
| Humor | jokes and teasing (encounters and dialogue), games, voice ("Ha!") |
| Competitiveness | rival jabs with same-profession peers, merchant rivalry, game outcomes and frustration |
| Compassion | comforting sad people, helping the injured, warning others |
| Caution | shelter preference, safety loss from predator news, less exploring, weight of danger memories |
| Adventurousness | exploring, fishing for boatwrights, companion willingness, wanderlust dialogue |
| Impulsiveness | shorter commitment to activities, friction in encounters |
| Pride | friction, boasting, offence at teasing, anger when attacked, reluctance to make amends |
| Forgiveness | reconciliation encounters and the urge to make amends |
| Resilience | how fast negative emotions fade |
| Frugality | eating at home rather than at the inn, appreciation of valuable gifts |
| Homebody | eating at home, favourite spot, less exploring, refusing to leave as a companion |
| Tolerance | personality compatibility, disagreement frequency, complaints |

## 3. Emotions (15, `Feelings.Emotion`) and needs (10, `Feelings.Need`)

Emotions are 0–1, event-driven, and decay hourly (negative ones faster with resilience): happiness,
contentment, excitement, curiosity, affection, concern, fear, anger, frustration, sadness, loneliness,
embarrassment, stress, pride, relief. Contentment follows needs and optimism; loneliness follows the social
need; stress follows hunger/exhaustion. They are expressed through choices (fleeing, comforting, making
amends), dialogue variants and the greeting — never as constant icons.

Needs are satisfaction 0–1 and decay per in-game hour (scaled by personality): hunger, rest, safety, social,
recreation, comfort, purpose, belonging, health, curiosity. Activities restore them (see `Activity`).

## 4. Decisions (`Brain`) and the daily cycle

Utility-based selection over the **50 activities** in `Activity` (7 personal, 16 work, 10 social, 10 leisure,
7 emergency). Each activity has a place (bed, home, work, fields, docks, library, inn, square, market, patrol
route, edge of town, a friend's home, near, wander, favourite place, shelter, guard post, clinic), a duration
range and hourly need effects. Utility combines needs, personality, profession, time phase, relationships,
goals and what the settlement actually has (`Places.fill`).

Stability: an activity is kept until its minimum duration (scaled by impulsiveness and patience) unless an
urgent choice (utility ≥ 3: danger, injury, exhaustion) interrupts; afterwards a new activity must beat it by
25 % + 0.15; past its maximum duration it is replaced. Diagnostics record the reason (`/underdeep npc`).

Daily cycle (underground, by world time): night 13000–23000, morning, work, midday break 5500–6500, evening
11000–13000. Each person's schedule is offset by up to ±300 ticks (more for undisciplined people); half the
guards and innkeepers work the night shift. Young inhabitants work less and play more; elders rest and teach.

Professions: farming, ranching, fishing, smithing, crafting, cooking, studying, serving customers, stocking
shelves, patrolling, watch posts, healing, herb gathering, running the inn, plus generic workstation work;
mentors teach apprentices at their station. Farmers harvest and replant mature crops; work produces sounds.

**Workplaces and shared spots**: job stations come from the plan (one record per station). Shared places
(inn seats, squares) use short reservations (`Places.resolve`, 2400 ticks) so people spread out; reservations
are released when an inhabitant leaves the active tier, dies or becomes a companion.

## 5. Memory (`Memory`, 28 categories)

First meeting, good/bad talk, gift received/given, helped by/helped someone, attacked by, witnessed attack,
rescued, lost friend, lost family, became friends, argument, reconciled, worked together, trust broken,
discovery, new place, changed job, moved home, goal done, danger, joined company, traded, shared meal,
defended by, wildlife seen. Each memory has a subject (`p:<uuid>`, `i:<index>`, `s:<species>`), detail,
time, importance and a recall count. Similar memories within four days consolidate (count++); storage is
bounded (`inhabitantMemoryLimit`, default 48) by evicting the lowest weight (importance × recency × recall),
never the newest; minor unrecalled memories fade. Dialogue only references memories that exist.

## 6. Relationships (`Bond`)

Sparse maps (only people actually met): familiarity 0–100, trust/affection/respect −100..100,
resentment/fear 0–100, plus kin type and flags (household, coworker, mentor, apprentice, companion, rivalry).
Asymmetric (each side has its own bond). Repeated trivial interactions on one day have diminishing returns;
significant events (≥ 10) never do. **18 labels** derived from the dimensions: stranger, acquaintance,
neighbour, coworker, friend, close friend, best friend, family, housemate, mentor, apprentice, trusted
associate, companion, professional rival, rival, disliked, distrusted, enemy. Players see only a word:
unfamiliar / familiar / friendly / trusted / close friend (or wary / distrustful / hostile).

Friendships form through repeated compatible encounters (compatibility = similarity of temperament plus
tolerance); rivalries form between competitive peers of the same trade and from accumulated resentment;
arguments are rare and non-violent and forgiving people seek reconciliation.

## 7. Households and families

Inhabitants sharing a home are a household (bonds, belonging need, family dialogue). In family homes created
from Stage 5 on, the first two residents are partners and the others their children (young), sharing a family
name. Households and families persist; there is no rapid ageing. When a member dies, the household notices
(no omniscience: only those sharing a home or kin) and grieves; others learn through conversation.

## 8. Conversations with players (`Dialogue`, `Topics`, client `DialogueScreen`)

Right-click opens the dialogue screen (sneak + right-click trades directly). Shows name, profession · culture ·
familiarity word, the line and options: Chat, Tell me about yourself, Any news?, About <settlement>, Who's who
around here?, How are you?, Trade, Give <held item>, Do you need anything?, Hand over <item>, companion
options, Goodbye. Esc closes at any time. The server decides everything; the client only displays.

The library has **92 topics** in 7 groups (11 greeting, 28 chat, 13 about, 9 news, 9 town, 9 people, 13 feeling); eligibility
predicates make each topic true for the speaker (real family members, real friends/rivals, real knowledge,
facilities that exist, real memories of the player). Lines vary by personality, emotion, needs, profession,
civilization (8 greeting styles, 8 cultural sayings), familiarity, current activity, age and time of day;
a voice pass adds stress/sadness/humour markers and makes reserved people terse with strangers. Talking grows
familiarity with diminishing returns. "About yourself" and familiar chats can reveal preferences (likes,
dislike, favourite place) — more readily to trusted people — which the journal records. No language model is
used; nothing is generated online. (Optional free-text input was not implemented.)

## 9. Inhabitant-to-inhabitant encounters (`Encounters`, 32 types)

Greeting, small talk, work talk, share news, share food, ask for help, disagreement, argument, reconcile,
congratulate, comfort, gift, introduction, lesson, rival jab, joke, invite to a meal, family talk, worry about
someone, warn of danger, talk about a player, talk about a discovery, boast, complain, plan a visit, merchant
banter, guard report, game, notice injury, ask whereabouts, compliment work, tease. Each has preconditions
(relationship, traits, needs, knowledge, possessions, profession), a weight, and effects on both minds (bonds,
memories, emotions, needs, goals, knowledge; food and gifts are moved between possessions, never created).
Embodied inhabitants talk with speech bubbles (text displays riding the speaker, `speechBubbleSeconds`, one per
speaker, per-pair cooldown 90–150 s); unembodied ones meet abstractly at the same kind of place.

## 10. Information (`Mind.Info`)

Kinds: predator seen, person injured, person lost, player helped, player attacked, discovery, gift from a
player. Each piece has a subject, source (witnessed / told / culture), confidence (drops each retelling, more
for less honest people) and time; storage is bounded (`inhabitantKnowledgeLimit`, default 12) and old news
(> 4 days) is no longer passed on. Spread only through encounters inside a settlement (local). Hearsay about a
player shifts the listener's view a little — never as much as a first-hand experience. Secrets from
`docs/private/` are not part of any knowledge pool.

## 11. Reputation with players

Per-player bonds keyed by UUID (private to each inhabitant): first meetings, conversations, gifts, help,
witnessed attacks and hearsay. Attacks are only known to the victim and inhabitants who could see the attacker;
guards respond as in Stage 4. Recovery is possible through gifts and help (trust grows again; resentment falls).

## 12. Preferences, gifts and help

Each inhabitant has three liked items (culture, profession and personal pools), one disliked item, a favourite
place and topic. Gifts are validated on the server against the held stack; exactly one item is consumed (not in
creative); food is eaten when hungry, medicine heals when hurt, other items join their possessions (max 9).
Reactions: loved / appreciated / neutral / disliked. A cooldown (`giftCooldownTicks`) gives diminishing returns.

**Requests** arise only from real circumstances: injured (medicine), hungry with no food (food), or a personal
item goal (that item). They expire after two days. Fulfilling one creates a "helped by" memory, raises trust,
spreads as good news, and generous people may give one of their own belongings in thanks.

## 13. Goals (`Goals`)

Implemented: eat a favourite meal, visit a friend, improve skill, obtain an item, spend time with family,
explore nearby, recover, reconcile, learn news, make a friend. Generated from state and personality, advanced
by real activities/encounters, completed (memory, pride, purpose) or abandoned at expiry. Stage 6 extension
points (never generated yet): save up, improve home, find safer work.

## 14. Companions (`Companions`, `/companion`)

Eligibility: adult, healthy, not anyone's companion, friendly and trusting toward the player (affection and
trust ≥ 25), personality (adventurousness, courage, loyalty vs. homebody, guards' duty, children at home); one
per player by default (`maxCompanionsPerPlayer`). The answer to "travel with me?" is stable for a day.

Commands (dialogue or `/companion <cmd>` from anywhere): follow, wait, close, distance, rest, avoid (fights),
defend_self, defend_me, home, regroup, leave, status; "Carry some supplies" opens a 9-slot pack that **is** the
record's list (no copy, so no duplication); leaving returns the pack's contents to the player.

Following: pathfinds to the owner (faster when far); when stuck, more than `companionRelocateDistance` away or
on a different vertical level it relocates to a safe spot (solid floor, two free blocks, no fluid/fire)
near the owner, preferably outside their field of view. Combat: stance avoid/self/protect-me; retreats at 35 %
health; never targets players or inhabitants. Defeat **incapacitates** (the companion withdraws home to recover;
`companionPermanentDeath=false` by default). Shared experiences become memories (new places by biome, being
defended, joining). Owner logout/dimension change: the companion's body is removed and it waits; it reappears
near the owner on return. Regroup from home takes travel time (≈ 5 blocks/s, 10 s – 2 min).

## 15. Tiers, catch-up and performance

- **Active** (embodied, within `inhabitantActivationDistance` of a settlement; ≤ 24 per settlement, 96 total):
  thinks once per second, staggered by entity id; sensing is one entity query of 14 blocks.
- **Abstract** (all other records): round-robin, `mindUpdatesPerTick` (24) records every second tick; interval
  `abstractUpdateInterval` (1000 ticks = 1 h) near players, `distantUpdateInterval` (6000) beyond
  `distantSimulationDistance`. Catch-up runs hourly steps for at most the last 24 in-game hours.
- Records are the single source of truth; embodying first brings the mind up to date, then places the body
  where the current activity happens. Speech bubbles are transient entities (tagged, cleaned up on load).

## 16. Persistence

Everything lives in `data/underdeep_settlements.dat`. Append-only contracts: ordinals of `Trait`,
`Emotion`, `Need`, `Activity`, `Memory.Type`, `Bond.Kin`, `Mind.InfoType`, `Mind.Source`, `Mind.GoalType`,
`Mind.Request.Kind`, `Companions.Mode/Stance`, and the NBT keys written by `Mind.toNbt`. Bump `Mind.SCHEMA` and
add a migration in `Mind.fromNbt` for incompatible changes.

## 17. Multiplayer and security

All effects are server-side. Dialogue choices are validated (session belongs to the player, entity alive and
within 8 blocks, not asleep, rate-limited); gifts check the actual held stack; companions obey only their
owner; the pack inventory re-validates owner and distance every tick. Payloads are registered in common code;
the dedicated server never loads client classes. Players without the client mod get chat lines instead.

## 18. Development commands (`/underdeep npc …`, permission-gated)

`npc` (profile: traits, mood, emotions, needs, activity + reason + place, goals, request, preferences,
companion), `npc memories`, `npc relationships`, `npc knowledge`, `npc household`, `npc stats` (records, minds,
companions, active, abstract updates, catch-up hours, encounters, cost of a full think pass), `npc encounter`
(force one with the nearest neighbour), `npc befriend` (testing aid for companions), `npc advance <hours>`
(simulate the nearest settlement's unembodied inhabitants).

## 19. Stage 6 contracts

- `Mind.possessions` and `companionPack` are the inhabitant's inventory; economy may add/remove items there.
- `Mind.GoalType` SAVE_UP / IMPROVE_HOME / FIND_SAFER_WORK are reserved for economy and construction.
- `Brain.Context` is filled by callers; new facilities (shops with stock, construction sites) become flags.
- Knowledge (`Mind.Info`) is the hook for travelling news between settlements (caravans carry `TOLD` info).
- Per-player bonds are the reputation basis for prices and quests.
