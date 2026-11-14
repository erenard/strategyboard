import org.junit.Test;

import play.test.UnitTest;

import models.openttd.RobotConfiguration;

/**
 * Coverage of models.openttd.RobotConfiguration, the enum that ties a scenario row to the admin port the
 * robot connects to (app/openttd/Openttd and the robot director both resolve it by scenario id).
 *
 *   - Five constants, each with a final adminPort and a final scenarioId. values() keeps the order the enum
 *     declares them, which is alphabetical here, so the loop of OpenttdServer.getStatus() over values() has
 *     a predictable order.
 *   - fromScenarioId() matches the scenarioId of the constant, never its adminPort: 4377 is the admin port of
 *     TOYLAND and answers null, which is how a port handed to the lookup instead of a scenario id is refused.
 *   - It answers null for a scenario id the fixtures do not hold (6, 0), so a caller that forgets the check
 *     reaches a NullPointerException inside OpenttdServer.getStatus(), where the exception is caught and the
 *     status becomes null.
 *   - The constants are singletons, so fromScenarioId() returns the same instance the enum constant is, which
 *     is what lets the callers compare with == rather than equals().
 */
public class RobotConfigurationTest extends UnitTest {

	@Test
	public void theFiveConstantsCarryTheirPortAndTheirScenarioId() {
		assertEquals(3977, RobotConfiguration.ARTIC.adminPort);
		assertEquals(3L, RobotConfiguration.ARTIC.scenarioId);
		assertEquals(4077, RobotConfiguration.DESERTIC.adminPort);
		assertEquals(4L, RobotConfiguration.DESERTIC.scenarioId);
		assertEquals(4177, RobotConfiguration.HIGH_SPEED.adminPort);
		assertEquals(1L, RobotConfiguration.HIGH_SPEED.scenarioId);
		assertEquals(4277, RobotConfiguration.STEAM.adminPort);
		assertEquals(5L, RobotConfiguration.STEAM.scenarioId);
		assertEquals(4377, RobotConfiguration.TOYLAND.adminPort);
		assertEquals(2L, RobotConfiguration.TOYLAND.scenarioId);
	}

	@Test
	public void valuesAreListedInTheOrderTheEnumDeclaresThem() {
		assertEquals(5, RobotConfiguration.values().length);
		assertEquals("[ARTIC, DESERTIC, HIGH_SPEED, STEAM, TOYLAND]",
				java.util.Arrays.toString(RobotConfiguration.values()));
		assertEquals(0, RobotConfiguration.ARTIC.ordinal());
		assertEquals(1, RobotConfiguration.DESERTIC.ordinal());
		assertEquals(2, RobotConfiguration.HIGH_SPEED.ordinal());
		assertEquals(3, RobotConfiguration.STEAM.ordinal());
		assertEquals(4, RobotConfiguration.TOYLAND.ordinal());
		assertEquals("HIGH_SPEED", RobotConfiguration.HIGH_SPEED.name());
	}

	@Test
	public void fromScenarioIdResolvesEveryScenarioOfTheLeaderboard() {
		assertEquals(RobotConfiguration.HIGH_SPEED, RobotConfiguration.fromScenarioId(1L));
		assertEquals(RobotConfiguration.TOYLAND, RobotConfiguration.fromScenarioId(2L));
		assertEquals(RobotConfiguration.ARTIC, RobotConfiguration.fromScenarioId(3L));
		assertEquals(RobotConfiguration.DESERTIC, RobotConfiguration.fromScenarioId(4L));
		assertEquals(RobotConfiguration.STEAM, RobotConfiguration.fromScenarioId(5L));
	}

	@Test
	public void fromScenarioIdAnswersNullForAnyOtherValue() {
		// 6 is the id of a scenario the fixtures never insert; 0 is what a missing id binds to.
		assertNull(RobotConfiguration.fromScenarioId(6L));
		assertNull(RobotConfiguration.fromScenarioId(0L));
		assertNull(RobotConfiguration.fromScenarioId(-1L));
		assertNull(RobotConfiguration.fromScenarioId(1000L));
		// An admin port is not a scenario id: 4377 belongs to TOYLAND as its port, and finds nothing.
		assertNull(RobotConfiguration.fromScenarioId(4377L));
		assertNull(RobotConfiguration.fromScenarioId(3977L));
	}

	@Test
	public void theScenarioIdsAndThePortsNeverCollide() {
		for (RobotConfiguration left : RobotConfiguration.values()) {
			for (RobotConfiguration right : RobotConfiguration.values()) {
				if (left != right) {
					assertTrue("same scenario id: " + left + " and " + right, left.scenarioId != right.scenarioId);
					assertTrue("same admin port: " + left + " and " + right, left.adminPort != right.adminPort);
					// No port is a scenario id either, so a confused caller cannot hit the wrong constant.
					assertTrue(left.adminPort + " is the scenario id of " + right, left.adminPort != right.scenarioId);
				}
			}
		}
		// The enum constants are singletons, so the lookup answers the very same instance.
		assertTrue(RobotConfiguration.fromScenarioId(1L) == RobotConfiguration.HIGH_SPEED);
		assertTrue(RobotConfiguration.valueOf("TOYLAND") == RobotConfiguration.TOYLAND);
	}
}
