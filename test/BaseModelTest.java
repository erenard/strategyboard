import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.Assert;

import play.libs.F;

import models.forum.Attachment;
import models.forum.Category;
import models.forum.Forum;
import models.forum.Message;
import models.forum.Post;
import models.forum.Topic;
import models.forum.User;
import models.forum.UserGroup;
import models.leaderboard.Game;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.PlayerScore;
import models.leaderboard.Scenario;
import models.openttd.OpenttdServer;

/**
 * Setup shared by the model suites (CategoryModelTest, ForumModelTest, TopicModelTest, PostModelTest,
 * AttachmentModelTest, UserGroupModelTest, PermissionModelTest, ScenarioModelTest, GameModelTest,
 * PlayedGameModelTest, PlayedGameScoreModelTest, OpenttdServerTest).
 *
 * These suites never issue a request: they call the models inside a JPA transaction. They inherit the
 * fixture seeding of BaseFunctionalTest (test/data.yml before every method, ids restarting at 1 because
 * Fixtures.deleteDatabase() truncates the tables) and the transaction helpers of BaseForumViewTest.
 *
 * The list helpers below print a row the way JPA hands it back, so a failure names the rows it compared:
 * entities render as EntityName[id] through play.db.jpa.JPABase.toString(), and a fixture date prints as
 * java.sql.Timestamp ("2024-01-01 01:00:00.0"). A date built in Java is java.util.Date ("Wed Nov 18 ..."),
 * so those are compared by relative order, never by their string form.
 */
public abstract class BaseModelTest extends BaseForumViewTest {

	/** String.valueOf of every row, as "[PlayedGameScore[1], ...]". */
	protected static String names(List<?> rows) {
		List<String> out = new ArrayList<String>();
		for (Object row : rows) {
			out.add(String.valueOf(row));
		}
		return out.toString();
	}

	/** names() of a query that has no "order by": the row order is whatever MySQL returns. */
	protected static String sortedNames(List<?> rows) {
		List<String> out = new ArrayList<String>();
		for (Object row : rows) {
			out.add(String.valueOf(row));
		}
		java.util.Collections.sort(out);
		return out.toString();
	}

	/** Topic id plus the subject of its first post, which is what a topic displays as its title. */
	protected static String topicLines(List<?> rows) {
		List<String> out = new ArrayList<String>();
		for (Object row : rows) {
			Topic topic = (Topic) row;
			Post first = topic.getFirstPost();
			out.add(topic.id + ":" + (first == null ? "(no post)" : first.subject));
		}
		return out.toString();
	}

	/**
	 * Post id, subject and timestamp: "[1:Topic Normal@2024-01-01 01:00:00.0]". The author is left out,
	 * because a list of posts is compared as a whole and describePost() adds it for the single-row cases
	 * (getFirstPost(), getLastPost()) where the author is the point of the assertion.
	 */
	protected static String postLines(List<?> rows) {
		List<String> out = new ArrayList<String>();
		for (Object row : rows) {
			Post post = (Post) row;
			out.add(post.id + ":" + post.subject + "@" + post.postedAt);
		}
		return out.toString();
	}

	protected static String describePost(Post post) {
		return post == null ? "null"
				: post.id + ":" + post.subject + "@" + post.postedAt + " by " + post.postedBy;
	}

	/** Attachment id, filename and its three relations: "1:report.txt post=Post[1] user=Bob message=null". */
	protected static String attachmentLines(List<?> rows) {
		List<String> out = new ArrayList<String>();
		for (Object row : rows) {
			Attachment attachment = (Attachment) row;
			out.add(attachment.id + ":" + attachment.filename + " post=" + attachment.post
					+ " user=" + attachment.user + " message=" + attachment.message);
		}
		return out.toString();
	}

	protected static String messageLines(List<?> rows) {
		List<String> out = new ArrayList<String>();
		for (Object row : rows) {
			Message message = (Message) row;
			out.add(message.id + ":" + message.subject + " from=" + message.from + " to=" + message.to
					+ " readed=" + message.readed);
		}
		return out.toString();
	}

	/** Played game ids in the order the query returned them: "[2, 1]". */
	protected static String gameIds(List<?> rows) {
		List<String> out = new ArrayList<String>();
		for (Object row : rows) {
			out.add(String.valueOf(((PlayedGame) row).id));
		}
		return out.toString();
	}

	/**
	 * Played game rows as "[1:HighSpeed Scenario scores=2]". The begin and end of a game created in Java are
	 * java.util.Date of the moment of creation, so they are never printed into an expectation; the scores
	 * size is, because it shows whether the collection was loaded.
	 */
	protected static String gameLines(List<?> rows) {
		List<String> out = new ArrayList<String>();
		for (Object row : rows) {
			PlayedGame game = (PlayedGame) row;
			out.add(game.id + ":" + game.scenario + " scores=" + game.scores.size());
		}
		return out.toString();
	}

	/** PlayerScore rows as "[index=1 player=Score Player 01 id=5 points=280 games=2]". */
	protected static String scoreLines(List<PlayerScore> rows) {
		List<String> out = new ArrayList<String>();
		for (PlayerScore row : rows) {
			out.add(describeScore(row));
		}
		return out.toString();
	}

	protected static String describeScore(PlayerScore row) {
		return row == null ? "null"
				: "index=" + row.index + " player=" + (row.player == null ? null : row.player.name)
				+ " id=" + (row.player == null ? null : row.player.id) + " points=" + row.points
				+ " games=" + row.gameCount;
	}

	/** Every field an OpenttdServer carries into app/views/openttd/Openttd/index.html. */
	protected static String describeServer(OpenttdServer server) {
		if (server == null) {
			return "null";
		}
		return "name=" + server.name + " revision=" + server.revision + " image=" + server.image
				+ " goal=" + server.goal + " startedDate=" + server.startedDate + " currentDate=" + server.currentDate
				+ " width=" + server.width + " height=" + server.height + " companiesCount=" + server.companiesCount
				+ " clientsCount=" + server.clientsCount;
	}

	/** The whole cause chain, one "class: message" per link, separated by " | ". */
	protected static String causeChain(Throwable thrown) {
		StringBuilder sb = new StringBuilder();
		Throwable current = thrown;
		while (current != null) {
			sb.append(current.getClass().getName() + ": " + current.getMessage() + " | ");
			current = current.getCause();
		}
		return sb.toString();
	}

	/**
	 * The exception the block raises, so a failure branch can be pinned with {@link #causeChain}.
	 * A block that returns normally is reported as a failure.
	 */
	protected static Throwable failure(F.Function0<Object> block) {
		try {
			block.apply();
		} catch (Throwable thrown) {
			return thrown;
		}
		Assert.fail("expected the block to raise, but it returned normally");
		return null;
	}

	/**
	 * The message of the exception the block raises, so a failure branch of a model method can be pinned the
	 * same way a returned value is. The block must fail; a block that returns is reported as a failure.
	 */
	protected static String failureMessage(F.Function0<Object> block) {
		return failure(block).getMessage();
	}

	/**
	 * Runs a block in its own JPA transaction and pins the cause chain of the exception it raises.
	 *
	 * The transaction has to end on the failing statement: Hibernate marks it rollback-only after a failed
	 * flush, and play.db.jpa.JPA.withTransaction() then reports that failure, so any read made after the
	 * caught exception in the same transaction raises too. The rows a refused delete should have removed are
	 * therefore counted in a second transaction, on the committed state.
	 */
	protected static void assertTransactionFailureContains(String needle, F.Function0<Void> block) throws Throwable {
		try {
			inTransactionVoid(block);
		} catch (Throwable thrown) {
			String chain = causeChain(thrown);
			Assert.assertTrue("expected '" + needle + "' in " + chain, chain.contains(needle));
			return;
		}
		Assert.fail("expected a failure containing '" + needle + "', but the call returned normally");
	}

	protected static User user(String email) {
		return User.findByEmail(email);
	}

	protected static Scenario scenario(long id) {
		return Scenario.findById(id);
	}

	/**
	 * The typed lookups below exist because play.db.jpa.GenericModel.findById() is declared
	 * {@code <T extends JPABase> T}: in a call argument position T falls back to its upper bound JPABase, so
	 * Post.findById(1L).topic does not compile. Assigning to a local, or calling one of these, gives the
	 * receiver its real type.
	 */
	protected static Forum forum(long id) {
		return Forum.findById(id);
	}

	protected static Topic topic(long id) {
		return Topic.findById(id);
	}

	protected static Post post(long id) {
		return Post.findById(id);
	}

	protected static Attachment attachment(long id) {
		return Attachment.findById(id);
	}

	protected static Category category(long id) {
		return Category.findById(id);
	}

	protected static UserGroup userGroup(String name) {
		return UserGroup.findByName(name);
	}

	protected static PlayedGame playedGame(long id) {
		return PlayedGame.findById(id);
	}

	protected static Game game(long id) {
		return Game.findById(id);
	}

	/** A played game of one hour, ending at endMillis. PlayedGame.begin and .end are @Required. */
	protected static PlayedGame newGame(Scenario scenario, long endMillis) {
		PlayedGame game = new PlayedGame();
		game.scenario = scenario;
		game.begin = new Date(endMillis - 3600 * 1000L);
		game.end = new Date(endMillis);
		game.create();
		return game;
	}

	protected static PlayedGameScore newScore(PlayedGame game, User user, double presenceRatio, double achievement) {
		PlayedGameScore score = new PlayedGameScore();
		score.playedGame = game;
		score.user = user;
		score.presenceRatio = presenceRatio;
		score.achivement = achievement;
		score.create();
		return score;
	}

	protected static Calendar calendar(long millis) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(millis);
		return calendar;
	}

	/** Days relative to now, so a window that the query has to filter out is unambiguous. */
	protected static long daysAgo(int days) {
		return System.currentTimeMillis() - days * 24 * 3600 * 1000L;
	}
}
