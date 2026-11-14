import org.junit.Assert;
import org.junit.Test;

import play.test.Fixtures;

import models.forum.User;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.PlayerScore;

/**
 * Coverage of models.leaderboard.PlayerScore, the value object the leaderboard templates render.
 *
 * It is not an entity: play.db.jpa.JPABase is not assignable from it, so nothing in the app can save,
 * refresh or delete a PlayerScore. Both PlayedGameScore.getPlayerScores() and
 * PlayedGameScore.getWinner() build one per row of their query, and the templates read index, player,
 * points and gameCount from it.
 *
 *   - The four fields are public and uninitialised, so a blank row answers 0 for index (an int) and null
 *     for player, points and gameCount (Integer and Long, which is what lets a NULL aggregate print as an
 *     empty column in openttd/Scores/show.html).
 *   - There is no toString() override, so the string is the identity of the object: the templates never
 *     print a PlayerScore directly, they read its fields, which is why the identity hash never shows up.
 *   - getPlayerScores() fills every field, numbering the rows from the start of the page.
 *   - getWinner() fills only points and player, leaving index at 0 and gameCount at null, so the winner line
 *     of a topic page never shows a rank or a game count.
 *   - points stays null when the aggregate is NULL: a player whose only score divides by zero has no points.
 */
public class PlayerScoreTest extends BaseModelTest {

	@Test
	public void aBlankScoreHasNoPlayerNoPointsNoGameCountAndIndexZero() throws Throwable {
		PlayerScore blank = new PlayerScore();

		Assert.assertEquals(0, blank.index);
		Assert.assertNull(blank.player);
		Assert.assertNull(blank.points);
		Assert.assertNull(blank.gameCount);
		// The four fields are the whole class: nothing is inherited and nothing is computed.
		Assert.assertEquals("gameCount,index,player,points",
				java.util.Arrays.stream(PlayerScore.class.getFields()).map(f -> f.getName())
						.sorted().reduce((a, b) -> a + "," + b).orElse("(none)"));
	}

	@Test
	public void theScoreIsNotAnEntityAndHasNoToStringOverride() throws Throwable {
		PlayerScore blank = new PlayerScore();

		// A plain class, so play.db.jpa.JPABase.refresh(), .save() and .delete() are not available on it.
		Assert.assertFalse(play.db.jpa.JPABase.class.isAssignableFrom(PlayerScore.class));
		Assert.assertEquals("models.leaderboard.PlayerScore", blank.getClass().getName());

		// Object.toString() is the class name and the identity hash, which no template can print usefully.
		Assert.assertTrue(blank.toString().startsWith("models.leaderboard.PlayerScore@"));
		Assert.assertEquals(Integer.toHexString(System.identityHashCode(blank)),
				blank.toString().substring(blank.toString().indexOf('@') + 1));
	}

	@Test
	public void getPlayerScoresFillsEveryFieldOfTheScore() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("scores-data.yml");
			Fixtures.loadModels("openttd-data.yml");

			PlayedGame game = newGame(scenario(1L), daysAgo(1));
			newScore(game, user("player01@domain.com"), 1.0, 100.0);
			newScore(game, user("player02@domain.com"), 2.0, 100.0);

			java.util.List<PlayerScore> rows = PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(-365)), 1, 10);
			Assert.assertEquals(2, rows.size());

			Assert.assertEquals(1, rows.get(0).index);
			Assert.assertEquals(2, rows.get(1).index);
			// Both players answer 50 points, so the user id ascending decides the order (see dump).
			Assert.assertEquals("Score Player 01", rows.get(0).player.name);
			Assert.assertEquals("Score Player 02", rows.get(1).player.name);
			// 100 / 1.0 = 100 and 100 / 2.0 = 50: the points are what separates them here.
			Assert.assertEquals("100", String.valueOf(rows.get(0).points));
			Assert.assertEquals("50", String.valueOf(rows.get(1).points));
			Assert.assertEquals("1", String.valueOf(rows.get(1).gameCount));

			// The player of a score is the User entity itself, so the template reads its name and uuid.
			Assert.assertEquals(User.class, rows.get(0).player.getClass());
			Assert.assertEquals("player01@domain.com", rows.get(0).player.email);
			return null;
		});
	}

	@Test
	public void getWinnerFillsOnlyThePointsAndThePlayerOfTheScore() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("scores-data.yml");
			Fixtures.loadModels("openttd-data.yml");

			PlayedGame game = newGame(scenario(2L), daysAgo(2));
			newScore(game, user("player03@domain.com"), 0.5, 60.0);
			newScore(game, user("player04@domain.com"), 1.0, 40.0);

			PlayerScore winner = game.getWinner();
			Assert.assertEquals("index=0 player=Score Player 03 id=7 points=120 games=null", describeScore(winner));

			// getWinner() never numbers the row nor counts the games: the query is per game, not per period.
			Assert.assertEquals(0, winner.index);
			Assert.assertNull(winner.gameCount);
			Assert.assertEquals("Score Player 03", winner.player.name);
			return null;
		});
	}

	@Test
	public void pointsStayNullWhenTheQuotientOfTheOnlyScoreIsNotNullable() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("scores-data.yml");
			Fixtures.loadModels("openttd-data.yml");

			// achivement / presenceRatio divides by zero, MariaDB answers NULL, and SUM(NULL) stays NULL,
			// so the row is listed with no points but with its game count.
			PlayedGame game = newGame(scenario(5L), daysAgo(1));
			newScore(game, user("player12@domain.com"), 0.0, 75.0);

			java.util.List<PlayerScore> rows = PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(-365)), 1, 10);
			Assert.assertEquals("[index=1 player=Score Player 12 id=16 points=null games=1]", scoreLines(rows));
			Assert.assertNull(rows.get(0).points);
			// The game still has a winner, because getWinner() reads the quotient of the row itself.
			Assert.assertEquals("index=0 player=Score Player 12 id=16 points=null games=null",
					describeScore(game.getWinner()));
			return null;
		});
	}

	@Test
	public void aScoreOfAGameWithoutRowsIsNeverBuilt() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			PlayedGame empty = newGame(scenario(3L), daysAgo(1));

			// getWinner() answers null rather than an empty PlayerScore, so the template checks for null.
			Assert.assertNull(empty.getWinner());
			Assert.assertEquals("null", describeScore(empty.getWinner()));
			Assert.assertEquals("[]", scoreLines(PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(-365)), 1, 10)));
			return null;
		});
	}
}
