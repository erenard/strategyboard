import org.junit.Assert;
import org.junit.Test;

import models.forum.Category;
import models.forum.Forum;
import models.forum.Permission;
import models.forum.Post;
import models.forum.Topic;

/**
 * Coverage of models.forum.Category.
 *
 *   - toString() is the name, which is what every template prints (Categories/index.html, the CRUD lists).
 *   - getForumsCount(), getTopicsCount() and getPostsCount() count through the relations, so a hidden topic
 *     and its post are counted: Main Category holds the hidden topic t3 of Forum 1 (test/data.yml).
 *   - getForums() and getLastPost() take a page and a page size; getLastPost() only looks at visible topics.
 *   - forUser(email) walks Permission -> UserGroup -> User and drops accessLevel 0. The Admin group of
 *     test/data.yml has no Permission row at all, so the administrator gets an empty list here even though
 *     User.getCategories() short-circuits on UserGroup.admin and returns every category.
 *   - The constructor creates the row, which is how forum/Categories are made.
 *   - delete() does not cascade to Permission: the @OneToMany on Category only covers Forum (the
 *     permissions field is commented out in the entity), so a category that permissions point at cannot be
 *     removed. Whether or not the forums collection was read first, the cascade reaches the forums and
 *     MariaDB refuses through Permission.category_id.
 *   - A refused delete leaves the transaction rollback-only, so every read after it raises: the surviving
 *     rows are counted in a second transaction (see assertTransactionFailureContains in BaseModelTest).
 */
public class CategoryModelTest extends BaseModelTest {

	@Test
	public void fixtureCategoriesAreNamedInIdOrder() throws Throwable {
		inTransactionVoid(() -> {
			Category moderators = Category.findById(1L);
			Category main = Category.findById(2L);

			Assert.assertEquals("1", String.valueOf(moderators.id));
			Assert.assertEquals("2", String.valueOf(main.id));
			Assert.assertEquals("Moderators Category", moderators.toString());
			Assert.assertEquals("Main Category", main.toString());
			Assert.assertEquals("Moderators Category", moderators.name);
			Assert.assertEquals("Main Category", main.name);
			// test/data.yml never sets displayOrder, and Fixtures only writes the keys it finds, so the
			// @Required Integer stays null. Only Category(name, displayOrder) ever fills it in.
			Assert.assertNull(moderators.displayOrder);
			Assert.assertNull(main.displayOrder);
			Assert.assertEquals("[Moderators Category, Main Category]", names(Category.findAll()));
			Assert.assertEquals("2", String.valueOf(Category.count()));
			return null;
		});
	}

	@Test
	public void countsWalkForumsTopicsAndPostsIncludingHiddenTopics() throws Throwable {
		inTransactionVoid(() -> {
			Category main = Category.findById(2L);
			Category moderators = Category.findById(1L);

			// Main Category: mainForum1, mainForum2 and homePage; 5 topics and 6 posts, the hidden topic t3
			// and its post included because none of the three counts filters on hidden.
			Assert.assertEquals("3", String.valueOf(main.getForumsCount()));
			Assert.assertEquals("5", String.valueOf(main.getTopicsCount()));
			Assert.assertEquals("6", String.valueOf(main.getPostsCount()));

			// Moderators Category: two forums, no topic in test/data.yml.
			Assert.assertEquals("2", String.valueOf(moderators.getForumsCount()));
			Assert.assertEquals("0", String.valueOf(moderators.getTopicsCount()));
			Assert.assertEquals("0", String.valueOf(moderators.getPostsCount()));
			return null;
		});
	}

	@Test
	public void getForumsPaginatesWithoutAnyOrderingClause() throws Throwable {
		inTransactionVoid(() -> {
			Category main = Category.findById(2L);
			Category moderators = Category.findById(1L);

			// Forum.find("category = ?1") has no order by, so the rows come back in insertion order:
			// mainForum1, mainForum2, homePage for Main Category.
			Assert.assertEquals("[mainForum1, mainForum2, Home Page]", names(main.getForums(1, 10)));
			Assert.assertEquals("[modoForum1, modoForum2]", names(moderators.getForums(1, 10)));
			Assert.assertEquals("[Home Page]", names(main.getForums(2, 2)));
			Assert.assertEquals("[]", names(main.getForums(3, 2)));
			return null;
		});
	}

	@Test
	public void getLastPostIsTheNewestPostOfAVisibleTopic() throws Throwable {
		inTransactionVoid(() -> {
			Category main = Category.findById(2L);
			Category moderators = Category.findById(1L);

			Assert.assertEquals("6:Second news@2024-01-06 01:00:00.0 by Admin", describePost(main.getLastPost()));
			// The hidden topic t3 is excluded by "topic.hidden = false", but its post 4 is older than post 6
			// anyway, so the filter cannot be seen here; Forum.getLastPost() documents the same filter.
			Assert.assertNull(moderators.getLastPost());
			return null;
		});
	}

	@Test
	public void forUserListsTheCategoriesTheUsersGroupMayPostIn() throws Throwable {
		inTransactionVoid(() -> {
			// No Permission row for the Admin group: the query needs p.accessLevel > 0.
			Assert.assertEquals("[]", names(Category.forUser("admin@domain.com")));
			// Moderators hold moderateMain (4) and postModerators (3); the General group holds postMain (3)
			// and postModerators (3). "select distinct" has no order by, so the pair is compared as a set.
			Assert.assertEquals("[Main Category, Moderators Category]", sortedNames(Category.forUser("modo@domain.com")));
			Assert.assertEquals("[Main Category, Moderators Category]", sortedNames(Category.forUser("bob@domain.com")));
			// The anonymous group only has viewMain (accessLevel 1), which still passes "> 0".
			Assert.assertEquals("[Main Category]", names(Category.forUser("anonymous@domain.com")));
			Assert.assertEquals("[]", names(Category.forUser("nope@domain.com")));
			return null;
		});
	}

	@Test
	public void constructorCreatesACategoryWithNoForumNoTopicAndNoPost() throws Throwable {
		inTransactionVoid(() -> {
			Category fresh = new Category("Fresh Category", 7);

			Assert.assertEquals("3", String.valueOf(fresh.id));
			Assert.assertEquals("Fresh Category", fresh.name);
			Assert.assertEquals("7", String.valueOf(fresh.displayOrder));
			Assert.assertEquals("0", String.valueOf(fresh.getForumsCount()));
			Assert.assertEquals("0", String.valueOf(fresh.getTopicsCount()));
			Assert.assertEquals("0", String.valueOf(fresh.getPostsCount()));
			Assert.assertEquals("[]", names(fresh.getForums(1, 10)));
			Assert.assertNull(fresh.getLastPost());
			Assert.assertEquals("3", String.valueOf(Category.count()));
			Assert.assertEquals("[Moderators Category, Main Category, Fresh Category]", names(Category.findAll()));
			return null;
		});
	}

	@Test
	public void deleteRemovesACategoryNothingPointsAt() throws Throwable {
		inTransactionVoid(() -> {
			Category fresh = new Category("Fresh Category", 7);
			fresh.delete();

			Assert.assertEquals("2", String.valueOf(Category.count()));
			Assert.assertNull(Category.findById(3L));
			return null;
		});
	}

	@Test
	public void deleteOfALoadedCategoryIsRefusedByThePermissionsThatPointAtIt() throws Throwable {
		// Category cascades to Forum only. With the forums collection loaded the DELETE reaches the
		// forums of Main Category, and MySQL refuses them through Permission.category_id.
		assertTransactionFailureContains("a foreign key constraint fails (`www`.`Permission`", () -> {
			Category main = (Category) category(2L).refresh();
			Assert.assertEquals("[mainForum1, mainForum2, Home Page]", names(main.forums));
			main.delete();
			return null;
		});
	}

	@Test
	public void deleteOfACategoryNobodyReadIsRefusedTheSameWay() throws Throwable {
		// Reading nothing first changes nothing: delete() cascades through the forums collection, which
		// Hibernate loads at that moment, so MariaDB still refuses the forum DELETE through
		// Permission.category_id instead of reporting a stale cascade batch.
		assertTransactionFailureContains("a foreign key constraint fails (`www`.`Permission`", () -> {
			category(2L).delete();
			return null;
		});
	}

	@Test
	public void nothingIsRemovedWhenTheDeleteOfACategoryIsRefused() throws Throwable {
		// The two refused deletes above left their transaction rollback-only, so the surviving rows are
		// counted here, in a transaction of its own, on the committed state.
		inTransactionVoid(() -> {
			Assert.assertEquals("2", String.valueOf(Category.count()));
			Assert.assertEquals("5", String.valueOf(Permission.count()));
			Assert.assertEquals("5", String.valueOf(Forum.count()));
			Assert.assertEquals("5", String.valueOf(Topic.count()));
			Assert.assertEquals("6", String.valueOf(Post.count()));
			Assert.assertEquals("Main Category", category(2L).toString());
			// getLastPost() walks the category through its forums, so the news post of the Home Page forum
			// (post 6) is still the newest one of Main Category.
			Assert.assertEquals("Post[6]", String.valueOf(category(2L).getLastPost()));
			return null;
		});
	}

	@Test
	public void forUserResolvesTheGroupsOfTheFixtureUsers() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertEquals("Admin", user("admin@domain.com").group.name);
			Assert.assertEquals("Moderators", user("modo@domain.com").group.name);
			Assert.assertEquals("General", user("bob@domain.com").group.name);
			Assert.assertEquals("Anonymous", user("anonymous@domain.com").group.name);
			return null;
		});
	}
}
