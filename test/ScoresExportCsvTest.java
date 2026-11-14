import org.junit.Test;

import play.mvc.Http.Response;

import models.leaderboard.PlayedGameScore;

/**
 * Coverage of controllers.openttd.Scores.exportAsCSV() and of the two routes that reach it.
 *
 * conf/routes declares GET /scores/{period} above GET /scores/exportCSV, so the export path is matched by
 * the period route first: openttd.Scores.show("exportCSV", null) normalizes the unknown period to
 * last_month and renders the leaderboard page instead of a CSV. Only the catch-all
 * `* /{controller}/{action}` route reaches the action itself, as GET /openttd.scores/exportAsCSV.
 *
 * Branches pinned below:
 *   - @Check(Profile.MODERATOR): anonymous visitors get Secure.checkAccess()'s redirect with the export
 *     url kept in the flash, Bob (replyer) gets app/views/errors/403.html, and the moderator group and the
 *     administrator pass Profile.Security.check() because an administrator is accepted by every profile.
 *   - response.contentType = "text/csv" is set before the loop, so a moderator always gets that content
 *     type even when nothing is written: with an empty PlayedGameScore table hasMoreData() is false and the
 *     body stays empty, which is why the dump shows a 200 with no Content-Length and no bytes.
 *   - the loop body calls await(generator.nextDataChunk()) on a FutureTask nothing ever starts
 *     (app/tasks/ExportScoresCsvGenerator.java, see ExportScoresCsvGeneratorTest), so any row in the table
 *     would block the request forever: the empty answer asserted here is the only answer this action can
 *     produce, whatever the data holds.
 *   - GET /openttd.scores/exportCSV resolves through the catch-all route to a controller that has no such
 *     action, so play.mvc.ActionInvoker.resolve() raises play.exceptions.ActionNotFoundException
 *     ("openttd.scores.exportCSV action not found") before any action body runs. It is not asserted here:
 *     the exception escapes from play.Invoker.Invocation.init(), so the JPA transaction the invocation
 *     started is never unwound and one Hikari connection leaks per such request, which starves the pool for
 *     every suite FirePhoque runs afterwards.
 *
 * ScoresPageTest already covers GET /scores, /scores/{period} and the page markup, so only the export
 * paths are exercised here.
 */
public class ScoresExportCsvTest extends BaseForumViewTest {

	private static final String EXPORT_PATH = "/openttd.scores/exportAsCSV";

	private static void assertScoresTableIsEmpty() throws Throwable {
		assertEquals(Long.valueOf(0), inTransaction(() -> PlayedGameScore.count()));
	}

	// ~~~~~~~~~~~~ GET /openttd.scores/exportAsCSV

	@Test
	public void exportAsCsvAnswersAnEmptyTextCsvBodyToAModerator() throws Throwable {
		assertScoresTableIsEmpty();

		clearCookies();
		login("modo@domain.com", "secret");
		Response response = GET(EXPORT_PATH);
		assertStatus(200, response);
		assertEquals("text/csv", response.contentType);
		assertEquals("modo@domain.com", sessionUsername(response));

		// the loop never runs, so no chunk is written and no header is added
		assertEquals(0, rawBody(response).length);
		assertTrue("expected no response header, got " + response.headers, response.headers.isEmpty());
		assertNull(header(response, "Location"));
	}

	@Test
	public void exportAsCsvAnswersTheSameEmptyBodyToTheAdministrator() throws Throwable {
		assertScoresTableIsEmpty();

		clearCookies();
		login("admin@domain.com", "secret");
		Response response = GET(EXPORT_PATH);
		assertStatus(200, response);
		assertEquals("text/csv", response.contentType);
		assertEquals(0, rawBody(response).length);
		assertNull(cookie(response, "rememberme"));
	}

	@Test
	public void exportAsCsvIsForbiddenForAReplyer() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET(EXPORT_PATH);
		assertStatus(403, response);
		assertEquals("text/html; charset=utf-8", response.contentType);
		assertEmptyMap(flashOf(response));

		String body = markup(response);
		assertForbiddenBody(body);
		// errors/403.html is the app template, so the flash login() left is rendered as an alert
		assertSuccessAlert("Welcome back Bob !", body);
		assertNotContains("text/csv", body);
	}

	@Test
	public void anonymousIsRedirectedToLoginAndTheExportUrlIsKept() throws Throwable {
		clearCookies();
		Response response = GET(EXPORT_PATH);
		assertStatus(302, response);
		assertLocation(response, "/login");
		// Secure.checkAccess() keeps request.url for a GET, which Secure.login() redirects back to
		assertFlashValue(response, "url", EXPORT_PATH);
		assertNull(sessionUsername(response));
	}

	// ~~~~~~~~~~~~ GET /scores/exportCSV is taken by the period route

	/** Scores.show() normalizes the period "exportCSV" to last_month and answers the leaderboard page. */
	@Test
	public void theExportCsvRouteIsShadowedByThePeriodRoute() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");
		Response response = GET("/scores/exportCSV");
		assertStatus(200, response);
		assertEquals("text/html; charset=utf-8", response.contentType);

		String body = markup(response);
		assertContains("<title>Strategyboard - Scores</title>", body);
		assertContains("<div class=\"page-header\"> <h1>Scores</h1> <p>0 scores</p> </div>", body);
		// the button the template offers to moderators points at the route that never reaches the action
		assertContains("<a class=\"btn btn-danger\" href=\"/scores/exportCSV\">Export as CSV</a>", body);
		assertNotContains("Content-Disposition", body);
	}

	@Test
	public void anAnonymousVisitorSeesTheSameLeaderboardPageThroughThePeriodRoute() throws Throwable {
		clearCookies();
		Response response = GET("/scores/exportCSV");
		assertStatus(200, response);

		String body = markup(response);
		assertContains("<div class=\"page-header\"> <h1>Scores</h1> <p>0 scores</p> </div>", body);
		// #{secure check 'moderator'} is the false branch, as the anonymous session of scores-nonsense.txt shows
		assertNotContains("Export as CSV", body);
	}
}
