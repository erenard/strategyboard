import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.*;

import play.db.jpa.JPA;
import play.mvc.Http;
import play.templates.JavaExtensions;
import play.templates.TemplateLoader;
import play.test.Fixtures;

import com.openttd.network.admin.GameInfo;

import models.forum.Attachment;
import models.forum.Post;
import models.forum.Topic;
import models.forum.User;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.PlayerScore;
import models.leaderboard.Scenario;
import models.openttd.OpenttdServer;
import models.openttd.RobotConfiguration;

/**
 * Direct unit coverage of every template in app/views/tags/*.html.
 *
 * Each test renders the tag file itself through play.templates.TemplateLoader.load(path) and
 * render(args), passing the tag variables (_arg, _size, _word, _page, _caller, request) the way
 * a caller template would. That reaches branches no route can reach:
 *   - miniOpenTTDStatus: Application/index.html is its only caller, it is commented out there, and
 *     Application.index() never puts articStatus/deserticStatus/... in renderArgs, so its #{if _arg}
 *     true path is unreachable from the application (see IndexPageTest's five down panels).
 *   - showBestGames: getBestPlayedGamesForScenarioId() builds its rows from PlayedGameScore, so every
 *     row's PlayedGame has at least one score and PlayedGameScore.getWinner() never returns null ->
 *     its #{else} "Anonymous" path is unreachable from /openttd.
 *   - pluralize: the thousands grouping of ${_size.format('###,###')} needs a count >= 1000, which no
 *     fixture page reaches.
 *
 * app/views/tags/css.html and app/views/tags/js.html are gone: they only forwarded to
 * #{greenscript.gs_}, and the greenscript module is not part of the project (nothing in
 * conf/dependencies.yml, nothing in lib/, nothing in Play 1.7.1's bundled modules), and no view ever
 * called them.
 *
 * Tags that touch the database are rendered inside a JPA transaction: ${user.postsCount} runs
 * Post.count() and ${post.attachments} runs Attachment.find(). Entities are created in transactions
 * too, so their ids exist for @{forum.Users.show(id)} and @{forum.Attachments.download(id)}.
 *
 * Dates: end.since() is relative, so dates are seeded a whole number of days in the past and the
 * assertions compare against JavaExtensions.since(end), the same call the template makes.
 * conf/messages defines no since.* keys, so the message lookup falls back to the raw arguments.
 */
public class TagsTest extends BaseFunctionalTest {

	private static final long HIGH_SPEED = 1L;
	private static final long TOYLAND = 2L;
	private static final long ARTIC = 3L;
	private static final long DESERTIC = 4L;
	private static final long STEAM = 5L;

	@Before
	public void loadScenarioFixtures() throws Throwable {
		// data.yml is loaded by BaseFunctionalTest.loadFixtures(); this adds the Game and the five
		// scenarios whose ids 1..5 Scenario.getImage()/getTrainImage() switch on.
		JPA.withTransaction(JPA.DEFAULT, false, () -> {
			Fixtures.loadModels("openttd-data.yml");
			return null;
		});
	}

	// ~~~~~~~~~~~~ pluralize.html

	@Test
	public void pluralizeFormatsGroupsAndPluralizes() throws Throwable {
		assertEquals("1 post", render("pluralize", map("_size", 1, "_word", "post")));
		assertEquals("2 posts", render("pluralize", map("_size", 2, "_word", "post")));
		// pluralize(0) returns "s", so a zero count takes the plural form
		assertEquals("0 scores", render("pluralize", map("_size", 0, "_word", "score")));
		// the ###,### grouping, which no page ever renders: the largest fixture count is 12
		assertEquals("1,234 users", render("pluralize", map("_size", 1234, "_word", "user")));
		assertEquals("1,000,000 games", render("pluralize", map("_size", 1000000, "_word", "game")));
	}

	// ~~~~~~~~~~~~ pagination.html

	@Test
	public void paginationRendersNothingForASinglePage() throws Throwable {
		String out = render("pagination", map("_page", 1, "_size", 5, "_caller", caller("pageSize", 10),
				"request", request("/scores/last_month")));
		assertFalse("no block expected: nbPages == 1", out.contains("<ul class=\"pagination\">"));
		assertEquals("nothing but whitespace expected", "", out.trim());
	}

	@Test
	public void paginationListsEveryPageWhenThereAreFiveOrFewer() throws Throwable {
		String out = render("pagination", map("_page", 1, "_size", 12, "_caller", caller("pageSize", 3),
				"request", request("/scores/last_month")));
		assertTrue(out.contains("<ul class=\"pagination\">"));
		assertTrue(out.contains("<li class=\"active\"><a href=\"#\">1</a></li>"));
		assertTrue(out.contains("<li><a href=\"/scores/last_month?page=2\">2</a></li>"));
		assertTrue(out.contains("<li><a href=\"/scores/last_month?page=3\">3</a></li>"));
		assertTrue(out.contains("<li><a href=\"/scores/last_month?page=4\">4</a></li>"));
		assertFalse(out.contains("?page=5"));
		assertEquals(1, count("class=\"active\"", out));
	}

	@Test
	public void paginationKeepsOnlyTheWindowAroundTheCurrentPage() throws Throwable {
		// nbPages = 12 > 5 -> only i == 1, i == nbPages, i % 5 == 0 and Math.abs(i - _page) <= 2
		String out = render("pagination", map("_page", 6, "_size", 12, "_caller", caller("pageSize", 1),
				"request", request("/scores/forever")));
		assertTrue(out.contains("<li class=\"active\"><a href=\"#\">6</a></li>"));
		for (String kept : new String[] { "?page=1", "?page=4", "?page=5", "?page=7", "?page=8", "?page=10", "?page=12" }) {
			assertTrue("expected " + kept, out.contains(kept));
		}
		for (String dropped : new String[] { "?page=2", "?page=3", "?page=9", "?page=11" }) {
			assertFalse("expected " + dropped + " to be skipped", out.contains(dropped));
		}
	}

	@Test
	public void paginationTakesBothSidesOfTheCallersPageExpression() throws Throwable {
		// page ?: 1 -> the caller hands a null page, so no item is the current one
		String nullPage = render("pagination", map("_page", null, "_size", 12, "_caller", caller("pageSize", 3),
				"request", request("/forums/1/topics/1")));
		assertFalse("no active item expected for a null page", nullPage.contains("class=\"active\""));
		assertTrue(nullPage.contains("<li><a href=\"/forums/1/topics/1?page=1\">1</a></li>"));
		assertTrue(nullPage.contains("<li><a href=\"/forums/1/topics/1?page=2\">2</a></li>"));

		// page ?: 1 -> the caller hands a real page, which becomes the active item
		String givenPage = render("pagination", map("_page", 3, "_size", 12, "_caller", caller("pageSize", 3),
				"request", request("/forums/1/topics/1")));
		assertTrue(givenPage.contains("<li class=\"active\"><a href=\"#\">3</a></li>"));
		assertTrue(givenPage.contains("<li><a href=\"/forums/1/topics/1?page=2\">2</a></li>"));
		assertFalse(givenPage.contains("?page=3"));
	}

	@Test
	public void paginationRoundsUpThePageCount() throws Throwable {
		// nbPages = _size.page(_caller.pageSize): 10 / 10 = 1 renders nothing, 11 / 10 = 2 renders 1..2
		assertFalse(render("pagination", map("_page", 1, "_size", 10, "_caller", caller("pageSize", 10),
				"request", request("/users"))).contains("<ul class=\"pagination\">"));
		String out = render("pagination", map("_page", 2, "_size", 11, "_caller", caller("pageSize", 10),
				"request", request("/users")));
		assertTrue(out.contains("<li><a href=\"/users?page=1\">1</a></li>"));
		assertTrue(out.contains("<li class=\"active\"><a href=\"#\">2</a></li>"));
	}

	// ~~~~~~~~~~~~ attachments.html

	@Test
	public void attachmentsRendersBothInnerConditionsAndBothPluralForms() throws Throwable {
		List<Attachment> attachments = inTransaction(() -> seedAttachmentsOnPost1());

		String out = render("attachments", map("_arg", attachments));
		assertTrue(out.contains("<div class=\"well\">"));
		assertEquals(3, count("<span class=\"glyphicon glyphicon-file\"></span>", out));
		// @{forum.Attachments.download(id)} is reverse-deduced from the catch-all routes, so only
		// the label is asserted exactly, as IndexPageTest does for the same link on the home page.
		// description and downloads both truthy -> ": <description>, N downloads."
		assertMatches("<a href=\"[^\"]*\">report\\.txt</a>:\\s*Attachment report,\\s*2 downloads\\.", out);
		// both conditions false -> the filename and a dot only
		assertMatches("<a href=\"[^\"]*\">chart\\.png</a>\\.", out);
		// downloads truthy and description absent -> singular form
		assertMatches("<a href=\"[^\"]*\">single\\.txt</a>,\\s*1 download\\.", out);
		assertFalse(out.contains("0 downloads"));
		assertEquals(3, count("<br/>", out));
	}

	@Test
	public void attachmentsRendersNothingForAnEmptyOrNullList() throws Throwable {
		assertFalse(render("attachments", map("_arg", new ArrayList<Attachment>())).contains("<div class=\"well\">"));
		assertFalse(render("attachments", map("_arg", null)).contains("<div class=\"well\">"));
		assertEquals("", render("attachments", map("_arg", null)).trim());
	}

	// ~~~~~~~~~~~~ ingameLogin.html

	@Test
	public void ingameLoginShowsTheUuidOfTheConnectedUserOrTheLoginPage() throws Throwable {
		// An account only gets its uuid on registration or on login (Profile.java), so it is set here.
		User bob = inTransaction(() -> seedUuid("bob@domain.com"));
		String out = render("ingameLogin", map("_arg", bob));
		assertTrue(out.contains("<h3 class=\"panel-title\">In game login</h3>"));
		assertTrue(out.contains("!login abc-123"));
		assertFalse(out.contains("<a href=\"/login\">Login</a>"));

		String anonymous = render("ingameLogin", map("_arg", null));
		assertTrue(anonymous.contains("<a href=\"/login\">Login</a>"));
		assertFalse(anonymous.contains("!login"));
	}

	// ~~~~~~~~~~~~ miniLeaderBoard.html

	@Test
	public void miniLeaderBoardLinksPlayersAndPoints() throws Throwable {
		List<PlayerScore> scores = inTransaction(() -> {
			List<PlayerScore> rows = new ArrayList<PlayerScore>();
			rows.add(playerScore(1, User.findByEmail("bob@domain.com"), 12, 2L));
			rows.add(playerScore(2, User.findByEmail("modo@domain.com"), 7, 1L));
			return rows;
		});
		String out = render("miniLeaderBoard", map("_arg", scores));
		assertTrue(out.contains("<a href=\"/scores\">This month scores</a>"));
		assertMatches("<tr><td><a href=\"/users/3\">Bob</a></td><td align=\"right\">12</td></tr>", out);
		assertMatches("<tr><td><a href=\"/users/2\">Moderator</a></td><td align=\"right\">7</td></tr>", out);
	}

	@Test
	public void miniLeaderBoardRendersThePanelWithoutRowsForAnEmptyList() throws Throwable {
		String out = render("miniLeaderBoard", map("_arg", new ArrayList<PlayerScore>()));
		assertTrue(out.contains("<a href=\"/scores\">This month scores</a>"));
		assertFalse(out.contains("<tr>"));
		assertFalse(out.contains("/users/"));
	}

	// ~~~~~~~~~~~~ miniOpenTTDStatus.html

	@Test
	public void miniOpenTTDStatusListsRevisionDatesAndCounts() throws Throwable {
		// The #{if _arg} path: unreachable through Application/index.html, whose five calls are
		// commented out and whose controller never provides the status variables.
		OpenttdServer server = newServer(RobotConfiguration.ARTIC, "Artic server", "r7000", 512, 1024, 7, 4, 1000, 3650);
		String out = render("miniOpenTTDStatus", map("_arg", server));

		assertTrue(out.contains("<a href=\"/openttd\">OpenTTD server</a>"));
		assertTrue(out.contains("<tr><td>Version</td><td><a href=\"http://www.openttd.org/en/download-stable\">r7000</a></td></tr>"));
		assertTrue(out.contains("<tr><td>Started</td><td>" + day(1000) + "</td></tr>"));
		assertTrue(out.contains("<tr><td>Current</td><td>" + day(3650) + "</td></tr>"));
		assertTrue(out.contains("<tr><td>Companies</td><td>7</td></tr>"));
		assertTrue(out.contains("<tr><td>Clients</td><td>4</td></tr>"));
		assertFalse(out.contains("Server is down :("));
	}

	@Test
	public void miniOpenTTDStatusSaysTheServerIsDownWithoutAStatus() throws Throwable {
		String out = render("miniOpenTTDStatus", map("_arg", null));
		assertTrue(out.contains("<a href=\"/openttd\">OpenTTD server</a>"));
		assertMatches("<tr><td colspan=\"2\">\\s*Server is down :\\(\\s*</td></tr>", out);
		assertFalse(out.contains("Version"));
		assertFalse(out.contains("download-stable"));
	}

	// ~~~~~~~~~~~~ showOpenTTDStatus.html

	@Test
	public void showOpenTTDStatusRendersEveryCellOfARunningServer() throws Throwable {
		OpenttdServer server = newServer(RobotConfiguration.HIGH_SPEED, "High Speed server", "r7002", 800, 600, 9, 8, 1000, 3652);
		String out = render("showOpenTTDStatus", map("_arg", server));

		assertTrue(out.contains("<tr><td align=\"center\">High Speed server</td></tr>"));
		assertTrue(out.contains("<img src=\"/images/openttd/highSpeed_on.gif\" alt=\"mapIcon\">"));
		assertTrue(out.contains("800x600"));
		assertTrue(out.contains("Started: " + day(1000) + ", current: " + day(3652)));
		assertTrue(out.contains("9&nbsp;Companies, 8&nbsp;Clients"));
		assertTrue(out.contains("<tr><td>Reach the best company performance in 10 years to win the game.</td></tr>"));
		assertTrue(out.contains("<a href=\"http://www.openttd.org/en/download-stable\">OpenTTD r7002</a>"));
		assertFalse(out.contains("Server is down :("));
	}

	@Test
	public void showOpenTTDStatusSaysTheServerIsDownWithoutAStatus() throws Throwable {
		String out = render("showOpenTTDStatus", map("_arg", null));
		assertTrue(out.contains("<tr><td>Server is down :(</td></tr>"));
		assertFalse(out.contains("Companies"));
		assertFalse(out.contains("download-stable"));
	}

	// ~~~~~~~~~~~~ showRecentGames.html

	@Test
	public void showRecentGamesShowsTheScenarioIconNameAndWinner() throws Throwable {
		final List<PlayedGame> games = new ArrayList<PlayedGame>();
		games.add(inTransaction(() -> newGameWithScore(HIGH_SPEED, "bob@domain.com", 12, 1, 2)));
		// A game with no score row at all: playedGame.winner is null -> #{else} "Anonymous"
		games.add(inTransaction(() -> newGame(TOYLAND, 3, 4)));

		String out = render("showRecentGames", map("_arg", games));
		assertTrue(out.contains("<th>Since</th>"));
		assertTrue(out.contains("<th>Scenario</th>"));
		assertTrue(out.contains("<th>Won by</th>"));
		assertMatches("<img src=\"/images/openttd/high_speed_train.gif\" alt=\"scenarioTrain\" align=\"middle\" class=\"train_icon\"> HighSpeed Scenario", out);
		assertMatches("<img src=\"/images/openttd/toyland_train.gif\" alt=\"scenarioTrain\" align=\"middle\" class=\"train_icon\"> Toyland Scenario", out);
		assertMatches("<a href=\"/users/3\">Bob</a> with 12 points", out);
		assertMatches("Anonymous\\s*</td>", out);
		assertTrue(out.contains(JavaExtensions.since(daysAgo(2))));
		assertTrue(out.contains(JavaExtensions.since(daysAgo(4))));
		assertEquals(2, count("<tr>", outOf(out, "<tbody>", "</tbody>")));
	}

	@Test
	public void showRecentGamesRendersHeadersOnlyForAnEmptyList() throws Throwable {
		String out = render("showRecentGames", map("_arg", new ArrayList<PlayedGame>()));
		assertTrue(out.contains("<th>Since</th>"));
		assertTrue(out.contains("<th>Scenario</th>"));
		assertTrue(out.contains("<th>Won by</th>"));
		assertEquals("", outOf(out, "<tbody>", "</tbody>").trim());
	}

	// ~~~~~~~~~~~~ showBestGames.html

	@Test
	public void showBestGamesShowsPlayerScoreAndDateOfTheWinningRow() throws Throwable {
		final PlayedGame game = inTransaction(() -> newGameWithScore(ARTIC, "bob@domain.com", 12, 1, 3));
		List<Object[]> rows = new ArrayList<Object[]>();
		// the shape PlayedGame.getBestPlayedGamesForScenarioId() returns: [PlayedGame, max(achivement)]
		rows.add(new Object[] { game, Integer.valueOf(12) });

		String out = render("showBestGames", map("_arg", rows));
		assertTrue(out.contains("<th>Player</th>"));
		assertTrue(out.contains("<th>Score</th>"));
		assertTrue(out.contains("<th>Since</th>"));
		assertMatches("<td>\\s*<a href=\"/users/3\">Bob</a>\\s*</td>", out);
		assertMatches("<td>\\s*12\\s*</td>", out);
		assertTrue(out.contains(JavaExtensions.since(daysAgo(3))));
		assertFalse(out.contains("Anonymous"));
	}

	@Test
	public void showBestGamesShowsAnAnonymousRowAndAnEmptyScoreCell() throws Throwable {
		// The #{else} path: getBestPlayedGamesForScenarioId() selects from PlayedGameScore, so every
		// row it returns has at least one score and PlayedGameScore.getWinner() is never null. This
		// row is built by hand to cover the branch the application cannot produce.
		final PlayedGame scoreless = inTransaction(() -> newGame(DESERTIC, 5, 6));
		List<Object[]> rows = new ArrayList<Object[]>();
		rows.add(new Object[] { scoreless, Integer.valueOf(42) });

		String out = render("showBestGames", map("_arg", rows));
		assertMatches("<td>\\s*Anonymous\\s*</td>", out);
		assertTrue(out.contains("<td></td>"));
		assertFalse(out.contains("42"));
		assertTrue(out.contains(JavaExtensions.since(daysAgo(6))));
	}

	@Test
	public void showBestGamesRendersHeadersOnlyForAnEmptyList() throws Throwable {
		String out = render("showBestGames", map("_arg", new ArrayList<Object[]>()));
		assertTrue(out.contains("<th>Player</th>"));
		assertTrue(out.contains("<th>Score</th>"));
		assertTrue(out.contains("<th>Since</th>"));
		assertEquals("", outOf(out, "<tbody>", "</tbody>").trim());
	}

	// ~~~~~~~~~~~~ showNews.html

	@Test
	public void showNewsShowsDateSubjectContentAndAttachments() throws Throwable {
		Post post = newsPost();
		String out = render("showNews", map("_arg", post));
		assertTrue(out.contains("<span class=\"pull-right\">"
				+ JavaExtensions.format(post.postedAt, "MM/dd, yyyy hh:mma") + "</span>"));
		assertTrue(out.contains("News with attachments"));
		// content.nl2br().raw(): nl2br escapes the markup first, then raw() stops the template
		// from escaping the result again, so the newline becomes a real break and <em> stays escaped
		assertTrue(out.contains("First line<br/>Second line"));
		assertTrue(out.contains("&lt;em&gt;raw markup&lt;/em&gt;"));
		assertFalse(out.contains("<em>raw markup</em>"));
		// nested #{attachments} with rows
		assertTrue(out.contains("<div class=\"well\">"));
		assertMatches("<a href=\"[^\"]*\">report\\.txt</a>", out);
	}

	@Test
	public void showNewsOmitsTheAttachmentBlockForAPostWithoutAttachments() throws Throwable {
		String out = render("showNews", map("_arg", plainPost()));
		assertTrue(out.contains("Plain news"));
		assertFalse(out.contains("<div class=\"well\">"));
		assertFalse(out.contains("glyphicon glyphicon-file"));
		assertFalse(out.contains("/attachments/"));
	}

	// ~~~~~~~~~~~~ showPost.html

	@Test
	public void showPostOmitsTheTopicLinkAndPluralizesZeroOrOne() throws Throwable {
		// the way forum/Topics/show.html calls it: #{showPost post /}, over a post with one reply
		Post first = inTransaction(() -> Post.findById(1L));
		String out = render("showPost", map("_arg", first));
		assertTrue(out.contains("<span class=\"pull-right\">"
				+ JavaExtensions.format(first.postedAt, "MM/dd, yyyy hh:mma") + "</span>"));
		assertMatches("<a href=\"/users/3\">Bob</a>\\s*1 post", out);
		assertFalse(out.contains("Posted in"));
		assertFalse(out.contains("<div class=\"well\">"));

		// pluralize() on the poster's post count: a second post by the same user takes the "s"
		Post second = inTransaction(() -> seedReplyPostBy("bob@domain.com"));
		assertMatches("<a href=\"/users/3\">Bob</a>\\s*2 posts", render("showPost", map("_arg", second)));
	}

	@Test
	public void showPostLinksTheTopicOfTheCallerAndShowsItsOwnAttachments() throws Throwable {
		// the way forum/Users/show.html calls it: #{showPost arg:post, showTopic:true /} inside
		// #{list items:user.recentsPosts, as:'post'}, so _caller.post is the loop variable
		Post reply = inTransaction(() -> seedReplyPost());
		Map<String, Object> args = map("_arg", reply, "_showTopic", Boolean.TRUE, "_caller", caller("post", reply));
		String out = render("showPost", args);

		assertMatches("<a href=\"/users/2\">Moderator</a>\\s*4 posts", out);
		assertMatches("Posted in <a href=\"/forums/1/topics/1\">mainForum1 - Reply with attachments</a>", out);
		assertTrue(out.contains("First line<br/>Second line &lt;em&gt;x&lt;/em&gt;"));
		assertTrue(out.contains("<div class=\"well\">"));
		assertMatches("<a href=\"[^\"]*\">report\\.txt</a>:\\s*Attachment report,\\s*2 downloads\\.", out);
	}

	@Test
	public void showPostShowsTheAttachmentBlockOfItsOwnPostWithoutATopicLink() throws Throwable {
		Post reply = inTransaction(() -> seedReplyPost());
		String out = render("showPost", map("_arg", reply));
		assertTrue(out.contains("<div class=\"well\">"));
		assertFalse(out.contains("Posted in"));
	}

	// ~~~~~~~~~~~~ entity helpers

	/** Three attachments on post 1 covering both inner conditions and both plural forms. */
	private List<Attachment> seedAttachmentsOnPost1() {
		Post post = Post.findById(1L);
		User poster = User.findByEmail("bob@domain.com");
		List<Attachment> rows = new ArrayList<Attachment>();
		rows.add(newAttachment(post, poster, "report.txt", "Attachment report", 2));
		rows.add(newAttachment(post, poster, "chart.png", null, 0));
		rows.add(newAttachment(post, poster, "single.txt", null, 1));
		return rows;
	}

	/** Gives a fixture user the uuid an account gets after registration or login. */
	private User seedUuid(String email) {
		User user = User.findByEmail(email);
		user.uuid = "abc-123";
		user.save();
		return user;
	}

	/** A reply on topic 1 by the given author, without attachments. */
	private Post seedReplyPostBy(String email) {
		Post post = new Post();
		post.topic = Topic.findById(1L);
		post.postedBy = User.findByEmail(email);
		post.subject = "Reply without attachments";
		post.content = "A reply.";
		post.postedAt = daysAgo(4);
		post.create();
		return post;
	}

	/** A reply post with a two-line content and one attachment. */
	private Post seedReplyPost() {
		Post post = new Post();
		post.topic = Topic.findById(1L);
		post.postedBy = User.findByEmail("modo@domain.com");
		post.subject = "Reply with attachments";
		post.content = "First line\nSecond line <em>x</em>";
		post.postedAt = daysAgo(3);
		post.create();
		newAttachment(post, post.postedBy, "report.txt", "Attachment report", 2);
		return post;
	}

	private Attachment newAttachment(Post post, User user, String filename, String description, int downloads) {
		Attachment attachment = new Attachment();
		attachment.post = post;
		attachment.user = user;
		attachment.filename = filename;
		attachment.realfilename = filename;
		attachment.mimetype = filename.endsWith(".png") ? "image/png" : "text/plain";
		attachment.description = description;
		attachment.downloads = downloads;
		attachment.size = 12;
		attachment.at = daysAgo(2);
		attachment.create();
		return attachment;
	}

	/** A news post with attachments, built here so the fixtures stay untouched by other suites. */
	private Post newsPost() throws Throwable {
		return inTransaction(() -> {
			Topic topic = new Topic();
			topic.forum = models.forum.Forum.findById(5L); // "Home Page", the news forum
			topic.views = 0;
			topic.create();

			Post post = new Post();
			post.topic = topic;
			post.postedBy = User.findByEmail("admin@domain.com");
			post.subject = "News with attachments";
			post.content = "First line\nSecond line <em>raw markup</em>";
			post.postedAt = daysAgo(7);
			post.create();

			newAttachment(post, post.postedBy, "report.txt", "Attachment report", 2);
			return post;
		});
	}

	/** A post with no attachment and a content of two lines. */
	private Post plainPost() throws Throwable {
		return inTransaction(() -> {
			Topic topic = Topic.findById(1L);
			Post post = new Post();
			post.topic = topic;
			post.postedBy = User.findByEmail("bob@domain.com");
			post.subject = "Plain news";
			post.content = "First line\nSecond line";
			post.postedAt = daysAgo(8);
			post.create();
			return post;
		});
	}

	private PlayedGame newGame(long scenarioId, int beginDaysAgo, int endDaysAgo) {
		PlayedGame game = new PlayedGame();
		game.scenario = Scenario.findById(scenarioId);
		assertTrue("scenario " + scenarioId + " missing", game.scenario != null);
		game.begin = daysAgo(beginDaysAgo);
		game.end = daysAgo(endDaysAgo);
		game.create();
		return game;
	}

	private PlayedGame newGameWithScore(long scenarioId, String email, double achivement, double presenceRatio,
			int endDaysAgo) {
		PlayedGame game = newGame(scenarioId, endDaysAgo + 10, endDaysAgo);
		PlayedGameScore score = new PlayedGameScore();
		score.playedGame = game;
		score.user = User.findByEmail(email);
		score.achivement = achivement;
		score.presenceRatio = presenceRatio;
		score.create();
		return game;
	}

	private static PlayerScore playerScore(int index, User player, int points, Long gameCount) {
		PlayerScore score = new PlayerScore();
		score.index = index;
		score.player = player;
		score.points = points;
		score.gameCount = gameCount;
		return score;
	}

	/**
	 * An OpenttdServer of a running server. The constructor is private and getStatus() reads the
	 * ExternalServices singleton wired by OpenttdAdminHandler, which opens admin sockets, so the
	 * private constructor is invoked by reflection instead.
	 */
	private static OpenttdServer newServer(RobotConfiguration configuration, String name, String revision,
			int width, int height, int companies, int clients, long startDay, long currentDay) throws Throwable {
		GameInfo gameInfo = new GameInfo();
		gameInfo.setServerName(name);
		gameInfo.setServerRevision(revision);
		gameInfo.setMapWidth(width);
		gameInfo.setMapHeight(height);
		gameInfo.setStartDate(startDay);
		gameInfo.setCurrentDate(currentDay);
		gameInfo.companiesCount = companies;
		gameInfo.clientsCount = clients;
		final Scenario scenario = inTransaction(() -> Scenario.findById(configuration.scenarioId));
		Constructor<OpenttdServer> constructor = OpenttdServer.class.getDeclaredConstructor(GameInfo.class, Scenario.class);
		constructor.setAccessible(true);
		return constructor.newInstance(gameInfo, scenario);
	}

	// ~~~~~~~~~~~~ rendering helpers

	/** Renders app/views/tags/<tag>.html directly, inside a JPA transaction and a request context. */
	private static String render(final String tag, final Map<String, Object> args) throws Throwable {
		Http.Request.current.set(request("/openttd"));
		try {
			return inTransaction(() -> TemplateLoader.load("tags/" + tag + ".html").render(args));
		} finally {
			Http.Request.current.remove();
		}
	}

	private static <T> T inTransaction(final ThrowableSupplier<T> work) throws Throwable {
		return JPA.withTransaction(JPA.DEFAULT, false, () -> work.get());
	}

	private interface ThrowableSupplier<T> {
		T get() throws Throwable;
	}

	private static Map<String, Object> map(Object... entries) {
		Map<String, Object> args = new HashMap<String, Object>();
		for (int i = 0; i < entries.length; i += 2) {
			args.put((String) entries[i], entries[i + 1]);
		}
		return args;
	}

	/** The caller template's binding variables, as TagContext passes them to a tag as _caller. */
	private static Map<String, Object> caller(Object... entries) {
		return map(entries);
	}

	/** A request whose path pagination.html writes into its links with ${request.path}. */
	private static Http.Request request(String path) {
		Http.Request request = new Http.Request();
		request.path = path;
		request.method = "GET";
		return request;
	}

	// ~~~~~~~~~~~~ assertions

	private static String day(long openttdDay) {
		return new java.text.SimpleDateFormat("dd/MM/yyyy")
				.format(com.openttd.util.Convert.dayToCalendar(openttdDay).getTime());
	}

	private static Date daysAgo(int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_MONTH, -days);
		return calendar.getTime();
	}

	private static String outOf(String body, String start, String end) {
		int from = body.indexOf(start);
		assertTrue("expected '" + start + "'", from >= 0);
		int to = body.indexOf(end, from + start.length());
		assertTrue("expected '" + end + "'", to >= 0);
		return body.substring(from + start.length(), to);
	}

	private static int count(String needle, String body) {
		int count = 0;
		for (int i = body.indexOf(needle); i >= 0; i = body.indexOf(needle, i + needle.length())) {
			count++;
		}
		return count;
	}

	private static void assertMatches(String regex, String text) {
		assertTrue("expected to match '" + regex + "'", Pattern.compile(regex).matcher(text).find());
	}
}
