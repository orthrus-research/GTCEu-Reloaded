# Changelog

One line per change that players, pack makers or addons can see. Releases will be published on
GitHub Releases. GTCEu's own history up to the fork is in the
[upstream changelog](https://github.com/GregTechCEu/GregTech/blob/master/CHANGELOG.md) and
[upstream releases](https://github.com/GregTechCEu/GregTech/releases).

## Unreleased

Changes since the fork from GTCEu at `52c04cd7b`, after 2.8.10.

| Area | Change |
| --- | --- |
| Identity | The mod list, crash reports and the jar name show GTCEu: Reloaded; versions read `2.8.10-reloaded.N` |
| Machines | Item and fluid output sides have independent permissions |
| Machines | Maintenance hatch screens show live maintenance faults |
| Machines | Common simple machines show contextual tool actions |
| Machines | Machine screens explain observed recipe failures without repeating recipe checks |
| Machines | The configuration memory card copies settings between machines, covers and pipes |
| Machines | Drum and quantum tank items can show fluid badges (off by default) |
| Recipes | JEI shows recipe-local overclock estimates |
| Recipes | Eligible recipe alternatives are tried without consuming rejected inputs |
| Recipes | Immutable recipe conditions and processing holds |
| Recipes | Composable startup recipe modifiers |
| Multiblocks | JEI multiblock previews support validated editing |
| Multiblocks | Output insertion is filtered and blocked completed products are retained |
| Multiblocks | Combined item and fluid hatches |
| Multiblocks | Opt-in paint groups and private input views |
| Covers | Item and fluid filters combine with guarded OR matching |
| Covers | Conveyors keep recovery items when block drops are disabled |
| Applied Energistics | ME stocking parts have minimums and refresh controls, and prefer abundant resources |
| Applied Energistics | ME processing pattern buffers and linked proxies |
| Pipes | PipeNet transport accounting and persistence are repaired |
| Pipes | Item pipe state stays independent across copies and dimensions |
| Pipes | Withdrawn fluid returns to the pipe when a destination fill fails |
| Pipes | Ticking pipe networks are tracked by identity, and route callbacks wait for their world |
| Performance | Unchanged energy values send no notifications |
| Performance | Idle recipe callbacks and auto-output capability probes are skipped |
| Performance | Lighter pipe chunk accounting and storage caches |
