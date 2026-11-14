import org.junit.*;

import models.forum.Message;
import models.forum.User;

/**
 * Coverage of models.forum.Message, the query behind forum/Messages/inbox.html and outbox.html.
 *
 * Message.outbox() and Message.outboxCount() are dead code: Messages.outbox() used Message.inbox()
 * until it was fixed, so nothing called them. They were also broken: find("from", user) and
 * count("from", user) build "from Message where from = ?1", and "from" is a reserved word in HQL.
 * Both now alias the entity, which makes m.from a property path.
 *
 * The two pages never filter on readed: the flag is only what User.getNewMessagesCount() counts, so a
 * message that was read stays in the list of its mailbox. at and subject carry no column constraint
 * either, so a row without them is stored and listed - see aMessageWithoutItsDateOrSubjectIsStillStoredAndListed.
 */
public class MessageTest extends BaseForumViewTest {

	@Test
	public void inboxListsTheMessagesAddressedToTheUserInInsertionOrder() throws Throwable {
		inTransactionVoid(() -> {
			seedMessages();
			Assert.assertEquals("[Message to Bob one, Message to Bob two]", names(Message.inbox(bob(), 1, 10)));
			Assert.assertEquals("[Message from Bob]", names(Message.inbox(admin(), 1, 10)));
			Assert.assertEquals("[Message admin to modo]", names(Message.inbox(modo(), 1, 10)));
			Assert.assertEquals("2", countString(Message.inboxCount(bob())));
			Assert.assertEquals("1", countString(Message.inboxCount(admin())));
			return null;
		});
	}

	@Test
	public void outboxListsTheMessagesTheUserSentToOthers() throws Throwable {
		inTransactionVoid(() -> {
			seedMessages();
			// The viewer is never in its own outbox: only messages it sent are listed
			Assert.assertEquals("[Message from Bob]", names(Message.outbox(bob(), 1, 10)));
			Assert.assertEquals("[Message to Bob one, Message admin to modo]", names(Message.outbox(admin(), 1, 10)));
			Assert.assertEquals("[Message to Bob two]", names(Message.outbox(modo(), 1, 10)));
			return null;
		});
	}

	@Test
	public void outboxCountCountsTheMessagesTheUserSent() throws Throwable {
		inTransactionVoid(() -> {
			seedMessages();
			Assert.assertEquals("1", countString(Message.outboxCount(bob())));
			Assert.assertEquals("2", countString(Message.outboxCount(admin())));
			Assert.assertEquals("1", countString(Message.outboxCount(modo())));
			return null;
		});
	}

	@Test
	public void bothPagesOfAUserWithoutMessagesAreEmpty() throws Throwable {
		// The anonymous user of test/data.yml never sends nor receives anything
		inTransactionVoid(() -> {
			User anonymous = User.findByEmail("anonymous@domain.com");

			Assert.assertEquals("[]", names(Message.inbox(anonymous, 1, 10)));
			Assert.assertEquals("[]", names(Message.outbox(anonymous, 1, 10)));
			Assert.assertEquals("0", countString(Message.inboxCount(anonymous)));
			Assert.assertEquals("0", countString(Message.outboxCount(anonymous)));
			return null;
		});
	}

	@Test
	public void outboxPaginatesByPageAndPageSize() throws Throwable {
		inTransactionVoid(() -> {
			seedMessages();
			Assert.assertEquals("[Message to Bob one, Message admin to modo]", names(Message.outbox(admin(), 1, 2)));
			Assert.assertEquals("[]", names(Message.outbox(admin(), 2, 2)).toString());
			Assert.assertEquals("[]", names(Message.outbox(admin(), 3, 2)).toString());
			return null;
		});
	}

	@Test
	public void unreadCountOnlyCountsTheRowsAddressedToTheUser() throws Throwable {
		// forum/Messages/inbox.html asks User.getNewMessagesCount(), which is Message.count("to = ?1 and readed = false").
		inTransactionVoid(() -> {
			seedMessages();

			// Bob is the only addressee of two messages, and nobody has read any of them yet.
			Assert.assertEquals("2", countString(Message.count("to = ?1 and readed = false", bob())));
			Assert.assertEquals("2", countString(bob().getNewMessagesCount()));
			Assert.assertEquals("1", countString(Message.count("to = ?1 and readed = false", admin())));
			Assert.assertEquals("1", countString(admin().getNewMessagesCount()));
			Assert.assertEquals("1", countString(Message.count("to = ?1 and readed = false", modo())));
			Assert.assertEquals("1", countString(modo().getNewMessagesCount()));
			Assert.assertEquals("0", countString(Message.count("to = ?1 and readed = false", anonymous())));
			Assert.assertEquals("0", countString(anonymous().getNewMessagesCount()));

			// readed defaults to FALSE, so the complement of the query answers nothing at first.
			Assert.assertEquals("0", countString(Message.count("to = ?1 and readed = true", bob())));
			Assert.assertEquals("2", countString(Message.inboxCount(bob())));
			return null;
		});
	}

	@Test
	public void readedByTheAddresseeKeepsTheMessageInTheInbox() throws Throwable {
		// The flag is what the badge counts, not what the page lists: inbox() never filters on it.
		inTransactionVoid(() -> {
			seedMessages();

			Message second = Message.findById(2L);
			Assert.assertEquals("Message[2]", second.toString());
			Assert.assertEquals("Message to Bob two", second.subject);
			Assert.assertEquals("Body of Message to Bob two", second.content);
			Assert.assertEquals("false", String.valueOf(second.readed));

			second.readed = Boolean.TRUE;
			second.save();

			Message reread = Message.findById(2L);
			Assert.assertEquals("true", String.valueOf(reread.readed));
			Assert.assertEquals("Message[2]", reread.toString());
			Assert.assertEquals("1", countString(Message.count("to = ?1 and readed = false", bob())));
			Assert.assertEquals("1", countString(bob().getNewMessagesCount()));
			Assert.assertEquals("1", countString(Message.count("to = ?1 and readed = true", bob())));

			// Both rows are still listed, in the order they were inserted, and the count of the page is unchanged.
			Assert.assertEquals("2", countString(Message.inboxCount(bob())));
			Assert.assertEquals("[1:Message to Bob one:readed=false, 2:Message to Bob two:readed=true]",
					readedLines(Message.inbox(bob(), 1, 10)));

			// Reading the mailbox of one user never touches the badge of another.
			Assert.assertEquals("1", countString(Message.count("to = ?1 and readed = false", admin())));
			return null;
		});
	}

	@Test
	public void theDateOfAMessageReadsBackAsJavaUtilDateWithItsMillis() throws Throwable {
		inTransactionVoid(() -> {
			Message message = new Message();
			message.from = bob();
			message.to = admin();
			message.at = new java.util.Date(1700000000000L);
			message.subject = "Hello";
			// @Lob content is read back exactly as it was stored, line break and blank line included.
			message.content = "line one\nline two\n\nline four";
			message.create();

			Message stored = Message.findById(1L);
			// Message.at is a plain java.util.Date, so the value a caller built survives the round trip.
			Assert.assertEquals("java.util.Date", stored.at.getClass().getName());
			Assert.assertEquals("1700000000000", String.valueOf(stored.at.getTime()));
			Assert.assertEquals("Message[1]", stored.toString());
			Assert.assertEquals("line one\nline two\n\nline four", stored.content);
			Assert.assertEquals("java.lang.String", stored.content.getClass().getName());
			Assert.assertEquals("Hello", stored.subject);
			Assert.assertEquals("Bob", stored.from.name);
			Assert.assertEquals("Admin", stored.to.name);
			return null;
		});
	}

	@Test
	public void anUnsavedMessageCarriesNothingAtAll() throws Throwable {
		// forum/Messages.send() binds the fields of a new Message through this constructor.
		Message bare = new Message();

		Assert.assertNull(bare.id);
		Assert.assertNull(bare.at);
		Assert.assertNull(bare.subject);
		Assert.assertNull(bare.content);
		Assert.assertNull(bare.from);
		Assert.assertNull(bare.to);
		// readed is the only field with a default, and it is FALSE: a new message starts unread.
		Assert.assertEquals("false", String.valueOf(bare.readed));
		Assert.assertEquals("Message[]", bare.toString());
		Assert.assertEquals("at,content,from,id,readed,subject,to,willBeSaved",
				java.util.Arrays.stream(Message.class.getFields()).map(f -> f.getName()).sorted()
						.reduce((a, b) -> a + "," + b).orElse("(none)"));
	}

	@Test
	public void aMessageWithoutItsDateOrSubjectIsStillStoredAndListed() throws Throwable {
		// @Required is only checked by the controller binding, so a row missing at or subject reaches the table.
		inTransactionVoid(() -> {
			Message noDate = new Message();
			noDate.from = bob();
			noDate.to = admin();
			noDate.subject = "No date";
			noDate.create();

			Assert.assertEquals("1", String.valueOf(noDate.id));
			Assert.assertEquals("Message[1]", noDate.toString());
			Message storedNoDate = Message.findById(1L);
			Assert.assertNull(storedNoDate.at);
			Assert.assertEquals("false", String.valueOf(storedNoDate.readed));
			Assert.assertNull(storedNoDate.content);
			// find("to", user) has no order by and no filter, so a row of null date is listed and counted.
			Assert.assertEquals("[No date]", names(Message.inbox(admin(), 1, 10)));
			Assert.assertEquals("1", countString(Message.inboxCount(admin())));
			Assert.assertEquals("1", countString(Message.count("to = ?1 and readed = false", admin())));

			Message noSubject = new Message();
			noSubject.from = bob();
			noSubject.to = admin();
			noSubject.at = new java.util.Date(1700000000000L);
			noSubject.create();

			Assert.assertEquals("2", String.valueOf(noSubject.id));
			Message storedNoSubject = Message.findById(2L);
			Assert.assertNull(storedNoSubject.subject);
			Assert.assertNull(storedNoSubject.content);
			Assert.assertEquals("1700000000000", String.valueOf(storedNoSubject.at.getTime()));
			// A null subject prints as null, and the row of null subject sorts nowhere: both stay in insertion order.
			Assert.assertEquals("[No date, null]", names(Message.inbox(admin(), 1, 10)));
			Assert.assertEquals("2", countString(Message.count()));
			return null;
		});
	}

	// ~~~~~~~~~~~~ helpers

	private static void seedMessages() {
		newMessage(admin(), bob(), "Message to Bob one");
		newMessage(modo(), bob(), "Message to Bob two");
		newMessage(bob(), admin(), "Message from Bob");
		newMessage(admin(), modo(), "Message admin to modo");
	}

	private static void newMessage(User from, User to, String subject) {
		Message message = new Message();
		message.from = from;
		message.to = to;
		message.at = new java.util.Date(System.currentTimeMillis() - 2 * 24 * 3600 * 1000L);
		message.subject = subject;
		message.content = "Body of " + subject;
		message.readed = Boolean.FALSE;
		message.create();
	}

	private static User bob() {
		return User.findByEmail("bob@domain.com");
	}

	private static User admin() {
		return User.findByEmail("admin@domain.com");
	}

	private static User modo() {
		return User.findByEmail("modo@domain.com");
	}

	/** The subjects of a message list, whitespace collapsed, as "[a, b]". A row without a subject prints null. */
	private static String names(java.util.List<Message> messages) {
		java.util.List<String> subjects = new java.util.ArrayList<String>();
		for (Object row : (java.util.List<?>) messages) {
			subjects.add(String.valueOf(((Message) row).subject).replaceAll("\\s+", " "));
		}
		return subjects.toString();
	}

	/** id, subject and readed flag of every row of a message list, as "[1:a:readed=false, 2:b:readed=true]". */
	private static String readedLines(java.util.List<Message> messages) {
		java.util.List<String> rows = new java.util.ArrayList<String>();
		for (Object row : (java.util.List<?>) messages) {
			Message message = (Message) row;
			rows.add(message.id + ":" + message.subject + ":readed=" + message.readed);
		}
		return rows.toString();
	}

	private static User anonymous() {
		return User.findByEmail("anonymous@domain.com");
	}

	private static String countString(long count) {
		return String.valueOf(count);
	}
}
