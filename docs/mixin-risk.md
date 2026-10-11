# Mixins

Steel Clash prefers NeoForge events. Every mixin is listed here with why it exists and what it could conflict with.

| Mixin | Target | Why | Conflict risk |
|---|---|---|---|
| `client.LivingEntityRendererMixin` | `LivingEntityRenderer#render`, injected right **after** the call to `EntityModel#setupAnim`; and `LivingEntityRenderer#setupRotations` at `RETURN` (whole-body rotation, the same transform Player Animation Library applies to players in `PlayerRenderer#setupRotations`) | Pose mob models (zombies, skeletons, piglins, vindicators, the training dummy) from their combat state. It has to run after each model's own `setupAnim`: zombie, skeleton and illager models set their arms *after* `HumanoidModel.setupAnim`, so an inject inside `HumanoidModel` would be overwritten. There's no NeoForge event between `setupAnim` and rendering. | **Low–medium.** Client only, read-only on game state. Only changes model part rotations for non-player entities whose combat state is busy. Mods that replace mob renderers entirely (GeckoLib mobs, Fresh Animations-style models) won't use these arms and simply won't show our poses. Mods that also pose arms after `setupAnim` (e.g. some combat-animation mods) would fight over the same parts; whichever runs last wins. |
| `client.ItemInHandLayerMixin` | `ItemInHandLayer#renderArmWithItem`, injected just **before** `ItemInHandRenderer#renderItem` (the same point Player Animation Library turns a player's held item) | Turn a fighting mob's weapon in its hand, so it can hold the arm naturally with the blade on the traced arc. Without it the whole arm had to be pitched about 80 degrees down to aim the blade, leaving the hand at the hip and the weapon drawn well below where it hits. | **Low.** Client only. Adds a rotation for non-player entities that are mid-attack this frame; otherwise does nothing. It also records which mob types draw items through this layer: a modded mob that draws its weapon some other way keeps the old pitched-arm pose. A mod that also rotates mob items here would stack its rotation with ours. |

Access transformers (`META-INF/accesstransformer.cfg`): `LivingEntity#attackStrengthTicker` (full-strength hits through vanilla `Player#attack`), and `IllagerModel` arm/leg parts (posing vindicators).

`LivingEntityRendererMixin` also modifies the age argument passed to `EntityModel#setupAnim` while the development
pose sheet is running. It holds vanilla idle/breathing motion at 100 ticks so equivalent free-hand captures are
comparable. Ordinary rendering receives its original age. This is a client-only capture hook; a renderer that replaces
the `setupAnim` call needs its own capture support. No event exposes this argument between setup and rendering.

## Development-only PAL More bridge

`client.PalMoreItemCaptureMixin` (priority 100) and `client.PalMoreItemApplyMixin` (priority 2000) target
`ItemInHandLayer#renderArmWithItem`, immediately before `ItemInHandRenderer#renderItem`. No NeoForge event spans
the observed addon/PAL callback sequence. The first saves the item's pose/normal matrices; the second restores
that hand/bend transform and applies the standard-axis item sample after PAL. Exported runtime code verifies
capture → addon → PAL → apply order. Both require the explicit prototype property and reviewed addon 1.1.0;
normal/absent-addon launches skip them. They use PAL's public same-frame sampler, without shadowing another
mixin's private injected fields or starting a second animation clock.

**High integration risk outside the tested dev stack:** intervening item transforms are discarded for the owned
pose. Other item/animation/render mods need separate checks before adoption. Version changes require a new
ordering review. See [prototype evidence and limits](archive/pal-more-prototype-20261011.md).
