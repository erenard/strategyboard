import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

import play.test.Fixtures;

import com.openttd.admin.OpenttdAdmin;
import com.openttd.client.OpenttdClient;
import com.openttd.network.admin.NetworkAdminSender;
import com.openttd.network.core.Configuration;
import com.openttd.robot.ExternalServices;
import com.openttd.robot.model.ExternalUser;
import com.openttd.robot.model.GamePlayer;

import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;

import openttd.OpenttdAdminHandler;
import openttd.OpenttdClientHandler;
import openttd.OpenttdService;

/**
 * Setup shared by the suites of app/openttd (OpenttdServiceTest, OpenttdAdminHandlerTest,
 * OpenttdClientHandlerTest). It exists because all three drive singletons and robot objects that talk
 * to a live OpenTTD server, and none of them may open a socket:
 *
 *   - OpenttdAdminHandler and OpenttdClientHandler keep their state in private fields of a singleton
 *     (robots / client). The fakes below are injected through reflection and removed again in a finally
 *     block, so a suite never leaves a robot behind for the next suite: openttd/Openttd.index.html asks
 *     OpenttdAdminHandler.isRunning() for every scenario, and OpenttdPageTest asserts every answer is false.
 *   - OpenttdService.identifyUser(), saveGame() and endGame() open transactions of their own
 *     (JPA.startTx / closeTx / rollbackTx), so they must be called outside an ambient one, and the rows
 *     they wrote are read back in a transaction of their own. A PlayedGame handed back by a closed
 *     transaction is detached, and its scores collection is lazy: touching it raises
 *     LazyInitializationException instead of loading the rows.
 *   - A GamePlayer the robot never matched to an account carries no ExternalUser, and
 *     GamePlayer.toString() dereferences it. OpenttdService.saveGame() logs the collection it was given,
 *     so such a player makes the call throw before its transaction starts. The fakes therefore always
 *     carry an ExternalUser unless a test is pinning that failure.
 *
 * The helpers print rows the way BaseModelTest does, and the score rows of one saveGame() call are
 * sorted: the service fills a Map<GamePlayer, User> and iterates its keys, whose order comes from the
 * identity hash of the GamePlayer instances, so the ids the scores get are not pinnable.
 */
public abstract class BaseOpenttdTest extends BaseModelTest {

	/** The scenarios of test/openttd-data.yml, loaded after the data.yml the base class seeds. */
	protected static void loadScenarioFixtures() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");
			return null;
		});
	}

	/** One PlayedGame row, in a transaction of its own: the service calls closed the one they used. */
	protected static PlayedGame gameInDb(long id) throws Throwable {
		return inTransaction(() -> playedGame(id));
	}

	/** Every PlayedGame as gameLines(), in a transaction of its own. */
	protected static String gameRowsInDb() throws Throwable {
		return inTransaction(() -> {
			List<PlayedGame> rows = PlayedGame.findAll();
			return gameLines(rows);
		});
	}

	protected static long gameCountInDb() throws Throwable {
		return inTransaction(() -> PlayedGame.count());
	}

	protected static long scoreCountInDb() throws Throwable {
		return inTransaction(() -> PlayedGameScore.count());
	}

	/**
	 * Every PlayedGameScore sorted by user name and its two ratios:
	 * "[Admin achivement=120.0 presence=0.5 game=PlayedGame[1], Bob achivement=60.0 presence=1.0 ...]".
	 */
	protected static String scoreRowsSorted() throws Throwable {
		return inTransaction(() -> {
			List<String> rows = new ArrayList<String>();
			List<PlayedGameScore> scores = PlayedGameScore.findAll();
			for (PlayedGameScore score : scores) {
				rows.add(describeScoreRow(score));
			}
			java.util.Collections.sort(rows);
			return rows.toString();
		});
	}

	/** The scores of one played game, sorted by their own rendering: "[1:Admin achivement=... presence=...]". */
	protected static String scoresOfGame(long gameId) throws Throwable {
		return inTransaction(() -> {
			PlayedGame game = playedGame(gameId);
			List<String> rows = new ArrayList<String>();
			List<PlayedGameScore> scores = game.scores;
			for (PlayedGameScore score : scores) {
				rows.add(score.id + ":" + describeScoreRow(score));
			}
			java.util.Collections.sort(rows);
			return rows.toString();
		});
	}

	protected static String describeScoreRow(PlayedGameScore score) {
		return score.user + " achivement=" + score.achivement + " presence=" + score.presenceRatio
				+ " game=" + score.playedGame;
	}

	/** Every PlayedGameScore as EntityName[id], sorted: "[PlayedGameScore[1], PlayedGameScore[2]]". */
	protected static String scoreNamesSorted() throws Throwable {
		return inTransaction(() -> {
			List<PlayedGameScore> scores = PlayedGameScore.findAll();
			return sortedNames(scores);
		});
	}

	/** begin and end as millis, "[begin=1700000000000 end=1795038688000]". MariaDB keeps DATETIME to the
	 * second, so a date built in Java reads back with its millis rounded down - which is what the
	 * assertions compare rather than the string form of a java.sql.Timestamp. */
	protected static String gameDates(long gameId) throws Throwable {
		return inTransaction(() -> {
			PlayedGame game = playedGame(gameId);
			return "begin=" + game.begin.getTime() + " end=" + game.end.getTime();
		});
	}

	protected static long gameBeginMillis(long gameId) throws Throwable {
		return inTransaction(() -> playedGame(gameId).begin.getTime());
	}

	protected static long gameEndMillis(long gameId) throws Throwable {
		return inTransaction(() -> playedGame(gameId).end.getTime());
	}

	protected static String dateClasses(long gameId) throws Throwable {
		return inTransaction(() -> {
			PlayedGame game = playedGame(gameId);
			return "begin class=" + game.begin.getClass().getName() + " end class=" + game.end.getClass().getName();
		});
	}

	/** The game a GamePlayer carries an account for: "[id=1 name=Admin admin=true]". */
	protected static String describeExternal(ExternalUser user) {
		return user == null ? "null" : "[id=" + user.getId() + " name=" + user.getName() + " admin=" + user.isAdmin() + "]";
	}

	/** The GamePlayer the robot director hands to saveGame(), with the account the robot matched. */
	protected static GamePlayer gamePlayer(long userId, String name, boolean admin, double accomplishment, double duration) {
		GamePlayer gamePlayer = new GamePlayer();
		ExternalUser externalUser = new ExternalUser();
		externalUser.setId(userId);
		externalUser.setName(name);
		externalUser.setAdmin(admin);
		gamePlayer.setExternalUser(externalUser);
		gamePlayer.setAccomplishment(accomplishment);
		gamePlayer.setDuration(duration);
		return gamePlayer;
	}

	/** A company that was never logged in: no ExternalUser, so toString() of it raises. */
	protected static GamePlayer gamePlayerWithoutAccount(double accomplishment, double duration) {
		GamePlayer gamePlayer = new GamePlayer();
		gamePlayer.setAccomplishment(accomplishment);
		gamePlayer.setDuration(duration);
		return gamePlayer;
	}

	/** The private robots map of the handler singleton, so a fake can be injected and read back. */
	@SuppressWarnings("unchecked")
	protected static Map<Long, OpenttdAdmin> robotsOfHandler() throws Throwable {
		Field field = OpenttdAdminHandler.class.getDeclaredField("robots");
		field.setAccessible(true);
		return (Map<Long, OpenttdAdmin>) field.get(OpenttdAdminHandler.getInstance());
	}

	protected static void putRobot(long scenarioId, OpenttdAdmin robot) throws Throwable {
		robotsOfHandler().put(scenarioId, robot);
	}

	protected static void removeRobot(long scenarioId) throws Throwable {
		robotsOfHandler().remove(scenarioId);
	}

	/** The private client field of the client handler singleton, as an Object: the fake is cast by the caller. */
	protected static Object clientHeld() throws Throwable {
		Field field = OpenttdClientHandler.class.getDeclaredField("client");
		field.setAccessible(true);
		return field.get(OpenttdClientHandler.getInstance());
	}

	protected static void holdClient(Object client) throws Throwable {
		Field field = OpenttdClientHandler.class.getDeclaredField("client");
		field.setAccessible(true);
		field.set(OpenttdClientHandler.getInstance(), client);
	}

	/** One private field of a service, so the maps that drive begin and getGame() can be inspected. */
	protected static Object fieldOf(Object target, String name) throws Throwable {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	@SuppressWarnings("unchecked")
	protected static Map<Long, Date> startingDatesOf(OpenttdService service) throws Throwable {
		return (Map<Long, Date>) fieldOf(service, "gameStartingDateByScenario");
	}

	@SuppressWarnings("unchecked")
	protected static Map<Long, com.openttd.network.admin.GameInfo> gameInfosOf(OpenttdService service) throws Throwable {
		return (Map<Long, com.openttd.network.admin.GameInfo>) fieldOf(service, "gameInfoByScenario");
	}

	protected static ExternalServices.ExternalGameService wiredGameService() {
		return ExternalServices.getInstance().getExternalGameService();
	}

	protected static ExternalServices.ExternalUserService wiredUserService() {
		return ExternalServices.getInstance().getExternalUserService();
	}

	/** The declared fields of a class, sorted: "[instance, robots]". */
	protected static String declaredFieldNames(Class<?> clazz) {
		List<String> names = new ArrayList<String>();
		for (Field field : clazz.getDeclaredFields()) {
			names.add(field.getName());
		}
		java.util.Collections.sort(names);
		return Arrays.toString(names.toArray());
	}

	protected static Collection<GamePlayer> playersOf(GamePlayer... players) {
		return new ArrayList<GamePlayer>(Arrays.asList(players));
	}

	/** Every assertion of a fake robot: what the handler did to it, in the order it did it. */
	protected static String describeFakeAdmin(FakeAdmin robot) {
		return "startupCalls=" + robot.startupCalls + " shutdownCalls=" + robot.shutdownCalls
				+ " connected=" + robot.connected + " commands=" + robot.commands();
	}

	/**
	 * An admin robot that never touches the network. startup() and shutdown() only count their calls, and
	 * shutdown() drops the connection the way a real robot does, so isRunning() of the handler can be
	 * read after each step.
	 */
	protected static class FakeAdmin extends OpenttdAdmin {
		private boolean connected = true;
		private int startupCalls = 0;
		private int shutdownCalls = 0;
		private FakeSender sender = new FakeSender();

		protected FakeAdmin(Configuration configuration) {
			super(configuration);
		}

		public boolean isConnected() {
			return connected;
		}

		public void startup() {
			startupCalls++;
		}

		public void shutdown() {
			shutdownCalls++;
			connected = false;
		}

		public NetworkAdminSender getSend() {
			return sender;
		}

		/**
		 * The rcon commands the handler sent through this robot's sender. A robot whose sender was removed
		 * answers an empty list, which is the point of the restart() branch that refuses to send anything.
		 */
		protected List<String> commands() {
			return sender == null ? new ArrayList<String>() : sender.commands;
		}

		protected int startupCalls() {
			return startupCalls;
		}

		protected int shutdownCalls() {
			return shutdownCalls;
		}

		protected void dropConnection() {
			connected = false;
		}

		protected void restoreConnection() {
			connected = true;
		}

		/** A robot whose sender is absent: the branch of restart() that refuses to send anything. */
		protected void removeSender() {
			sender = null;
		}
	}

	/** A sender that records the commands instead of writing them to a socket. */
	protected static class FakeSender extends NetworkAdminSender {
		private final List<String> commands = new ArrayList<String>();

		protected FakeSender() {
			super(null);
		}

		public void rcon(String command) {
			commands.add(command);
		}
	}

	/** A client that never touches the network, for the same reason as {@link FakeAdmin}. */
	protected static class FakeClient extends OpenttdClient {
		private boolean connected = true;
		private int startupCalls = 0;
		private int shutdownCalls = 0;

		protected FakeClient(Configuration configuration) {
			super(configuration);
		}

		public boolean isConnected() {
			return connected;
		}

		public void startup() {
			startupCalls++;
		}

		public void shutdown() {
			shutdownCalls++;
			connected = false;
		}

		protected void dropConnection() {
			connected = false;
		}

		protected void restoreConnection() {
			connected = true;
		}

		protected int startupCalls() {
			return startupCalls;
		}

		protected int shutdownCalls() {
			return shutdownCalls;
		}
	}

	/** Asserts the robots map of the singleton is empty again, so a suite cannot leak state into the next. */
	protected static void assertNoRobotsLeftBehind() throws Throwable {
		Assert.assertEquals("[]", robotsOfHandler().keySet().toString());
	}
}
