# AGENTS.md

## What this is

Legacy **Play Framework 1.7.1** (Java) web app — forums + OpenTTD game leaderboard (www.strategyboard.net). No Maven/Gradle/SBT; everything goes through the `play` CLI (framework unzipped in `/opt` on the server; locally in `/home/eric/www/tools/play-1.7.1`, see README).

## Setup (things that are gitignored but required)

- `conf/application.conf` is **not committed** — copy it from the server (per README). Without it nothing starts. Config is read via `Play.configuration.getProperty(...)`: `openttd.password`, `forum.pageSize`, `forum.attachments.path`, `anonymous.email`.
- **Source the toolchain env before any `play`/`java`/`ant`/`gradle` command**: `. /home/eric/www/tools/env.sh`. It exports `JAVA_HOME` (Temurin JDK 17), `PLAY_HOME`, `GRADLE_HOME` and activates the py3.11 venv that `play`'s shebang needs. Unsourced, Play runs on Fedora's OpenJDK 25 and **every request 500s** — Groovy 3.0.10 cannot read JDK 25 class files, so route/template compilation dies with `GroovyBugError ... Unsupported class file major version 69` (reported against `{module:crud}/conf/routes`, `{module:secure}/conf/routes`, `/app/views/errors/500.html`).
- Never run `play dependencies` (nor `deps`, `--nosync`, `--forceCopy`) to install the `crud` and `secure` modules into `modules/` (declared in `conf/dependencies.yml`): it re-resolves `crud` from the retired Play 1 modules repo, deletes `tools/play-1.7.1/modules/crud`, and still exits 0. Instead symlink the modules bundled in the framework zip, with **absolute** targets (`ln -s "$PLAY_HOME/modules/crud" modules/crud` silently makes a dangling link when `PLAY_HOME` is unset):
  ```
  ln -s /home/eric/www/tools/play-1.7.1/modules/crud modules/crud
  ln -s /home/eric/www/tools/play-1.7.1/modules/secure modules/secure
  ```
  `modules/` is git-ignored, so the symlinks are local-only state. If an entry is a plain text file rather than a symlink, module routes and `controllers.Secure` are missing — replace it with a symlink, never with `play dependencies`.
- `lib/` must contain the separately-built OpenTTD jars (`com.openttd.network/robot/admin/client` packages). They come from another project — "build and add the openttd dependencies" (README). No jar is in this repo. Only `openttd-admin.jar` belongs in `lib/`; `openttd-robot.jar` shadows `com/openttd/robot/rule/*` constructor signatures.
- Requires MariaDB with a `www` database and a `play` user (`db=mysql://play:play@localhost/www`). No systemd in the container: start it with `sudo tools/start-mariadb.sh` (TCP 3306).
- Runtime directories must exist: `logs/` (log4j2 appender dir, resolved as `${sys:user.dir}/logs`; `play` also writes `logs/system.out`) and `/home/eric/www/upload`, plus `/home/eric/www/upload/test` for the `%test` id's `forum.attachments.path`.

## Commands

Always from `strategyboard/` in a shell that sourced `tools/env.sh`. The ladder is `play check` → `play precompile` → `play run` → `play test` → `play auto-test`.

- `play run` — dev server; `play start` — prod. In DEV mode `curl http://localhost:9000/@kill` stops it (`server.pid` goes stale otherwise).
- `play test` — test runner UI at `localhost:9000/@tests` (can select a single suite); the first request also starts JPA/Hibernate and loads the schema. `play auto-test` — headless full run.
- JPDA ports: 8001 (`play run`), 8002 (`play test` / `play auto-test`). Port 8000 is probed by something on the host and breaks the debugger handshake.
- `play --version` is not a command (`Invalid command`); the banner prints `play! 1.7.1` plus a "latest release 1.11.0" notice — ignore it, the app stays on 1.7.1.
- No CI, no linter/formatter config in this repo.

## Tests

- Unit tests extend `play.test.UnitTest` (Play's JUnit wrapper), e.g. `test/UserTest.java`; functional/page tests extend `BaseFunctionalTest` (`play.test.FunctionalTest`), e.g. `test/TopicsPageTest.java`. `test/controllers/Categories.java` is a test-only controller stand-in: test sources compile into the same class loader, so its actions become reversible through the catch-all routes — the application itself has no `Categories` controller.
- Shared setup, and the suites that use it: `BaseFunctionalTest` (fixtures, `login()`) → `BaseForumViewTest` (whitespace-collapsed markup, flash, template helpers) → `BaseCrudTest` (the CRUD module's markup); `BaseModelTest` (typed row lookups, printers, `failure`/`causeChain`) → `BaseOpenttdTest` (reflective access to the handler singletons plus the fake robot/sender/client). The bases are abstract, so the testrunner never runs them: 60 `*Test.java` files in `test/`, 55 suites in `test-result/`.
- `test/*.test.html` are Selenium suites using Play template fixture tags (`#{fixture delete: ... /}` `#{fixture load: 'data.yml' /}`) — they need a working DB and a running test server, not plain JUnit.
- Test data lives in `test/data.yml`; initial production data in `conf/initial-data.yml`. Several page tests load extra fixtures from `test/`: `Fixtures.loadModels("index-data.yml")`, `"scores-data.yml"`, `"openttd-data.yml"`, `"attachments-data.yml"`. **Never edit `test/data.yml` to make a test pass** — a new fixture goes in a new `test/*.yml` loaded by that suite. Like the other files in `test/`, a fixture file is rendered as a Play template before being parsed as YAML, so it must contain no play tag or `$` expression.
- `BaseFunctionalTest.loadFixtures()` runs `Fixtures.deleteDatabase()` + `Fixtures.loadModels("data.yml")` in a transaction of its own before **every** method, so fixture ids restart at 1 per test: `Admin` is always id 1, `Bob` id 3, `test/openttd-data.yml` scenarios always take ids 1..5. Assertions may pin those ids.
- **Test mode** (the official Play 1.x flow — the docs stop at 1.5.x): `play test` sets the framework id `test`, so `%test.*` keys in `application.conf` apply (`%test.db`, `%test.jpa.ddl=create`, `%test.mail.smtp=mock`, `%test.application.mode=dev`, `%test.jpda.port=8002`, `%test.forum.attachments.path`), `test/` is added to `Play.javaPath` only in test mode, and Play auto-loads the framework's `testrunner` module as `_testrunner`. That module has **no `conf/routes`**: the runner routes come from `TestRunnerPlugin.onRoutesLoaded()` — `/@tests`, `/@tests.list`, `GET|POST /@tests/{<.*>test}`, `/@tests/emails`, `/@tests/cache`. They are prepended, so they win over the app's catch-all route.
- **`play auto-test`** = test server + `play.modules.testrunner.FirePhoque` (htmlunit, classpath `modules/testrunner/conf` + `lib/play-testrunner.jar` + `firephoque/*.jar`): `GET /@tests.list` → `GET /@tests/init` (clears `test-result/`) → each `GET /@tests/<test>.class` (selenium suites go through `/public/test-runner/selenium/TestRunner.html`) → `GET /@tests/end?result=passed|failed`, which writes **`test-result/result.passed` or `result.failed`** — that marker is the pass/fail signal, plus one `<test>.passed.html` and `TEST-<test>.xml` per suite and `test-result/application.log`. Flags: `--unit`, `--functional`, `--selenium`, `--timeout=N`; knobs `%test.webclient.timeout` and `%test.headlessBrowser`. FirePhoque prints its per-suite `PASSED/FAILED` lines to the CLI, not to `logs/`, and **always exits 0**: the wrapper only checks the marker, so `play auto-test` exits 1 when `result.failed` exists (and prints `~ Some tests have failed`) and 0 with `~ All tests passed` when `result.passed` exists.
- The suite is 55 Java suites (632 tests) + 2 selenium suites (`Application.test.html`, `Forum.test.html`) and takes ~52 s wall clock, so `--timeout` and `%test.headlessBrowser` are not needed. Only the Java suites get a `TEST-<suite>.xml` (55); the selenium suites get `<test>.passed.html`, so 57 markers vs 55 XML files is normal. See `TEST_COVERAGE.md` for what each suite covers, the defects pinned in application code and the branches left uncovered on purpose.
- `play auto-test` is destructive: it deletes `tmp/` and `test-result/` at start, and `%test.jpa.ddl=create` drops and recreates the whole `www` schema every run.

### Test sources are compiled by Eclipse JDT, not javac

`play check` never compiles `test/` (test sources join `Play.javaPath` only in test mode), and Play's
`ApplicationCompiler` uses `org.eclipse.jdt.core` at compliance `java.source` — default `"11"`
(`ApplicationCompiler.JAVA_SOURCE_DEFAULT_VERSION`). JDT is stricter than javac about generic inference:
`GenericModel.findById()` and `findAll()` are declared `<T extends JPABase> T`, so JDT infers `JPABase` even
inside a lambda and rejects `for (PlayedGameScore score : PlayedGameScore.findAll())`
(`Type mismatch: cannot convert from element type JPABase to PlayedGameScore`) and a field access on
`PlayedGame.findById(1L)` (`begin cannot be resolved or is not a field`). Declare a typed local first
(`List<PlayedGameScore> scores = PlayedGameScore.findAll()`) or cast
(`(PlayedGame) PlayedGame.findById(id)`); javac passing is not proof. One uncompilable test file makes
`GET /@tests.list` answer 500 and takes the whole runner down — the reason appears in
`test-result/application.log` as `Compilation error (In /test/... around line N)`.

To check test sources before launching a server, run the same compiler at the same compliance level
(JDT batch `Main` rejects `dir/*` classpath entries, so the jars must be listed, and needs `-proc:none`):
`bash /tmp/opencode/check-tests.sh` (outside this repo).

### JPA and singletons inside tests

- **Never nest `JPA.withTransaction`.** The inner call re-binds and closes the outer EntityManager and
  unbinds the thread, so rows written by the outer block are lost. The fixture load in `BaseFunctionalTest`
  already nests inside the testrunner Job's transaction, which orphans that EntityManager and leaks one Hikari
  connection per suite — `%test.db.pool.maxSize` is raised in `conf/application.conf` for that.
- `OpenttdService.identifyUser()`, `saveGame()` and `endGame()` open transactions of their own
  (`JPA.startTx` / `closeTx` / `rollbackTx`), so call them **outside** an ambient transaction and read the rows
  they wrote back in a transaction of their own. An entity handed back by a closed transaction is detached and
  its lazy collection raises `org.hibernate.LazyInitializationException`.
- MariaDB keeps `DATETIME` to the second, so a date built in Java reads back as `java.sql.Timestamp` with its
  millis rounded down: compare millis or relative order, never the string form, and never pin two clock
  stamps that land in different seconds.
- `app/openttd/OpenttdAdminHandler` and `OpenttdClientHandler` are singletons holding a `robots` map and a
  `client` field, and `ExternalServices.getInstance()` holds the `OpenttdService` the handler wired. Suites
  inject fakes through reflection and must remove them in a `finally`: `OpenttdPageTest` asserts every scenario
  answers `isRunning == false`, and suites run alphabetically, so a leaked robot breaks the suite that follows.
  A real `OpenttdAdminHandler.startup()` opens an admin socket (3977..4377) with nothing behind it and logs
  pairs of `ERROR ~ Connection refused` into `test-result/application.log` — expected noise, not a failure.
- Static helpers of a test class must not be readable as a property of the `Class` object (see the `get(String)`
  gotcha below): the `app/openttd` helpers in `test/BaseOpenttdTest.java` are `clientHeld()` / `holdClient(...)`,
  never `getClient()` / `setClient(...)`.
- A stale `play test` JVM keeps port 9000 and JPDA 8002 and answers `/@tests.list` from the sources it
  compiled at start-up. Kill it with `pgrep -f "^/home/eric/www/tools/jdk-17/bin/java"` — `pgrep -af
  "play.server.Server"` matches the bash wrapper too and kills the command that is running.

### Test-class gotcha: never name a static helper `get(String)`

Groovy resolves a **static single-argument `get(String)`** on a class as a *generic property getter* for the `Class` object itself. The runner UI (`{module:testrunner}/app/views/TestRunner/index.html`) reads `${test.name}` for every test class, so such a method is invoked as `get("name")` while rendering `/@tests` — for a `FunctionalTest` helper that means `GET("name")` → no route → 500 `play.mvc.results.NotFound: GET name`. `test/TagsCallersTest.java` therefore uses `getPage(String url)`, not `get(String url)`. Same trap for any one-arg static `get`/`set` (or `getX()`-style) helper reachable from a test class.

### Debugging a template 500

`play.mvc.results.Result` extends `play.utils.FastRuntimeException`, which **generates no stack trace**, so `BaseTemplate.throwException` finds no template frame and the DEV error page shows only the controller line — the template name/line is unavailable. To locate it: temporarily shadow the module template in the app (`app/views/<Module>/<name>.html` — `app/views` is first in `Play.templatesPath`, so it wins over module views) and probe from a `%{ ... }%` block, wrapping each suspect expression in a `try`/`catch (Throwable)`. Inside a template the binding variable `play` shadows the `play` package, so reach framework classes as `Class.forName('play.mvc.Router')` / `Class.forName('play.Play')` (static methods and `routes` work on the `Class` object). Delete the shadow afterwards.

## Architecture

- `conf/routes` is the single routing table. Controllers: `app/controllers/forum/`, `app/controllers/admin/`, `app/controllers/openttd/` (leaderboard + server start/stop), plus root `Application`, `Profile` (role checks), `Html5` (demos).
- Security uses the Play `secure` module plus custom role checks: `@Check(Profile.VIEWER)` etc. CRUD module is mounted at `/admin` (`@With(Profile.class)` guards controllers).
- JPA models in `app/models` (`forum/`, `leaderboard/`, `openttd/`); Groovy-based templates in `app/views` with custom tags in `app/views/tags`. `TemplateLoader.load(name)` searches `Play.templatesPath` in order — app views first, modules second, `framework/templates` last — so an app template shadows a module one.
- Static asset routing is `GET /public/ staticDir:public` plus `GET /images/ staticDir:public/images`; module assets (`/public/test-runner/**`) are served through it via the virtual-file search, so no extra route is needed.
- The app talks to live OpenTTD servers as an admin/client bot (`app/openttd/`, `RobotConfiguration` enum maps scenarioId → admin port).
- `conf/routes` ends with catch-all `{controller}`/`{action}` routes — real routes are declared above them.

## Gotchas

- Play Framework online documentation is limited to the version `1.5.x`, available at `https://www.playframework.com/documentation/1.5.x/home`; 1.7.1 behaves the same for routes/templates/test mode.
- `tags` at the repo root is **generated ctags output** (~500 KB, committed) — do not read or edit it.
- `conf/log4j2.xml` is active logging config with security-hardened patterns (Log4Shell mitigations). Paths are relative to `${sys:user.dir}`, so running `play` from `strategyboard/` writes `strategyboard/logs/{application,openttd}.log` (rotated as `application-<date>.log`); the old `/home/eric/play/logs/openttd.log` path is the server's project root. In test mode the app log is duplicated into `test-result/application.log`.
- Requests to `/captcha` print an NPE stack to stderr (`jj.play.ns.nl.captcha.util.ImageUtil.applyFilter → RippleGimpyRenderer.gimp`, `"this.inPixels" is null`): `jj-simplecaptcha.jar` ships no image resources, so the ripple filter runs on an AWT `ToolkitImage` with no pixel producer. The response status is already 200 when the stream fails, so the page tests still pass and log4j2 records nothing — pre-existing library wart, not a test-runner failure.
- `/@routes`, `/@mem`, `/@info`, `/@help` log `ActionNotFoundException` (`Controller controllers.@mem not found`): Play 1.7.1 has no such dev pages, and the app's catch-all route matches them. Expected noise, not a regression.
- Play 1.x conventions: static controller methods (`public static void index()`), render via implicit template name, UTF-8 everywhere. Don't apply Spring/modern-Java patterns.
