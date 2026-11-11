# AGENTS.md

## What this is

Legacy **Play Framework 1.5.0** (Java) web app — forums + OpenTTD game leaderboard (www.strategyboard.net). No Maven/Gradle/SBT; everything goes through the `play` CLI (framework unzipped in `/opt`, see README).

## Setup (things that are gitignored but required)

- `conf/application.conf` is **not committed** — copy it from the server (per README). Without it nothing starts. Config is read via `Play.configuration.getProperty(...)`: `openttd.password`, `forum.pageSize`, `forum.attachments.path`, `anonymous.email`.
- Run `play dependencies` to install the `crud` and `secure` modules into `modules/` (declared in `conf/dependencies.yml`).
- `lib/` must contain the separately-built OpenTTD jars (`com.openttd.network/robot/admin/client` packages). They come from another project — "build and add the openttd dependencies" (README). No jar is in this repo.
- Requires MariaDB with a `www` database and a `play` user.

## Commands

- `play run` — dev server; `play start` — prod
- `play test` — test runner UI at `localhost:9000/@tests` (can select a single suite); `play auto-test` — headless full run
- No CI, no linter/formatter config in this repo.

## Tests

- Tests extend `play.test.UnitTest` (Play's JUnit wrapper), e.g. `test/UserTest.java`.
- `test/*.test.html` are Selenium suites using Play template fixture tags (`#{fixture delete: ... /}` `#{fixture load: 'data.yml' /}`) — they need a working DB and a running test server, not plain JUnit.
- Test data lives in `test/data.yml`; initial production data in `conf/initial-data.yml`.

## Architecture

- `conf/routes` is the single routing table. Controllers: `app/controllers/forum/`, `app/controllers/admin/`, `app/controllers/openttd/` (leaderboard + server start/stop), plus root `Application`, `Profile` (role checks), `Html5` (demos).
- Security uses the Play `secure` module plus custom role checks: `@Check(Profile.VIEWER)` etc. CRUD module is mounted at `/admin` (`@With(Profile.class)` guards controllers).
- JPA models in `app/models` (`forum/`, `leaderboard/`, `openttd/`); Groovy-based templates in `app/views` with custom tags in `app/views/tags`.
- The app talks to live OpenTTD servers as an admin/client bot (`app/openttd/`, `RobotConfiguration` enum maps scenarioId → admin port).
- `conf/routes` ends with catch-all `{controller}`/`{action}` routes — real routes are declared above them.

## Gotchas

- `tags` at the repo root is **generated ctags output** (~500 KB, committed) — do not read or edit it.
- `conf/log4j2.xml` is active logging config with security-hardened patterns (Log4Shell mitigations); it writes to `/home/eric/play/logs/openttd.log`.
- Play 1.x conventions: static controller methods (`public static void index()`), render via implicit template name, UTF-8 everywhere. Don't apply Spring/modern-Java patterns.
