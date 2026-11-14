import org.junit.Assert;
import org.junit.Test;

import com.openttd.network.core.Configuration;

import openttd.OpenttdClientHandler;

/**
 * Coverage of app/openttd/OpenttdClientHandler, the singleton that keeps the one com.openttd.client.OpenttdClient
 * the game client bot uses. controllers.openttd.Openttd never calls it: the startup() and shutdown() calls of the
 * page are admin-only, and the block that asked this handler is commented out in app/openttd/OpenttdService.endGame(),
 * so the handler is reachable only through its own singleton.
 *
 *   - getInstance() answers the same object every time, and its private client field is null until
 *     startup() is called, which is why isRunning() answers false in every suite.
 *   - isRunning() is `client != null && client.isConnected()`, so an absent client and a client that lost
 *     its connection both answer false.
 *   - shutdown() asks the client to disconnect but never clears the field, so the handler keeps the object
 *     it shut down: a second shutdown() asks it again, and a client that reports connected afterwards is
 *     reported running although it was shut down. Only startup() replaces the field.
 *   - startup(port) is not exercised: it builds a com.openttd.network.core.Configuration with that port and
 *     opens a client socket with it, and the object it creates is left running in the singleton for the
 *     suites that follow. The fakes below stand in for it, injected through reflection and removed again.
 */
public class OpenttdClientHandlerTest extends BaseOpenttdTest {

	@Test
	public void getInstanceAnswersTheSameHandlerWithNoClientAtAll() throws Throwable {
		Assert.assertSame(OpenttdClientHandler.getInstance(), OpenttdClientHandler.getInstance());
		Assert.assertEquals("[client, instance]", declaredFieldNames(OpenttdClientHandler.class));
		Assert.assertEquals("null", String.valueOf(clientHeld()));
		Assert.assertEquals("false", String.valueOf(OpenttdClientHandler.getInstance().isRunning()));
	}

	@Test
	public void shutdownOfAnAbsentClientReturnsWithoutReachingAnyClient() throws Throwable {
		// The guard is `client != null`, so shutting the bot down before it was started is refused quietly.
		OpenttdClientHandler.getInstance().shutdown();
		Assert.assertEquals("null", String.valueOf(clientHeld()));
		Assert.assertEquals("false", String.valueOf(OpenttdClientHandler.getInstance().isRunning()));
	}

	@Test
	public void isRunningFollowsTheConnectionOfTheClientThatIsHeld() throws Throwable {
		holdClient(new FakeClient(new Configuration()));
		try {
			FakeClient client = (FakeClient) clientHeld();
			Assert.assertEquals("BaseOpenttdTest$FakeClient", client.getClass().getName());
			Assert.assertEquals("startupCalls=0 shutdownCalls=0", describeFakeClient(client));
			Assert.assertEquals("true", String.valueOf(OpenttdClientHandler.getInstance().isRunning()));

			client.dropConnection();
			Assert.assertEquals("false", String.valueOf(OpenttdClientHandler.getInstance().isRunning()));

			client.restoreConnection();
			Assert.assertEquals("true", String.valueOf(OpenttdClientHandler.getInstance().isRunning()));
		} finally {
			holdClient(null);
		}
		Assert.assertEquals("null", String.valueOf(clientHeld()));
		Assert.assertEquals("false", String.valueOf(OpenttdClientHandler.getInstance().isRunning()));
	}

	@Test
	public void shutdownAsksTheClientToDisconnectAndKeepsIt() throws Throwable {
		FakeClient client = new FakeClient(new Configuration());
		holdClient(client);
		try {
			OpenttdClientHandler.getInstance().shutdown();
			Assert.assertEquals("startupCalls=0 shutdownCalls=1", describeFakeClient(client));
			Assert.assertEquals("false", String.valueOf(OpenttdClientHandler.getInstance().isRunning()));

			// The field is not cleared, so shutdown() of a client that is already disconnected asks it again.
			Assert.assertEquals("true", String.valueOf(clientHeld() != null));
			OpenttdClientHandler.getInstance().shutdown();
			Assert.assertEquals("2", String.valueOf(client.shutdownCalls()));

			// A client that reports connected after being shut down is reported running: isConnected() is the
			// only thing isRunning() looks at, and shutdown() left the object in place.
			client.restoreConnection();
			Assert.assertEquals("true", String.valueOf(OpenttdClientHandler.getInstance().isRunning()));
		} finally {
			holdClient(null);
		}
		Assert.assertEquals("null", String.valueOf(clientHeld()));
	}

	/** startupCalls and shutdownCalls of a fake client, the way describeFakeAdmin() prints a robot. */
	private static String describeFakeClient(FakeClient client) {
		return "startupCalls=" + client.startupCalls() + " shutdownCalls=" + client.shutdownCalls();
	}
}
