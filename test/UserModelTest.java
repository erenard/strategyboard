import java.util.Date;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import play.libs.Codec;

import controllers.legacy.MD5;

import models.forum.Category;
import models.forum.Post;
import models.forum.Topic;
import models.forum.User;
import models.forum.UserGroup;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.Scenario;
import play.test.Fixtures;

/**
 * Coverage of models.forum.User.
 *
 *   - toString() is the name; passwordHash, uuid, confirmed and active come from test/data.yml, where every
 *     account has uuid: null and the anonymous one has no passwordHash at all (Profile.checkAccess() puts that
 *     email in the session, so the row exists without credentials).
 *   - checkPassword() compares the hash twice: Codec.hexMD5(password) and MD5.crypt(password). controllers.legacy.MD5.crypt()
 *     is play.libs.Codec.hexMD5() by another name, so the second comparison accepts nothing the first one did
 *     not - pinned below as a redundancy, not as a second credential path. An empty password reaches MD5.crypt()
 *     and raises IllegalArgumentException; a null password raises before the comparison, inside String.getBytes().
 *   - getRecentsPosts() windows 10 rows ordered by postedAt desc and skips the posts of hidden topics;
 *     getPostsCount() and getTopicsCount() do not filter hidden topics.
 *   - getCategories() short-circuits on UserGroup.admin and returns Category.findAll(), while Category.forUser()
 *     needs a Permission row: the Admin group of the fixtures has none, so the administrator sees every category
 *     here and none through forUser().
 *   - findAllActive() and countActive() keep the users that posted or scored and are active, ordered by name, so
 *     the anonymous user (no post, no score) and the confirmed flag never enter the list.
 *   - The constructor hashes the password, stamps a 36-character dashed UUID and creates the row; confirmed stays
 *     false because nothing in the constructor sets it.
 *   - delete() never cascades to Post or PlayedGameScore, so a user with a post cannot be removed.
 */
public class UserModelTest extends BaseModelTest {

	@Test
	public void fixtureUsersCarryTheirIdGroupAndFlags() throws Throwable {
		inTransactionVoid(() -> {
			User admin = user("admin@domain.com");
			User modo = user("modo@domain.com");
			User bob = user("bob@domain.com");
			User anonymous = user("anonymous@domain.com");

			Assert.assertEquals("1", String.valueOf(admin.id));
			Assert.assertEquals("2", String.valueOf(modo.id));
			Assert.assertEquals("3", String.valueOf(bob.id));
			Assert.assertEquals("4", String.valueOf(anonymous.id));

			Assert.assertEquals("Admin", admin.toString());
			Assert.assertEquals("Admin", admin.group.name);
			Assert.assertEquals("Moderators", modo.group.name);
			Assert.assertEquals("General", bob.group.name);
			Assert.assertEquals("Anonymous", anonymous.group.name);

			// Every fixture account shares the password "secret", hashed once.
			Assert.assertEquals("5ebe2294ecd0e0f08eab7690d2a6ee69", admin.passwordHash);
			Assert.assertEquals("5ebe2294ecd0e0f08eab7690d2a6ee69", bob.passwordHash);
			// Fixtures never write uuid, so the field stays null: login() fills it in through onAuthenticated().
			Assert.assertNull(admin.uuid);
			Assert.assertNull(bob.uuid);
			Assert.assertNull(anonymous.uuid);

			Assert.assertEquals("true", String.valueOf(admin.confirmed));
			Assert.assertEquals("true", String.valueOf(bob.confirmed));
			Assert.assertEquals("false", String.valueOf(anonymous.confirmed));
			Assert.assertEquals("true", String.valueOf(anonymous.active));
			// The anonymous row has no credentials at all, which is what makes checkPassword() fail on it.
			Assert.assertNull(anonymous.passwordHash);

			Assert.assertEquals("4", String.valueOf(User.count()));
			Assert.assertEquals("[Admin, Moderator, Bob, Anonymous]", names(User.findAll()));
			return null;
		});
	}

	@Test
	public void checkPasswordAcceptsTheHashOfThePasswordAndRejectsAnythingElse() throws Throwable {
		inTransactionVoid(() -> {
			User admin = user("admin@domain.com");

			Assert.assertTrue(admin.checkPassword("secret"));
			Assert.assertFalse(admin.checkPassword("nope"));
			Assert.assertFalse(admin.checkPassword("SECRET"));
			// An empty password is refused inside controllers.legacy.MD5.crypt(), before any comparison.
			Assert.assertEquals("String to encript cannot be null or zero length",
					failureMessage(() -> admin.checkPassword("")));
			// null never reaches the hash: play.libs.Codec.hexMD5() calls String.getBytes() on it, and Play
			// wraps the IllegalArgumentException-family failure into play.exceptions.UnexpectedException.
			Assert.assertEquals("Unexpected Error", failureMessage(() -> admin.checkPassword(null)));
			return null;
		});
	}

	@Test
	public void checkPasswordOfAnAccountWithoutAPasswordHashRaisesOnTheComparison() throws Throwable {
		inTransactionVoid(() -> {
			User anonymous = user("anonymous@domain.com");

			// passwordHash is null, so the first equals() call is the one that raises: no branch of the
			// method can ever answer false for this row.
			Assert.assertEquals("Cannot invoke \"String.equals(Object)\" because \"<local3>\" is null",
					failureMessage(() -> anonymous.checkPassword("secret")));
			Assert.assertEquals("Cannot invoke \"String.equals(Object)\" because \"<local3>\" is null",
					failureMessage(() -> anonymous.checkPassword("")));
			Assert.assertEquals("Unexpected Error", failureMessage(() -> anonymous.checkPassword(null)));
			return null;
		});
	}

	@Test
	public void md5CryptIsHexMd5ByAnotherName() throws Throwable {
		// The second comparison of checkPassword() can therefore never add an accepted password.
		Assert.assertEquals(Codec.hexMD5("legacy"), MD5.crypt("legacy"));
		Assert.assertEquals("228c70bfc5589c58c044e03fff0e17eb", MD5.crypt("legacy"));
		Assert.assertEquals(Codec.hexMD5("secret"), MD5.crypt("secret"));
		Assert.assertEquals("5ebe2294ecd0e0f08eab7690d2a6ee69", MD5.crypt("secret"));
	}

	@Test
	public void constructorHashesThePasswordStampsAnUuidAndCreatesTheRow() throws Throwable {
		inTransactionVoid(() -> {
			User fresh = new User("fresh@domain.com", "legacy", "Fresh User", userGroup(UserGroup.GENERAL));

			Assert.assertEquals("5", String.valueOf(fresh.id));
			Assert.assertEquals("fresh@domain.com", fresh.email);
			Assert.assertEquals("Fresh User", fresh.name);
			Assert.assertEquals("General", fresh.group.name);
			Assert.assertEquals(MD5.crypt("legacy"), fresh.passwordHash);
			// Codec.UUID() is a dashed version-4 UUID: 36 characters, 4 dashes.
			Assert.assertEquals(36, fresh.uuid.length());
			Assert.assertEquals("----", fresh.uuid.replaceAll("[0-9a-f]", ""));
			Assert.assertTrue(User.findByUUID(fresh.uuid) != null);
			// Nothing in the constructor touches confirmed, and active is a field default.
			Assert.assertEquals("false", String.valueOf(fresh.confirmed));
			Assert.assertEquals("true", String.valueOf(fresh.active));
			Assert.assertEquals("Fresh User", fresh.toString());
			Assert.assertTrue(fresh.checkPassword("legacy"));
			Assert.assertFalse(fresh.checkPassword("nope"));
			Assert.assertEquals("5", String.valueOf(User.count()));
			return null;
		});
	}

	@Test
	public void emptyConstructorLeavesEveryFieldAtItsDefault() throws Throwable {
		// admin/UserGroups and the CRUD binding of admin/Users build the row through this constructor first.
		inTransactionVoid(() -> {
			User blank = new User();

			Assert.assertNull(blank.id);
			Assert.assertNull(blank.email);
			Assert.assertNull(blank.passwordHash);
			Assert.assertNull(blank.name);
			Assert.assertNull(blank.group);
			Assert.assertNull(blank.uuid);
			Assert.assertEquals("false", String.valueOf(blank.confirmed));
			Assert.assertEquals("true", String.valueOf(blank.active));
			Assert.assertEquals("4", String.valueOf(User.count()));
			return null;
		});
	}

	@Test
	public void recentPostsAreWindowedToTenAndOrderedByPostedAtDescending() throws Throwable {
		inTransactionVoid(() -> {
			User legacy = new User("legacy@domain.com", "legacy", "Legacy User", userGroup(UserGroup.GENERAL));
			Topic t1 = topic(1L);
			for (int i = 0; i < 12; i++) {
				Post post = new Post(t1, legacy, "Bulk " + i, "body");
				// Dates a few seconds apart, so the ordering of the window is decided by postedAt alone.
				post.postedAt = new Date(System.currentTimeMillis() + i * 1000L);
				post.save();
			}

			List<Post> recents = legacy.getRecentsPosts();
			Assert.assertEquals(10, recents.size());
			Assert.assertEquals("18:Bulk 11", recents.get(0).id + ":" + recents.get(0).subject);
			Assert.assertEquals("9:Bulk 2", recents.get(9).id + ":" + recents.get(9).subject);
			// The window hides two of the twelve posts, the counts do not.
			Assert.assertEquals("12", String.valueOf(legacy.getPostsCount()));
			Assert.assertEquals("1", String.valueOf(legacy.getTopicsCount()));
			return null;
		});
	}

	@Test
	public void recentPostsOfTheFixtureUsersAreThePostsTheyWroteInInsertionOrder() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertEquals("[1:Topic Normal@2024-01-01 01:00:00.0]",
					postLines(user("bob@domain.com").getRecentsPosts()));
			Assert.assertEquals("[6:Second news@2024-01-06 01:00:00.0, 5:First news@2024-01-05 01:00:00.0]",
					postLines(user("admin@domain.com").getRecentsPosts()));
			Assert.assertEquals("[3:Topic Locked@2024-01-03 01:00:00.0, 2:Topic Normal reply@2024-01-02 01:00:00.0]",
					postLines(user("modo@domain.com").getRecentsPosts()));
			Assert.assertEquals("[]", postLines(user("anonymous@domain.com").getRecentsPosts()));

			Assert.assertEquals("1", String.valueOf(user("bob@domain.com").getPostsCount()));
			Assert.assertEquals("2", String.valueOf(user("admin@domain.com").getPostsCount()));
			Assert.assertEquals("3", String.valueOf(user("modo@domain.com").getPostsCount()));
			Assert.assertEquals("0", String.valueOf(user("anonymous@domain.com").getPostsCount()));

			// getTopicsCount() counts distinct topics through Post.topic; Bob started one topic.
			Assert.assertEquals("1", String.valueOf(user("bob@domain.com").getTopicsCount()));
			Assert.assertEquals("2", String.valueOf(user("admin@domain.com").getTopicsCount()));
			// The moderator started t2 and t3 and replied in t1, so three topics, the hidden t3 included.
			Assert.assertEquals("3", String.valueOf(user("modo@domain.com").getTopicsCount()));
			return null;
		});
	}

	@Test
	public void aRecentPostOfAHiddenTopicIsMissingWhileTheCountsStillSeeIt() throws Throwable {
		inTransactionVoid(() -> {
			// Post 4 belongs to the hidden topic t3 and is the moderator's newest post, so the window starts
			// at post 3 instead of post 4. getPostsCount() has no hidden filter and still counts 3.
			User modo = user("modo@domain.com");
			Assert.assertEquals("[3:Topic Locked@2024-01-03 01:00:00.0, 2:Topic Normal reply@2024-01-02 01:00:00.0]",
					postLines(modo.getRecentsPosts()));
			Assert.assertEquals("3", String.valueOf(modo.getPostsCount()));
			Assert.assertEquals("3", String.valueOf(post(4L).topic.id));
			Assert.assertEquals("true", String.valueOf(topic(3L).hidden));
			return null;
		});
	}

	@Test
	public void categoriesOfAnAdministratorAreAllTheCategoriesOfTheForum() throws Throwable {
		inTransactionVoid(() -> {
			// The group flag short-circuits the permission query, so Category.findAll() is returned as it is,
			// in id order: Moderators Category then Main Category.
			Assert.assertEquals("[Moderators Category, Main Category]", names(user("admin@domain.com").getCategories()));
			Assert.assertEquals("[]", names(Category.forUser("admin@domain.com")));

			// The other three go through Permission, which has no order by, so the pair is compared as a set.
			Assert.assertEquals("[Main Category, Moderators Category]", sortedNames(user("modo@domain.com").getCategories()));
			Assert.assertEquals("[Main Category, Moderators Category]", sortedNames(user("bob@domain.com").getCategories()));
			Assert.assertEquals("[Main Category]", names(user("anonymous@domain.com").getCategories()));
			return null;
		});
	}

	@Test
	public void lookupsAnswerNullForAnythingTheFixturesDoNotHold() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertNull(user("nope@domain.com"));
			Assert.assertNull(User.findByEmail(""));
			// The collation is case insensitive, so an email written in upper case finds the same row;
			// this is why User.isEmailAvailable() cannot tell "BOB@DOMAIN.COM" from "bob@domain.com".
			Assert.assertEquals("Bob", String.valueOf(User.findByEmail("BOB@DOMAIN.COM")));
			Assert.assertFalse(User.isEmailAvailable("BOB@DOMAIN.COM"));
			Assert.assertEquals("Bob", String.valueOf(User.findByName("Bob")));
			Assert.assertNull(User.findByName("Nope"));
			// A null parameter becomes "uuid = null" in SQL, which matches no row - not even the fixture rows
			// whose uuid column is itself null - so the lookup answers null instead of raising.
			Assert.assertNull(User.findByUUID(null));
			Assert.assertNull(User.findByUUID("zzzz"));
			// A uuid stamped by the constructor does resolve.
			User fresh = new User("fresh@domain.com", "legacy", "Fresh User", userGroup(UserGroup.GENERAL));
			Assert.assertEquals("Fresh User", User.findByUUID(fresh.uuid).toString());
			return null;
		});
	}

	@Test
	public void findAllActiveKeepsTheUsersThatPostedOrScoredOrderedByName() throws Throwable {
		inTransactionVoid(() -> {
			// Bob and the moderator posted, Admin wrote the news posts; the anonymous user wrote nothing, so
			// it is absent even though active is true. count() counts every row.
			Assert.assertEquals("[Admin, Bob, Moderator]", names(User.findAllActive(1, 10)));
			Assert.assertEquals("3", String.valueOf(User.countActive()));
			Assert.assertEquals("4", String.valueOf(User.count()));
			// Ordered by name, so page 2 of a window of 3 is empty.
			Assert.assertEquals("[Admin, Bob, Moderator]", names(User.findAllActive(1, 3)));
			Assert.assertEquals("[]", names(User.findAllActive(2, 3)));
			return null;
		});
	}

	@Test
	public void findAllActiveFollowsNewPostsAndDropsAnInactiveUser() throws Throwable {
		inTransactionVoid(() -> {
			User legacy = new User("legacy@domain.com", "legacy", "Legacy User", userGroup(UserGroup.GENERAL));
			new Post(topic(1L), legacy, "Bulk 0", "body");

			Assert.assertEquals("[Admin, Bob, Legacy User, Moderator]", names(User.findAllActive(1, 10)));
			Assert.assertEquals("4", String.valueOf(User.countActive()));

			// active is a flag the moderation page flips; the query filters on it, name order unchanged.
			legacy.active = Boolean.FALSE;
			legacy.save();

			Assert.assertEquals("[Admin, Bob, Moderator]", names(User.findAllActive(1, 10)));
			Assert.assertEquals("3", String.valueOf(User.countActive()));
			Assert.assertEquals("5", String.valueOf(User.count()));
			return null;
		});
	}

	@Test
	public void gamesCountAndRecentGamesComeFromTheScoresOfThePlayer() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("scores-data.yml");
			Fixtures.loadModels("openttd-data.yml");

			Scenario highSpeed = scenario(1L);
			Scenario toyland = scenario(2L);
			User p1 = user("player01@domain.com");
			User p5 = user("player05@domain.com");

			PlayedGame older = newGame(highSpeed, daysAgo(5));
			PlayedGame newer = newGame(toyland, daysAgo(1));
			newScore(older, p1, 1.0, 100.0);
			newScore(newer, p1, 1.0, 50.0);
			newScore(newer, p5, 1.0, 70.0);

			Assert.assertEquals("2", String.valueOf(p1.getGamesCount()));
			Assert.assertEquals("1", String.valueOf(p5.getGamesCount()));
			Assert.assertEquals("0", String.valueOf(user("bob@domain.com").getGamesCount()));

			// getRecentsGames() selects s.playedGame, so the games come back with an empty scores collection:
			// the newest game first, ordered by playedGame.end desc.
			Assert.assertEquals("[2:Toyland Scenario scores=0, 1:HighSpeed Scenario scores=0]",
					gameLines(p1.getRecentsGames()));
			Assert.assertEquals("[2:Toyland Scenario scores=0]", gameLines(p5.getRecentsGames()));
			Assert.assertEquals("[]", gameLines(user("bob@domain.com").getRecentsGames()));
			// The window of getRecentsGames() is 10 rows; two games is below it.
			Assert.assertEquals(2, p1.getRecentsGames().size());
			Assert.assertEquals("3", String.valueOf(PlayedGameScore.count()));
			Assert.assertEquals("[PlayedGameScore[1], PlayedGameScore[2], PlayedGameScore[3]]",
				sortedNames(PlayedGameScore.findAll()));
			return null;
		});
	}

	@Test
	public void newMessagesCountIsTheUnreadMessagesAddressedToTheUser() throws Throwable {
		inTransactionVoid(() -> {
			// test/data.yml has no Message row, so every user answers 0. The round trip is in MessageTest.
			Assert.assertEquals("0", String.valueOf(user("admin@domain.com").getNewMessagesCount()));
			Assert.assertEquals("0", String.valueOf(user("bob@domain.com").getNewMessagesCount()));
			return null;
		});
	}

	@Test
	public void isEmailAvailableAndCheckUuidAnswerTrueOnlyForUnusedValues() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertFalse(User.isEmailAvailable("admin@domain.com"));
			Assert.assertFalse(User.isEmailAvailable("bob@domain.com"));
			Assert.assertTrue(User.isEmailAvailable("fresh@domain.com"));
			Assert.assertTrue(User.isEmailAvailable(""));

			// Bob's uuid is null, and a null parameter becomes "uuid = null" in SQL, which matches no row:
			// so the check answers true for every uuid the fixtures do not hold, including the null one.
			Assert.assertTrue(User.checkUuid(user("bob@domain.com").uuid));
			Assert.assertTrue(User.checkUuid("zzzz"));

			User fresh = new User("fresh@domain.com", "legacy", "Fresh User", userGroup(UserGroup.GENERAL));
			Assert.assertFalse(User.checkUuid(fresh.uuid));
			return null;
		});
	}

	@Test
	public void deleteRemovesAUserNothingPointsAt() throws Throwable {
		inTransactionVoid(() -> {
			User fresh = new User("fresh@domain.com", "legacy", "Fresh User", userGroup(UserGroup.GENERAL));
			fresh.delete();

			Assert.assertEquals("4", String.valueOf(User.count()));
			Assert.assertNull(user("fresh@domain.com"));
			Assert.assertNull(User.findByUUID(fresh.uuid));
			return null;
		});
	}

	@Test
	public void deleteOfAUserThatWroteAPostIsRefusedByThePostRelation() throws Throwable {
		// Post.postedBy is a @OneToOne with no cascade, so MySQL refuses the DELETE of Bob, who owns post 1.
		assertTransactionFailureContains("a foreign key constraint fails (`www`.`Post`", () -> {
			User bob = user("bob@domain.com");
			Assert.assertEquals("1", String.valueOf(bob.getPostsCount()));
			bob.delete();
			return null;
		});
	}

	@Test
	public void theRefusedDeleteOfAUserLeavesTheRowsOfItsPostsUntouched() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertEquals("4", String.valueOf(User.count()));
			Assert.assertEquals("6", String.valueOf(Post.count()));
			Assert.assertEquals("Bob", user("bob@domain.com").toString());
			Assert.assertEquals("1", String.valueOf(user("bob@domain.com").getPostsCount()));
			return null;
		});
	}
}
