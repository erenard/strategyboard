import org.junit.Assert;
import org.junit.Test;

import models.forum.Category;
import models.forum.Forum;
import models.forum.Topic;
import models.leaderboard.Scenario;

/**
 * Coverage of models.forum.Forum.
 *
 *   - toString() is the name; locked defaults to FALSE and the entity keeps a @ManyToOne Category.
 *   - getTopicsCount()/getPostsCount() count everything, getVisibleTopicsCount()/getVisiblePostsCount()
 *     filter hidden topics. Forum 1 of test/data.yml holds the hidden topic t3, so 3 vs 2 topics and
 *     4 vs 3 posts.
 *   - getTopics() and getVisibleTopics() select "distinct t from Topic t, Post p ... order by p.postedAt
 *     desc": the order is the newest post of each topic, and a topic with no post never appears.
 *   - getLastPost() filters hidden topics; a forum without any post answers null, which is what
 *     Forums/show.html renders as no last-post line.
 *   - newTopic() builds the Topic and refresh()es the forum, so the topics collection is populated
 *     before the caller reads it.
 *   - delete() cascades to Topic (cascade = ALL) and Topic cascades to Post, so a forum removal takes its
 *     topics and posts with it. That only works when the topics collection is loaded, which is why
 *     delete() is reached through findById() in forum/AdminForums - the field of a freshly constructed
 *     Forum is null and the cascade then stops at the Topic rows.
 */
public class ForumModelTest extends BaseModelTest {

	@Test
	public void fixtureForumCarriesItsNameCategoryAndOrder() throws Throwable {
		inTransactionVoid(() -> {
			Forum mainForum = Forum.findById(1L);

			Assert.assertEquals("mainForum1", mainForum.name);
			Assert.assertEquals("mainForum1", mainForum.toString());
			Assert.assertEquals("Main Category", mainForum.category.toString());
			Assert.assertEquals("2", String.valueOf(mainForum.category.id));
			Assert.assertEquals("1", String.valueOf(mainForum.displayOrder));
			Assert.assertEquals("false", String.valueOf(mainForum.locked));
			Assert.assertEquals("5", String.valueOf(Forum.count()));
			Assert.assertEquals("[mainForum1, mainForum2, modoForum1, modoForum2, Home Page]", names(Forum.findAll()));
			return null;
		});
	}

	@Test
	public void visibleCountsSkipHiddenTopics() throws Throwable {
		inTransactionVoid(() -> {
			Forum mainForum = Forum.findById(1L);
			Forum homePage = Forum.findById(5L);
			Forum modoForum = Forum.findById(3L);

			// Forum 1: t1 (posts 1 and 2), t2 (post 3), t3 hidden (post 4).
			Assert.assertEquals("3", String.valueOf(mainForum.getTopicsCount()));
			Assert.assertEquals("2", String.valueOf(mainForum.getVisibleTopicsCount()));
			Assert.assertEquals("4", String.valueOf(mainForum.getPostsCount()));
			Assert.assertEquals("3", String.valueOf(mainForum.getVisiblePostsCount()));

			// homePage: news1 and news2, nothing hidden.
			Assert.assertEquals("2", String.valueOf(homePage.getTopicsCount()));
			Assert.assertEquals("2", String.valueOf(homePage.getVisibleTopicsCount()));
			Assert.assertEquals("2", String.valueOf(homePage.getPostsCount()));
			Assert.assertEquals("2", String.valueOf(homePage.getVisiblePostsCount()));

			// modoForum1: a forum with no topic at all.
			Assert.assertEquals("0", String.valueOf(modoForum.getTopicsCount()));
			Assert.assertEquals("0", String.valueOf(modoForum.getVisibleTopicsCount()));
			Assert.assertEquals("0", String.valueOf(modoForum.getPostsCount()));
			Assert.assertEquals("0", String.valueOf(modoForum.getVisiblePostsCount()));
			return null;
		});
	}

	@Test
	public void topicListsAreOrderedByTheNewestPostOfEachTopic() throws Throwable {
		inTransactionVoid(() -> {
			Forum mainForum = Forum.findById(1L);
			Forum homePage = Forum.findById(5L);
			Forum modoForum = Forum.findById(3L);

			// Post 4 (hidden topic, 2024-01-04) is the newest post of Forum 1, so t3 comes first even
			// though getTopics() does not filter hidden topics.
			Assert.assertEquals("[3:Topic Hidden, 2:Topic Locked, 1:Topic Normal]", topicLines(mainForum.getTopics(1, 10)));
			Assert.assertEquals("[2:Topic Locked, 1:Topic Normal]", topicLines(mainForum.getVisibleTopics(1, 10)));
			Assert.assertEquals("[5:Second news, 4:First news]", topicLines(homePage.getTopics(1, 10)));
			Assert.assertEquals("[]", topicLines(modoForum.getTopics(1, 10)));
			return null;
		});
	}

	@Test
	public void topicListsPaginateOnTheSortedRows() throws Throwable {
		inTransactionVoid(() -> {
			Forum mainForum = Forum.findById(1L);

			Assert.assertEquals("[1:Topic Normal]", topicLines(mainForum.getTopics(2, 2)));
			Assert.assertEquals("[]", topicLines(mainForum.getTopics(3, 2)));
			Assert.assertEquals("[2:Topic Locked]", topicLines(mainForum.getVisibleTopics(1, 1)));
			Assert.assertEquals("[1:Topic Normal]", topicLines(mainForum.getVisibleTopics(2, 1)));
			Assert.assertEquals("[]", topicLines(mainForum.getVisibleTopics(3, 1)));
			return null;
		});
	}

	@Test
	public void getLastPostIsTheNewestPostOfAVisibleTopic() throws Throwable {
		inTransactionVoid(() -> {
			Forum mainForum = Forum.findById(1L);
			Forum homePage = Forum.findById(5L);
			Forum modoForum = Forum.findById(3L);

			// Post 4 belongs to the hidden topic t3 and is the newest one in Forum 1; getLastPost()
			// answers post 3 instead, the newest post of a visible topic.
			Assert.assertEquals("3:Topic Locked@2024-01-03 01:00:00.0 by Moderator", describePost(mainForum.getLastPost()));
			Assert.assertEquals("6:Second news@2024-01-06 01:00:00.0 by Admin", describePost(homePage.getLastPost()));
			Assert.assertNull(modoForum.getLastPost());
			return null;
		});
	}

	@Test
	public void constructorCreatesAForumWithNoTopicAndNoPost() throws Throwable {
		inTransactionVoid(() -> {
			Category main = Category.findById(2L);
			Forum fresh = new Forum(main, "Fresh Forum", "Fresh description", 6);

			Assert.assertEquals("6", String.valueOf(fresh.id));
			Assert.assertEquals("Fresh Forum", fresh.name);
			Assert.assertEquals("Fresh description", fresh.description);
			Assert.assertEquals("6", String.valueOf(fresh.displayOrder));
			Assert.assertEquals("false", String.valueOf(fresh.locked));
			Assert.assertEquals("Main Category", fresh.category.toString());
			Assert.assertEquals("0", String.valueOf(fresh.getTopicsCount()));
			Assert.assertEquals("0", String.valueOf(fresh.getPostsCount()));
			Assert.assertEquals("[]", topicLines(fresh.getTopics(1, 10)));
			Assert.assertNull(fresh.getLastPost());
			Assert.assertEquals("6", String.valueOf(Forum.count()));
			// The topics field is not initialised by the constructor, unlike PlayedGame.scores.
			Assert.assertNull(fresh.topics);
			return null;
		});
	}

	@Test
	public void newTopicAddsATopicWithOnePostAndRefreshesTheForum() throws Throwable {
		inTransactionVoid(() -> {
			Category main = Category.findById(2L);
			Forum fresh = new Forum(main, "Fresh Forum", "Fresh description", 6);
			Topic created = fresh.newTopic(user("bob@domain.com"), "Fresh topic", "Fresh content");

			Assert.assertEquals("6", String.valueOf(created.id));
			Assert.assertEquals("6", String.valueOf(created.forum.id));
			Assert.assertEquals("1", String.valueOf(created.getPostsCount()));

			Forum refreshed = (Forum) fresh.refresh();
			Assert.assertEquals(1, refreshed.topics.size());
			Assert.assertEquals("[6:Fresh topic]", topicLines(refreshed.topics));
			Assert.assertEquals("1", String.valueOf(fresh.getTopicsCount()));
			Assert.assertEquals("1", String.valueOf(fresh.getVisibleTopicsCount()));
			Assert.assertEquals("1", String.valueOf(fresh.getPostsCount()));
			Assert.assertEquals("1", String.valueOf(fresh.getVisiblePostsCount()));
			Assert.assertEquals("[6:Fresh topic]", topicLines(fresh.getTopics(1, 10)));
			// The post of a topic made in Java carries a java.util.Date, so it is read as an object,
			// not as a timestamp string.
			Assert.assertEquals("Bob", fresh.getLastPost().postedBy.name);
			Assert.assertNotNull(fresh.getLastPost());
			return null;
		});
	}

	@Test
	public void deleteCascadeTakesTheTopicsAndTheirPosts() throws Throwable {
		inTransactionVoid(() -> {
			Forum mainForum = Forum.findById(1L);
			mainForum = (Forum) mainForum.refresh();

			Assert.assertEquals("[1:Topic Normal, 2:Topic Locked, 3:Topic Hidden]", topicLines(mainForum.topics));
			mainForum.delete();

			// Forum 1 held 3 topics and 4 posts.
			Assert.assertEquals("4", String.valueOf(Forum.count()));
			Assert.assertEquals("2", String.valueOf(Topic.count()));
			Assert.assertEquals("2", String.valueOf(models.forum.Post.count()));
			Assert.assertNull(Forum.findById(1L));
			Assert.assertEquals("[mainForum2, modoForum1, modoForum2, Home Page]", names(Forum.findAll()));
			return null;
		});
	}

	@Test
	public void deleteCascadeWorksEvenWhenTheTopicsFieldWasNeverRead() throws Throwable {
		inTransactionVoid(() -> {
			Forum mainForum = Forum.findById(1L);
			mainForum.delete();

			Assert.assertEquals("4", String.valueOf(Forum.count()));
			Assert.assertEquals("2", String.valueOf(Topic.count()));
			Assert.assertEquals("2", String.valueOf(models.forum.Post.count()));
			return null;
		});
	}

	@Test
	public void aForumWithNoTopicCanBeRemoved() throws Throwable {
		inTransactionVoid(() -> {
			Forum modoForum = Forum.findById(3L);
			modoForum.delete();

			Assert.assertEquals("4", String.valueOf(Forum.count()));
			Assert.assertNull(Forum.findById(3L));
			// The other moderators forum is untouched.
			Assert.assertEquals("modoForum2", Forum.findById(4L).toString());
			return null;
		});
	}

	@Test
	public void scenarioAndForumRowsNeverMeet() throws Throwable {
		// Forum lives in the forum package, Scenario in the leaderboard one; this only pins that the
		// forum fixtures and the leaderboard fixtures are separate tables.
		inTransactionVoid(() -> {
			Assert.assertEquals("0", String.valueOf(Scenario.count()));
			Assert.assertEquals("5", String.valueOf(Forum.count()));
			return null;
		});
	}
}
