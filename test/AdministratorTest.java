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
	public void canAccessToAHiddenTopic() {
		// Topics.show() only forbids a hidden topic below Permission.moderate; an administrator is at
		// Permission.administrate, so the forum list link it is given works.
		Response response = GET("/forums/1/topics/3");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
	}
}