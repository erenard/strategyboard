import org.junit.Assert;
import org.junit.Test;

import play.test.Fixtures;

import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.Scenario;
import models.forum.User;

/**
 * Coverage of models.leaderboard.PlayedGameScore, the row the leaderboard pages aggregate.
 *
 *   - The entity declares one field besides the relations: presenceRatio and achivement, both primitive
 *     double, so a blank row answers 0.0 for them and never null. play.db.jpa.JPABase.toString() gives
 *     "PlayedGameScore[id]", and an unsaved row prints "PlayedGameScore[]".
 *   - @Required on playedGame and user is a validation annotation, not a schema constraint: a score without
 *     a user is persisted, so the leaderboard counts a row no player owns (pinned below).
 *   - getWinner() divides achivement by presenceRatio and casts the quotient to int, so a player is ranked
 *     by points per unit of presence, truncated. A row whose presenceRatio is 0 divides by zero, which
 *     MariaDB answers NULL: SUM() ignores it and ORDER BY 1 DESC puts it last, so such a row neither adds
 *     points nor wins.
 *   - getPlayerScores() groups by player over the games whose end falls inside [from, to): points are the
 *     sum of achivement / presenceRatio, gameCount the number of score rows, and index the position in the
 *     ordered list, offset by the page. The order by is "1 desc, 3 desc, 2 asc", so equal points break on
 *     the number of games and then on the user id ascending.
 *   - count(from, to) counts DISTINCT players, not score rows: a player with two rows of one period is
 *     reported once here and twice in the Games column of the same period.
 *   - getLastMonthPlayerScores() ignores its page and pageSize and always asks for page 1 of 10 of the last
 *     month, which is why the sidebar of openttd/Scores/show.html never paginates.
 *   - delete() removes a score without touching its game or its player, and a game left without any score
 *     answers a null winner.
 */
public class PlayedGameScoreModelTest extends BaseModelTest {

	/**
	 * The two games and five scores of the leaderboard dump: game 1 ended three days ago, game 2 one day
	 * ago, and the ratios are chosen so that the sums, the truncation and the tie break are all visible.
	 */
	private static void seedScores() throws Throwable {
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
	public void aBlankScoreCarriesZeroRatiosAndNoRelation() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			PlayedGameScore blank = new PlayedGameScore();

			Assert.assertNull(blank.id);
			// presenceRatio and achivement are primitive double, so they are never null.
			Assert.assertEquals("0.0", String.valueOf(blank.presenceRatio));
			Assert.assertEquals("0.0", String.valueOf(blank.achivement));
			Assert.assertNull(blank.playedGame);
			Assert.assertNull(blank.user);
			// JPABase.toString() of an unsaved row prints the id of nothing.
			Assert.assertEquals("PlayedGameScore[]", blank.toString());
			Assert.assertEquals("achivement,id,playedGame,presenceRatio,user,willBeSaved",
					java.util.Arrays.stream(PlayedGameScore.class.getFields()).map(f -> f.getName())
							.sorted().reduce((a, b) -> a + "," + b).orElse("(none)"));
			Assert.assertEquals("0", String.valueOf(PlayedGameScore.count()));
			return null;
		});
	}

	@Test
	public void aScoreWithoutAPlayerIsStillStored() throws Throwable {
		// @Required is a validation annotation the controller binding applies; JPA never checks it, so a
		// row with no user reaches the database and joins the leaderboard as a player of null.
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			PlayedGame game = newGame(scenario(1L), daysAgo(1));

			PlayedGameScore noUser = new PlayedGameScore();
			noUser.playedGame = game;
			noUser.presenceRatio = 1.0;
			noUser.achivement = 42.0;
			noUser.create();

			Assert.assertEquals("1", String.valueOf(noUser.id));
			Assert.assertNull(noUser.user);
			Assert.assertEquals("1", String.valueOf(PlayedGameScore.count()));
			Assert.assertEquals("[PlayedGameScore[1]]", names(PlayedGameScore.findAll()));
			// PlayedGame.scores is mapped by the playedGame_id column, so the row joins the game of its own.
			Assert.assertEquals("1", String.valueOf(((PlayedGame) game.refresh()).scores.size()));
			return null;
		});
	}

	@Test
	public void theRatiosOfTheRowsAreStoredExactlyAsTheyWereGiven() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			PlayedGame game = newGame(scenario(3L), daysAgo(2));

			PlayedGameScore score = newScore(game, user("admin@domain.com"), 0.25, 30.0);

			Assert.assertEquals("1", String.valueOf(score.id));
			Assert.assertEquals("PlayedGameScore[1]", score.toString());
			Assert.assertEquals("0.25", String.valueOf(score.presenceRatio));
			Assert.assertEquals("30.0", String.valueOf(score.achivement));
			Assert.assertEquals("Artic Scenario", String.valueOf(score.playedGame.scenario));
			Assert.assertEquals("Admin", score.user.name);

			// The collection of the game stays empty until the entity is read back, like PlayedGameModelTest.
			Assert.assertEquals(0, game.scores.size());
			Assert.assertEquals("[PlayedGameScore[1]]", names(((PlayedGame) game.refresh()).scores));
			return null;
		});
	}

	@Test
	public void winnerOfAGameIsTheBestAchievementPerUnitOfPresence() throws Throwable {
		inTransactionVoid(() -> {
			seedScores();
			PlayedGame first = playedGame(1L);

			// Game 1: Score Player 01 answers 100 / 1.0 = 100, Score Player 02 answers 40 / 0.5 = 80.
			Assert.assertEquals("index=0 player=Score Player 01 id=5 points=100 games=null",
					describeScore(first.getWinner()));
			// Game 2: 90 / 0.5 = 180 beats 30 / 0.25 = 120, which beats 10 / 1.0 = 10.
			Assert.assertEquals("index=0 player=Score Player 01 id=5 points=180 games=null",
					describeScore(playedGame(2L).getWinner()));
			// getWinner() never fills index or gameCount: only points and player are read from the row.
			Assert.assertNull(first.getWinner().gameCount);
			Assert.assertEquals(0, first.getWinner().index);
			return null;
		});
	}

	@Test
	public void winnerIgnoresARowWhosePresenceRatioIsZero() throws Throwable {
		inTransactionVoid(() -> {
			seedScores();
			PlayedGame first = playedGame(1L);

			// 0.0 / 0.0 divides by zero, which MariaDB answers NULL: the row neither adds points, since
			// SUM() skips NULL, nor wins, since ORDER BY 1 DESC puts NULL last.
			newScore(first, user("player03@domain.com"), 0.0, 0.0);

			Assert.assertEquals("3", String.valueOf(((PlayedGame) first.refresh()).scores.size()));
			Assert.assertEquals("index=0 player=Score Player 01 id=5 points=100 games=null",
					describeScore(first.getWinner()));
			Assert.assertEquals("[index=1 player=Score Player 01 id=5 points=280 games=2, "
					+ "index=2 player=Score Player 03 id=7 points=120 games=2, "
					+ "index=3 player=Score Player 02 id=6 points=90 games=2]",
					scoreLines(PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(-365)), 1, 10)));

			// Ending the window at the end of the first game keeps only the row that divides by zero, so the
			// sum of that player is NULL: MariaDB still lists the player, with null points, sorted last.
			Assert.assertEquals("[index=1 player=Score Player 01 id=5 points=100 games=1, "
					+ "index=2 player=Score Player 02 id=6 points=80 games=1, "
					+ "index=3 player=Score Player 03 id=7 points=null games=1]",
					scoreLines(PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(2)), 1, 10)));
			// count() counts players, so the player of null points is counted here.
			Assert.assertEquals("3", String.valueOf(PlayedGameScore.count(calendar(0L), calendar(daysAgo(2)))));
			return null;
		});
	}

	@Test
	public void playerScoresSumTheRatiosOfEveryGameOfThePlayerInTheWindow() throws Throwable {
		inTransactionVoid(() -> {
			seedScores();

			Assert.assertEquals("[index=1 player=Score Player 01 id=5 points=280 games=2, "
					+ "index=2 player=Score Player 03 id=7 points=120 games=1, "
					+ "index=3 player=Score Player 02 id=6 points=90 games=2]",
					scoreLines(PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(-365)), 1, 10)));

			// Upper bound exclusive: a window ending two days ago keeps game 1 only, so 100 and 80.
			Assert.assertEquals("[index=1 player=Score Player 01 id=5 points=100 games=1, "
					+ "index=2 player=Score Player 02 id=6 points=80 games=1]",
					scoreLines(PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(2)), 1, 10)));

			// A window ending before the oldest game answers nothing at all.
			Assert.assertEquals("[]", scoreLines(PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(4)), 1, 10)));
			Assert.assertEquals("0", String.valueOf(PlayedGameScore.count(calendar(0L), calendar(daysAgo(4)))));

			// null from becomes the epoch, null to becomes now: every game of the fixtures is inside it.
			Assert.assertEquals("[index=1 player=Score Player 01 id=5 points=280 games=2, "
					+ "index=2 player=Score Player 03 id=7 points=120 games=1, "
					+ "index=3 player=Score Player 02 id=6 points=90 games=2]",
					scoreLines(PlayedGameScore.getPlayerScores(null, null, null, null)));
			return null;
		});
	}

	@Test
	public void playerScoresPageByOffsetAndNumberTheIndexFromThePageStart() throws Throwable {
		inTransactionVoid(() -> {
			seedScores();

			Assert.assertEquals("[index=1 player=Score Player 01 id=5 points=280 games=2, "
					+ "index=2 player=Score Player 03 id=7 points=120 games=1]",
					scoreLines(PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(-365)), 1, 2)));
			// The index of page 2 continues where page 1 stopped, because it is 1 + (page - 1) * pageSize.
			Assert.assertEquals("[index=3 player=Score Player 02 id=6 points=90 games=2]",
					scoreLines(PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(-365)), 2, 2)));
			Assert.assertEquals("[]", scoreLines(PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(-365)), 3, 2)));
			return null;
		});
	}

	@Test
	public void equalPointsBreakOnTheGameCountThenOnTheUserId() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("scores-data.yml");
			Fixtures.loadModels("openttd-data.yml");

			// Three players of one game, all answering 50 points, so the order by 1 desc cannot separate them
			// and the user ids decide: Score Player 04 = 8, 05 = 9, 06 = 10.
			PlayedGame game = newGame(scenario(4L), daysAgo(1));
			newScore(game, user("player04@domain.com"), 1.0, 50.0);
			newScore(game, user("player05@domain.com"), 2.0, 100.0);
			newScore(game, user("player06@domain.com"), 1.0, 50.0);

			Assert.assertEquals("[index=1 player=Score Player 04 id=8 points=50 games=1, "
					+ "index=2 player=Score Player 05 id=9 points=50 games=1, "
					+ "index=3 player=Score Player 06 id=10 points=50 games=1]",
					scoreLines(PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(-365)), 1, 10)));

			// getWinner() orders by the same single column, so its answer is the first user id too.
			Assert.assertEquals("index=0 player=Score Player 04 id=8 points=50 games=null",
					describeScore(game.getWinner()));
			return null;
		});
	}

	@Test
	public void countOfAPeriodCountsThePlayersNotTheScoreRows() throws Throwable {
		inTransactionVoid(() -> {
			seedScores();

			// Five rows over two games, three distinct players: nbScores of the leaderboard is this number.
			Assert.assertEquals("3", String.valueOf(PlayedGameScore.count(calendar(0L), calendar(daysAgo(-365)))));
			Assert.assertEquals("5", String.valueOf(PlayedGameScore.count()));
			Assert.assertEquals("2", String.valueOf(PlayedGameScore.count(calendar(0L), calendar(daysAgo(2)))));
			Assert.assertEquals("0", String.valueOf(PlayedGameScore.count(calendar(daysAgo(365)), calendar(daysAgo(364)))));
			return null;
		});
	}

	@Test
	public void lastMonthPlayerScoresIgnoreTheirPageAndPageSize() throws Throwable {
		inTransactionVoid(() -> {
			seedScores();

			// The signature invites paging, but the method calls getPlayerScores(from, to, 1, 10): the
			// arguments are dropped, so a window of one row on page 2 still answers every player.
			Assert.assertEquals("[index=1 player=Score Player 01 id=5 points=280 games=2, "
					+ "index=2 player=Score Player 03 id=7 points=120 games=1, "
					+ "index=3 player=Score Player 02 id=6 points=90 games=2]",
					scoreLines(PlayedGameScore.getLastMonthPlayerScores(2, 1)));
			Assert.assertEquals(scoreLines(PlayedGameScore.getPlayerScores(calendar(0L), calendar(daysAgo(-365)), 1, 10)),
					scoreLines(PlayedGameScore.getLastMonthPlayerScores(null, null)));
			return null;
		});
	}

	@Test
	public void deleteRemovesTheRowAndLeavesItsGameAndPlayer() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			PlayedGame game = newGame(scenario(2L), daysAgo(1));
			PlayedGameScore score = newScore(game, user("admin@domain.com"), 1.0, 50.0);

			Assert.assertEquals("1", String.valueOf(PlayedGameScore.count()));
			Assert.assertEquals("1", String.valueOf(PlayedGame.count()));

			score.delete();

			// The game keeps its row, with an empty collection, and its winner becomes null.
			Assert.assertEquals("0", String.valueOf(PlayedGameScore.count()));
			Assert.assertEquals("1", String.valueOf(PlayedGame.count()));
			Assert.assertEquals("5", String.valueOf(Scenario.count()));
			Assert.assertEquals("4", String.valueOf(User.count()));
			Assert.assertEquals(0, ((PlayedGame) game.refresh()).scores.size());
			Assert.assertNull(game.getWinner());
			return null;
		});
	}

	@Test
	public void deleteOfAGameTakesItsScoresWithIt() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			PlayedGame game = newGame(scenario(1L), daysAgo(1));
			newScore(game, user("admin@domain.com"), 1.0, 50.0);
			newScore(game, user("bob@domain.com"), 1.0, 20.0);

			PlayedGame loaded = (PlayedGame) game.refresh();
			Assert.assertEquals(2, loaded.scores.size());

			// cascade = CascadeType.ALL on PlayedGame.scores.
			loaded.delete();

			Assert.assertEquals("0", String.valueOf(PlayedGame.count()));
			Assert.assertEquals("0", String.valueOf(PlayedGameScore.count()));
			Assert.assertEquals("4", String.valueOf(User.count()));
			return null;
		});
	}
}
