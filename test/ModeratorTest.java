import org.junit.*;
import play.mvc.Http.*;
import play.mvc.Http.Response;
import play.test.*;

public class ModeratorTest extends FunctionalTest {
	
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
		Response response = POST("/forums/1/topics/1/lock");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
		response = POST("/forums/1/topics/1/lock");
		assertIsOk(response);
	}

	@Test
	public void canHideATopic() {
		Response response = POST("/forums/1/topics/1/hide");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
		response = POST("/forums/1/topics/1/hide");
		assertIsOk(response);
	}

	@Test
	public void canMoveATopic() {
		Response response = POST("/forums/1/topics/1/move/2");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
		response = POST("/forums/2/topics/1/move/1");
		assertIsOk(response);
	}
}