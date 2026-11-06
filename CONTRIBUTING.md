# Contributing to GTCEu: Reloaded

Pull requests are welcome. The organization's
[contributing guide](https://github.com/orthrus-research/.github/blob/main/CONTRIBUTING.md),
[code of conduct](https://github.com/orthrus-research/.github/blob/main/CODE_OF_CONDUCT.md) and
[organization policies](https://github.com/orthrus-research/.github) apply. This page adds
what is specific to GTCEu: Reloaded. Report GTCEu: Reloaded problems here, not to upstream GTCEu.

## Compatibility is a contract

- Release requirements cover Reloaded's documented APIs and pack capabilities. Compatibility with
  upstream GTCEu addons and existing packs must be documented and tested separately;
  see [release acceptance](docs/release-readiness.md).

- The `gregtech` mod ID, the `gregtech` packages, registry names, configuration keys, network
  messages and the save format stay as they are. Renaming or removing any of them needs an
  explicit migration and its own discussion.
- The ct-overhaul branch replaces CT-specific APIs with typed Java and Groovy APIs. Document
  intentional API changes and migration paths, and test the functionality of affected integrations.
  Do not retain a legacy adapter solely to preserve the old API.
- A feature that changes existing behavior is opt-in or defaults off unless its document says
  otherwise, and its document states where its guarantees end.
- Upstream authorship, copyright headers and license notices stay intact. Material adapted from
  another project names its source and license in [NOTICE.md](NOTICE.md).
- Code and tests target Java 26 through CleanroomGradle. Do not introduce a Java 8 compatibility lane.

## Checks

Build with the tests and the formatting check (JDK 26):

```sh
./gradlew test build spotlessCheck
```

The standard `test` task includes authoring lifecycle regressions for Java and both Groovy modes,
recipe replacement/disable/re-enable, and restoration ordering.
CI uses Temurin 26 through the shared build setup action.

`./gradlew spotlessApply` applies the formatting. Follow [CODESTYLE.md](CODESTYLE.md) and
`.editorconfig`. A change to machines, pipes, recipes or saves also needs a check in a development
client (`./gradlew runClient`) or a pack that exercises it; say in the pull request what was run.

## Commits and documentation

- One reviewed change per commit. The message says what changed and why.
- Describe a change in the document that owns its area (`docs/`), and add a line to
  [CHANGELOG.md](CHANGELOG.md) when it changes what players or addons see.
- Run output and evidence stay out of the repository. Local tooling configuration stays
  local (see `.gitignore`).
