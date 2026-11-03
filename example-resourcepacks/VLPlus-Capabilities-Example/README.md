# VL+ capabilities example pack

Demonstrates every resource-pack-facing capability VL+ actually has, both on a plain bow:

1. **Weapon Models** - swap the held item's model (idle/activate/active/deactivate, per tier)
   via `custom_model_data`, same as any vanilla CIT-style override.
2. **Animations** - a custom first-person swing animation read straight from the pack, no mod
   config needed for it to take effect.

Two other things in the mod are sometimes mistaken for resource-pack features but aren't, so
they're deliberately not included here:
- **Custom sound alerts** (NoiseNotif/Reader Notif's own sounds) - these bypass the resource
  pack sound lookup entirely; you point them at an arbitrary local `.ogg` file directly in the
  module's own settings, not through a pack.
- **Custom title-screen panorama** - reads 6 images from `.minecraft/vlplus-panoramas/` on disk
  directly (drop the files in yourself), not from any resource pack's `assets/`.

## 1. Weapon Models

See `assets/minecraft/models/item/` - a fully original, hand-authored 4-cuboid low-poly bow
(grip, two angled limbs, string) with procedurally generated gradient textures, reused across
every state/tier via the standard parent + texture-variable model pattern (one real geometry,
only the bound texture changes). `bow.json`'s `custom_model_data` overrides:

| Variant   | Idle CMD | Activate CMD | Active CMD | Deactivate CMD |
|-----------|----------|---------------|------------|-----------------|
| Base      | 500001   | 500002        | 500003     | 500004          |
| T4        | 500041   | 500042        | 500043     | 500044          |
| T4 Unique | 500045   | 500046        | 500047     | 500048          |
| T5        | 500051   | 500052        | 500053     | 500054          |
| T5 Unique | 500055   | 500056        | 500057     | 500058          |
| T6        | 500061   | 500062        | 500063     | 500064          |
| T6 Unique | 500065   | 500066        | 500067     | 500068          |
| T7 Cosmic | 500071   | 500072        | 500073     | 500074          |

In-game: open Weapon Models' **Manage Weapon Models**, add weapon `minecraft:bow` with the Base
row's four values, then **Add Tier Override** for whichever tiers/Unique rows you want.

## 2. Animations

See `assets/vlplus/animations/bow.json` - takes effect automatically whenever Animations' own
**Use Pack** setting is on (it is by default) and you use/drop a bow, no extra config needed:

```json
{
    "use": {
        "duration": 350,
        "keyframes": [
            { "t": 0,   "pos": [0, 0, 0],    "rot": [0, 0, 0],  "scale": 1, "ease": "out" },
            { "t": 0.4, "pos": [0, 0, 0.15], "rot": [10, 0, 0], "scale": 1, "ease": "in" },
            { "t": 1,   "pos": [0, 0, 0],    "rot": [0, 0, 0],  "scale": 1, "ease": "inout" }
        ]
    },
    "drop": {
        "preset": "Flourish",
        "duration": 300
    }
}
```

`use` is a real hand-authored keyframe animation (a simple draw-back-and-release motion - `pos`
is in blocks, `-z` is forward per the doc comment in `Animations.java`, so `+z` pulls the bow
back toward the camera); `drop` just reuses a built-in preset (`Thrust`/`Slash`/`Spin`/`Recoil`/
`Pulse`/`Flourish`/`Missile`) to show that shortcut too. Files are looked up in this order:
`assets/vlplus/animations/name/<display_name_slug>.json` (most specific), then
`assets/vlplus/animations/<item_id>.json` (this file), then `.../default.json` (fallback for
everything). A file can hold one shared animation for right-click + drop, or split per-trigger
sections like this one does (`use`/`sneak_use`/`drop`/`sneak_drop`).

## Installing

Drop the whole `VLPlus-Capabilities-Example` folder (or the zip) into
`.minecraft/resourcepacks/` and enable it. Weapon Models still needs the config step above -
Animations works immediately just from the pack being enabled.
