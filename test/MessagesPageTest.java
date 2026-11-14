import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.*;

import play.mvc.Http.Response;
import play.mvc.results.NotFound;

import models.forum.Message;
import models.forum.User;

/**
 * Coverage of app/views/forum/Messages/*.html: inbox.html and outbox.html, in both directions of
 * #{list items:userMessages, as:'message'}.
 *
 * The list is named userMessages because Play binds every Groovy template with a play.i18n.Messages
 * object under the name "messages" (GroovyTemplate.setUpBindingVariables), so a renderArgs entry
 * called messages is overwritten by the i18n object. Both controllers supply ${user} as well, which
 * #{set title: user.name/} dereferences.
 *
 * The outbox lists Message.outbox(user) and says 'Outbox', so the To column shows the recipient.
 * outbox.html has no <h1 id="title"> / <strong> block, unlike inbox.html, which is asserted absent.
 */
public class MessagesPageTest extends BaseForumViewTest {

	// ~~~~~~~~~~~~ inbox.html

	@Test
	public void inboxListsTheMessagesAddressedToTheConnectedUser() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedMessages();
			return null;
		});
		login("bob@domain.com", "secret");
		Response response = GET("/forum.messages/inbox");
		assertStatus(200, response);
		String body = markup(response);

		assertHeader(body, "Bob", "Inbox");
		assertContains("<h1 id=\"title\">Bob</h1> <p class=\"light\"> <strong>Inbox</strong> </p>", body);
		assertSuccessAlert("Welcome back Bob !", body);
		Assert.assertEquals("[From, Subject, Since]", tableHeaders(body).toString());

		List<String> rows = tableRows(body);
		Assert.assertEquals(2, rows.size());
		Assert.assertEquals("<td class=\"main\">Admin</td> <td class=\"main\">Message to Bob one</td>"
				+ " <td class=\"infos\">2 days ago</td>", rows.get(0));
		Assert.assertEquals("<td class=\"main\">Moderator</td> <td class=\"main\">Message to Bob two</td>"
				+ " <td class=\"infos\">2 days ago</td>", rows.get(1));

		// inbox.html and outbox.html have no #{pagination}, so nothing is rendered there
		Assert.assertNull(paginationBlock(body));
	}

	@Test
	public void inboxOfAUserWithMessagesFromSeveralSenders() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedMessages();
			return null;
		});
		login("modo@domain.com", "secret");
		String body = markup(GET("/forum.messages/inbox"));

		assertHeader(body, "Moderator", "Inbox");
		assertContains("<h1 id=\"title\">Moderator</h1>", body);
		assertSuccessAlert("Welcome back Moderator !", body);
		List<String> rows = tableRows(body);
		Assert.assertEquals(1, rows.size());
		Assert.assertEquals("<td class=\"main\">Admin</td> <td class=\"main\">Message admin to modo</td>"
				+ " <td class=\"infos\">2 days ago</td>", rows.get(0));
	}

	@Test
	public void inboxOfAnAdministratorListsTheMessagesSentToIt() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedMessages();
			return null;
		});
		login("admin@domain.com", "secret");
		String body = markup(GET("/forum.messages/inbox"));

		assertHeader(body, "Admin", "Inbox");
		assertContains("<h1 id=\"title\">Admin</h1> <p class=\"light\"> <strong>Inbox</strong> </p>", body);
		List<String> rows = tableRows(body);
		Assert.assertEquals(1, rows.size());
		Assert.assertEquals("<td class=\"main\">Bob</td> <td class=\"main\">Message from Bob</td>"
				+ " <td class=\"infos\">2 days ago</td>", rows.get(0));
	}

	@Test
	public void inboxHasAnEmptyListWhenTheUserHasNoMessages() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedMessages();
			deleteMessagesAddressedTo("bob@domain.com");
			return null;
		});
		login("bob@domain.com", "secret");
		String body = markup(GET("/forum.messages/inbox"));

		// #{list items:userMessages} empty branch: the tbody holds nothing
		assertHeader(body, "Bob", "Inbox");
		assertContains("<h1 id=\"title\">Bob</h1>", body);
		assertContains("<tbody> </tbody>", body);
		Assert.assertEquals(0, tableRows(body).size());
	}

	@Test
	public void inboxFetchesTheSecondPageAndStillRendersNoPagination() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedMessages();
			return null;
		});
		login("bob@domain.com", "secret");
		setPageSize(2);
		try {
			Assert.assertEquals(2, tableRows(markup(GET("/forum.messages/inbox"))).size());

			String secondPage = markup(GET("/forum.messages/inbox?page=2"));
			assertHeader(secondPage, "Bob", "Inbox");
			assertContains("<h1 id=\"title\">Bob</h1>", secondPage);
			assertContains("<tbody> </tbody>", secondPage);
			Assert.assertEquals(0, tableRows(secondPage).size());
			// The welcome flash was consumed by the first request
			assertNotContains("alert alert-success", secondPage);
			Assert.assertNull(paginationBlock(secondPage));
		} finally {
			restorePageSize();
		}
	}

	// ~~~~~~~~~~~~ outbox.html

	@Test
	public void outboxListsTheMessagesTheViewerSentToOtherUsers() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedMessages();
			return null;
		});
		login("admin@domain.com", "secret");
		String body = markup(GET("/forum.messages/outbox"));

		assertHeader(body, "Admin", "Outbox");
		Assert.assertEquals("[To, Subject, Since]", tableHeaders(body).toString());

		// ${message.to.name} is the recipient, because Messages.outbox() lists Message.outbox(user)
		List<String> rows = tableRows(body);
		Assert.assertEquals(3, rows.size());
		Assert.assertEquals("<td class=\"main\">Bob</td> <td class=\"main\">Message to Bob one</td>"
				+ " <td class=\"infos\">2 days ago</td>", rows.get(0));
		Assert.assertEquals("<td class=\"main\">Moderator</td> <td class=\"main\">Message admin to modo</td>"
				+ " <td class=\"infos\">2 days ago</td>", rows.get(1));
		Assert.assertEquals("<td class=\"main\">Anonymous</td> <td class=\"main\">Message admin to anonymous</td>"
				+ " <td class=\"infos\">2 days ago</td>", rows.get(2));
		assertNotContains("<td class=\"main\">Admin</td>", body);
		Assert.assertNull(paginationBlock(body));
	}

	@Test
	public void outboxOfAPosterListsTheMessageItSent() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedMessages();
			return null;
		});
		login("bob@domain.com", "secret");
		String body = markup(GET("/forum.messages/outbox"));

		assertHeader(body, "Bob", "Outbox");
		List<String> rows = tableRows(body);
		Assert.assertEquals(1, rows.size());
		Assert.assertEquals("<td class=\"main\">Admin</td> <td class=\"main\">Message from Bob</td>"
				+ " <td class=\"infos\">2 days ago</td>", rows.get(0));
		// The viewer is never listed in its own outbox
		assertNotContains("<td class=\"main\">Bob</td>", body);
	}

	@Test
	public void outboxHasItsOwnSubtitleButNoTitleHeading() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedMessages();
			return null;
		});
		login("bob@domain.com", "secret");
		String body = markup(GET("/forum.messages/outbox"));

		// outbox.html has no <h1 id="title"> / <strong> block, unlike inbox.html
		assertHeader(body, "Bob", "Outbox");
		assertNotContains("<h1 id=\"title\">", body);
		assertNotContains("<strong>", body);
	}

	@Test
	public void outboxFetchesTheSecondPageAndStillRendersNoPagination() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedMessages();
			return null;
		});
		login("admin@domain.com", "secret");
		setPageSize(2);
		try {
			Assert.assertEquals(2, tableRows(markup(GET("/forum.messages/outbox"))).size());

			String secondPage = markup(GET("/forum.messages/outbox?page=2"));
			assertHeader(secondPage, "Admin", "Outbox");
			List<String> rows = tableRows(secondPage);
			Assert.assertEquals(1, rows.size());
			Assert.assertEquals("<td class=\"main\">Anonymous</td> <td class=\"main\">Message admin to anonymous</td>"
					+ " <td class=\"infos\">2 days ago</td>", rows.get(0));
			// The welcome flash was consumed by the first request
			assertNotContains("alert alert-success", secondPage);
			Assert.assertNull(paginationBlock(secondPage));
		} finally {
			restorePageSize();
		}
	}

	@Test
	public void outboxHasAnEmptyListWhenTheViewerHasSentNoMessages() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedMessages();
			deleteMessagesSentBy("modo@domain.com");
			return null;
		});
		login("modo@domain.com", "secret");
		String body = markup(GET("/forum.messages/outbox"));

		assertHeader(body, "Moderator", "Outbox");
		assertContains("<tbody> </tbody>", body);
		Assert.assertEquals(0, tableRows(body).size());

		// Moderator still has the message Admin sent to it
		assertContains("<td class=\"main\">Admin</td> <td class=\"main\">Message admin to modo</td>",
				markup(GET("/forum.messages/inbox")));
	}

	// ~~~~~~~~~~~~ access to the actions

	@Test
	public void inboxIsNotReachableWithoutLogin() throws Throwable {
		clearCookies();
		Response response = GET("/forum.messages/inbox");
		assertStatus(302, response);
		assertLocation(response, "/login");
		Assert.assertEquals("/forum.messages/inbox", flashOf(response).get("url"));
	}

	@Test
	public void outboxIsNotReachableWithoutLogin() throws Throwable {
		clearCookies();
		Response response = GET("/forum.messages/outbox");
		assertStatus(302, response);
		assertLocation(response, "/login");
		Assert.assertEquals("/forum.messages/outbox", flashOf(response).get("url"));
	}

	@Test
	public void theUnroutedMessagesPathIsNotFound() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");

		// conf/routes has no route for Messages; only the catch-all * /{controller}/{action} reaches it,
		// and it resolves forum.messages, not messages.
		try {
			GET("/messages/inbox");
			fail("expected NotFound for /messages/inbox");
		} catch (NotFound e) {
			assertContains("messages.inbox action not found", e.getMessage());
		}
	}

	// ~~~~~~~~~~~~ helpers

	/**
	 * Five messages: two addressed to Bob, one sent by Bob, one addressed to Moderator, and one sent
	 * by Admin to the anonymous user so that Admin's outbox has a second page.
	 */
	private static void seedMessages() {
		User bob = User.findByEmail("bob@domain.com");
		User admin = User.findByEmail("admin@domain.com");
		User modo = User.findByEmail("modo@domain.com");
		User anonymous = User.findByEmail("anonymous@domain.com");
		newMessage(admin, bob, "Message to Bob one");
		newMessage(modo, bob, "Message to Bob two");
		newMessage(bob, admin, "Message from Bob");
		newMessage(admin, modo, "Message admin to modo");
		newMessage(admin, anonymous, "Message admin to anonymous");
	}

	private static void newMessage(User from, User to, String subject) {
		Message message = new Message();
		message.from = from;
		message.to = to;
		message.at = new Date(System.currentTimeMillis() - 2 * 24 * 3600 * 1000L);
		message.subject = subject;
		message.content = "Body of " + subject;
		message.readed = Boolean.FALSE;
		message.create();
	}

	/** Removes every message addressed to the given user, so both of its pages become empty. */
	private static void deleteMessagesAddressedTo(String email) {
		User user = User.findByEmail(email);
		// GenericModel.JPAQuery.fetch() is erased by Play's javassist enhancement, so its elements
		// come back as Object and have to be cast.
		for (Object row : Message.find("to", user).fetch()) {
			((Message) row).delete();
		}
	}

	/** Removes every message the given user sent, leaving its inbox untouched. */
	private static void deleteMessagesSentBy(String email) {
		User user = User.findByEmail(email);
		// "from" is a reserved word in HQL, so find("from", user) cannot be parsed: the entity is
		// aliased, exactly as Message.outbox() does.
		for (Object row : Message.find("select m from Message m where m.from = ?1", user).fetch()) {
			((Message) row).delete();
		}
	}

	/** The th cells of the tables on the page. */
	private static List<String> tableHeaders(String body) {
		List<String> headers = new ArrayList<String>();
		Matcher m = Pattern.compile("<th[^>]*>([^<]*)</th>").matcher(body);
		while (m.find()) {
			headers.add(m.group(1));
		}
		return headers;
	}
}
