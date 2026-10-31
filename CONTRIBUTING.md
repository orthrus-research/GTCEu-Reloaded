# Contributing to GTCEu: Reloaded

Pull requests are welcome. The organization's
[contributing guide](https://github.com/orthrus-research/.github/blob/main/CONTRIBUTING.md),
[code of conduct](https://github.com/orthrus-research/.github/blob/main/CODE_OF_CONDUCT.md) and
[organization policies](https://github.com/orthrus-research/.github) apply. This page adds
what is specific to GTCEu: Reloaded. Report GTCEu: Reloaded problems here, not to upstream GTCEu.

## Compatibility is a contract

- The `gregtech` mod ID, the `gregtech` packages, registry names, configuration keys, network
  messages and the save format stay as they are. Renaming or removing any of them needs an
  explicit migration and its own discussion.
- `gregtech.api` changes are additive. Addons built for GTCEu keep compiling and running.
- A feature that changes existing behavior is opt-in or defaults off unless its document says
  otherwise, and its document states where its guarantees end.
- Upstream authorship, copyright headers and license notices stay intact. Material adapted from
  another project names its source and license in [NOTICE.md](NOTICE.md).
- Code compiles to Java 8 bytecode against Java 8 APIs, although the build uses JDK 17.

## Checks

Build with the tests and the formatting check (JDK 17):

```sh
./gradlew test build spotlessCheck
```

`./gradlew spotlessApply` applies the formatting. Follow [CODESTYLE.md](CODESTYLE.md) and
`.editorconfig`. A change to machines, pipes, recipes or saves also needs a check in a development
client (`./gradlew runClient`) or a pack that exercises it; say in the pull request what was run.

## Commits and documentation

- One reviewed change per commit. The message says what changed and why.
- Describe a change in the document that owns its area (`docs/`), and add a line to
  [CHANGELOG.md](CHANGELOG.md) when it changes what players or addons see.
- Run output and evidence stay out of the repository. Editor and assistant configuration stays
  local (see `.gitignore`).
