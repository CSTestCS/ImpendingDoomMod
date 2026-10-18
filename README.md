# Impending Doom

A NeoForge mod for **Minecraft 26.3**. Every few Minecraft days something terrible happens.

Events come in two kinds:

- **Minor events** run until the next event starts. Some, like a tornado or a raid, end sooner on their own.
- **Major events** run until the next *major* event starts, so minor events happen during them.

## The cycle

- An event begins every **7 Minecraft days**. The mod counts Minecraft days (the world's day time), not real time, so sleeping through the night brings events sooner.
- The first **3** events are minor and the 4th is major, and that repeats forever.
- When an event begins, the mod picks the next event at random. A small **HUD element** (top left by default) shows every player the next event and counts down the time until it begins (Minecraft time: `2d 5h`, `5h 30m`). Each player can hide or move their own with `/impendingdoom hud`; the choice is saved in that player's `config/impendingdoom-client.toml`.
- The seasons are major events. They don't have to be evenly spaced, but they always come in order: **Summer → Fall → Winter → Spring → Summer…** A season stays in effect, alongside other major events, until the next season begins.
- In game, players are only told when an event begins (its name as a title and one chat line). Events are never explained.

Both numbers can be changed with gamerules:

```
/gamerule impendingdoom:days_between_events 7
/gamerule impendingdoom:minor_events_before_major 3
```

### Commands

| Command | |
|---|---|
| `/impendingdoom status` | current minor and major events, the next event and when it arrives (anyone can run it) |
| `/impendingdoom list` | every event and its id |
| `/impendingdoom trigger [event]` | start the foretold event (or the one named) right now (op) |
| `/impendingdoom next <event>` | choose the next event (op). `foretell` is an alias |
| `/impendingdoom disable <event>` / `enable <event>` | stop an event from ever being chosen, or allow it again. Disabling a running event ends it (op) |
| `/impendingdoom disabled` | list disabled events |
| `/impendingdoom hud show\|hide\|toggle\|reset` | show or hide your own next-event HUD (anyone) |
| `/impendingdoom hud position <anchor> [x] [y]` | move your HUD. Anchors: `top_left`, `top_center`, `top_right`, `middle_left`, `middle_right`, `bottom_left`, `bottom_right`; x and y are offsets in GUI pixels from that corner (anyone) |
| `/impendingdoom end minor\|major` | end the running event (op) |
| `/impendingdoom days <n>` / `minors <n>` | shortcuts for the two gamerules (op) |

The first event of a new world can be picked with the **First Doom Event** button on the Create World screen (default: random), or with `firstEvent` in the config for servers.

You can tune the rest in `config/impendingdoom-startup.toml`: the valid mutations list, horde mobs, small meteor frequency and burst size, Kaiju mobs, the hunter blacklist, horde size, Kaiju scale, how fast floods rise, and whether events may destroy blocks. The `mobGriefing` gamerule is also respected.

## Minor events

| Event | What happens |
|---|---|
| **Incoming Raid** | Each player's respawn point is raided (players who share a spawn share a raid). It uses vanilla's wave table, captain banner, raid bar, horn and Hero of the Village reward. No village or villagers are needed, because the players are the target. Ends when every raid is beaten. |
| **Monster Mutations** | Mobs on the valid mutations list spawn bigger and stronger, with random potion effects and one ability: leaper, venomous, volatile (explodes on death), blinker (teleports behind you), splitter, thorny, webber or frenzied. |
| **Tornado** | A tornado spawns 64–80 blocks away and slowly tracks one player, so you can lead it away from your builds. It pulls in and flings entities and rips up weak blocks, but only out in the open on the surface. Natural blocks it throws (dirt, grass, sand, stone, logs, leaves and so on) land as **decaying** copies that wear away layer by layer, like melting snow, over 2 days; mining one still drops the original block. It lasts 3–5 minutes and can be seen from far away. |
| **Electrical Storm** | A long, intense thunderstorm. Lightning charges any monster it hits instead of burning it. Strikes can spawn fire-immune charged breezes. Attacks by charged mobs (and charged creepers) inflict **Stunned**: 1 second with no movement, attacks or interaction. You can't sleep through it unless you wear wool on your head. |
| **Distortion** | Armor that loses 25% of its max durability during the event gets the **Warped** enchantment. Warped armor pops off into your inventory with a break sound (or drops if your inventory is full) and can't be worn. It stays Warped after the event until you repair it or use a grindstone. |
| **Thieves** | Thieves (illagers, so iron golems attack them on sight) spawn at night and head for your chests and barrels. They climb walls, tunnel through dirt, grass, sand and gravel, and glide down from heights. After stealing they put on the best armor, wield the strongest weapon, stop despawning, and flee to the nearest cave. There they sit admiring the loot until a player or golem comes near, then fight. Killing a thief drops everything it stole. |
| **Blood Moon** | Red nights with more monsters, all carrying potion effects. Zombies often carry shovels, pickaxes or axes and slowly break blocks their tool is good at. A creeper hunting you explodes the moment it loses its path to you. You can't sleep unless you wear wool on your head. |
| **Hunted** | Each player gets a pack of 5 named, armed hunters, drawn from every neutral or hostile mob in the game, modded ones included. Every hunter wears an indestructible helmet, piglins and hoglins don't zombify, and pack members never fight each other. Every two minutes the pack is told its target's coordinates, and the pack is re-summoned if the player gets away from it. When a hunter kills its target it taunts in chat and gets a permanent upgrade: a potion effect (never regeneration or instant health), or more health, damage, speed or armor. Surviving hunters and their upgrades are saved and come back next time; new recruits replace the dead. |
| **Famine** | Food restores 50% less, crops don't grow (bone meal included), and animals drop only one food item. |
| **Hordes** | Three times a night large groups of one random hostile mob spawn around each player and roam like pillager patrols, attacking players they see. Hitting a member can call zombie-style reinforcements while the event lasts. Horde members are the only mobs from this mod that can call for backup. |
| **Mortis's Bloodlust** | Three or four times a day, a random 30% of mobs get the **Bloodlust Haze** (red haze, shaking): 2 minutes for hostile mobs, 5 for the rest. If it runs out naturally the mob becomes bloodlusted: blood drips from it, its eyes glow red, it attacks any mob it finds, and it grows bigger and stronger with each kill. When the event ends, passive and neutral mobs stop attacking (they keep the size, health and damage they gained); hostile ones stay bloodlusted. |
| **Blackout** | Torches, lanterns, campfires and jack o'lanterns flicker and burn out; relight them with flint and steel. Players get Darkness at night and in caves. |
| **Paranoia** | Heavy fog, false footsteps behind you, mob sounds that aren't there, and the occasional hallucinated monster or Herobrine that only you can see and that vanishes when you look straight at it. |
| **Wildfire** | Fire spreads faster, grass and leaves catch easily, grass blocks burn and turn to coarse dirt, and the odd brush fire starts on dry days. |
| **Swarms** | Swarms of 10–17 half-size bees (bats during Fall) with one heart each roam together. When one spots a player, the swarm circles them, then dives in together, regroups and repeats until it loses sight of them. |
| **Soul Thieves** | Passive animals become possessed and creep toward you. Touching one drains your levels. After it takes more than 10 levels it bursts into soul fire, grows, and hunts you. Killing it releases the stolen experience. |

## Major events

| Event | What happens |
|---|---|
| **Summer** | You have to manage **thirst** (a HUD bar above hunger). It drops slowly, faster in the sun, in hot biomes, while sprinting and while wearing heavy armor (iron, diamond and netherite, tag `impendingdoom:heavy_armor`). Rain stops it dropping, and swimming fully underwater slowly refills it. Drink water bottles, milk, honey, soups or juicy fruit, sneak with an empty hand while looking at water, or sneak and look up at the rain. |
| **Fall** | Leaves turn red, orange and yellow and shed **fallen leaves** that stack like snow. A full block becomes a **leaf pile**, a spawner that works even in light and spawns decaying creepers. These have weaker explosions but always know where you are. Tiny spiders spawn at any time of day in roaming packs. Skeletons spawn more often with any weapon or armor. |
| **Winter** | Clear weather is snow and every other weather is a blizzard (dense white fog, fast snowfall). Snow builds up anywhere open to the sky. Crops and saplings break and can't be planted, grown or bone-mealed. Without a full set of leather or netherite armor (tag `impendingdoom:warm_armor`), or a fire-based light within 4 blocks, you start **Freezing**. |
| **Spring** | Snow melts fast in biomes that don't normally have snow, snow blocks included. Crops grow faster and give more. Any animal spawn may be a larger, hostile **alpha**. |
| **Herobrine** | Dense fog and exactly one Herobrine. He appears at a distance and stares, and hides behind blocks in the direction you're heading, vanishing when spotted. He casts random magic (lightning, levitation, fireballs, wither skulls, evoker fangs, darkness), summons monsters, and sometimes sneaks up behind you to break the block under you when you stand on a one-block-thick floor. Hitting him just makes him vanish. |
| **Kaiju** | Every night a monster spawns at 20x scale (configurable) with far higher stats. It always knows where players are and smashes through terrain to reach them, with a boss bar. If there's more than one Kaiju, they fight each other. |
| **Unstable** | Earthquakes at random times: camera shake, rumbling, stumbling. During a quake, breaking or starting to mine a block turns a large, randomly shaped area around it into falling blocks. |
| **Meteor Shower** | End stone meteors fall from the sky, blow a crater, and leave an end stone boulder with an ore core. The ores come from `#c:ores`, so modded ores are included, and every meteor has at least one diamond ore. |
| **Flooding** | A temporary, layered water rises from sea level and evenly fills every air block at its height. It never makes source blocks or spreads, and you can swim and drown in it. It rises slowly each day (a quarter block a day by default) and it rains the whole time, then drains to nothing over the last 3 days before the next major event. |
| **Volcanic Flooding** | The same, but with lava, rising from deep underground and only filling underground space. |
| **Solar Eclipse** | Daytime goes dark. Monsters spawn by day and don't burn, and wardens and creakings spawn like ordinary mobs. |
| **Nether Invasion** | Ruined portals nearby are rebuilt and lit. Parties of hostile piglins and brutes in gold and netherite gear pour out of every lit portal and roam the land. Some ride hoglins (which are friendly to them) and some ride hostile ghasts wearing harnesses. They hunt passive mobs the way they hunt hoglins and throw **Splash Potions of Netherflame**, which turn the land into nether blocks. |

## Other features

- **Mob behaviour.** Groups the mod spawns (hordes, swarms, invasion parties, spider packs) roam like pillager patrols; single mobs wander like normal mobs. They only attack players they can see: an invisible player, or a mob with Blindness, never gets targeted. Mobs meant to hunt you (hunters, Kaiju, decaying creepers, soul thieves, Herobrine's summons) still always find you.
- **Wool helmets.** Any wool block can be worn on your head. While you wear it the world is completely black and only the interface is visible. It's the only way to sleep during a Blood Moon or an Electrical Storm.

- **Walking creaking hearts.** Every creaking the mod spawns (eclipse, hunters, Herobrine, hordes and so on) comes with a creaking heart on legs that runs away and hides from players. The creaking can't be hurt while its heart lives, and killing the heart kills the creaking. If a hunter is a creaking, its heart is an ally of the pack but doesn't count as one of the five.
- **Armor refit.** Any hostile or neutral mob can be given armor by the mod, and it's drawn on mobs whose models normally don't show armor (spiders, creepers, illagers, endermen, modded mobs, and so on). It uses the player armor model scaled by the mob's size compared to a player (0.6 wide, 1.8 tall), standing on the mob's feet. It isn't shrink-wrapped to the mob's own model.

## Download

The ready-to-use mod jar is in [`dist/impendingdoom-1.0.0.jar`](dist/impendingdoom-1.0.0.jar). Drop it into the `mods` folder of a NeoForge 26.3 installation.

Every push to the repository is built automatically by GitHub Actions (`.github/workflows/build.yml`):

1. It compiles the mod against the newest NeoForge release for Minecraft 26.3.
2. It boots a headless NeoForge server with the mod as a startup check.
3. It commits the resulting jar to `dist/` and attaches it to the run as a download.

To build it yourself instead, run `./gradlew build` (Java 25). The jar is also copied to `dist/`.

Textures are generated by `tools/gen_textures.py`. When a build fails, `tools/api-dump.sh` prints the Minecraft/NeoForge API signatures involved into the CI log to help with porting.
