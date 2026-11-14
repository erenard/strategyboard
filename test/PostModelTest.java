import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

import play.test.Fixtures;

import models.forum.Attachment;
import models.forum.Post;
import models.forum.Topic;

/**
 * Coverage of models.forum.Post.
 *
 *   - The entity stores the subject, the content (@Lob), postedAt and editedAt, a @OneToOne postedBy and a
 *     @ManyToOne topic. play.db.jpa.JPABase.toString() prints "Post[id]", which is what Attachment.toString()
 *     embeds.
 *   - A fixture post carries a java.sql.Timestamp (the @As("yyyy-MM-dd") key of test/data.yml is parsed by
 *     play.data.binding.Binder) and never sets editedAt, so editedAt is null until forum/Topics edits a post.
 *   - The constructor sets postedAt to now and creates the row; the no-arg constructor leaves everything null,
 *     which is the state forum/Topics.reply() fills in from the request.
 *   - getAttachments() is Attachment.find("post", this) with no order by: the rows come back in insertion
 *     order, and a post without attachment answers [].
 */
public class PostModelTest extends BaseModelTest {

	@Test
	public void fixturePostCarriesSubjectTimestampAuthorAndTopic() throws Throwable {
		inTransactionVoid(() -> {
			Post p1 = Post.findById(1L);

			Assert.assertEquals("Topic Normal", p1.subject);
			Assert.assertEquals("First post of the normal topic.", p1.content);
			// The @As("yyyy-MM-dd") key parses as midnight, and the value read back prints one hour later:
			// the JDBC connection of MariaDB runs on UTC while the JVM default zone is CET (+1h in January).
			// The whole test suite sees the same skew, so every fixture date prints at 01:00:00.0.
			Assert.assertEquals("2024-01-01 01:00:00.0", String.valueOf(p1.postedAt));
			Assert.assertNull(p1.editedAt);
			Assert.assertEquals("Bob", p1.postedBy.name);
			Assert.assertEquals("Topic[1]", p1.topic.toString());
			Assert.assertEquals("Post[1]", p1.toString());
			Assert.assertEquals("6", String.valueOf(Post.count()));
			Assert.assertEquals("[Post[1], Post[2], Post[3], Post[4], Post[5], Post[6]]", names(Post.findAll()));
			return null;
		});
	}

	@Test
	public void everyFixturePostBelongsToATopicThatHasItAsItsFirstPost() throws Throwable {
		inTransactionVoid(() -> {
			// Topic.getPosts() and Post.topic agree on the pairing, so topic 1 owns posts 1 and 2.
			Assert.assertEquals("1", String.valueOf(post(1L).topic.id));
			Assert.assertEquals("1", String.valueOf(post(2L).topic.id));
			Assert.assertEquals("2", String.valueOf(post(3L).topic.id));
			Assert.assertEquals("3", String.valueOf(post(4L).topic.id));
			Assert.assertEquals("4", String.valueOf(post(5L).topic.id));
			Assert.assertEquals("5", String.valueOf(post(6L).topic.id));
			return null;
		});
	}

	@Test
	public void aPostWithoutAttachmentAnswersAnEmptyList() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertEquals("[]", attachmentLines(post(1L).getAttachments()));
			Assert.assertEquals("[]", attachmentLines(post(6L).getAttachments()));
			Assert.assertEquals("0", String.valueOf(Attachment.count()));
			return null;
		});
	}

	@Test
	public void getAttachmentsListsTheRowsOfThePostInInsertionOrder() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("attachments-data.yml");

			// test/attachments-data.yml gives attachment 1 to post 1 and attachment 5 to post 7.
			Assert.assertEquals("[1:report.txt post=Post[1] user=Bob message=null]",
					attachmentLines(post(1L).getAttachments()));
			Assert.assertEquals("[5:view-only.txt post=Post[7] user=Bob message=null]",
					attachmentLines(post(7L).getAttachments()));
			// Post 5 and post 6 belong to the news topics and carry no attachment row.
			Assert.assertEquals("[]", attachmentLines(post(5L).getAttachments()));
			Assert.assertEquals("[]", attachmentLines(post(6L).getAttachments()));
			return null;
		});
	}

	@Test
	public void constructorSetsPostedAtAndCreatesTheRow() throws Throwable {
		inTransactionVoid(() -> {
			Topic t1 = Topic.findById(1L);
			Post fresh = new Post(t1, user("bob@domain.com"), "Fresh post", "Fresh body");

			Assert.assertEquals("7", String.valueOf(fresh.id));
			Assert.assertEquals("Fresh post", fresh.subject);
			Assert.assertEquals("Fresh body", fresh.content);
			Assert.assertEquals("1", String.valueOf(fresh.topic.id));
			Assert.assertEquals("Bob", fresh.postedBy.name);
			Assert.assertNull(fresh.editedAt);
			// A java.util.Date, not the timestamp a fixture produces.
			Assert.assertEquals("java.util.Date", fresh.postedAt.getClass().getName());
			Assert.assertNotNull(fresh.postedAt);
			Assert.assertEquals("[]", attachmentLines(fresh.getAttachments()));
			Assert.assertEquals("7", String.valueOf(Post.count()));
			// The topic of the constructor now holds its two posts: the fixture reply and the new one.
			Assert.assertEquals("3", String.valueOf(t1.getPostsCount()));
			return null;
		});
	}

	@Test
	public void constructorOfAPostWithATimestampFromTheRequestKeepsIt() throws Throwable {
		inTransactionVoid(() -> {
			// forum/Topics.reply() reassigns postedAt after the constructor ran; the constructor alone
			// stamps the moment of creation.
			Post fresh = new Post(topic(1L), user("bob@domain.com"), "Fresh post", "Fresh body");
			Date stamped = fresh.postedAt;
			Date forced = new Date(1700000000000L);
			fresh.postedAt = forced;
			fresh.save();

			Assert.assertEquals("1700000000000", String.valueOf(forced.getTime()));
			// The stamp the constructor put in is the moment of creation, later than the 2023 date.
			Assert.assertTrue(forced.getTime() < stamped.getTime());
			// A java.util.Date the test put in is read back as a java.util.Date. The YAML fixtures write
			// their dates as java.sql.Timestamp, which is why postLines() prints "01:00:00.0" for them.
			Post stored = post(fresh.id);
			Assert.assertEquals(java.util.Date.class, stored.postedAt.getClass());
			Assert.assertEquals(forced.getTime(), stored.postedAt.getTime());
			Assert.assertEquals(java.sql.Timestamp.class, post(1L).postedAt.getClass());
			return null;
		});
	}

	@Test
	public void emptyConstructorLeavesEveryFieldNull() throws Throwable {
		inTransactionVoid(() -> {
			Post blank = new Post();

			Assert.assertNull(blank.id);
			Assert.assertNull(blank.subject);
			Assert.assertNull(blank.content);
			Assert.assertNull(blank.postedAt);
			Assert.assertNull(blank.editedAt);
			Assert.assertNull(blank.postedBy);
			Assert.assertNull(blank.topic);
			Assert.assertEquals("6", String.valueOf(Post.count()));
			return null;
		});
	}

	@Test
	public void contentOfALobFieldSurvivesAMultiLineBody() throws Throwable {
		inTransactionVoid(() -> {
			Post fresh = new Post(topic(1L), user("bob@domain.com"), "Fresh post",
					"line one\nline two\n\nline four");

			Assert.assertEquals("line one\nline two\n\nline four", post(fresh.id).content);
			return null;
		});
	}
}
