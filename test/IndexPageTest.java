import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.*;

import play.db.jpa.JPA;
import play.mvc.Http.Response;
import play.test.Fixtures;
import play.libs.F;

import controllers.Application;
import models.forum.Attachment;
import models.forum.Post;
import models.forum.Topic;
import models.forum.User;
import models.leaderboard.Game;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.Scenario;

/**
 * Coverage of app/views/Application/index.html: every construct of the template, and of
 * the tags it renders (ingameLogin, miniLeaderBoard, showNews -> attachments -> pluralize,
 * pagination, miniOpenTTDStatus), runs in both directions of each conditional.
 *
 * Two branches are unreachable from this template and are asserted as absent instead of
 * exercised:
 *   - miniOpenTTDStatus's arg branch: Play does not skip tags inside HTML comments, so the
 *     five commented-out calls do run, but nothing ever puts articStatus, deserticStatus,
 *     highSpeedStatus, steamStatus or toylandStatus into renderArgs -> they always take the
 *     "server is down" path.
 *   - ingameLogin's login-link branch: index.html only calls the tag when connectedUser is
 *     truthy, so its else path can never be reached.
 *
 * index.html passes the literal size:10 to pagination, so nbPages is 10 / pageSize. With the
 * configured forum.pageSize of 10 the pagination block is dead code; the pagination tests set
 * Application.pageSize by reflection (it is parsed once at class initialization, from
 * Play.configuration) and restore it afterwards. Application.index also has to render its page
 * parameter - action parameters are not added to renderArgs by the framework, so without it
 * "page ?: 1" could only ever take its left side.
 */
public class IndexPageTest extends BaseFunctionalTest {

	private Integer originalPageSize;

	@Before
	public void loadHomePageFixtures() throws Throwable {
		// data.yml is already loaded by BaseFunctionalTest.loadFixtures(); this adds the home
		// page news topics, their attachments and an unread message.
		JPA.withTransaction(JPA.DEFAULT, false, () -> {
			Fixtures.loadModels("index-data.yml");
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

	// ~~~~~~~~~~~~ anonymous (connectedUser is null: Profile.Security.connected() maps the
	// anonymous email and a missing session to null)

	@Test
	public void anonymousHomePageShowsTitleActiveNavAndLoginLinks() throws Throwable {
		Response response = GET("/");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
		String body = getContent(response);

		// #{extends 'main.html' /} and #{set title:'News' /}
		assertContains("<title>Strategyboard - News</title>", body);
		assertContains("<h1>News</h1>", body);
		// main.html nav: the News item is active for controller Application
		assertContains("<li class=\"active\"><a href=\"/\">News</a></li>", body);
		// main.html #{else} branch, and secure.check('administrator') denying an anonymous
		assertContains(">Login</a>", body);
		assertContains(">Signup</a>", body);
		assertNotContains("href=\"/admin\"", body);
		// main.html flash branches, neither set on this request
		assertNotContains("alert alert-success", body);
		assertNotContains("alert alert-danger", body);
	}

	@Test
	public void commentedOpenTTDStatusTagsStillRenderFiveDownPanels() throws Throwable {
		String body = getContent(GET("/"));
		String leftColumn = leftColumn(body);

		// Play compiles tags inside HTML comments (see Template_1022 in precompiled/), so the
		// five commented-out miniOpenTTDStatus calls render, with a null arg -> else branch.
		assertContains("<!--", leftColumn);
		assertContains("-->", leftColumn);
		assertEquals(5, count("Server is down :(", leftColumn));
		assertEquals(5, count(">OpenTTD server</a>", leftColumn));
		// the panels sit between the comment markers, and the rest of the page still renders
		assertTrue(leftColumn.indexOf("<!--") < leftColumn.indexOf("Server is down :("));
		assertTrue(leftColumn.indexOf("Server is down :(") < leftColumn.indexOf("-->"));
		assertContains("<div class=\"row\">", body);
	}

	@Test
	public void anonymousHasNoInGameLoginPanel() throws Throwable {
		String body = getContent(GET("/"));
		// index.html #{if connectedUser} false path: nothing is rendered there
		assertNotContains("In game login", body);
		assertNotContains("!login", body);
	}

	@Test
	public void newsIsListedInDescendingPostOrder() throws Throwable {
		String body = getContent(GET("/"));
		// Forum.getTopics() orders by the latest post, so #{list items:news} renders newest first
		int plain = body.indexOf("Plain news");
		int withAttachments = body.indexOf("News with attachments");
		int second = body.indexOf("Second news");
		int first = body.indexOf("First news");
		assertTrue("Plain news not found", plain >= 0);
		assertTrue("news with attachments must come before Second news", withAttachments < second);
		assertTrue("Second news must come before First news", second < first);
		assertTrue("Plain news must be the newest", plain < withAttachments);
	}

	@Test
	public void showNewsFormatsTimestampAndTurnsNewlinesIntoBreaks() throws Throwable {
		String body = getContent(GET("/"));
		// showNews: _arg.postedAt.format('MM/dd, yyyy hh:mma'). The hour depends on the JVM time
		// zone (fixtures are stored as dates), so only the date part is asserted literally.
		assertContains("01/08, 2024 ", body);
		assertContains("01/07, 2024 ", body);
		assertContains("01/06, 2024 ", body);
		assertContains("01/05, 2024 ", body);
		assertMatches("\\d{2}/\\d{2}, 2024 \\d{2}:\\d{2}(AM|PM)", body);
		// showNews: content.nl2br().raw()
		assertContains("First line of the plain news<br/>Second line of the plain news", body);
	}

	@Test
	public void attachmentsRenderBothBranchesAndBothPluralForms() throws Throwable {
		String body = getContent(GET("/"));

		String panel = newsPanel(body, "News with attachments");
		assertEquals(3, count("glyphicon glyphicon-file", panel));
		// description + downloads both truthy -> plural form
		assertMatches("<a href=\"[^\"]*\">report\\.txt</a>:\\s*Attachment report,\\s*2 downloads\\.", panel);
		// description absent and downloads 0 -> both inner ifs take their false path
		assertMatches("<a href=\"[^\"]*\">chart\\.png</a>\\.", panel);
		// downloads truthy, description absent -> singular form
		assertMatches("<a href=\"[^\"]*\">single\\.txt</a>,\\s*1 download\\.", panel);
		assertNotContains("0 downloads", panel);

		// a news post with no attachment takes attachments' empty branch
		assertNotContains("glyphicon glyphicon-file", newsPanel(body, "Plain news"));
	}

	@Test
	public void emptyLeaderboardRendersThePanelWithoutRows() throws Throwable {
		String body = getContent(GET("/"));
		String table = leaderboardTable(body);
		assertContains("This month scores", body);
		// #{list items:_arg} over an empty list: no rows at all
		assertNotContains("<tr>", table);
		assertNotContains("/users/", table);
	}

	@Test
	public void leaderboardShowsOnlyScoresOfTheLastMonth() throws Throwable {
		seedScore("index@domain.com", 10, 2.0, 20);
		seedScore("index@domain.com", 4, 2.0, 15);
		seedScore("news@domain.com", 100, 1.0, 60); // older than a month -> filtered out

		String body = getContent(GET("/"));
		assertMatches("Index User</a></td>\\s*<td align=\"right\">7</td>", leaderboardTable(body));
		assertNotContains("News Author", body);
	}

	@Test
	public void paginationIsAbsentWhenThereIsASinglePage() throws Throwable {
		// forum.pageSize is 10 and index.html passes size:10 -> nbPages == 1 -> #{if nbPages > 1} false
		String body = getContent(GET("/"));
		assertNotContains("<ul class=\"pagination\">", body);
	}

	@Test
	public void paginationMarksTheFirstPageAsActive() throws Throwable {
		setPageSize(2); // nbPages = 10 / 2 = 5, page null -> page ?: 1 takes its left side
		Response response = GET("/");
		assertIsOk(response);
		String block = paginationBlock(getContent(response));

		assertContains("<li class=\"active\"><a href=\"#\">1</a></li>", block);
		assertEquals(1, count("class=\"active\"", block));
		assertContains("?page=2", block);
		assertContains("?page=3", block);
		assertContains("?page=4", block);
		assertContains("?page=5", block);
		// nbPages <= 5 -> every page is listed
		assertEquals(5, count("<li>", block) + count("<li class=\"active\">", block));
	}

	@Test
	public void paginationHonoursThePageParameter() throws Throwable {
		setPageSize(2); // nbPages = 5, and page ?: 1 takes its right side
		Response response = GET("/?page=3");
		assertIsOk(response);
		String body = getContent(response);
		String block = paginationBlock(body);

		assertContains("<li class=\"active\"><a href=\"#\">3</a></li>", block);
		assertContains("?page=1", block);
		assertContains("?page=2", block);
		assertContains("?page=4", block);
		assertContains("?page=5", block);
		assertEquals(1, count("class=\"active\"", block));
		// page 3 is past the four topics -> #{list items:news} iterates zero times
		assertNotContains("First news", body);
		assertNotContains("Second news", body);
	}

	@Test
	public void paginationSkipsPagesOutsideTheWindowOfTheCurrentOne() throws Throwable {
		setPageSize(1); // nbPages = 10 / 1 = 10 -> nbPages <= 5 false
		String block = paginationBlock(getContent(GET("/"))); // page null -> page ?: 1

		assertContains("?page=1", block); // i == 1
		assertContains("?page=3", block); // Math.abs(i - _page) <= 2
		assertContains("?page=5", block); // i % 5 == 0
		assertContains("?page=10", block); // i == nbPages
		assertNotContains("?page=4", block);
		assertNotContains("?page=6", block);
		assertNotContains("?page=7", block);
		assertNotContains("?page=8", block);
		assertNotContains("?page=9", block);
		assertContains("<li class=\"active\"><a href=\"#\">1</a></li>", block);
	}

	@Test
	public void paginationKeepsTheNeighboursOfTheCurrentPage() throws Throwable {
		setPageSize(1); // nbPages = 10
		String block = paginationBlock(getContent(GET("/?page=5")));

		assertContains("<li class=\"active\"><a href=\"#\">5</a></li>", block);
		assertContains("?page=1", block); // i == 1
		assertContains("?page=3", block);
		assertContains("?page=4", block);
		assertContains("?page=6", block);
		assertContains("?page=7", block); // Math.abs(i - _page) <= 2
		assertContains("?page=10", block); // i == nbPages
		assertNotContains("?page=2", block);
		assertNotContains("?page=8", block);
		assertNotContains("?page=9", block);
	}

	@Test
	public void homePageRendersWithoutAnyNews() throws Throwable {
		deleteNews();
		Response response = GET("/");
		assertIsOk(response);
		String body = getContent(response);
		assertNotContains("panel-body", body);
		assertNotContains("First news", body);
		assertNotContains("Plain news", body);
		assertContains("This month scores", body);
		assertContains("<title>Strategyboard - News</title>", body);
	}

	// ~~~~~~~~~~~~ logged in (connectedUser is the user, uuid set by Secure.onAuthenticated())

	@Test
	public void loggedInHomeShowsInGameLoginUuidMessagesBadgeAndWelcome() throws Throwable {
		login("index@domain.com", "secret");
		Response response = GET("/");
		assertIsOk(response);
		String body = getContent(response);

		// index.html #{if connectedUser} true path -> ingameLogin with a truthy arg
		assertContains("In game login", body);
		assertContains(">In game login</h3>", body);
		final String email = "index@domain.com";
		String uuid = inTransaction(() -> User.findByEmail(email).uuid);
		assertTrue("no in game uuid after login", uuid != null && uuid.length() > 0);
		assertContains("!login " + uuid, body);
		assertNotContains(">Signup</a>", body);
		assertNotContains(">Login</a>", body);
		// main.html dropdown, newMessagesCount > 0 branch, flash.success branch
		assertContains("Index User <b class=\"caret\"></b>", body);
		assertContains("<span class=\"badge pull-right\">1</span>Messages", body);
		assertContains(">My profile</a>", body);
		assertContains(">Logout</a>", body);
		assertContains("alert alert-success", body);
		assertContains("Welcome back Index User !", body);
	}

	@Test
	public void adminHomeShowsAdministrationLinkWithoutMessagesBadge() throws Throwable {
		login("admin@domain.com", "secret");
		Response response = GET("/");
		assertIsOk(response);
		String body = getContent(response);

		// main.html secure.check('administrator') true branch (CRUD is mounted at /admin)
		assertContains("href=\"/admin/\"", body);
		assertContains(">Administration</a>", body);
		// newMessagesCount == 0 -> false branch
		assertNotContains("badge pull-right", body);
		assertContains("!login " + inTransaction(() -> User.findByEmail("admin@domain.com").uuid), body);
		// flash.success branch, no flash.error anywhere on this page
		assertContains("alert alert-success", body);
		assertNotContains("alert alert-danger", body);
	}

	// ~~~~~~~~~~~~ fixtures helpers

	private void setPageSize(Integer value) throws Throwable {
		Field field = Application.class.getDeclaredField("pageSize");
		field.setAccessible(true);
		Integer previous = (Integer) field.get(null);
		if (originalPageSize == null) {
			originalPageSize = previous;
		}
		field.set(null, value);
	}

	/** One PlayedGameScore whose game ended the given number of days ago. */
	private void seedScore(final String email, final int achivement, final double presenceRatio, final int daysAgo) throws Throwable {
		JPA.withTransaction(JPA.DEFAULT, false, () -> {
			Game game = new Game();
			game.name = "Game ended " + daysAgo + " days ago";
			game.create();
			Scenario scenario = new Scenario();
			scenario.name = "HighSpeed " + daysAgo;
			scenario.game = game;
			scenario.create();
			PlayedGame playedGame = new PlayedGame();
			playedGame.scenario = scenario;
			playedGame.begin = daysAgo(daysAgo + 10);
			playedGame.end = daysAgo(daysAgo);
			playedGame.create();
			PlayedGameScore score = new PlayedGameScore();
			score.playedGame = playedGame;
			score.user = User.findByEmail(email);
			score.presenceRatio = presenceRatio;
			score.achivement = achivement;
			score.create();
			return null;
		});
	}

	private static Date daysAgo(int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_MONTH, -days);
		return calendar.getTime();
	}

	/** Empties the Home Page forum, attachments first so no post row is left dangling. */
	private void deleteNews() throws Throwable {
		JPA.withTransaction(JPA.DEFAULT, false, () -> {
			List<Attachment> attachments = Attachment.find(
					"select a from Attachment a where a.post.topic.forum.name = ?1", "Home Page").fetch();
			for (Attachment attachment : attachments) {
				attachment.delete();
			}
			List<Post> posts = Post.find("select p from Post p where p.topic.forum.name = ?1", "Home Page").fetch();
			for (Post post : posts) {
				post.delete();
			}
			List<Topic> topics = Topic.find("select t from Topic t where t.forum.name = ?1", "Home Page").fetch();
			for (Topic topic : topics) {
				topic.delete();
			}
			return null;
		});
	}

	// ~~~~~~~~~~~~ response helpers

	/** The left column of index.html, from the narrow column div to the news column div. */
	private static String leftColumn(String body) {
		int start = body.indexOf("<div class=\"col-xs-12 col-sm-4 col-md-3\">");
		assertTrue("left column not found", start >= 0);
		int end = body.indexOf("<div class=\"col-xs-12 col-sm-8 col-md-9\">", start);
		assertTrue("news column not found", end >= 0);
		return body.substring(start, end);
	}

	/** Reads a value from the database; request transactions are closed once the response is done. */
	private static String inTransaction(F.Function0<String> block) throws Throwable {
		return JPA.withTransaction(JPA.DEFAULT, false, block);
	}

	/** The showNews panel body that follows the given news subject. */
	private static String newsPanel(String body, String subject) {
		int start = body.indexOf(subject);
		assertTrue("news panel for '" + subject + "' not found", start >= 0);
		int panelBody = body.indexOf("panel-body", start);
		assertTrue("panel-body not found for '" + subject + "'", panelBody >= 0);
		return body.substring(panelBody, body.indexOf("</div>", panelBody));
	}

	/** The miniLeaderBoard table, from its opening tag to its closing one. */
	private static String leaderboardTable(String body) {
		int heading = body.indexOf("This month scores");
		assertTrue("leaderboard panel not found", heading >= 0);
		int start = body.indexOf("<table", heading);
		assertTrue("leaderboard table not found", start >= 0);
		return body.substring(start, body.indexOf("</table>", start));
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
