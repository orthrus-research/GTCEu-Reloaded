# Emissive textures and bloom

GTCEu: Reloaded draws no bloom of its own. Its emissive textures are meshed in an emissive layer, and its fusion
reactor ring and cable heat glow are emissive geometry; both are declared through
[OrthLib](https://github.com/orthrus-research/OrthLib)'s render interface. A renderer that provides that interface
meshes the emissive layer itself, draws the emissive geometry, and computes the glow with a technique of its own.
Without such a renderer, emissive textures are meshed in the `CUTOUT` layer at full brightness, OrthLib draws the
fusion reactor ring and cable heat glow plainly at the end of the world pass, and nothing glows. GTCEu: Reloaded keeps
no bloom pass, framebuffer, shader program or depth texture, and makes no GL call for bloom.

## Getting OrthLib

OrthLib is a required dependency at runtime (`required-after:orthlib@[0.5.0-dev,)`): install its release JAR next to
GTCEu: Reloaded.

OrthLib is not yet published to a Maven repository. The Set names its commit and the SHA-256 of the two JARs its
build writes: the production JAR, which the build compiles against, and the development (`-mcp`) JAR, which the
development client and the tests run. In a Workbench worktree both come from the Store, re-hashed before anything
compiles (`setVerify`); a new OrthLib reaches this mod by landing in the Set, with no commit here. In a plain clone,
build OrthLib beside this repository and name it: `./gradlew build -Pset.dir=../orthrus-set
-Pset.with.orthlib=../OrthLib` takes each JAR from that build by its glob.

## The emissive layer

The layer `gregtech:bloom` is emissive, falls back to `CUTOUT` and glows with the `gregtech:emissive_textures` bloom
kind. With `emissiveTexturesBloom` off it is declared plain: still meshed apart and full-bright, without glow.

A block state is in the layer when its block implements `gregtech.api.block.IEmissiveLayerBlock` and answers so:

| Block | In the emissive layer |
| --- | --- |
| Machines (`BlockMachine`) and pipes (`BlockPipe`) | Always: machines' emissive overlays, laser pipes' active overlay and covers' overlays are chosen by the tile entity |
| Active casings and coils (`VariantActiveBlock`) | When the variant's bloom is enabled (`isBloomEnabled`, by default `machinesEmissiveTextures`) |
| Lamps | When lit and with bloom |
| Ores | When the ore material is emissive |
| Other blocks | When their model has GTCEu custom textures in the emissive layer (texture metadata below) |

A renderer may keep each state's answer until resources reload. Changing a setting that changes the layer or its
membership declares the layer again, with a new handle.

### Per-layer geometry

Code that emits geometry per layer asks `gregtech.client.renderer.EmissiveLayer`:

- `isCurrent()`: whether emissive geometry is emitted now, on this thread. It is while a renderer meshes the emissive
  layer, and while Forge's layer is `CUTOUT` where none does.
- `isMeshing()`: whether a renderer is meshing the emissive layer on this thread. Forge's layer is null then, so a
  model that returns every quad for a null layer asks this first and returns its emissive quads only.
- `gameLayer()`: the game layer emissive geometry is meshed in, for `Block.canRenderInLayer`: `CUTOUT`, or null where a
  renderer meshes the emissive layer.

Cube renderers draw emissive faces with `Textures.renderEmissiveFace` and other faces with `Textures.renderFace`.
Block renderers set `Textures.RENDER_STATE` to a `CubeRendererState` that says whether the block is in the emissive
layer; the three-argument constructor describes a block outside it, whose emissive faces are drawn with its `CUTOUT`
faces. Pipe renderers draw emissive geometry in `PipeRenderer.renderEmissiveLayer`. While the emissive layer is meshed,
machines and covers are asked `canRenderInLayer(CUTOUT)`.

### Texture metadata

GTCEu's custom textures (the `ctm` metadata section with `"gregtech": true`, read when CTM is absent) put a texture in
the emissive layer with `"layer": "BLOOM"`. CTM reads the same section and knows no `BLOOM` layer, so GTCEu's own
textures name `"layer": "CUTOUT"`; their models take the textures named `_bloom` or `_emissive` (and their `_ctm`
variants) into the emissive layer themselves.

## Bloom kinds

Each kind's parameters come from the shader settings. GTCEu weighs a kind's glow over a background of brightness `Y`
by `strength * (base + low + (1 - Y) * (high - low))`. OrthLib bounds the brightness limits to [0, 1], so settings
above 1 are divided by the larger limit and the strength multiplied by it, which leaves that weight unchanged. A step
becomes the kind's radius multiplier, at most 4.

| Kind | Settings | Defaults (strength / base / low / high / radius) |
| --- | --- | --- |
| `gregtech:emissive_textures` | `strength`, `baseBrightness`, `lowBrightnessThreshold`, `highBrightnessThreshold`, `step` | 1.5 / 0.1 / 0.2 / 0.5 / 1 |
| `gregtech:fusion_reactor` | `Fusion Reactor` | 1.5 / 0 / 0.3 / 1.3 / 1, defined as 1.95 / 0 / 0.231 / 1 / 1 |
| `gregtech:heat_effect` | `Heat Effect` | 1.1 / 0 / 0.6 / 1.4 / 1, defined as 1.54 / 0 / 0.429 / 1 / 1 |

With `emissiveTexturesBloom` off, the fusion reactor and heat effect kinds have no strength: their geometry is drawn
without glow. The bloom style settings, the mipmap count, `hookDepthTexture` and the fusion reactor and heat effect
`useShader` switches are retired: the renderer owns the technique. The shader options' `useShader` still turns off the
shader programs of interface effects.

## Emissive contributions

Geometry that is not terrain is submitted with `GTBloomKind.contribute(shape, source)`, which returns the owner's
`EmissiveContribution`; the owner closes it when the geometry goes away. OrthLib also retires a contribution when its
source's `valid()` answers false at a client tick, when its world unloads and when the source throws. A source writes
vertices into the buffer the renderer has begun, with positions relative to the camera, and never begins, finishes or
draws a buffer.

| Geometry | Shape | Blend | Kind | Valid while | World |
| --- | --- | --- | --- | --- | --- |
| Fusion reactor ring | `POSITION_COLOR` quads, 800 vertices | `OPAQUE` (its colors are opaque) | `gregtech:fusion_reactor` | The reactor is valid and in its holder; drawn while the ring has a color | The reactor's |
| Cable heat glow | `POSITION_COLOR` quads, a cuboid's faces per pipe box | `ALPHA` (alpha 0 to 0.8 by temperature) | `gregtech:heat_effect` | The particle is alive and its cable valid; drawn while alpha is above 0; not submitted for insulated cables | The cable's |

## Deprecated API

These public types and members are kept for addons built against them and answer as GTCEu did under a renderer that
drew terrain without its bloom pass:

- `BloomEffectUtil.getBloomLayer()` and `getEffectiveBloomLayer()` answer `CUTOUT`; `getEffectiveBloomLayer(fallback)`
  and `getEffectiveBloomLayer(boolean, fallback)` answer `fallback`. There is no `BLOOM` game layer.
- `BloomEffectUtil.registerBloomRender(...)` (all four forms) returns `BloomRenderTicket.INVALID` and never calls the
  render callback.
- `BloomEffect` keeps its public parameter fields; nothing reads them.
- `IBloomEffect`, `BloomType`, `BloomEffectUtil.BloomRenderTicket` and `GTBloomParticle` remain; a `GTBloomParticle`'s
  bloom effect is never drawn.

`IRenderSetup` and `EffectRenderContext` are unchanged: the particle manager uses them.

## Other renderers and mods

- Previews (`MultiblockPreviewRenderer`, `WorldSceneRenderer`) set Forge's layer themselves, so emissive textures are
  drawn in `CUTOUT` there, full-bright.
- OptiFine shader packs and Celeritas (also bundled in Demonica) provide no render interface, so OrthLib's absence
  answers apply: emissive textures use `CUTOUT`, OrthLib draws the emissive geometry plainly, and nothing glows.
- Vintagium and Nothirium get no support of their own: their Mixin configurations, which added a `BLOOM` pass for
  the former `BLOOM` game layer, are removed. Under them, as under any renderer that provides no render interface,
  OrthLib's absence answers apply.
- The CTM and LittleTiles Mixins existed for the `BLOOM` game layer only and are removed.
