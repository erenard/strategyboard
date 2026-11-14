import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.*;

import play.mvc.Http.Response;

import models.forum.Forum;
import models.forum.Post;
import models.forum.Topic;
import models.forum.User;

/**
 * Coverage of app/views/forum/Topics/*.html: show.html, post.html and reply.html, in both directions
 * of every conditional, plus the controller branches that decide which of them renders.
 *
 * reply.html's #{ifError 'content'} true branch cannot be reached over HTTP: Topics.createReply()
 * declares no validation at all, so an empty reply is stored instead of rejected. Both sides of that
 * conditional are therefore produced by rendering the template directly.
 */
public class TopicsPageTest extends BaseForumViewTest {

	@Test
	public void topicPageListsPostsWithoutTopicLinksAndWithoutPagination() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/forums/1/topics/1");
		assertStatus(200, response);
		String body = markup(response);

		assertHeader(body, "Topic Normal", "2 posts by 2 users");
		assertContains("<ol class=\"breadcrumb\"> <li><a href=\"/forums\">Forums</a></li>"
				+ " <li><a href=\"/forums/1\">mainForum1</a></li> <li class=\"active\">Topic Normal</li> </ol>", body);

		// #{pagination}: topic.postsCount (2) fits in the configured pageSize, so the tag renders nothing
		Assert.assertNull(paginationBlock(body));

		// #{showPost post /} without showTopic -> the tag's #{if _showTopic} false branch
		assertContains("<div class=\"panel panel-default\"> <div class=\"panel-heading\">"
				+ " <span class=\"pull-right\">01/01, 2024 01:00AM</span> <a href=\"/users/3\">Bob</a> 1 post </div>"
				+ " <div class=\"panel-body\"> First post of the normal topic. </div> </div>", body);
		assertContains("<a href=\"/users/2\">Moderator</a> 3 posts </div>", body);
		assertNotContains("Posted in", body);
		Assert.assertEquals(2, count("panel panel-default", body));
	}

	@Test
	public void topicPageIncrementsViewsOnEveryVisit() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Assert.assertEquals("views=0", dbViews(1L));

		GET("/forums/1/topics/1");
		Assert.assertEquals("views=1", dbViews(1L));
		GET("/forums/1/topics/1");
		Assert.assertEquals("views=2", dbViews(1L));
	}

	@Test
	public void hiddenTopicDoesNotIncrementViewsBecauseShowForbidsBeforeTheIncrement() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");
		Response response = GET("/forums/1/topics/3");
		assertStatus(403, response);
		Assert.assertEquals("views=0", dbViews(3L));
	}

	@Test
	public void topicPagePaginatesWhenPostsExceedPageSize() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedExtraPosts(1L, 4);
			return null;
		});
		login("bob@domain.com", "secret");
		setPageSize(2);
		try {
			String firstPage = markup(GET("/forums/1/topics/1"));
			assertHeader(firstPage, "Topic Normal", "6 posts by 2 users");
			assertContains("<ul class=\"pagination\"> <li class=\"active\"><a href=\"#\">1</a></li>"
					+ " <li><a href=\"/forums/1/topics/1?page=2\">2</a></li>"
					+ " <li><a href=\"/forums/1/topics/1?page=3\">3</a></li> </ul>", paginationBlock(firstPage));
			Assert.assertEquals(2, count("panel panel-default", firstPage));

			String secondPage = markup(GET("/forums/1/topics/1?page=2"));
			assertContains("<ul class=\"pagination\"> <li><a href=\"/forums/1/topics/1?page=1\">1</a></li>"
					+ " <li class=\"active\"><a href=\"#\">2</a></li>"
					+ " <li><a href=\"/forums/1/topics/1?page=3\">3</a></li> </ul>", paginationBlock(secondPage));
			Assert.assertEquals(2, count("panel panel-default", secondPage));

			String thirdPage = markup(GET("/forums/1/topics/1?page=3"));
			assertContains("<ul class=\"pagination\"> <li><a href=\"/forums/1/topics/1?page=1\">1</a></li>"
					+ " <li><a href=\"/forums/1/topics/1?page=2\">2</a></li>"
					+ " <li class=\"active\"><a href=\"#\">3</a></li> </ul>", paginationBlock(thirdPage));
			Assert.assertEquals(2, count("panel panel-default", thirdPage));
		} finally {
			restorePageSize();
		}
	}

	@Test
	public void topicPageShowsNoButtonToAnAnonymousVisitor() throws Throwable {
		clearCookies();
		String body = markup(GET("/forums/1/topics/1"));

		assertHeader(body, "Topic Normal", "2 posts by 2 users");
		// #{secure.check 'replyer'} and #{secure.check 'moderator'} both fail
		Assert.assertEquals(0, actionButtons(body).size());
	}

	@Test
	public void topicPageShowsReplyAndModerationButtonsToAModerator() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");
		String body = markup(GET("/forums/1/topics/1"));

		List<String> buttons = actionButtons(body);
		Assert.assertEquals("[Reply -> /forums/1/topics/1/reply, (Un)lock -> /forums/1/topics/1/lock,"
				+ " (Un)hide -> POST /forums/1/topics/1/hide]", buttons.toString());
	}

	@Test
	public void lockedTopicHasNoReplyButtonForAPoster() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		String body = markup(GET("/forums/1/topics/2"));

		// #{pluralize} singular branches
		assertHeader(body, "Topic Locked", "1 post by 1 user");
		// #{secure.check 'replyer'} passes, #{ifnot topic.locked} fails
		Assert.assertEquals(0, actionButtons(body).size());
		assertContains("First post of the locked topic.", body);
	}

	@Test
	public void lockedTopicStillShowsModerationButtonsToAModerator() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");
		String body = markup(GET("/forums/1/topics/2"));

		assertHeader(body, "Topic Locked", "1 post by 1 user");
		Assert.assertEquals("[(Un)lock -> /forums/1/topics/2/lock, (Un)hide -> POST /forums/1/topics/2/hide]",
				actionButtons(body).toString());
	}

	@Test
	public void hiddenTopicIsForbiddenForEveryProfile() throws Throwable {
		clearCookies();
		assertForbiddenTopicAs("anonymous@domain.com");
		clearCookies();
		assertForbiddenTopicAs("bob@domain.com");
		clearCookies();
		assertForbiddenTopicAs("admin@domain.com");
	}

	private void assertForbiddenTopicAs(String email) throws Throwable {
		Response response = GET("/forums/1/topics/3");
		assertStatus(403, response);
		String body = markup(response);
		assertHeader(body, "Forbidden", "You don't have the permission to access this page.");
		// errors/403.html: #{else} branch, because Topic.show() forbids before rendering anything
		assertContains("<p> Please <a href=\"/login\">login</a>. </p>", body);
		assertNotContains("login</a> as administrator", body);
	}

	@Test
	public void unknownTopicIsNotFound() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		Response response = GET("/forums/1/topics/999");
		assertStatus(404, response);
		assertHeader(markup(response), "Forbidden", "The forum, topic, or post you search does not exist !");
	}

	// ~~~~~~~~~~~~ post.html (new topic form)

	@Test
	public void newTopicFormIsCleanAndCarriesThePosterName() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		String body = markup(GET("/forums/1/newTopic"));

		// post.html wraps its subtitle in #{set 'subtitle'}, whose newlines leave a space before </p>
		assertContains("<div class=\"page-header\"> <h1>New topic</h1> <p>by Bob </p> </div>", body);
		assertContains("<ol class=\"breadcrumb\"> <li><a href=\"/forums\">Forums</a></li>"
				+ " <li><a href=\"/forums/1\">mainForum1</a></li> <li class=\"active\">New topic</li> </ol>", body);
		assertField(body, "subject", "Subject ", null);
		assertField(body, "content", "Content ", null);
		assertContains("<textarea name=\"content\" rows=\"8\" class=\"form-control\" id=\"contentText\"></textarea>",
				body);
		assertContains("<form id=\"post\" action=\"/forums/1/topics\" method=\"POST\"", body);
		assertContains("<a class=\"btn btn-primary\" href=\"/forums/1\">Cancel</a>", body);
	}

	@Test
	public void newTopicFormIsNotReachableWithoutLogin() throws Throwable {
		clearCookies();
		Response response = GET("/forums/1/newTopic");
		assertStatus(302, response);
		assertLocation(response, "/login");
		Assert.assertEquals("/forums/1/newTopic", flashOf(response).get("url"));
	}

	@Test
	public void newTopicKeepsTheSubjectRequiredErrorAndTheContentFlash() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/forums/1/topics", form("subject", "", "content", "Some body."));
		assertStatus(302, response);
		assertLocation(response, "/forums/1/newTopic");
		Assert.assertEquals("validation.required", errorsOf(response).get("subject"));
		Assert.assertEquals("Some body.", flashOf(response).get("content"));

		String body = markup(GET("/forums/1/newTopic"));
		assertErrorAlert("Please correct these errors !", body);
		assertField(body, "subject", "Subject ", "Required");
		assertField(body, "content", "Content ", null);
		// params.flash() -> ${flash.content} inside the textarea
		assertContains("<textarea name=\"content\" rows=\"8\" class=\"form-control\" id=\"contentText\">Some body.</textarea>",
				body);
	}

	@Test
	public void newTopicWithoutContentIsCreatedBecauseCreateValidatesNothing() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/forums/1/topics", form("subject", "Topic without content"));
		assertStatus(302, response);
		assertMatches("/forums/1/topics/[0-9]+", location(response));

		String body = markup(GET(location(response)));
		assertHeader(body, "Topic without content", "1 post by 1 user");
		Assert.assertEquals(1, count("panel panel-default", body));
	}

	@Test
	public void newTopicIsCreatedInTheForumWithThePosterAsItsAuthor() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/forums/1/topics", form("subject", "Brand new topic", "content",
				"Body of the new topic."));
		assertStatus(302, response);
		assertMatches("/forums/1/topics/[0-9]+", location(response));

		String state = inTransaction(() -> {
			Post first = Post.find("subject = ?1", "Brand new topic").first();
			if (first == null) {
				return "not created";
			}
			Topic topic = first.topic;
			return "forum=" + topic.forum.id + " posts=" + topic.getPostsCount() + " views=" + topic.views
					+ " author=" + first.postedBy.name;
		});
		Assert.assertEquals("forum=1 posts=1 views=0 author=Bob", state);

		String body = markup(GET(location(response)));
		assertHeader(body, "Brand new topic", "1 post by 1 user");
		assertContains("Body of the new topic.", body);
	}

	@Test
	public void newTopicOnALockedForumRedirectsToTheForumWithAnError() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			setForumLocked(2L, true);
			return null;
		});
		login("bob@domain.com", "secret");
		Response response = GET("/forums/2/newTopic");
		assertStatus(302, response);
		Assert.assertEquals("This forum is not open for posting.", flashOf(response).get("error"));

		String body = markup(GET("/forums/2"));
		assertErrorAlert("This forum is not open for posting.", body);
		Assert.assertEquals(0, actionButtons(body).size());
	}

	@Test
	public void createOnALockedForumRedirectsWithTheSameError() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			setForumLocked(2L, true);
			return null;
		});
		login("bob@domain.com", "secret");
		Response response = POST("/forums/2/topics", form("subject", "S", "content", "C"));
		assertStatus(302, response);
		Assert.assertEquals("This forum is not open for posting.", flashOf(response).get("error"));
		Assert.assertEquals("0", inTransaction(() -> {
			Forum forum = Forum.findById(2L);
			return String.valueOf(forum.getTopicsCount());
		}));
	}

	// ~~~~~~~~~~~~ reply.html

	@Test
	public void replyFormIsCleanAndPostsToTheTopicItself() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		String body = markup(GET("/forums/1/topics/1/reply"));

		assertHeader(body, "Reply to &laquo;Topic Normal&raquo;", "by Bob");
		assertContains("<ol class=\"breadcrumb\"> <li><a href=\"/forums\">Forums</a></li>"
				+ " <li><a href=\"/forums/1\">mainForum1</a></li>"
				+ " <li><a href=\"/forums/1/topics/1\">Topic Normal</a></li>"
				+ " <li class=\"active\">Reply to &laquo;Topic Normal&raquo;</li> </ol>", body);
		assertField(body, "content", "Content ", null);
		assertContains("<form role=\"form\" action=\"/forums/1/topics/1\" method=\"POST\" accept-charset=\"utf-8\">",
				body);
		assertContains("<a class=\"btn btn-primary\" href=\"/forums/1/topics/1\">Cancel</a>", body);
	}

	@Test
	public void replyFormSubtitleNamesTheConnectedUser() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");
		assertHeader(markup(GET("/forums/1/topics/1/reply")), "Reply to &laquo;Topic Normal&raquo;", "by Moderator");
	}

	@Test
	public void replyFormIsNotReachableWithoutLogin() throws Throwable {
		clearCookies();
		Response response = GET("/forums/1/topics/1/reply");
		assertStatus(302, response);
		assertLocation(response, "/login");
		Assert.assertEquals("/forums/1/topics/1/reply", flashOf(response).get("url"));
	}

	@Test
	public void replyFormOnALockedTopicRedirectsToTheTopicWithAnError() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/forums/1/topics/2/reply");
		assertStatus(302, response);
		Assert.assertEquals("This topic is not open for replying.", flashOf(response).get("error"));

		String body = markup(GET("/forums/1/topics/2"));
		assertErrorAlert("This topic is not open for replying.", body);
		assertHeader(body, "Topic Locked", "1 post by 1 user");
	}

	@Test
	public void replyFormOnAHiddenTopicIsRenderedBecauseReplyIgnoresHidden() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		String body = markup(GET("/forums/1/topics/3/reply"));
		assertHeader(body, "Reply to &laquo;Topic Hidden&raquo;", "by Bob");
		assertContains("<li><a href=\"/forums/1/topics/3\">Topic Hidden</a></li>", body);
	}

	@Test
	public void replyIsStoredEvenWithAnEmptyContent() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/forums/1/topics/1", form("content", ""));
		assertStatus(302, response);
		assertLocation(response, "/forums/1/topics/1");
		Assert.assertEquals("posts=3", inTransaction(() -> {
			Topic topic = Topic.findById(1L);
			return "posts=" + topic.getPostsCount();
		}));

		String body = markup(GET("/forums/1/topics/1"));
		assertHeader(body, "Topic Normal", "3 posts by 2 users");
		Assert.assertEquals(3, count("panel panel-default", body));
	}

	@Test
	public void replyWithContentIsStoredAndShownOnTheTopic() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = POST("/forums/1/topics/1", form("content", "Reply body."));
		assertStatus(302, response);
		assertLocation(response, "/forums/1/topics/1");

		String body = markup(GET("/forums/1/topics/1"));
		assertHeader(body, "Topic Normal", "3 posts by 2 users");
		assertContains("Reply body.", body);
	}

	@Test
	public void replyFormShowsBothDirectionsOfTheContentErrorBranchOnDirectRendering() throws Throwable {
		clearCookies();
		// No controller reaches the true branch: Topics.createReply() validates nothing, so the error
		// is installed by hand to cover #{ifError 'content'} and #{error 'content'}.
		Map<String, Object> errorArgs = viewArgs("bob@domain.com", "/forums/1/topics/1/reply");
		errorArgs.put("topic", inTransaction(() -> Topic.findById(1L)));
		String withError = renderViewWithError("forum/Topics/reply.html", errorArgs, "content", "Content is required");
		assertField(withError, "content", "Content ", "Content is required");

		Map<String, Object> cleanArgs = viewArgs("bob@domain.com", "/forums/1/topics/1/reply");
		cleanArgs.put("topic", inTransaction(() -> Topic.findById(1L)));
		assertField(renderView("forum/Topics/reply.html", cleanArgs), "content", "Content ", null);
	}

	// ~~~~~~~~~~~~ helpers

	private static String dbViews(Long topicId) throws Throwable {
		return inTransaction(() -> {
			Topic topic = Topic.findById(topicId);
			return "views=" + topic.views;
		});
	}

	/** Four posts by Moderator on the given topic, newest last. */
	private static void seedExtraPosts(Long topicId, int count) {
		Topic topic = Topic.findById(topicId);
		User modo = User.findByEmail("modo@domain.com");
		for (int i = 0; i < count; i++) {
			Post post = new Post();
			post.topic = topic;
			post.postedBy = modo;
			post.subject = "Extra post " + i;
			post.content = "Extra content " + i;
			post.postedAt = new Date(System.currentTimeMillis() + i * 1000L);
			post.create();
		}
	}

	private static void setForumLocked(Long forumId, boolean locked) {
		Forum forum = Forum.findById(forumId);
		forum.locked = Boolean.valueOf(locked);
		forum.save();
	}
}
