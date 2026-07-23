# Architecture — Nexus Developer Portal

Target platform: **Nexus Repository CE 3.94+** (Spring Boot 3 fat-JAR architecture).
The old OSGi/Karaf plugin model (≤ 3.93) is **not** supported by this codebase.

## Guiding principles

1. **Single deployable** — one plain `.jar` in `$NEXUS_HOME/deploy/`, no external processes.
2. **Never break the host** — every integration point fails soft: if anything goes wrong, the portal 404s and logs a warning; Nexus itself is unaffected.
3. **Public APIs only** — registration uses RESTEasy SPI and Spring events; no reflection hacks, no classes in Sonatype's package namespace.
4. **Nexus auth re-use** — no separate login; all portal endpoints sit behind Nexus's existing `/service/rest` filter chain.
5. **Provider abstraction** — each package format lives behind `PackageProvider`; adding PyPI later requires zero changes to the service or REST layer.
6. **Layered, no upward dependencies** — REST → Service → Provider → Nexus API. No layer imports from a higher one.

---

## How the plugin loads (Nexus 3.94 boot sequence)

Nexus 3.94 ships as a Spring Boot 3 fat JAR launched by
`org.springframework.boot.loader.launch.PropertiesLauncher`. The JVM classpath
includes `$NEXUS_DATA/etc`, so a `loader.properties` there is honored:

```
loader.path=/opt/sonatype/nexus/deploy
```

This makes `PropertiesLauncher` add every JAR in `deploy/` to the application
classpath before Spring starts — that is the entire "plugin mechanism".

```
sonatype-nexus-repository-3.94.x.jar        (fat JAR)
 └─ PropertiesLauncher
     └─ reads $NEXUS_DATA/etc/loader.properties
         └─ loader.path=/opt/sonatype/nexus/deploy
             └─ nexus-developer-portal-<version>.jar   ← our plugin
                 └─ META-INF/spring/…AutoConfiguration.imports
                     └─ DevPortalAutoConfiguration (@AutoConfiguration)
```

### The two-context problem (and how we solve it)

Nexus 3.94 runs **two Spring ApplicationContexts**:

```
┌────────────────────────────────────────────────────────────────┐
│ MAIN Spring Boot context                                        │
│  • processes AutoConfiguration.imports → OUR beans live here    │
│  • services, providers, REST resource instances, registrar      │
└───────────────┬────────────────────────────────────────────────┘
                │ parent of…
                ▼
┌────────────────────────────────────────────────────────────────┐
│ CHILD context  ("nexus-spring-component-scan")                  │
│  • created later by SpringComponentScan, which scans ONLY       │
│    org.sonatype.nexus.* / com.sonatype.nexus.* for @Named       │
│  • holds RepositoryManager, ResteasyDeployment,                 │
│    ComponentContainerImpl and all of Nexus's own REST resources │
└────────────────────────────────────────────────────────────────┘
```

Two consequences, each with a dedicated bridge component:

| Problem | Consequence | Bridge |
|---|---|---|
| `ComponentContainerImpl` discovers REST resources via `childContext.getBeansOfType(Component.class)`, which never looks at the parent | Our resources are invisible → 404 | `DevPortalRestRegistrar` |
| Nexus beans (`RepositoryManager`, …) live in the child; `getBean()` on our (parent) context never looks downward | Services can't reach Nexus internals | `NexusBeanLocator` |

Both bridges exploit the same Spring guarantee: **a child context's
`ContextRefreshedEvent` propagates to parent-context listeners.**

**`DevPortalRestRegistrar`** — on the child's refresh event, resolves the
`ResteasyDeployment` bean from the event's context, polls until
`deployment.getRegistry()` is non-null (the registry only exists after
`ComponentContainerImpl.init()` has started the deployment inside Jetty), then
registers each JAX-RS resource instance directly:

```java
registry.addSingletonResource(resource);   // RESTEasy reads @Path/@GET off the instance
```

No Spring discovery involved; only the public RESTEasy 7 SPI. If the registry
never appears, it logs a warning after a 5-minute deadline and gives up —
Nexus keeps running, portal endpoints 404.

**`NexusBeanLocator`** — collects every refreshed context (newest first) and
lets services look up Nexus beans lazily at request time:

```java
beanLocator.lookup(RepositoryManager.class)   // Optional<RepositoryManager>
```

> **Startup-safety rule:** never `@Autowired` a Nexus-internal type
> (e.g. `RepositoryManager`) in the auto-configuration. It is not a bean in
> the main context, and an unsatisfied dependency there aborts the whole
> Nexus startup. Always resolve Nexus beans lazily through `NexusBeanLocator`.

---

## Layer diagram

```
┌───────────────────────────────────────────────────────────┐
│  Browser (vanilla JS SPA, zero build tooling)             │
│   Hash router · fetch-based API client · no frameworks    │
└──────────────────────┬────────────────────────────────────┘
                       │ HTTP/JSON
                       ▼
┌───────────────────────────────────────────────────────────┐
│  REST Layer  (jakarta.ws.rs 3.1, RESTEasy 7)              │
│   UiResource            ← serves index.html + assets      │
│   SearchApiResource     ← GET /devportal/api/search        │
│   RepositoryApiResource ← GET /devportal/api/repositories  │
│   RecentApiResource     ← GET /devportal/api/recent|popular│
│   (registered with RESTEasy by DevPortalRestRegistrar)    │
└──────────────────────┬────────────────────────────────────┘
                       │ constructor injection (Spring @Bean)
                       ▼
┌───────────────────────────────────────────────────────────┐
│  Service Layer                                            │
│   SearchService      — aggregates across all providers    │
│   RepositoryService  — RepositoryManager via NexusBeanLocator │
└──────────────────────┬────────────────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────────────────┐
│  Provider Layer   PackageProvider (interface)             │
│   NpmPackageProvider    (Phase 2)                         │
│   MavenPackageProvider  (Phase 3)                         │
│   PyPiPackageProvider   (future)                          │
│   DockerPackageProvider (future)                          │
└──────────────────────┬────────────────────────────────────┘
                       │ NexusBeanLocator (lazy, request-time)
                       ▼
┌───────────────────────────────────────────────────────────┐
│  Nexus internals (child Spring context / provided scope)  │
│   RepositoryManager · Repository · facets · search        │
└───────────────────────────────────────────────────────────┘
```

---

## Component wiring

`DevPortalAutoConfiguration` (discovered via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`)
declares every bean explicitly — no classpath scanning of our own packages:

```
NexusBeanLocator          ← event listener; collects contexts
RepositoryAccessFilter    ← NexusRepositoryAccessFilter(beanLocator); authz gate
RepositoryService         ← RepositoryServiceImpl(beanLocator, accessFilter)
NpmPackageProvider        ← (beanLocator, accessFilter)
MavenPackageProvider      ← (beanLocator, accessFilter)
SearchService             ← SearchServiceImpl(providers map)
UiResource, SearchApiResource, RepositoryApiResource,
RecentApiResource, PackageApiResource
DevPortalRestRegistrar    ← event listener; registers the REST resources above
```

REST resources implement `org.sonatype.nexus.rest.Resource` (which extends
`Component`) for semantic parity with Nexus's own resources, though with direct
registration only the JAX-RS annotations actually matter.

---

## Package provider contract

```java
public interface PackageProvider {
    String  getFormat();          // "npm", "maven2", …
    String  getDisplayName();     // "npm", "Maven", …
    boolean isAvailable();        // false if format not installed

    PagedResult<PackageSummary> search(SearchRequest request);
    List<PackageSummary>        recent(int limit);
    List<PackageSummary>        popular(int limit);
    Optional<PackageSummary>    findPackage(String repo, String group, String name);
    List<String>                listVersions(String repo, String group, String name);
}
```

`SearchServiceImpl` holds a `Map<String, PackageProvider>` keyed by format.
Adding a new format:

1. Implement the interface.
2. Register a `@Bean` for it in `DevPortalAutoConfiguration` and add it to the provider map.
3. Done — the service and REST layer need no changes.

---

## Static file serving

Static assets (HTML/CSS/JS) live on the classpath under `static/devportal/`.
`UiResource` reads them with `getClass().getResourceAsStream(…)` and streams
them back via JAX-RS.

- `GET /devportal/ui` (no trailing slash) issues a redirect to `/devportal/ui/`
  so the browser resolves relative asset URLs (`css/…`, `js/…`) against the
  right base. The redirect is built from the request URI — no hardcoded host
  or context path, so it stays correct behind reverse proxies.
- Unknown sub-paths fall back to `index.html` (SPA routing).
- Path traversal (`..`) is rejected.
- Content-Type is determined by file extension.

The SPA uses **hash-based routing** (`#/`, `#/search`, `#/repos`) so navigation
never triggers a full-page request — only the initial load and API calls go
over the network.

---

## Build & packaging

Plain `jar` packaging — no OSGi manifest, no KAR, no Sisu index.

```
nexus-developer-portal-<version>.jar
├── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
├── com/rxlogix/nexus/devportal/**            (all classes, own namespace)
└── static/devportal/**                       (SPA assets)
```

- **Java 21 target** — Nexus 3.94 classes are class-file version 65.
- All Nexus and Spring dependencies are `<scope>provided</scope>` — the fat JAR supplies them at runtime.
- `resteasy-core-spi` version must match the one inside the fat JAR (`BOOT-INF/lib/resteasy-core-spi-*.jar`).
- Nexus API JARs are **not on Maven Central**; `scripts/install-nexus-deps.sh`
  extracts them from the fat JAR (`unzip -p … BOOT-INF/lib/…`) into `~/.m2`.
  Note: in 3.94 `RepositoryManager` lives in `nexus-repository-config`
  (moved from `nexus-repository`).

### Deployment

```bash
# one-time per Nexus instance
echo "loader.path=/opt/sonatype/nexus/deploy" > $NEXUS_DATA/etc/loader.properties

# per release
cp nexus-developer-portal-<version>.jar $NEXUS_HOME/deploy/
# restart Nexus; success line in the log:
#   Dev Portal: all 4 REST resources registered with RESTEasy
```

---

## Security

- All REST resources are served through Nexus's RESTEasy dispatcher under
  `/service/rest`, behind Nexus's existing authentication filter chain. No new
  login mechanism, session store, or token generation.
- **Authentication** is Nexus's. A request arrives as either an authenticated
  user or — when Nexus's *Anonymous Access* is enabled — the anonymous user.
  With anonymous access disabled, unauthenticated requests are rejected before
  reaching the portal. "Public browsing" is therefore just Nexus's anonymous
  access setting; the portal adds nothing of its own.
- **Authorization** is enforced explicitly. `RepositoryManager.browse()` returns
  *all* repositories regardless of the caller, so every repository enumeration
  is passed through `RepositoryAccessFilter` before use. The production
  implementation (`NexusRepositoryAccessFilter`) delegates to Nexus's
  `RepositoryPermissionChecker.userCanBrowseRepositories(...)`, which evaluates
  repository-view permissions and content selectors against the request's Shiro
  subject — the same check Nexus's own browse/search endpoints use. A subject
  only sees repositories, search hits, and package details for repositories it is
  permitted to read; everything else is filtered out (package detail returns 404).
- The filter **fails closed**: if the permission checker cannot be resolved, no
  repositories are returned rather than risking exposure.
- This does not replace download-time security — artifact downloads still go
  through Nexus's own `/repository/*` handlers with their normal authorization.

---

## Phase roadmap

| Phase | Deliverable | Status |
|-------|-------------|--------|
| 1 | Plugin loading, REST registration, SPA UI, repository listing | ✅ done, verified on 3.94.0-12 |
| 2 | `NpmPackageProvider` — search, recent, versions, detail (author/deps/README) | ✅ done, verified on 3.94.0-12 |
| 3 | `MavenPackageProvider` — search, versions, detail, Maven XML snippet, POM-parsed dependencies | ✅ done, verified on 3.94.0-12 |
| 4 | Stats & caching | ✂️ dropped — not aligned with the developer-facing goal (and CE exposes no usage/popularity data) |
| — | Developer polish: rendered Markdown README, multi-tool install snippets (npm/yarn/pnpm, Maven/Gradle), version picker | ✅ done, verified on 3.94.0-12 |
| — | Verdaccio-style content tabs: Readme / Changelog (extracted from npm tarball) / Dependencies; Maven links out to POM url/SCM | ✅ done, verified on 3.94.0-12 |
| — | OSS release prep: license, version-compat matrix, CI smoke test | pending |
| — | Optional later: dark mode toggle, syntax highlighting | pending |
