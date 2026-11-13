import java.util.HashMap;
import java.util.Map;

import org.junit.Before;

import play.db.jpa.JPA;
import play.mvc.Http.Response;
import play.test.Fixtures;
import play.test.FunctionalTest;

/**
 * Setup shared by the functional tests.
 *
 * Play does not load any data by itself, so every test starts from an empty database
 * seeded with test/data.yml. Sessions live in signed cookies, and ActionInvoker
 * rebuilds the session from the request cookie on every call, so a login has to go
 * through POST /login: writing to Session.current() in a @Before method is not seen
 * by the requests that follow.
 */
public abstract class BaseFunctionalTest extends FunctionalTest {

	@Before
	public void loadFixtures() throws Throwable {
		// Fixtures need a JPA transaction in progress: outside one, loadModels() cannot
		// write the rows, so every test starts with an empty database.
		JPA.withTransaction(JPA.DEFAULT, false, () -> {
			Fixtures.deleteDatabase();
			Fixtures.loadModels("data.yml");
			return null;
		});
	}

	/** Logs in through the secure module, which stores the session in a cookie. */
	protected void login(String email, String password) {
		Map<String, String> credentials = new HashMap<String, String>();
		credentials.put("username", email);
		credentials.put("password", password);
		Response response = POST("/login", credentials);
		assertStatus(302, response);
	}

}
