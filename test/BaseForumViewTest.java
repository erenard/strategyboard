import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.data.validation.Validation;
import play.db.jpa.JPA;
import play.libs.F;
import play.mvc.CookieDataCodec;
import play.mvc.Http;
import play.mvc.Http.Response;
import play.mvc.Scope;
import play.templates.TemplateLoader;

import controllers.Application;

/**
 * Setup shared by the forum view suites (forum/UsersPageTest, forum/TopicsPageTest, forum/ForumsPageTest,
 * forum/MessagesPageTest) and by the suites that drive one controller action directly: forum/Attachments.download()
 * in AttachmentsDownloadTest, forum/Topics.delete() in TopicsDeleteTest, controllers.Secure.login()/logout() in
 * LogoutFlowTest and openttd.Scores.exportAsCSV() in ScoresExportCsvTest. The CRUD suites get every helper below
 * through BaseCrudTest, which extends this class.
 *
 * Assertions are made against whitespace-collapsed markup, so every string below is exactly what
 * Play produced; each conditional of the templates under test is exercised in both directions.
 *
 * Two things have to be respected by every case:
 *   - clearCookies() at the start of the case. FunctionalTest.savedCookies is a static field, so a
 *     validation error kept in the PLAY_ERRORS cookie by an earlier request is restored by
 *     ValidationPlugin.restore() and changes what a later form renders.
 *   - renderView() installs its own request/session/flash/validation thread-locals and must put the
 *     outer ones back afterwards: Validation.current() is what Binder.addValidationError() writes
 *     into while fixtures are loaded, and dropping it makes the next loadFixtures() fail with
 *     "Cannot read field \"errors\" because ... Validation.current() is null".
 */
public abstract class BaseForumViewTest extends BaseFunctionalTest {

	private static Integer originalPageSize;

	// ~~~~~~~~~~~~ forum.pageSize

	/** Application.pageSize is protected static, so pagination branches are driven by reflection. */
	protected static void setPageSize(int value) throws Throwable {
		Field field = Application.class.getDeclaredField("pageSize");
		field.setAccessible(true);
		if (originalPageSize == null) {
			originalPageSize = (Integer) field.get(null);
		}
		field.set(null, Integer.valueOf(value));
	}

	protected static void restorePageSize() throws Throwable {
		if (originalPageSize == null) {
			return;
		}
		Field field = Application.class.getDeclaredField("pageSize");
		field.setAccessible(true);
		field.set(null, originalPageSize);
		originalPageSize = null;
	}

	protected static Integer pageSize() throws Throwable {
		Field field = Application.class.getDeclaredField("pageSize");
		field.setAccessible(true);
		return (Integer) field.get(null);
	}

	// ~~~~~~~~~~~~ request helpers

	/** A POST body, written as key, value, key, value... */
	protected static Map<String, String> form(Object... entries) {
		Map<String, String> body = new HashMap<String, String>();
		for (int i = 0; i < entries.length; i += 2) {
			body.put((String) entries[i], (String) entries[i + 1]);
		}
		return body;
	}

	/**
	 * A GET request carrying a referer header. FunctionalTest.newRequest() builds a request with no
	 * header at all, and Application.backToPreviousPage() reads the referer, so a request that wants the
	 * referer branch of that helper has to install it here.
	 */
	protected static Response GETFrom(String referer, Object url) {
		Http.Request request = newRequest();
		request.headers.put("referer", new Http.Header("referer", referer));
		return GET(request, url);
	}

	/** The same, for a form POST. */
	protected static Response POSTFrom(String referer, Object url, Map<String, String> parameters) {
		Http.Request request = newRequest();
		request.headers.put("referer", new Http.Header("referer", referer));
		return POST(request, url, parameters, new HashMap<String, java.io.File>());
	}

	protected static String location(Response response) {
		Http.Header header = response.headers.get("Location");
		if (header == null) {
			header = response.headers.get("location");
		}
		return header == null ? null : header.values.get(0);
	}

	protected static void assertLocation(Response response, String expected) {
		assertTrue("expected Location " + expected + ", got " + location(response), expected.equals(location(response)));
	}

	protected static Map<String, String> flashOf(Response response) throws Throwable {
		return decodeCookie(response, "PLAY_FLASH");
	}

	protected static Map<String, String> errorsOf(Response response) throws Throwable {
		return decodeCookie(response, "PLAY_ERRORS");
	}

	/**
	 * The message kept for one field by validation.keep(). The PLAY_ERRORS cookie stores
	 * "key:message\u0001variable...", where the variables are the arguments of the constraint (the field
	 * an @Equals compares against), so only the part before the first \u0001 is the message shown by #{error}.
	 */
	protected static String errorMessage(Response response, String key) throws Throwable {
		String value = errorsOf(response).get(key);
		return value == null ? null : value.split("\u0001", -1)[0];
	}

	protected static String sessionUsername(Response response) throws Throwable {
		Http.Cookie cookie = response.cookies.get("PLAY_SESSION");
		if (cookie == null) {
			return null;
		}
		// The session cookie is "<signature>-<url encoded key=value pairs>", which CookieDataCodec
		// cannot parse because of the signature prefix.
		String value = java.net.URLDecoder.decode(cookie.value, "UTF-8");
		int dash = value.indexOf('-');
		String data = dash < 0 ? "" : value.substring(dash + 1);
		for (String pair : data.split("&")) {
			String[] entry = pair.split("=", 2);
			if (entry.length == 2 && "username".equals(entry[0])) {
				return entry[1];
			}
		}
		return null;
	}

	private static Map<String, String> decodeCookie(Response response, String name) throws Throwable {
		Http.Cookie cookie = response.cookies.get(name);
		if (cookie == null) {
			return new HashMap<String, String>();
		}
		Map<String, String> decoded = new HashMap<String, String>();
		CookieDataCodec.decode(decoded, cookie.value);
		return decoded;
	}

	// ~~~~~~~~~~~~ markup helpers

	/** The body with every run of whitespace collapsed, which is what the assertions below match. */
	protected static String markup(Response response) {
		return getContent(response).replaceAll("\\s+", " ");
	}

	/**
	 * The bytes Play wrote to response.out, un-decoded. forum/Attachments.download() pads its 64 KB read
	 * buffer with NULs, which getContent() turns into characters that no assertion can pin, so the length
	 * and the leading bytes are checked here.
	 */
	protected static byte[] rawBody(Response response) {
		return response.out.toByteArray();
	}

	/** A response header, looked up case-insensitively, or null. */
	protected static String header(Response response, String name) {
		Http.Header h = response.headers.get(name);
		if (h == null) {
			h = response.headers.get(name.toLowerCase());
		}
		return h == null || h.values.isEmpty() ? null : h.values.get(0);
	}

	/** A response cookie, or null. Used by the remember-me and logout branches of controllers.Secure. */
	protected static Http.Cookie cookie(Response response, String name) {
		return response.cookies.get(name);
	}

	protected static void assertHeader(Response response, String name, String value) {
		assertTrue("expected header " + name + "=" + value + ", got " + header(response, name),
				value.equals(header(response, name)));
	}

	protected static void assertContains(String needle, String body) {
		assertTrue("expected to find '" + needle + "'", body.contains(needle));
	}

	protected static void assertNotContains(String needle, String body) {
		assertFalse("expected not to find '" + needle + "'", body.contains(needle));
	}

	protected static void assertMatches(String regex, String text) {
		assertTrue("expected to match '" + regex + "'", Pattern.compile(regex).matcher(text).find());
	}

	protected static int count(String needle, String body) {
		int n = 0;
		for (int i = body.indexOf(needle); i >= 0; i = body.indexOf(needle, i + needle.length())) {
			n++;
		}
		return n;
	}

	/** <div class="page-header"> <h1>title</h1> <p>subtitle</p> </div>, i.e. the #{set} tags. */
	protected static void assertHeader(String body, String title, String subtitle) {
		assertContains("<div class=\"page-header\"> <h1>" + title + "</h1> <p>" + subtitle + "</p> </div>", body);
	}

	protected static void assertSuccessAlert(String text, String body) {
		assertContains("<div class=\"alert alert-success\">" + text + "</div>", body);
	}

	protected static void assertErrorAlert(String text, String body) {
		assertContains("<div class=\"alert alert-danger\">" + text + "</div>", body);
	}

	/**
	 * A form-group in the state #{ifError} produced, together with the text #{error} appended to its
	 * label. error == null asserts the false branch, where the label keeps its trailing space.
	 *
	 * @param field the value of the label's for attribute
	 * @param labelInner the label text before #{error}, ending with the space the template writes
	 */
	protected static void assertField(String body, String field, String labelInner, String error) {
		assertContains(formGroup(error) + " <label class=\"control-label\" for=\"" + field + "\">" + labelInner
				+ (error == null ? "" : error) + "</label>", body);
	}

	/** Same as assertField, for the labels written without a for attribute. */
	protected static void assertLabellessField(String body, String labelInner, String error) {
		assertContains(formGroup(error) + " <label class=\"control-label\">" + labelInner
				+ (error == null ? "" : error) + "</label>", body);
	}

	private static String formGroup(String error) {
		return error == null ? "<div class=\"form-group \">" : "<div class=\"form-group has-error\">";
	}

	/**
	 * Data rows of the tables on the page, whitespace collapsed, in document order. Rows inside a tbody
	 * are taken as they are; users/index.html and forum/Forums/show.html have no tbody, so their rows are
	 * read from the whole document and the thead rows, which hold <th> cells, are skipped.
	 */
	protected static List<String> tableRows(String body) {
		List<String> rows = new ArrayList<String>();
		Matcher tbody = Pattern.compile("<tbody>(.*?)</tbody>").matcher(body);
		List<String> scanned = new ArrayList<String>();
		while (tbody.find()) {
			scanned.add(tbody.group(1));
		}
		if (scanned.isEmpty()) {
			scanned.add(body);
		}
		for (String section : scanned) {
			Matcher row = Pattern.compile("<tr>(.*?)</tr>").matcher(section);
			while (row.find()) {
				String single = row.group(1).trim();
				if (!single.contains("<th")) {
					rows.add(single);
				}
			}
		}
		return rows;
	}

	/** The pagination <ul>, whitespace collapsed; null when #{pagination} rendered nothing. */
	protected static String paginationBlock(String body) {
		int start = body.indexOf("<ul class=\"pagination\">");
		if (start < 0) {
			return null;
		}
		return body.substring(start, body.indexOf("</ul>", start) + 5);
	}

	/**
	 * Every moderation/posting button of a page, as label then target. Two shapes are recognised:
	 * a plain `<a class="btn btn-*">` (a GET), and a `<button>` inside a `<form method="POST">`
	 * (the actions conf/routes declares as POST only, such as forum.Topics.hide()), whose entry is
	 * prefixed with "POST ". Entries come out in document order.
	 */
	protected static List<String> actionButtons(String body) {
		List<String> buttons = new ArrayList<String>();
		Matcher m = Pattern.compile("<a class=\"btn btn-(?:info|danger)\" href=\"([^\"]*)\">([^<]*)</a>"
				+ "|<form role=\"form\" action=\"([^\"]*)\" method=\"POST\"[^>]*>\\s*<button class=\"btn btn-(?:info|danger)\" type=\"submit\">([^<]*)</button>")
				.matcher(body);
		while (m.find()) {
			if (m.group(2) != null) {
				buttons.add(m.group(2) + " -> " + m.group(1));
			} else {
				buttons.add(m.group(4) + " -> POST " + m.group(3));
			}
		}
		return buttons;
	}

	// ~~~~~~~~~~~~ direct rendering of unreachable branches

	private static Http.Request previousRequest;
	private static Http.Response previousResponse;
	private static Scope.Session previousSession;
	private static Scope.Flash previousFlash;
	private static Scope.Params previousParams;
	private static Scope.RenderArgs previousRenderArgs;
	private static Validation previousValidation;

	/**
	 * The binding variables Controller.renderTemplate injects, plus the renderArgs of
	 * Application.globals(), for a request made as the given user.
	 */
	protected static Map<String, Object> viewArgs(String username, String path) throws Throwable {
		Http.Request request = new Http.Request();
		request.method = "GET";
		request.path = path;
		request.url = path;
		request.routeArgs = new HashMap<String, String>();

		previousRequest = Http.Request.current.get();
		previousResponse = Http.Response.current.get();
		previousSession = Scope.Session.current.get();
		previousFlash = Scope.Flash.current.get();
		previousParams = Scope.Params.current.get();
		previousRenderArgs = Scope.RenderArgs.current.get();
		previousValidation = Validation.current.get();

		Http.Request.current.set(request);
		Http.Response.current.set(new Http.Response());

		Scope.Session session = new Scope.Session();
		session.put("username", username);
		Scope.Session.current.set(session);

		Scope.Flash flash = new Scope.Flash();
		Scope.Flash.current.set(flash);

		Scope.Params params = new Scope.Params();
		Scope.Params.current.set(params);

		Scope.RenderArgs renderArgs = new Scope.RenderArgs();
		Scope.RenderArgs.current.set(renderArgs);

		Validation.current.set(new Validation() {
		});

		Map<String, Object> args = new HashMap<String, Object>();
		args.put("session", session);
		args.put("flash", flash);
		args.put("params", params);
		args.put("request", request);
		args.put("errors", Validation.errors());
		args.put("connectedUser", inTransaction(() -> models.forum.User.findByEmail(username)));
		args.put("pageSize", pageSize());
		return args;
	}

	/** Renders a template without going through a controller, inside a JPA transaction. */
	protected static String renderView(String path, Map<String, Object> args) throws Throwable {
		try {
			return inTransaction(() -> TemplateLoader.load(path).render(args)).replaceAll("\\s+", " ");
		} finally {
			restoreThreadLocals();
		}
	}

	/**
	 * Renders a template with the default EntityManager unbound. That is the state
	 * PlayHandler.serve404() renders errors/404.html in: it writes the page outside any controller, so
	 * no JPA interceptor ran, and main.html's #{secure.check} tags reach User.findByEmail() with no
	 * EntityManager. Before Profile.Security.check() started refusing that, the render raised
	 * JPAException and an unmatched route answered 500 instead of 404.
	 */
	protected static String renderViewWithoutEntityManager(String path, Map<String, Object> args) throws Throwable {
		javax.persistence.EntityManager em = JPA.isInsideTransaction() ? JPA.em() : null;
		JPA.unbindForCurrentThread(JPA.DEFAULT);
		try {
			return TemplateLoader.load(path).render(args).replaceAll("\\s+", " ");
		} finally {
			if (em != null) {
				JPA.bindForCurrentThread(JPA.DEFAULT, em, false);
			}
			restoreThreadLocals();
		}
	}

	/** Puts the thread-locals viewArgs() replaced back, and drops the errors the render added. */
	private static void restoreThreadLocals() {
		Http.Request.current.set(previousRequest);
		Http.Response.current.set(previousResponse);
		Scope.Session.current.set(previousSession);
		Scope.Flash.current.set(previousFlash);
		Scope.Params.current.set(previousParams);
		Scope.RenderArgs.current.set(previousRenderArgs);
		Validation.current.set(previousValidation != null ? previousValidation : new Validation() {
		});
		// Drop the errors the render added, so fixtures loaded later are not polluted by them.
		Validation.errors().clear();
	}

	/** Renders a template with one validation error installed, which no controller produces. */
	protected static String renderViewWithError(String path, Map<String, Object> args, String key, String message)
			throws Throwable {
		Validation.addError(key, message);
		args.put("errors", Validation.errors());
		return renderView(path, args);
	}

	// ~~~~~~~~~~~~ database helpers

	/** Runs the block inside a JPA transaction and returns whatever it produced. */
	protected static <T> T inTransaction(F.Function0<T> block) throws Throwable {
		return JPA.withTransaction(JPA.DEFAULT, false, block);
	}

	protected static void inTransactionVoid(F.Function0<Void> block) throws Throwable {
		JPA.withTransaction(JPA.DEFAULT, false, block);
	}

	// ~~~~~~~~~~~~ flash, errors and the exceptions Play wraps

	/** flashOf(response).get(key), with a message that names the flash map when the key is absent. */
	protected static void assertFlashValue(Response response, String key, String value) throws Throwable {
		Map<String, String> flash = flashOf(response);
		assertTrue("expected flash " + key + "=" + value + ", got " + flash, value.equals(flash.get(key)));
	}

	protected static void assertEmptyMap(Map<String, String> map) {
		assertTrue("expected an empty cookie map, got " + map, map.isEmpty());
	}

	private static Throwable rootCause(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		return c;
	}

	/** Walks the cause chain and asserts each level, for the exceptions Play wraps around controller errors. */
	protected static void assertCauseChain(Throwable t, String... expectedMessages) {
		StringBuilder all = new StringBuilder(t.getClass().getName() + ": " + t.getMessage());
		for (Throwable c = t.getCause(); c != null; c = c.getCause()) {
			all.append(" <- " + c.getClass().getName() + ": " + c.getMessage());
		}
		for (String expected : expectedMessages) {
			assertTrue("unexpected cause chain: " + all, all.toString().contains(expected));
		}
	}

	protected static Throwable causeRoot(Throwable t) {
		return rootCause(t);
	}

	// ~~~~~~~~~~~~ errors/403.html and errors/404.html of the app

	protected static void assertForbiddenBody(String body) {
		assertContains("<div class=\"page-header\"> <h1>Forbidden</h1> "
				+ "<p>You don't have the permission to access this page.</p> </div>", body);
	}

	/** app/views/errors/404.html, which every notFoundIfNull() of the app controllers reaches. */
	protected static void assertNotFoundBody(String body) {
		assertContains("<div class=\"page-header\"> <h1>Forbidden</h1> "
				+ "<p>The forum, topic, or post you search does not exist !</p> </div>", body);
	}
}
