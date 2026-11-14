import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

import com.openttd.network.admin.GameInfo;
import com.openttd.network.core.Configuration;
import com.openttd.robot.model.GamePlayer;

import models.forum.User;
import models.leaderboard.PlayedGame;

import openttd.OpenttdService;

/**
 * Coverage of app/openttd/OpenttdService, the ExternalUserService and ExternalGameService the robot
 * director calls when a game ends (app/openttd/OpenttdAdminHandler wires it into
 * com.openttd.robot.ExternalServices).
 *
 *   - identifyUser() looks a models.forum.User up by its uuid, the login token
 *     controllers.Profile.onAuthenticated() gives a connected user and Profile.onDisconnect() takes away
 *     again. The four users test/data.yml seeds carry no uuid, so the service answers null for every
 *     token until a token is written - and it answers null, never an exception, for the empty token and
 *     for null.
 *   - saveGame() writes one models.leaderboard.PlayedGame and one PlayedGameScore per player whose
 *     ExternalUser carries an existing user id. Players with id 0 (Logger.error("userId null")), an id no
 *     row holds (Logger.error("user null")), or no ExternalUser at all are dropped.
 *   - A scenario id the fixtures do not hold answers null from Scenario.findById(), and PlayedGame.scenario
 *     is a plain @ManyToOne with no column constraint, so the game is written with a null scenario.
 *   - begin comes from gameStartingDateByScenario when the scenario has one (endGame() puts the moment it
 *     restarted the robot), otherwise from the clock. MariaDB stores DATETIME to the second, so a date
 *     built in Java reads back with its millis rounded down to the second.
 *   - Two failures escape or are swallowed, and both are pinned below: a GamePlayer without an
 *     ExternalUser makes the Logger.info() line of the method raise (GamePlayer.toString() dereferences
 *     externalUser) before the transaction is opened, so nothing is written at all; a null collection
 *     raises inside the transaction and the catch(Exception) of the method swallows it, so the game the
 *     loop never reached is not written either.
 *   - endGame() restarts, shuts down and starts the robot of the scenario through
 *     OpenttdAdminHandler, and its startup() replaces whatever robot the map held with a real
 *     com.openttd.admin.OpenttdAdmin, which opens an admin socket. The suites therefore inject a fake,
 *     let endGame() replace it, and remove the entry again - see BaseOpenttdTest.
 *   - exposeGame() and getGame() share one HashMap per service instance, and answer the GameInfo itself,
 *     so a second OpenttdService knows nothing about what the wired instance exposed.
 */
public class OpenttdServiceTest extends BaseOpenttdTest {

	// ~~~~~~~~~~~~ identifyUser

	@Test
	public void nobodyIsIdentifiedWhileTheFixturesCarryNoLoginToken() throws Throwable {
		loadScenarioFixtures();
		OpenttdService service = new OpenttdService();

		// test/data.yml writes no uuid key, and play.test.Fixtures only sets fields the entity declares,
		// so every fixture user has uuid == null and User.findByUUID() answers nothing.
		Assert.assertEquals("null", String.valueOf(service.identifyUser("admin-token")));
		Assert.assertEquals("null", String.valueOf(service.identifyUser("admin@domain.com")));
		Assert.assertEquals("null", String.valueOf(inTransaction(() -> User.findByEmail("admin@domain.com").uuid)));

		// An empty or absent token is refused by the query, not by a guard: findByUUID() finds no row and
		// the method returns null rather than raising.
		Assert.assertEquals("null", String.valueOf(service.identifyUser("")));
		Assert.assertEquals("null", String.valueOf(service.identifyUser(null)));
	}

	@Test
	public void identifyUserAnswersTheAccountAndItsAdminFlagForTheTokenItMatches() throws Throwable {
		loadScenarioFixtures();
		inTransactionVoid(() -> {
			User admin = user("admin@domain.com");
			admin.uuid = "abcd";
			admin.save();
			User bob = user("bob@domain.com");
			bob.uuid = "xy89";
			bob.save();
			return null;
		});

		OpenttdService service = new OpenttdService();
		// group.admin is what ExternalUser.setAdmin() carries into the robot's rules: only the Admin group
		// of test/data.yml is an admin group, so Bob's General group answers false.
		Assert.assertEquals("[id=1 name=Admin admin=true]", describeExternal(service.identifyUser("abcd")));
		Assert.assertEquals("[id=3 name=Bob admin=false]", describeExternal(service.identifyUser("xy89")));

		// A token nobody carries, and a token that is the id of a group rather than a uuid.
		Assert.assertEquals("null", String.valueOf(service.identifyUser("zzzz")));
		Assert.assertEquals("null", String.valueOf(service.identifyUser("1")));
	}

	@Test
	public void identifyUserStopsAnsweringOnceTheLoginTokenIsTakenAway() throws Throwable {
		loadScenarioFixtures();
		inTransactionVoid(() -> {
			User admin = user("admin@domain.com");
			admin.uuid = "abcd";
			admin.save();
			User bob = user("bob@domain.com");
			bob.uuid = "xy89";
			bob.save();
			return null;
		});

		OpenttdService service = new OpenttdService();
		Assert.assertEquals("[id=1 name=Admin admin=true]", describeExternal(service.identifyUser("abcd")));

		// Profile.onDisconnect() writes uuid = null, which is what logs the user out of the robot too.
		inTransactionVoid(() -> {
			User admin = user("admin@domain.com");
			admin.uuid = null;
			admin.save();
			return null;
		});

		Assert.assertEquals("null", String.valueOf(service.identifyUser("abcd")));
		Assert.assertEquals("[id=3 name=Bob admin=false]", describeExternal(service.identifyUser("xy89")));
	}

	@Test
	public void theServiceTheRobotDirectorUsesIsTheOneTheHandlerWired() throws Throwable {
		loadScenarioFixtures();
		inTransactionVoid(() -> {
			User admin = user("admin@domain.com");
			admin.uuid = "abcd";
			admin.save();
			return null;
		});

		// com.openttd.robot.ExternalServices holds the instance OpenttdAdminHandler built, so the director
		// reaches the same service the page tests reach through the handler.
		Assert.assertEquals("[id=1 name=Admin admin=true]", describeExternal(wiredUserService().identifyUser("abcd")));
		Assert.assertEquals("[id=1 name=Admin admin=true]", describeExternal(new OpenttdService().identifyUser("abcd")));
	}

	// ~~~~~~~~~~~~ saveGame

	@Test
	public void saveGameWritesOnePlayedGameAndOneScorePerLoggedPlayer() throws Throwable {
		loadScenarioFixtures();
		long adminId = inTransaction(() -> user("admin@domain.com").id);
		long bobId = inTransaction(() -> user("bob@domain.com").id);

		new OpenttdService().saveGame(1L, playersOf(
				gamePlayer(adminId, "Admin", true, 120.0, 0.5),
				gamePlayer(bobId, "Bob", false, 60.0, 1.0)));

		Assert.assertEquals("[1:HighSpeed Scenario scores=2]", gameRowsInDb());
		Assert.assertEquals("[PlayedGameScore[1], PlayedGameScore[2]]", scoreNamesSorted());

		// The order of the two scores is not pinnable: saveGame() iterates the keys of a
		// Map<GamePlayer, User>, and GamePlayer does not override hashCode(), so it is the identity hash.
		Assert.assertEquals("[Admin achivement=120.0 presence=0.5 game=PlayedGame[1], "
				+ "Bob achivement=60.0 presence=1.0 game=PlayedGame[1]]", scoreRowsSorted());

		// begin falls back to the clock because this service has no recorded starting date, and both stamps
		// land in the same second: MariaDB keeps DATETIME to the second, so they read back equal.
		Assert.assertEquals(gameBeginMillis(1L), gameEndMillis(1L));
		Assert.assertEquals("0", String.valueOf(gameBeginMillis(1L) % 1000));
		Assert.assertEquals("begin class=java.sql.Timestamp end class=java.sql.Timestamp", dateClasses(1L));
	}

	@Test
	public void saveGameOfACollectionWithoutAnExternalUserWritesNothingAtAll() throws Throwable {
		loadScenarioFixtures();
		long adminId = inTransaction(() -> user("admin@domain.com").id);
		long bobId = inTransaction(() -> user("bob@domain.com").id);

		// The log line is the first statement of saveGame(), and it concatenates the collection: a player
		// with no ExternalUser raises there, before JPA.startTx(), so even the matched players are lost.
		GamePlayer loggedIn = gamePlayer(bobId, "Bob", false, 45.0, 0.25);
		Assert.assertEquals("1:Admin accomplishment:120.0 duration:0.5 score:0 winner:false",
				String.valueOf(gamePlayer(adminId, "Admin", true, 120.0, 0.5)));
		Assert.assertEquals("java.lang.NullPointerException: Cannot invoke \"com.openttd.robot.model.ExternalUser.getId()\""
				+ " because \"this.externalUser\" is null | ",
				causeChain(failure(() -> String.valueOf(gamePlayerWithoutAccount(30.0, 1.0)))));

		OpenttdService service = new OpenttdService();
		long gamesBefore = gameCountInDb();
		long scoresBefore = scoreCountInDb();

		Assert.assertEquals("java.lang.NullPointerException: Cannot invoke \"com.openttd.robot.model.ExternalUser.getId()\""
				+ " because \"this.externalUser\" is null | ",
				causeChain(failure(() -> {
					service.saveGame(2L, playersOf(
							gamePlayer(adminId, "Admin", true, 10.0, 1.0),
							loggedIn,
							gamePlayerWithoutAccount(30.0, 1.0)));
					return "returned";
				})));

		Assert.assertEquals(String.valueOf(gamesBefore), String.valueOf(gameCountInDb()));
		Assert.assertEquals(String.valueOf(scoresBefore), String.valueOf(scoreCountInDb()));
		Assert.assertEquals("[]", gameRowsInDb());
	}

	@Test
	public void saveGameKeepsOnlyThePlayersWhoseAccountExists() throws Throwable {
		loadScenarioFixtures();
		long bobId = inTransaction(() -> user("bob@domain.com").id);

		// userId 0 answers Logger.error("userId null"), and an id no row holds answers
		// Logger.error("user null"); both are dropped by the filter, so only Bob's row is written.
		new OpenttdService().saveGame(2L, playersOf(
				gamePlayer(0L, "Nobody", false, 10.0, 1.0),
				gamePlayer(999L, "Ghost", false, 20.0, 1.0),
				gamePlayer(bobId, "Bob", false, 45.0, 0.25)));

		Assert.assertEquals("[1:Toyland Scenario scores=1]", gameRowsInDb());
		Assert.assertEquals("[1:Bob achivement=45.0 presence=0.25 game=PlayedGame[1]]", scoresOfGame(1L));
		Assert.assertEquals("[Bob achivement=45.0 presence=0.25 game=PlayedGame[1]]", scoreRowsSorted());
	}

	@Test
	public void saveGameOfAnUnknownScenarioStillWritesTheGameWithNoScenario() throws Throwable {
		loadScenarioFixtures();
		long adminId = inTransaction(() -> user("admin@domain.com").id);

		// RobotConfiguration covers scenario ids 1..5, so 6 is a scenario the fixtures do not hold:
		// Scenario.findById() answers null and the game row is stored with scenario_id == null.
		new OpenttdService().saveGame(6L, playersOf(gamePlayer(adminId, "Admin", true, 5.0, 1.0)));

		Assert.assertEquals("[1:null scores=1]", gameRowsInDb());
		Assert.assertEquals("[1:Admin achivement=5.0 presence=1.0 game=PlayedGame[1]]", scoresOfGame(1L));
		Assert.assertEquals("null", inTransaction(() -> String.valueOf(playedGame(1L).scenario)));
	}

	@Test
	public void saveGameOfAnEmptyCollectionWritesTheGameWithoutAnyScore() throws Throwable {
		loadScenarioFixtures();

		new OpenttdService().saveGame(1L, playersOf());

		Assert.assertEquals("[1:HighSpeed Scenario scores=0]", gameRowsInDb());
		Assert.assertEquals("[]", scoreRowsSorted());
		Assert.assertEquals("0", String.valueOf(scoreCountInDb()));
	}

	@Test
	public void saveGameOfANullCollectionSwallowsTheNullPointerAndWritesNothing() throws Throwable {
		loadScenarioFixtures();

		// Logger.info() prints "null" for the collection, then the for-each raises. The catch(Exception) of
		// the method rolls the transaction back, so the PlayedGame the method builds after the loop is lost.
		new OpenttdService().saveGame(1L, null);

		Assert.assertEquals("0", String.valueOf(gameCountInDb()));
		Assert.assertEquals("[]", gameRowsInDb());
	}

	@Test
	public void theStartingDateTheServiceRecordedBecomesTheBeginOfTheNextGame() throws Throwable {
		loadScenarioFixtures();
		long adminId = inTransaction(() -> user("admin@domain.com").id);
		long bobId = inTransaction(() -> user("bob@domain.com").id);

		OpenttdService service = new OpenttdService();
		Assert.assertEquals("{}", startingDatesOf(service).toString());

		// gameStartingDateByScenario is keyed by scenario id, so a date put for 3 (Artic) is what
		// saveGame(3L) reads; saveGame(1L) still falls back to the clock.
		startingDatesOf(service).put(3L, new Date(1700000000000L));
		service.saveGame(1L, playersOf(gamePlayer(adminId, "Admin", true, 1.0, 1.0)));
		service.saveGame(3L, playersOf(gamePlayer(bobId, "Bob", false, 2.0, 1.0)));

		Assert.assertEquals("[1:HighSpeed Scenario scores=1, 2:Artic Scenario scores=1]", gameRowsInDb());

		// 1700000000000 is already a whole second, so the recorded begin survives the round trip exactly,
		// and end is the moment of the call: a begin of 2023 against an end of now.
		Assert.assertEquals(1700000000000L, gameBeginMillis(2L));
		Assert.assertTrue("expected the end of game 2 to be after 2023-11-14: " + gameEndMillis(2L),
				gameEndMillis(2L) > gameBeginMillis(2L));
		Assert.assertEquals("begin class=java.sql.Timestamp end class=java.sql.Timestamp", dateClasses(2L));

		// The game of the scenario with no recorded date begins at its own end.
		Assert.assertEquals(gameBeginMillis(1L), gameEndMillis(1L));
	}

	@Test
	public void theSamePlayerTwiceInTheCollectionWritesTwoScoresOfOneGame() throws Throwable {
		loadScenarioFixtures();
		long bobId = inTransaction(() -> user("bob@domain.com").id);

		// The filter keys on GamePlayer, not on User, so two GamePlayer objects of one account both survive.
		new OpenttdService().saveGame(1L, playersOf(
				gamePlayer(bobId, "Bob", false, 40.0, 1.0),
				gamePlayer(bobId, "Bob", false, 10.0, 0.5)));

		Assert.assertEquals("[1:HighSpeed Scenario scores=2]", gameRowsInDb());
		Assert.assertEquals("[Bob achivement=10.0 presence=0.5 game=PlayedGame[1], "
				+ "Bob achivement=40.0 presence=1.0 game=PlayedGame[1]]", scoreRowsSorted());
		Assert.assertEquals("2", String.valueOf(scoreCountInDb()));
	}

	@Test
	public void aPlayedGameReadBackAfterTheServiceClosedItsTransactionIsDetached() throws Throwable {
		loadScenarioFixtures();
		long bobId = inTransaction(() -> user("bob@domain.com").id);

		new OpenttdService().saveGame(1L, playersOf(gamePlayer(bobId, "Bob", false, 30.0, 1.0)));

		// saveGame() commits and closes its own transaction, so the entity the read below hands back is
		// detached: its scores collection is lazy and cannot be loaded without a session.
		PlayedGame detached = gameInDb(1L);
		Assert.assertEquals("org.hibernate.LazyInitializationException: failed to lazily initialize a"
				+ " collection of role: models.leaderboard.PlayedGame.scores, could not initialize proxy"
				+ " - no Session | ", causeChain(failure(() -> detached.scores.size())));
		Assert.assertEquals("[1:HighSpeed Scenario scores=1]", gameRowsInDb());
	}

	// ~~~~~~~~~~~~ endGame

	@Test
	public void endGameRestartsThenShutsDownThenStartsTheRobotAgain() throws Throwable {
		loadScenarioFixtures();
		long bobId = inTransaction(() -> user("bob@domain.com").id);
		OpenttdService service = new OpenttdService();

		FakeAdmin robot = new FakeAdmin(new Configuration());
		putRobot(1L, robot);
		try {
			service.endGame(1L);

			// isRunning is asked three times: restart while up, shutdown while still up, then startup
			// because the shutdown made it not running.
			Assert.assertEquals("[restart]", robot.commands().toString());
			Assert.assertEquals("1", String.valueOf(robot.shutdownCalls()));
			Assert.assertEquals("startupCalls=0 shutdownCalls=1 connected=false commands=[restart]",
					describeFakeAdmin(robot));

			// OpenttdAdminHandler.startup() builds a real com.openttd.admin.OpenttdAdmin and puts it in the
			// map, so the fake is replaced rather than started: the fake's startup() is never reached.
			Assert.assertEquals("com.openttd.admin.OpenttdAdmin", robotsOfHandler().get(1L).getClass().getName());

			// endGame() records the moment it restarted the robot as the next game's begin.
			Assert.assertEquals("[1]", startingDatesOf(service).keySet().toString());
			long recorded = startingDatesOf(service).get(1L).getTime();
			service.saveGame(1L, playersOf(gamePlayer(bobId, "Bob", false, 30.0, 1.0)));

			// MariaDB truncates DATETIME to the second, so begin is the recorded date rounded down to it.
			Assert.assertEquals(recorded - recorded % 1000, gameBeginMillis(1L));
			Assert.assertEquals("[1:HighSpeed Scenario scores=1]", gameRowsInDb());
			Assert.assertEquals("[Bob achivement=30.0 presence=1.0 game=PlayedGame[1]]", scoreRowsSorted());
		} finally {
			removeRobot(1L);
		}
		assertNoRobotsLeftBehind();
	}

	@Test
	public void endGameOfAScenarioThatHasNoRobotStartsOne() throws Throwable {
		loadScenarioFixtures();
		OpenttdService service = new OpenttdService();

		// The two isRunning() guards answer false, so the third branch starts a robot for scenario 5
		// (Steam, admin port 4277) even though nothing was connected before.
		service.endGame(5L);
		try {
			Assert.assertEquals("[5]", robotsOfHandler().keySet().toString());
			Assert.assertEquals("com.openttd.admin.OpenttdAdmin", robotsOfHandler().get(5L).getClass().getName());
			Assert.assertEquals("[5]", startingDatesOf(service).keySet().toString());
		} finally {
			removeRobot(5L);
		}
		assertNoRobotsLeftBehind();
	}

	@Test
	public void endGameOfAnUnknownScenarioIdRaisesBeforeAnyRobotIsStarted() throws Throwable {
		loadScenarioFixtures();
		OpenttdService service = new OpenttdService();

		// RobotConfiguration.fromScenarioId(6) answers null and OpenttdAdminHandler.startup() reads
		// configuration.adminPort on it, so the game stats of the scenario are never recorded either.
		Assert.assertEquals("java.lang.NullPointerException: Try to read adminPort on null object"
				+ " models.openttd.RobotConfiguration (openttd.OpenttdAdminHandler.startup, line 45) | ",
				causeChain(failure(() -> {
					service.endGame(6L);
					return "returned";
				})));

		Assert.assertEquals("[]", robotsOfHandler().keySet().toString());
		Assert.assertEquals("{}", startingDatesOf(service).toString());
	}

	// ~~~~~~~~~~~~ exposeGame / getGame

	@Test
	public void exposeGameHandsTheSameGameInfoBackUntilItIsExposedAsNull() throws Throwable {
		OpenttdService service = new OpenttdService();

		Assert.assertEquals("null", String.valueOf(service.getGame(1L)));

		GameInfo info = new GameInfo();
		info.setServerName("Exposed server");
		service.exposeGame(1L, info);

		// The map stores the GameInfo the robot passed in, so the page asking getGame() reads the live object.
		Assert.assertEquals("Exposed server", service.getGame(1L).getServerName());
		Assert.assertEquals("true", String.valueOf(service.getGame(1L) == info));
		Assert.assertEquals("[1]", gameInfosOf(service).keySet().toString());

		// A scenario nobody exposed, and an id the enum does not cover, both answer null.
		Assert.assertEquals("null", String.valueOf(service.getGame(2L)));
		Assert.assertEquals("null", String.valueOf(service.getGame(999L)));

		// Exposing null replaces the entry rather than removing it, so getGame() answers null and the key stays.
		service.exposeGame(1L, null);
		Assert.assertEquals("null", String.valueOf(service.getGame(1L)));
		Assert.assertEquals("[1]", gameInfosOf(service).keySet().toString());
	}

	@Test
	public void gameInfoIsKeptPerServiceInstanceAndTheWiredOneIsTheHandlerService() throws Throwable {
		GameInfo info = new GameInfo();
		info.setServerName("Wired server");
		try {
			wiredGameService().exposeGame(1L, info);

			// gameInfoByScenario is an instance field, so a service built by a test answers nothing about
			// what the instance com.openttd.robot.ExternalServices holds exposed.
			Assert.assertEquals("null", String.valueOf(new OpenttdService().getGame(1L)));
			Assert.assertEquals("{}", gameInfosOf(new OpenttdService()).toString());
			Assert.assertEquals("Wired server", wiredGameService().getGame(1L).getServerName());
		} finally {
			// The wired instance is a singleton, so its map is cleared again for the suites that follow.
			wiredGameService().exposeGame(1L, null);
			Assert.assertEquals("null", String.valueOf(wiredGameService().getGame(1L)));
		}
	}
}
