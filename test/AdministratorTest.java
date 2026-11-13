import org.junit.*;
import org.junit.After;
import org.junit.Before;

import play.mvc.Http.*;

public class AdministratorTest extends BaseFunctionalTest {
	
	@Before
	public void signInAsAdmin() {
		login("admin@domain.com", "secret");
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
		// forum.Topics.show() forbids hidden topics for everybody, admins included
		// (a moderator unhides it first).
		Response response = GET("/forums/1/topics/3");
		assertEquals(403, response.status.intValue());
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
	}
}