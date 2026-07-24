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

## 2. How Nexus loads the plugin (read this first)

The plugin is a JAR you place in a `deploy/` folder. Nexus only picks it up if it
is started with Spring Boot's **`PropertiesLauncher`**, which reads a `loader.path`
pointing at that folder. This detail is why the two install types differ:

- **Docker** images already start Nexus with `PropertiesLauncher` → you only set
  `loader.path` (§4a).
- **Native / traditional installs** start Nexus with `java -jar` → that uses the
  JAR's default **`JarLauncher`**, which **ignores `loader.path` entirely**. You
  must switch the launcher to `PropertiesLauncher` (§4b), or nothing loads.

If you deploy and see no effect and no errors in the log, it's almost always
this: the launcher isn't `PropertiesLauncher`.

---

## 3. Get the JAR onto the server (binary-safe)

A JAR is a binary; transferring it as text (copy-paste, an ASCII/text-mode SFTP,
a web console, CRLF conversion) corrupts it. Always transfer in binary and
**verify the checksum**.

Reference for the shipped `1.0.0` artifact:

| | Value |
|---|---|
| Size | `80663` bytes |
| SHA-256 | `ce0b543fbd2510b378959324033c322f3615332ef5650016ec6d8e4d94a18605` |

```bash
# binary-safe copy (scp/rsync/sftp-binary), then verify ON THE SERVER:
scp nexus-developer-portal-1.0.0.jar user@host:/tmp/
sha256sum /tmp/nexus-developer-portal-1.0.0.jar          # must equal the value above
unzip -l  /tmp/nexus-developer-portal-1.0.0.jar | tail -1 # lists 60 files, no error
```

Only proceed once the checksum matches. A corrupt JAR fails silently.

---

## 4. Enable plugin loading (one-time, per Nexus instance)

Place the verified JAR in the deploy folder first. **Keep exactly one
`nexus-developer-portal-*.jar` there** — two copies load together and the beans
collide.

```bash
sudo cp /tmp/nexus-developer-portal-1.0.0.jar $NEXUS_HOME/deploy/
sudo chown nexus:nexus $NEXUS_HOME/deploy/nexus-developer-portal-1.0.0.jar
```

Then follow **4a (Docker)** or **4b (native)** for your install.

### 4a. Docker

The container already runs `PropertiesLauncher`; just point `loader.path` at the
deploy folder and restart.

```bash
docker exec <container> sh -c \
  'echo "loader.path=/opt/sonatype/nexus/deploy" > /nexus-data/etc/loader.properties'
docker cp nexus-developer-portal-1.0.0.jar <container>:/opt/sonatype/nexus/deploy/
docker restart <container>
```

### 4b. Native / traditional install (the `bin/nexus` launcher)

Native installs start with `java -jar` (JarLauncher), so you must (i) tell the
launcher where the deploy folder is, and (ii) switch it to `PropertiesLauncher`.
Paths below assume a typical layout — adjust `$NEXUS_HOME` to yours (e.g.
`/opt/nexus`, often a symlink to `/opt/nexus-<version>`).

**(i) Set `loader.path` as a JVM option** — add one line to the vmoptions file
(`$NEXUS_HOME/bin/nexus.vmoptions`), one option per line, no spaces:

```
-Dloader.path=/opt/nexus/deploy
```

**(ii) Switch the launcher to `PropertiesLauncher`.** The `bin/nexus` script
invokes `java … -jar "$bootJar"` on two lines (the `start` and `run` paths).
Change both to run `PropertiesLauncher` on the classpath instead:

```bash
sudo cp $NEXUS_HOME/bin/nexus $NEXUS_HOME/bin/nexus.bak          # backup / undo
sudo sed -i 's|-jar "\$bootJar"|-cp "\$bootJar" org.springframework.boot.loader.launch.PropertiesLauncher|g' \
  $NEXUS_HOME/bin/nexus
# verify: both lines now show PropertiesLauncher, and NO  -jar "$bootJar"  remains
grep -nE '\-jar "\$bootJar"|PropertiesLauncher' $NEXUS_HOME/bin/nexus
```

`PropertiesLauncher` reads the real application entry point from the JAR's
`Start-Class` manifest, so Nexus boots exactly as before — it now also honors
`-Dloader.path` and loads the plugin. (Editing the script does **not** restart
Nexus; the change takes effect on the next restart. To undo:
`sudo cp $NEXUS_HOME/bin/nexus.bak $NEXUS_HOME/bin/nexus`.)

> **Upgrade caveat:** a Nexus upgrade may overwrite `bin/nexus` (and
> `nexus.vmoptions`), reverting this. Re-apply the `sed` and the vmoptions line
> after upgrading. Keep both commands handy.

Optional but recommended before the service restart — test in the foreground and
watch for the success line, then `Ctrl-C`:

```bash
sudo -u nexus $NEXUS_HOME/bin/nexus run
#   look for:  Dev Portal: all 5 REST resources registered with RESTEasy
```

Then restart the service:

```bash
sudo systemctl restart nexus     # or your service name
```

---

## 5. Verify it loaded

Nexus takes roughly 90 seconds to start. Look for this line in the Nexus log
(`$NEXUS_DATA/log/nexus.log`, or `docker logs <container>`):

```
Dev Portal: all 5 REST resources registered with RESTEasy
```

Then:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  http://<nexus-host>:<port>/service/rest/devportal/ui
#   expect 200 (or a 303 redirect to ui/), not 404
```

If endpoints 404 and the log shows nothing about "Dev Portal", the JAR isn't on
the classpath — re-check the launcher (§2 / §4b) and that the JAR is valid (§3).
The plugin never affects Nexus's own startup: worst case the portal is
unavailable, Nexus itself is fine.

---

## 6. Using the portal

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

## 7. REST API (optional, for scripts/tooling)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/service/rest/devportal/api/repositories` | List supported repositories (`?format=npm`) |
| `GET` | `/service/rest/devportal/api/search?q=lodash` | Search packages (`&format=`, `&repository=`) |
| `GET` | `/service/rest/devportal/api/recent?limit=10` | Recently published |
| `GET` | `/service/rest/devportal/api/package?format=npm&name=lodash` | Full package detail (`&version=`, `&group=`, `&repository=`) |

All endpoints honor the caller's Nexus permissions.

---

## 8. Upgrades & uninstall

**Upgrade the plugin:** replace the JAR in `deploy/` (keep only one), restart.
On a native install the launcher change from §4b persists across plugin
upgrades — but re-check it after a *Nexus* version upgrade.

**Uninstall:**

```bash
rm $NEXUS_HOME/deploy/nexus-developer-portal-*.jar
# native: optionally revert bin/nexus (sudo cp bin/nexus.bak bin/nexus) and
#         remove the -Dloader.path line from nexus.vmoptions
# restart Nexus
```

---

## Troubleshooting

| Symptom | Cause / Fix |
|---------|-------------|
| Endpoints 404, and the log has **no** "Dev Portal" line at all | JAR not on the classpath. Native install still on `java -jar` (JarLauncher) — apply §4b. Or `loader.path`/deploy path wrong. |
| `unzip` says "not a zipfile" / size ≠ 80663 | JAR corrupted in transfer — re-copy in binary mode and verify SHA-256 (§3). |
| Startup log shows a bean/duplicate error | More than one `nexus-developer-portal-*.jar` in `deploy/` — keep only one. |
| Worked before, broke after a Nexus upgrade | The upgrade reset `bin/nexus` / `nexus.vmoptions` — re-apply §4b. |
| UI looks stale after an upgrade | Hard-reload the browser once (assets send `Cache-Control: no-cache`). |
| A package shows no README/Changelog | The package didn't publish one — npm README comes from published metadata; the Changelog tab needs a `CHANGELOG.md` inside the package. |
