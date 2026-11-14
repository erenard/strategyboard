import org.junit.Test;

import play.mvc.Http.Response;

/**
 * Coverage of controllers.CRUD.index() (modules/crud), which forbids itself when it is reached as the
 * module class rather than as one of the CRUD$ObjectType subclasses the app declares:
 *
 *     if (getControllerClass() == CRUD.class) { forbidden(); }
 *
 * conf/routes offers two ways in, and they answer differently:
 *   - GET /crud/index and GET /crud reach it through the catch-all `* /{controller}/{action}` and
 *     `* /{controller}` routes, which resolve the name crud to controllers.CRUD. That class is abstract,
 *     but index() is a static method, so ActionInvoker still calls it and the guard fires. Nothing is
 *     checked first: controllers.CRUD carries no @With(Secure.class) and no @Check, and controllers.Profile
 *     is not inherited either, so an anonymous visitor is not sent to /login and gets 403 like everybody.
 *   - GET /admin/ and GET /admin reach index() through the `GET / CRUD.index` line of
 *     modules/crud/conf/routes, mounted by `* /admin module:crud`. There the guard does not fire and the
 *     module's CRUD/index.html type list is rendered, and the mount is guarded by the secure module, so an
 *     anonymous visitor gets Secure.checkAccess()'s redirect to /login with the url kept in the flash.
 *
 * The @Before addType() runs on both paths and puts a null renderArgs.type, since ObjectType.get(CRUD.class)
 * has no entity to resolve. The CRUD pages every suite asserts carry
 * <div id="crudHeader"> <h1><a href="/admin/">Administration</a></h1> </div>, so this root page is the one
 * an administrator actually lands on when clicking that Home link, while a logged-out visitor lands on the
 * login page.
 */
public class CrudIndexForbiddenTest extends BaseForumViewTest {

	private static final String FORBIDDEN_TITLE = "<title>Strategyboard - Forbidden</title>";

	private static final String FORBIDDEN_BODY = "<div class=\"page-header\"> <h1>Forbidden</h1> "
			+ "<p>You don't have the permission to access this page.</p> </div>";

	private static final String CRUD_HEADER = "<div id=\"crudHeader\"> <h1><a href=\"/admin/\">Administration</a></h1> </div>";

	// ~~~~~~~~~~~~ the catch-all routes

	@Test
	public void anAnonymousVisitorIsForbiddenOnCrudIndexWithoutALoginRedirect() throws Throwable {
		clearCookies();
		Response response = GET("/crud/index");
		assertStatus(403, response);
		assertEquals("text/html; charset=utf-8", response.contentType);
		assertNull(header(response, "Location"));
		assertNull(sessionUsername(response));
		// Secure.checkAccess() never runs, so no url flash is written either
		assertEmptyMap(flashOf(response));

		String body = markup(response);
		assertContains(FORBIDDEN_TITLE, body);
		assertContains(FORBIDDEN_BODY, body);
		assertNotContains("Welcome back", body);
		assertNotContains("<div id=\"crudHeader\"", body);
	}

	/** The `* /{controller} {controller}.index` route gives the same answer as the explicit action path. */
	@Test
	public void anAnonymousVisitorIsForbiddenOnTheBareCrudRoute() throws Throwable {
		clearCookies();
		Response response = GET("/crud");
		assertStatus(403, response);
		assertNull(header(response, "Location"));
		assertNull(sessionUsername(response));
		assertContains(FORBIDDEN_BODY, markup(response));
	}

	/** The administrator passes every Profile check elsewhere, but CRUD.index() forbids before any check. */
	@Test
	public void theAdministratorIsForbiddenOnCrudIndex() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		Response response = GET("/crud/index");
		assertStatus(403, response);
		assertEquals("admin@domain.com", sessionUsername(response));

		String body = markup(response);
		assertContains(FORBIDDEN_BODY, body);
		// the flash the login POST left is consumed by this request and rendered as an alert
		assertSuccessAlert("Welcome back Admin !", body);
	}

	@Test
	public void aReplyerIsForbiddenOnCrudIndex() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/crud/index");
		assertStatus(403, response);
		assertEquals("bob@domain.com", sessionUsername(response));

		String body = markup(response);
		assertContains(FORBIDDEN_TITLE, body);
		assertContains(FORBIDDEN_BODY, body);
		assertSuccessAlert("Welcome back Bob !", body);
	}

	// ~~~~~~~~~~~~ the Home link of the CRUD layout, through the module mount

	@Test
	public void anAnonymousVisitorIsSentToLoginFromTheRootOfTheAdminMount() throws Throwable {
		clearCookies();
		Response withSlash = GET("/admin/");
		assertStatus(302, withSlash);
		assertLocation(withSlash, "/login");
		assertFlashValue(withSlash, "url", "/admin/");
		assertNull(sessionUsername(GET("/")));

		clearCookies();
		Response withoutSlash = GET("/admin");
		assertStatus(302, withoutSlash);
		assertLocation(withoutSlash, "/login");
		assertFlashValue(withoutSlash, "url", "/admin");
	}

	/** The administrator lands on the module's type list, which is what every CRUD breadcrumb links to. */
	@Test
	public void theRootOfTheAdminMountListsTheSevenObjectTypesTheAdminControllersExpose() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		Response response = GET("/admin/");
		assertStatus(200, response);
		assertEquals("text/html; charset=utf-8", response.contentType);
		assertEquals("admin@domain.com", sessionUsername(response));

		String body = markup(response);
		assertContains("<title>Administration</title>", body);
		assertContains(CRUD_HEADER, body);
		assertContains("<div class=\"crudFlash flashSuccess\"> Welcome back Admin ! </div>", body);
		assertContains("<h2>Choose the object to edit</h2>", body);
		assertContains("<tr> <td> <a href=\"/admin/adminattachments\">AdminAttachments</a> </td> "
				+ "<td class=\"crudNew\" > <a href=\"/admin/adminattachments/new\">Add</a> </td> </tr>", body);
		assertContains("<tr> <td> <a href=\"/admin/admincategories\">AdminCategories</a> </td> "
				+ "<td class=\"crudNew\" > <a href=\"/admin/admincategories/new\">Add</a> </td> </tr>", body);
		assertContains("<tr> <td> <a href=\"/admin/adminforums\">AdminForums</a> </td> "
				+ "<td class=\"crudNew\" > <a href=\"/admin/adminforums/new\">Add</a> </td> </tr>", body);
		assertContains("<tr> <td> <a href=\"/admin/adminpermissions\">AdminPermissions</a> </td> "
				+ "<td class=\"crudNew\" > <a href=\"/admin/adminpermissions/new\">Add</a> </td> </tr>", body);
		assertContains("<tr> <td> <a href=\"/admin/admintopics\">AdminTopics</a> </td> "
				+ "<td class=\"crudNew\" > <a href=\"/admin/admintopics/new\">Add</a> </td> </tr>", body);
		assertContains("<tr> <td> <a href=\"/admin/adminusers\">AdminUsers</a> </td> "
				+ "<td class=\"crudNew\" > <a href=\"/admin/adminusers/new\">Add</a> </td> </tr>", body);
		assertContains("<tr> <td> <a href=\"/admin/adminusergroups\">AdminUserGroups</a> </td> "
				+ "<td class=\"crudNew\" > <a href=\"/admin/adminusergroups/new\">Add</a> </td> </tr>", body);
		assertContains("<div id=\"crudFooter\"> Generated by the Play CRUD module. "
				+ "<a href=\"https://www.playframework.com/documentation/1.1/crud\">Learn how to customize it !</a> </div>",
				body);
		// CRUD/index.html is the module template, so it does not extend main.html and prints no page-header
		assertNotContains(FORBIDDEN_BODY, body);
	}

	@Test
	public void theRootOfTheAdminMountWithoutTheTrailingSlashAnswersTheSamePage() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		Response response = GET("/admin");
		assertStatus(200, response);
		String body = markup(response);
		assertContains("<title>Administration</title>", body);
		assertContains("<h2>Choose the object to edit</h2>", body);
	}

	/** The list pages of the mounted CRUD$ObjectType controllers stay reachable, as CrudAdminPagesTest shows. */
	@Test
	public void theListPageOfARealObjectTypeIsStillReachable() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		// consumes the login flash, so the list page fetched next renders no flash box at all
		assertStatus(200, GET("/admin/adminusers"));

		Response response = GET("/admin/adminusers");
		assertStatus(200, response);
		String body = markup(response);
		assertContains(CRUD_HEADER, body);
		assertNotContains("<div class=\"crudFlash", body);
		assertContains("<div id=\"crudBreadcrumb\"> <ul> <li><a href=\"/admin/\">Home</a></li> "
				+ "<li>&gt; <a href=\"/admin/adminusers\">AdminUsers</a></li> </ul> </div>", body);
	}
}
