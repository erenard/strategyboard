import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.junit.*;

import org.apache.commons.mail.Email;

import play.libs.Mail;
import play.libs.mail.MailSystem;
import play.mvc.Http.Response;
import play.templates.TemplateLoader;

import models.forum.User;
import notifiers.Notifier;

/**
 * Coverage of app/views/Notifier/welcome.txt and app/views/Notifier/sendPassword.txt.
 *
 * play.mvc.Mailer derives the template name from the caller method (infos.get("method"), then
 * "notifiers.Notifier.welcome" -> "Notifier/welcome.txt") and renders it with the names of the local
 * variables of that call, which is how ${user.name} and ${password} are resolved. Calling the notifier
 * therefore executes the template.
 *
 * conf/application.conf sets mail.smtp=mock, so nothing is ever sent, and the mock system keeps
 * nothing. Mail.useMailSystem() installs a double that stores the built Email, which gives the subject
 * and the recipients; org.apache.commons.mail.Email has no getter for the body, so the text Mailer
 * rendered is read back from the template with the same variable names, and both agree on the uuid and
 * the password they carry.
 *
 * welcome.txt writes @@{forum.Users.confirmRegistration(user.uuid)}, the absolute form of
 * GET /signup/{uuid}. One case follows that link and one follows a uuid of an account that does not
 * exist, so both sides of notFoundIfNull() in forum.Users.confirmRegistration are exercised.
 */
public class NotifierTemplatesTest extends BaseForumViewTest {

	private static final List<Email> sent = new ArrayList<Email>();

	private static class CapturingMailSystem implements MailSystem {
		public Future<Boolean> sendMessage(Email email) {
			sent.add(email);
			return java.util.concurrent.CompletableFuture.completedFuture(Boolean.TRUE);
		}
	}

	private static Map<String, Object> binding(Object... entries) {
		Map<String, Object> args = new HashMap<String, Object>();
		for (int i = 0; i < entries.length; i += 2) {
			args.put((String) entries[i], entries[i + 1]);
		}
		return args;
	}

	@Test
	public void welcomeMailCarriesTheConfirmationLinkOfTheUuid() throws Throwable {
		sent.clear();
		Mail.useMailSystem(new CapturingMailSystem());
		try {
			final String[] body = new String[1];
			User bob = inTransaction(() -> {
				User user = User.findByEmail("bob@domain.com");
				user.uuid = "abc-123";
				user.save();
				body[0] = TemplateLoader.load("Notifier/welcome.txt").render(binding("user", user));
				return user;
			});
			Assert.assertTrue(Notifier.welcome(bob));

			Assert.assertEquals(1, sent.size());
			Email mail = sent.get(0);
			Assert.assertEquals("Welcome Bob", mail.getSubject());
			Assert.assertEquals("bob@domain.com", mail.getToAddresses().get(0).getAddress());

			// welcome.txt writes the greeting, the absolute confirmation link the
			// @@{forum.Users.confirmRegistration(uuid)} tag builds, and the closing line
			Assert.assertEquals("Hey Bob, \n\nPlease confirm your registration by clicking on http://localhost/signup/abc-123\n\nThank !",
					body[0]);
		} finally {
			Mail.resetMailSystem();
		}
	}

	@Test
	public void sendPasswordMailCarriesTheNewPassword() throws Throwable {
		sent.clear();
		Mail.useMailSystem(new CapturingMailSystem());
		try {
			final String[] body = new String[1];
			User modo = inTransaction(() -> {
				User user = User.findByEmail("modo@domain.com");
				body[0] = TemplateLoader.load("Notifier/sendPassword.txt")
						.render(binding("user", user, "password", "aNewPassword"));
				return user;
			});
			Assert.assertTrue(Notifier.sendPassword(modo, "aNewPassword"));

			Assert.assertEquals(1, sent.size());
			Email mail = sent.get(0);
			Assert.assertEquals("Reset password Moderator", mail.getSubject());
			Assert.assertEquals("modo@domain.com", mail.getToAddresses().get(0).getAddress());

			Assert.assertEquals("Hey Moderator, \n\nHere is your new password aNewPassword\n\nThank !", body[0]);
		} finally {
			Mail.resetMailSystem();
		}
	}

	@Test
	public void theRegistrationPageOfTheLinkInTheWelcomeBodyConfirmsTheAccount() throws Throwable {
		sent.clear();
		Mail.useMailSystem(new CapturingMailSystem());
		try {
			// The uuid welcome.txt tells the user to click is the account's own uuid, and
			// GET /signup/{uuid} is forum.Users.confirmRegistration, the route the link points at
			final String[] body = new String[1];
			User anonymous = inTransaction(() -> {
				User user = User.findByEmail("anonymous@domain.com");
				user.confirmed = Boolean.FALSE;
				user.uuid = "dead-beef";
				user.save();
				body[0] = TemplateLoader.load("Notifier/welcome.txt").render(binding("user", user));
				return user;
			});
			Assert.assertTrue(Notifier.welcome(anonymous));
			Assert.assertEquals(1, sent.size());
			Assert.assertTrue(body[0].contains("http://localhost/signup/dead-beef"));
		} finally {
			Mail.resetMailSystem();
		}

		clearCookies();
		Response response = GET("/signup/dead-beef");
		assertStatus(302, response);
		// confirmRegistration() greets the account and then calls login(), which redirects to /login
		assertLocation(response, "/login");
		Assert.assertEquals(Boolean.TRUE, inTransaction(() -> User.findByEmail("anonymous@domain.com").confirmed));
		assertSuccessAlert("Welcome Anonymous !", markup(GET("/login")));
	}

	@Test
	public void anUnknownUuidInWelcomeBodyNotFoundsTheConfirmationRoute() throws Throwable {
		clearCookies();
		Response response = GET("/signup/noSuchUuid");
		assertStatus(404, response);
		// notFoundIfNull() renders errors/404.html, whose subtitle says what was searched for
		assertHeader(markup(response), "Forbidden", "The forum, topic, or post you search does not exist !");
	}
}
