import java.util.HashMap;

import org.junit.Test;

import play.libs.Crypto;
import play.mvc.Http;
import play.mvc.Http.Response;

import models.forum.User;

/**
 * Coverage of controllers.Secure.login() and controllers.Secure.logout() (modules/secure), as driven by
 * GET /login and GET /logout of conf/routes, together with the three hooks of controllers.Profile.Security:
 * authenticate(), onAuthenticated() and onDisconnect().
 *
 * Branches pinned below:
 *   - Secure.authenticate() writes the remember-me cookie only when the remember parameter is ticked:
 *     value Crypto.sign(username + "-" + expiration) + "-" + username + "-" + expiration, maxAge 2592000
 *     (%test's secure.rememberme.duration of 30d parsed by play.libs.Time), path "/", domain null.
 *     Profile.Security.onAuthenticated() then gives the row a four-character uuid.
 *   - Secure.login() is the only reader of that cookie, so a cookie is only honored on GET /login:
 *     the signature check runs on the part after the first dash, and a matching one puts username in the
 *     session and redirects to the url kept in the flash (which is absent here, so it becomes "/").
 *     The response carries no rewritten rememberme cookie, and Profile.Security.authenticate() never runs,
 *     so there is no "Welcome back Bob !" flash.
 *   - An expired cookie calls logout(), whose nested Application.index(null) call aborts the action: the
 *     module's own flash.success("secure.logout") and its login() are never reached, and the answer is
 *     302 /logout carrying no flash.
 *   - A cookie whose signature does not verify, or whose dashes are missing (lastIndex > firstIndex is
 *     false), falls through to flash.keep("url") + render(), i.e. the login page with a null session.
 *   - Long.parseLong(time) is not guarded: a malformed expiration escapes as a NumberFormatException, which
 *     is a 500 in production.
 *   - Secure.logout() clears the session, removes the cookie (value "", maxAge 0, path "/", domain null),
 *     calls Profile.Security.onDisconnect() (uuid back to null) and then Profile.Security.onDisconnected(),
 *     whose flash.success("You've been logged out") is what the next page prints. A logout nobody performed
 *     behaves the same, because onDisconnect() skips the row when nobody is connected.
 *
 * The login POST leaves a "Welcome back Bob !" flash that the next rendered page consumes, so cases that
 * want a clean page fetch one first, and every case starts with clearCookies() because
 * play.test.FunctionalTest.savedCookies is a static field.
 */
public class LogoutFlowTest extends BaseForumViewTest {

	private static final String LOGGED_OUT_FLASH = "You've been logged out";

	private static final String LOGIN_PAGE_HEADER = "<div class=\"page-header\"> <h1>Login</h1> "
			+ "<p>Please enter you email address and password.</p> </div>";

	private static final String LOGIN_FORM = "<form action=\"/login\" method=\"post\" accept-charset=\"utf-8\" "
			+ "enctype=\"application/x-www-form-urlencoded\" >";

	private static final String NEWS_PAGE_HEADER = "<div class=\"page-header\"> <h1>News</h1> <p></p> </div>";

	/** A request carrying one rememberme cookie, as a browser would send it. */
	private static Http.Request requestWithCookie(String remembermeValue) {
		Http.Request request = newRequest();
		request.cookies = new HashMap<String, Http.Cookie>();
		Http.Cookie cookie = new Http.Cookie();
		cookie.name = "rememberme";
		cookie.value = remembermeValue;
		request.cookies.put("rememberme", cookie);
		return request;
	}

	/** The value Secure.authenticate() writes: Crypto.sign over "username-<expiration time millis>". */
	private static String cookieValue(String username, long expiration) {
		return Crypto.sign(username + "-" + expiration) + "-" + username + "-" + expiration;
	}

	// ~~~~~~~~~~~~ Secure.authenticate() with remember ticked

	@Test
	public void loginWithRememberWritesASignedCookieAndANewUuid() throws Throwable {
		clearCookies();
		assertEquals(null, inTransaction(() -> User.findByEmail("bob@domain.com")).uuid);

		Response response = POST("/login", form("username", "bob@domain.com", "password", "secret", "remember",
				"true"));
		assertStatus(302, response);
		assertLocation(response, "/");
		assertFlashValue(response, "success", "Welcome back Bob !");
		assertEquals("bob@domain.com", sessionUsername(response));

		Http.Cookie remember = cookie(response, "rememberme");
		assertNotNull("expected a rememberme cookie", remember);
		// <sign>-<username>-<expiration>, with the expiration 30d ahead of now
		assertTrue("unexpected cookie value: " + remember.value,
				remember.value.matches("[0-9a-f]{40}-bob@domain.com-[0-9]{13}"));
		assertEquals(Integer.valueOf(2592000), remember.maxAge);
		assertEquals("/", remember.path);
		assertNull(remember.domain);
		// what Secure.login() compares: Crypto.sign over everything after the first dash
		int firstDash = remember.value.indexOf('-');
		int lastDash = remember.value.lastIndexOf('-');
		assertEquals(Crypto.sign(remember.value.substring(firstDash + 1)), remember.value.substring(0, firstDash));
		assertEquals("bob@domain.com", remember.value.substring(firstDash + 1, lastDash));

		// Profile.Security.onAuthenticated() writes a four-character uuid that no other row holds
		User bob = inTransaction(() -> User.findByEmail("bob@domain.com"));
		assertTrue("unexpected uuid: " + bob.uuid, bob.uuid.matches("[0-9a-zA-Z]{4}"));
	}

	@Test
	public void loginWithoutRememberLeavesNoCookie() throws Throwable {
		clearCookies();
		Response response = POST("/login", form("username", "bob@domain.com", "password", "secret"));
		assertStatus(302, response);
		assertLocation(response, "/");
		assertNull(cookie(response, "rememberme"));
		assertEquals("bob@domain.com", sessionUsername(response));

		User bob = inTransaction(() -> User.findByEmail("bob@domain.com"));
		assertTrue("unexpected uuid: " + bob.uuid, bob.uuid.matches("[0-9a-zA-Z]{4}"));
	}

	// ~~~~~~~~~~~~ Secure.logout()

	@Test
	public void logoutClearsTheSessionAndExpiresTheRememberCookie() throws Throwable {
		clearCookies();
		Response signedIn = POST("/login", form("username", "bob@domain.com", "password", "secret", "remember",
				"true"));
		assertNotNull("expected a rememberme cookie from the login", cookie(signedIn, "rememberme"));

		Response response = GET("/logout");
		assertStatus(302, response);
		assertLocation(response, "/");
		// Profile.Security.onDisconnected() writes this flash; the module's secure.logout never runs
		assertFlashValue(response, "success", LOGGED_OUT_FLASH);
		assertNull(sessionUsername(response));

		Http.Cookie removed = cookie(response, "rememberme");
		assertNotNull("the cookie must be present to be expired", removed);
		assertEquals("", removed.value);
		assertEquals(Integer.valueOf(0), removed.maxAge);
		assertEquals("/", removed.path);
		assertNull(removed.domain);

		// Profile.Security.onDisconnect() nulls the uuid of the row that was connected
		assertEquals(null, inTransaction(() -> User.findByEmail("bob@domain.com")).uuid);
	}

	/**
	 * Secure.logout() calls onDisconnected(), which calls Application.index(null). A controller method
	 * invoked from another one answers with a Result, and ActionInvoker hands that Result out, so the
	 * logout action stops before flash.success("secure.logout") and before its own login().
	 */
	@Test
	public void theLoggedOutFlashIsPrintedOnTheNewsPageOfTheNestedIndexCall() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		// the welcome flash of the login POST is consumed here, leaving a page with no flash at all
		assertEmptyMap(flashOf(GET("/")));
		assertFlashValue(GET("/logout"), "success", LOGGED_OUT_FLASH);

		String body = markup(GET("/"));
		assertContains(NEWS_PAGE_HEADER, body);
		assertSuccessAlert(LOGGED_OUT_FLASH, body);
		assertNull(sessionUsername(GET("/")));
	}

	@Test
	public void theLoginPageRenderedAfterLogoutStillCarriesTheLoggedOutFlash() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		assertFlashValue(GET("/logout"), "success", LOGGED_OUT_FLASH);

		// the flash is consumed by this one request, so the page must be fetched exactly once
		Response response = GET("/login");
		assertStatus(200, response);
		String body = markup(response);
		assertContains(LOGIN_PAGE_HEADER, body);
		assertContains(LOGIN_FORM, body);
		assertSuccessAlert(LOGGED_OUT_FLASH, body);
	}

	@Test
	public void aLogoutNobodyPerformedDoesTheSameThing() throws Throwable {
		clearCookies();
		Response response = GET("/logout");
		assertStatus(302, response);
		assertLocation(response, "/");
		assertFlashValue(response, "success", LOGGED_OUT_FLASH);
		assertNull(sessionUsername(response));

		Http.Cookie removed = cookie(response, "rememberme");
		assertNotNull(removed);
		assertEquals("", removed.value);
		assertEquals(Integer.valueOf(0), removed.maxAge);
		assertEquals("/", removed.path);
		assertNull(removed.domain);

		String body = markup(GET("/"));
		assertSuccessAlert(LOGGED_OUT_FLASH, body);
		assertContains(NEWS_PAGE_HEADER, body);
		assertNull(sessionUsername(GET("/")));
	}

	// ~~~~~~~~~~~~ Secure.login() reads the remember-me cookie

	/** A cookie that is still inside its expiration and correctly signed logs the visitor back in. */
	@Test
	public void aValidRememberCookieLogsTheVisitorInThroughTheLoginPage() throws Throwable {
		clearCookies();
		Response response = GET(requestWithCookie(cookieValue("bob@domain.com",
				System.currentTimeMillis() + 60000)), "/login");
		assertStatus(302, response);
		assertLocation(response, "/");
		assertEquals("bob@domain.com", sessionUsername(response));
		// redirectToOriginalURL() has no url in the flash, so it sends the visitor to "/"
		assertEmptyMap(flashOf(response));
		assertNull(cookie(response, "rememberme"));

		String body = markup(GET("/"));
		assertContains(NEWS_PAGE_HEADER, body);
		// Profile.Security.authenticate() never runs, so no welcome flash reaches this page
		assertNotContains("<div class=\"alert", body);
	}

	/** onAuthenticated() runs on the remember-me path too, so the uuid is replaced without a password. */
	@Test
	public void theRememberCookieAlsoReplacesTheUuidOfTheRow() throws Throwable {
		clearCookies();
		GET(requestWithCookie(cookieValue("bob@domain.com", System.currentTimeMillis() + 60000)), "/login");

		User bob = inTransaction(() -> User.findByEmail("bob@domain.com"));
		assertTrue("unexpected uuid: " + bob.uuid, bob.uuid.matches("[0-9a-zA-Z]{4}"));
		assertEquals("bob@domain.com", sessionUsername(GET("/")));
	}

	/** An expired cookie calls logout() before the signature is looked at, and aborts through logout(). */
	@Test
	public void anExpiredRememberCookieIsRejectedThroughALogout() throws Throwable {
		clearCookies();
		Response response = GET(requestWithCookie(cookieValue("bob@domain.com",
				System.currentTimeMillis() - 1000)), "/login");
		assertStatus(302, response);
		assertLocation(response, "/logout");
		assertNull(sessionUsername(response));
		assertEmptyMap(flashOf(response));

		Response logout = GET("/logout");
		assertStatus(302, logout);
		assertLocation(logout, "/");
		assertFlashValue(logout, "success", LOGGED_OUT_FLASH);

		assertSuccessAlert(LOGGED_OUT_FLASH, markup(GET("/")));
	}

	@Test
	public void aCookieWithAnUnverifiedSignatureLeavesTheLoginPageRendered() throws Throwable {
		clearCookies();
		Response response = GET(requestWithCookie("deadbeef-bob@domain.com-"
				+ (System.currentTimeMillis() + 60000)), "/login");
		assertStatus(200, response);
		assertNull(sessionUsername(response));

		String body = markup(response);
		assertContains(LOGIN_PAGE_HEADER, body);
		assertContains(LOGIN_FORM, body);
		assertNotContains("<div class=\"alert", body);
	}

	/** A cookie without two dashes never enters the parsing block: lastIndex > firstIndex is false. */
	@Test
	public void aCookieWithoutAnyDashLeavesTheLoginPageRendered() throws Throwable {
		clearCookies();
		Response response = GET(requestWithCookie("bob@domain.com"), "/login");
		assertStatus(200, response);
		assertNull(sessionUsername(response));

		String body = markup(response);
		assertContains(LOGIN_PAGE_HEADER, body);
		assertContains(LOGIN_FORM, body);
	}

	/**
	 * Long.parseLong(time) is unguarded, so a cookie whose expiration is not a number fails the whole
	 * request. Secure.login() never reaches the signature comparison, and in production this is a 500.
	 */
	@Test
	public void aCookieWithAMalformedExpirationFailsWithANumberFormatException() throws Throwable {
		clearCookies();
		long future = 2000000000000L;
		String malformed = cookieValue("bob@domain.com", future) + "x-bob@domain.com-" + future + "x";

		try {
			GET(requestWithCookie(malformed), "/login");
			fail("expected Long.parseLong to reject the malformed expiration");
		} catch (Throwable t) {
			assertEquals("java.lang.NumberFormatException", t.getClass().getName());
			assertEquals("For input string: \"2000000000000x\"", t.getMessage());
		}
	}
}
