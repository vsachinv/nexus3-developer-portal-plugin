# Screenshots

Captures of the running portal, used by the top-level [README](../../README.md).

| File | Page | Shows |
|------|------|-------|
| `home.png` | `#/` | Hero search, repository/format stats, "Recently Published" cards, repository grid |
| `search.png` | `#/search?q=portal` | Search results with format badges |
| `repositories.png` | `#/repos` | Repository cards with format filter chips |
| `package-npm.png` | npm package page | npm/yarn/pnpm install tabs + rendered Markdown README + Details (author, publisher, homepage) |
| `package-maven.png` | Maven package page | Maven/Gradle/Gradle-Kotlin install tabs + Versions + "Changelog / Source" and "Issues" links |

## Regenerating

These were rendered headlessly against a running instance (anonymous access
enabled, so no login needed):

```bash
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
BASE="http://localhost:8081/service/rest/devportal/ui/"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
  --window-size=1200,1600 --virtual-time-budget=9000 \
  --screenshot=home.png "${BASE}#/"
```

Repeat with the relevant `#/...` hash for each page. Note: headless capture
can't click, so the Changelog tab (which requires selecting it) isn't captured
here — view it live on any package that ships a `CHANGELOG.md`.
