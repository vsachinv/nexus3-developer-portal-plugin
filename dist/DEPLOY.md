# Nexus Developer Portal — Deploy & Use

A drop-in developer package portal for **Nexus Repository CE 3.94+**
(Verdaccio-style browsing of your npm and Maven repositories). This folder ships
the ready-to-deploy `nexus-developer-portal-1.0.0.jar` — **no build required**.

---

## 1. What you need

- A running **Nexus Repository CE 3.94.0 or later** (Spring Boot fat-JAR build).
  Nexus 3.93 and earlier (OSGi/Karaf) are **not** supported.
- Filesystem access to the Nexus install (`$NEXUS_HOME`) and data dir
  (`$NEXUS_DATA`), or `docker exec`/`docker cp` for containers.
- Ability to restart Nexus.

You do **not** need Java, Maven, or any build tooling to deploy — those are only
for building from source.

---

## 2. Required configuration change (one-time, per Nexus instance)

This is the one "other change" you must make. Nexus 3.94 will only load JARs
from its `deploy/` folder if a `loader.properties` file points its classpath
loader there. Create it once:

```bash
cat > $NEXUS_DATA/etc/loader.properties <<'EOF'
loader.path=/opt/sonatype/nexus/deploy
EOF
```

- `$NEXUS_DATA` is the Nexus data directory (in Docker: `/nexus-data`).
- If your `deploy/` folder lives elsewhere, set `loader.path` to that absolute path.
- Without this file, the JAR is silently ignored — nothing else will work.

---

## 3. Deploy the plugin

```bash
cp nexus-developer-portal-1.0.0.jar $NEXUS_HOME/deploy/
# then restart Nexus
```

**Rule:** keep exactly **one** `nexus-developer-portal-*.jar` in `deploy/`.
Remove any older version first — if two are present, Nexus loads both and the
plugin's beans collide.

### Docker

```bash
# one-time config
docker exec <container> sh -c \
  'echo "loader.path=/opt/sonatype/nexus/deploy" > /nexus-data/etc/loader.properties'

# deploy + restart
docker cp nexus-developer-portal-1.0.0.jar <container>:/opt/sonatype/nexus/deploy/
docker restart <container>
```

---

## 4. Verify it loaded

Nexus takes roughly 90 seconds to start. Look for this line in the Nexus log
(`$NEXUS_DATA/log/nexus.log`, or `docker logs <container>`):

```
Dev Portal: all 5 REST resources registered with RESTEasy
```

If you see it, the plugin is live. If endpoints return 404, the JAR loaded but
registration didn't happen — re-check step 2 (`loader.properties`) and confirm
the JAR is actually in the `deploy/` folder. The plugin never affects Nexus's
own startup: worst case the portal is unavailable, Nexus itself is fine.

---

## 5. Using the portal

Open in a browser:

```
http://<nexus-host>:<port>/service/rest/devportal/ui
```

(The URL sits under `/service/rest` because the plugin registers with Nexus's
REST layer; that also means it runs behind Nexus's existing authentication.)

What you can do:

- **Home** — search box plus a "Recently Published" feed and your repositories.
- **Search / Packages** — find packages by name or description; filter by format.
- **Repositories** — browse supported repositories with format filters.
- **Package page** — for each package:
  - Tabbed **Readme / Changelog / Dependencies** (README and CHANGELOG rendered
    as Markdown; the Changelog tab appears only when the package ships one).
  - **Install snippets** for the right tool — npm/yarn/pnpm, or Maven/Gradle —
    each with a copy button.
  - **Version picker** — view any published version; install snippets pin to the
    selected version.
  - **Details** — author, who published it, and links out to the project
    homepage / source / issue tracker.

### Access & visibility

- **Authentication is Nexus's.** Requests arrive as the logged-in user, or — if
  Nexus's *Anonymous Access* is enabled — as the anonymous user. Enable
  anonymous access for no-login public browsing; disable it to require a Nexus
  account.
- **Visibility follows Nexus permissions.** A user only sees repositories,
  search results, and package pages for repositories they're permitted to read.

### Supported formats

- **npm** and **Maven (maven2)** hosted/proxy/group repositories.
- The provider layer is pluggable; other formats can be added without touching
  the UI or REST layer.

---

## 6. REST API (optional, for scripts/tooling)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/service/rest/devportal/api/repositories` | List supported repositories (`?format=npm`) |
| `GET` | `/service/rest/devportal/api/search?q=lodash` | Search packages (`&format=`, `&repository=`) |
| `GET` | `/service/rest/devportal/api/recent?limit=10` | Recently published |
| `GET` | `/service/rest/devportal/api/package?format=npm&name=lodash` | Full package detail (`&version=`, `&group=`, `&repository=`) |

All endpoints honor the caller's Nexus permissions.

---

## 7. Uninstall

```bash
rm $NEXUS_HOME/deploy/nexus-developer-portal-*.jar
# restart Nexus
```

Optionally remove `$NEXUS_DATA/etc/loader.properties` if nothing else in
`deploy/` needs it.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Endpoints 404 after restart | `loader.properties` missing/wrong (step 2), or JAR not in `deploy/`. |
| Startup log shows a bean/duplicate error | More than one `nexus-developer-portal-*.jar` in `deploy/` — keep only one. |
| UI looks stale after an upgrade | Hard-reload the browser once (assets send `Cache-Control: no-cache`, so a reload re-fetches). |
| A package shows no README/Changelog | The package didn't publish one — npm README comes from published metadata; the Changelog tab needs a `CHANGELOG.md` in the package. |
