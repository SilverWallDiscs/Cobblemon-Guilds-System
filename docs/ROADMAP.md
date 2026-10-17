# CobbleGuilds roadmap — specification tracking

Source-of-truth requirements: `Instrucciones.txt` (included in this package).

- [x] Server-side-only entrypoint, vanilla chest menus and text command output.
- [x] Guild creation cost, guild list, colors, configurable visibility, editable draft text via commands.
- [x] BigInteger bank and guild daily fees; server-side restrictions.
- [x] Vanilla physical chest and row-based upgrades with persistent NBT storage.
- [x] Guild-chat command and chat spy, permission checks and basic admin list/moderation.
- [x] Guild permission ranks, with hierarchy restrictions and default rank.
- [x] Basic guild quest contributions on Pokémon capture + persistent claims.
- [ ] Build and dedicated-server integration test.
- [ ] Full admin modifications, ban reason data, member heads and leader transfer book confirmation.
- [ ] Colored tag insertion in chat without overwriting other chat formatting plugins.
- [ ] Full 54-slot menu detail and native sign/anvil/book input, chat fallback.
- [ ] Live Guild Buff effects on Cobblemon's per-player/pokémon hooks. Never activate billing until hooks verified.
- [ ] Full daily/weekly quest configuration and remaining objective types.
- [ ] Guild activity auditing for every action and tamper-safe guild chest rollback.
- [ ] Guild recovery GUI, bank/item escrow claim with double-claim prevention; OP-only restore command prepared.
- [x] Guild homes, member capacity upgrades and announcements (code written; not runtime verified).
- [x] Quests rotation: 2 daily + 1 weekly chosen deterministically, capture and net-new bank funding objectives.
- [x] Signed chat decoration using Fabric message decorator (runtime compatibility requires testing).
- [x] Guild leadership transfer with expiring explicit confirmation command.
- [x] GitHub Actions build workflow for reproducible remote verification.
- [ ] Bingo, inter-guild challenges, bounties, alliances, broader upgrades and achievements.
- [ ] Thorough anti-dupe tests (inventory close, forced kill, concurrent sessions, player logout).
- [ ] Long-term treasury reconciliation and world restore procedures.
- [ ] GitHub publish complete source + CI build verification.
