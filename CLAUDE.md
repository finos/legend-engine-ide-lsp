# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Language Server Protocol implementation for the Legend language, used by the Legend VS Code extension. Alpha-stage FINOS project (`org.finos.legend.engine.ide.lsp`). Java 11 bytecode target, built and tested on JDK 17 or 21.

## Build & test

**Set `JAVA_HOME` to a JDK 17 or 21 first.** The root pom's enforcer requires `[17,18),[21,22)`; a newer JDK (Maven on this machine defaults to Java 25) fails the build at `validate`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

```bash
mvn install                          # full build; CI runs `mvn install javadoc:javadoc`
mvn install -DskipTests              # compile + checkstyle/PMD only
mvn test -pl legend-engine-ide-lsp-default-extensions -Dtest=TestPureLSPGrammarExtension
mvn test -pl legend-engine-ide-lsp-server -Dtest=TestLegendLanguageServerIntegration#testX
```

Quality gates run inside the normal lifecycle, not as separate commands:
- PMD (`.pmd/legend-ruleset.xml`) runs at `validate`.
- Checkstyle (`checkstyle.xml`) runs at `verify` over **both** main and test sources, with `violationSeverity=warning` — any warning fails the build. Match the surrounding style: Allman braces (`LeftCurly` = `nl`), no tabs, import groups separated by a blank line (`CustomImportOrder`), and an Apache copyright header on every file — including test files and new resources.

Surefire forks 2 JVMs (`reuseForks=false`) and writes all reports to `legend-engine-ide-lsp-test-reports/surefire-reports-aggregate/` regardless of module.

The integration tests in `legend-engine-ide-lsp-server/src/test/java/.../server/integration/` spin up a real server over piped streams and **invoke real Maven** to resolve the Legend engine classpath. They are slow, network-dependent, and the first run downloads a large dependency tree. `LegendLanguageServerIntegrationExtension` uses a `Phaser` to wait on async tasks with a 5-minute deadlock timeout — raise `MAYBE_DEADLOCK_TIMEOUT_SECONDS` when debugging locally.

## Architecture

### The classloader split (the key thing to understand)

The LSP server **does not compile against the Legend engine**. At `initialize`, `ClasspathFactory.create()` produces a `ClassLoader` and `ExtensionsGuard` `ServiceLoader`s `LegendLSPExtensionLoader` out of it to discover grammar extensions and features:

- `ClasspathUsingMavenFactory` (production) runs maven-invoker against the `legend-engine-ide-lsp-default-extensions-dependencies` pom — or a user-supplied pom via the `legend.extensions.dependencies.pom` setting — parses the resulting classpath, and builds a `URLClassLoader`.
- `EmbeddedClasspathFactory` just returns the app classloader (used by unit tests).

`ExtensionsGuard` also owns a `ForkJoinPool` whose threads carry that classloader as their context classloader; anything touching engine classes must run on it. The guard can be re-initialized (classpath reload) and closes the previous classloader.

Consequences worth remembering:
- Everything in `legend-engine-ide-lsp-default-extensions` declares its engine dependencies as `provided` — they arrive at runtime from the resolved classpath, not from the jar.
- The server module copies the `default-extensions-dependencies/pom.xml` into `target/generated-test-resources` at `generate-test-resources` so integration tests can resolve the same classpath.
- Bumping `legend.engine.version` / `legend.sdlc.version` in the root pom changes what gets loaded at runtime, so breakage often shows up as test failures rather than compile errors.

### Modules (build order matters)

| Module | Role |
| --- | --- |
| `text-tools` | `LineIndexedText`, `TextTools` — line/offset indexing, no dependencies |
| `extension-api` | The SPI: `LegendLSPGrammarExtension`, `LegendLSPFeature`, the `State` model, and result types. Interface methods are `default` returning empty, so extensions implement only what they support. Published for third-party extension authors — treat changes as breaking. |
| `default-extensions` | Per-grammar implementations (Pure, mapping, relational, service, connection, runtime, diagram, dataSpace, functionActivator, notebook) plus feature impls (REPL, SDLC, TDS). Registered via `META-INF/services/`. |
| `server` | LSP4J wiring: `LegendLanguageServer` (+ `Builder`, `main`), the text/notebook/workspace services, classpath factories, command handlers |
| `server-shaded` | Fat jar, `Main-Class` = `LegendLanguageServer`. Relocates maven/plexus/commons-io/gson under `org.finos.legend.engine.ide.lsp.shaded.*` so they can't clash with the dynamically loaded engine classpath. |
| `default-extensions-dependencies` | pom-only; pins the engine/SDLC runtime dependency set resolved above |
| `test-reports` | pom-only; jacoco `report-aggregate` + surefire aggregation. Must stay second-to-last in `<modules>` (shaded last) for deploy to work. |

### Document model

A Legend file is split into sections on `###GrammarName` lines by `GrammarSectionIndex` (default grammar: `Pure`). Each `SectionState` is dispatched to the grammar extension registered for that name, which supplies declarations, diagnostics, completions, commands, references, entities, and tests.

Caching is keyed by scope on the `State` hierarchy (`GlobalState` → `DocumentState` → `SectionState`), via `getProperty(key, supplier)`:
- **Parse** results cache on the `SectionState`.
- **Compile** results cache on the `GlobalState` — compilation builds one `PureModel` from *all* open documents, so a change anywhere invalidates it globally.

`SourceInformationUtil` converts engine `SourceInformation` to LSP `TextLocation`; always guard with `isValidSourceInfo` before converting — engine elements frequently carry placeholder source info.

## Adding dependencies

The root pom's enforcer **bans all `compile`, `runtime`, and `provided` dependencies by default**; each module re-allows specific ones through a `bannedDependencies/includes` allowlist (see `legend-engine-ide-lsp-server/pom.xml` and `legend-engine-ide-lsp-server-shaded/pom.xml`, which has its own separate runtime allowlist). `dependencyConvergence` is also enforced. Adding or upgrading a dependency generally means editing the allowlist(s) as well as the dependency block, and `maven-dependency-plugin analyze-only` runs with `failOnWarning=true`, so unused or undeclared-but-used dependencies break the build too.

## Adding a grammar extension

Extend `AbstractSectionParserLSPGrammarExtension` (or `AbstractLegacyParserLSPGrammarExtension` for grammars without a section parser), place it in `default-extensions`, and register it in `src/main/resources/META-INF/services/org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension`. Tests extend `AbstractLSPGrammarExtensionTest` and use `StateForTestFactory` to build section states from raw grammar text.
