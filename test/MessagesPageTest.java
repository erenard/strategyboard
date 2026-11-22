import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.*;

import play.mvc.Http.Response;

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
 *
 * Both actions are @Check(LOGGED). A session holding anonymous@domain.com passes that check, so the two
 * actions guard on connectedUser() and answer Application.backToPreviousPage(); the cases below pin both
 * of its branches, and main.html's links to them are asserted too.
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
		Response response = GET("/messages/inbox");
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
		String body = markup(GET("/messages/inbox"));

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
		String body = markup(GET("/messages/inbox"));

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
		String body = markup(GET("/messages/inbox"));

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
			Assert.assertEquals(2, tableRows(markup(GET("/messages/inbox"))).size());

			String secondPage = markup(GET("/messages/inbox?page=2"));
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
		String body = markup(GET("/messages/outbox"));

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
		String body = markup(GET("/messages/outbox"));

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
		String body = markup(GET("/messages/outbox"));

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
			Assert.assertEquals(2, tableRows(markup(GET("/messages/outbox"))).size());

			String secondPage = markup(GET("/messages/outbox?page=2"));
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
		String body = markup(GET("/messages/outbox"));

		assertHeader(body, "Moderator", "Outbox");
		assertContains("<tbody> </tbody>", body);
		Assert.assertEquals(0, tableRows(body).size());

		// Moderator still has the message Admin sent to it
		assertContains("<td class=\"main\">Admin</td> <td class=\"main\">Message admin to modo</td>",
				markup(GET("/messages/inbox")));
	}

	// ~~~~~~~~~~~~ access to the actions

	@Test
	public void inboxIsNotReachableWithoutLogin() throws Throwable {
		clearCookies();
		Response response = GET("/messages/inbox");
		assertStatus(302, response);
		assertLocation(response, "/login");
		Assert.assertEquals("/messages/inbox", flashOf(response).get("url"));
	}

	@Test
	public void outboxIsNotReachableWithoutLogin() throws Throwable {
		clearCookies();
		Response response = GET("/messages/outbox");
		assertStatus(302, response);
		assertLocation(response, "/login");
		Assert.assertEquals("/messages/outbox", flashOf(response).get("url"));
	}

	/**
	 * conf/routes declares /messages/inbox and /messages/outbox above the catch-all, which is what
	 * @{forum.Messages.inbox()} in main.html resolves to. The /forum.messages/inbox url the catch-all
	 * used to answer is still served.
	 */
	@Test
	public void bothPagesAreReachedByTheirOwnRouteAndByTheCatchAllUrl() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");

		assertHeader(markup(GET("/messages/inbox")), "Bob", "Inbox");
		assertHeader(markup(GET("/messages/outbox")), "Bob", "Outbox");
		assertHeader(markup(GET("/forum.messages/inbox")), "Bob", "Inbox");
		assertHeader(markup(GET("/forum.messages/outbox")), "Bob", "Outbox");
	}

	/**
	 * main.html linked Messages to href="#" and never linked the outbox. Both links now point at the
	 * routes above, in both branches of #{if connectedUser.newMessagesCount > 0}.
	 */
	@Test
	public void theNavbarDropdownLinksToTheInboxAndToTheOutbox() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedMessages();
			return null;
		});
		login("bob@domain.com", "secret");
		// Bob has two unread messages -> the badge branch
		String badgeBody = markup(GET("/forums"));
		assertContains("<a href=\"/messages/inbox\"><span class=\"badge pull-right\">2</span>Messages</a>", badgeBody);
		assertContains("<a href=\"/messages/outbox\">Sent messages</a>", badgeBody);
		// The old dead link, href="#" on the badge, is gone
		assertNotContains("<a href=\"#\"><span class=\"badge pull-right\">", badgeBody);

		// Admin receives "Message from Bob", unread -> badge branch again, with 1
		clearCookies();
		login("admin@domain.com", "secret");
		String oneBadge = markup(GET("/forums"));
		assertContains("<a href=\"/messages/inbox\"><span class=\"badge pull-right\">1</span>Messages</a>", oneBadge);
		assertContains("<a href=\"/messages/outbox\">Sent messages</a>", oneBadge);

		// With nothing left in its inbox the plain branch is taken: Moderator keeps one message in
		// seedMessages(), so it is removed first
		clearCookies();
		inTransactionVoid(() -> {
			deleteMessagesAddressedTo("modo@domain.com");
			return null;
		});
		login("modo@domain.com", "secret");
		String plainBody = markup(GET("/forums"));
		assertContains("<a href=\"/messages/inbox\">Messages</a>", plainBody);
		assertContains("<a href=\"/messages/outbox\">Sent messages</a>", plainBody);
		assertNotContains("badge pull-right", plainBody);
	}

	/**
	 * The anonymous session passes @Check(LOGGED): Profile.checkAccess() puts anonymous.email in it for a
	 * @Check(VIEWER) request and Security.check("") only asks whether a row exists, so forum.Messages runs
	 * with connectedUser() == null. It answers Application.backToPreviousPage() instead of faulting on a
	 * null user; with no referer that calls Application.index(), which Play's ControllersEnhancer turns
	 * into a 302 to the home page.
	 */
	@Test
	public void anAnonymousSessionIsSentToTheHomePageFromBothPages() throws Throwable {
		clearCookies();
		assertIsOk(GET("/forums"));
		assertEquals("anonymous@domain.com", sessionUsername(GET("/forums")));

		Response inbox = GET("/messages/inbox");
		assertStatus(302, inbox);
		assertLocation(inbox, "/");
		assertNotContains(">Inbox</strong>", markup(inbox));

		Response outbox = GET("/messages/outbox");
		assertStatus(302, outbox);
		assertLocation(outbox, "/");
		assertNotContains(">Outbox</p>", markup(outbox));
	}

	/** The referer branch of backToPreviousPage(): the page that referred the visitor here. */
	@Test
	public void anAnonymousSessionWithARefererIsSentBackToThePageThatReferredIt() throws Throwable {
		clearCookies();
		assertIsOk(GET("/forums"));

		Response inbox = GETFrom("/forums/1", "/messages/inbox");
		assertStatus(302, inbox);
		assertLocation(inbox, "/forums/1");

		Response outbox = GETFrom("/forums/1/topics/1", "/messages/outbox");
		assertStatus(302, outbox);
		assertLocation(outbox, "/forums/1/topics/1");
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
