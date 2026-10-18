# Cobblemon Guilds System

**Version:** `0.4.4-alpha` · **Minecraft:** 1.21.1 · **Fabric:** dedicated server only · Java 21.

This alpha targets Cobblemon 1.7.x and CobbleDollars 2.x. The mod itself is installed **only on the dedicated server** (`fabric.mod.json` uses `"environment": "server"`). Players need only the normal client mods required by the server. It does not register client entrypoints, add HUDs, use bossbars, scoreboards or sidebars.

> **ALPHA WARNING:** Automated compilation is not the same as gameplay testing. Back up your world, guild data, CobbleDollars balances and inventories. Test economy transactions and storage with disposable accounts before production deployment.

## Menu-first gameplay

- Open `/guilds` (or `/guild`). Create or browse guilds through vanilla chest menus.
- Click **Name**, **Tag**, **Description**, **Announcement**, **Custom Bank Amount** or **Invite Player** to enter a **private one-shot chat prompt**. Type a response normally in chat; the message is intercepted and never broadcast. Type `cancel` to cancel. The relevant menu reopens after a successful response.
- Create guilds without `/guild draft`: **this command has been removed**.
- Use click-to-edit menus for rank priorities, rank default status and permissions, member ranks and kicks (with confirmation), applications, guild colors, settings and icons.
- Access Guild Bank, Chest, Buff Shop and active effect timers, Quests, Bingo, Weekly Challenge, Achievements, Milestones, Bounties, Alliances, Experience and Home through the **Guild Main Menu**.
- Staff use `/guild admin` to browse every guild with the guild's name and leader, inspect their data, and manage authorized settings **without joining the guild**.
- `/guild home` actually teleports to the guild home; GUI Home offers teleport and setting the location. `/guild announcement` remains an optional shortcut, with announcement editing available in the menu.
- Real player head icons use online `GameProfile` skin data, with saved names for offline members. Names are cached when members are online; previously offline players may show as Unknown Player until they next join.
- The server-only PlayerManager mixin prefixes the **chat display name**, not the message text: `[GUILD] Username: Hello`. Its interaction with other chat/rank mods needs integration testing.

**No sidebar, bossbar, scoreboard, client UI mod, custom packet or HUD is required.** The only player-facing interfaces are vanilla chests and temporary chat prompts; `/guild effects` remains available.

## XP rebalance

Previously quest XP was awarded equal to the target count, so a quest requiring a large currency deposit could cause hundreds of levels. Now each quest grants **25–150 XP**, Bingo line rewards are capped to 150 XP, and the threshold to reach level N is `125 × (N − 1) × N`. A pair of high-value quests cannot instantly produce hundreds of levels. Legacy XP is migrated **once** on next load (`old XP / 200`, capped at 2,500), after which normal XP accrues using the new curve. This migration deliberately changes guild levels; take a backup before upgrading.

## Buffs

Cobblemon event-backed shop: Shiny x2/x3, Rare Spawn Bucket x2, catch x1.5 (permanent) / x2, battle EXP x2, battle EV x2 and friendship x2. Bank-funded purchases and timed stacking are implemented but **enabled by default** for the eight implemented buffs (`config/cobbleguilds.json`: `buffPurchasesEnabled: true`), and configurable from `/guild config`. No on-screen indicators are added. **Hidden Ability** and **Alpha** boosts are NOT implemented / not offered for purchase because they require a compatible spawn/ability integration for the specific Cobblemon addons used on the server.

## Quests and Guild features

Shared daily/weekly capture and deposit quests, rare-candy claims, contributor bonuses, Guild Bingo, weekly capture scoreboard, achievements, milestones, bounties, member capacity and guild chest upgrades, guild alliances, `/ga` allied guild chat, invitations/applications, guild home and activity logs. Quests require actual Cobblemon capture events, and guild data is persisted by UUID.

## Admin tools

Admin menu: `/guild admin`. Chat monitoring: `/guild chatspy` (OP 4 or corresponding LuckPerms node). Other commands remain available as optional shortcuts for operators, automation and command users; gameplay no longer requires draft, role, bank or announcement commands. Staff may also have emergency recovery commands for archived guilds.

## Dependencies

- Java 21; Fabric Loader 0.16.5+; Fabric API.
- Cobblemon 1.7.x; CobbleDollars 2.0.0+ Beta 5.1 compatible API.
- Optional LuckPerms.
- `gradle build` uses Gradle 8.10.2; GitHub Actions runs compilation and isolated Java domain tests.

**Main storage:** `<world>/cobbleguilds/guilds.json` and `chest-<UUID>.nbt`.
**Config:** `config/cobbleguilds.json`.

## Limitations still requiring dedicated-server testing

- Guild economy transactions and shared chests need crash/dupe tests before production use.
- Chat name mixin integration with rank providers and other chat decorators.
- Captures outside vanilla spawn pipeline, optional addons, and special Alpha/hidden-ability mechanics.
- Menus are server-side chests, not a graphical custom client screen. Offline skins may require a real profile texture cache; this alpha uses known/online profiles.
- Archived guilds can never be restored. See `docs/ADMIN_CONFIG_ARCHIVES.md` for archive-only administration, NBT-backed custom modded item quest rewards, menu-based configuration and permanent deletion safeguards.

## 0.4.1-alpha: Admin Configuration and Permanent Archives

- `/guild admin` opens separate **Active Guilds**, **Disbanded Guilds** and **Guild Configuration** areas. The archive viewer reveals former leader/member records, chest, bank and audit. Guilds are permanently disbanded, never restorable; staff can delete one or all archived guilds using two confirmations.
- `/guild config` lets OP 4 or `cobbleguilds.admin.config` manage quest types, targets and reward items, Bingo settings, daily/weekly quest counts, buffs and bank prices. Admin clicks with a held item to clone its entire ItemStack, including modded components, into the quest reward config.
- New files: `disbanded-guilds.json` per world; custom reward data in `config/cobbleguilds-quests.json` and Bingo reward metadata in `config/cobbleguilds.json`. Old completed quests retain their rewards after reload.
- Back controls are centered in the bottom row of the vanilla menus. No custom client mod or persistent HUD.

**Alpha warning:** Tests run against isolated guild rules; gameplay and economy regression testing are still required. Always back up a world before migration or archive deletion.

## 0.4.2-alpha — guild invitations and reward display

- Guild invites now direct players to `/guilds` → **Invitations** instead of a nonexistent `/guild join` command. The invitation browser has explicit accept/decline actions; private guilds respect whether an invite was pre-approved.
- Global chat no longer receives any guild tag: the signed-chat mixin and its registration were removed. Only `/g` shows `[TAG] Player: message`, with no extra `[Guild]` prefix. `/ga` retains `[Alliance]` and the guild tag.
- Application heads now use the real online player's profile, rather than an unskinned generic head. Offline players rely on vanilla profile resolution, so offline skins may take time or use a default.
- Quest and Bingo rewards now show the selected item's actual icon and user-facing name, never the raw registry ID in the reward line. Names fall back to readable title case if the mod's translation is unavailable on a dedicated server.
- Admin quest/bingo config menus show readable reward names. Saving/reloading quest templates synchronizes unclaimed, unexpired active quest rewards without resetting objectives/progress. Already claimed quests retain their payouts to prevent inconsistent rewards.
- The persisted stack snapshot remains the authoritative modded item, with all components/NBT, and the configured reward count is enforced when decoding.

**Upgrade note:** Back up your world before replacing the JAR. `/guilds` remains the primary player UI; no client installation or HUD is required. Runtime-in-game compatibility is still awaiting multiplayer tests.

## 0.4.3-alpha — Quest/Bounty/Bingo details and catch-rate pricing

- **Quest and Bounty listings** show paper items; the **nine Bingo tiles** show vanilla map items. Click a paper/map for a full chest detail screen with objective, shared progress, your contribution/minimum, expiry, a preview of the actual configured modded reward item and a claim button where eligible. Back remains in the bottom center.
- Catch Rate x1.5 stays permanent (default $500,000); Catch Rate x2 costs **10,000,000 CobbleDollars for 30 minutes** by default. Server admins' explicit `buffPrices.catch_x2` overrides are preserved; to use the default remove that key in `config/cobbleguilds.json` or edit it in `/guild config` → Buffs. Existing purchased timed buffs retain their original expiry; no retroactive extension or reduction.
- Catch buffs use Cobblemon's `POKEMON_CATCH_RATE` event and modify the target species' catch rate *during the capture calculation*. The **1.0x number on a Poké Ball is that BALL'S modifier**, not the guild bonus. It is normal for that tooltip to stay 1.0x. A Quick Ball has its own situational ball multiplier; the effects multiply the base catch rate, not add 1.5 or 2 to a ball's display. **The stronger catch buff takes precedence; x1.5 and x2 do not add or multiply together.** Pokémon species with already high catch rates can hit the game's 255 rate cap, making the difference smaller or zero. Verify with a lower-catch-rate species and enough throws; a small number of throws cannot reliably demonstrate the probability.
- Dedicated-server-only, no HUD, sidebar, bossbar or permanent on-screen messaging. Runtime behavior still needs in-game validation.

## 0.4.4-alpha — Buff tooltips and Alliance ChatSpy

- The buff-info icon in both **Guild Buff Shop** and **Active Guild Effects** is now the registered `cobblemon:poke_ball` item, not a slime ball.
- English GUI lore documents highest-tier-only behavior: Catch Rate x1.5 and x2 never stack. The permanent x1.5 bonus resumes when the timed x2 expires, if owned. Shiny Luck x2/x3 use the same strongest-active-tier rule; the lower tier resumes only if its timer remains active.
- The **Alliance ChatSpy** listener already existed: `/guild chatspy` can observe `/ga` across guilds. Moderators with ChatSpy enabled now receive a single `[GuildSpy-Alliance]` copy even when in a participating guild, avoiding ambiguous regular/spy messages. Respect `chatSpyEnabled` and `cobbleguilds.admin.chatspy`.
- This update changes no capture/shiny/rare multipliers or purchase pricing. **No client installation, HUD, sidebar or scoreboard** was added.
- See `docs/STRESS_TEST_CHECKLIST.md` for current high-risk unverified paths.
