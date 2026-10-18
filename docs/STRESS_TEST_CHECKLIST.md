# CobbleGuilds 0.4.4-alpha: Dedicated-server stress-test checklist

**Code review findings, not claims of tested functionality.** Make a backup of the world, bank accounts and guild data; use disposable player accounts.

## Critical: potential loss and duplication
1. Guild chest: two players opening simultaneously, shift-click, drag, cursor items, close/disconnect/restart, chest upgrade, modded item components and intentional server crashes. Verify total item counts.
2. Guild bank: simultaneous deposit/withdraw from different players, negative/overflow inputs, permission checks, restart mid-transfer. Transfers lack a complete crash-safe atomic journal.
3. Disbanded guilds: disband with funds and chest items, confirm archive contains former members and exact contents; delete a single archive and then all archives, confirming active guilds remain unaffected. Archive deletion is permanent.
4. Quest rewards: edit custom modded rewards, reload quest configuration, complete and claim offline, full inventory, double-click/rapid claim, relog and server restart. Claimed rewards must never duplicate.

## Buff verification
5. Catch Rate: player's 10/10 controlled catches with x2 is encouraging but not a statistical proof of the exact multiplier; verify that permanent x1.5 becomes effective again when x2 expires.
6. Shiny Luck: only acts on Cobblemon BestSpawner wild Pokémon; source selects the nearest online member within 48 blocks. Eggs/trades/admin spawns and addons may bypass the event. A reliable rate test needs very large spawn samples and controlled nearby players. After x3 expiry, x2 returns **only if its timer is still active**.
7. Rare Spawn x2: only adjusts rare/ultra-rare bucket weights for `SpawnBucketChosenEvent` with a player spawn cause. If a spawner has no player cause or different bucket naming, the purchased effect may do **nothing**. The feature does not double total spawn counts and remains unverified.
8. EXP, EV and friendship: verify boosts with battle gains, party/PC Pokémon, level/EV limits, candy rewards and reconnects.

## Chat, roles and timing
9. `/guild chatspy` + `/ga`: test a moderator in and outside the alliance, after reconnect, without the permission, with config disabled; one `[GuildSpy-Alliance]` line per alliance message, no duplicates, no leakage to regular players. Verify `/g` separately.
10. Invitations, offline members, pending applications, rank privilege escalation, admin GUI permissions, banned players, leader transfer, chat input cancellation, home teleport, midnight fees and daily/weekly resets.
11. Offline player skulls and interactions with other chat mods / LuckPerms may still require compatibility testing.

**Incomplete / known limits:** Hidden Ability and Alpha boosts are not sold; Shiny and Rare are dependent on particular spawn-event paths; unclean-crash recovery is not fully transactional; Minecraft multiplayer runtime validation is not provided by a passing Gradle build.
