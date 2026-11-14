import java.util.Map;

import org.junit.*;

import play.Play;
import play.mvc.Http.Response;

/**
 * Coverage of app/views/errors/403.html, app/views/errors/404.html and app/views/errors/500.html.
 *
 *   - errors/403.html: #{if connected} reads a name nothing ever binds. play.mvc.Controller injects
 *     session/flash/params/request/renderArgs and play.mvc/results/Error.java adds exception/result, but
 *     not connected: Profile.Security.connected() is a Security hook, not a renderArg.
 *     GroovyTemplate.ExecutableTemplate.getProperty() answers unknown names with null, so the true
 *     branch is dead code in production and is covered here by a direct render that binds it.
 *   - errors/500.html: the DEV branch #{500 exception /} and the PROD branch are mutually exclusive,
 *     and auto-test runs in DEV, so the PROD branch needs Play.mode switched. It is a public static
 *     field, and every case restores it before it returns.
 *   - errors/404.html is reached by a real request: forum.Users.confirmRegistration calls
 *     notFoundIfNull() for an unknown uuid.
 *
 * Both sides of exception instanceof play.exceptions.PlayException are exercised in PROD: one render
 * with a PlayException (UnexpectedException, which has an id) writes the logged-id paragraph, one with
 * a plain RuntimeException writes nothing but the heading.
 */
public class ErrorPagesTest extends BaseForumViewTest {

	private static final String FORBIDDEN_HEADER = "<div class=\"page-header\"> <h1>Forbidden</h1> "
			+ "<p>You don't have the permission to access this page.</p> </div>";

	private static final String ERROR_TITLE = "<title>Application error</title>";

	private static Play.Mode originalMode;

	private static void setMode(Play.Mode mode) {
		if (originalMode == null) {
			originalMode = Play.mode;
		}
		Play.mode = mode;
	}

	private static void restoreMode() {
		if (originalMode != null) {
			Play.mode = originalMode;
			originalMode = null;
		}
	}

	// ~~~~~~~~~~~~ errors/403.html

	@Test
	public void theForbiddenPageOfAnAnonymousVisitorOnlyInvitesItToLogin() throws Throwable {
		Map<String, Object> args = viewArgs("anonymous@domain.com", "/admin");
		String body = renderView("errors/403.html", args);

		assertContains(FORBIDDEN_HEADER, body);
		assertContains("<p> Please <a href=\"/login\">login</a>. </p>", body);
		assertNotContains("as administrator", body);
		// A direct render binds connectedUser to the account itself, so main.html shows the user
		// dropdown; the Login/Signup items only appear on a real request, where
		// Profile.Security.connected() answers null for the anonymous address (see Html5PagesTest)
		assertContains("<li class=\"dropdown\"> <a href=\"#\" class=\"dropdown-toggle\" data-toggle=\"dropdown\">Anonymous <b class=\"caret\"></b></a>", body);
		assertNotContains("<li><a href=\"/signup\">Signup</a></li>", body);
		assertNotContains("<li><a href=\"/admin\">Administration</a></li>", body);
	}

	@Test
	public void theForbiddenPageOfAConnectedVisitorInvitesItToLoginAsAdministrator() throws Throwable {
		Map<String, Object> args = viewArgs("bob@domain.com", "/admin");
		// The name the framework never binds; @{Secure.login} still reverses to /login
		args.put("connected", new TemplateStandIns.Connected());
		String body = renderView("errors/403.html", args);

		assertContains(FORBIDDEN_HEADER, body);
		assertContains("<p> Please <a href=\"/login\">login</a> as administrator. </p>", body);
		assertNotContains("<li><a href=\"/signup\">Signup</a></li>", body);
	}

	@Test
	public void aRealForbiddenResponseRendersTheAnonymousBranch() throws Throwable {
		// A logged-in non-administrator hits an administrator-only CRUD page: Secure.checkAccess() finds
		// the @Check(Profile.ADMINISTRATOR) annotation, Profile.Security.check() refuses it and
		// onCheckFailed() calls forbidden(), which renders errors/403.html
		clearCookies();
		login("modo@domain.com", "secret");
		Response response = GET("/admin/adminforums");
		assertStatus(403, response);
		String body = markup(response);

		assertContains(FORBIDDEN_HEADER, body);
		// connected is not bound by the framework, so the #{else} branch is the one a real request gets
		assertContains("<p> Please <a href=\"/login\">login</a>. </p>", body);
		assertNotContains("as administrator", body);
		// the flash of the login that just happened is still on the page
		assertSuccessAlert("Welcome back Moderator !", body);
	}

	// ~~~~~~~~~~~~ errors/500.html

	@Test
	public void theDevErrorPageShowsTheExceptionDetails() throws Throwable {
		Map<String, Object> args = viewArgs("anonymous@domain.com", "/boom");
		args.put("exception", new play.exceptions.UnexpectedException("Boom in dev"));

		String body = renderView("errors/500.html", args);
		assertContains(ERROR_TITLE, body);
		// the #{500 exception /} tag of the framework writes the header block and the logged id
		assertContains("<div id=\"header\" class=\"block\"> <h1> Unexpected error </h1> "
				+ "<p> Unexpected error : Boom in dev </p> </div>", body);
		assertMatches("<div id=\"more\" class=\"block\"> This exception has been logged with id <strong>[^<]+</strong> </div>", body);
		assertNotContains("Oops, an error occured", body);
	}

	@Test
	public void theProdErrorPageLogsTheIdOfAPlayException() throws Throwable {
		Map<String, Object> args = viewArgs("anonymous@domain.com", "/boom");
		args.put("exception", new play.exceptions.UnexpectedException("Boom in prod"));

		setMode(Play.Mode.PROD);
		try {
			String body = renderView("errors/500.html", args);
			assertContains(ERROR_TITLE, body);
			assertContains("<h1>Oops, an error occured</h1>", body);
			assertMatches("<p> This exception has been logged with id <strong>[^<]+</strong>. </p>", body);
			assertNotContains("Unexpected error", body);
		} finally {
			restoreMode();
		}
	}

	@Test
	public void theProdErrorPageShowsNoIdForAnExceptionThatIsNotAPlayException() throws Throwable {
		Map<String, Object> args = viewArgs("anonymous@domain.com", "/boom");
		args.put("exception", new RuntimeException("Boom in prod"));

		setMode(Play.Mode.PROD);
		try {
			String body = renderView("errors/500.html", args);
			assertContains(ERROR_TITLE, body);
			assertContains("<h1>Oops, an error occured</h1>", body);
			assertNotContains("has been logged with id", body);
			assertNotContains("Boom in prod", body);
		} finally {
			restoreMode();
		}
	}

	@Test
	public void theDevErrorPageIsRenderedAgainAfterRestoringTheMode() throws Throwable {
		// The two PROD renders above must not leave Play.mode changed for the suites that follow
		Map<String, Object> args = viewArgs("anonymous@domain.com", "/boom");
		args.put("exception", new play.exceptions.UnexpectedException("Boom again"));

		String body = renderView("errors/500.html", args);
		assertContains("<div id=\"header\" class=\"block\"> <h1> Unexpected error </h1> "
				+ "<p> Unexpected error : Boom again </p> </div>", body);
		assertNotContains("Oops, an error occured", body);
	}

	// ~~~~~~~~~~~~ errors/404.html

	@Test
	public void anUnknownUuidNotFoundsTheRegistrationPage() throws Throwable {
		clearCookies();
		Response response = GET("/signup/noSuchUuid");
		assertStatus(404, response);
		String body = markup(response);

		// forum.Users.confirmRegistration calls notFoundIfNull(), which renders errors/404.html: its
		// title is Forbidden but its subtitle is its own sentence, not the one of errors/403.html
		assertHeader(body, "Forbidden", "The forum, topic, or post you search does not exist !");
		assertContains("<p> Please go back to the <a href=\"/\">home page</a>. </p>", body);
	}
}
