# Test coverage

Task: fill the missing unit tests of this app — controllers first, then models, then `app/openttd`.
Notes on what was done, what the assertions pin, what was left uncovered and why.

## Status

Run on 2026-11-22, in a shell that sourced `tools/env.sh`, from `strategyboard/`:

- `play check` → exit 0
- `play precompile` → exit 0
- `play auto-test` → exit 0, `test-result/result.passed`, `~ All tests passed`, ~52 s wall clock
- 56 Java suites + 2 Selenium suites (`Application.test.html`, `Forum.test.html`), **659 tests, 0 failures, 0 errors** across the `TEST-*.xml` reports

Before this task there were 6 Java suites (25 tests) plus the 2 Selenium suites. The task added 51 suites
and 657 tests. The test sources are committed on `master` (`full test coverage for application.html`); the
scratch probe suites used to read the behaviour were deleted after transcription, and `conf/application.conf`
stays untracked as usual. This file and the `AGENTS.md` updates below it are not committed yet.

The pass signal is `test-result/result.passed` (or `result.failed`), not the CLI: `play auto-test` exits 1
only when `result.failed` exists, and FirePhoque itself always exits 0. Per suite there is a
`<suite>.passed.html`, a `TEST-<suite>.xml` (56 of them, the Selenium suites only produce a `.passed.html`),
and the app log duplicated into `test-result/application.log`.

## How the suites are built

- `test/BaseFunctionalTest.java` — extends `play.test.FunctionalTest`, seeds `test/data.yml` in `@Before`
  (`Fixtures.deleteDatabase()` + `Fixtures.loadModels()` inside its own transaction). Because the fixture is
  reloaded per method, **fixture ids restart at 1 for every test**: `Admin` is always id 1, `Bob` id 3, the
  scenario fixtures always take ids 1..5.
- `test/BaseForumViewTest.java` (`extends BaseFunctionalTest`) — whitespace-collapsed markup helpers, cookie/session handling, flash, rendered-template helpers. Shared by the page suites and by the suites that drive one action directly.
- `test/BaseCrudTest.java` (`extends BaseForumViewTest`) — the CRUD module markup helpers (list, form, footer, pagination), used by the seven `CrudAdmin*Test` suites that drive the module's list/new/edit/show/delete actions. `CrudAdminPagesTest` and `CrudIndexForbiddenTest` only check module markup and access, so they stay on `BaseForumViewTest`.
- `test/BaseModelTest.java` (`extends BaseForumViewTest`) — typed row lookups and the printers the model suites assert against, plus `failure` / `causeChain` / `assertTransactionFailureContains` for the branches that raise.
- `test/BaseOpenttdTest.java` (`extends BaseModelTest`) — reflective access to the two handler singletons, the fake robot/sender/client, and the read helpers of the `app/openttd` suites.
- `test/TemplateStandIns.java` and `test/controllers/Categories.java` — stand-ins that make an unreachable template or controller reachable: `TemplateStandIns` supplies the object type and rows `CrudAdminPagesTest` needs to render `AdminCategories/list.html` (the real request cannot reach it, see below), and `test/controllers/Categories.java` is a test-only controller whose actions become reachable through the catch-all routes.
- Extra fixtures live in new files (`test/index-data.yml`, `test/scores-data.yml`, `test/openttd-data.yml`,
  `test/attachments-data.yml`); `test/data.yml` was never edited.

The five bases are abstract and `TemplateStandIns` is not a test class, so `test/` holds 61 `*Test.java`
files while the runner lists 56 suites.

Assertions pin exact observed values rather than plausible ones: every string below was read from a scratch
probe run against the real framework, and the probe was deleted afterwards. Where a value cannot be pinned
(see "Deliberately uncovered"), the doc comment of the case says so instead of asserting a guess.

## Coverage

| Area | Suites | Tests |
| --- | --- | --- |
| CRUD admin pages (`app/controllers/admin/`, CRUD module) | 9 | 164 |
| Controller actions and tasks | 8 | 77 |
| Models (`app/models/`) | 17 | 163 |
| `app/openttd/` | 3 | 30 |
| Pages, templates, tags, roles | 20 | 248 |

| Suite | Tests | What it holds |
| --- | --- | --- |
| `CrudAdminAttachmentsTest` | 21 | `controllers.admin.AdminAttachments` (CRUD over `Attachment`): list rows, the optional `(None)` relation options, `mimetype`/`filename`/`realfilename` (`@Required`), `at` as `yyyy-MM-dd`, create/update/delete |
| `CrudAdminCategoriesTest` | 18 | `controllers.admin.AdminCategories` over `Category`: `name` (`@MaxSize(50)`), `displayOrder`, and the `crud_unknown` block a `@OneToMany` renders (posting `object.forums.id` changes nothing) |
| `CrudAdminForumsTest` | 20 | `controllers.admin.AdminForums` over `Forum`: text, long text, number, category select, the `locked` checkbox (`crud.checkField` posts a false input when the box is absent, so an omitted flag writes false), `crud_unknown` for `topics`, validation and delete |
| `CrudAdminPagesTest` | 8 | `app/views/admin/AdminForums/list.html` and `AdminCategories/list.html`, the two list templates the app overrides inside the CRUD layout |
| `CrudAdminPermissionsTest` | 19 | `controllers.admin.AdminPermissions` over `Permission`: the two `@ManyToOne` ids (`object.category.id`, `object.group.id`), `accessLevel`, and `toString()` as the breadcrumb and row label |
| `CrudAdminTopicsTest` | 20 | `controllers.admin.AdminTopics` over `Topic`: forum relation with `(None)`, `views`, locked/hidden, `crud_unknown` for posts, and the empty POST that creates a row because nothing is `@Required` |
| `CrudAdminUserGroupsTest` | 22 | `controllers.admin.AdminUserGroups` over `UserGroup`: `name`, `description`, `admin` checkbox, `crud_unknown` for permissions |
| `CrudAdminUsersTest` | 27 | `controllers.admin.AdminUsers` over `User`: `email` (`@Email`), `passwordHash`, `name`, `group` (`object.group.id`), `uuid`, `confirmed`, `active`, and the `object_email` style field ids the templates write |
| `CrudIndexForbiddenTest` | 8 | `controllers.CRUD.index()` reached as `/crud`, `/crud/index`, `/admin` and `/admin/`: the module class forbids itself (403 for everybody, anonymous included), the mounted root page renders and sends an anonymous visitor to `/login` |
| `AttachmentsDownloadTest` | 11 | `forum.Attachments.download()` as `GET /attachments/{id}`: unknown id → 404, the file served, missing file on disk, forum/topic visibility, anonymous and logged-in |
| `TopicsDeleteTest` | 9 | `forum.Topics.delete()` as POST only: unknown id → 404, hidden topic, moderator and administrator paths, cascaded posts and counters, the refused deletes |
| `TopicsLockTest` | 15 | `forum.Topics.lock()` (GET), `hide()` (POST) and `move()` (POST) read back from the row, and the guards that follow: `reply()` / `createReply()` refuse a topic locked through the UI and store no `Post`, the Reply link leaves `Topics/show.html`, a hidden topic is 403 below `Permission.moderate` and dropped from `Forums/show.html`, a locked hidden topic is still locked and still readable for the moderator who locked it, unknown ids → 404, anonymous → `/login`, replyer → 403 |
| `LogoutFlowTest` | 12 | `controllers.Secure.login()` / `logout()` plus `Profile.Security.authenticate()`, `onAuthenticated()`, `onDisconnect()`: remember-me cookie, session cookie, flash, the `uuid` login token |
| `ScoresExportCsvTest` | 6 | `openttd.Scores.exportAsCSV()` and the two routes that reach it: the `@Check(Profile.MODERATOR)` branches, the `text/csv` content type, the empty body the action can only produce |
| `ExportScoresCsvGeneratorTest` | 6 | `tasks/ExportScoresCsvGenerator` without a request: `hasMoreData()`, the chunk iterator, the `FutureTask` nothing ever starts |
| `LegacyMd5Test` | 6 | `controllers/legacy/MD5.crypt()` and `User.checkPassword()` against a legacy hash; the empty/null guard, and the `NoSuchAlgorithmException` catch that is unreachable |
| `AttachmentModelTest`, `CategoryModelTest`, `ForumModelTest`, `GameModelTest`, `MessageTest`, `PermissionModelTest`, `PlayedGameModelTest`, `PlayedGameScoreModelTest`, `PlayerScoreTest`, `PostModelTest`, `RobotConfigurationTest`, `ScenarioModelTest`, `TopicModelTest`, `OpenttdServerTest`, `UserGroupModelTest`, `UserModelTest`, `UserTest` | 162 | Every model of `app/models/forum/`, `app/models/leaderboard/`, `app/models/openttd/`: finders, pagination, ordering, validation, the counters a cascade moves, and the delete branches a foreign key refuses. `Category.getVisibleTopicsCount()` / `getVisiblePostsCount()` drop `Topic.hidden`, and are read through the entity's own paths (`Topic.count("hidden = false")`, not `topic.hidden = false`) |
| `OpenttdServiceTest` | 18 | `identifyUser`, `saveGame`, `endGame`, `exposeGame` / `getGame` |
| `OpenttdAdminHandlerTest` | 8 | Singleton, `robots` map, `isRunning`, `restart`, `shutdown`, `startup` of an unmapped scenario |
| `OpenttdClientHandlerTest` | 4 | Singleton, `client` field, `isRunning`, `shutdown` |
| `IndexPageTest`, `ForumsPageTest`, `TopicsPageTest`, `MessagesPageTest`, `UsersPageTest`, `ScoresPageTest`, `OpenttdPageTest`, `LoginPageTest`, `ErrorPagesTest`, `Html5PagesTest`, `CategoriesViewsTest`, `SecureTagsTest`, `TagsTest`, `TagsCallersTest`, `NotifierTemplatesTest`, `AdministratorTest`, `AnonymousTest`, `ModeratorTest`, `ApplicationTest`, `BasicTest` | 239 | Every page template, the tag library of `app/views/tags`, the `Notifier` templates, and the role checks of `controllers.Profile` for administrator / moderator / replyer / anonymous. `ForumsPageTest` counts visible posts/topics below `Permission.moderate` and full ones at moderate, per category; `IndexPageTest` hides a hidden news topic from everybody and asserts the narrow column of `Application/index.html` holds only the leaderboard (`leftColumnRendersOnlyTheLeaderboardPanel`, which replaced the commented `#{miniOpenTTDStatus}` block `cleanup and script typing` removed - Play compiles tags inside HTML comments, so those five "Server is down :(" panels used to render on every home page); `MessagesPageTest` drives `/messages/inbox` and `/messages/outbox` and the navbar links, and sends an anonymous session to the home page; `UsersPageTest` does the same for `/profile`; `ErrorPagesTest` renders `errors/403.html`, `errors/500.html` and `errors/404.html`, and asserts the `NotFound` an unmatched url raises plus the 404 page rendered without an EntityManager |

## Defects the tests pinned (application code left as it is)

`app/openttd/OpenttdService.java`:

- `saveGame()` logs the collection it was given **before** opening its transaction, and
  `com.openttd.robot.model.GamePlayer.toString()` dereferences `externalUser`. One company that never
  logged in therefore raises `NullPointerException: Cannot invoke "com.openttd.robot.model.ExternalUser.getId()"
  because "this.externalUser" is null` **outside** the `try`, so nothing at all is written — not even the
  games of the players that do have an account.
- `saveGame(id, null)` raises inside the transaction, and `catch(Exception)` swallows it: the caller sees a
  normal return and `Logger.info("saveGameStat() : Fin")`, while no `PlayedGame` row exists.
- An unknown scenario id writes the game anyway: `Scenario.findById()` answers null and
  `PlayedGame.scenario` is a plain `@ManyToOne` with no column constraint, so the row lands with
  `scenario_id == null`.
- Players are filtered by `userId != 0` and by an existing `User`; the ones that fail are logged
  (`userId null`, `user null`, `Pas de CEO`) and dropped. The filter keys on `GamePlayer`, not on `User`, so
  the same account twice in the collection writes two scores of one game.
- `begin` comes from `gameStartingDateByScenario` when the scenario has one, otherwise from the clock.
  The scores of one call are iterated from a `Map<GamePlayer, User>`, whose order is the identity hash of the
  `GamePlayer` instances, so the ids the scores get are not pinnable — the suites assert the rows sorted.
- `endGame()` restarts, shuts down and starts the robot of the scenario, then records the starting date. The
  block that asked `OpenttdClientHandler` is commented out, so the client handler is reachable only through
  its own singleton. Because `gameStartingDateByScenario.put()` is the last statement, a `startup()` that
  raises records nothing.

`app/openttd/OpenttdAdminHandler.java`:

- `startup()` reads `configuration.adminPort` on the result of `RobotConfiguration.fromScenarioId()`, which
  answers null for a scenario id outside 1..5. The NPE (`Try to read adminPort on null object
  models.openttd.RobotConfiguration (openttd.OpenttdAdminHandler.startup, line 45)`) happens before
  `robots.put()`, so the map stays untouched and the scenario is not running afterwards.
- `RobotConfiguration.fromScenarioId()` matches `scenarioId`, never `adminPort`, so passing an admin port
  (4377 is Toyland's) raises instead of starting that scenario.
- `restart()` guards on the map entry, not on the connection: it sends `restart` through `getSend()` whenever
  a robot is held, connected or not, and sends nothing when `getSend()` answers null. `shutdown()` asks the
  robot to disconnect and keeps the entry, so `isRunning()` turns false while the map still holds the robot.

`app/openttd/OpenttdClientHandler.java`: `shutdown()` never clears the `client` field, so a second shutdown
asks the same client again, and a client that reports connected after being shut down is reported running.
Only `startup()` replaces the field.

`app/models/`:

- `Scenario.getImage()` and `getTrainImage()` switch on `Id.fromValue(this.id)`, which answers null outside
  ids 1..5, so both raise `NullPointerException: Cannot invoke "models.leaderboard.Scenario$Id.ordinal()"`.
  `getTrainImage()` also folds `default` into the `HighSpeed` case, so an id that does reach the switch gets
  the high-speed train.
- `PlayedGame.getBestPlayedGamesForScenarioId()` is declared `List<PlayedGame>` but selects three columns, so
  its rows are `Object[]`, not `PlayedGame`.
- `PlayedGameScore.getLastMonthPlayerScores(page, pageSize)` ignores both arguments and calls
  `getPlayerScores(from, to, 1, 10)`; the pagination of "this month" is hardcoded.
- Deleting a row another row points at is refused by the database rather than by the app, and the message is
  the MySQL constraint: `Permission.category_id`, `Post.topic_id`, `Attachment.post_id`, `Post.postedBy_id`,
  `User.group_id`, `PlayedGame.scenario_id`, `Scenario.game_id`, `PlayedGameScore.playedGame_id`.
- `models.openttd.RobotConfiguration.fromScenarioId()` answers null for an id no constant carries, and
  `OpenttdServer.getStatus()` then raises `Try to read scenarioId on null object ... (line 28)`, which the
  openttd page renders as "Server is down :(".

Controllers, tasks and templates:

- `Scores.exportAsCSV()` can never write a row: it calls `await(generator.nextDataChunk())` on a `FutureTask`
  nothing ever starts (`tasks/ExportScoresCsvGenerator.nextDataChunk()` builds the task but never runs it and
  never gives it to a thread), so `hasMoreData()` stays true once the table holds a row and the request blocks
  forever. The only answer the action can produce is an empty `text/csv` body with a 200 and no
  `Content-Length`, whatever the data holds — `ScoresExportCsvTest` pins exactly that.
- `conf/routes` declares `GET /scores/{period}` above `GET /scores/exportCSV`, so `/scores/exportCSV` is
  matched by the period route and `Scores.show("exportCSV", null)` normalizes the unknown period to
  `last_month` and renders the leaderboard page instead of a CSV. The action is reached only through the
  catch-all, as `GET /openttd.scores/exportAsCSV`. The misspelled variant `/openttd.scores/exportCSV` raises
  `ActionNotFoundException` in `play.Invoker$Invocation.init()`, which leaves the JPA transaction of that
  invocation open and leaks one Hikari connection per request — deliberately not asserted, it would starve
  the pool for every suite that runs afterwards.
- `controllers.CRUD.index()` forbids itself when reached as the module class: `GET /crud` and `GET /crud/index`
  resolve through the catch-all to `controllers.CRUD`, whose guard fires and answers 403 to everybody,
  anonymous included, because `controllers.CRUD` carries no `@With(Secure.class)` and no `@Check`. Reached
  through `GET /admin` / `GET /admin/` (`modules/crud/conf/routes`) the guard does not fire and the module's
  type list renders, with the mount guarded by the secure module.
- Four templates no request can reach, covered by a direct render through `BaseForumViewTest.renderView`, with
  the reason stated in the suite's doc comment: `app/views/Categories/index.html` and `Categories/show.html`
  (`conf/routes` has no line for `Categories`), `app/views/errors/403.html` (`#{if connected}` reads a name
  nothing ever binds, so the true branch is dead code in production), `app/views/tags/secure/checkNot.tag` and
  `isConnected.tag` (the secure module's own `check.tag` inlines its logic and never delegates to them), and
  `app/views/admin/AdminCategories/list.html`, whose `#{crud.table fields:['name', 'description', 'forums']}`
  asks for a `description` field `models.forum.Category` does not have, so a real administrator request to
  `/admin/admincategories` fails with a NullPointerException in the table tag. `TemplateStandIns` supplies the
  missing field and its descriptor so the markup can still be asserted.

## Fixes made while writing the tests (already committed)

These were found by the suites and corrected in the application, not worked around in the tests:

- `Application.index()` now renders `page`, which `app/views/application.html` passes to the pagination tag —
  action parameters are not added to `renderArgs` by the framework.
- `Forums.toggleLock()` and `Topics.show()` call `save()`: Play's Hibernate setup does not flush a plain
  field mutation, so the flash claimed a lock the row never got, and the view counter never incremented.
- `Message.outbox()` / `Message.outboxCount()` use an explicit HQL query: `find("from", user)` cannot be
  parsed because `from` is reserved in HQL.
- `conf/routes` binds the attachment route argument as `id`, matching `forum.Attachments.download(Long id)`:
  with `attachmentId` the parameter stayed null and every download failed with "id to load is required".

Added on 2026-11-21, found by `TopicsLockTest` and by an administrator reading a hidden topic:

- `Topics.lock()`, `Topics.hide()` and `Topics.move()` call `save()` after the field mutation, for the same
  reason `Forums.lock()` does. Without it the row kept its old value while `flash.success()` announced the
  change, so a moderator locked a topic and `Topics.reply()` / `Topics.createReply()` still accepted a reply,
  a hidden topic stayed visible to everybody below `Profile.MODERATOR`, and a moved topic stayed in its
  source forum. `ModeratorTest.canLockATopic()` / `canHideATopic()` / `canMoveATopic()` only assert status
  codes, which is why the defect survived them; `TopicsLockTest` reads the row.
- `app/views/forum/Topics/show.html` renders `(Un)hide` as a `<form method="POST">` with a `<button>`: the
  route is POST only, so the plain link was a GET, fell through to the catch-all route and answered 500
  (`JPAException: No active EntityManager`, because `PlayHandler.serve404()` renders `main.html` whose
  `#{secure.check}` tag queries `User.findByEmail()` outside a transaction). `(Un)lock` stays a GET link —
  its route is GET, and `ModeratorTest.canLockATopic()` drives it as such.
- `Topics.show()` gates `topic.hidden` on `accessLevel() < Permission.moderate` instead of forbidding on the
  flag alone. It used to answer 403 for everybody, admins included, while `Forums.show()` lists hidden topics
  to anyone at `Permission.moderate` or above: the forum page offered the link and the topic page refused it.
  A moderator or an administrator now reads it (and the view counter increments); anonymous (`Permission.view`
  = 1) and a replyer (`Permission.post` = 3) still get 403. `AdministratorTest`, `ModeratorTest` and
  `TopicsPageTest` pinned the old 403-for-everybody behaviour and now pin the boundary;
  `TopicsPageTest.hiddenTopicKeepsItsViewCountForAProfileBelowModerate` pins that a refused read does not
  increment `views`. `Topics.reply()` and `Attachments.download()` ignore `hidden`, so a replyer who knows the
  url can still reply to or download from a hidden topic — `replyFormOnAHiddenTopicIsRenderedBecauseReplyIgnoresHidden`
  keeps that as today's behaviour. `Application.index()` no longer lists hidden topics on the public
  homepage — see the 2026-11-22 block below.

Added on 2026-11-22, found by an anonymous `/profile`, and by curl against `play run`:

- Visibility is a topic property only: `models.forum.Forum` carries `locked` and nothing else, and the
  production `Forum` table is `id, description, displayOrder, locked, name, category_id`. `Topics.show()`
  refuses a hidden topic below `Permission.moderate` only, `Forums.index()` counts topics and posts per
  category through `Category.getVisibleTopicsCount()` / `getVisiblePostsCount()` below that level, and
  `Application.index()` reads `getVisibleTopics()`.
- `Profile.Security.check()` wraps `User.findByEmail()` in a `try` and answers false when it raises.
  `PlayHandler.serve404()` renders `errors/404.html` - and `main.html`, with its `#{secure.check}` tags -
  outside any controller, so no JPA interceptor ran and there is no EntityManager; the lookup used to raise
  `JPAException`, and the unmatched route answered 500 instead of 404. Verified over HTTP: `GET
  /no/controller/here` and `GET /nope` answer 404 with that page. `play.test.FunctionalTest` calls
  `Router.route()` itself and lets the `NotFound` escape, so `ErrorPagesTest` asserts the exception and
  renders the template with the EntityManager unbound rather than a status code.
- `forum.Users.profile()` / `update()` and `forum.Messages.inbox()` / `outbox()` answer
  `Application.backToPreviousPage()` when `connectedUser()` is null. `Profile.checkAccess()` puts
  `anonymous.email` in the session for a `@Check(VIEWER)` GET, and `Security.check("")` only asks whether
  `User.findByEmail()` answers something, so those `@Check(LOGGED)` actions ran with the anonymous row as
  their user while `Security.connected()` filtered that address out — `profile.html` and `inbox.html` then
  dereferenced null and answered 500. The helper redirects to the referer when it is on the same host and
  is not the current path (a form posts to the page it is on, so `/profile` as its own referer would loop),
  and otherwise calls `Application.index()`. Play's `ControllersEnhancer` puts an auto-redirect in front of
  every public static void action, so an action called from another action never runs: it becomes
  `redirect("Application.index")`, a 302 to `/` through the `GET /` route — the same thing
  `Profile.Security.onDisconnected()` already does.
- `app/views/main.html` linked Messages to `href="#"` and never linked the outbox. `conf/routes` declares
  `GET /messages/inbox` and `GET /messages/outbox` above the catch-all, and the dropdown links both in the
  badge branch and the plain one. The legacy `/forum.messages/inbox` (the catch-all spelling of the same
  action) still answers, so it stays asserted.

## Deliberately uncovered

- `OpenttdAdminHandler.startup()` of a scenario the enum covers, and `OpenttdClientHandler.startup(port)`: both
  open a socket (admin ports 3977..4377 per scenario, client port from the caller) and leave a running object
  in the singleton for the suites that follow. Two consequences of a failed connect were observed with nothing
  listening and cannot be asserted hermetically — `com.openttd.admin.OpenttdAdmin.isConnected()` answers true
  as soon as its network thread is alive, so a robot of a server that is not listening is reported running, and
  `com.openttd.network.admin.NetworkAdmin.shutdown()` dereferences a sender the failed connect never created.
  The doc comments of `OpenttdAdminHandlerTest` and `OpenttdClientHandlerTest` state both instead of asserting
  them.
- `GET /openttd/start/{scenarioId}` is only ever asked anonymously (redirect to `/login`) and as a
  non-administrator (403), so the controller never reaches `startup()` and `OpenttdPageTest` can assert that
  every scenario reports "Server is down".
- `OpenttdService.identifyUser()` dereferences `user.group.admin`; a `User` row without a group would raise
  inside the robot's login path, but `group` is `@Required` and every fixture carries one, so no case pins it.
- `/openttd.scores/exportCSV` (the action name spelled `exportCSV`) is not asserted: the
  `ActionNotFoundException` escapes from `play.Invoker$Invocation.init()`, so the JPA transaction the
  invocation started is never unwound and each such request leaks a Hikari connection, which would starve the
  pool for the suites FirePhoque runs afterwards. `ScoresExportCsvTest` covers the two paths that do reach an
  action instead.
- `controllers.Html5` demos are asserted as pages, not as behaviour.

## Expected noise in a test run

None of these is a failure, and none should be "fixed":

- The captcha NPE stack on **stderr** (`jj-simplecaptcha.jar` ships no image resources); log4j2 records nothing.
- `ActionNotFoundException` for `/@routes`, `/@mem`, `/@info`, `/@help` (the catch-all route matches them).
- `ERROR ~ userId null`, `ERROR ~ user null`, `ERROR ~ no robot connected`,
  `ERROR ~ Try to read scenarioId on null object models.openttd.RobotConfiguration` — the branches the
  openttd suites drive on purpose.
- Pairs of `ERROR ~ Connection refused` while `OpenttdService.endGame()` reaches the real
  `OpenttdAdminHandler.startup()`, which opens an admin socket with no server behind it. The suite removes the
  robot it left in the map, so nothing leaks into the suites that run after it.
- `Cannot delete or update a parent row` from the refused-delete cases.

## How the assertions were obtained

Play compiles test sources with Eclipse JDT at compliance 11 (`java.source` default), not javac, and JDT is
stricter about generic inference: `GenericModel.findById()` / `findAll()` are inferred as `JPABase`, so a
for-each over `PlayedGameScore.findAll()` or a field access on `PlayedGame.findById(1L)` fails to compile
where javac accepts it. Test sources also never go through `play check`, and a bad test file makes
`GET /@tests.list` answer 500 with `Compilation error (In /test/... around line N)` in
`test-result/application.log` — the whole runner goes down, not just the suite. So a scratch probe was
compiled and run before transcription:

```
bash /tmp/opencode/check-tests.sh                 # JDT batch Main, -source 11 -target 11, jars listed
bash /tmp/opencode/run-suites.sh OpenttdProbeTest # play test, one suite, reads the runner header
```

The dumps are ground truth and live outside the repo, under `/tmp/opencode/probe/models/` and
`/tmp/opencode/probe/openttd/`. The probe classes (`test/ProbeModelsTest.java`, `test/OpenttdProbeTest.java`)
were deleted once their values were transcribed into real assertions.

## Verify

```
. /home/eric/www/tools/env.sh && cd /home/eric/www/strategyboard
sudo tools/start-mariadb.sh                # once, if MariaDB is not up
play check && play precompile && play auto-test
ls test-result/result.passed
grep -L 'failures="0"' test-result/TEST-*.xml   # prints nothing when every suite is clean
grep -L 'errors="0"'   test-result/TEST-*.xml   # prints nothing when every suite is clean
grep -l 'failures="0"' test-result/TEST-*.xml | wc -l   # 56
```

The `<testsuite>` attributes are ordered `failures="0" time=... errors="0" skipped="0" tests=... name=...`, so
an assertion like `grep 'failures="0" errors="0"'` matches nothing.

The three behaviours the fix changes are also checked over real HTTP, because `play.test.FunctionalTest`
dispatches through `ActionInvoker` and never through `PlayHandler`: the 404 of an unmatched url, and the
redirects of an anonymous `/profile` and `/messages/inbox`. `jpa.ddl` is `create`, so a server start empties
the database and `conf/initial-data.yml` is a commented-out stub: the dev rows have to be replayed after the
first request created the schema. The dump and its replay script lived under `/tmp/opencode/`
(`www-dev-data-clean.sql` and `restore-dev-data.py`); they are not there now, only
`/tmp/opencode/prod-schema-check.sql` is. Re-take the dump with `mariadb-dump --complete-insert
--no-create-info` from the demo state you want, naming every column: `Forum` is
`id, description, displayOrder, locked, name, category_id`, and `Topic` is
`id, hidden, locked, views, forum_id`.

```
python3.11 /tmp/opencode/restore-dev-data.py        # after play run / play test created the schema
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9000/nope                  # 404
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9000/no/controller/here    # 404
curl -s -c /tmp/jar.txt http://localhost:9000/forums > /dev/null                     # session = anonymous
curl -s -b /tmp/jar.txt -D - -o /dev/null http://localhost:9000/profile              # 302, Location /
curl -s -b /tmp/jar.txt -D - -o /dev/null http://localhost:9000/messages/inbox       # 302, Location /
curl -s -b /tmp/jar.txt -D - -o /dev/null http://localhost:9000/messages/outbox      # 302, Location /
curl -s -b /tmp/jar.txt -D - -o /dev/null http://localhost:9000/forum.messages/inbox # 302, Location /
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9000/profile               # 302, Location /login
curl -s -c /tmp/admin.txt -d 'username=admin@domain.com&password=secret' http://localhost:9000/login
curl -s -c /tmp/modo.txt  -d 'username=modo@domain.com&password=secret'  http://localhost:9000/login
curl -s -c /tmp/bob.txt   -d 'username=bob@domain.com&password=secret'   http://localhost:9000/login
curl -s -b /tmp/modo.txt  -o /dev/null -w '%{http_code}\n' http://localhost:9000/forums/1              # 200
curl -s -b /tmp/admin.txt -o /dev/null -w '%{http_code}\n' http://localhost:9000/forums/1              # 200
curl -s -b /tmp/bob.txt   -o /dev/null -w '%{http_code}\n' http://localhost:9000/forums/1              # 200
curl -s -b /tmp/jar.txt   -o /dev/null -w '%{http_code}\n' http://localhost:9000/forums/1              # 200
curl -s -b /tmp/bob.txt   -o /dev/null -w '%{http_code}\n' http://localhost:9000/forums/1/topics/3    # 403
curl -s -b /tmp/jar.txt   -o /dev/null -w '%{http_code}\n' http://localhost:9000/forums/1/topics/3    # 403
curl -s -b /tmp/modo.txt  -o /dev/null -w '%{http_code}\n' http://localhost:9000/forums/1/topics/3    # 200
curl -s -b /tmp/admin.txt -o /dev/null -w '%{http_code}\n' http://localhost:9000/forums/1/topics/3    # 200
```

`-X POST` matters on the topic routes (`/forums/{id}/topics/{id}/hide`, `.../delete`, `.../move/{to}`): they are
POST only, so a GET falls through to the catch-all and answers 404 without touching the row. The dev passwords
are the MD5 of `secret` (`test/data.yml` writes the same hash), which is why these logins work. A forum is open
to every role that its `Permission` row allows, so `/forums/1` answers 200 for Bob and for the anonymous session
too; what is gated is the hidden topic: `Permission.moderateMain` = 4 lets the moderator and the administrator
read `/forums/1/topics/3`, `Permission.postMain` = 3 and `Permission.viewAnonymous` = 1 do not. The header of
`/forums` follows the same boundary: `5 posts in 4 topics` below moderate, `6 posts in 5 topics` at moderate,
and the hidden topic never appears in the home page news for any role.
The navbar renders `href="/messages/inbox"` and `href="/messages/outbox"` for a logged-in session, and only Login
and Signup for an anonymous one, whose `connectedUser` is null. `/nope` renders `errors/404.html` through
`main.html` — subtitle "The forum, topic, or post you search does not exist !" and the link back to `/` — with no
EntityManager bound, which is the case that used to answer 500.
