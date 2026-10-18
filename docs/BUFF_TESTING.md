# Experimental guild buff QA (0.2.0-alpha)

This is an **unverified source-level implementation**. Do not enable real guild-bank spending until CI compiles and a test server runs cleanly.

## Dedicated-server compatibility

Install CobbleGuilds only in the server's `mods` folder. Use Minecraft 1.21.1, Java 21, Fabric, Fabric API, Fabric Language Kotlin, CobbleDollars and Cobblemon 1.7.x. Do not install CobbleGuilds on clients. Keep `buffPurchasesEnabled: false` initially.

## Test script

1. Back up the test world. Start an isolated server. Verify boot and `/guilds` GUI.
2. Create a guild, deposit test CobbleDollars and ensure bank ledger and permissions behave as expected.
3. Set `buffPurchasesEnabled: true` in `config/cobbleguilds.json`, **restart** the server, and have the leader buy a buff using `/guild buff buy <id>`.
4. Run `/guild effects` to verify the remaining time. Purchase the same timed buff twice and confirm the duration stacks to ~120 minutes. Buy a stronger tier and verify only the stronger multiplier is applied while it is active.
5. Confirm Shiny x2/x3 applies only to BestSpawner natural wild Pokémon, not to gifts/trades/admin spawns. Observe ample spawn samples; a few encounters cannot statistically prove the rate.
6. Test x1.5 and x2 capture bonuses with the same species and Poké Ball; verify catch rate is never above 255.
7. Verify EXP x2 and EV x2 only on battle-sourced gains; item/command XP and EV mutations must not receive a bonus.
8. Verify friendship bonus only increases positive friendship changes and does not boost decreases.
9. Disconnect guild members, restart, and verify buffs and expiry remain intact; fees and banks must remain consistent.
10. Ensure no scoreboard, sidebar, actionbar, custom packet, or client installation is required.

Rare spawn weighting is implemented for player-caused bucket selection; test custom spawners independently.

## Release gate

A GitHub Actions successful Java 21 Gradle build plus dedicated-server functional tests are required before the JAR can be represented as validated.
