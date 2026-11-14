import java.util.Map;

import org.junit.*;

import play.mvc.Http.Response;

import models.forum.User;

/**
 * Coverage of app/views/Secure/login.html, the template the secure module renders after a failed
 * POST /login and for GET /login.
 *
 * A failed POST never renders the page in the same request: Profile.Security.authenticate() returns
 * false, controllers.Secure.authenticate() puts the flash values and calls login(), and the app's
 * login() (forum/Users.java) redirects, so the response is 302 with Location /login and the flash goes
 * into the PLAY_FLASH cookie. The markup below is therefore read from the GET /login that follows,
 * which is how a browser sees it.
 *
 * Profile.Security.authenticate() (app/controllers/Profile.java) writes flash.errorDetail on each of
 * its three refusals and flash.notconfirmed plus a fresh uuid on the third one, so each branch of
 * #{if flash.errorDetail} and of #{if flash.notconfirmed} is reached by a different account state.
 * flash.error is the module's own message, secure.error, which the application overrides in
 * conf/messages with "Oops, unknown email or password.".
 *
 * params.flash() copies the submitted parameters into the flash, which feeds the
 * ${flash.remember ? 'checked="true"' : ''} ternary. remember arrives as the string "false", which is
 * truthy, so a POST always re-checks the box; the unchecked branch appears when flash.remember is
 * absent, i.e. on a GET /login and on a POST that did not send the field. ${flash.email} is never set
 * by anything, so the email box always comes back empty.
 *
 * #{authenticityToken /} writes a random token after the opening <form>, so the form is asserted piece
 * by piece. A POST without a username is not exercised: @Required on Secure.authenticate makes
 * Security.invoke() reach play.utils.Java.invokeStaticOrParent with a null argument, which throws
 * NullPointerException before any template runs, so no markup exists for that request.
 */
public class LoginPageTest extends BaseForumViewTest {

	private static final String LOGIN_HEADER = "<div class=\"page-header\"> <h1>Login</h1> "
			+ "<p>Please enter you email address and password.</p> </div>";

	private static final String OPEN_FORM = "<form action=\"/login\" method=\"post\" accept-charset=\"utf-8\" enctype=\"application/x-www-form-urlencoded\" >";

	private static final String EMAIL_GROUP = "<div class=\"form-group\"> <label for=\"username\">Email</label> <input type=\"email\" name=\"username\" value=\"\" id=\"email\" class=\"form-control\" placeholder=\"Email\" /> </div>";

	private static final String PASSWORD_GROUP = "<div class=\"form-group\"> <label for=\"password\">Password</label> <input type=\"password\" name=\"password\" value=\"\" id=\"password\" class=\"form-control\" placeholder=\"Password\"/> </div>";

	private static final String SIGN_IN_BUTTON = "<button type=\"submit\" id=\"signin\" class=\"btn btn-primary\">Sign in</button>";

	private static final String FORGOTTEN_DROPDOWN = "<div class=\"btn-group\"> <button type=\"button\" class=\"btn btn-info dropdown-toggle\" data-toggle=\"dropdown\"> I have forgotten ! <span class=\"caret\"></span> </button> <ul class=\"dropdown-menu\" role=\"menu\"> <li><a href=\"/forgottenEmail\">my email</a></li> <li><a href=\"/forgottenPassword\">my password</a></li> </ul> </div>";

	private static final String CHECKED_BOX = "<input type=\"checkbox\" name=\"remember\" id=\"remember\" value=\"true\" checked=&quot;true&quot;> Remember me </label>";

	private static final String UNCHECKED_BOX = "<input type=\"checkbox\" name=\"remember\" id=\"remember\" value=\"true\" > Remember me </label>";

	private static final String RESEND_LINK = "<a class=\"btn btn-warning\" href=\"/confirmation/";

	/**
	 * POST /login, which fails and redirects to /login. The flash of the response is what the page the
	 * redirect points at reads, so the caller keeps the Response to read both.
	 */
	private Response failedLogin(Map<String, String> postBody) throws Throwable {
		clearCookies();
		Response response = POST("/login", postBody);
		assertStatus(302, response);
		assertLocation(response, "/login");
		Assert.assertEquals("Oops, unknown email or password.", flashOf(response).get("error"));
		return response;
	}

	/** The login page the redirect above points at. */
	private String loginPage(Response failedResponse) throws Throwable {
		assertStatus(302, failedResponse);
		return markup(GET("/login"));
	}

	private static void assertDetail(String detail, String body) {
		assertContains("<div class=\"alert alert-danger\"> " + detail + " </div>", body);
		assertErrorAlert("Oops, unknown email or password.", body);
	}

	private static void assertWholeForm(String body) {
		assertContains(OPEN_FORM, body);
		assertMatches("<input type=\"hidden\" name=\"authenticityToken\" value=\"[0-9a-f]+\"/>", body);
		assertContains(EMAIL_GROUP, body);
		assertContains(PASSWORD_GROUP, body);
		assertContains(SIGN_IN_BUTTON, body);
		assertContains(FORGOTTEN_DROPDOWN, body);
	}

	// ~~~~~~~~~~~~ the page without a failed POST

	@Test
	public void theLoginPageOfAnAnonymousVisitorHasNoAlertAndNoResendLink() throws Throwable {
		clearCookies();
		Response response = GET("/login");
		assertStatus(200, response);
		String body = markup(response);

		assertContains(LOGIN_HEADER, body);
		assertWholeForm(body);
		// The checkbox is written without the attribute: flash.remember is absent
		assertContains(UNCHECKED_BOX, body);
		assertNotContains("checked=", body);
		assertContains("</form>", body);
		// Secure is the controller, so Login is the active nav item and Signup sits next to it
		assertContains("<li class=\"active\"><a href=\"/login\">Login</a></li> <li><a href=\"/signup\">Signup</a></li>", body);
		assertNotContains("alert-danger", body);
		assertNotContains("Resend me a confirmation email", body);
	}

	// ~~~~~~~~~~~~ the three refusals of Profile.Security.authenticate()

	@Test
	public void aBadPasswordIsRefusedAndKeepsTheRememberBoxChecked() throws Throwable {
		Response failed = failedLogin(form("username", "bob@domain.com", "password", "wrong", "remember", "true"));
		Map<String, String> flash = flashOf(failed);
		Assert.assertEquals("Bad email or bad password", flash.get("errorDetail"));
		Assert.assertEquals("bob@domain.com", flash.get("username"));
		Assert.assertEquals("wrong", flash.get("password"));

		String body = loginPage(failed);
		assertDetail("Bad email or bad password", body);
		assertWholeForm(body);
		// flash.remember is "true" -> the ternary writes the attribute, escaped by Groovy
		assertContains(CHECKED_BOX, body);
		assertNotContains("Resend me a confirmation email", body);
	}

	@Test
	public void aRememberFieldOfFalseStillChecksTheBoxBecauseAStringIsTruthy() throws Throwable {
		Response failed = failedLogin(form("username", "bob@domain.com", "password", "wrong", "remember", "false"));
		Assert.assertEquals("false", flashOf(failed).get("remember"));

		String body = loginPage(failed);
		assertDetail("Bad email or bad password", body);
		assertContains(CHECKED_BOX, body);
	}

	@Test
	public void anInactiveAccountIsRefusedWithoutConfirmationLink() throws Throwable {
		inTransactionVoid(() -> {
			User bob = User.findByEmail("bob@domain.com");
			bob.active = Boolean.FALSE;
			bob.save();
			return null;
		});

		Response failed = failedLogin(form("username", "bob@domain.com", "password", "secret", "remember", "true"));
		Assert.assertEquals("This account is not active", flashOf(failed).get("errorDetail"));

		String body = loginPage(failed);
		assertDetail("This account is not active", body);
		assertContains(CHECKED_BOX, body);
		assertNotContains("Resend me a confirmation email", body);
	}

	@Test
	public void anUnconfirmedAccountOffersTheResendConfirmationLinkAndLeavesTheBoxUnchecked() throws Throwable {
		inTransactionVoid(() -> {
			User bob = User.findByEmail("bob@domain.com");
			bob.confirmed = Boolean.FALSE;
			bob.save();
			return null;
		});

		// No remember field at all -> flash.remember absent -> the unchecked branch of the ternary
		Response failed = failedLogin(form("username", "bob@domain.com", "password", "secret"));
		Map<String, String> flash = flashOf(failed);
		Assert.assertEquals("This account is not confirmed", flash.get("errorDetail"));
		Assert.assertNull(flash.get("remember"));

		String body = loginPage(failed);
		assertDetail("This account is not confirmed", body);
		assertContains(UNCHECKED_BOX, body);

		// authenticate() stores a new uuid on the account and puts it in the flash, so the link the
		// template writes carries exactly that uuid: @{forum.Users.resendConfirmation(uuid)}
		final String uuid = inTransaction(() -> User.findByEmail("bob@domain.com").uuid);
		Assert.assertNotNull(uuid);
		Assert.assertEquals(uuid, flash.get("notconfirmed"));
		assertContains(RESEND_LINK + uuid + "\">Resend me a confirmation email</a>", body);
	}

	@Test
	public void theResendConfirmationLinkOfTheLoginPageSendsTheWelcomeMailAgain() throws Throwable {
		inTransactionVoid(() -> {
			User bob = User.findByEmail("bob@domain.com");
			bob.confirmed = Boolean.FALSE;
			bob.save();
			return null;
		});

		Response failed = failedLogin(form("username", "bob@domain.com", "password", "secret"));
		final String uuid = flashOf(failed).get("notconfirmed");
		assertContains(RESEND_LINK + uuid + "\">Resend me a confirmation email</a>", loginPage(failed));

		// GET /confirmation/{uuid} is forum.Users.resendConfirmation, which mails and then redirects
		clearCookies();
		Response response = GET("/confirmation/" + uuid);
		assertStatus(302, response);
		assertLocation(response, "/login");
		assertSuccessAlert("Please check your emails ...", markup(GET("/login")));
	}

	// ~~~~~~~~~~~~ a successful login

	@Test
	public void aSuccessfulLoginRedirectsToTheHomeRootAndStoresTheSession() throws Throwable {
		clearCookies();
		Response response = POST("/login", form("username", "admin@domain.com", "password", "secret"));
		assertStatus(302, response);
		assertLocation(response, "/");
		Assert.assertEquals("admin@domain.com", sessionUsername(response));
		Assert.assertTrue(response.cookies.containsKey("PLAY_SESSION"));

		// Application.index renders the welcome flash the secure module wrote on authentication
		assertSuccessAlert("Welcome back Admin !", markup(GET("/")));
	}

	@Test
	public void aSuccessfulLoginWithRememberMeSetsTheRememberMeCookie() throws Throwable {
		clearCookies();
		Response response = POST("/login",
				form("username", "admin@domain.com", "password", "secret", "remember", "true"));
		assertStatus(302, response);
		assertLocation(response, "/");
		Assert.assertTrue("expected a rememberme cookie", response.cookies.containsKey("rememberme"));
	}
}
