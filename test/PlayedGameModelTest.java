import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import play.test.Fixtures;

import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.PlayerScore;
import models.leaderboard.Scenario;

/**
 * Coverage of models.leaderboard.PlayedGame.
 *
 *   - begin and end are @Required dates stamped by the caller (openttd/Openttd and the robot write them), and
 *     scores is initialised to an empty list, unlike the forum entities whose collections stay null.
 *   - play.db.jpa.JPABase.toString() answers "PlayedGame[id]"; openttd/Openttd/index.html prints the scenario
 *     name beside it, so the game itself never renders a string of its own.
 *   - getLastPlayedGames() orders by end descending and windows 10 rows.
 *   - getWinner() delegates to PlayedGameScore.getWinner() and answers null for a game without any score.
 *   - getBestPlayedGamesForScenarioId() declares List<PlayedGame> but selects three columns, so Hibernate hands
 *     back Object[] rows: the first element is the game, the second the best achievement of that player, the
 *     third the player. A cast to PlayedGame raises ClassCastException, and a scenario with no score raises
 *     IndexOutOfBoundsException on the first row. openttd/Openttd/index.html never reads that method, which is
 *     why the wrong return type has never surfaced.
 *   - delete() cascades to PlayedGameScore (cascade = ALL) only once the collection is loaded; a game whose
 *     scores were never read cannot be removed, MySQL refuses it through PlayedGameScore.playedGame_id.
 */
public class PlayedGameModelTest extends BaseModelTest {

	/** Seeds the two games and five scores of the leaderboard dump. */
	private static void seedGames() throws Throwable {
		Fixtures.loadModels("scores-data.yml");
		Fixtures.loadModels("openttd-data.yml");

		PlayedGame first = newGame(scenario(1L), daysAgo(3));
		PlayedGame second = newGame(scenario(2L), daysAgo(1));
		newScore(first, user("player01@domain.com"), 1.0, 100.0);
		newScore(first, user("player02@domain.com"), 0.5, 40.0);
		newScore(second, user("player01@domain.com"), 0.5, 90.0);
		newScore(second, user("player02@domain.com"), 1.0, 10.0);
		newScore(second, user("player03@domain.com"), 0.25, 30.0);
	}

	@Test
	public void aGameOfItsOwnCarriesItsScenarioItsTwoDatesAndAnEmptyScoreList() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			PlayedGame fresh = newGame(scenario(1L), System.currentTimeMillis());

			Assert.assertEquals("1", String.valueOf(fresh.id));
			Assert.assertEquals("HighSpeed Scenario", fresh.scenario.toString());
			Assert.assertEquals("1", String.valueOf(fresh.scenario.id));
			Assert.assertNotNull(fresh.begin);
			Assert.assertNotNull(fresh.end);
			// newGame() ends the game now and begins it an hour earlier.
			Assert.assertTrue(fresh.begin.getTime() < fresh.end.getTime());
			Assert.assertEquals(3600 * 1000L, fresh.end.getTime() - fresh.begin.getTime());
			Assert.assertEquals("PlayedGame[1]", fresh.toString());
			Assert.assertEquals("0", String.valueOf(fresh.scores.size()));
			Assert.assertEquals("1", String.valueOf(PlayedGame.count()));
			Assert.assertEquals("0", String.valueOf(PlayedGameScore.count()));
			// Hibernate replaces the ArrayList the entity declares with its own bag once the row is persisted.
			Assert.assertEquals("org.hibernate.collection.internal.PersistentBag", fresh.scores.getClass().getName());
			return null;
		});
	}

	@Test
	public void theScoresCollectionIsEmptyUntilTheEntityIsReadBack() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("scores-data.yml");
			Fixtures.loadModels("openttd-data.yml");
			PlayedGame game = newGame(scenario(1L), daysAgo(1));
			newScore(game, user("player01@domain.com"), 1.0, 10.0);

			Assert.assertEquals("1", String.valueOf(PlayedGameScore.count()));
			// The scores of the row the caller created are not fetched by the entity.
			Assert.assertEquals(0, game.scores.size());
			PlayedGame loaded = (PlayedGame) game.refresh();
			Assert.assertEquals(1, loaded.scores.size());
			Assert.assertEquals("[PlayedGameScore[1]]", names(loaded.scores));
			return null;
		});
	}

	@Test
	public void lastPlayedGamesAreOrderedByTheirEndDate() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("scores-data.yml");
			Fixtures.loadModels("openttd-data.yml");

			PlayedGame oldest = newGame(scenario(1L), daysAgo(5));
			PlayedGame middle = newGame(scenario(2L), daysAgo(2));
			PlayedGame newest = newGame(scenario(1L), daysAgo(1));

			// Ordered by end descending: the newest game of the three comes back first.
			Assert.assertEquals("[3:HighSpeed Scenario scores=0, 2:Toyland Scenario scores=0, "
					+ "1:HighSpeed Scenario scores=0]", gameLines(PlayedGame.getLastPlayedGames()));
			// The same rows as ids, which pins the order without the dates of the moment of creation.
			Assert.assertEquals("[3, 2, 1]", gameIds(PlayedGame.getLastPlayedGames()));
			Assert.assertEquals("HighSpeed Scenario", ((PlayedGame) PlayedGame.getLastPlayedGames().get(0)).scenario.name);
			Assert.assertEquals("Toyland Scenario", ((PlayedGame) PlayedGame.getLastPlayedGames().get(1)).scenario.name);
			Assert.assertEquals("HighSpeed Scenario", ((PlayedGame) PlayedGame.getLastPlayedGames().get(2)).scenario.name);
			Assert.assertEquals(3, PlayedGame.getLastPlayedGames().size());
			// Three games is below the 10-row window, so none is dropped.
			Assert.assertEquals(3, PlayedGame.count());
			Assert.assertEquals(newest.id, PlayedGame.getLastPlayedGames().get(0).id);
			Assert.assertEquals(oldest.id, PlayedGame.getLastPlayedGames().get(2).id);
			Assert.assertEquals(middle.id, PlayedGame.getLastPlayedGames().get(1).id);
			return null;
		});
	}

	@Test
	public void winnerIsNullForAGameWithoutAnyScore() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("scores-data.yml");
			Fixtures.loadModels("openttd-data.yml");
			PlayedGame empty = newGame(scenario(3L), daysAgo(1));

			// getWinner() answers null, and describeScore() renders that null as the string "null".
			Assert.assertNull(empty.getWinner());
			Assert.assertEquals("null", describeScore(empty.getWinner()));
			return null;
		});
	}

	@Test
	public void winnerIsThePlayerWithTheBestAchievementPerPresence() throws Throwable {
		inTransactionVoid(() -> {
			seedGames();
			PlayedGame first = playedGame(1L);
			PlayedGame second = playedGame(2L);

			Assert.assertEquals("index=0 player=Score Player 01 id=5 points=100 games=null", describeScore(first.getWinner()));
			Assert.assertEquals("index=0 player=Score Player 01 id=5 points=180 games=null", describeScore(second.getWinner()));
			return null;
		});
	}

	@Test
	public void bestGamesForAScenarioAreRowsOfThreeColumnsNotPlayedGames() throws Throwable {
		inTransactionVoid(() -> {
			seedGames();

			List<?> high = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.HighSpeed);
			Assert.assertEquals(2, high.size());
			// The player with the best achievement of game 1 comes first, then the second player.
			Assert.assertEquals("[PlayedGame[1], 100.0, Score Player 01]", Arrays.toString((Object[]) high.get(0)));
			Assert.assertEquals("[PlayedGame[1], 40.0, Score Player 02]", Arrays.toString((Object[]) high.get(1)));
			Assert.assertEquals("[Ljava.lang.Object;", high.get(0).getClass().getName());

			List<?> toyland = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.Toyland);
			Assert.assertEquals("[PlayedGame[2], 90.0, Score Player 01]", Arrays.toString((Object[]) toyland.get(0)));
			Assert.assertEquals("[PlayedGame[2], 30.0, Score Player 03]", Arrays.toString((Object[]) toyland.get(1)));
			Assert.assertEquals("[PlayedGame[2], 10.0, Score Player 02]", Arrays.toString((Object[]) toyland.get(2)));

			// The declared element type is a lie, so the cast the signature invites fails on the first row.
			Assert.assertTrue(failureMessage(() -> (PlayedGame) (Object) high.get(0))
					.contains("cannot be cast to class models.leaderboard.PlayedGame"));
			return null;
		});
	}

	@Test
	public void bestGamesOfAScenarioNobodyPlayedAnswerNoRows() throws Throwable {
		inTransactionVoid(() -> {
			seedGames();

			List<?> steam = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.Steam);
			Assert.assertEquals("[]", names(steam));
			Assert.assertEquals(0, steam.size());
			Assert.assertEquals("Index 0 out of bounds for length 0", failureMessage(() -> steam.get(0)));
			return null;
		});
	}

	@Test
	public void deleteOfAGameWhoseScoresWereNeverLoadedIsRefusedByTheScoreRelation() throws Throwable {
		assertTransactionFailureContains("a foreign key constraint fails (`www`.`PlayedGameScore`", () -> {
			Fixtures.loadModels("scores-data.yml");
			Fixtures.loadModels("openttd-data.yml");
			PlayedGame game = newGame(scenario(1L), daysAgo(1));
			newScore(game, user("player01@domain.com"), 1.0, 10.0);

			// cascade = CascadeType.ALL only reaches the rows of the collection, which is still empty here.
			Assert.assertEquals(0, game.scores.size());
			game.delete();
			return null;
		});
	}

	@Test
	public void deleteOfAGameWithLoadedScoresRemovesThemWithIt() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("scores-data.yml");
			Fixtures.loadModels("openttd-data.yml");
			PlayedGame game = newGame(scenario(1L), daysAgo(1));
			newScore(game, user("player01@domain.com"), 1.0, 10.0);
			newScore(game, user("player02@domain.com"), 1.0, 20.0);

			PlayedGame loaded = (PlayedGame) game.refresh();
			Assert.assertEquals(2, loaded.scores.size());

			loaded.delete();

			Assert.assertEquals("0", String.valueOf(PlayedGame.count()));
			Assert.assertEquals("0", String.valueOf(PlayedGameScore.count()));
			Assert.assertNull(playedGame(1L));
			// The scenario and the players survive their game.
			Assert.assertEquals("5", String.valueOf(Scenario.count()));
			// test/data.yml (4 accounts) plus test/scores-data.yml (12 players).
			Assert.assertEquals("16", String.valueOf(models.forum.User.count()));
			return null;
		});
	}

	@Test
	public void aGameWithoutScoresCanBeRemovedWithoutBeingReadBack() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			PlayedGame game = newGame(scenario(4L), daysAgo(2));

			Assert.assertEquals(0, game.scores.size());
			game.delete();

			Assert.assertEquals("0", String.valueOf(PlayedGame.count()));
			Assert.assertNull(playedGame(1L));
			Assert.assertEquals("5", String.valueOf(Scenario.count()));
			return null;
		});
	}
}
