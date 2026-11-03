# VL+ Weapon Models example pack - Bow (original 3D model)

Demonstrates the Weapon Models module's four states (Idle / Activate / Active / Deactivate)
across tiers, using a fully original, hand-authored 3D model (not a flat 2D icon, and not
derived from any existing game/server texture) - a simple low-poly bow built from 4 cuboids
(grip, two angled limbs, string), with procedurally generated gradient textures.

## How it works

`assets/minecraft/models/item/vlplus_bow3d_base.json` defines the actual 3D geometry (the 4
cuboids) once, with its faces referencing texture *variables* (`#wood`, `#string`) instead of
concrete textures. Every state/tier model is a tiny child model that just sets `"parent"` to
that base model and fills in `wood`/`string` with its own concrete texture - same geometry
everywhere, only the texture changes. This is the standard vanilla model-inheritance mechanism
(the same one `item/generated`/`item/handheld` etc. use), not anything custom.

`assets/minecraft/models/item/bow.json` then adds `custom_model_data` overrides on top of the
normal bow model, each pointing at one of those state/tier models:

| State      | custom_model_data | Model                          |
|------------|---------------------|---------------------------------|
| Idle       | 500001               | `item/vlplus_bow3d_base_idle`       |
| Activate   | 500002               | `item/vlplus_bow3d_base_activate`   |
| Active     | 500003               | `item/vlplus_bow3d_base_active`     |
| Deactivate | 500004               | `item/vlplus_bow3d_base_deactivate` |

Activate/Deactivate are quick flashes (Weapon Models shows each for ~250ms by default before
auto-advancing); Active loops for as long as the ability stays up - all done by swapping which
4-frame gradient texture is bound to `#wood`, the geometry never changes.

## Per-tier variants

Same real tier colors confirmed from this server's own lore earlier (T4 purple, T5 gold, T6
dark_red, T7 Cosmic blue), Unique variants cross-fading tier color ↔ Unique green - same
concept as before, just reimplemented as original gradient art instead of recolored real
textures:

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

1. Drop the whole `VLPlus-WeaponModels-Example-Bow` folder (or the zip) into
   `.minecraft/resourcepacks/` and enable it in Resource Packs.
2. In-game, open Weapon Models' **Manage Weapon Models** screen and add an entry:
   - Weapon: `minecraft:bow`
   - Idle: `500001`, Activate: `500002`, Active: `500003`, Deactivate: `500004`
3. For each tier/rarity you want, click **Add Tier Override**, set the Tier number, tick
   **Unique** for the `*u` rows, and fill in its four CMD values from the table above.
4. Enable Weapon Models and use a bow.

## Making your own 3D models

Build your model in Blockbench (or hand-author JSON like this pack does), export/save it as the
item's own model JSON with real `"elements"`, pick free `custom_model_data` numbers, and point
Weapon Models at them - geometry and animated textures both work the same way a flat 2D model
does, nothing extra needed on the mod's side. If you want several CustomModelData values to
share one geometry (like this pack's states/tiers do), use the parent + texture-variable pattern
`vlplus_bow3d_base.json` demonstrates instead of repeating the same `"elements"` block everywhere.
