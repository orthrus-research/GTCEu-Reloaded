<p align="center"><img src=".github/assets/banner.svg" alt="GTCEu: Reloaded by Orthrus Research" width="100%"></p>

<p align="center">
<a href="#status"><img alt="Status: preview" src="https://img.shields.io/badge/status-preview-000000?style=flat-square&labelColor=000000"></a>
<a href="LICENSE"><img alt="License: LGPL-3.0" src="https://img.shields.io/badge/license-LGPL--3.0-000000?style=flat-square&labelColor=000000"></a>
<a href="https://discord.gg/qCVqhXtSzR"><img alt="Discord" src="https://img.shields.io/badge/discord-join-000000?style=flat-square&labelColor=000000"></a>
</p>

GTCEu: Reloaded is a continuation of [GregTech CE Unofficial](https://github.com/GregTechCEu/GregTech)
(GTCEu) for Minecraft 1.12.2, developed by Orthrus Research. It keeps GTCEu's machines, materials
and progression, brings selected features from GregTech CEu: Modern and GregTech: New Horizons to
1.12.2, and keeps existing worlds, addons and packs working.

GTCEu: Reloaded is not an official GregTechCEu release and implies no endorsement by its
maintainers.

## Status

Preview: public for study and testing, with no releases yet; they will be published on GitHub
Releases. Worlds, mod IDs and APIs are kept compatible with GTCEu (see below), but no release yet
promises compatibility guarantees of its own. [CHANGELOG.md](CHANGELOG.md) lists the changes so far.

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
ID, the `gregtech` packages, registry names, configuration and the save format, so worlds and
addons built for GTCEu find what they expect. New features that change behavior are opt-in or
default off where the documents say so. Versions carry the GTCEu release they build on plus a
Reloaded number, such as `2.8.10-reloaded.1`, so addons that require a GTCEu version still load.

| Component | Version | |
| --- | --- | --- |
| Minecraft | 1.12.2 | |
| Forge | 14.23.5.2847 or later | Required |
| CodeChickenLib | 3.2.3 or later | Required |
| ModularUI | 3.0.4 or later | Required |
| MixinBooter | 8.0 or later | Required |
| Applied Energistics 2 Unofficial Extended Life | Built against 0.56.4 | Optional: ME parts and pattern buffers |
| JEI, The One Probe, HWYLA, CraftTweaker, GroovyScript, Forestry | | Optional integrations, as in GTCEu |

## For addon and mod developers

The public API is `gregtech.api`, unchanged in its packages. New APIs are additive: recipe
conditions, recipe modifier providers and the PipeNet extension points are described in their
documents. [docs/pipenet-compatibility.md](docs/pipenet-compatibility.md) states what pipe
networks guarantee to addon handlers and where those guarantees end, and
[docs/helper-library-map.md](docs/helper-library-map.md) maps the PipeNet helpers and their owners.

## Building from source

The build needs JDK 17 and compiles to Java 8 bytecode; tests and the development client run on
Java 8.

```sh
git clone https://github.com/orthrus-research/GTCEu-Reloaded.git
cd GTCEu-Reloaded
./gradlew build
```

The build writes the mod JAR to `build/libs`. [CONTRIBUTING.md](CONTRIBUTING.md) lists the checks a
change must pass.

## Documentation

| Document | Contents |
| --- | --- |
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

Orthrus Research develops with AI assistance (Claude Code and Codex). Every change meets the same
verification gates whatever its origin. See
[AI usage](https://github.com/orthrus-research/.github/blob/main/AI.md).

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
