import org.junit.Test;

import play.mvc.Http.Response;

import models.forum.Forum;
import models.forum.Post;
import models.forum.Topic;

/**
 * Coverage of the moderation flags of controllers.forum.Topics - lock(), hide(), move() - and of the guards
 * that read them: reply() and createReply().
 *
 * The defect these suites pin: lock(), hide() and move() used to change the field on the entity only.
 * Play 1.7.1 does not flush a plain field mutation on a request that ends in a controller-to-controller
 * redirect, so the row kept its old value while flash.success() announced the change. Every guard that
 * reads topic.locked or topic.hidden afterwards saw the old value: a moderator locked a topic and Bob
 * could still reply to it, a hidden topic stayed visible, a moved topic stayed in its source forum.
 * Forums.lock() and Topics.show() already call save() for the same reason (see the comments there); these
 * three now do too, and the assertions below read the row rather than the status code, which is what
 * ModeratorTest.canLockATopic() left unchecked.
 *
 * Branches and behaviors pinned below:
 *   - lock() (GET /forums/{forumId}/topics/{topicId}/lock) flips Topic.locked in both directions and answers
 *     302 to /forums/{forumId} with success.topic.locked / success.topic.unlocked resolved from conf/messages.
 *   - hide() (POST) flips Topic.hidden the same way, and move() (POST /forums/{forumId}/topics/{topicId}/move/{toForumId})
 *     writes Topic.forum. forumId stays only the redirect target.
 *   - once the flag is stored, reply() and createReply() refuse with error.topic.locked and create no Post.
 *     The refusal is caused by the stored flag, not by the topic id: the same request on an unlocked topic
 *     is accepted and stores a Post.
 *   - app/views/forum/Topics/show.html drops the Reply link for a topic locked through the UI, which is the
 *     #{ifnot topic.locked} branch TopicsPageTest only ever exercised on a fixture-locked topic.
 *   - Topics.show() forbids a hidden topic below Permission.moderate (403 for anonymous and for a
 *     replyer) and renders it for a moderator or an administrator, the level Forums.show() uses to list
 *     it; Forums.show() drops it from the visible list of a user below that level through
 *     forum.getVisibleTopics().
 *   - lock() does not spare a hidden topic: the only guard is @Check(Profile.MODERATOR).
 *   - notFoundIfNull() covers the three actions with an unknown topicId, and move() with an unknown toForumId.
 *   - An anonymous GET is redirected to /login with flash("url") = the lock path; a replyer is forbidden from
 *     moderating, a moderator is not.
 *
 * The suites each start from test/data.yml: forums 1..5, topics 1..5 (t1 unlocked with p1 and p2, t2 locked
 * with p3, t3 hidden with p4, news1 and news2 in forum 5), posts 1..6, so a reply written by a method is
 * always Post id 7.
 */
public class TopicsLockTest extends BaseForumViewTest {

	private static final String LOCKED_FLASH = "The topic has been locked.";
	private static final String UNLOCKED_FLASH = "The topic has been unlocked.";
	private static final String HIDDEN_FLASH = "The topic has been hidden.";
	private static final String RESTORED_FLASH = "The topic has been restored.";
	private static final String MOVED_FLASH = "The topic has been moved.";
	private static final String LOCKED_REPLY_ERROR = "This topic is not open for replying.";

	private static Boolean lockedOf(Long topicId) throws Throwable {
		return inTransaction(() -> {
			Topic topic = Topic.findById(topicId);
			return topic.locked;
		});
	}

	private static Boolean hiddenOf(Long topicId) throws Throwable {
		return inTransaction(() -> {
			Topic topic = Topic.findById(topicId);
			return topic.hidden;
		});
	}

	private static String forumOf(Long topicId) throws Throwable {
		return inTransaction(() -> {
			Topic topic = Topic.findById(topicId);
			return "forum=" + topic.forum.id;
		});
	}

	private static long postCount() throws Throwable {
		return inTransaction(() -> Post.count()).longValue();
	}

	// ~~~~~~~~~~~~ the flag reaches the row

	/** lock() has to write Topic.locked, otherwise the guards of reply() and createReply() never fire. */
	@Test
	public void lockStoresTheFlagOnTheRowInBothDirections() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		assertEquals(Boolean.FALSE, lockedOf(1L));

		Response locked = GET("/forums/1/topics/1/lock");
		assertStatus(302, locked);
		assertLocation(locked, "/forums/1");
		assertFlashValue(locked, "success", LOCKED_FLASH);
		assertEquals(Boolean.TRUE, lockedOf(1L));
		assertSuccessAlert(LOCKED_FLASH, markup(GET("/forums/1")));

		Response unlocked = GET("/forums/1/topics/1/lock");
		assertStatus(302, unlocked);
		assertLocation(unlocked, "/forums/1");
		assertFlashValue(unlocked, "success", UNLOCKED_FLASH);
		assertEquals(Boolean.FALSE, lockedOf(1L));
		assertSuccessAlert(UNLOCKED_FLASH, markup(GET("/forums/1")));
	}

	/** The reply link disappears from the topic page once the flag is stored, not only on a fixture row. */
	@Test
	public void aUiLockedTopicHasNoReplyLinkForAReplyer() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		assertFlashValue(GET("/forums/1/topics/1/lock"), "success", LOCKED_FLASH);

		clearCookies();
		login("bob@domain.com", "secret");
		String body = markup(GET("/forums/1/topics/1"));
		assertHeader(body, "Topic Normal", "2 posts by 2 users");
		assertEquals("[]", actionButtons(body).toString());

		// A moderator keeps both moderation buttons: (Un)lock is a GET link, (Un)hide a POST form.
		clearCookies();
		login("modo@domain.com", "secret");
		assertEquals("[(Un)lock -> /forums/1/topics/1/lock, (Un)hide -> POST /forums/1/topics/1/hide]",
				actionButtons(markup(GET("/forums/1/topics/1"))).toString());
	}

	/** hide() has to write Topic.hidden, or the topic stays visible to everybody below Profile.MODERATOR. */
	@Test
	public void hideStoresTheFlagOnTheRowInBothDirections() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");
		assertEquals(Boolean.FALSE, hiddenOf(1L));

		Response hidden = POST("/forums/1/topics/1/hide", form());
		assertStatus(302, hidden);
		assertLocation(hidden, "/forums/1");
		assertFlashValue(hidden, "success", HIDDEN_FLASH);
		assertEquals(Boolean.TRUE, hiddenOf(1L));

		// Topics.show() forbids it below Permission.moderate, and Forums.show() drops it from the
		// visible list of a plain user.
		clearCookies();
		login("bob@domain.com", "secret");
		assertStatus(403, GET("/forums/1/topics/1"));
		assertForbiddenBody(markup(GET("/forums/1/topics/1")));
		assertHeader(markup(GET("/forums/1")), "mainForum1", "1 post in 1 topic");

		clearCookies();
		login("modo@domain.com", "secret");
		Response restored = POST("/forums/1/topics/1/hide", form());
		assertStatus(302, restored);
		assertFlashValue(restored, "success", RESTORED_FLASH);
		assertEquals(Boolean.FALSE, hiddenOf(1L));

		clearCookies();
		login("bob@domain.com", "secret");
		assertIsOk(GET("/forums/1/topics/1"));
		assertHeader(markup(GET("/forums/1")), "mainForum1", "3 posts in 2 topics");
	}

	/** move() has to write Topic.forum; forumId is only the redirect target it names. */
	@Test
	public void moveStoresTheDestinationForumOnTheRow() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		assertEquals("forum=1", forumOf(1L));

		Response response = POST("/forums/1/topics/1/move/2", form());
		assertStatus(302, response);
		assertLocation(response, "/forums/1");
		assertFlashValue(response, "success", MOVED_FLASH);
		assertEquals("forum=2", forumOf(1L));

		// forum 2 had no topic, forum 1 keeps the locked and hidden ones
		assertHeader(markup(GET("/forums/2")), "mainForum2", "2 posts in 1 topic");
		assertContains("<a href=\"/forums/2/topics/1\">Topic Normal</a>", markup(GET("/forums/2")));
		assertHeader(markup(GET("/forums/1")), "mainForum1", "2 posts in 2 topics");
		assertNotContains("/forums/1/topics/1", markup(GET("/forums/1")));

		// The topic is shown under its new forum, breadcrumb included
		clearCookies();
		login("bob@domain.com", "secret");
		String body = markup(GET("/forums/2/topics/1"));
		assertContains("<li><a href=\"/forums/2\">mainForum2</a></li> <li class=\"active\">Topic Normal</li>", body);

		clearCookies();
		login("admin@domain.com", "secret");
		assertFlashValue(POST("/forums/2/topics/1/move/1", form()), "success", MOVED_FLASH);
		assertEquals("forum=1", forumOf(1L));
	}

	/** A hidden topic is locked without a word, like delete() spares nothing, and stays readable for the moderator. */
	@Test
	public void aHiddenTopicIsStillLocked() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");
		assertEquals(Boolean.FALSE, lockedOf(3L));

		assertFlashValue(GET("/forums/1/topics/3/lock"), "success", LOCKED_FLASH);
		assertEquals(Boolean.TRUE, lockedOf(3L));
		assertEquals(Boolean.TRUE, hiddenOf(3L));
		assertIsOk(GET("/forums/1/topics/3"));
		assertHeader(markup(GET("/forums/1/topics/3")), "Topic Hidden", "1 post by 1 user");
	}

	// ~~~~~~~~~~~~ a locked topic takes no reply

	/** The reply form is refused once the flag is stored, with the topic page as the redirect target. */
	@Test
	public void replyFormOnAUiLockedTopicIsRefused() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		assertFlashValue(GET("/forums/1/topics/1/lock"), "success", LOCKED_FLASH);

		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/forums/1/topics/1/reply");
		assertStatus(302, response);
		assertLocation(response, "/forums/1/topics/1");
		assertFlashValue(response, "error", LOCKED_REPLY_ERROR);

		assertErrorAlert(LOCKED_REPLY_ERROR, markup(GET("/forums/1/topics/1")));
		assertEquals(6L, postCount());
	}

	/** The POST is refused too: Topics.createReply() reads the same flag, and stores nothing. */
	@Test
	public void replyOnAUiLockedTopicIsRefusedAndStoresNothing() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		assertFlashValue(GET("/forums/1/topics/1/lock"), "success", LOCKED_FLASH);

		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/forums/1/topics/1", form("content", "Reply to a locked topic."));
		assertStatus(302, response);
		assertLocation(response, "/forums/1/topics/1");
		assertFlashValue(response, "error", LOCKED_REPLY_ERROR);
		assertEmptyMap(errorsOf(response));

		assertEquals(6L, postCount());
		assertNull(inTransaction(() -> Post.findById(7L)));
		assertEquals("posts=2", inTransaction(() -> {
			Topic topic = Topic.findById(1L);
			return "posts=" + topic.getPostsCount();
		}));

		String body = markup(GET("/forums/1/topics/1"));
		assertErrorAlert(LOCKED_REPLY_ERROR, body);
		assertNotContains("Reply to a locked topic.", body);
	}

	/** The administrator is not exempted either: the guard is topic.locked, not the profile. */
	@Test
	public void anAdministratorCannotReplyToAUiLockedTopic() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		assertFlashValue(GET("/forums/1/topics/1/lock"), "success", LOCKED_FLASH);

		Response response = POST("/forums/1/topics/1", form("content", "Admin reply."));
		assertStatus(302, response);
		assertFlashValue(response, "error", LOCKED_REPLY_ERROR);
		assertEquals(6L, postCount());

		// Unlocking makes the very same request pass again
		assertFlashValue(GET("/forums/1/topics/1/lock"), "success", UNLOCKED_FLASH);
		Response accepted = POST("/forums/1/topics/1", form("content", "Admin reply."));
		assertStatus(302, accepted);
		assertLocation(accepted, "/forums/1/topics/1");
		assertNull(flashOf(accepted).get("error"));

		assertEquals(7L, postCount());
		assertNotNull(inTransaction(() -> Post.findById(7L)));
		assertContains("Admin reply.", markup(GET("/forums/1/topics/1")));
	}

	/** Control case: an unlocked topic takes the reply, so the refusal above comes from the stored flag. */
	@Test
	public void replyOnAnUnlockedTopicIsStored() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/forums/1/topics/1", form("content", "Reply body."));
		assertStatus(302, response);
		assertLocation(response, "/forums/1/topics/1");
		assertNull(flashOf(response).get("error"));

		assertEquals(7L, postCount());
		String stored = inTransaction(() -> {
			Post post = Post.findById(7L);
			return "subject=" + post.subject + " content=" + post.content + " by=" + post.postedBy.name;
		});
		assertEquals("subject=null content=Reply body. by=Bob", stored);
		assertContains("Reply body.", markup(GET("/forums/1/topics/1")));
	}

	// ~~~~~~~~~~~~ guards and unknown ids

	@Test
	public void anonymousIsRedirectedToLoginWithTheLockUrlKept() throws Throwable {
		clearCookies();
		Response response = GET("/forums/1/topics/1/lock");
		assertStatus(302, response);
		assertLocation(response, "/login");
		assertFlashValue(response, "url", "/forums/1/topics/1/lock");
		assertNull(sessionUsername(response));

		assertEquals(Boolean.FALSE, lockedOf(1L));
		assertEquals(6L, postCount());
	}

	/** Profile.ADMINISTRATOR and Profile.MODERATOR are above Profile.REPLYER, so Bob's level is not enough. */
	@Test
	public void aReplyerIsForbiddenFromLocking() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/forums/1/topics/1/lock");
		assertStatus(403, response);
		assertForbiddenBody(markup(response));

		assertEquals(Boolean.FALSE, lockedOf(1L));
		assertEquals(6L, postCount());
	}

	/** Profile.MODERATOR passes through Permission.moderate of the topic's category (moderateMain = 4). */
	@Test
	public void aModeratorCanLockATopic() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");
		Response response = GET("/forums/1/topics/1/lock");
		assertStatus(302, response);
		assertLocation(response, "/forums/1");
		assertFlashValue(response, "success", LOCKED_FLASH);
		assertEquals(Boolean.TRUE, lockedOf(1L));
	}

	@Test
	public void lockOfAnUnknownTopicIsNotFoundAndChangesNothing() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		Response response = GET("/forums/1/topics/999/lock");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
		assertEmptyMap(flashOf(response));

		assertEquals(5L, inTransaction(() -> Topic.count()).longValue());
		assertEquals(6L, postCount());
	}

	@Test
	public void hideOfAnUnknownTopicIsNotFoundAndChangesNothing() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		Response response = POST("/forums/1/topics/999/hide", form());
		assertStatus(404, response);
		assertNotFoundBody(markup(response));

		assertEquals(5L, inTransaction(() -> Topic.count()).longValue());
	}

	/** The destination forum is loaded before the entity is touched, so a bad toForumId leaves topic 1 in forum 1. */
	@Test
	public void moveToAnUnknownForumIsNotFoundAndKeepsTheTopic() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		Response response = POST("/forums/1/topics/1/move/999", form());
		assertStatus(404, response);
		assertNotFoundBody(markup(response));

		assertEquals("forum=1", forumOf(1L));
		assertEquals(5L, inTransaction(() -> Forum.count()).longValue());
	}
}
