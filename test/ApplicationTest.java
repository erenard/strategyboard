import org.junit.*;
import play.test.*;
import play.mvc.Http.*;

public class ApplicationTest extends BaseFunctionalTest {

	@Test
	public void testThatIndexPageWorks() {
		Response response = GET("/");
		assertIsOk(response);
		assertContentType("text/html", response);
		assertCharset(play.Play.defaultWebEncoding, response);
	}

	@Test
	public void testAnonymousAccessToHomePage() {
		
	}
}