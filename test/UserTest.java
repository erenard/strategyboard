import org.junit.Before;
import org.junit.Test;

import play.mvc.Http.Response;

public class UserTest extends BaseFunctionalTest {
	
	@Before
	public void signInAsUser() {
		login("bob@domain.com", "secret");
	}

	@Test
	public void canAccessToHomePage() {
		Response response = GET("/");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
	}

	@Test
	public void canAccessToForums() {
		Response response = GET("/forums");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
	}

	@Test
	public void canAccessToAForum() {
		Response response = GET("/forums/1");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
	}

	@Test
	public void canAccessToATopic() {
		Response response = GET("/forums/1/topics/1");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
	}

	@Test
	public void canAccessToALockedTopic() {
		Response response = GET("/forums/1/topics/2");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
	}

	@Test
	public void cannotAccessToAHiddenTopic() {
		Response response = GET("/forums/1/topics/3");
		assertEquals(response.status.longValue(), 403);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
	}
}