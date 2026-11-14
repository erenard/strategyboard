import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.*;

import play.cache.Cache;
import play.db.jpa.JPA;
import play.libs.Codec;
import play.mvc.Http.Response;
import play.test.Fixtures;

import models.forum.Post;
import models.forum.Topic;
import models.forum.User;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.Scenario;

/**
 * Coverage of app/views/forum/Users/*.html: index.html, show.html, forgottenEmail.html,
 * forgottenPassword.html, signup.html and profile.html, in both directions of every conditional.
 *
 * The branch of showRecentGames's #{else}Anonymous is not exercised: User.getRecentsGames() selects
 * games through PlayedGameScore, so every listed game has at least one score and PlayedGame.getWinner()
 * never returns null. The false branch is asserted absent instead.
 *
 * Scenarios that change Bob's password or email are separate tests: BaseFunctionalTest.loadFixtures()
 * runs before each one and restores the seeded passwordHash and email.
 */
public class UsersPageTest extends BaseForumViewTest {

	private static final String SECRET_HASH = "5ebe2294ecd0e0f08eab7690d2a6ee69";

	// ~~~~~~~~~~~~ index.html

	@Test
	public void usersIndexCountsUsersPlurallyAndListsTheirTotals() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		Response response = GET("/users");
		assertStatus(200, response);
		String body = markup(response);

		assertHeader(body, "Members", "3 users");
		List<String> rows = tableRows(body);
		Assert.assertEquals(3, rows.size());
		assertContains("<td class=\"main\"> <a href=\"/users/1\">Admin</a> </td> <td class=\"main\"> 2 </td>"
				+ " <td class=\"main\"> 2 </td> <td class=\"infos\"> 0 </td>", rows.get(0));
		assertContains("<td class=\"main\"> <a href=\"/users/3\">Bob</a> </td> <td class=\"main\"> 1 </td>"
				+ " <td class=\"main\"> 1 </td> <td class=\"infos\"> 0 </td>", rows.get(1));
		assertContains("<td class=\"main\"> <a href=\"/users/2\">Moderator</a> </td> <td class=\"main\"> 3 </td>"
				+ " <td class=\"main\"> 3 </td> <td class=\"infos\"> 0 </td>", rows.get(2));

		// #{pagination ...}: nbPages == 1 at the configured pageSize, so the tag renders nothing
		Assert.assertNull(paginationBlock(body));

		// User.findAllActive only counts users that posted or scored, so Anonymous is not listed
		assertNotContains(">Anonymous</a>", body);
		assertSuccessAlert("Welcome back Admin !", body);
	}

	@Test
	public void usersIndexPaginatesWhenUsersExceedPageSize() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		setPageSize(2);
		try {
			String firstPage = markup(GET("/users"));
			assertHeader(firstPage, "Members", "3 users");
			assertContains("<ul class=\"pagination\"> <li class=\"active\"><a href=\"#\">1</a></li>"
					+ " <li><a href=\"/users?page=2\">2</a></li> </ul>", paginationBlock(firstPage));
			Assert.assertEquals(2, tableRows(firstPage).size());
			assertContains(">Admin</a>", firstPage);
			assertContains(">Bob</a>", firstPage);
			assertNotContains(">Moderator</a>", firstPage);

			String secondPage = markup(GET("/users?page=2"));
			assertContains("<ul class=\"pagination\"> <li><a href=\"/users?page=1\">1</a></li>"
					+ " <li class=\"active\"><a href=\"#\">2</a></li> </ul>", paginationBlock(secondPage));
			List<String> rows = tableRows(secondPage);
			Assert.assertEquals(1, rows.size());
			assertContains(">Moderator</a>", rows.get(0));

			// Past the last page: #{if _page == i} is false for every page number, and #{list} is empty
			String thirdPage = markup(GET("/users?page=3"));
			assertContains("<ul class=\"pagination\"> <li><a href=\"/users?page=1\">1</a></li>"
					+ " <li><a href=\"/users?page=2\">2</a></li> </ul>", paginationBlock(thirdPage));
			// main.html marks its own nav item with class="active", so only the pagination is checked
			assertNotContains("<li class=\"active\">", paginationBlock(thirdPage));
			Assert.assertEquals(0, tableRows(thirdPage).size());
		} finally {
			restorePageSize();
		}
	}

	@Test
	public void usersIndexCountsASingleUserInSingular() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			User.findByEmail("admin@domain.com").active = Boolean.FALSE;
			User.findByEmail("admin@domain.com").save();
			User.findByEmail("modo@domain.com").active = Boolean.FALSE;
			User.findByEmail("modo@domain.com").save();
			return null;
		});
		login("bob@domain.com", "secret");
		String body = markup(GET("/users"));

		// #{pluralize size:nbUsers, word:'user'} singular branch
		assertHeader(body, "Members", "1 user");
		List<String> rows = tableRows(body);
		Assert.assertEquals(1, rows.size());
		assertContains(">Bob</a>", rows.get(0));
		Assert.assertNull(paginationBlock(body));
	}

	@Test
	public void usersIndexShowsTheGamesColumnOfAUserWithScores() throws Throwable {
		clearCookies();
		loadOpenTTDFixtures();
		inTransactionVoid(() -> {
			seedGameEndedDaysAgo("bob@domain.com", 12, 3);
			return null;
		});
		login("admin@domain.com", "secret");
		String body = markup(GET("/users"));

		List<String> rows = tableRows(body);
		Assert.assertEquals(3, rows.size());
		assertContains("<a href=\"/users/3\">Bob</a> </td> <td class=\"main\"> 1 </td> <td class=\"main\"> 1 </td>"
				+ " <td class=\"infos\"> 1 </td>", rows.get(1));
		assertContains("<a href=\"/users/1\">Admin</a> </td> <td class=\"main\"> 2 </td> <td class=\"main\"> 2 </td>"
				+ " <td class=\"infos\"> 0 </td>", rows.get(0));
	}

	// ~~~~~~~~~~~~ show.html

	@Test
	public void userPageOmitsBothSectionsForAUserWithoutPostsOrGames() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		String body = markup(GET("/users/4"));

		assertHeader(body, "Anonymous", "0 games, 0 posts in 0 topics");
		assertContains("<ol class=\"breadcrumb\"> <li><a href=\"/users\">Members</a></li>"
				+ " <li class=\"active\">Anonymous</li> </ol>", body);
		// #{if user.recentsGames} and #{if user.recentsPosts} are both empty lists
		assertNotContains("<h3>Recents games ...</h3>", body);
		assertNotContains("<h3>Recents posts ...</h3>", body);
		assertNotContains("panel-body", body);
		assertNotContains("<th>Won by</th>", body);
	}

	@Test
	public void userPageShowsOnlyThePostsSectionForAUserWithoutGames() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		String body = markup(GET("/users/3"));

		assertHeader(body, "Bob", "0 games, 1 post in 1 topic");
		assertNotContains("<h3>Recents games ...</h3>", body);
		assertNotContains("<th>Won by</th>", body);
		assertContains("<h3>Recents posts ...</h3>", body);

		// #{showPost arg:post, showTopic:true /}: the tag's #{if _showTopic} branch
		assertContains("Posted in <a href=\"/forums/1/topics/1\">mainForum1 - Topic Normal</a>", body);
		assertContains("First post of the normal topic.", body);
		assertContains("<a href=\"/users/3\">Bob</a> 1 post </div>", body);
	}

	@Test
	public void userPageShowsTheGamesTableWhenTheUserHasPlayedGames() throws Throwable {
		clearCookies();
		loadOpenTTDFixtures();
		inTransactionVoid(() -> {
			seedGameEndedDaysAgo("bob@domain.com", 12, 3);
			return null;
		});
		login("bob@domain.com", "secret");
		String body = markup(GET("/users/3"));

		// #{pluralize} singular branches for game, post and topic
		assertHeader(body, "Bob", "1 game, 1 post in 1 topic");

		// #{if user.recentsGames} true branch
		assertContains("<h3>Recents games ...</h3>", body);
		List<String> rows = tableRows(body);
		Assert.assertEquals(1, rows.size());
		assertContains("<td>3 days ago</td> <td><img src=\"/images/openttd/high_speed_train.gif\""
				+ " alt=\"scenarioTrain\" align=\"middle\" class=\"train_icon\"> HighSpeed Scenario</td>", rows.get(0));
		// #{if playedGame.winner} true branch; the #{else} branch is unreachable through this query
		assertContains("<td> <a href=\"/users/3\">Bob</a> with 12 points </td>", rows.get(0));
		assertNotContains(">Anonymous<", body);

		assertContains("<h3>Recents posts ...</h3>", body);
		assertContains("Posted in <a href=\"/forums/1/topics/1\">mainForum1 - Topic Normal</a>", body);
	}

	@Test
	public void userPageExcludesPostsMadeOnHiddenTopics() throws Throwable {
		clearCookies();
		loadOpenTTDFixtures();
		inTransactionVoid(() -> {
			seedGameEndedDaysAgo("bob@domain.com", 12, 3);
			seedPost("bob@domain.com", 3L, "Post on the hidden topic", "Hidden topic content.", 1);
			return null;
		});
		login("bob@domain.com", "secret");
		String body = markup(GET("/users/3"));

		// The counters include the hidden post, the recent list does not: getRecentsPosts() filters
		// topic.hidden = false while getPostsCount() and getTopicsCount() do not.
		assertHeader(body, "Bob", "1 game, 2 posts in 2 topics");
		assertContains("<h3>Recents posts ...</h3>", body);
		assertContains("Posted in <a href=\"/forums/1/topics/1\">mainForum1 - Topic Normal</a>", body);
		assertNotContains("Topic Hidden", body);
		assertNotContains("/forums/1/topics/3", body);
		Assert.assertEquals(1, count("Posted in", body));
	}

	@Test
	public void unknownUserPageIsNotFound() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		Response response = GET("/users/999");
		assertStatus(404, response);
		String body = markup(response);

		assertHeader(body, "Forbidden", "The forum, topic, or post you search does not exist !");
		assertContains("Please go back to the <a href=\"/\">home page</a>.", body);
	}

	// ~~~~~~~~~~~~ forgottenEmail.html

	@Test
	public void forgottenEmailFormHasNoErrorClassOnAPlainGet() throws Throwable {
		clearCookies();
		String body = markup(GET("/forgottenEmail"));

		assertHeader(body, "Email retrieval", "Please enter your pseudo and password.");
		assertField(body, "pseudo", "Username ", null);
		assertField(body, "password", "Password ", null);
		assertNotContains("form-group has-error", body);
		assertContains("<button type=\"submit\" class=\"btn btn-info\" >Show my email</button>", body);
		assertContains("<form id=\"userform\" action=\"/forgottenEmail\" method=\"POST\"", body);
	}

	@Test
	public void forgottenEmailKeepsTheRequiredErrorsOfEmptyFields() throws Throwable {
		clearCookies();
		// The form must be fetched first: an anonymous POST is intercepted by the secure module.
		GET("/forgottenEmail");
		Response response = POST("/forgottenEmail", form("pseudo", "", "password", ""));
		assertStatus(302, response);
		assertLocation(response, "/forgottenEmail");
		Assert.assertEquals("validation.required", errorMessage(response, "pseudo"));
		Assert.assertEquals("validation.required", errorMessage(response, "password"));

		String body = markup(GET("/forgottenEmail"));
		assertErrorAlert("Please correct these errors !", body);
		assertField(body, "pseudo", "Username ", "Required");
		assertField(body, "password", "Password ", "Required");
	}

	@Test
	public void forgottenEmailRejectsAWrongPassword() throws Throwable {
		clearCookies();
		GET("/forgottenEmail");
		Response response = POST("/forgottenEmail", form("pseudo", "Bob", "password", "nope"));
		assertStatus(302, response);
		assertLocation(response, "/forgottenEmail");
		Assert.assertEquals("Incorrect pseudo or password !", flashOf(response).get("error"));

		// No validation error, so every form-group keeps the false branch of #{ifError}
		String body = markup(GET("/forgottenEmail"));
		assertErrorAlert("Incorrect pseudo or password !", body);
		assertField(body, "pseudo", "Username ", null);
		assertField(body, "password", "Password ", null);
	}

	@Test
	public void forgottenEmailRejectsAnUnknownPseudo() throws Throwable {
		clearCookies();
		GET("/forgottenEmail");
		Response response = POST("/forgottenEmail", form("pseudo", "Nobody", "password", "secret"));
		assertStatus(302, response);
		assertLocation(response, "/forgottenEmail");
		Assert.assertEquals("Incorrect pseudo or password !", flashOf(response).get("error"));

		String body = markup(GET("/forgottenEmail"));
		assertErrorAlert("Incorrect pseudo or password !", body);
		assertNotContains("form-group has-error", body);
	}

	@Test
	public void forgottenEmailRevealsTheEmailOnTheLoginPage() throws Throwable {
		clearCookies();
		GET("/forgottenEmail");
		Response response = POST("/forgottenEmail", form("pseudo", "Bob", "password", "secret"));
		assertStatus(302, response);
		assertLocation(response, "/login");
		Assert.assertEquals("Your email is bob@domain.com", flashOf(response).get("success"));
		Assert.assertEquals("bob@domain.com", flashOf(response).get("email"));

		String body = markup(GET("/login"));
		assertSuccessAlert("Your email is bob@domain.com", body);
	}

	// ~~~~~~~~~~~~ forgottenPassword.html

	@Test
	public void forgottenPasswordFormHasNoErrorClassAndNoFlashValue() throws Throwable {
		clearCookies();
		String body = markup(GET("/forgottenPassword"));

		assertHeader(body, "Password retrieval", "Please enter your email address.");
		assertField(body, "email", "Email ", null);
		assertContains("<input type=\"email\" name=\"email\" value=\"\" class=\"form-control\" placeholder=\"Email\"/>",
				body);
		assertContains("<button class=\"btn btn-info\" type=\"submit\">Reset my password</button>", body);
	}

	@Test
	public void forgottenPasswordKeepsTheErrorOfAnUnknownEmail() throws Throwable {
		clearCookies();
		GET("/forgottenPassword");
		Response response = POST("/forgottenPassword", form("email", "zz@domain.com"));
		assertStatus(302, response);
		assertLocation(response, "/forgottenPassword");
		Assert.assertEquals("Unkown email: zz@domain.com", errorMessage(response, "email"));

		String body = markup(GET("/forgottenPassword"));
		assertErrorAlert("Please correct these errors !", body);
		assertField(body, "email", "Email ", "Unkown email: zz@domain.com");
		// params.flash() puts email back, which ${flash.email} reads
		assertContains("<input type=\"email\" name=\"email\" value=\"zz@domain.com\"", body);
	}

	@Test
	public void forgottenPasswordKeepsTheErrorOfAnInvalidEmailFormat() throws Throwable {
		clearCookies();
		GET("/forgottenPassword");
		Response response = POST("/forgottenPassword", form("email", "not-an-email"));
		assertStatus(302, response);
		assertLocation(response, "/forgottenPassword");

		String body = markup(GET("/forgottenPassword"));
		assertErrorAlert("Please correct these errors !", body);
		// @Email is checked before Users.sendPassword() adds its own error, so #{error 'email'} shows it
		assertField(body, "email", "Email ", "Invalid email address");
		assertContains("Unkown email: not-an-email", errorsOf(response).toString());
	}

	@Test
	public void forgottenPasswordResetsThePasswordAndRedirectsToLogin() throws Throwable {
		clearCookies();
		GET("/forgottenPassword");
		String hashBefore = passwordHash("bob@domain.com");

		Response response = POST("/forgottenPassword", form("email", "bob@domain.com"));
		assertStatus(302, response);
		assertLocation(response, "/login");
		Assert.assertEquals("A new password has been sent to you !", flashOf(response).get("success"));
		Assert.assertEquals("bob@domain.com", flashOf(response).get("email"));

		String hashAfter = passwordHash("bob@domain.com");
		Assert.assertEquals(SECRET_HASH, hashBefore);
		Assert.assertNotEquals(SECRET_HASH, hashAfter);

		assertSuccessAlert("A new password has been sent to you !", markup(GET("/login")));

		// The form comes back clean: the kept errors were consumed by the redirect
		assertNotContains("form-group has-error", markup(GET("/forgottenPassword")));
	}

	// ~~~~~~~~~~~~ signup.html

	@Test
	public void signupFormRendersTheCaptchaAndItsUuid() throws Throwable {
		clearCookies();
		String body = markup(GET("/signup"));

		assertHeader(body, "Signup", "Please enter your user informations");
		assertField(body, "email", "Email, we will send you an email for confirmation. ", null);
		assertLabellessField(body, "Display name. ", null);
		assertLabellessField(body, "Password, it must be at least 5 character long. ", null);
		assertLabellessField(body, "Password confirmation. ", null);
		assertField(body, "code", "Please type this: ", null);
		assertNotContains("form-group has-error", body);

		String uuid = captchaUuid(body);
		assertContains("<img src=\"/captcha?id=" + uuid + "\" />", body);
		assertContains("<input type=\"hidden\" name=\"uuid\" value=\"" + uuid + "\" />", body);
	}

	@Test
	public void signupKeepsTheRequiredErrorsOfAnEmptyForm() throws Throwable {
		clearCookies();
		GET("/signup");
		Response response = POST("/signup", form("email", "", "name", "", "password", "", "password2", "", "code", "",
				"uuid", "fixed-uuid"));
		assertStatus(302, response);
		assertLocation(response, "/signup");

		String body = markup(GET("/signup"));
		assertErrorAlert("Please correct these errors !", body);
		assertField(body, "email", "Email, we will send you an email for confirmation. ", "Required");
		assertLabellessField(body, "Display name. ", "Required");
		assertLabellessField(body, "Password, it must be at least 5 character long. ", "Required");
		// @Equals("password") passes on two empty values, so password2 stays in the false branch
		assertLabellessField(body, "Password confirmation. ", null);
		assertField(body, "code", "Please type this: ", "Required");
		assertContains("<input type=\"email\" name=\"email\" value=\"\" id=\"email\"", body);
	}

	@Test
	public void signupRejectsAnAlreadyRegisteredEmail() throws Throwable {
		clearCookies();
		GET("/signup");
		Response response = POST("/signup", form("email", "bob@domain.com", "name", "Fresh", "password", "secret",
				"password2", "secret", "code", "zzzz", "uuid", "fixed-uuid"));
		assertStatus(302, response);
		assertLocation(response, "/signup");
		Assert.assertEquals("This email is already registered.", errorMessage(response, "email"));

		String body = markup(GET("/signup"));
		assertErrorAlert("Please correct these errors !", body);
		assertField(body, "email", "Email, we will send you an email for confirmation. ",
				"This email is already registered.");
		assertField(body, "code", "Please type this: ", "Invalid code. Please type it again");
		assertLabellessField(body, "Display name. ", null);
		assertLabellessField(body, "Password, it must be at least 5 character long. ", null);
		assertLabellessField(body, "Password confirmation. ", null);
		assertContains("<input type=\"email\" name=\"email\" value=\"bob@domain.com\"", body);
		assertContains("<input type=\"text\" name=\"name\" value=\"Fresh\"", body);
	}

	@Test
	public void signupRejectsOnlyTheCaptchaCode() throws Throwable {
		clearCookies();
		GET("/signup");
		Response response = POST("/signup", form("email", "fresh@domain.com", "name", "Fresh", "password", "secret",
				"password2", "secret", "code", "zzzz", "uuid", "fixed-uuid"));
		assertStatus(302, response);
		assertLocation(response, "/signup");
		Assert.assertEquals("Invalid code. Please type it again", errorMessage(response, "code"));

		String body = markup(GET("/signup"));
		assertErrorAlert("Please correct these errors !", body);
		assertField(body, "email", "Email, we will send you an email for confirmation. ", null);
		assertField(body, "code", "Please type this: ", "Invalid code. Please type it again");
		assertNotContains("This email is already registered", body);
		Assert.assertEquals(1, count("form-group has-error", body));
	}

	@Test
	public void signupRegistersTheUserAndRedirectsToLoginWithTheWelcomeFlash() throws Throwable {
		clearCookies();
		String uuid = captchaUuid(markup(GET("/signup")));

		// Application.captcha() stores the code with Cache.set(id, code, "10mn"); clearing the cache
		// here would make Users.register() reject the code it just generated.
		assertStatus(200, GET("/captcha?id=" + uuid));
		Object code = Cache.get(uuid);
		Assert.assertNotNull(code);

		Response response = POST("/signup", form("email", "fresh@domain.com", "name", "Fresh", "password", "secret",
				"password2", "secret", "code", code.toString(), "uuid", uuid));
		assertStatus(302, response);
		assertLocation(response, "/login");
		Assert.assertEquals("Your account is created. Please check your emails ...", flashOf(response).get("success"));

		String created = inTransaction(() -> {
			User fresh = User.findByEmail("fresh@domain.com");
			return fresh == null ? "not created" : "uuid=" + fresh.uuid + " confirmed=" + fresh.confirmed
					+ " group=" + fresh.group.name + " active=" + fresh.active;
		});
		assertMatches("uuid=[0-9a-f-]{36} confirmed=false group=General active=true", created);

		assertSuccessAlert("Your account is created. Please check your emails ...", markup(GET("/login")));
	}

	// ~~~~~~~~~~~~ profile.html

	@Test
	public void profileFormShowsTheConnectedEmailWithoutErrors() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		String body = markup(GET("/profile"));

		assertHeader(body, "My profile", "Update your user information");
		assertField(body, "password", "Current password ", null);
		assertField(body, "email", "Email, we will send you an email for confirmation. ", null);
		assertField(body, "email2", "Email confirmation ", null);
		assertField(body, "newPassword", "New password, it must be at least 5 character long. ", null);
		assertField(body, "newPassword2", "New password confirmation ", null);
		assertNotContains("form-group has-error", body);

		// ${user.email} for both email inputs, ${flash.newPassword} empty for the password inputs
		assertContains("<input type=\"email\" name=\"email\" value=\"bob@domain.com\"", body);
		assertContains("<input type=\"email\" name=\"email2\" value=\"bob@domain.com\"", body);
		assertContains("<input type=\"password\" name=\"newPassword\" value=\"\"", body);
		assertContains("<input type=\"password\" name=\"newPassword2\" value=\"\"", body);
	}

	@Test
	public void profileRejectsAnonymousUsersWithARedirectToLogin() throws Throwable {
		clearCookies();
		Response response = GET("/profile");
		assertStatus(302, response);
		assertLocation(response, "/login");
		Assert.assertEquals("/profile", flashOf(response).get("url"));
	}

	@Test
	public void profileRejectsAShortWrongPasswordOnBothSidesOfTheField() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/profile", form("password", "nope", "email", "bob@domain.com", "email2",
				"bob@domain.com"));
		assertStatus(302, response);
		assertLocation(response, "/profile");

		String body = markup(GET("/profile"));
		assertErrorAlert("Please correct these errors !", body);
		// @MinSize(5) fires before Users.update() adds "Wrong password", and #{error} shows the first
		assertField(body, "password", "Current password ", "Minimum size is 5");
		assertContains("Wrong password", errorsOf(response).toString());
		assertField(body, "email", "Email, we will send you an email for confirmation. ", null);
		assertField(body, "email2", "Email confirmation ", null);
		assertField(body, "newPassword", "New password, it must be at least 5 character long. ", null);
		assertField(body, "newPassword2", "New password confirmation ", null);
	}

	@Test
	public void profileRejectsALongWrongPassword() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/profile", form("password", "wrongpass", "email", "bob@domain.com", "email2",
				"bob@domain.com"));
		assertStatus(302, response);
		assertLocation(response, "/profile");

		String body = markup(GET("/profile"));
		assertErrorAlert("Please correct these errors !", body);
		assertField(body, "password", "Current password ", "Wrong password");
		// profile.html has no value attribute on the current-password input, so it is never repopulated
		assertContains("<input type=\"password\" name=\"password\" class=\"form-control\""
				+ " placeholder=\"Please enter your password.\"/>", body);
	}

	@Test
	public void profileRejectsAnEmailTakenByAnotherAccount() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/profile", form("password", "secret", "email", "admin@domain.com", "email2",
				"admin@domain.com"));
		assertStatus(302, response);
		assertLocation(response, "/profile");

		String body = markup(GET("/profile"));
		assertErrorAlert("Please correct these errors !", body);
		assertField(body, "email", "Email, we will send you an email for confirmation. ",
				"This email (admin@domain.com) is already registered.");
		assertField(body, "email2", "Email confirmation ", null);
		// profile.html reads ${user.email}, not the submitted value, so the rejected address is not shown
		assertContains("<input type=\"email\" name=\"email\" value=\"bob@domain.com\"", body);
		Assert.assertEquals("bob@domain.com", passwordEmailOf("bob@domain.com"));
	}

	@Test
	public void profileRejectsAMismatchedEmailConfirmation() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/profile", form("password", "secret", "email", "new@domain.com", "email2",
				"other@domain.com"));
		assertStatus(302, response);
		assertLocation(response, "/profile");
		Assert.assertEquals("validation.equals", errorMessage(response, "email2"));

		String body = markup(GET("/profile"));
		assertErrorAlert("Please correct these errors !", body);
		assertField(body, "email2", "Email confirmation ", "Must be equal to email");
		assertField(body, "email", "Email, we will send you an email for confirmation. ", null);
	}

	@Test
	public void profileRejectsATooShortNewPasswordAndItsConfirmation() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/profile", form("password", "secret", "email", "bob@domain.com", "email2",
				"bob@domain.com", "newPassword", "abc", "newPassword2", "xyz"));
		assertStatus(302, response);
		assertLocation(response, "/profile");

		String body = markup(GET("/profile"));
		assertErrorAlert("Please correct these errors !", body);
		assertField(body, "newPassword", "New password, it must be at least 5 character long. ", "Minimum size is 5");
		assertField(body, "newPassword2", "New password confirmation ", "Must be equal to newPassword");
		assertField(body, "password", "Current password ", null);
		assertField(body, "email", "Email, we will send you an email for confirmation. ", null);
		assertContains("<input type=\"password\" name=\"newPassword\" value=\"abc\"", body);
		assertContains("<input type=\"password\" name=\"newPassword2\" value=\"xyz\"", body);
	}

	@Test
	public void profileRejectsAMismatchedNewPasswordConfirmation() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/profile", form("password", "secret", "email", "bob@domain.com", "email2",
				"bob@domain.com", "newPassword", "newpass123", "newPassword2", "other123"));
		assertStatus(302, response);
		assertLocation(response, "/profile");
		Assert.assertEquals("validation.equals", errorMessage(response, "newPassword2"));

		String body = markup(GET("/profile"));
		assertErrorAlert("Please correct these errors !", body);
		assertField(body, "newPassword2", "New password confirmation ", "Must be equal to newPassword");
		assertField(body, "newPassword", "New password, it must be at least 5 character long. ", null);
		assertContains("<input type=\"password\" name=\"newPassword\" value=\"newpass123\"", body);
		Assert.assertEquals(SECRET_HASH, passwordHash("bob@domain.com"));
	}

	@Test
	public void profileUpdatesThePasswordWithoutTouchingTheEmail() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/profile", form("password", "secret", "email", "bob@domain.com", "email2",
				"bob@domain.com", "newPassword", "newpass123", "newPassword2", "newpass123"));
		assertStatus(302, response);
		assertLocation(response, "/profile");
		Assert.assertEquals("Your account has been updated.", flashOf(response).get("success"));

		Assert.assertEquals(Codec.hexMD5("newpass123"), passwordHash("bob@domain.com"));
		Assert.assertEquals("bob@domain.com", passwordEmailOf("bob@domain.com"));

		// emailSentence stays empty because the email was not changed
		assertSuccessAlert("Your account has been updated.", markup(GET("/profile")));
	}

	@Test
	public void profileUpdatesTheEmailAndResetsTheConfirmation() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/profile", form("password", "secret", "email", "moved@domain.com", "email2",
				"moved@domain.com"));
		assertStatus(302, response);
		assertLocation(response, "/profile");
		Assert.assertEquals("Your account has been updated. Please check your emails ...", flashOf(response).get("success"));
		Assert.assertEquals("moved@domain.com", sessionUsername(response));

		String state = inTransaction(() -> {
			User moved = User.findByEmail("moved@domain.com");
			return "uuid=" + moved.uuid + " confirmed=" + moved.confirmed + " hash=" + moved.passwordHash;
		});
		assertMatches("uuid=[0-9a-f-]{36} confirmed=false hash=" + SECRET_HASH, state);

		assertSuccessAlert("Your account has been updated. Please check your emails ...", markup(GET("/profile")));
		assertContains("<input type=\"email\" name=\"email\" value=\"moved@domain.com\"", markup(GET("/profile")));
	}

	// ~~~~~~~~~~~~ helpers

	/** The uuid Users.signup() generated, taken from the captcha img of the rendered form. */
	private static String captchaUuid(String body) {
		Matcher m = Pattern.compile("<img src=\"/captcha\\?id=([^\"]+)\" />").matcher(body);
		assertTrue("captcha img not found", m.find());
		return m.group(1);
	}

	private static String passwordHash(String email) throws Throwable {
		return inTransaction(() -> User.findByEmail(email).passwordHash);
	}

	private static String passwordEmailOf(String email) throws Throwable {
		return inTransaction(() -> User.findByEmail(email).email);
	}

	private static void loadOpenTTDFixtures() throws Throwable {
		JPA.withTransaction(JPA.DEFAULT, false, () -> {
			Fixtures.loadModels("openttd-data.yml");
			return null;
		});
	}

	/** One game that ended the given number of days ago, won by the given user. */
	private static void seedGameEndedDaysAgo(String email, int points, int daysAgo) {
		PlayedGame game = new PlayedGame();
		game.scenario = Scenario.findById(1L);
		game.begin = daysAgo(daysAgo + 10);
		game.end = daysAgo(daysAgo);
		game.create();
		PlayedGameScore score = new PlayedGameScore();
		score.playedGame = game;
		score.user = User.findByEmail(email);
		score.achivement = points;
		score.presenceRatio = 1;
		score.create();
	}

	/** A post by the given user on the given topic, daysAgo days in the past. */
	private static void seedPost(String email, Long topicId, String subject, String content, int daysAgo) {
		Post post = new Post();
		post.topic = Topic.findById(topicId);
		post.postedBy = User.findByEmail(email);
		post.subject = subject;
		post.content = content;
		post.postedAt = daysAgo(daysAgo);
		post.create();
	}

	private static Date daysAgo(int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_MONTH, -days);
		return calendar.getTime();
	}
}
