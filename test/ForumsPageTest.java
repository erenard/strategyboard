import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.*;

import play.mvc.Http.Response;

import models.forum.Category;
import models.forum.Forum;
import models.forum.Permission;
import models.forum.Post;
import models.forum.Topic;
import models.forum.User;
import models.forum.UserGroup;

/**
 * Coverage of app/views/forum/Forums/*.html: index.html and show.html, in both directions of every
 * conditional.
 *
 * The counts of index.html are decided per category, because Permission is one row per
 * (group, category): forum.Forums.index() takes getVisibleTopicsCount()/getVisiblePostsCount() for the
 * categories whose Permission row is below Permission.moderate, so a hidden topic is not advertised
 * there, and forum.Forums.show() drops it from its own list the same way.
 *
 * show.html's #{if topic.lastPost} false branch is unreachable through the database: Topic.getFirstPost()
 * and Topic.getLastPost() read the same rows, and the same row dereferences ${topic.firstPost.subject}.
 * It is exercised by rendering the template with a topic stub whose getLastPost() returns null.
 */
public class ForumsPageTest extends BaseForumViewTest {

	// ~~~~~~~~~~~~ index.html

	@Test
	public void forumsIndexForAnonymousShowsOnlyTheMainCategory() throws Throwable {
		clearCookies();
		String body = markup(GET("/forums"));

		// Below Permission.moderate the counts come from Category.getVisibleTopicsCount() and
		// getVisiblePostsCount(), so the hidden topic t3 and its post 4 are not advertised
		assertHeader(body, "Forums", "5 posts in 4 topics");
		Assert.assertEquals("[Main Category]", panelTitles(body).toString());
		List<String> rows = tableRows(body);
		Assert.assertEquals(3, rows.size());
		assertContains("<a href=\"/forums/1\">mainForum1</a> <p class=\"light\"> 3 posts in 2 topics </p>"
				+ " Main forum 1 description", rows.get(0));
		// #{if forum.lastPost} true branch, with the view link pointing at the last post's page
		assertContains("by <strong>Moderator</strong> (<a href=\"/forums/1/topics/2?page=1\">view</a>)", rows.get(0));
		// #{else} branch
		assertContains("<a href=\"/forums/2\">mainForum2</a> <p class=\"light\"> 0 posts in 0 topics </p>", rows.get(1));
		assertContains("No post", rows.get(1));
		assertContains("by <strong>Admin</strong> (<a href=\"/forums/5/topics/5?page=1\">view</a>)", rows.get(2));

		// Category.forUser() excludes the moderators' category for the anonymous user
		assertNotContains("modoForum1", body);
	}

	@Test
	public void forumsIndexForModeratorAddsTheModeratorsCategory() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");
		String body = markup(GET("/forums"));

		// Permission.moderateMain gives Main Category level 4 -> the full counts; Permission.post
		// gives Moderators Category level 3 -> the visible ones, which are 0/0 anyway
		assertHeader(body, "Forums", "6 posts in 5 topics");
		assertSuccessAlert("Welcome back Moderator !", body);
		Assert.assertEquals("[Moderators Category, Main Category]", panelTitles(body).toString());
		List<String> rows = tableRows(body);
		Assert.assertEquals(5, rows.size());
		assertContains("<a href=\"/forums/3\">modoForum1</a> <p class=\"light\"> 0 posts in 0 topics </p>", rows.get(0));
		assertContains("No post", rows.get(0));
		assertContains("<a href=\"/forums/4\">modoForum2</a>", rows.get(1));
		assertContains("No post", rows.get(1));
		Assert.assertEquals(3, count("No post", body));
	}

	@Test
	public void forumsIndexForAdministratorCountsHiddenTopicsInTheForumTotals() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		String body = markup(GET("/forums"));

		// Forum.getPostsCount()/getTopicsCount() are not filtered by hidden, so mainForum1 counts topic 3
		assertHeader(body, "Forums", "6 posts in 5 topics");
		Assert.assertEquals("[Moderators Category, Main Category]", panelTitles(body).toString());
		assertContains("4 posts in 3 topics", body);
	}

	@Test
	public void forumsIndexPrintsSingularCountsAndAnEmptyCategoryPanel() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedExtras();
			return null;
		});
		login("admin@domain.com", "secret");
		String body = markup(GET("/forums"));

		assertHeader(body, "Forums", "11 posts in 7 topics");
		Assert.assertEquals("[Moderators Category, Main Category, Empty Category]",
				panelTitles(body).toString());

		// #{pluralize} singular branches for post and topic
		assertContains("<a href=\"/forums/6\">Solo Forum</a> <p class=\"light\"> 1 post in 1 topic </p>"
				+ " Solo forum description", body);
		assertContains("by <strong>Bob</strong> (<a href=\"/forums/6/topics/6?page=1\">view</a>)", body);

		// A category without forums still renders its panel, with an empty tbody
		assertContains("<h3 class=\"panel-title\">Empty Category</h3>", body);
		assertContains("<tbody> </tbody>", body);
	}

	// ~~~~~~~~~~~~ show.html

	@Test
	public void forumShowForAnonymousCountsOnlyTheVisibleTopics() throws Throwable {
		clearCookies();
		String body = markup(GET("/forums/1"));

		assertHeader(body, "mainForum1", "3 posts in 2 topics");
		assertContains("<ol class=\"breadcrumb\"> <li><a href=\"/forums\">Forums</a></li>"
				+ " <li class=\"active\">mainForum1</li> </ol>", body);
		List<String> rows = tableRows(body);
		Assert.assertEquals(2, rows.size());
		assertContains("<a href=\"/forums/1/topics/2\">Topic Locked</a> </td> <td class=\"infos count\"> 1 </td>"
				+ " <td class=\"infos count\"> 0 </td>", rows.get(0));
		assertNotContains("Topic Hidden", body);

		// #{secure.check 'poster'} and #{secure.check 'moderator'} both fail for an anonymous visitor
		Assert.assertEquals(0, actionButtons(body).size());
		// #{pagination}: topicsCount (2) fits in the configured pageSize, so the tag renders nothing
		Assert.assertNull(paginationBlock(body));
	}

	@Test
	public void forumShowForModeratorCountsAndListsHiddenTopics() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");
		String body = markup(GET("/forums/1"));

		// accessLevel >= moderate -> Forum.getTopicsCount()/getTopics(), which are not hidden-filtered
		assertHeader(body, "mainForum1", "4 posts in 3 topics");
		List<String> rows = tableRows(body);
		Assert.assertEquals(3, rows.size());
		assertContains("<a href=\"/forums/1/topics/3\">Topic Hidden</a>", rows.get(0));
		Assert.assertEquals("[Post a new topic -> /forums/1/newTopic, (Un)lock -> /forums/1/lock]",
				actionButtons(body).toString());
	}

	@Test
	public void forumShowHidesTheNewTopicButtonOfALockedForum() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");

		inTransactionVoid(() -> {
			setForumLocked(1L, true);
			return null;
		});
		// #{ifnot forum.locked} false branch, and #{secure.check 'moderator'} fails for Bob
		Assert.assertEquals("[]", actionButtons(markup(GET("/forums/1"))).toString());

		inTransactionVoid(() -> {
			setForumLocked(1L, false);
			return null;
		});
		// #{ifnot forum.locked} true branch
		Assert.assertEquals("[Post a new topic -> /forums/1/newTopic]",
				actionButtons(markup(GET("/forums/1"))).toString());
	}

	@Test
	public void forumShowOfAPosterInAModeratorsForumHasNoButtons() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		String body = markup(GET("/forums/3"));

		// Permission(postModerators) gives the Moderators group accessLevel 3, Permission(viewModerators)
		// gives General 1, so the page renders but neither button appears.
		assertHeader(body, "modoForum1", "0 posts in 0 topics");
		Assert.assertEquals(0, actionButtons(body).size());
		// #{list items:topics} empty branch
		Assert.assertEquals(0, tableRows(body).size());
	}

	@Test
	public void forumShowOfAnEmptyForumHasNoRows() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		String body = markup(GET("/forums/2"));

		assertHeader(body, "mainForum2", "0 posts in 0 topics");
		Assert.assertEquals(0, tableRows(body).size());
		Assert.assertNull(paginationBlock(body));
		Assert.assertEquals("[Post a new topic -> /forums/2/newTopic]", actionButtons(body).toString());
	}

	@Test
	public void forumShowOfAForumWithASingleTopicAndPost() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedExtras();
			return null;
		});
		login("bob@domain.com", "secret");
		String body = markup(GET("/forums/6"));

		// #{pluralize} singular branches for post and topic
		assertHeader(body, "Solo Forum", "1 post in 1 topic");
		List<String> rows = tableRows(body);
		Assert.assertEquals(1, rows.size());
		assertContains("<a href=\"/forums/6/topics/6\">Solo topic</a> </td> <td class=\"infos count\"> 1 </td>"
				+ " <td class=\"infos count\"> 0 </td>", rows.get(0));
		// #{if topic.lastPost} true branch
		assertContains("by <strong>Bob</strong> (<a href=\"/forums/6/topics/6?page=1\">view</a>)", rows.get(0));
	}

	@Test
	public void forumShowPaginatesTopicsAndLinksToTheLastPostPage() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedExtras();
			return null;
		});
		login("bob@domain.com", "secret");
		setPageSize(2);
		try {
			String firstPage = markup(GET("/forums/1"));
			assertHeader(firstPage, "mainForum1", "7 posts in 3 topics");
			assertContains("<ul class=\"pagination\"> <li class=\"active\"><a href=\"#\">1</a></li>"
					+ " <li><a href=\"/forums/1?page=2\">2</a></li> </ul>", paginationBlock(firstPage));
			List<String> rows = tableRows(firstPage);
			Assert.assertEquals(2, rows.size());

			// #{if topic.postsCount > pageSize}: only the topic with 6 posts gets a last page link
			assertContains("&nbsp;<a class=\"last\" href=\"/forums/1/topics/1?page=3\">last page</a>", rows.get(1));
			Assert.assertEquals(1, count("last page", firstPage));

			String secondPage = markup(GET("/forums/1?page=2"));
			assertContains("<ul class=\"pagination\"> <li><a href=\"/forums/1?page=1\">1</a></li>"
					+ " <li class=\"active\"><a href=\"#\">2</a></li> </ul>", paginationBlock(secondPage));
			// Forum.getTopics() joins on Post, so the postless topic is counted in topicsCount but never listed
			Assert.assertEquals(0, tableRows(secondPage).size());
		} finally {
			restorePageSize();
		}
	}

	@Test
	public void forumShowForModeratorPaginatesTheHiddenTopicsToo() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedExtras();
			return null;
		});
		login("admin@domain.com", "secret");
		setPageSize(2);
		try {
			String body = markup(GET("/forums/1"));
			assertHeader(body, "mainForum1", "8 posts in 4 topics");
			assertContains("<ul class=\"pagination\"> <li class=\"active\"><a href=\"#\">1</a></li>"
					+ " <li><a href=\"/forums/1?page=2\">2</a></li> </ul>", paginationBlock(body));
			Assert.assertEquals(2, tableRows(body).size());
		} finally {
			restorePageSize();
		}
	}

	@Test
	public void forumLockAndUnlockToggleTheRowAndFlashOnTheForumsIndex() throws Throwable {
		clearCookies();
		login("modo@domain.com", "secret");

		Response locked = GET("/forums/1/lock");
		assertStatus(302, locked);
		assertLocation(locked, "/forums");
		Assert.assertEquals("The forum has been locked.", flashOf(locked).get("success"));
		Assert.assertEquals("locked=true", dbLocked(1L));
		assertSuccessAlert("The forum has been locked.", markup(GET("/forums")));

		// forum/Forums/show.html takes the false branch of #{ifnot forum.locked}: only the moderation
		// button is left
		Assert.assertEquals("[(Un)lock -> /forums/1/lock]",
				actionButtons(markup(GET("/forums/1"))).toString());

		Response unlocked = GET("/forums/1/lock");
		assertStatus(302, unlocked);
		assertLocation(unlocked, "/forums");
		Assert.assertEquals("The forum has been unlocked.", flashOf(unlocked).get("success"));
		Assert.assertEquals("locked=false", dbLocked(1L));
		assertSuccessAlert("The forum has been unlocked.", markup(GET("/forums")));
		Assert.assertEquals("[Post a new topic -> /forums/1/newTopic, (Un)lock -> /forums/1/lock]",
				actionButtons(markup(GET("/forums/1"))).toString());
	}

	@Test
	public void unknownForumIsNotFound() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/forums/999");
		assertStatus(404, response);
		assertHeader(markup(response), "Forbidden", "The forum, topic, or post you search does not exist !");
	}

	// ~~~~~~~~~~~~ dead branch of show.html

	@Test
	public void forumShowOmitsTheLastPostCellForATopicWithoutALastPost() throws Throwable {
		clearCookies();
		inTransactionVoid(() -> {
			seedExtras();
			return null;
		});

		Forum forum1 = inTransaction(() -> Forum.findById(1L));
		Post firstPost = inTransaction(() -> Post.find("topic.id = ?1 order by postedAt asc", 1L).first());

		// Topic.getFirstPost() and Topic.getLastPost() read the same rows, so only a stub can make
		// #{if topic.lastPost} falsy while ${topic.firstPost.subject} still resolves.
		TopicWithoutLastPost stub = new TopicWithoutLastPost(firstPost);
		stub.id = 1L;
		stub.forum = forum1;

		Map<String, Object> args = viewArgs("bob@domain.com", "/forums/1");
		args.put("forum", forum1);
		args.put("topicsCount", Long.valueOf(1));
		args.put("postsCount", Long.valueOf(1));
		List<Topic> topics = new ArrayList<Topic>();
		topics.add(stub);
		args.put("topics", topics);
		args.put("page", Integer.valueOf(1));

		String body = renderView("forum/Forums/show.html", args);
		assertHeader(body, "mainForum1", "1 post in 1 topic");
		List<String> rows = tableRows(body);
		Assert.assertEquals(1, rows.size());
		assertContains("<a href=\"/forums/1/topics/1\">Topic Normal</a> </td> <td class=\"infos count\"> 1 </td>"
				+ " <td class=\"infos count\"> 0 </td> <td class=\"infos\"> </td>", rows.get(0));
		// the #{if topic.lastPost} false branch writes nothing at all
		assertNotContains("by <strong>", rows.get(0));
		assertNotContains("view</a>", rows.get(0));
	}

	/** A topic with a first post but no last post; see forumShowOmitsTheLastPostCellForATopicWithoutALastPost. */
	private static class TopicWithoutLastPost extends Topic {
		final Post first;

		TopicWithoutLastPost(Post first) {
			this.first = first;
		}

		@Override
		public Post getFirstPost() {
			return first;
		}

		@Override
		public Post getLastPost() {
			return null;
		}

		@Override
		public Long getPostsCount() {
			return Long.valueOf(1);
		}
	}

	// ~~~~~~~~~~~~ helpers

	private static String dbLocked(Long forumId) throws Throwable {
		return inTransaction(() -> {
			Forum forum = Forum.findById(forumId);
			return "locked=" + forum.locked;
		});
	}

	private static String dbViews(Long topicId) throws Throwable {
		return inTransaction(() -> {
			Topic topic = Topic.findById(topicId);
			return "views=" + topic.views;
		});
	}

	/**
	 * A forum with one topic and one post (singular pluralize), a topic without any post (the topicsCount
	 * of a page that lists nothing), an empty category (the empty tbody of index.html) and four extra posts
	 * on topic 1 (pagination plus the last page link).
	 */
	private static void seedExtras() {
		Category main = Category.findById(2L);
		Forum solo = new Forum(main, "Solo Forum", "Solo forum description", 6);
		new Topic(solo, User.findByEmail("bob@domain.com"), "Solo topic", "Solo post content.");

		Topic postless = new Topic();
		postless.forum = Forum.findById(1L);
		postless.views = 0;
		postless.locked = Boolean.FALSE;
		postless.hidden = Boolean.FALSE;
		postless.create();

		Category empty = new Category("Empty Category", 7);
		new Permission(empty, UserGroup.findByName("General"), 1).create();

		Topic busy = Topic.findById(1L);
		User modo = User.findByEmail("modo@domain.com");
		for (int i = 0; i < 4; i++) {
			Post post = new Post();
			post.topic = busy;
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

	private static List<String> panelTitles(String body) {
		List<String> titles = new ArrayList<String>();
		Matcher m = Pattern.compile("<h3 class=\"panel-title\">([^<]*)</h3>").matcher(body);
		while (m.find()) {
			titles.add(m.group(1));
		}
		return titles;
	}
}
