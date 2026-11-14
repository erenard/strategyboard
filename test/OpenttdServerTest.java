import org.junit.Assert;
import org.junit.Test;

import play.db.jpa.JPA;
import play.libs.F;
import play.test.Fixtures;

import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.Scenario;
import models.openttd.OpenttdServer;
import models.openttd.RobotConfiguration;

import com.openttd.network.admin.GameInfo;
import com.openttd.robot.ExternalServices;

/**
 * Coverage of models.openttd.OpenttdServer, the value object app/views/openttd/Openttd/index.html renders
 * for the server block of the front page.
 *
 *   - getStatus() asks the robot's ExternalGameService for the GameInfo of the scenario id of the
 *     RobotConfiguration and looks the Scenario row up by the same id. Both must be present: a service that
 *     answers null, a service that raises, or a scenario row that was never inserted all answer null, and
 *     the template then shows the field defaults of the class instead of a status.
 *   - getStatus(null) raises inside its own try/catch, so it answers null too: the NPE of
 *     robotConfiguration.scenarioId is logged and dropped, which is why the front page never fails on a
 *     robot configuration nobody resolved.
 *   - name, revision, width, height, clientsCount and companiesCount are read from the GameInfo; goal is not
 *     read from anything, it is the same sentence the field default already holds.
 *   - startedDate and currentDate are formatted dd/MM/yyyy from com.openttd.util.Convert.dayToCalendar(),
 *     which maps an OpenTTD day number onto the year 1 epoch plus two days: day 0 reads as 03/01/0001 and
 *     day 1000 as 29/09/0002.
 *   - image comes from Scenario.getImage(), so it is the image of the scenario row, not the default
 *     "highSpeed_on.gif" the field carries. The private constructor leaves that default when the scenario is
 *     null, which getStatus() never reaches because it refuses a null scenario before calling it.
 *   - The private constructor refuses a null GameInfo with IllegalArgumentException("GameInfo == null"); the
 *     only caller that could pass one is getStatus(), whose null check runs first, so the branch is reachable
 *     only through reflection (pinned below).
 */
public class OpenttdServerTest extends BaseModelTest {

	/** The GameInfo of the dump: a server nobody runs, with day numbers chosen to be printable. */
	private static GameInfo probeGameInfo() {
		GameInfo info = new GameInfo();
		info.setServerName("Probe Server");
		info.setServerRevision("1.2.3");
		info.setStartDate(0L);
		info.setCurrentDate(1000L);
		info.setMapWidth(512);
		info.setMapHeight(1024);
		info.clientsCount = 7;
		info.companiesCount = 3;
		return info;
	}

	private static class FixedGameService implements ExternalServices.ExternalGameService {
		private final GameInfo info;

		FixedGameService(GameInfo info) {
			this.info = info;
		}

		public GameInfo getGame(long scenarioId) {
			return info;
		}

		public void exposeGame(long scenarioId, GameInfo gameInfo) {
		}

		public void saveGame(long scenarioId, java.util.Collection<com.openttd.robot.model.GamePlayer> players) {
		}

		public void endGame(long scenarioId) {
		}
	}

	private static class ThrowingGameService implements ExternalServices.ExternalGameService {
		public GameInfo getGame(long scenarioId) {
			throw new IllegalStateException("no robot connected");
		}

		public void exposeGame(long scenarioId, GameInfo gameInfo) {
		}

		public void saveGame(long scenarioId, java.util.Collection<com.openttd.robot.model.GamePlayer> players) {
		}

		public void endGame(long scenarioId) {
		}
	}

	/**
	 * Runs the block with a stand-in game service. The real one is put back afterwards: ExternalServices is a
	 * process-wide singleton, so leaving a fake service in place would break every other suite that talks to
	 * the robot.
	 */
	private static void withGameService(final ExternalServices.ExternalGameService service, final F.Function0<Void> block)
			throws Throwable {
		ExternalServices.ExternalGameService original = ExternalServices.getInstance().getExternalGameService();
		try {
			ExternalServices.getInstance().setExternalGameService(service);
			inTransactionVoid(block);
		} finally {
			ExternalServices.getInstance().setExternalGameService(original);
		}
	}

	@Test
	public void statusOfEveryRobotConfigurationCarriesTheGameInfoAndTheImageOfItsScenario() throws Throwable {
		withGameService(new FixedGameService(probeGameInfo()), () -> {
			Fixtures.loadModels("openttd-data.yml");

			Assert.assertEquals("name=Probe Server revision=1.2.3 image=/images/openttd/highSpeed_on.gif"
					+ " goal=Reach the best company performance in 10 years to win the game."
					+ " startedDate=03/01/0001 currentDate=29/09/0002 width=512 height=1024"
					+ " companiesCount=3 clientsCount=7",
					describeServer(OpenttdServer.getStatus(RobotConfiguration.HIGH_SPEED)));
			Assert.assertEquals("name=Probe Server revision=1.2.3 image=/images/openttd/toyland_on.gif"
					+ " goal=Reach the best company performance in 10 years to win the game."
					+ " startedDate=03/01/0001 currentDate=29/09/0002 width=512 height=1024"
					+ " companiesCount=3 clientsCount=7",
					describeServer(OpenttdServer.getStatus(RobotConfiguration.TOYLAND)));
			Assert.assertEquals("name=Probe Server revision=1.2.3 image=/images/openttd/artic_on.gif"
					+ " goal=Reach the best company performance in 10 years to win the game."
					+ " startedDate=03/01/0001 currentDate=29/09/0002 width=512 height=1024"
					+ " companiesCount=3 clientsCount=7",
					describeServer(OpenttdServer.getStatus(RobotConfiguration.ARTIC)));
			Assert.assertEquals("name=Probe Server revision=1.2.3 image=/images/openttd/desertic_on.gif"
					+ " goal=Reach the best company performance in 10 years to win the game."
					+ " startedDate=03/01/0001 currentDate=29/09/0002 width=512 height=1024"
					+ " companiesCount=3 clientsCount=7",
					describeServer(OpenttdServer.getStatus(RobotConfiguration.DESERTIC)));
			Assert.assertEquals("name=Probe Server revision=1.2.3 image=/images/openttd/steam_on.gif"
					+ " goal=Reach the best company performance in 10 years to win the game."
					+ " startedDate=03/01/0001 currentDate=29/09/0002 width=512 height=1024"
					+ " companiesCount=3 clientsCount=7",
					describeServer(OpenttdServer.getStatus(RobotConfiguration.STEAM)));
			return null;
		});
	}

	@Test
	public void theGameInfoOverridesTheFieldDefaultsExceptTheGoal() throws Throwable {
		withGameService(new FixedGameService(probeGameInfo()), () -> {
			Fixtures.loadModels("openttd-data.yml");

			OpenttdServer status = OpenttdServer.getStatus(RobotConfiguration.HIGH_SPEED);
			// The class answers "Plop" and a 2 company / 5 client server until the GameInfo overwrites them.
			Assert.assertEquals("Probe Server", status.name);
			Assert.assertEquals(7, status.clientsCount);
			Assert.assertEquals(3, status.companiesCount);
			Assert.assertEquals(512, status.width);
			Assert.assertEquals(1024, status.height);
			// goal is never read from the GameInfo: the constructor writes the sentence the field already has.
			Assert.assertEquals("Reach the best company performance in 10 years to win the game.", status.goal);
			// The revision of the probe GameInfo happens to equal the field default, so the override is pinned
			// by the name above rather than by this string.
			Assert.assertEquals("1.2.3", status.revision);
			return null;
		});
	}

	@Test
	public void aDistinctRevisionReachesTheStatus() throws Throwable {
		GameInfo info = probeGameInfo();
		info.setServerRevision("12.4-r2");
		withGameService(new FixedGameService(info), () -> {
			Fixtures.loadModels("openttd-data.yml");

			Assert.assertEquals("12.4-r2", OpenttdServer.getStatus(RobotConfiguration.STEAM).revision);
			return null;
		});
	}

	@Test
	public void statusAnswersNullWhenTheGameServiceHasNoGame() throws Throwable {
		withGameService(new FixedGameService(null), () -> {
			Fixtures.loadModels("openttd-data.yml");

			Assert.assertNull(OpenttdServer.getStatus(RobotConfiguration.HIGH_SPEED));
			// The other configurations answer the same way, so no scenario of the fixtures renders a block.
			Assert.assertNull(OpenttdServer.getStatus(RobotConfiguration.TOYLAND));
			Assert.assertNull(OpenttdServer.getStatus(RobotConfiguration.STEAM));
			return null;
		});
	}

	@Test
	public void statusAnswersNullWhenTheGameServiceRaises() throws Throwable {
		// The catch of getStatus() swallows anything the service throws and logs it, so the front page shows
		// the defaults rather than an error page when no robot is connected.
		withGameService(new ThrowingGameService(), () -> {
			Fixtures.loadModels("openttd-data.yml");

			Assert.assertNull(OpenttdServer.getStatus(RobotConfiguration.HIGH_SPEED));
			Assert.assertNull(OpenttdServer.getStatus(RobotConfiguration.ARTIC));
			return null;
		});
	}

	@Test
	public void statusAnswersNullWhenTheScenarioRowIsMissing() throws Throwable {
		withGameService(new FixedGameService(probeGameInfo()), () -> {
			Fixtures.loadModels("openttd-data.yml");

			// PlayedGame.scenario_id and PlayedGameScore.playedGame_id have to go before the scenario itself.
			PlayedGameScore.deleteAll();
			PlayedGame.deleteAll();
			Scenario highSpeed = scenario(1L);
			highSpeed.delete();

			Assert.assertNull(OpenttdServer.getStatus(RobotConfiguration.HIGH_SPEED));
			// The game service still answers a GameInfo, so the null comes from the missing row alone.
			Assert.assertEquals("[Toyland Scenario, Artic Scenario, Desertic Scenario, Steam Scenario]",
					names(Scenario.findAll()));
			Assert.assertEquals("name=Probe Server revision=1.2.3 image=/images/openttd/toyland_on.gif"
					+ " goal=Reach the best company performance in 10 years to win the game."
					+ " startedDate=03/01/0001 currentDate=29/09/0002 width=512 height=1024"
					+ " companiesCount=3 clientsCount=7",
					describeServer(OpenttdServer.getStatus(RobotConfiguration.TOYLAND)));
			return null;
		});
	}

	@Test
	public void statusAnswersNullForAConfigurationNobodyResolved() throws Throwable {
		withGameService(new FixedGameService(probeGameInfo()), () -> {
			Fixtures.loadModels("openttd-data.yml");

			// robotConfiguration.scenarioId raises a NullPointerException, which the catch drops.
			Assert.assertNull(OpenttdServer.getStatus(null));
			return null;
		});
	}

	@Test
	public void thePrivateConstructorLeavesTheDefaultImageWithoutAScenario() throws Throwable {
		withGameService(new FixedGameService(probeGameInfo()), () -> {
			Fixtures.loadModels("openttd-data.yml");

			java.lang.reflect.Constructor<OpenttdServer> ctor =
					OpenttdServer.class.getDeclaredConstructor(GameInfo.class, Scenario.class);
			ctor.setAccessible(true);

			// image stays the field default "highSpeed_on.gif", because the scenario is only read when it is
			// not null; getStatus() never gets here, since it refuses a null scenario first.
			OpenttdServer withoutScenario = ctor.newInstance(probeGameInfo(), null);
			Assert.assertEquals("highSpeed_on.gif", withoutScenario.image);
			Assert.assertEquals("name=Probe Server revision=1.2.3 image=highSpeed_on.gif"
					+ " goal=Reach the best company performance in 10 years to win the game."
					+ " startedDate=03/01/0001 currentDate=29/09/0002 width=512 height=1024"
					+ " companiesCount=3 clientsCount=7", describeServer(withoutScenario));
			return null;
		});
	}

	@Test
	public void thePrivateConstructorRejectsANullGameInfo() throws Throwable {
		inTransactionVoid(() -> {
			Fixtures.loadModels("openttd-data.yml");

			java.lang.reflect.Constructor<OpenttdServer> ctor =
					OpenttdServer.class.getDeclaredConstructor(GameInfo.class, Scenario.class);
			ctor.setAccessible(true);

			// The guard is the first statement of the constructor, and getStatus() never passes a null
			// GameInfo to it, so this is the only way the branch runs. Reflection wraps whatever the
			// constructor raises, so the chain starts with the wrapper, whose own message is null, and the
			// guard itself is the second link.
			Assert.assertEquals("java.lang.reflect.InvocationTargetException: null | "
					+ "java.lang.IllegalArgumentException: GameInfo == null | ",
					causeChain(failure(() -> ctor.newInstance(null, scenario(2L)))));
			return null;
		});
	}

	/** The GameInfo the game service hands back is never kept: only its fields are copied. */
	@Test
	public void statusCarriesNoReferenceToTheGameInfoOrToTheScenario() throws Throwable {
		withGameService(new FixedGameService(probeGameInfo()), () -> {
			Fixtures.loadModels("openttd-data.yml");

			OpenttdServer status = OpenttdServer.getStatus(RobotConfiguration.HIGH_SPEED);
			Assert.assertEquals("java.lang.String", status.image.getClass().getName());
			Assert.assertEquals("java.lang.String", status.name.getClass().getName());
			Assert.assertEquals("java.lang.String", status.startedDate.getClass().getName());
			Assert.assertEquals("java.lang.String", status.currentDate.getClass().getName());
			Assert.assertEquals("models.openttd.OpenttdServer", status.getClass().getName());
			// The fields are the only thing the template reads, so nothing of the GameInfo or of the
			// Scenario entity survives; getFields() has no declared order, so the names are compared as a set.
			Assert.assertEquals("clientsCount,companiesCount,currentDate,goal,height,image,name,revision,startedDate,width",
					java.util.Arrays.stream(OpenttdServer.class.getFields()).map(f -> f.getName()).sorted()
							.reduce((a, b) -> a + "," + b).orElse("(none)"));
			return null;
		});
	}
}
