import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Pattern;

import org.junit.*;

import play.cache.Cache;
import play.db.jpa.JPA;
import play.mvc.Http.Response;
import play.templates.JavaExtensions;
import play.test.Fixtures;

import com.openttd.network.admin.GameInfo;
import com.openttd.robot.ExternalServices;
import com.openttd.util.Convert;

import models.forum.User;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.Scenario;
import models.openttd.RobotConfiguration;
import openttd.OpenttdAdminHandler;

/**
 * Coverage of app/views/openttd/Openttd/index.html: every construct of the template and of
 * the tags it renders (ingameLogin, miniLeaderBoard, secure.check, showOpenTTDStatus,
 * showRecentGames, showBestGames), in both directions of each conditional.
 *
 * Branches covered here:
 *   - secure.check 'moderator': shown for the moderator group (Permission.moderate on the
 *     default category, data.yml's moderateMain) and for an admin group, hidden for the
 *     users group (accessLevel post < moderate) and for an anonymous visitor.
 *   - ingameLogin: uuid line when connectedUser is truthy, login link when it is null
 *     (index.html calls the tag unconditionally, unlike Application/index.html).
 *   - showOpenTTDStatus: the detail rows when OpenttdServer.getStatus() returns a server,
 *     "Server is down :(" when it returns null.
 *   - showRecentGames / showBestGames: rows with a winner and rows without one, and the
 *     empty list path.
 *   - miniLeaderBoard: rows, and the empty list path.
 *
 * How the status branch is reached without a live OpenTTD server: OpenttdServer.getStatus()
 * reads ExternalServices.getInstance().getExternalGameService().getGame(scenarioId), and the
 * service wired by OpenttdAdminHandler's constructor is openttd.OpenttdService, whose getGame()
 * returns whatever exposeGame() stored. So the test calls getInstance() first (that wires the
 * service), then exposeGame() for the scenarios that must look up; scenarios left unexposed
 * stay down.
 *
 * Openttd.index() caches the leaderboard and the game lists for 5s, so every request goes
 * through renderPage(), which clears the cache first: otherwise a list cached by an earlier
 * test would be served to the next one.
 *
 * Dates: end.since() is relative, so played games are seeded with dates a whole number of days
 * in the past and assertions compare against JavaExtensions.since(end), the same call the
 * template makes. conf/messages defines no since.* keys, so the message lookup falls back to
 * the raw arguments; computing the expected string through the same API keeps the assertion
 * valid whatever bundle is loaded. presenceRatio is never 0: getWinner and getPlayerScores
 * divide achivement by it, and MariaDB runs with ERROR_FOR_DIVISION_BY_ZERO.
 *
 * start() is not exercised as a moderator: OpenttdAdminHandler.startup() opens real admin
 * sockets on ports 3977..4377. index.html's coverage needs only the links it renders, and
 * stop() is safe because isRunning() is false for every scenario in this JVM.
 */
public class OpenttdPageTest extends BaseFunctionalTest {

	private static final long HIGH_SPEED = RobotConfiguration.HIGH_SPEED.scenarioId;
	private static final long TOYLAND = RobotConfiguration.TOYLAND.scenarioId;
	private static final long ARTIC = RobotConfiguration.ARTIC.scenarioId;
	private static final long DESERTIC = RobotConfiguration.DESERTIC.scenarioId;
	private static final long STEAM = RobotConfiguration.STEAM.scenarioId;

	@Before
	public void loadOpenttdFixtures() throws Throwable {
		// data.yml is already loaded by BaseFunctionalTest.loadFixtures(); this adds the five
		// scenarios with ids 1..5.
		JPA.withTransaction(JPA.DEFAULT, false, () -> {
			Fixtures.loadModels("openttd-data.yml");
			return null;
		});
		// Constructs the OpenttdAdminHandler singleton, which wires ExternalServices to an
		// OpenttdService. Must happen before exposing game info, or getGame() would read a
		// service that the singleton replaces later.
		OpenttdAdminHandler.getInstance();
	}

	@After
	public void restoreServerStatuses() throws Throwable {
		ExternalServices.ExternalGameService service = ExternalServices.getInstance().getExternalGameService();
		if (service != null) {
			for (RobotConfiguration configuration : RobotConfiguration.values()) {
				service.exposeGame(configuration.scenarioId, null);
			}
		}
		Cache.clear();
	}

	// ~~~~~~~~~~~~ anonymous: connectedUser null, moderation hidden, every server down

	@Test
	public void anonymousPageHasTitleActiveNavLoginLinkAndFiveDownPanels() throws Throwable {
		Response response = renderPage();
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
		String body = getContent(response);

		// #{extends 'main.html' /} and #{set title: 'OpenTTD server' /}
		assertContains("<title>Strategyboard - OpenTTD server</title>", body);
		assertContains("<h1>OpenTTD server</h1>", body);
		// main.html nav: OpenTTD active, News and Scores not
		assertContains("<li class=\"active\"><a href=\"/openttd\">OpenTTD</a></li>", body);
		assertNotContains("<li class=\"active\"><a href=\"/\">News</a></li>", body);
		assertContains(">Scores</a>", body);
		// main.html #{else} branch, and #{secure.check 'administrator')} denying an anonymous
		assertContains(">Login</a>", body);
		assertContains(">Signup</a>", body);
		assertNotContains("href=\"/admin\"", body);
		// main.html flash branches, neither set on this request
		assertNotContains("alert alert-success", body);
		assertNotContains("alert alert-danger", body);

		// five showOpenTTDStatus calls with a null arg -> #{else}
		assertEquals(5, count("Server is down :(", body));
		assertContains("OpenTTD artic server status", body);
		assertContains("OpenTTD desertic server status", body);
		assertContains("OpenTTD high speed server status", body);
		assertContains("OpenTTD steam server status", body);
		assertContains("OpenTTD toyland server status", body);
	}

	@Test
	public void anonymousHasEmptyLeaderboardRecentGamesAndBestGames() throws Throwable {
		String body = getContent(renderPage());
		String left = leftColumn(body);

		// ingameLogin #{else}: a login link inside the panel
		assertContains("In game login", left);
		assertMatches("<a href=\"/login\">Login</a>", left);
		assertNotContains("!login", left);

		// miniLeaderBoard #{list} over an empty list: the panel and its heading, no rows
		assertContains("This month scores", left);
		assertContains("<a href=\"/scores\">This month scores</a>", left);
		assertNotContains("<tr>", leaderboardTable(left));
		assertNotContains("/users/", leaderboardTable(left));

		// secure.check 'moderator' false path: nothing is rendered where the panel would be
		assertNotContains("Moderation options", left);
		assertNotContains("/openttd/start/", left);
		assertNotContains("/openttd/stop/", left);

		// showRecentGames and showBestGames over empty lists: headers only
		String recent = recentGamesTable(body);
		assertContains("<th>Since</th>", recent);
		assertContains("<th>Scenario</th>", recent);
		assertContains("<th>Won by</th>", recent);
		assertNotContains("<tr>", tbodyOf(recent));
		for (String alt : new String[] { "artic scenario", "desertic scenario", "high speed scenario", "steam scenario", "toyland scenario" }) {
			String panel = bestGamesTable(body, alt);
			assertContains("<th>Player</th>", panel);
			assertContains("<th>Score</th>", panel);
			assertContains("<th>Since</th>", panel);
			assertNotContains("<tr>", tbodyOf(panel));
			assertNotContains("Anonymous", panel);
		}
	}

	@Test
	public void anonymousIsSentToTheLoginPageInsteadOfStartingOrStoppingAServer() throws Throwable {
		// start()/stop() are not @Check(VIEWER), so Profile.checkAccess() does not put the
		// anonymous email in the session and Secure.checkAccess() redirects to its login page
		// instead of reaching the action.
		Response response = GET("/openttd/start/3");
		assertStatus(302, response);
		assertHeaderEquals("Location", "/login", response);
		String loginPage = getContent(GET("/openttd/start/3", true));
		assertContains("<form action=\"/login\"", loginPage);
		assertContains("name=\"username\"", loginPage);
		assertNotContains("OpenTTD server", loginPage);
		assertNotContains("Moderation options", loginPage);

		response = GET("/openttd/stop/3");
		assertStatus(302, response);
		assertHeaderEquals("Location", "/login", response);
	}

	// ~~~~~~~~~~~~ logged in with the users group: connectedUser truthy, moderation hidden

	@Test
	public void userSeesInGameLoginUuidWelcomeAndNoModerationPanel() throws Throwable {
		login("bob@domain.com", "secret");
		Response response = renderPage();
		assertIsOk(response);
		String body = getContent(response);
		String left = leftColumn(body);

		// ingameLogin #{if _arg} true path
		assertContains("In game login", left);
		assertContains(">In game login</h3>", left);
		final String uuid = uuidOf("bob@domain.com");
		assertTrue("no in game uuid after login", uuid != null && uuid.length() > 0);
		assertContains("!login " + uuid, left);
		assertNotMatches("<a href=\"/login\">Login</a>", left);

		// main.html #{if connectedUser} true path, newMessagesCount == 0 false path
		assertContains("Bob <b class=\"caret\"></b>", body);
		assertNotContains("badge pull-right", body);
		assertContains(">My profile</a>", body);
		assertContains(">Logout</a>", body);
		assertNotContains(">Signup</a>", body);
		// Profile.Security.authenticate sets flash.success -> main.html alert-success branch
		assertContains("alert alert-success", body);
		assertContains("Welcome back Bob !", body);

		// secure.check 'moderator' false path for the users group (accessLevel 3 < moderate 4)
		assertNotContains("Moderation options", left);
		assertNotContains("/openttd/start/", left);
	}

	@Test
	public void userCannotStartOrStopServers() throws Throwable {
		login("bob@domain.com", "secret");
		assertStatus(403, GET("/openttd/start/2"));
		assertStatus(403, GET("/openttd/stop/2"));
	}

	@Test
	public void leaderboardShowsOnlyScoresOfTheLastMonth() throws Throwable {
		// Integer ratios keep cast(sum(achivement / presenceRatio) as int) exact.
		seedScores("bob@domain.com", new double[][] { { 120, 10 }, { 30, 5 } }, 5); // 12 + 6
		seedScores("modo@domain.com", new double[][] { { 100, 20 } }, 5); // 5
		seedScores("admin@domain.com", new double[][] { { 500, 10 } }, 40); // older than a month

		String table = leaderboardTable(getContent(renderPage()));
		assertMatches("Bob</a></td>\\s*<td align=\"right\">18</td>", table);
		assertMatches("Moderator</a></td>\\s*<td align=\"right\">5</td>", table);
		assertNotContains("Admin</a>", table);
		// ordering: 18 before 5
		assertTrue("leaderboard must order by points", table.indexOf("Bob") < table.indexOf("Moderator"));
	}

	// ~~~~~~~~~~~~ games lists

	@Test
	public void recentGamesAreListedNewestFirstWithAndWithoutWinner() throws Throwable {
		seedPlayedGame(ARTIC, 4, 2, new String[] { "bob@domain.com" }, new double[] { 120 }, new double[] { 10 });
		seedPlayedGame(DESERTIC, 3, 1); // no score at all -> no winner
		seedPlayedGame(HIGH_SPEED, 20, 12, new String[] { "modo@domain.com" }, new double[] { 100 }, new double[] { 20 });

		String body = getContent(renderPage());
		String table = recentGamesTable(body);
		String tbody = tbodyOf(table);

		// PlayedGame.getLastPlayedGames() orders by end desc: desertic (1 day) first
		int desertic = tbody.indexOf("Desertic Scenario");
		int artic = tbody.indexOf("Artic Scenario");
		int highSpeed = tbody.indexOf("HighSpeed Scenario");
		assertTrue("desertic game not found", desertic >= 0);
		assertTrue("artic game must come after the newer desertic one", desertic < artic);
		assertTrue("artic game must come before the older high speed one", artic < highSpeed);

		// scenario column: train image + name
		assertContains("<img src=\"/images/openttd/desertic_train.gif\" alt=\"scenarioTrain\"", tbody);
		assertContains("class=\"train_icon\"", tbody);

		// #{if playedGame.winner} true path
		assertMatches("<a href=\"/users/\\d+\">Bob</a> with 12 points", tbody);
		// #{else} path: the desertic game has no score row at all
		assertMatches("Anonymous\\s*</td>", tbody);

		// #{list} renders playedGame.end.since() for every row
		assertContains(since(daysAgo(1)), tbody);
		assertContains(since(daysAgo(2)), tbody);
		assertContains(since(daysAgo(12)), tbody);
		assertEquals(3, count("<tr>", tbody));
	}

	@Test
	public void bestGamesShowWinnerPlayerLinkScoreAndEmptyRows() throws Throwable {
		seedPlayedGame(ARTIC, 8, 2, new String[] { "bob@domain.com" }, new double[] { 120 }, new double[] { 10 });
		// A second Artic game, ended later, for another user: getBestPlayedGamesForScenarioId
		// groups by user and orders by max(achivement), so Admin (250) comes before Bob (120).
		seedPlayedGame(ARTIC, 5, 3, new String[] { "admin@domain.com" }, new double[] { 250 }, new double[] { 10 });
		seedPlayedGame(DESERTIC, 6, 2, new String[] { "bob@domain.com" }, new double[] { 0 }, new double[] { 10 });
		seedPlayedGame(STEAM, 4, 1); // no score -> the best games list stays empty for Steam

		String body = getContent(renderPage());

		String artic = tbodyOf(bestGamesTable(body, "artic scenario"));
		// #{if playedGame[0].winner}: player link and (int) max(achivement)
		assertMatches("<td>\\s*<a href=\"/users/\\d+\">Admin</a>\\s*</td>\\s*<td>\\s*250</td>", artic);
		assertMatches("<td>\\s*<a href=\"/users/\\d+\">Bob</a>\\s*</td>\\s*<td>\\s*120</td>", artic);
		// ordering by max achivement desc
		assertTrue("best games must order by score", artic.indexOf("Admin") < artic.indexOf("Bob"));
		assertContains(since(daysAgo(3)), artic);
		assertEquals(2, count("<tr>", artic));

		// showBestGames' #{else} cannot be reached from this controller: every row of
		// getBestPlayedGamesForScenarioId comes from a PlayedGameScore, so getWinner() always
		// finds a winner for the playedGame it returns. Assert the branch stays unused, and
		// cover the zero score path: achivement 0 renders "0" in the score cell.
		String desertic = tbodyOf(bestGamesTable(body, "desertic scenario"));
		assertMatches("<td>\\s*<a href=\"/users/\\d+\">Bob</a>\\s*</td>\\s*<td>\\s*0</td>", desertic);
		assertNotContains("Anonymous", desertic);

		// Steam and Toyland have no score row -> empty tbody, headers still rendered
		assertNotContains("<tr>", tbodyOf(bestGamesTable(body, "steam scenario")));
		assertNotContains("<tr>", tbodyOf(bestGamesTable(body, "toyland scenario")));
		assertNotContains("Anonymous", tbodyOf(bestGamesTable(body, "steam scenario")));
	}

	@Test
	public void recentGameWithoutAnyScoreIsReportedAsAnonymous() throws Throwable {
		seedPlayedGame(TOYLAND, 3, 1);
		String tbody = tbodyOf(recentGamesTable(getContent(renderPage())));
		assertMatches("Anonymous\\s*</td>", tbody);
		assertContains("<img src=\"/images/openttd/toyland_train.gif\" alt=\"scenarioTrain\"", tbody);
		assertContains("Toyland Scenario", tbody);
	}

	// ~~~~~~~~~~~~ moderator: secure.check 'moderator' true path

	@Test
	public void moderatorSeesAllFiveStartStopLinkGroups() throws Throwable {
		login("modo@domain.com", "secret");
		String left = leftColumn(getContent(renderPage()));

		assertContains("Moderation options", left);
		assertContains("<div class=\"panel panel-danger\">", left);
		assertContains("<h3 class=\"panel-title\">Moderation options</h3>", left);
		assertContains("<div class=\"panel-body\">", left);
		for (String label : new String[] { "Artic Server", "Desertic Server", "High Speed Server", "Steam Server", "Toyland Server" }) {
			assertContains("<p>" + label + "</p>", left);
		}
		// index.html maps Artic->3, Desertic->4, High Speed->1, Steam->5, Toyland->2
		for (long id : new long[] { ARTIC, DESERTIC, HIGH_SPEED, STEAM, TOYLAND }) {
			assertContains("<a class=\"btn btn-default\" href=\"/openttd/start/" + id + "\">Start</a>", left);
			assertContains("<a class=\"btn btn-default\" href=\"/openttd/stop/" + id + "\">Stop</a>", left);
		}
		assertEquals(5, count("/openttd/start/", left));
		assertEquals(5, count("/openttd/stop/", left));
		assertEquals(5, count("<div class=\"btn-group\">", left));
		// the moderator is logged in, so ingameLogin takes its #{if} path
		assertContains("!login " + uuidOf("modo@domain.com"), left);
	}

	@Test
	public void pageStructureMatchesTheMarkupOfTheTemplate() throws Throwable {
		login("modo@domain.com", "secret");
		String body = getContent(renderPage());

		// the outer row and the row nested inside the wide column
		assertEquals(2, count("<div class=\"row\">", body));
		assertEquals(1, count("<div class=\"col-xs-12 col-sm-4 col-md-2\">", body));
		assertEquals(1, count("<div class=\"col-xs-12 col-sm-8 col-md-10\">", body));
		assertContains("<!--/span-->", body);
		// one wrapper per server status panel, one per games panel
		assertEquals(5, count("<div class=\"col-md-4 col-sm-6 col-xs-12\">", body));
		assertEquals(6, count("<div class=\"col-md-6 col-sm-12\">", body));
		assertEquals(5, count("<h3 class=\"panel-title\">OpenTTD ", body));
		assertEquals(1, count("<h3 class=\"panel-title\">Recent games ...</h3>", body));
		assertEquals(5, count("<h3 class=\"panel-title\">Best games on ", body));
		// the static scenario icons of the five best games headings
		for (String image : new String[] { "artic", "desertic", "high_speed", "steam", "toyland" }) {
			assertContains("<img src=\"/images/openttd/" + image + "_train.gif\" alt=\"" + image.replace("_", " ")
					+ " scenario\" class=\"train_icon\">", body);
		}
	}

	@Test
	public void moderatorStopOfAnUnrunningServerRedirectsToThePageItRenders() throws Throwable {
		login("modo@domain.com", "secret");
		// Openttd.stop() ends with index(), which Play turns into a framework redirect to the
		// nested action's URL (same shape as Topics.lock() -> Forums.show()). isRunning(3) is
		// false, so nothing is shut down and the followed request renders index.html.
		Response response = GET("/openttd/stop/3");
		assertStatus(302, response);
		assertHeaderEquals("Location", "/openttd", response);

		Response followed = GET("/openttd/stop/3", true);
		assertIsOk(followed);
		String body = getContent(followed);
		assertContains("<title>Strategyboard - OpenTTD server</title>", body);
		assertContains("Moderation options", leftColumn(body));
		assertContains("OpenTTD artic server status", body);
	}

	@Test
	public void moderatorSeesFourUpServersAndOneDownPanel() throws Throwable {
		login("modo@domain.com", "secret");
		exposeUp(RobotConfiguration.ARTIC, "Artic server", "r7000", 512, 1024, 7, 4, 1000, 3650);
		exposeUp(RobotConfiguration.DESERTIC, "Desertic server", "r7001", 1024, 512, 3, 2, 1000, 3651);
		exposeUp(RobotConfiguration.HIGH_SPEED, "High Speed server", "r7002", 800, 600, 9, 8, 1001, 3652);
		exposeUp(RobotConfiguration.STEAM, "Steam server", "r7003", 256, 256, 1, 6, 1002, 3653);
		// Toyland is left unexposed -> getStatus() returns null

		String body = getContent(renderPage());
		assertEquals(1, count("Server is down :(", body));

		String artic = statusPanel(body, "OpenTTD artic server status");
		assertContains("<tr><td align=\"center\">Artic server</td></tr>", artic);
		assertContains("<img src=\"/images/openttd/artic_on.gif\" alt=\"mapIcon\">", artic);
		assertContains("512x1024", artic);
		assertContains("Started: " + formatDay(1000) + ", current: " + formatDay(3650), artic);
		assertContains("7&nbsp;Companies, 4&nbsp;Clients", artic);
		assertContains("Reach the best company performance in 10 years to win the game.", artic);
		assertContains("<a href=\"http://www.openttd.org/en/download-stable\">OpenTTD r7000</a>", artic);

		String toyland = statusPanel(body, "OpenTTD toyland server status");
		assertContains("<tr><td>Server is down :(</td></tr>", toyland);
		assertNotContains("download-stable", toyland);
		assertNotContains("Companies", toyland);

		// the other three take the #{if} path with their own values
		assertContains("Desertic server", statusPanel(body, "OpenTTD desertic server status"));
		assertContains("1024x512", statusPanel(body, "OpenTTD desertic server status"));
		assertContains("3&nbsp;Companies, 2&nbsp;Clients", statusPanel(body, "OpenTTD desertic server status"));
		assertContains("High Speed server", statusPanel(body, "OpenTTD high speed server status"));
		assertContains("/images/openttd/highSpeed_on.gif", statusPanel(body, "OpenTTD high speed server status"));
		assertContains("9&nbsp;Companies, 8&nbsp;Clients", statusPanel(body, "OpenTTD high speed server status"));
		assertContains("Steam server", statusPanel(body, "OpenTTD steam server status"));
		assertContains("OpenTTD r7003", statusPanel(body, "OpenTTD steam server status"));
		// Scenario.getImage() per id: Toyland is down, so its image never appears
		assertNotContains("toyland_on.gif", body);
		assertEquals(4, count("Reach the best company performance", body));
	}

	// ~~~~~~~~~~~~ administrator: Secure.Security.check() short-circuits on group.admin

	@Test
	public void adminSeesModerationPanelWithoutMessagesBadge() throws Throwable {
		login("admin@domain.com", "secret");
		Response response = renderPage();
		assertIsOk(response);
		String body = getContent(response);
		String left = leftColumn(body);

		assertContains("Moderation options", left);
		assertEquals(5, count("/openttd/start/", left));
		assertContains("!login " + uuidOf("admin@domain.com"), left);
		// main.html secure.check('administrator') true branch
		assertContains("href=\"/admin/\"", body);
		assertContains(">Administration</a>", body);
		assertNotContains("badge pull-right", body);
		assertContains("alert alert-success", body);
		assertNotContains("alert alert-danger", body);
	}

	// ~~~~~~~~~~~~ request helpers

	/** GET /openttd with the controller's 5s caches cleared, so seeded data is always read. */
	private static Response renderPage() {
		Cache.clear();
		return GET("/openttd");
	}

	/** Puts a GameInfo in the service OpenttdAdminHandler wired, so getStatus() returns a server. */
	private static void exposeUp(final RobotConfiguration configuration, final String name, final String revision,
			final int width, final int height, final int companies, final int clients, final long startDay, final long currentDay) {
		GameInfo gameInfo = new GameInfo();
		gameInfo.setServerName(name);
		gameInfo.setServerRevision(revision);
		gameInfo.setMapWidth(width);
		gameInfo.setMapHeight(height);
		gameInfo.setStartDate(startDay);
		gameInfo.setCurrentDate(currentDay);
		gameInfo.companiesCount = companies;
		gameInfo.clientsCount = clients;
		ExternalServices.getInstance().getExternalGameService().exposeGame(configuration.scenarioId, gameInfo);
	}

	// ~~~~~~~~~~~~ fixtures helpers

	/** One PlayedGame of the given scenario, ending daysAgo days ago, with optional scores. */
	private void seedPlayedGame(final long scenarioId, final int beginDaysAgo, final int endDaysAgo,
			final String[] emails, final double[] achievements, final double[] presenceRatios) throws Throwable {
		JPA.withTransaction(JPA.DEFAULT, false, () -> {
			PlayedGame playedGame = new PlayedGame();
			playedGame.scenario = Scenario.findById(scenarioId);
			assertTrue("scenario " + scenarioId + " missing", playedGame.scenario != null);
			playedGame.begin = daysAgo(beginDaysAgo);
			playedGame.end = daysAgo(endDaysAgo);
			playedGame.create();
			for (int i = 0; i < emails.length; i++) {
				PlayedGameScore score = new PlayedGameScore();
				score.playedGame = playedGame;
				score.user = User.findByEmail(emails[i]);
				score.achivement = achievements[i];
				score.presenceRatio = presenceRatios[i];
				score.create();
			}
			return null;
		});
	}

	private void seedPlayedGame(final long scenarioId, final int beginDaysAgo, final int endDaysAgo) throws Throwable {
		seedPlayedGame(scenarioId, beginDaysAgo, endDaysAgo, new String[0], new double[0], new double[0]);
	}

	/** PlayedGameScores ending daysAgo days ago, one per [achivement, presenceRatio] pair. */
	private void seedScores(final String email, final double[][] scores, final int daysAgo) throws Throwable {
		JPA.withTransaction(JPA.DEFAULT, false, () -> {
			User user = User.findByEmail(email);
			for (int i = 0; i < scores.length; i++) {
				PlayedGame playedGame = new PlayedGame();
				playedGame.scenario = Scenario.findById(HIGH_SPEED);
				playedGame.begin = daysAgo(daysAgo + 10);
				playedGame.end = daysAgo(daysAgo);
				playedGame.create();
				PlayedGameScore score = new PlayedGameScore();
				score.playedGame = playedGame;
				score.user = user;
				score.achivement = scores[i][0];
				score.presenceRatio = scores[i][1];
				score.create();
			}
			return null;
		});
	}

	private static Date daysAgo(final int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_MONTH, -days);
		return calendar.getTime();
	}

	/** The same string the templates get from ${playedGame.end.since()}. */
	private static String since(final Date date) {
		return JavaExtensions.since(date);
	}

	/** The same date string OpenttdServer builds from a GameInfo day number. */
	private static String formatDay(final long day) {
		return new SimpleDateFormat("dd/MM/yyyy").format(Convert.dayToCalendar(day).getTime());
	}

	private static String uuidOf(final String email) throws Throwable {
		return JPA.withTransaction(JPA.DEFAULT, false, () -> User.findByEmail(email).uuid);
	}

	// ~~~~~~~~~~~~ response helpers

	/** The narrow left column of index.html, up to the status/game columns. */
	private static String leftColumn(String body) {
		int start = body.indexOf("<div class=\"col-xs-12 col-sm-4 col-md-2\">");
		assertTrue("left column not found", start >= 0);
		int end = body.indexOf("<div class=\"col-xs-12 col-sm-8 col-md-10\">", start);
		assertTrue("game columns not found", end >= 0);
		return body.substring(start, end);
	}

	/** The miniLeaderBoard table, from its heading link to its closing tag. */
	private static String leaderboardTable(String body) {
		int heading = body.indexOf("This month scores");
		assertTrue("leaderboard panel not found", heading >= 0);
		int start = body.indexOf("<table", heading);
		assertTrue("leaderboard table not found", start >= 0);
		return body.substring(start, body.indexOf("</table>", start));
	}

	/** The showRecentGames table, following the "Recent games ..." heading. */
	private static String recentGamesTable(String body) {
		int heading = body.indexOf("Recent games ...");
		assertTrue("recent games panel not found", heading >= 0);
		return tableAfter(body, heading);
	}

	/** The showBestGames table following the heading carrying the given scenario img alt. */
	private static String bestGamesTable(String body, String scenarioAlt) {
		int heading = body.indexOf("alt=\"" + scenarioAlt + "\"");
		assertTrue("best games panel for '" + scenarioAlt + "' not found", heading >= 0);
		return tableAfter(body, heading);
	}

	private static String tableAfter(String body, int from) {
		int start = body.indexOf("<table", from);
		assertTrue("table not found after offset " + from, start >= 0);
		return body.substring(start, body.indexOf("</table>", start));
	}

	/** The tbody of a table, i.e. its rows without the header row. */
	private static String tbodyOf(String table) {
		int start = table.indexOf("<tbody>");
		assertTrue("tbody not found", start >= 0);
		return table.substring(start, table.indexOf("</tbody>", start));
	}

	/** The showOpenTTDStatus table of the panel carrying the given heading. */
	private static String statusPanel(String body, String heading) {
		int start = body.indexOf(heading);
		assertTrue("status panel '" + heading + "' not found", start >= 0);
		int table = body.indexOf("<table", start);
		assertTrue("status table not found for '" + heading + "'", table >= 0);
		return body.substring(table, body.indexOf("</table>", table));
	}

	private static int count(String needle, String body) {
		int count = 0;
		for (int i = body.indexOf(needle); i >= 0; i = body.indexOf(needle, i + needle.length())) {
			count++;
		}
		return count;
	}

	private static void assertContains(String needle, String body) {
		assertTrue("expected to find '" + needle + "'", body.contains(needle));
	}

	private static void assertNotContains(String needle, String body) {
		assertFalse("expected not to find '" + needle + "'", body.contains(needle));
	}

	private static void assertMatches(String regex, String text) {
		assertTrue("expected to match '" + regex + "'", Pattern.compile(regex).matcher(text).find());
	}

	private static void assertNotMatches(String regex, String text) {
		assertFalse("expected not to match '" + regex + "'", Pattern.compile(regex).matcher(text).find());
	}
}
