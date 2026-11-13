<p align="center"><img src=".github/assets/banner.svg" alt="GTCEu: Reloaded by Orthrus Research" width="100%"></p>

<p align="center">
<a href="#status"><img alt="Status: preview" src="https://img.shields.io/badge/status-preview-000000?style=flat-square&labelColor=000000"></a>
<a href="LICENSE"><img alt="License: LGPL-3.0" src="https://img.shields.io/badge/license-LGPL--3.0-000000?style=flat-square&labelColor=000000"></a>
<a href="https://discord.gg/qCVqhXtSzR"><img alt="Discord" src="https://img.shields.io/badge/discord-join-000000?style=flat-square&labelColor=000000"></a>
</p>

GTCEu: Reloaded is a continuation of [GregTech CE Unofficial](https://github.com/GregTechCEu/GregTech)
(GTCEu) for Minecraft 1.12.2, developed by Orthrus Research. It keeps GTCEu's machines, materials
and progression, brings selected features from GregTech CEu: Modern and GregTech: New Horizons to
1.12.2, with explicit migration paths for changed authoring APIs and saved content identities.

GTCEu: Reloaded is not an official GregTechCEu release and implies no endorsement by its
maintainers.

## Status

The `ct-overhaul` branch prepares the 3.0 alpha line for Java 25 and Cleanroom 0.7.14-alpha.
It replaces CraftTweaker integration with shared typed Java definitions and GroovyScript 1.5.0
authoring. This is a breaking API/toolchain migration; existing packs and addons require review.
Saved content retains its registry identities when declarations preserve their original IDs.
See [the migration guide](docs/ct-migration.md), [typed definitions](docs/typed-definitions.md)
and [cache behavior](docs/groovy-cache.md). No release has been published from this branch.

## Changes from GTCEu

The fork began at upstream commit `52c04cd7b`, after GTCEu 2.8.10. In summary:

- Machine and recipe screens from CEu Modern: recipe-local JEI overclock estimates, observed recipe
  failure reasons, live maintenance faults, contextual tool hints, optional tank fluid badges,
  validated multiblock preview editing and a configuration memory card
  ([docs/modern-backports.md](docs/modern-backports.md)).
- Applied Energistics: ME stocking minimums, refresh controls and a preference for abundant
  resources; ME processing pattern buffers with linked proxies
  ([docs/ae-processing-buffers.md](docs/ae-processing-buffers.md)).
- Multiblocks: combined item and fluid hatches, opt-in paint groups and private input views,
  output filtering that holds completed products until they fit, and combined item and fluid
  filters.
- Recipes: immutable recipe conditions with processing holds
  ([docs/recipe-conditions.md](docs/recipe-conditions.md)), composable startup recipe modifiers
  ([docs/recipe-modifiers.md](docs/recipe-modifiers.md)), and alternatives tried without consuming
  rejected inputs.
- Pipes: repaired transport accounting and persistence for item, fluid, energy, optical and laser
  networks, with state kept independent across copies and dimensions
  ([docs/pipenet-compatibility.md](docs/pipenet-compatibility.md)).
- Fewer idle costs: skipped idle recipe callbacks, auto-output probes and unchanged energy
  notifications, and lighter pipe chunk accounting.

## For players and pack makers

GTCEu: Reloaded replaces GTCEu: install one or the other, never both. It keeps the `gregtech` mod
ID and saved registry identities. The 3.0 line changes the authoring API and runtime requirements;
it is not a drop-in upgrade for every GTCEu addon or pack. Preserve original content IDs, migrate
scripts, rebuild affected addons and test a copied world before adopting it.

| Component | Version | Notes |
| --- | --- | --- |
| Minecraft | 1.12.2 | |
| Cleanroom | 0.7.14-alpha | Required on the ct-overhaul branch |
| Java | 26 | Required; qualified on Temurin 26.0.2.1 |
| CodeChickenLib | 3.3.10 | Required; build and runtime use the same pinned release |
| ModularUI | 3.1.6 | Required |
| CleanMix | Supplied by Cleanroom | No separate MixinBooter installation |
| Applied Energistics 2 Unofficial Extended Life | Built against 0.56.4 | Optional: ME parts and pattern buffers |
| HEI, The One Probe, HWYLA, GroovyScript, Forestry | | Optional integrations; pack configuration uses native Java and Groovy |

Groovy authoring is qualified with GroovyScript 1.5.0. HEI reload requires the exact repaired
4.36.1 artifact described in [release acceptance](docs/release-readiness.md); upstream 4.36.1
still has the asynchronous completion stall. The reproducible dependency repair is retained in
[tools/dependency-patches/hei-async](tools/dependency-patches/hei-async/README.md).

## For addon and mod developers

The public API is `gregtech.api`. The ct-overhaul branch uses typed Java definitions and a thin
Groovy frontend. CT-specific APIs are removed; see [the migration guide](docs/ct-migration.md)
for replacements and intentional source changes. Existing recipe
conditions, recipe modifier providers and the PipeNet extension points are described in their
documents. [docs/pipenet-compatibility.md](docs/pipenet-compatibility.md) states what pipe
networks guarantee to addon handlers and where those guarantees end, and
[docs/helper-library-map.md](docs/helper-library-map.md) maps the PipeNet helpers and their owners.

## Building from source

The ct-overhaul build requires JDK 25 and uses CleanroomGradle. Product code and tests target
Java 25, the runtime Cleanroom declares; Java 8 and the standalone Forge development runtime are no longer supported.
See [release validation](docs/pre-delivery-qualification.md) for the tested artifact and runtime.

```sh
git clone --branch ct-overhaul https://github.com/orthrus-research/GTCEu-Reloaded.git
cd GTCEu-Reloaded
./gradlew build
```

The build writes the mod JAR to `build/libs`. [CONTRIBUTING.md](CONTRIBUTING.md) lists the checks a
change must pass.
`check` also writes `build/reports/runtime-baseline.json`, verifying the Java target and the
ModularUI development/production artifact pair and CodeChickenLib release used for native qualification.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/ct-migration.md](docs/ct-migration.md) | Migrating CraftTweaker scripts and addon APIs |
| [docs/typed-definitions.md](docs/typed-definitions.md) | Java and Groovy recipe/content definitions |
| [docs/groovy-cache.md](docs/groovy-cache.md) | Script cache validity and restart requirements |
| [docs/pre-delivery-qualification.md](docs/pre-delivery-qualification.md) | Tested runtime, validation scope and known limitations |
| [docs/modern-backports.md](docs/modern-backports.md) | Features adapted from CEu Modern |
| [docs/ae-processing-buffers.md](docs/ae-processing-buffers.md) | ME processing pattern buffers and proxies |
| [docs/recipe-conditions.md](docs/recipe-conditions.md) | Recipe conditions and processing holds |
| [docs/recipe-modifiers.md](docs/recipe-modifiers.md) | The startup recipe modifier API |
| [docs/pipenet-compatibility.md](docs/pipenet-compatibility.md) | The PipeNet compatibility contract |
| [docs/helper-library-map.md](docs/helper-library-map.md) | PipeNet helpers and ownership |
| [CHANGELOG.md](CHANGELOG.md) | Changes since the fork |
| [NOTICE.md](NOTICE.md) | License, credited works and permissions |

## Community

Questions and discussion: [Discord](https://discord.gg/qCVqhXtSzR). Bugs and requests for GTCEu:
Reloaded: this repository's issues, not upstream's. Security and private matters:
admin@orthrus.cc. Pull requests are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

GTCEu: Reloaded keeps GTCEu's terms: [LGPL-3.0](LICENSE). Original authorship and copyright
headers are intact. [NOTICE.md](NOTICE.md) credits the textures and code adapted from other
projects and records the permissions behind the GregTech name and assets.

## Upstream

GregTech CE Unofficial is an indirect descendant and total rewrite of GregTech, created with
GregoriusT's knowledge and permission but neither created nor maintained by him. It is the work of
the GregTechCEu team and its contributors, and GTCEu: Reloaded builds on all of it. The upstream
project, its downloads and its community are theirs:

- [GregTechCEu/GregTech](https://github.com/GregTechCEu/GregTech)
- [GTCEu on CurseForge](https://www.curseforge.com/minecraft/mc-mods/gregtech-ce-unofficial) and
  [Modrinth](https://modrinth.com/mod/gregtech-ce-unofficial)
- The [GregTech CEu Discord](https://discord.gg/bWSWuYvURP)

"Community Edition" stays part of the name: the permission to use the GregTech name requires it,
and it marks the ideas and the work as the community's. The repository keeps upstream's full
history, so every upstream author is credited in its commits.

The upstream logo is not used for GTCEu: Reloaded.
