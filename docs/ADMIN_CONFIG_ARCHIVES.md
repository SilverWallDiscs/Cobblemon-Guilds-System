# Guild administration (0.4.0-alpha)

## Archive-only disband flow

- An active guild that disbands for any reason is moved out of `guilds.json` into `<world>/cobbleguilds/disbanded-guilds.json`.
- The `guilds.json` loader also migrates older guilds with `closed=true` to the archive the first time 0.4 boots. Archive is written before active removal to reduce crash-loss risk.
- **No restore feature exists.** Former members do not count as guild members and cannot join the archived guild.
- Admin: `/guild admin` → `Disbanded Guilds` → choose an archive. Inspect original leader and former members, audit logs and bank; authorized staff can access the historic vanilla chest and withdraw historic funds.
- Staff with `cobbleguilds.admin.manage` can delete one archive or all archives through **two confirmation dialogs**. Deletion permanently destroys guild records and its `chest-<UUID>.nbt` save file. Deleted guilds have no recovery path in the mod; world backups are the only recovery.
- Separate archived guild bank access uses `cobbleguilds.admin.bank` or OP 4. Archive chest access uses `cobbleguilds.admin.manage` or OP 4. Archive browser requires `cobbleguilds.admin.gui` or OP 4.

## In-game configuration

Run `/guild config` as OP 4 or with `cobbleguilds.admin.config` (or umbrella `cobbleguilds.admin`):

- Daily and Weekly Quest Templates: add/remove template, edit name, objective, type/species qualifier, goal, minimum participation, daily/weekly rotation, enabled status, item + amount.
- To set a quest reward, **hold any item, including a modded item, then click `Reward`**. A snapshot of its entire ItemStack, including data components, is stored in `config/cobbleguilds-quests.json` as compressed-base64 NBT. The actual item remains in the admin inventory. The reward is a copy, not the original item.
- Bingo Settings: enable/disable, XP per completed row, reward item and quantity. Bingo template choices are pulled from quest pool and its reward overrides are saved in `config/cobbleguilds.json`.
- Buff Settings: enable/disable the whole shop or individual working buffs, edit bank prices. These persist in `config/cobbleguilds.json`. Unsupported Alpha and Hidden Ability boosts cannot be activated until their real Cobblemon integration exists.
- Change number of daily (1–5) and weekly (1–3) quests, daily fee and creation price.
- Separate **Reload Server Config** and **Reload Quest Templates** buttons validate their respective files before applying them. No guild chest/bank/quest progress resets occur. **Changes to quest rewards apply to newly rotated quests only**, not claims already earned.

## Compatibility and safety

- Fabric 1.21.1, Java 21, server-only. No custom client packets, sidebars, or HUDs.
- Existing `cobbleguilds.json` from the older alpha migrates to `configVersion=2`, enabling purchase of implemented working buffs. Operators can disable them at any time.
- Before upgrading, back up your world, inventories and CobbleDollars accounts. Legacy data migration, archived asset withdrawal, and custom NBT reward delivery still require in-game integration testing.
