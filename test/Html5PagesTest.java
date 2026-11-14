import org.junit.*;

import play.mvc.Http.Response;
import play.mvc.results.NotFound;

/**
 * Coverage of app/views/Html5/index.html, app/views/Html5/life.html and app/views/Html5/mandelbrot.html.
 *
 * Html5 has no route of its own: conf/routes resolves it through the catch-all pair at the end of the
 * file, * /{controller} -> {controller}.index and * /{controller}/{action} -> {controller}.{action}, so
 * /html5 reaches Html5.index and /html5/life reaches Html5.life. Html5 extends Application, so
 * Application.globals() supplies connectedUser and pageSize to main.html, and nobody logs in here: the
 * anonymous visitor gets the Login and Signup items of the nav bar.
 *
 * The active class of a nav item is a ternary on request.controller, so each of the three pages asserts
 * its own item is active, which is the only way each of the three comparisons becomes true, and asserts
 * a second item is inactive.
 *
 * /html5/unknownAction resolves through the catch-all route to an action Html5 does not have, so
 * ActionInvoker raises play.mvc.results.NotFound, which play.mvc/results/NotFound.java turns into a 404;
 * FunctionalTest.makeRequest rethrows it instead of returning a Response, so the case catches it.
 */
public class Html5PagesTest extends BaseForumViewTest {

	private static final String ANONYMOUS_NAV = "<li class=\"\"><a href=\"/login\">Login</a></li> <li><a href=\"/signup\">Signup</a></li>";

	private String page(String path) throws Throwable {
		clearCookies();
		Response response = GET(path);
		assertStatus(200, response);
		return markup(response);
	}

	@Test
	public void indexListsTheTwoDemosAndMarksTheHtml5ItemActive() throws Throwable {
		String body = page("/html5");

		assertHeader(body, "HTML5 and WebGL", "Demos of these two amazing technologies");
		assertContains("<li class=\"active\"><a href=\"/html5\">HTML5</a></li>", body);
		// The other items are inactive: request.controller is Html5, not Application or forum.Users
		assertContains("<li class=\"\"><a href=\"/\">News</a></li>", body);
		assertContains("<li class=\"\"><a href=\"/users\">Members</a></li>", body);
		assertContains(ANONYMOUS_NAV, body);
		assertNotContains("Administration", body);

		assertContains("<div class=\"well well-sm\">", body);
		assertContains("<a class=\"pull-left\" href=\"/html5/life\"> <img class=\"media-object\" src=\"/public/html5/life.png\" alt=\"canvas\" width=\"133\" height=\"100\"> </a>", body);
		assertContains("<h3 class=\"media-heading\">Game of life</h3>", body);
		assertContains("<a class=\"pull-left\" href=\"/html5/mandelbrot\"> <img class=\"media-object\" src=\"/public/html5/mandelbrot.png\" alt=\"webgl\" width=\"133\" height=\"100\"> </a>", body);
		assertContains("<h3 class=\"media-heading\">Mandelbrot fractal</h3>", body);
	}

	@Test
	public void lifePageEmbedsTheCanvasDemo() throws Throwable {
		String body = page("/html5/life");

		assertHeader(body, "Conway's game of life", "Rendered with HTML5 canvas");
		assertContains("<li class=\"active\"><a href=\"/html5\">HTML5</a></li>", body);
		assertContains("<iframe id=\"lifeJs\" src=\"https://erenard.github.io/life.js\"></iframe>", body);
		assertContains(ANONYMOUS_NAV, body);
		// mandelbrot.html is not rendered here, so its iframe is absent
		assertNotContains("<iframe id=\"mandelbrot\"", body);
	}

	@Test
	public void mandelbrotPageEmbedsTheWebGLDemo() throws Throwable {
		String body = page("/html5/mandelbrot");

		assertHeader(body, "Mandelbrot fractal", "Rendered with WebGL shaders");
		assertContains("<li class=\"active\"><a href=\"/html5\">HTML5</a></li>", body);
		assertContains("<iframe id=\"mandelbrot\" src=\"https://erenard.github.io/mandelbrot\"></iframe>", body);
		assertContains(ANONYMOUS_NAV, body);
		assertNotContains("<iframe id=\"lifeJs\"", body);
	}

	@Test
	public void anUnknownHtml5ActionIsNotFound() throws Throwable {
		// The catch-all route sends /html5/whatever to Html5.whatever, which does not exist
		clearCookies();
		try {
			GET("/html5/unknownAction");
			Assert.fail("expected play.mvc.results.NotFound");
		} catch (NotFound notFound) {
			Assert.assertEquals("html5.unknownAction action not found", notFound.getMessage());
		}
	}

	@Test
	public void theHtml5StaticFilesAreServedFromThePublicDir() throws Throwable {
		// main.html and the demo pages link /public/..., served by staticDir:public
		clearCookies();
		assertStatus(200, GET("/public/stylesheets/bootstrap.min.css"));
		assertStatus(200, GET("/public/html5/life.png"));
		// A file that is not there is answered the same way: staticDir does not 404, it redirects to the
		// static file handler, which leaves an empty body
		assertStatus(200, GET("/public/html5/noSuchImage.png"));
		Assert.assertEquals(0, getContent(GET("/public/html5/noSuchImage.png")).length());
	}
}
