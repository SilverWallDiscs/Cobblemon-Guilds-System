# Rare Spawn Boost x2 — Server QA

Cobblemon 1.7's `SPAWN_BUCKET_CHOSEN` event exposes the chosen bucket, mutable selection and effective weight table. The `rare_spawn_x2` guild buff re-samples by multiplying the weight of `rare` and `ultra-rare` buckets by 2 **only when SpawnCause identifies an online player in a guild with the active buff**.

- Confirm a player-created wild-spawn attempt triggers a bucket-selection event.
- Confirm non-player spawn causes keep the original bucket unchanged.
- Verify rare/ultra-rare spawn bucket weight grows, not the total number of spawn attempts.
- Test alongside Cobblemon configuration changes and other spawn-influence addons.
- Check `/guild effects` countdown across relogs and server restarts.
- No buffs are enabled for purchase by default (`buffPurchasesEnabled=false`).

Alpha and Hidden Ability boosts remain unsupported in Cobblemon 1.7 without confirming the server's additional spawn mechanics.
