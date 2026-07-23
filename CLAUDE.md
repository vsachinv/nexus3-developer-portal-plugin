# CLAUDE.md

Guidance for working in this repository. Read alongside [README.md](README.md)
(user-facing) and [Architecture.md](Architecture.md) (design + reasoning).

## What this is

A developer-facing package portal (Verdaccio-style) for **Nexus Repository CE
3.94+**, shipped as a single drop-in JAR. Java plugin; no external processes,
no Node server. Supports npm and Maven, extensible via a `PackageProvider`
interface. Nexus 3.93 and earlier (OSGi/Karaf) are **not** supported — 3.94 is a
Spring Boot 3 fat JAR, a completely different plugin model.

## Build, test, deploy

```bash
# 0. One-time per machine: install Nexus API jars (NOT on Maven Central).
#    Extracts them from the Nexus fat JAR into ~/.m2.
./scripts/install-nexus-deps.sh

# 1. Build + test (Java 21 REQUIRED — Nexus 3.94 classes are class-file v65)
JAVA_HOME=/path/to/java21 mvn clean package
# local dev used: /Users/sachinverma/.sdkman/candidates/java/21.0.11-zulu

# 2. Deploy to a Docker Nexus and watch for the success line
docker cp target/nexus-developer-portal-<version>.jar nexus-dev:/opt/sonatype/nexus/deploy/
docker restart nexus-dev
docker logs nexus-dev 2>&1 | grep 'REST resources registered'
#   -> "Dev Portal: all N REST resources registered with RESTEasy"
```

- **One-time per Nexus instance:** `loader.properties` with
  `loader.path=/opt/sonatype/nexus/deploy` must exist in `$NEXUS_DATA/etc/`, or
  Nexus never loads the JAR. See `scripts/setup-loader-path.sh`.
- **Only one `nexus-developer-portal-*.jar` in `deploy/` at a time** — two copies
  load together and the beans collide. Remove the old one (in Docker it's
  host-owned: `docker exec -u root … rm`).
- Startup is ~90s. `docker` binary here: `/Applications/Docker.app/Contents/Resources/bin/docker`.

## Critical constraints (don't relearn these the hard way)

- **Java 21** to compile; Nexus jars are `provided` scope (supplied at runtime).
- **Nexus API jars aren't on Maven Central** — always via `install-nexus-deps.sh`.
  Third-party deps that ARE on Central (jackson, commons-compress, resteasy-spi)
  are declared `provided` and resolve normally.
- **Never `@Autowired` a Nexus-internal bean** (e.g. `RepositoryManager`) in the
  auto-configuration — it lives in a *child* Spring context that doesn't exist at
  our bean-creation time, and an unsatisfied dependency aborts all of Nexus.
  Resolve such beans lazily at request time via `NexusBeanLocator`.

## Architecture in one breath

Nexus 3.94 runs **two Spring contexts**. Our `@AutoConfiguration` beans live in
the MAIN context; Nexus's REST discovery + `RepositoryManager` live in a CHILD
context that can't see us. Two bridges (both `internal/`) solve this, keyed off
the fact that a child's `ContextRefreshedEvent` propagates to parent listeners:

- **`DevPortalRestRegistrar`** — grabs the child's `ResteasyDeployment` and
  registers our JAX-RS resources directly with RESTEasy. This is *why* endpoints
  live under `/service/rest/devportal/…` (RESTEasy's mount point).
- **`NexusBeanLocator`** — collects refreshed contexts so services can look up
  Nexus beans (`RepositoryManager`, `RepositoryPermissionChecker`) lazily.

**Authorization:** every repository enumeration goes through
`RepositoryAccessFilter` (`NexusRepositoryAccessFilter` → Nexus's
`RepositoryPermissionChecker`), so a caller only sees repos they may read. It
**fails closed**. Public (no-login) browsing is purely Nexus's *Anonymous Access*
setting — nothing portal-specific.

## Adding a package format

Implement `PackageProvider` in `provider/`, register a `@Bean` in
`DevPortalAutoConfiguration`, and add it to `SearchServiceImpl`'s provider map.
Service + REST + UI need no changes. Format specifics learned so far:

- **npm**: component `namespace()` = scope *without* the leading `@` (re-add for
  display); one component per (name, version) → dedupe by name. Description is on
  the tarball asset attributes (`"npm"` child); author/dependencies/README live in
  the PACKAGE_ROOT metadata doc (asset at `/<fullName>`); CHANGELOG is only inside
  the `.tgz` (extracted with commons-compress).
- **Maven** (`maven2`): `namespace()`=groupId, `name()`=artifactId; identity is
  groupId:artifactId. `pom_name`/`pom_description` are in attributes, but
  dependencies + url/scm/issue links require parsing the `.pom` (XXE-hardened
  DOM parse).

## Frontend (src/main/resources/static/devportal/)

Vanilla JS ES modules, zero build tooling. `app.js` (router) → `components.js`
(HTML builders) → `markdown.js` (self-contained, XSS-safe renderer) + `api.js`.

- **Maven does NOT syntax-check the JS** — a broken script still "builds". After
  editing, run `node --input-type=module --check < file.js` on each module.
- **Browsers cache ES modules by URL across the SPA session** — after redeploying
  JS, a plain re-navigate can serve the OLD module. Force `location.reload()`
  (assets send `Cache-Control: no-cache`, so a reload re-fetches).
- Hash router: `#/`, `#/search?q=`, `#/repos`, `#/package?format=&name=&group=&repository=&version=`.
  Strip the query string before extracting the route segment.

## Testing notes

- **Mockito cannot mock some Nexus classes** (`FluentAssetBuilder`,
  `RepositoryPermissionChecker` — Byte Buddy hierarchy errors). Don't try. Instead
  either inject an interface you own (see `RepositoryAccessFilter`, tested with a
  lambda) or extract pure logic into package-visible static methods and test those
  directly (see `parsePom`, `extractChangelog`, `applyPackageRootMetadata`).

## Screenshots

In-app browser screenshots can't be written to repo files. Regenerate the ones in
`docs/screenshots/` with **headless Chrome** against a running instance (anonymous
access on): see `docs/screenshots/README.md`. Headless can't click, so
tab-active views (e.g. the Changelog tab) can't be auto-captured.

## Conventions

- Jakarta namespace throughout (`jakarta.ws.rs`, `jakarta.inject`).
- All our code stays in `com.rxlogix.nexus.devportal.*` — no squatting in Nexus
  package namespaces; public APIs only; never destabilize the host.
- The plugin JAR is intentionally **thin** (~80 KB) — no shading; Nexus provides
  everything at runtime.
