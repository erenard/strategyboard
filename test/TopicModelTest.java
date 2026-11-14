import java.lang.reflect.Field;

import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

import models.forum.Attachment;
import models.forum.Forum;
import models.forum.Post;
import models.forum.Topic;

/**
 * Coverage of models.forum.Topic.
 *
 *   - The entity has no subject field: the title a topic displays is the subject of its first post, which
 *     is what Topic.forum/Topics/show.html and Forums/show.html print. test/data.yml still writes a
 *     "subject:" key on every topic, and play.test.Fixtures only binds properties the entity declares, so
 *     that key is ignored - see fixtureTopicHasNoSubjectOfItsOwn.
 *   - views, locked and hidden default to 0 / FALSE / FALSE, and play.db.jpa.JPABase.toString() prints
 *     "Topic[id]" (no override in the entity).
 *   - getPosts() has no order by, so the posts come back in insertion order, newest reply last.
 *     getFirstPost() and getLastPost() do order by postedAt, asc and desc, and neither has a second key, so
 *     two posts carrying the same instant answer the same row for both queries - see the timestamps test.
 *   - getVoicesCount() counts distinct authors through Post.postedBy.
 *   - The constructor Topic(forum, by, subject, content) creates the topic and one post; reply() adds one.
 *     The posts field stays null until the entity is refreshed, which is what makes delete() fail.
 *   - delete() cascades to Post (cascade = ALL) only when the posts collection is loaded, and it never
 *     cascades to Attachment, whose post_id points at the post of the topic.
 */
public class TopicModelTest extends BaseModelTest {

	@Test
	public void fixtureTopicHasNoSubjectOfItsOwn() throws Throwable {
		inTransactionVoid(() -> {
			Topic t1 = Topic.findById(1L);

			Assert.assertEquals("Topic[1]", t1.toString());
			Assert.assertEquals("mainForum1", t1.forum.toString());
			Assert.assertEquals("0", String.valueOf(t1.views));
			Assert.assertEquals("false", String.valueOf(t1.locked));
			Assert.assertEquals("false", String.valueOf(t1.hidden));
			// The title comes from the first post, not from a field of Topic.
			Assert.assertEquals("Topic Normal", t1.getFirstPost().subject);

			try {
				Field subject = Topic.class.getField("subject");
				Assert.fail("Topic declares a subject field: " + subject);
			} catch (NoSuchFieldException expected) {
				Assert.assertEquals("subject", expected.getMessage());
			}
			return null;
		});
	}

	@Test
	public void flagsOfEveryFixtureTopic() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertEquals("false", String.valueOf(topic(1L).locked));
			Assert.assertEquals("true", String.valueOf(topic(2L).locked));
			Assert.assertEquals("true", String.valueOf(topic(3L).hidden));
			Assert.assertEquals("false", String.valueOf(topic(4L).hidden));
			Assert.assertEquals("5", String.valueOf(Topic.count()));
			Assert.assertEquals("[Topic[1], Topic[2], Topic[3], Topic[4], Topic[5]]", names(Topic.findAll()));
			return null;
		});
	}

	@Test
	public void postsAreListedInInsertionOrder() throws Throwable {
		inTransactionVoid(() -> {
			Topic t1 = Topic.findById(1L);

			Assert.assertEquals("2", String.valueOf(t1.getPostsCount()));
			Assert.assertEquals("[1:Topic Normal@2024-01-01 01:00:00.0, 2:Topic Normal reply@2024-01-02 01:00:00.0]",
					postLines(t1.getPosts(1, 10)));
			// No order by: page 2 of size 1 is the second inserted post, page 3 is empty.
			Assert.assertEquals("[1:Topic Normal@2024-01-01 01:00:00.0]", postLines(t1.getPosts(1, 1)));
			Assert.assertEquals("[2:Topic Normal reply@2024-01-02 01:00:00.0]", postLines(t1.getPosts(2, 1)));
			Assert.assertEquals("[]", postLines(t1.getPosts(3, 1)));
			return null;
		});
	}

	@Test
	public void firstAndLastPostAreOrderedByPostedAt() throws Throwable {
		inTransactionVoid(() -> {
			Topic t1 = Topic.findById(1L);
			Topic t2 = Topic.findById(2L);
			Topic news1 = Topic.findById(4L);

			Assert.assertEquals("1:Topic Normal@2024-01-01 01:00:00.0 by Bob", describePost(t1.getFirstPost()));
			Assert.assertEquals("2:Topic Normal reply@2024-01-02 01:00:00.0 by Moderator", describePost(t1.getLastPost()));
			Assert.assertEquals("3:Topic Locked@2024-01-03 01:00:00.0 by Moderator", describePost(t2.getLastPost()));
			// A topic with a single post answers the same post twice.
			Assert.assertEquals("5:First news@2024-01-05 01:00:00.0 by Admin", describePost(news1.getFirstPost()));
			Assert.assertEquals(news1.getFirstPost().id, news1.getLastPost().id);
			return null;
		});
	}

	@Test
	public void voicesCountCountsTheDistinctAuthorsOfTheTopic() throws Throwable {
		inTransactionVoid(() -> {
			Topic t1 = Topic.findById(1L);
			Topic t2 = Topic.findById(2L);
			Topic t3 = Topic.findById(3L);
			Topic news1 = Topic.findById(4L);

			// t1 is started by Bob and answered by the moderator: two voices, two posts.
			Assert.assertEquals("2", String.valueOf(t1.getVoicesCount()));
			Assert.assertEquals("1", String.valueOf(t2.getVoicesCount()));
			Assert.assertEquals("1", String.valueOf(t3.getVoicesCount()));
			Assert.assertEquals("1", String.valueOf(news1.getVoicesCount()));
			return null;
		});
	}

	@Test
	public void constructorCreatesATopicWithOnePostAndTheDefaults() throws Throwable {
		inTransactionVoid(() -> {
			Forum mainForum = Forum.findById(1L);
			Topic fresh = new Topic(mainForum, user("bob@domain.com"), "Fresh topic", "Fresh content");

			Assert.assertEquals("6", String.valueOf(fresh.id));
			Assert.assertEquals("1", String.valueOf(fresh.forum.id));
			Assert.assertEquals("0", String.valueOf(fresh.views));
			Assert.assertEquals("false", String.valueOf(fresh.locked));
			Assert.assertEquals("false", String.valueOf(fresh.hidden));
			Assert.assertEquals("1", String.valueOf(fresh.getPostsCount()));
			Assert.assertEquals("Bob", fresh.getFirstPost().postedBy.name);
			Assert.assertEquals("Fresh topic", fresh.getFirstPost().subject);
			Assert.assertNotNull(fresh.getFirstPost().postedAt);
			// The subject given to the constructor only reaches the post, and the posts field is untouched.
			Assert.assertNull(fresh.posts);
			Assert.assertEquals("6", String.valueOf(Topic.count()));
			return null;
		});
	}

	@Test
	public void replyAddsAPostOfItsOwnToTheTopic() throws Throwable {
		inTransactionVoid(() -> {
			Topic fresh = new Topic(forum(1L), user("bob@domain.com"), "Fresh topic", "Fresh content");
			Post replied = fresh.reply(user("modo@domain.com"), "Reply", "Reply content");

			// The topic's own post takes id 7, the reply id 8, and no query runs between the two persists.
			Assert.assertEquals("8", String.valueOf(replied.id));
			Assert.assertEquals("6", String.valueOf(replied.topic.id));
			Assert.assertEquals("Reply", replied.subject);
			Assert.assertEquals("Reply content", replied.content);
			Assert.assertEquals("Moderator", replied.postedBy.name);
			Assert.assertEquals("2", String.valueOf(fresh.getPostsCount()));
			// getVoicesCount() counts distinct authors, so two accounts answering twice give 2 voices.
			Assert.assertEquals("2", String.valueOf(fresh.getVoicesCount()));
			Assert.assertEquals("[Post[7], Post[8]]", names(fresh.getPosts(1, 10)));
			Assert.assertEquals("[Post[7], Post[8]]", names(((Topic) topic(6L).refresh()).posts));
			return null;
		});
	}

	@Test
	public void firstAndLastPostOfATopicOnlyOrderTheReplyByItsTimestamp() throws Throwable {
		inTransactionVoid(() -> {
			Topic fresh = new Topic(forum(1L), user("bob@domain.com"), "Fresh topic", "Fresh content");
			Post replied = fresh.reply(user("modo@domain.com"), "Reply", "Reply content");
			Post own = post(fresh.getFirstPost().id);

			// forum/Topics.reply() only builds a Post, whose constructor stamps postedAt with the clock. The
			// two stamps fall in the same millisecond or one millisecond apart depending on how fast the JVM
			// reaches the second constructor, so the answer of the two queries cannot be pinned without
			// setting the dates - which is what forum/Topics.reply() never does.
			Date same = new Date(1700000000000L);
			own.postedAt = same;
			own.save();
			replied.postedAt = same;
			replied.save();

			// Two rows of the same instant, and neither query has a second sort key: MariaDB answers the row
			// it reached first for both, so the topic's own post is reported as both the first and the last.
			Assert.assertEquals("7", String.valueOf(fresh.getFirstPost().id));
			Assert.assertEquals("7", String.valueOf(fresh.getLastPost().id));
			Assert.assertEquals("1700000000000", String.valueOf(fresh.getFirstPost().postedAt.getTime()));
			Assert.assertEquals("1700000000000", String.valueOf(fresh.getLastPost().postedAt.getTime()));

			// A later stamp makes the reply the newest post, and the date survives as a java.util.Date.
			replied.postedAt = new Date(same.getTime() + 1000L);
			replied.save();
			Assert.assertEquals("7", String.valueOf(fresh.getFirstPost().id));
			Assert.assertEquals("8", String.valueOf(fresh.getLastPost().id));
			Assert.assertEquals("Reply", fresh.getLastPost().subject);
			Assert.assertEquals("java.util.Date", post(replied.id).postedAt.getClass().getName());
			Assert.assertEquals("1700000001000", String.valueOf(post(replied.id).postedAt.getTime()));

			// An earlier stamp makes the reply the oldest post, so the topic's own post becomes the newest.
			replied.postedAt = new Date(same.getTime() - 1000L);
			replied.save();
			Assert.assertEquals("8", String.valueOf(fresh.getFirstPost().id));
			Assert.assertEquals("7", String.valueOf(fresh.getLastPost().id));
			Assert.assertEquals("Reply", fresh.getFirstPost().subject);
			Assert.assertEquals("Fresh topic", fresh.getLastPost().subject);

			// getPosts() has no order by at all, so the window keeps the insertion order whatever the dates do.
			Assert.assertEquals("[Post[7], Post[8]]", names(fresh.getPosts(1, 10)));
			Assert.assertEquals("[7:Fresh topic@Tue Nov 14 23:13:20 CET 2023, 8:Reply@Tue Nov 14 23:13:19 CET 2023]",
					postLines(fresh.getPosts(1, 10)));
			return null;
		});
	}

	@Test
	public void emptyConstructorLeavesEveryFieldAtItsDefault() throws Throwable {
		// forum/Topics.create() binds the posted topic through this constructor before overwriting the fields.
		inTransactionVoid(() -> {
			Topic blank = new Topic();

			Assert.assertNull(blank.forum);
			Assert.assertNull(blank.posts);
			Assert.assertEquals("0", String.valueOf(blank.views));
			Assert.assertEquals("false", String.valueOf(blank.locked));
			Assert.assertEquals("false", String.valueOf(blank.hidden));
			Assert.assertNull(blank.id);
			Assert.assertEquals("5", String.valueOf(Topic.count()));
			return null;
		});
	}

	@Test
	public void deleteOfACreatedTopicIsRefusedWhileItsPostsFieldIsNull() throws Throwable {
		// cascade = CascadeType.ALL only reaches the rows Hibernate knows about, and the posts field of a
		// topic built in Java is never loaded, so the DELETE of the topic runs while its own post still
		// points at it.
		assertTransactionFailureContains("a foreign key constraint fails (`www`.`Post`", () -> {
			Topic fresh = new Topic(forum(1L), user("bob@domain.com"), "Fresh topic", "Fresh content");

			Assert.assertNull(fresh.posts);
			Assert.assertEquals("6", String.valueOf(fresh.id));
			Assert.assertEquals("7", String.valueOf(Post.count()));

			fresh.delete();
			return null;
		});
	}

	@Test
	public void theTransactionOfARefusedTopicDeleteIsRolledBack() throws Throwable {
		// The rollback undoes the topic and the post that transaction inserted as well, so nothing of it
		// survives into the committed state.
		inTransactionVoid(() -> {
			Assert.assertEquals("5", String.valueOf(Topic.count()));
			Assert.assertEquals("6", String.valueOf(Post.count()));
			Assert.assertNull(topic(6L));
			return null;
		});
	}

	@Test
	public void deleteOfATopicWhosePostsAreLoadedRemovesThem() throws Throwable {
		inTransactionVoid(() -> {
			Topic fresh = new Topic(forum(1L), user("bob@domain.com"), "Fresh topic", "Fresh content");
			Topic loaded = (Topic) fresh.refresh();
			// refresh() hands back the post Hibernate inserted, so the field is populated this time.
			Assert.assertEquals(1, loaded.posts.size());
			Assert.assertEquals("Fresh topic", loaded.posts.get(0).subject);

			loaded.delete();

			Assert.assertEquals("5", String.valueOf(Topic.count()));
			Assert.assertEquals("6", String.valueOf(Post.count()));
			Assert.assertNull(topic(6L));
			return null;
		});
	}

	@Test
	public void deleteOfAFixtureTopicRemovesItsPosts() throws Throwable {
		inTransactionVoid(() -> {
			Topic t1 = topic(1L);
			Assert.assertEquals("2", String.valueOf(t1.getPostsCount()));

			t1.delete();

			Assert.assertEquals("4", String.valueOf(Topic.count()));
			Assert.assertEquals("4", String.valueOf(Post.count()));
			Assert.assertNull(topic(1L));
			// Forum 1 keeps the locked topic and the hidden one, ordered by their newest post.
			Assert.assertEquals("[3:Topic Hidden, 2:Topic Locked]", topicLines(forum(1L).getTopics(1, 10)));
			return null;
		});
	}

	@Test
	public void deleteOfATopicIsRefusedWhileAnAttachmentPointsAtOneOfItsPosts() throws Throwable {
		assertTransactionFailureContains("a foreign key constraint fails (`www`.`Attachment`", () -> {
			Topic topicWithAttachment = (Topic) topic(1L).refresh();
			Assert.assertEquals("[1:Topic Normal@2024-01-01 01:00:00.0, 2:Topic Normal reply@2024-01-02 01:00:00.0]",
					postLines(topicWithAttachment.posts));

			Attachment fresh = new Attachment();
			fresh.filename = "fresh.txt";
			fresh.realfilename = "fresh.txt";
			fresh.post = post(1L);
			fresh.create();
			Assert.assertEquals("1", String.valueOf(Attachment.count()));

			// Topic cascades to Post, Post never cascades to Attachment, so the DELETE of post 1 is refused.
			topicWithAttachment.delete();
			return null;
		});
	}

	@Test
	public void theTransactionOfATopicDeleteRefusedByAnAttachmentIsRolledBack() throws Throwable {
		// Neither the topic, nor its posts, nor the attachment row of that transaction survive it.
		inTransactionVoid(() -> {
			Assert.assertEquals("5", String.valueOf(Topic.count()));
			Assert.assertEquals("6", String.valueOf(Post.count()));
			Assert.assertEquals("0", String.valueOf(Attachment.count()));
			Assert.assertEquals("[1:Topic Normal@2024-01-01 01:00:00.0, 2:Topic Normal reply@2024-01-02 01:00:00.0]",
					postLines(topic(1L).getPosts(1, 10)));
			return null;
		});
	}

	@Test
	public void topicsOfAForumWithNoTopicAreEmpty() throws Throwable {
		inTransactionVoid(() -> {
			Forum modoForum = Forum.findById(3L);

			Assert.assertEquals("0", String.valueOf(modoForum.getTopicsCount()));
			Assert.assertEquals("[]", topicLines(modoForum.getTopics(1, 10)));
			return null;
		});
	}
}
