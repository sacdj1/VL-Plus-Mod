# VL+ Weapon Models example pack - Bow

Demonstrates the Weapon Models module's four states (Idle / Activate / Active / Deactivate)
on a plain vanilla bow, using nothing but a resource pack - no mod code involved on the pack side.

## How it works

`assets/minecraft/models/item/bow.json` adds four `custom_model_data` overrides on top of the
normal bow model:

| State      | custom_model_data | Model                          | Texture                    |
|------------|--------------------|--------------------------------|-----------------------------|
| Idle       | 500001              | `item/vlplus_bow_idle`        | unmodified bow texture      |
| Activate   | 500002              | `item/vlplus_bow_activate`    | animated flash, 4 frames    |
| Active     | 500003              | `item/vlplus_bow_active`      | animated pulsing glow, loop |
| Deactivate | 500004              | `item/vlplus_bow_deactivate`  | animated fade-out, 4 frames |

Activate/Deactivate are quick flashes (the Weapon Models module shows each for ~250ms by
default before auto-advancing); Active loops for as long as the ability stays up.

## Per-tier variants

The pack also ships a per-tier set, using the real colors this server's own lore actually uses
(confirmed from captured item lore, not guessed). Tier number and "Unique" rarity are two
separate things here - a Tier 4 item and a Tier 4 Unique item are different rarities, not just a
recolor of each other, so each gets its own model:

| Tier | Base color (plain)       | Unique color (flashes base ↔ green) |
|------|---------------------------|--------------------------------------------|
| 4    | dark_purple (`#AA00AA`)  | flashes dark_purple ↔ dark_green         |
| 5    | gold (`#FFAA00`)         | flashes gold ↔ dark_green                |
| 6    | dark_red (`#AA0000`)     | flashes dark_red ↔ dark_green            |
| 7    | Cosmic: dark_blue (`#0000AA`) - no separate Unique variant, Cosmic is already the top rarity |

Base tiers get a static idle tint in their own color. Unique tiers animate their idle, cross-
fading between that same base color and the shared Unique green - so a glance tells you both
"what tier" and "that it's specifically the Unique version" at once. Activate/Active/Deactivate
keep the same flash-in/pulse/flash-out shape as the plain example, recolored to match.

| Variant   | Idle CMD | Activate CMD | Active CMD | Deactivate CMD |
|-----------|----------|---------------|------------|-----------------|
| T4        | 500041   | 500042        | 500043     | 500044          |
| T4 Unique | 500045   | 500046        | 500047     | 500048          |
| T5        | 500051   | 500052        | 500053     | 500054          |
| T5 Unique | 500055   | 500056        | 500057     | 500058          |
| T6        | 500061   | 500062        | 500063     | 500064          |
| T6 Unique | 500065   | 500066        | 500067     | 500068          |
| T7 Cosmic | 500071   | 500072        | 500073     | 500074          |

## Installing

1. Drop the whole `VLPlus-WeaponModels-Example-Bow` folder (or a zip of it) into your
   `.minecraft/resourcepacks/` folder and enable it above/after your main server pack in
   Resource Packs.
2. In-game, open Weapon Models' **Manage Weapon Models** screen and add an entry:
   - Weapon: `minecraft:bow`
   - Idle: `500001`
   - Activate: `500002`
   - Active: `500003`
   - Deactivate: `500004`
3. For each tier/rarity you want its own look, click **Add Tier Override** on that same entry
   and fill in its row from the table above - set the Tier number, tick **Unique** only for the
   `*u_*` rows, and fill in its four CMD values, e.g. Tier `4`, Unique checked, Idle `500045`,
   Activate `500046`, Active `500047`, Deactivate `500048`. Leaving a tier/rarity combo out just
   falls back to the base values from step 2.
4. Enable the Weapon Models module and use a bow - the glow should kick in on draw/release,
   matching whatever tier (and whether it's Unique) that specific bow actually is.

## Merging this into an existing pack instead

If you don't want a standalone pack, copy the four texture+model file pairs
(`vlplus_bow_idle`/`activate`/`active`/`deactivate`, both `.json` and `.png`(`.mcmeta`)) into your
own pack, then add the four `custom_model_data` override lines above into your pack's own
`bow.json` `"overrides"` array instead of replacing the whole file - keep your existing
`damage`/`pulling` entries as they are, `custom_model_data` is a separate predicate and doesn't
conflict with them.

## Making your own

Any weapon works the same way - pick four free `custom_model_data` numbers that don't collide
with anything else your pack already uses, make a model+texture per state, add the override
lines, and point Weapon Models at them. Animated textures are optional (a state can just be a
single static frame); when you do animate one, stack the frames vertically into one PNG and add
a same-named `.png.mcmeta` next to it with `{"animation": {"frametime": N}}` - see this pack's
own `vlplus_bow_active.png`/`.mcmeta` for a working example.
