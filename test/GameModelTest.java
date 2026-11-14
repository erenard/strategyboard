import org.junit.Assert;
import org.junit.Test;

import models.leaderboard.Game;
import models.leaderboard.Scenario;
import play.test.Fixtures;

/**
 * Coverage of models.leaderboard.Game, the row a set of scenarios belongs to.
 *
 *   - Game declares one field, name, and no toString() override, so play.db.jpa.JPABase.toString() answers
 *     "Game[id]". conf/initial-data.yml and test/openttd-data.yml create one game, "OpenTTD goal games".
 *   - There is no constructor: the CRUD form of admin/Games sets name on a blank row.
 *   - delete() never cascades to Scenario (the entity has no @OneToMany at all, its scenarios are only the
 *     inverse side of Scenario.game), so a game that scenarios point at cannot be removed: MySQL refuses the
 *     DELETE through Scenario.game_id.
 */
public class GameModelTest extends BaseModelTest {

	@Test
	public void theFixtureGameIsTheOnlyRowAndHasNoToStringOverride() throws Throwable {
		inTransactionVoid(() -> {
			// test/data.yml has no Game row: the leaderboard fixtures bring the one game.
			Fixtures.loadModels("openttd-data.yml");
			Game game = game(1L);

			Assert.assertEquals("1", String.valueOf(game.id));
			Assert.assertEquals("OpenTTD goal games", game.name);
			Assert.assertEquals("Game[1]", game.toString());
			Assert.assertEquals("1", String.valueOf(Game.count()));
			Assert.assertEquals("[Game[1]]", names(Game.findAll()));
			// name is the only field the entity declares besides the inherited id and willBeSaved.
			Assert.assertEquals("id,name,willBeSaved", java.util.Arrays.stream(Game.class.getFields())
					.map(f -> f.getName()).sorted().reduce((a, b) -> a + "," + b).orElse("(none)"));
			return null;
		});
	}

	@Test
	public void scenariosOfTheFixturePointAtTheOneGame() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			Game game = game(1L);
			Assert.assertEquals("5", String.valueOf(Scenario.count()));
			for (long id = 1; id <= 5; id++) {
				Assert.assertEquals("Game[1]", scenario(id).game.toString());
				Assert.assertEquals("1", String.valueOf(scenario(id).game.id));
			}
			Assert.assertEquals(game, scenario(1L).game);
			return null;
		});
	}

	@Test
	public void aBlankGameBecomesARowOfItsOwnAndCanBeRemoved() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			Game fresh = new Game();
			fresh.name = "Fresh Game";
			fresh.create();

			Assert.assertEquals("2", String.valueOf(fresh.id));
			Assert.assertEquals("Fresh Game", fresh.name);
			Assert.assertEquals("Game[2]", fresh.toString());
			Assert.assertEquals("2", String.valueOf(Game.count()));
			Assert.assertEquals("[Game[1], Game[2]]", names(Game.findAll()));

			// Nothing points at this game, so the DELETE is allowed.
			fresh.delete();

			Assert.assertEquals("1", String.valueOf(Game.count()));
			Assert.assertNull(game(2L));
			Assert.assertEquals("OpenTTD goal games", game(1L).name);
			return null;
		});
	}

	@Test
	public void deleteOfTheGameTheScenariosPointAtIsRefusedByTheScenarioRelation() throws Throwable {
		// Scenario.game is a @ManyToOne with no cascade, so the DELETE of game 1 is refused.
		assertTransactionFailureContains("a foreign key constraint fails (`www`.`Scenario`", () -> {
			Fixtures.loadModels("openttd-data.yml");
			Game game = game(1L);
			Assert.assertEquals("5", String.valueOf(Scenario.count()));
			game.delete();
			return null;
		});
	}

	@Test
	public void theGameAndItsFiveScenariosSurviveTheRefusedDelete() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			Assert.assertEquals("1", String.valueOf(Game.count()));
			Assert.assertEquals("5", String.valueOf(Scenario.count()));
			Assert.assertEquals("OpenTTD goal games", game(1L).name);
			Assert.assertEquals("[HighSpeed Scenario, Toyland Scenario, Artic Scenario, Desertic Scenario, "
					+ "Steam Scenario]", names(Scenario.findAll()));
			return null;
		});
	}

	@Test
	public void aGameWithoutAnyScenarioCanBeRemovedEvenAfterScenariosWereDeleted() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			Game game = game(1L);
			// The scenarios have to go first, which is what admin/Games cannot do on its own.
			Scenario.deleteAll();
			game.delete();

			Assert.assertEquals("0", String.valueOf(Game.count()));
			Assert.assertEquals("0", String.valueOf(Scenario.count()));
			return null;
		});
	}
}
