# Cobblemon Guilds System

**CobbleGuilds — early alpha source release (0.1.0-alpha)**

A **dedicated-server-only** Fabric mod for **Minecraft 1.21.1**, written for the Cobblemon ecosystem and integrated with **CobbleDollars 2.x**. The server mod ID is `cobbleguilds`.

> ⚠️ **Uncompiled / unverified prototype.** Dependencies were unavailable in the authoring environment, so there is **no tested JAR**. Do not install on a production server or trust its accounting/persistence without compiling it, testing it and backing up your world.

## Strict server-side design

- `fabric.mod.json` declares `"environment": "server"`.
- No client Java classes, client entrypoints, custom client packets, HUD, sidebar, scoreboard, bossbar, actionbar or overlay are used.
- GUI buttons use normal Minecraft chest containers and server-side click handling.
- Guild effects are queried through **`/guild effects`**, never a persistent overlay.
- The mod is designed so clients **do not install CobbleGuilds** (Cobblemon/CobbleDollars themselves may have their own client requirements).
- Guild chat is explicit via `/g <message>`; normal global-chat tag decoration is not yet implemented.

## Implemented in source (not yet runtime verified)

| Area | Current functionality |
|---|---|
| Guild creation | 9-slot join/create menu; 36-slot creation draft; name and tag via commands; color palette, public/application mode, non-consuming item icon selection |
| Search | Browse clickable guild icons in a paginated chest menu |
| CobbleDollars | BigInteger player balances, guild bank deposits/withdrawals and creation fee, server-side authorization |
| Gholdengo daily fee | Configurable daily fee and local timezone; broadcasts 10/5/1-minute reminders; unpaid guilds marked closed; assets preserved for admin recovery |
| Roles | Leader/Member, ranked permissions, create/grant/revoke/default/assign/rename with hierarchy checks |
| Guild chests | Vanilla container mechanics, 9 to 54 slots, persistent NBT, permission checks, configurable row upgrades |
| Guild chat | `/g` members-only messages and optional admin `/guild chatspy` |
| Cooperative quests | Capture quests (100, 50 daily and 500 weekly by default), contributions, minimum participation, offline persistence, in-menu view, Rare Candies + contributor bonus, guild XP |
| Admin | Read guild list in a GUI without joining, open a specific guild, audit history, guild-scoped online kick/ban, unban by UUID, Chat Spy |
| Settings | Name, tag, description and public/private status via commands |

## Not implemented — do **not** advertise as working

- **Functional Cobblemon buffs:** Shiny, EXP, catch rate, EV, hidden ability, Alpha, friendship and rarity multipliers. **Purchases are deliberately disabled, so no money can be taken for nonworking effects.** `/guild effects` is a textual status command.
- Complete guild quests list (types, evolutions, PvP, Alpha, eggs, configurable catalogs, payouts from configurable item tables), real Guild Bingo, alliances, bounty boards, streaks and leaderboards beyond XP.
- In-chat name-tag decoration, client-side anvil/book text input, custom book confirmation, and full member profile head rendering.
- Full Guild Admin edit-as-leader actions (there is an inspection/menu foundation, plus moderation) and full Guild recovery/escrow payout interface.
- Guild home / teleport, robust atomic transfer journaling, multi-world and compatibility integration tests.

## Dependencies and building

- Java 21
- Minecraft 1.21.1
- Fabric Loader 0.16.14+ (configured version)
- Fabric API 0.115.6+1.21.1 (configured version)
- Cobblemon 1.7.x for the tested **API target** (1.8.x unverified)
- CobbleDollars 2.0.0 Beta 5.1+ (integration API based on the author's PlayerShops project; later versions unverified)
- Gradle 8.10.2 (not bundled here)

`gradle build` from the project directory, after installing Gradle and downloading Maven dependencies. Resolve any API mismatches before deployment. A Gradle wrapper JAR was not accessible, so this project does not include a fake `gradlew`.

## Commands available in source

```text
/guilds                            /guild                           /guild browse
/guild create                      /guild draft name <text>         /guild draft tag <tag>
/guild draft description <text>    /guild join <name>               /guild invite <online-player>
/guild accept <online-player>      /guild leave                      /guild disband confirm
/guild bank balance                /guild bank deposit <amount>      /guild bank withdraw <amount>
/guild chest                       /guild chest upgrade              /guild quest
/guild quest claim                 /guild effects                    /guild top
/guild buff buy <id>               /guild settings public            /guild settings name <text>
/guild settings tag <tag>          /guild settings description <text>
/guild role list                   /guild role create <id> <priority>
/guild role default <id>           /guild role grant <id> <permission>
/guild role revoke <id> <permission>
/guild role assign <online-player> <id>
/guild role rename <id> <name>     /g <message>
/guild admin                       /guild admin open <name>
/guild admin logs <name>           /guild admin kick <name> <online-player>
/guild admin ban <name> <online-player>
/guild admin unban <name> <UUID>   /guild chatspy
```

## Permissions

OP level 4 or LuckPerms node `cobbleguilds.admin` for all admin abilities. Granular nodes:
`cobbleguilds.admin.gui`, `.manage`, `.bank`, `.quests`, `.buffs`, `.moderation`, `.logs`, `.chatspy`.
LuckPerms is optional and checked using reflection without adding a client dependency.

## Data layout

- Global settings: `config/cobbleguilds.json`
- Per-world guilds: `<world>/cobbleguilds/guilds.json`
- Per-world chests: `<world>/cobbleguilds/chest-<guild UUID>.nbt`
- Staff spy preferences: `<world>/cobbleguilds/spy.json`

Daily fee settings default to **enabled, 10,000 CobbleDollars** and guild creation to **1,000,000 CobbleDollars**. `timeZone: "system"` uses the dedicated server system clock; specify an IANA timezone if needed (e.g. `America/Santiago`). Fees are charged at the first online tick of the new server-local day, including after an offline restart; new guilds receive no immediate charge.

Closed guilds remain stored with their bank and item data. Staff recovery is still pending; back up this data before enabling recurring fees on a real world.

## Development

`src/test/java/dev/silver/cobbleguilds/GuildDomainSelfTest.java` is a zero-dependency smoke test of role limits, guild status, big balances, cooperative quest threshold and single-claim behavior. It does **not** replace integration tests against a dedicated Fabric server.

No third-party Cobblemon or CobbleDollars source is copied into the mod. Those APIs are compile-only dependencies.