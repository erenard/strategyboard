import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

import play.test.Fixtures;

import models.forum.Attachment;
import models.forum.Message;
import models.forum.Post;
import models.forum.Topic;

/**
 * Coverage of models.forum.Attachment.
 *
 *   - toString() concatenates "mimetype:filename" then the three relations, each only when it is set:
 *     a row with nothing but a filename answers "null:fresh.txt", a row with a message answers
 *     "null:msg.txt - Message : Message[1]". The order is user, post, message.
 *   - Only filename and realfilename are @Required; downloads, size, at, mimetype and description stay null
 *     when a row is built in Java, and forum/Attachments.download() reads realfilename from the disk.
 *   - The three relations are separate @ManyToOne columns: an attachment may hang on a post, on a message,
 *     or on nothing, and Post.getAttachments() lists exactly the ones pointing at that post.
 *   - test/attachments-data.yml adds topic 6 and post 7 on top of test/data.yml, so the attachment of the
 *     moderators forum is attachment 5 on post 7.
 */
public class AttachmentModelTest extends BaseModelTest {

	@Test
	public void noAttachmentRowUntilTheFixtureFileIsLoaded() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertEquals("0", String.valueOf(Attachment.count()));
			Assert.assertEquals("[]", names(Attachment.findAll()));
			return null;
		});
	}

	@Test
	public void fixtureRowsCarryTheirFileAndTheirCounters() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("attachments-data.yml");

			Assert.assertEquals("5", String.valueOf(Attachment.count()));
			// names() would print the toString() override of the entity, not "Attachment[1]", so the rows
			// are compared through attachmentLines() instead; the toString() branch is pinned further down.
			Assert.assertEquals("[1:report.txt post=Post[1] user=Bob message=null, "
					+ "2:no-mime.txt post=Post[2] user=Admin message=null, "
					+ "3:absent.txt post=Post[3] user=Moderator message=null, "
					+ "4:large.bin post=Post[4] user=Admin message=null, "
					+ "5:view-only.txt post=Post[7] user=Bob message=null]",
					attachmentLines(Attachment.findAll()));

			Attachment small = Attachment.findById(1L);
			Assert.assertEquals("report.txt", small.filename);
			Assert.assertEquals("probe-report.txt", small.realfilename);
			Assert.assertEquals("text/plain", small.mimetype);
			Assert.assertEquals("Small attachment", small.description);
			Assert.assertEquals("1", String.valueOf(small.downloads));
			Assert.assertEquals("12", String.valueOf(small.size));
			Assert.assertEquals("2024-01-01 01:00:00.0", String.valueOf(small.at));
			Assert.assertEquals("Post[1]", small.post.toString());
			Assert.assertEquals("Bob", small.user.name);
			Assert.assertNull(small.message);
			// The three relations of the entity are printed in the order user, post, message.
			Assert.assertEquals("text/plain:report.txt - User : Bob - Post : Post[1]", small.toString());

			// Attachment 2 has no mimetype, so the first half of toString() reads "null:".
			Attachment noMime = Attachment.findById(2L);
			Assert.assertNull(noMime.mimetype);
			Assert.assertEquals("null:no-mime.txt - User : Admin - Post : Post[2]", noMime.toString());
			Assert.assertEquals("0", String.valueOf(noMime.downloads));
			Assert.assertEquals("8", String.valueOf(attachment(3L).size));
			Assert.assertEquals("70000", String.valueOf(attachment(4L).size));
			return null;
		});
	}

	@Test
	public void fixtureRowsPointAtThePostsTheirFileBelongsTo() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("attachments-data.yml");

			Assert.assertEquals("[1:report.txt post=Post[1] user=Bob message=null]",
					attachmentLines(post(1L).getAttachments()));
			Assert.assertEquals("[2:no-mime.txt post=Post[2] user=Admin message=null]",
					attachmentLines(post(2L).getAttachments()));
			Assert.assertEquals("[3:absent.txt post=Post[3] user=Moderator message=null]",
					attachmentLines(post(3L).getAttachments()));
			Assert.assertEquals("[4:large.bin post=Post[4] user=Admin message=null]",
					attachmentLines(post(4L).getAttachments()));
			// The moderators topic of test/attachments-data.yml is topic 6, its post is post 7.
			Assert.assertEquals("[5:view-only.txt post=Post[7] user=Bob message=null]",
					attachmentLines(post(7L).getAttachments()));
			Assert.assertEquals("6", String.valueOf(attachment(5L).post.topic.id));
			Assert.assertEquals("3", String.valueOf(attachment(5L).post.topic.forum.id));
			// That fixture entry never sets description, unlike attachment 1.
			Assert.assertNull(attachment(5L).description);
			return null;
		});
	}

	@Test
	public void aRowBuiltInJavaHasNothingButItsTwoRequiredNames() throws Throwable {
		inTransactionVoid(() -> {
			Attachment fresh = new Attachment();
			fresh.filename = "fresh.txt";
			fresh.realfilename = "fresh.txt";
			fresh.create();

			Assert.assertEquals("1", String.valueOf(fresh.id));
			Assert.assertNull(fresh.downloads);
			Assert.assertNull(fresh.size);
			Assert.assertNull(fresh.at);
			Assert.assertNull(fresh.mimetype);
			Assert.assertNull(fresh.description);
			Assert.assertNull(fresh.post);
			Assert.assertNull(fresh.user);
			Assert.assertNull(fresh.message);
			Assert.assertEquals("null:fresh.txt", fresh.toString());
			Assert.assertEquals("1", String.valueOf(Attachment.count()));
			return null;
		});
	}

	@Test
	public void toStringAppendsTheRelationsThatAreSet() throws Throwable {
		inTransactionVoid(() -> {
			Message message = new Message();
			message.from = user("bob@domain.com");
			message.to = user("admin@domain.com");
			message.at = new Date(1700000000000L);
			message.subject = "Hello";
			message.content = "Body of the message";
			message.create();

			Attachment onMessage = new Attachment();
			onMessage.filename = "msg.txt";
			onMessage.realfilename = "msg.txt";
			onMessage.message = message;
			onMessage.mimetype = "text/plain";
			onMessage.create();

			// No post, no user: only the message branch is appended.
			Assert.assertEquals("text/plain:msg.txt - Message : Message[1]", onMessage.toString());

			Attachment onPostAndUser = new Attachment();
			onPostAndUser.filename = "both.txt";
			onPostAndUser.realfilename = "both.txt";
			onPostAndUser.post = Post.findById(1L);
			onPostAndUser.user = user("bob@domain.com");
			onPostAndUser.message = message;
			onPostAndUser.create();

			// All three branches, in the order the entity declares them.
			Assert.assertEquals("null:both.txt - User : Bob - Post : Post[1] - Message : Message[1]",
					onPostAndUser.toString());
			// Two rows: the message itself is a Message row, not an Attachment.
			Assert.assertEquals("2", String.valueOf(Attachment.count()));
			return null;
		});
	}

	@Test
	public void anAttachmentOnAMessageIsNotListedByTheAttachmentsOfAPost() throws Throwable {
		inTransactionVoid(() -> {
			Message message = new Message();
			message.from = user("bob@domain.com");
			message.to = user("admin@domain.com");
			message.at = new Date(1700000000000L);
			message.subject = "Hello";
			message.content = "Body of the message";
			message.create();

			Attachment fresh = new Attachment();
			fresh.filename = "msg.txt";
			fresh.realfilename = "msg.txt";
			fresh.message = message;
			fresh.create();

			Assert.assertEquals("[]", attachmentLines(post(1L).getAttachments()));
			Assert.assertEquals("[1:msg.txt post=null user=null message=Message[1]]",
					attachmentLines(Attachment.findAll()));
			return null;
		});
	}

	@Test
	public void attachmentsOfANewPostAreEmptyUntilOnePointsAtIt() throws Throwable {
		inTransactionVoid(() -> {
			Post fresh = new Post(Topic.findById(1L), user("bob@domain.com"), "Fresh post", "Fresh body");
			Assert.assertEquals("[]", attachmentLines(fresh.getAttachments()));

			Attachment attachment = new Attachment();
			attachment.filename = "on-new-post.txt";
			attachment.realfilename = "on-new-post.txt";
			attachment.post = fresh;
			attachment.create();

			Assert.assertEquals("[1:on-new-post.txt post=Post[7] user=null message=null]",
					attachmentLines(fresh.getAttachments()));
			Assert.assertEquals("7", String.valueOf(fresh.id));
			return null;
		});
	}
}
