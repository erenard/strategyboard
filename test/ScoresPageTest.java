import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Pattern;

import org.junit.*;

import play.cache.Cache;
import play.db.jpa.JPA;
import play.mvc.Http.Response;
import play.test.Fixtures;

import controllers.Application;
import models.forum.User;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;

/**
 * Coverage of app/views/openttd/Scores/show.html: every construct of the template and of the
 * tags it calls (pluralize, pagination, secure.check), in both directions of each conditional.
 *
 * Branches covered here:
 *   - #{set title} / #{extends 'main.html'}: the page header, and main.html's nav item for
 *     controller openttd.Scores being active while News and OpenTTD are not.
 *   - #{set 'subtitle'} + #{pluralize size:nbScores, word:'score'}: singular (nbScores == 1,
 *     pluralize returns "") and plural (0 and >1).
 *   - the two period buttons: #{if period == 'last_month'}active#{/if} and the forever one, in
 *     both directions. Scores.show() normalizes any unknown period to last_month, so
 *     /scores/whatever also covers the normalization path.
 *   - #{secure.check 'moderator'}: the export button for the moderator group (Permission.moderate
 *     on the default category) and for an admin group, hidden for the users group and anonymous.
 *   - #{pagination page:page ?: 1, size:nbScores}: nbPages > 1 false, nbPages <= 5 (every page
 *     listed), nbPages > 5 (only i == 1, i == nbPages, i % 5 == 0 and Math.abs(i - _page) <= 2),
 *     _page == i (active) vs #{else}, and page ?: 1 taking both its sides.
 *   - #{list items:playerScores}: the empty list path, and the four row cells index / player link
 *     / points / gameCount.
 *
 * nbPages is nbScores.page(pageSize) (JavaExtensions.page: ceil), and nbScores is
 * PlayedGameScore.count(from, to), which counts DISTINCT players in the period, not score rows.
 * With the configured forum.pageSize of 10 the pagination block needs at least 11 distinct
 * players, so the pagination tests set Application.pageSize by reflection (it is parsed once at
 * class initialization) and restore it afterwards, exactly as IndexPageTest does.
 *
 * Scores.show() caches playerScores_<period>_<page> for 5s, so every request goes through show(),
 * which clears the cache first: otherwise a list cached by an earlier test would be served to the
 * next one.
 *
 * Ratios are integers (achivement / presenceRatio) so cast(sum(...) as int) is exact, and
 * presenceRatio is never 0: MariaDB runs with ERROR_FOR_DIVISION_BY_ZERO.
 *
 * Scores.exportAsCSV() is never reachable: conf/routes declares GET /scores/{period} before
 * GET /scores/exportCSV, and Play matches the first route, so /scores/exportCSV is handled by
 * show("exportCSV", null) and renders this template as text/html. The tests assert that observed
 * behaviour instead of a CSV body, and the moderator-only @Check(MODERATOR) never runs.
 */
public class ScoresPageTest extends BaseFunctionalTest {

	private static final String LAST_MONTH = "/scores/last_month";
	private static final String FOREVER = "/scores/forever";

	private Integer originalPageSize;

	@Before
	public void loadScoresFixtures() throws Throwable {
		// data.yml is already loaded by BaseFunctionalTest.loadFixtures(); this adds twelve
		// players with no scores, ids 5..16.
		JPA.withTransaction(JPA.DEFAULT, false, () -> {
			Fixtures.loadModels("scores-data.yml");
			return null;
		});
	}

	@After
	public void restorePageSize() throws Throwable {
		if (originalPageSize != null) {
			setPageSize(originalPageSize);
			originalPageSize = null;
		}
	}

	// ~~~~~~~~~~~~ anonymous, empty leaderboard

	@Test
	public void anonymousScoresPageShowsTitleActiveNavZeroScoresAndNoExportButton() throws Throwable {
		Response response = show(LAST_MONTH);
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
		String body = getContent(response);

		// #{extends 'main.html' /} and #{set title: 'Scores'/}
		assertContains("<title>Strategyboard - Scores</title>", body);
		assertContains("<h1>Scores</h1>", body);
		// main.html nav: Scores active for controller openttd.Scores, News and OpenTTD not
		assertContains("<li class=\"active\"><a href=\"/scores\">Scores</a></li>", body);
		assertNotContains("<li class=\"active\"><a href=\"/\">News</a></li>", body);
		assertNotContains("<li class=\"active\"><a href=\"/openttd\">OpenTTD</a></li>", body);
		// main.html #{else} branch, and secure.check('administrator') denying an anonymous
		assertContains(">Login</a>", body);
		assertContains(">Signup</a>", body);
		assertNotContains("href=\"/admin\"", body);
		assertNotContains("alert alert-success", body);
		assertNotContains("alert alert-danger", body);

		// #{pluralize} with nbScores == 0: plural form
		assertContains("<p>0 scores</p>", body);

		// #{secure.check 'moderator'} false path
		assertNotContains("Export as CSV", body);
		assertNotContains("/scores/exportCSV", body);

		// the panel wrapper of the table, and the empty <p> that holds the pagination tag
		assertContains("<div class=\"panel panel-default\">", body);
		assertEquals(1, count("<div class=\"panel panel-default\">", body));

		// #{pagination} with nbPages == 1: no block at all
		assertNotContains("<ul class=\"pagination\">", body);

		// #{list items:playerScores} over an empty list: headers only, no rows
		String table = usersTable(body);
		assertContains("<table id=\"users-list\" class=\"table table-striped\">", table);
		assertContains("<th>#</th>", table);
		assertContains("<th>Name</th>", table);
		assertContains("<th>Points</th>", table);
		assertContains("<th>Games</th>", table);
		assertNotContains("<tr>", tbodyOf(table));
		assertNotContains("/users/", table);
	}

	@Test
	public void subtitlePluralizesOneAndManyScores() throws Throwable {
		seedScore("player01@domain.com", 10, 1, 2);
		assertContains("<p>1 score</p>", getContent(show(LAST_MONTH)));
		assertNotContains("<p>1 scores</p>", getContent(show(LAST_MONTH)));

		seedScore("player02@domain.com", 20, 2, 2); // ratio 10, so two players now
		assertContains("<p>2 scores</p>", getContent(show(LAST_MONTH)));
		assertNotContains("<p>2 score</p>", getContent(show(LAST_MONTH)));
	}

	// ~~~~~~~~~~~~ period buttons

	@Test
	public void periodButtonsMarkTheCurrentPeriodAsActive() throws Throwable {
		String lastMonth = buttonGroup(getContent(show(LAST_MONTH)));
		assertContains("<div class=\"btn-group\">", lastMonth);
		assertMatches("<a href=\"/scores/last_month\\?page=1\" class=\"btn btn-default\\s*active\">last month</a>", lastMonth);
		assertMatches("<a href=\"/scores/forever\\?page=1\" class=\"btn btn-default\\s*\">forever</a>", lastMonth);
		assertEquals(1, count("active", lastMonth));

		String forever = buttonGroup(getContent(show(FOREVER)));
		assertMatches("<a href=\"/scores/forever\\?page=1\" class=\"btn btn-default\\s*active\">forever</a>", forever);
		assertMatches("<a href=\"/scores/last_month\\?page=1\" class=\"btn btn-default\\s*\">last month</a>", forever);
		assertEquals(1, count("active", forever));
	}

	@Test
	public void unknownPeriodIsNormalizedToLastMonth() throws Throwable {
		seedScore("player01@domain.com", 10, 1, 2);
		String body = getContent(show("/scores/whatever"));
		assertContains("<title>Strategyboard - Scores</title>", body);
		// Scores.show() reassigns period to period_last_month when it is not 'forever'
		assertMatches("<a href=\"/scores/last_month\\?page=1\" class=\"btn btn-default\\s*active\">last month</a>", buttonGroup(body));
		assertContains("<p>1 score</p>", body);
		assertContains("Score Player 01", usersTable(body));
	}

	@Test
	public void foreverIncludesScoresOlderThanTheLastMonth() throws Throwable {
		seedScore("player01@domain.com", 12, 1, 2); // inside the last month
		seedScore("player02@domain.com", 30, 1, 40); // outside it

		String lastMonth = getContent(show(LAST_MONTH));
		assertContains("<p>1 score</p>", lastMonth);
		assertContains("Score Player 01", usersTable(lastMonth));
		assertNotContains("Score Player 02", usersTable(lastMonth));

		String forever = getContent(show(FOREVER));
		assertContains("<p>2 scores</p>", forever);
		String tbody = tbodyOf(usersTable(forever));
		// 30 points outranks 12, and the older player is listed first with index 1
		assertMatches("<td>\\s*1\\s*</td>\\s*<td>\\s*<a href=\"/users/\\d+\">Score Player 02</a>\\s*</td>\\s*<td>\\s*30</td>", tbody);
		assertMatches("<td>\\s*2\\s*</td>\\s*<td>\\s*<a href=\"/users/\\d+\">Score Player 01</a>\\s*</td>\\s*<td>\\s*12</td>", tbody);
		assertTrue("the older player must come first", tbody.indexOf("Score Player 02") < tbody.indexOf("Score Player 01"));
	}

	// ~~~~~~~~~~~~ rows

	@Test
	public void tableRowsShowIndexPlayerLinkPointsAndGameCount() throws Throwable {
		// Two ties on points: gameCount decides the order, then the user id ascending.
		seedScore("player01@domain.com", 60, 10, 2); // 6 points
		seedScore("player01@domain.com", 60, 10, 3); // 6 points -> 12 total, gameCount 2
		seedScore("player02@domain.com", 12, 1, 2); // 12 points, gameCount 1
		seedScore("player03@domain.com", 12, 1, 2); // 12 points, gameCount 1, id 7 > id 6

		String tbody = tbodyOf(usersTable(getContent(show(LAST_MONTH))));
		assertMatches("<td>\\s*1\\s*</td>\\s*<td>\\s*<a href=\"/users/\\d+\">Score Player 01</a>\\s*</td>\\s*<td>\\s*12</td>\\s*<td>\\s*2</td>", tbody);
		assertMatches("<td>\\s*2\\s*</td>\\s*<td>\\s*<a href=\"/users/\\d+\">Score Player 02</a>\\s*</td>\\s*<td>\\s*12</td>\\s*<td>\\s*1</td>", tbody);
		assertMatches("<td>\\s*3\\s*</td>\\s*<td>\\s*<a href=\"/users/\\d+\">Score Player 03</a>\\s*</td>\\s*<td>\\s*12</td>\\s*<td>\\s*1</td>", tbody);
		assertEquals(3, count("<tr>", tbody));
		// PlayedGameScore.count() counts distinct players, so three players -> "3 scores"
		assertContains("<p>3 scores</p>", getContent(show(LAST_MONTH)));
	}

	// ~~~~~~~~~~~~ pagination

	@Test
	public void paginationIsAbsentWhenThereIsASinglePage() throws Throwable {
		// forum.pageSize is 10, so five players give nbPages == 1 -> #{if nbPages > 1} false
		seedPlayers(5);
		String body = getContent(show(LAST_MONTH));
		assertContains("<p>5 scores</p>", body);
		assertNotContains("<ul class=\"pagination\">", body);
		// the pagination tag renders nothing, so its <p> wrapper is empty (the tag emits a
		// newline, hence the \s*)
		assertMatches("<p>\\s*</p>", body);
		assertEquals(5, count("<tr>", tbodyOf(usersTable(body))));
	}

	@Test
	public void paginationListsEveryPageWhenThereAreFiveOrFewer() throws Throwable {
		setPageSize(3); // nbPages = 12 / 3 = 4 -> nbPages <= 5 true
		seedPlayers(12);
		String body = getContent(show(LAST_MONTH));
		String block = paginationBlock(body);
		// the block sits inside the <p> wrapper of #{pagination ...}
		assertMatches("<p>\\s*<ul class=\"pagination\">", body);

		assertContains("<li class=\"active\"><a href=\"#\">1</a></li>", block);
		assertEquals(1, count("class=\"active\"", block));
		assertContains("<li><a href=\"/scores/last_month?page=2\">2</a></li>", block);
		assertContains("<li><a href=\"/scores/last_month?page=3\">3</a></li>", block);
		assertContains("<li><a href=\"/scores/last_month?page=4\">4</a></li>", block);
		assertNotContains("?page=5", block);
		assertEquals(4, count("<li>", block) + count("<li class=\"active\">", block));
		// page 1 holds the first three players
		String tbody = tbodyOf(usersTable(body));
		assertEquals(3, count("<tr>", tbody));
		assertContains("Score Player 03", tbody);
		assertNotContains("Score Player 04", tbody);
	}

	@Test
	public void paginationHonoursThePageParameter() throws Throwable {
		setPageSize(3); // nbPages = 4, and page ?: 1 takes its right side
		seedPlayers(12);
		String body = getContent(show("/scores/last_month?page=3"));
		String block = paginationBlock(body);

		assertContains("<li class=\"active\"><a href=\"#\">3</a></li>", block);
		assertContains("?page=1", block);
		assertContains("?page=2", block);
		assertContains("?page=4", block);
		assertEquals(1, count("class=\"active\"", block));
		// PlayerScore.index starts at 1 + (page - 1) * pageSize -> 7..9
		String tbody = tbodyOf(usersTable(body));
		assertEquals(3, count("<tr>", tbody));
		assertMatches("<td>\\s*7\\s*</td>", tbody);
		assertMatches("<td>\\s*9\\s*</td>", tbody);
		assertContains("Score Player 07", tbody);
		assertNotContains("Score Player 06", tbody);
	}

	@Test
	public void paginationSkipsPagesOutsideTheWindowOfTheCurrentOne() throws Throwable {
		setPageSize(1); // nbPages = 12 / 1 = 12 -> nbPages <= 5 false
		seedPlayers(12);
		String block = paginationBlock(getContent(show("/scores/last_month?page=6")));

		assertContains("<li class=\"active\"><a href=\"#\">6</a></li>", block);
		assertContains("?page=1", block); // i == 1
		assertContains("?page=4", block); // Math.abs(i - _page) <= 2
		assertContains("?page=5", block); // Math.abs(i - _page) <= 2 and i % 5 == 0
		assertContains("?page=7", block);
		assertContains("?page=8", block);
		assertContains("?page=10", block); // i % 5 == 0
		assertContains("?page=12", block); // i == nbPages
		assertNotContains("?page=2", block);
		assertNotContains("?page=3", block);
		assertNotContains("?page=9", block);
		assertNotContains("?page=11", block);
		// one row on this page, carrying index 6
		String tbody = tbodyOf(usersTable(getContent(show("/scores/last_month?page=6"))));
		assertEquals(1, count("<tr>", tbody));
		assertContains("Score Player 06", tbody);
		assertMatches("<td>\\s*6\\s*</td>", tbody);
	}

	@Test
	public void paginationLinksCarryThePathOfTheCurrentPeriod() throws Throwable {
		setPageSize(1); // nbPages = 12
		seedPlayers(12);
		String block = paginationBlock(getContent(show("/scores/forever?page=6")));
		assertContains("<li><a href=\"/scores/forever?page=4\">4</a></li>", block);
		assertContains("<li><a href=\"/scores/forever?page=12\">12</a></li>", block);
		assertNotContains("/scores/last_month?page=", block);
	}

	// ~~~~~~~~~~~~ the entry route

	@Test
	public void scoresIndexRedirectsToTheLastMonthPage() throws Throwable {
		seedScore("player01@domain.com", 10, 1, 2);
		// Scores.index() calls show() directly, which Play turns into a framework redirect to
		// the nested action's URL (same shape as Openttd.stop() -> index()).
		Cache.clear();
		Response response = GET("/scores");
		assertStatus(302, response);
		String location = response.headers.get("Location").value();
		assertTrue("redirect target must be the last month page: " + location, location.startsWith("/scores/last_month"));

		Cache.clear();
		Response followed = GET("/scores", true);
		assertIsOk(followed);
		String body = getContent(followed);
		assertContains("<title>Strategyboard - Scores</title>", body);
		assertContains("<p>1 score</p>", body);
		assertMatches("<a href=\"/scores/last_month\\?page=1\" class=\"btn btn-default\\s*active\">last month</a>", buttonGroup(body));
	}

	// ~~~~~~~~~~~~ moderator and admin

	@Test
	public void moderatorSeesTheExportButtonAndExportCsvIsShadowedByThePeriodRoute() throws Throwable {
		login("modo@domain.com", "secret");
		String body = getContent(show(LAST_MONTH));
		assertContains("<a class=\"btn btn-danger\" href=\"/scores/exportCSV\">Export as CSV</a>", body);
		assertEquals(1, count("/scores/exportCSV", body));
		// logged in: main.html #{if connectedUser} true path, flash.success branch
		assertContains("Moderator <b class=\"caret\"></b>", body);
		assertContains("Welcome back Moderator !", body);
		assertContains("alert alert-success", body);
		assertNotContains(">Signup</a>", body);

		// conf/routes declares GET /scores/{period} before GET /scores/exportCSV, so the export
		// link is handled by show("exportCSV", null): this template, as text/html, with the
		// period normalized to last_month. Scores.exportAsCSV() never runs.
		Response export = show("/scores/exportCSV");
		assertStatus(200, export);
		assertContentType("text/html", export);
		assertContains("<p>0 scores</p>", getContent(export));
		assertMatches("<a href=\"/scores/last_month\\?page=1\" class=\"btn btn-default\\s*active\">last month</a>", buttonGroup(getContent(export)));
		assertContains("<a class=\"btn btn-danger\" href=\"/scores/exportCSV\">Export as CSV</a>", getContent(export));
	}

	@Test
	public void userDoesNotSeeTheExportButtonAndExportCsvIsNeverForbidden() throws Throwable {
		login("bob@domain.com", "secret");
		String body = getContent(show(LAST_MONTH));
		// secure.check 'moderator' false path for the users group (accessLevel 3 < moderate 4)
		assertNotContains("Export as CSV", body);
		assertNotContains("/scores/exportCSV", body);
		assertContains("Bob <b class=\"caret\"></b>", body);

		// the shadowed route reaches show(), not exportAsCSV(), so @Check(MODERATOR) never runs
		Response export = show("/scores/exportCSV");
		assertStatus(200, export);
		assertContentType("text/html", export);
		assertNotContains("Export as CSV", getContent(export));
	}

	@Test
	public void adminSeesTheExportButtonAndTheAdministrationLink() throws Throwable {
		login("admin@domain.com", "secret");
		String body = getContent(show(LAST_MONTH));
		// Profile.Security.check() short-circuits on group.admin
		assertContains("<a class=\"btn btn-danger\" href=\"/scores/exportCSV\">Export as CSV</a>", body);
		assertContains("href=\"/admin/\"", body);
		assertContains(">Administration</a>", body);
		assertNotContains("badge pull-right", body);
		assertNotContains("alert alert-danger", body);
	}

	// ~~~~~~~~~~~~ fixtures helpers

	/** One PlayedGameScore ending daysAgo days ago, with an exact achivement/presenceRatio ratio. */
	private void seedScore(final String email, final double achivement, final double presenceRatio, final int daysAgo) throws Throwable {
		JPA.withTransaction(JPA.DEFAULT, false, () -> {
			PlayedGame playedGame = new PlayedGame();
			playedGame.begin = daysAgo(daysAgo + 10);
			playedGame.end = daysAgo(daysAgo);
			playedGame.create();
			PlayedGameScore score = new PlayedGameScore();
			score.playedGame = playedGame;
			score.user = User.findByEmail(email);
			score.achivement = achivement;
			score.presenceRatio = presenceRatio;
			score.create();
			return null;
		});
	}

	/** One score each for the first count fixture players, all with 10 points so ties order by id. */
	private void seedPlayers(final int count) throws Throwable {
		for (int i = 1; i <= count; i++) {
			seedScore(String.format("player%02d@domain.com", i), 10, 1, 2);
		}
	}

	private static Date daysAgo(final int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_MONTH, -days);
		return calendar.getTime();
	}

	private void setPageSize(final Integer value) throws Throwable {
		Field field = Application.class.getDeclaredField("pageSize");
		field.setAccessible(true);
		Integer previous = (Integer) field.get(null);
		if (originalPageSize == null) {
			originalPageSize = previous;
		}
		field.set(null, value);
	}

	// ~~~~~~~~~~~~ request helpers

	/** GET with the controller's 5s playerScores cache cleared, so seeded data is always read. */
	private static Response show(final String url) {
		Cache.clear();
		return GET(url);
	}

	// ~~~~~~~~~~~~ response helpers

	/** The period buttons group of show.html, from its opening div to its closing one. */
	private static String buttonGroup(String body) {
		int start = body.indexOf("<div class=\"btn-group\">");
		assertTrue("period buttons group not found", start >= 0);
		return body.substring(start, body.indexOf("</div>", start));
	}

	/** The players table of show.html, from its id to its closing tag. */
	private static String usersTable(String body) {
		int start = body.indexOf("id=\"users-list\"");
		assertTrue("users table not found", start >= 0);
		int table = body.lastIndexOf("<table", start);
		assertTrue("table element not found", table >= 0);
		return body.substring(table, body.indexOf("</table>", table));
	}

	/** The tbody of a table, i.e. its rows without the header row. */
	private static String tbodyOf(String table) {
		int start = table.indexOf("<tbody>");
		assertTrue("tbody not found", start >= 0);
		return table.substring(start, table.indexOf("</tbody>", start));
	}

	/** The pagination list, from its opening tag to its closing one. */
	private static String paginationBlock(String body) {
		int start = body.indexOf("<ul class=\"pagination\">");
		assertTrue("pagination block not found", start >= 0);
		return body.substring(start, body.indexOf("</ul>", start));
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
}
