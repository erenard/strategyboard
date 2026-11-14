import org.junit.Test;

import play.mvc.Http.Response;

import models.forum.Forum;
import models.forum.Post;
import models.forum.Topic;

/**
 * Coverage of controllers.forum.Topics.delete(), reached as POST /forums/{forumId}/topics/{topicId}/delete.
 *
 * The route declares POST only, so this action is never reachable through a GET.
 *
 * Branches and behaviors pinned below:
 *   - Topic.findById(topicId) then notFoundIfNull(): an unknown id answers 404 with app/views/errors/404.html.
 *   - topic.delete() cascades: Topic.posts is @OneToMany(cascade = CascadeType.ALL) (app/models/forum/Topic.java),
 *     so removing a topic removes its posts too, and the counters move by more than one row.
 *   - flash.success("success.topic.deleted") is resolved through conf/messages into
 *     "The topic has been deleted.", and the alert appears on the forum page the redirect target renders.
 *   - Forums.show(forumId, null) is a controller method called from another controller method, so
 *     ControllersEnhancer turns it into a redirect: the answer is 302 + Location /forums/{forumId}.
 *   - forumId is never compared with topic.forum: the topic is loaded by topicId alone, so a URL naming a
 *     forum the topic does not belong to still deletes it and redirects to that forum's page.
 *   - Neither locked nor hidden topics are spared: the only guard is @Check(Profile.ADMINISTRATOR).
 *   - Secure.checkAccess() puts flash("url") as `GET ? request.url : Play.ctxPath + "/"`, so an anonymous
 *     POST is redirected to /login with url = / rather than the delete path.
 *   - Profile.Security.check() fails for a moderator and for a replyer, and Profile.Security.onCheckFailed()
 *     is the module default, which calls forbidden() and renders app/views/errors/403.html.
 *
 * The suites each start from test/data.yml: forums 1..5, topics 1..5 (t1, t2 locked, t3 hidden, news1 and
 * news2 in forum 5), posts 1..6 (p1 and p2 on t1, p3 on t2, p4 on t3, p5 on news1, p6 on news2).
 */
public class TopicsDeleteTest extends BaseForumViewTest {

	private static final String DELETED_FLASH = "The topic has been deleted.";

	private static void assertTopicCount(long expected) throws Throwable {
		assertEquals(Long.valueOf(expected), inTransaction(() -> Topic.count()));
	}

	private static void assertPostCount(long expected) throws Throwable {
		assertEquals(Long.valueOf(expected), inTransaction(() -> Post.count()));
	}

	private static void assertForumCount(long expected) throws Throwable {
		assertEquals(Long.valueOf(expected), inTransaction(() -> Forum.count()));
	}

	// ~~~~~~~~~~~~ the rows the delete removes

	/** t1 carries p1 and p2, so the cascade takes two posts with the topic. */
	@Test
	public void deleteRemovesTheTopicAndCascadesToItsPosts() throws Throwable {
		assertTopicCount(5);
		assertPostCount(6);

		login("admin@domain.com", "secret");
		Response response = POST("/forums/1/topics/1/delete", form());
		assertStatus(302, response);
		assertLocation(response, "/forums/1");
		assertFlashValue(response, "success", DELETED_FLASH);
		assertEmptyMap(errorsOf(response));

		assertTopicCount(4);
		assertPostCount(4);
		assertNull(inTransaction(() -> Topic.findById(1L)));
		assertNull(inTransaction(() -> Post.findById(1L)));
		assertNull(inTransaction(() -> Post.findById(2L)));
		assertNotNull(inTransaction(() -> Topic.findById(2L)));
		assertNotNull(inTransaction(() -> Post.findById(3L)));

		// app/views/forum/Forums/show.html: forum 1 keeps t2 and t3, one post each
		String body = markup(GET("/forums/1"));
		assertSuccessAlert(DELETED_FLASH, body);
		assertHeader(body, "mainForum1", "2 posts in 2 topics");
		assertContains("<a href=\"/forums/1/topics/2\">Topic Locked</a>", body);
		assertContains("<a href=\"/forums/1/topics/3\">Topic Hidden</a>", body);
		assertNotContains("/forums/1/topics/1", body);
	}

	/** A locked topic is deleted without a word: the guard is the profile, not the topic flags. */
	@Test
	public void deleteOfALockedTopicIsAcceptedByTheAdministrator() throws Throwable {
		login("admin@domain.com", "secret");
		Response response = POST("/forums/1/topics/2/delete", form());
		assertStatus(302, response);
		assertLocation(response, "/forums/1");
		assertFlashValue(response, "success", DELETED_FLASH);

		assertTopicCount(4);
		assertPostCount(5);
		assertNull(inTransaction(() -> Topic.findById(2L)));
		assertNull(inTransaction(() -> Post.findById(3L)));

		assertSuccessAlert(DELETED_FLASH, markup(GET("/forums/1")));
	}

	/** A hidden topic is deleted as well, although Topics.show() forbids it for hidden topics. */
	@Test
	public void deleteOfAHiddenTopicIsAcceptedByTheAdministrator() throws Throwable {
		login("admin@domain.com", "secret");
		Response response = POST("/forums/1/topics/3/delete", form());
		assertStatus(302, response);
		assertLocation(response, "/forums/1");
		assertFlashValue(response, "success", DELETED_FLASH);

		assertTopicCount(4);
		assertPostCount(5);
		assertNull(inTransaction(() -> Topic.findById(3L)));
		assertNull(inTransaction(() -> Post.findById(4L)));

		// forum 1 keeps t1 with its two posts, and t2
		String body = markup(GET("/forums/1"));
		assertHeader(body, "mainForum1", "3 posts in 2 topics");
		assertContains("<a href=\"/forums/1/topics/1\">Topic Normal</a>", body);
	}

	// ~~~~~~~~~~~~ forumId is only the redirect target

	/** Topic 4 belongs to forum 5 (Home Page); the URL names forum 1, and topic 4 still disappears. */
	@Test
	public void deleteDoesNotCompareTheTopicWithTheForumNamedInTheUrl() throws Throwable {
		login("admin@domain.com", "secret");
		Response response = POST("/forums/1/topics/4/delete", form());
		assertStatus(302, response);
		assertLocation(response, "/forums/1");
		assertFlashValue(response, "success", DELETED_FLASH);

		assertTopicCount(4);
		assertPostCount(5);
		assertNull(inTransaction(() -> Topic.findById(4L)));
		assertNull(inTransaction(() -> Post.findById(5L)));
		// news2 is the topic of forum 5 that survives
		assertNotNull(inTransaction(() -> Topic.findById(5L)));
		assertNotNull(inTransaction(() -> Post.findById(6L)));

		String body = markup(GET("/forums/1"));
		assertSuccessAlert(DELETED_FLASH, body);
		assertHeader(body, "mainForum1", "4 posts in 3 topics");
		assertNotContains("/forums/5/topics/4", body);
	}

	@Test
	public void deleteOfAnUnknownTopicIsNotFoundAndRemovesNothing() throws Throwable {
		login("admin@domain.com", "secret");
		Response response = POST("/forums/1/topics/999/delete", form());
		assertStatus(404, response);
		assertEquals("text/html; charset=utf-8", response.contentType);
		assertNotFoundBody(markup(response));
		assertEmptyMap(flashOf(response));

		assertTopicCount(5);
		assertPostCount(6);
		assertForumCount(5);
	}

	// ~~~~~~~~~~~~ guards

	@Test
	public void anonymousIsRedirectedToLoginAndTheUrlOfTheRootIsKept() throws Throwable {
		clearCookies();
		Response response = POST("/forums/1/topics/1/delete", form());
		assertStatus(302, response);
		assertLocation(response, "/login");
		// Secure.checkAccess() keeps Play.ctxPath + "/" for a POST, so the delete path is not remembered
		assertFlashValue(response, "url", "/");
		assertNull(sessionUsername(response));

		assertTopicCount(5);
		assertPostCount(6);
		assertNotNull(inTransaction(() -> Topic.findById(1L)));
	}

	@Test
	public void aModeratorIsForbiddenFromDeletingATopic() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");
		Response response = POST("/forums/1/topics/1/delete", form());
		assertStatus(403, response);
		assertEmptyMap(flashOf(response));

		String body = markup(response);
		assertForbiddenBody(body);
		// errors/403.html is the app template, so the flash of login() is an alert rather than a flash box
		assertSuccessAlert("Welcome back Moderator !", body);

		assertTopicCount(5);
		assertPostCount(6);
		assertNotNull(inTransaction(() -> Topic.findById(1L)));
	}

	/** Profile.ADMINISTRATOR is above Profile.REPLYER, so Bob's permission level of the category is not enough. */
	@Test
	public void aReplyerIsForbiddenFromDeletingATopic() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/forums/1/topics/1/delete", form());
		assertStatus(403, response);

		String body = markup(response);
		assertForbiddenBody(body);
		assertSuccessAlert("Welcome back Bob !", body);
		assertNotContains("/forums/1/topics/1/delete", body);

		assertTopicCount(5);
		assertPostCount(6);
		assertNotNull(inTransaction(() -> Topic.findById(1L)));
	}

	// ~~~~~~~~~~~~ the forum page the redirect points at

	/** Two deletes in a row: the flash of the second one replaces the first, and both rows are gone. */
	@Test
	public void consecutiveDeletesLeaveOnlyTheFlashOfTheLastOne() throws Throwable {
		login("admin@domain.com", "secret");
		assertFlashValue(POST("/forums/1/topics/1/delete", form()), "success", DELETED_FLASH);
		assertFlashValue(POST("/forums/1/topics/2/delete", form()), "success", DELETED_FLASH);

		assertTopicCount(3);
		assertPostCount(3);
		assertForumCount(5);

		String body = markup(GET("/forums/1"));
		assertSuccessAlert(DELETED_FLASH, body);
		assertEquals(1, count(DELETED_FLASH, body));
		// #{pluralize} turns the singular counts into "1 post in 1 topic"
		assertHeader(body, "mainForum1", "1 post in 1 topic");
		assertContains("<a href=\"/forums/1/topics/3\">Topic Hidden</a>", body);
	}
}
