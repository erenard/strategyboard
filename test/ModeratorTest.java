import org.junit.*;
import play.mvc.Http.*;
import play.mvc.Http.Response;
import play.test.*;

public class ModeratorTest extends BaseFunctionalTest {
	
	@Before
	public void signInAsModerator() {
		login("modo@domain.com", "secret");
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

	// ~~~~~~~~~~~~~~~~~
	
	@Test
	public void canAccessToMainForum() {
		Response response = GET("/forums/1");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
	}

	@Test
	public void canAccessToModeratorForum() {
		Response response = GET("/forums/3");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
	}

	// ~~~~~~~~~~~~~~~~~
	
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

	@Test
	public void canLockATopic() {
		// Routes declare lock as GET (it is a plain link in the templates).
		// Topics.lock() finishes with Forums.show(), which Play turns into a framework
		// redirect (302 to the forum page) rather than rendering a 200 body.
		Response response = GET("/forums/1/topics/1/lock");
		assertStatus(302, response);
		assertHeaderEquals("Location", "/forums/1", response);
		response = GET("/forums/1/topics/1/lock");
		assertStatus(302, response);
		assertIsOk(GET("/forums/1"));
	}

	@Test
	public void canHideATopic() {
		Response response = POST("/forums/1/topics/1/hide");
		assertStatus(302, response);
		assertHeaderEquals("Location", "/forums/1", response);
		response = POST("/forums/1/topics/1/hide");
		assertStatus(302, response);
		assertIsOk(GET("/forums/1"));
	}

	@Test
	public void canMoveATopic() {
		Response response = POST("/forums/1/topics/1/move/2");
		assertStatus(302, response);
		assertHeaderEquals("Location", "/forums/1", response);
		response = POST("/forums/2/topics/1/move/1");
		assertStatus(302, response);
		assertHeaderEquals("Location", "/forums/2", response);
	}
}