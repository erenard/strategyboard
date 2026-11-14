import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.openttd.network.core.Configuration;

import models.openttd.RobotConfiguration;

import openttd.OpenttdAdminHandler;

/**
 * Coverage of app/openttd/OpenttdAdminHandler, the singleton controllers.openttd.Openttd calls to start
 * and stop the admin robots, and the constructor that wires app/openttd/OpenttdService into
 * com.openttd.robot.ExternalServices.
 *
 *   - getInstance() answers the same object every time, and the private robots map it keeps is empty
 *     until something starts a robot, so every isRunning() answer of app/views/openttd/index.html is
 *     false before a startup. The suites inject fakes into that map through reflection and remove them
 *     again, so the page suite never sees a robot left behind.
 *   - isRunning() is `robot != null && robot.isConnected()`: a scenario id the map does not hold answers
 *     false, and so does null, because HashMap.get(null) answers null rather than raising.
 *   - restart() guards on the entry, not on the connection: it sends "restart" through getSend() whenever
 *     a robot is held, whether or not it is connected, and sends nothing when getSend() answers null.
 *   - shutdown() asks the robot to disconnect and keeps the entry, so isRunning() turns false while the
 *     map still holds the robot. An unknown scenario id is refused by the same guard as restart(), which
 *     is why shutdown() and restart() of a scenario nobody started return normally.
 *   - startup() is the only action that can raise: RobotConfiguration.fromScenarioId() answers null for a
 *     scenario id outside 1..5, and startup() reads configuration.adminPort on it before anything is put
 *     in the map, so the failure leaves the map untouched.
 *   - startup() of a scenario the enum does cover is not exercised here: it opens an admin socket on the
 *     port RobotConfiguration names (3977..4377), and two of its consequences cannot be asserted
 *     hermetically. com.openttd.admin.OpenttdAdmin.isConnected() answers true as soon as its network
 *     thread is alive, so a robot of a server that is not listening is reported running, and
 *     com.openttd.network.admin.NetworkAdmin.shutdown() dereferences a sender that the failed connect
 *     never created. Both were observed by a scratch probe with nothing listening on 4377; neither is a
 *     value a suite can pin without a live server.
 */
public class OpenttdAdminHandlerTest extends BaseOpenttdTest {

	// ~~~~~~~~~~~~ the singleton and the services it wires

	@Test
	public void getInstanceAnswersTheSameHandlerEveryTime() throws Throwable {
		Assert.assertSame(OpenttdAdminHandler.getInstance(), OpenttdAdminHandler.getInstance());
		Assert.assertEquals("[instance, robots]", declaredFieldNames(OpenttdAdminHandler.class));
		Assert.assertEquals("[]", robotsOfHandler().keySet().toString());
		Assert.assertEquals("{}", robotsOfHandler().toString());
	}

	@Test
	public void theConstructorWiresOneServiceForBothExternalInterfaces() throws Throwable {
		// The constructor of the handler builds one OpenttdService and registers it twice: the game service
		// the rules read for exposeGame(), and the user service they read for identifyUser().
		Assert.assertSame(wiredGameService(), wiredUserService());
		Assert.assertEquals("openttd.OpenttdService", wiredGameService().getClass().getName());
		Assert.assertEquals("openttd.OpenttdService", wiredUserService().getClass().getName());

		// That one instance implements both interfaces com.openttd.robot.ExternalServices exposes.
		Assert.assertEquals("true", String.valueOf(wiredGameService()
				 instanceof com.openttd.robot.ExternalServices.ExternalGameService));
		Assert.assertEquals("true", String.valueOf(wiredUserService()
				 instanceof com.openttd.robot.ExternalServices.ExternalUserService));
	}

	// ~~~~~~~~~~~~ isRunning, restart and shutdown of a map that holds nothing

	@Test
	public void noRobotIsRunningBeforeAnyStartup() throws Throwable {
		Assert.assertEquals("false", String.valueOf(OpenttdAdminHandler.getInstance().isRunning(null)));
		Assert.assertEquals("false", String.valueOf(OpenttdAdminHandler.getInstance().isRunning(6L)));
		Assert.assertEquals("false", String.valueOf(OpenttdAdminHandler.getInstance()
				.isRunning(RobotConfiguration.HIGH_SPEED.scenarioId)));

		// Both actions guard on the entry, so a scenario nobody started is refused without an exception.
		OpenttdAdminHandler.getInstance().restart(6L);
		OpenttdAdminHandler.getInstance().shutdown(6L);
		Assert.assertEquals("[]", robotsOfHandler().keySet().toString());
	}

	@Test
	public void startupOfAnUnknownScenarioIdRaisesBeforeTheMapIsFilled() throws Throwable {
		// RobotConfiguration.fromScenarioId() answers null for a scenario id the fixtures do not hold, and
		// startup() reads adminPort on it: the message names the field and the line of the read.
		Assert.assertEquals("java.lang.NullPointerException: Try to read adminPort on null object"
				+ " models.openttd.RobotConfiguration (openttd.OpenttdAdminHandler.startup, line 45) | ",
				causeChain(failure(() -> {
					OpenttdAdminHandler.getInstance().startup(6L);
					return "returned";
				})));

		// The NPE happens before robots.put(), so nothing is held for the scenario and nothing is running.
		Assert.assertEquals("[]", robotsOfHandler().keySet().toString());
		Assert.assertEquals("false", String.valueOf(OpenttdAdminHandler.getInstance().isRunning(6L)));

		// fromScenarioId() matches the scenarioId of a constant, never its adminPort: 4377 is TOYLAND's
		// port, so startup() of it raises the same way rather than starting the toyland robot.
		Assert.assertEquals("java.lang.NullPointerException: Try to read adminPort on null object"
				+ " models.openttd.RobotConfiguration (openttd.OpenttdAdminHandler.startup, line 45) | ",
				causeChain(failure(() -> {
					OpenttdAdminHandler.getInstance().startup(Long.valueOf(RobotConfiguration.TOYLAND.adminPort));
					return "returned";
				})));
		Assert.assertEquals("[]", robotsOfHandler().keySet().toString());
	}

	// ~~~~~~~~~~~~ the three actions against an injected robot

	@Test
	public void isRunningFollowsTheConnectionOfTheRobotTheMapHolds() throws Throwable {
		FakeAdmin robot = new FakeAdmin(new Configuration());
		putRobot(RobotConfiguration.ARTIC.scenarioId, robot);
		try {
			Assert.assertEquals("true", String.valueOf(OpenttdAdminHandler.getInstance()
					.isRunning(RobotConfiguration.ARTIC.scenarioId)));

			// The other scenarios are still absent, so their answers stay false whatever this robot does.
			Assert.assertEquals("false", String.valueOf(OpenttdAdminHandler.getInstance()
					.isRunning(RobotConfiguration.STEAM.scenarioId)));

			robot.dropConnection();
			Assert.assertEquals("false", String.valueOf(OpenttdAdminHandler.getInstance()
					.isRunning(RobotConfiguration.ARTIC.scenarioId)));

			// isRunning() asks isConnected(), so a robot that reconnects is running again: neither restart()
			// nor shutdown() ever removes the entry.
			robot.restoreConnection();
			Assert.assertEquals("true", String.valueOf(OpenttdAdminHandler.getInstance()
					.isRunning(RobotConfiguration.ARTIC.scenarioId)));
		} finally {
			removeRobot(RobotConfiguration.ARTIC.scenarioId);
		}
		assertNoRobotsLeftBehind();
	}

	@Test
	public void restartSendsRestartThroughTheSenderOfTheRobot() throws Throwable {
		FakeAdmin robot = new FakeAdmin(new Configuration());
		putRobot(1L, robot);
		try {
			OpenttdAdminHandler.getInstance().restart(1L);
			Assert.assertEquals("[restart]", robot.commands().toString());
			Assert.assertEquals("startupCalls=0 shutdownCalls=0 connected=true commands=[restart]",
					describeFakeAdmin(robot));

			// The guard is on the entry, not on isConnected(), so a disconnected robot is still told to restart.
			robot.dropConnection();
			OpenttdAdminHandler.getInstance().restart(1L);
			Assert.assertEquals("[restart, restart]", robot.commands().toString());
			Assert.assertEquals("false", String.valueOf(OpenttdAdminHandler.getInstance().isRunning(1L)));

			// getSend() answering null is the second guard: nothing is sent, and nothing raises. The
			// commands live in the sender, so they are captured before it is removed - FakeAdmin.commands()
			// of a robot without a sender answers an empty list, which is what pins the branch that sends
			// nothing rather than one that sends a third restart.
			List<String> sent = robot.commands();
			Assert.assertEquals("[restart, restart]", sent.toString());

			robot.removeSender();
			robot.restoreConnection();
			OpenttdAdminHandler.getInstance().restart(1L);
			Assert.assertEquals("[restart, restart]", sent.toString());
			Assert.assertEquals("[]", robot.commands().toString());
			Assert.assertEquals("startupCalls=0 shutdownCalls=0 connected=true commands=[]",
					describeFakeAdmin(robot));
			Assert.assertEquals("true", String.valueOf(OpenttdAdminHandler.getInstance().isRunning(1L)));
		} finally {
			removeRobot(1L);
		}
		assertNoRobotsLeftBehind();
	}

	@Test
	public void shutdownAsksTheRobotToDisconnectAndKeepsTheEntry() throws Throwable {
		FakeAdmin robot = new FakeAdmin(new Configuration());
		putRobot(2L, robot);
		try {
			OpenttdAdminHandler.getInstance().shutdown(2L);
			Assert.assertEquals("startupCalls=0 shutdownCalls=1 connected=false commands=[]", describeFakeAdmin(robot));
			Assert.assertEquals("false", String.valueOf(OpenttdAdminHandler.getInstance().isRunning(2L)));

			// The map still holds the robot, so shutdown() of the same scenario again asks it a second time.
			OpenttdAdminHandler.getInstance().shutdown(2L);
			Assert.assertEquals("2", String.valueOf(robot.shutdownCalls()));
			Assert.assertEquals("[2]", robotsOfHandler().keySet().toString());

			// A robot that is running is shut down by the same call the page asks for: /openttd/shutdown.
			robot.restoreConnection();
			Assert.assertEquals("true", String.valueOf(OpenttdAdminHandler.getInstance().isRunning(2L)));
			OpenttdAdminHandler.getInstance().shutdown(2L);
			Assert.assertEquals("false", String.valueOf(OpenttdAdminHandler.getInstance().isRunning(2L)));
			Assert.assertEquals("3", String.valueOf(robot.shutdownCalls()));
		} finally {
			removeRobot(2L);
		}
		assertNoRobotsLeftBehind();
	}

	@Test
	public void theRobotsMapIsSharedByEveryActionOfTheSingleton() throws Throwable {
		FakeAdmin first = new FakeAdmin(new Configuration());
		FakeAdmin second = new FakeAdmin(new Configuration());
		putRobot(1L, first);
		putRobot(2L, second);
		try {
			Assert.assertEquals("[1, 2]", robotsOfHandler().keySet().toString());
			Assert.assertEquals("BaseOpenttdTest$FakeAdmin", first.getClass().getName());
			Assert.assertSame(first, robotsOfHandler().get(1L));
			Assert.assertSame(second, robotsOfHandler().get(2L));

			// One restart per scenario: the map is keyed by scenarioId, so a robot is restarted alone.
			OpenttdAdminHandler.getInstance().restart(2L);
			Assert.assertEquals("[]", first.commands().toString());
			Assert.assertEquals("[restart]", second.commands().toString());
		} finally {
			removeRobot(1L);
			removeRobot(2L);
		}
		assertNoRobotsLeftBehind();
	}
}
