# CobbleGuilds roadmap — specification tracking

Source-of-truth requirements: `Instrucciones.txt` (included in this package).

- [x] Server-side-only entrypoint, vanilla chest menus and text command output.
- [x] Guild creation cost, guild list, colors, configurable visibility, editable draft text via commands.
- [x] BigInteger bank and guild daily fees; server-side restrictions.
- [x] Vanilla physical chest and row-based upgrades with persistent NBT storage.
- [x] Guild-chat command and chat spy, permission checks and basic admin list/moderation.
- [x] Guild permission ranks, with hierarchy restrictions and default rank.
- [x] Capture/shiny/type/unique-species + net-new bank-funding quests, deterministic daily/weekly rotation and persistent claims (source only).
- [ ] Build and dedicated-server integration test.
- [ ] Full admin modifications, ban reason data, member heads and leader transfer book confirmation.
- [x] Fabric server-side chat decorator prefixes guild tag to message content (rank plugin compatibility must be tested).
- [x] Basic settings/color/bank chest GUI and quests menu, built on vanilla inventory slots.
- [ ] Native book/anvil editing and fully interactive rank/admin screens.
- [ ] Live Guild Buff effects on Cobblemon's per-player/pokémon hooks. Never activate billing until hooks verified.
- [x] JSON-configurable daily/weekly quests for implemented objective types.
- [ ] Alpha, EV/EXP, evolution, PvP, fishing, broader objectives and full PvP anti-farming.
- [ ] Audit completeness, tamper-safe chest recovery and financial transaction reconciliation.
- [ ] Guild recovery GUI, bank/item escrow claim with double-claim prevention; OP-only restore command prepared.
- [x] Guild homes, member capacity upgrades and announcements (code written; not runtime verified).
- [x] Quests rotation: 2 daily + 1 weekly chosen deterministically, capture and net-new bank funding objectives.
- [x] Signed chat decoration using Fabric message decorator (runtime compatibility requires testing).
- [x] Guild leadership transfer with expiring explicit confirmation command.
- [x] GitHub Actions build workflow for reproducible remote verification.
- [x] Reciprocal guild alliances, shared `/ga` chat, weekly capture challenge leaderboard, bounties and milestone achievements (source only).
- [x] Weekly 3×3 cooperative Bingo GUI and repeat-safe line XP rewards (source only).
- [ ] Challenge rewards, seasonal prize resets, broader upgrades and achievement rewards.
- [ ] Thorough anti-dupe tests (inventory close, forced kill, concurrent sessions, player logout).
- [ ] Long-term treasury reconciliation and world restore procedures.
- [x] GitHub source repository and Java 21 CI workflow.
- [ ] Verified Fabric CI build and runtime integration tests.

- [x] Staff `/guild admin edit <tag>` controls for guild name, tag, color, description, privacy, icon, chest capacity, fee waiver and non-leader ranks (source only).
- [x] Member GUI with current online usernames, roles and displayed online CobbleDollars balances.
- [ ] Validate Gradle build and Minecraft integration, including Cobblemon capture events and signed chat.
