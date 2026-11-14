import org.junit.Assert;
import org.junit.Test;

import models.leaderboard.Game;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.Scenario;
import play.test.Fixtures;

/**
 * Coverage of models.leaderboard.Scenario and its nested enum Id.
 *
 *   - toString() is the name, which openttd/Openttd/index.html and the leaderboard pages print. getImage()
 *     and getTrainImage() switch on Id.fromValue(id), so the image of a row is decided by its id, not by its
 *     name: test/openttd-data.yml inserts the five scenarios in the order HighSpeed, Toyland, Artic, Desertic,
 *     Steam so ids 1..5 line up with the enum.
 *   - The default branch of getImage() ("/images/openttd/openttdLogo.png") is unreachable: Id.fromValue()
 *     answers null for any id outside 1..5, and the switch on a null enum raises a NullPointerException before
 *     the default label is read. getTrainImage() has no default label at all, so the same id raises the same
 *     way. Both are pinned below on a sixth scenario.
 *   - Id.fromValue() maps 1..5 and answers null for 6, 0 and -1; Id.longValue() gives the stored value back.
 *   - delete() never cascades to PlayedGame (the entity has no collection), so a scenario a played game points
 *     at cannot be removed, while one whose games were deleted first can be.
 */
public class ScenarioModelTest extends BaseModelTest {

	@Test
	public void theFiveFixtureScenariosAreNamedInEnumOrder() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");

			Assert.assertEquals("5", String.valueOf(Scenario.count()));
			Assert.assertEquals("[HighSpeed Scenario, Toyland Scenario, Artic Scenario, Desertic Scenario, "
					+ "Steam Scenario]", names(Scenario.findAll()));

			Assert.assertEquals("1", String.valueOf(scenario(1L).id));
			Assert.assertEquals("HighSpeed Scenario", scenario(1L).toString());
			Assert.assertEquals("Toyland Scenario", scenario(2L).toString());
			Assert.assertEquals("Artic Scenario", scenario(3L).toString());
			Assert.assertEquals("Desertic Scenario", scenario(4L).toString());
			Assert.assertEquals("Steam Scenario", scenario(5L).toString());
			return null;
		});
	}

	@Test
	public void imagesFollowTheIdOfTheScenarioNotItsName() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");

			Assert.assertEquals("/images/openttd/highSpeed_on.gif", scenario(1L).getImage());
			Assert.assertEquals("/images/openttd/toyland_on.gif", scenario(2L).getImage());
			Assert.assertEquals("/images/openttd/artic_on.gif", scenario(3L).getImage());
			Assert.assertEquals("/images/openttd/desertic_on.gif", scenario(4L).getImage());
			Assert.assertEquals("/images/openttd/steam_on.gif", scenario(5L).getImage());

			Assert.assertEquals("/images/openttd/high_speed_train.gif", scenario(1L).getTrainImage());
			Assert.assertEquals("/images/openttd/toyland_train.gif", scenario(2L).getTrainImage());
			Assert.assertEquals("/images/openttd/artic_train.gif", scenario(3L).getTrainImage());
			Assert.assertEquals("/images/openttd/desertic_train.gif", scenario(4L).getTrainImage());
			Assert.assertEquals("/images/openttd/steam_train.gif", scenario(5L).getTrainImage());
			return null;
		});
	}

	@Test
	public void aScenarioOutsideTheEnumHasNoImageAndRaisesInsteadOfTakingTheDefaultBranch() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			Game game = Game.findById(1L);

			Scenario extra = new Scenario();
			extra.name = "Extra Scenario";
			extra.game = game;
			extra.create();

			Assert.assertEquals("6", String.valueOf(extra.id));
			Assert.assertEquals("Extra Scenario", extra.toString());
			// Id.fromValue(6) is null, so the switch dereferences null and the openttdLogo.png default is never
			// returned. The message names the enum method, which is what makes the branch unreachable.
			Assert.assertEquals("Cannot invoke \"models.leaderboard.Scenario$Id.ordinal()\" because the return"
					+ " value of \"models.leaderboard.Scenario$Id.fromValue(long)\" is null",
					failureMessage(() -> extra.getImage()));
			Assert.assertEquals("Cannot invoke \"models.leaderboard.Scenario$Id.ordinal()\" because the return"
					+ " value of \"models.leaderboard.Scenario$Id.fromValue(long)\" is null",
					failureMessage(() -> extra.getTrainImage()));
			return null;
		});
	}

	@Test
	public void idEnumMapsTheStoredValuesAndAnswersNullOutOfRange() throws Throwable {
		Assert.assertEquals("HighSpeed", String.valueOf(Scenario.Id.fromValue(1)));
		Assert.assertEquals("Toyland", String.valueOf(Scenario.Id.fromValue(2)));
		Assert.assertEquals("Artic", String.valueOf(Scenario.Id.fromValue(3)));
		Assert.assertEquals("Desertic", String.valueOf(Scenario.Id.fromValue(4)));
		Assert.assertEquals("Steam", String.valueOf(Scenario.Id.fromValue(5)));
		Assert.assertNull(Scenario.Id.fromValue(6));
		Assert.assertNull(Scenario.Id.fromValue(0));
		Assert.assertNull(Scenario.Id.fromValue(-1));

		Assert.assertEquals("1", String.valueOf(Scenario.Id.HighSpeed.longValue()));
		Assert.assertEquals("2", String.valueOf(Scenario.Id.Toyland.longValue()));
		Assert.assertEquals("3", String.valueOf(Scenario.Id.Artic.longValue()));
		Assert.assertEquals("4", String.valueOf(Scenario.Id.Desertic.longValue()));
		Assert.assertEquals("5", String.valueOf(Scenario.Id.Steam.longValue()));

		// fromValue is the inverse of longValue for every constant, in the order the enum declares them.
		Assert.assertEquals("[HighSpeed, Toyland, Artic, Desertic, Steam]", java.util.Arrays.toString(Scenario.Id.values()));
		Assert.assertEquals(5, Scenario.Id.values().length);
	}

	@Test
	public void deleteOfAScenarioAPlayedGamePointsAtIsRefusedByThePlayedGameRelation() throws Throwable {
		// PlayedGame.scenario is a @ManyToOne with no cascade, so MySQL refuses the DELETE.
		assertTransactionFailureContains("a foreign key constraint fails (`www`.`PlayedGame`", () -> {
			Fixtures.loadModels("openttd-data.yml");
			Fixtures.loadModels("scores-data.yml");
			PlayedGame game = newGame(scenario(1L), daysAgo(1));
			newScore(game, user("player01@domain.com"), 1.0, 10.0);

			Assert.assertEquals("1", String.valueOf(PlayedGame.count()));
			Assert.assertEquals("1", String.valueOf(PlayedGameScore.count()));

			scenario(1L).delete();
			return null;
		});
	}

	@Test
	public void theScenarioAndItsGameSurviveTheRefusedDelete() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");

			Assert.assertEquals("5", String.valueOf(Scenario.count()));
			Assert.assertEquals("0", String.valueOf(PlayedGame.count()));
			Assert.assertEquals("HighSpeed Scenario", scenario(1L).toString());
			Assert.assertEquals("/images/openttd/highSpeed_on.gif", scenario(1L).getImage());
			return null;
		});
	}

	@Test
	public void deleteOfAScenarioNothingPointsAtRemovesItAndKeepsTheIdsOfTheOthers() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");

			Assert.assertEquals("5", String.valueOf(Scenario.count()));
			Assert.assertEquals("0", String.valueOf(PlayedGame.count()));

			Scenario highSpeed = scenario(1L);
			highSpeed.delete();

			// The remaining rows keep their ids, so the image of id 2 is still the Toyland one.
			Assert.assertEquals("4", String.valueOf(Scenario.count()));
			Assert.assertEquals("[Toyland Scenario, Artic Scenario, Desertic Scenario, Steam Scenario]",
					names(Scenario.findAll()));
			Assert.assertNull(scenario(1L));
			Assert.assertEquals("/images/openttd/toyland_on.gif", scenario(2L).getImage());
			Assert.assertEquals("/images/openttd/toyland_train.gif", scenario(2L).getTrainImage());
			Assert.assertEquals("1", String.valueOf(Game.count()));
			return null;
		});
	}

	@Test
	public void aScenarioMadeInJavaIsUsableOnceItsGamesAreGone() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			Scenario toyland = scenario(2L);
			PlayedGame game = newGame(toyland, daysAgo(1));
			newScore(game, user("player01@domain.com"), 1.0, 10.0);

			PlayedGame loaded = (PlayedGame) game.refresh();
			// PlayedGame cascades to its scores, so the game and its score row go together and the scenario
			// becomes removable afterwards.
			loaded.delete();

			Assert.assertEquals("0", String.valueOf(PlayedGame.count()));
			Assert.assertEquals("0", String.valueOf(PlayedGameScore.count()));

			toyland.delete();

			Assert.assertEquals("4", String.valueOf(Scenario.count()));
			Assert.assertNull(scenario(2L));
			return null;
		});
	}
}
