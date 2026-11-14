import org.junit.Test;

import play.mvc.Http.Response;

import models.forum.Category;
import models.forum.Forum;

/**
 * Coverage of controllers.admin.AdminCategories, the CRUD wrapper of models.forum.Category.
 *
 * models.forum.Category has name (@Required @MaxSize(50)), displayOrder (@Required) and forums
 * (@OneToMany), so crud.form renders a text field, a number field and one crud_unknown block: a
 * collection has no ObjectField.type (CRUD$ObjectType.getField() only accepts scalar and relation
 * properties), which is why posting object.forums.id changes nothing. Category.toString() is the name,
 * which is what the breadcrumb and the show title print.
 *
 * The two fixture categories in test/data.yml carry no displayOrder, so CRUD.show renders an empty
 * number field for them, and CRUD.save of one of them without object.displayOrder fails validation.
 *
 * app/controllers/admin/AdminCategories.java declares no template of its own, but
 * app/views/admin/AdminCategories/list.html overrides the module's list template (app/views comes first
 * in Play.templatesPath). That template asks #{crud.table fields:['name', 'description', 'forums']} and
 * models.forum.Category has no description, so GET /admin/admincategories always fails with
 * "Cannot get property 'type' on null object" - CrudAdminPagesTest renders the template directly to
 * cover its markup. Every CRUD action that redirects to the list therefore cannot be followed: the
 * suites assert the 302 and its flash, and use the show page when a rendered target is needed.
 *
 * app/views/errors/403.html and errors/404.html are the app's own templates, so a forbidden CRUD page
 * shows the login flash as <div class="alert alert-success">...</div> rather than the module's
 * crudFlash box.
 */
public class CrudAdminCategoriesTest extends BaseCrudTest {

	/** id, label pairs as crud.relationField lists them; Category is not a relation of itself. */
	private static final String[] CATEGORY_OPTIONS = {"1", "Moderators Category", "2", "Main Category"};

	// ~~~~~~~~~~~~ blank (GET /admin/admincategories/new)

	@Test
	public void blankFormOffersNameDisplayOrderAndTheForumsCollectionItCannotEdit() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/admincategories/new");
		assertStatus(200, response);
		String body = markup(response);

		assertLayoutHeader(body);
		assertBlankTitle(body, "Category");
		assertBreadcrumb(body, "admincategories", "AdminCategories", "/admin/admincategories/new");
		assertCreateForm(body, "/admin/admincategories");
		assertBlankButtons(body);
		assertLayoutFooter(body);
		// CRUD/blank.html is the module template: AdminCategories has no views of its own
		assertLoginFlash(body);

		assertTextField(body, "name", "", "Required. Max length is 50.");
		assertNumberField(body, "displayOrder", "", "Numeric. Required.");
		assertUnknownField(body, "forums");
	}

	// ~~~~~~~~~~~~ show (GET /admin/admincategories/{id})

	@Test
	public void showFormOfACategoryWithoutDisplayOrderPrintsAnEmptyNumberField() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/admincategories/2");
		assertStatus(200, response);
		String body = markup(response);

		assertShowTitle(body, "Category");
		assertBreadcrumbOf(body, "admincategories", "AdminCategories", "/admin/admincategories/2", "Main Category");
		assertSaveForm(body, "/admin/admincategories/2");
		assertDeleteForm(body, "/admin/admincategories/2");
		assertShowButtons(body);
		assertDeleteButton(body, "Category");
		assertLayoutFooter(body);
		assertLoginFlash(body);

		assertTextField(body, "name", "Main Category", "Required. Max length is 50.");
		// test/data.yml gives the fixture categories no displayOrder, and the entity has no initializer
		assertNumberField(body, "displayOrder", "", "Numeric. Required.");
		assertUnknownField(body, "forums");
	}

	@Test
	public void showOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/admincategories/999");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	// ~~~~~~~~~~~~ create (POST /admin/admincategories)

	@Test
	public void createWithoutAnyValueReportsBothRequiredFields() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/admincategories", form());
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertBlankTitle(body, "Category");
		// CRUD.create never calls validation.keep(), so nothing is written to the PLAY_ERRORS cookie
		assertEmptyMap(errorsOf(response));
		assertTextFieldError(body, "name", "", "Required", "Required. Max length is 50.");
		assertNumberFieldError(body, "displayOrder", "", "Required", "Numeric. Required.");
		assertUnknownField(body, "forums");

		assertEquals(2, inTransaction(() -> Category.count()).longValue());
	}

	@Test
	public void createWithSaveRedirectsToTheListWhichCannotBeRendered() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/admincategories", form("object.name", "Fresh Category",
				"object.displayOrder", "6", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/admincategories");
		assertFlashValue(response, "success", "The Category has been created");

		// The row is written even though its list page throws
		assertEquals(3, inTransaction(() -> Category.count()).longValue());
		Category created = inTransaction(() -> Category.findById(3L));
		assertEquals("Fresh Category", created.name);
		assertEquals(Integer.valueOf(6), created.displayOrder);

		// Following the redirect proves app/views/admin/AdminCategories/list.html is broken:
		// tags/crud/table.html reads type.getField('description').type and description does not exist
		try {
			GET("/admin/admincategories");
			fail("expected the missing description field to break #{crud.table}");
		} catch (Throwable t) {
			assertCauseChain(t, "play.exceptions.TemplateExecutionException",
					"Cannot get property 'type' on null object", "java.lang.NullPointerException");
		}
	}

	@Test
	public void createWithoutSaveRedirectsToTheNewRowAndIgnoresTheForumsCollection() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/admincategories", form("object.name", "Fresh Category",
				"object.displayOrder", "6", "object.forums.id", "1"));
		assertStatus(302, response);
		assertLocation(response, "/admin/admincategories/3");
		assertFlashValue(response, "success", "The Category has been created");

		String body = markup(GET("/admin/admincategories/3"));
		assertCrudFlash(body, "flashSuccess", "The Category has been created");
		assertBreadcrumbOf(body, "admincategories", "AdminCategories", "/admin/admincategories/3", "Fresh Category");
		assertTextField(body, "name", "Fresh Category", "Required. Max length is 50.");
		assertNumberField(body, "displayOrder", "6", "Numeric. Required.");

		// crud_unknown renders no input, so object.forums.id is not read and forum 1 keeps its category
		Forum forum1 = inTransaction(() -> Forum.findById(1L));
		assertEquals(Long.valueOf(2L), forum1.category.id);
		assertEquals(5, inTransaction(() -> Forum.count()).longValue());
	}

	@Test
	public void anonymousIsRedirectedAndAModeratorIsForbiddenForBothWriteActions() throws Throwable {
		clearCookies();
		Response anonymous = POST("/admin/admincategories", form("object.name", "Nope", "object.displayOrder", "9"));
		assertStatus(302, anonymous);
		assertLocation(anonymous, "/login");
		// Secure.checkAccess() redirects to login(), which keeps "/" as the url to come back to
		assertFlashValue(anonymous, "url", "/");
		assertEquals(2, inTransaction(() -> Category.count()).longValue());

		clearCookies();
		login("modo@domain.com", "secret");
		Response moderator = DELETE("/admin/admincategories/1");
		assertStatus(403, moderator);
		String body = markup(moderator);
		assertForbiddenBody(body);
		// errors/403.html is the app's template, so the kept login flash is an alert, not a crudFlash
		assertSuccessAlert("Welcome back Moderator !", body);
		assertNotContains("<div id=\"crudHeader\"", body);
		assertEquals(2, inTransaction(() -> Category.count()).longValue());
	}

	// ~~~~~~~~~~~~ save (POST /admin/admincategories/{id})

	@Test
	public void saveWithSaveRedirectsToTheListAndWritesBothFields() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/admincategories/1", form("object.name", "Renamed Category",
				"object.displayOrder", "9", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/admincategories");
		assertFlashValue(response, "success", "The Category has been saved");
		assertEmptyMap(errorsOf(response));

		Category renamed = inTransaction(() -> Category.findById(1L));
		assertEquals("Renamed Category", renamed.name);
		assertEquals(Integer.valueOf(9), renamed.displayOrder);
	}

	@Test
	public void saveWithoutTheDisplayOrderReportsTheMissingNumberFieldAndWritesNothing() throws Throwable {
		loginAdmin();
		// The fixture row has no displayOrder, so @Required fails and object.name is not saved either
		Response response = POST("/admin/admincategories/2", form("object.name", "Renamed main"));
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertShowTitle(body, "Category");
		assertTextField(body, "name", "Renamed main", "Required. Max length is 50.");
		assertNumberFieldError(body, "displayOrder", "", "Required", "Numeric. Required.");

		Category untouched = inTransaction(() -> Category.findById(2L));
		assertEquals("Main Category", untouched.name);
	}

	@Test
	public void saveWithoutSaveRedirectsBackToTheShowPage() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/admincategories/1", form("object.name", "Renamed Category",
				"object.displayOrder", "9"));
		assertStatus(302, response);
		assertLocation(response, "/admin/admincategories/1");
		assertFlashValue(response, "success", "The Category has been saved");

		String body = markup(GET("/admin/admincategories/1"));
		assertCrudFlash(body, "flashSuccess", "The Category has been saved");
		assertBreadcrumbOf(body, "admincategories", "AdminCategories", "/admin/admincategories/1",
				"Renamed Category");
		assertTextField(body, "name", "Renamed Category", "Required. Max length is 50.");
		assertNumberField(body, "displayOrder", "9", "Numeric. Required.");
	}

	@Test
	public void saveOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, POST("/admin/admincategories/999", form("object.name", "X", "object.displayOrder", "1")));
	}

	// ~~~~~~~~~~~~ delete (DELETE /admin/admincategories/{id})

	@Test
	public void deleteOfACategoryThatHasForumsIsRefusedAndRedirectsToTheShowPage() throws Throwable {
		loginAdmin();
		// Every fixture forum points at a category, so the Forum.category foreign key blocks the delete
		Response response = DELETE("/admin/admincategories/1");
		assertStatus(302, response);
		assertLocation(response, "/admin/admincategories/1");
		assertFlashValue(response, "error", "Cannot delete this object");

		String body = markup(GET("/admin/admincategories/1"));
		assertCrudFlash(body, "flashError", "Cannot delete this object");
		assertShowTitle(body, "Category");
		// CRUD.delete redirects from inside its catch, so the success flash under it is never written
		assertNotContains("The Category has been deleted", body);
		assertEquals(2, inTransaction(() -> Category.count()).longValue());
		assertEquals(5, inTransaction(() -> Forum.count()).longValue());
	}

	@Test
	public void deleteOfACategoryWithoutForumsRemovesTheRow() throws Throwable {
		loginAdmin();
		POST("/admin/admincategories", form("object.name", "Bare", "object.displayOrder", "7"));
		assertEquals(3, inTransaction(() -> Category.count()).longValue());

		// The redirect target is the list page, which throws, so only the 302 and its flash are asserted
		Response response = DELETE("/admin/admincategories/3");
		assertStatus(302, response);
		assertLocation(response, "/admin/admincategories");
		assertFlashValue(response, "success", "The Category has been deleted");

		assertEquals(2, inTransaction(() -> Category.count()).longValue());
		assertNull(inTransaction(() -> Category.findById(3L)));
	}

	@Test
	public void deleteOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, DELETE("/admin/admincategories/999"));
	}

	// ~~~~~~~~~~~~ attachment (GET /admin/admincategories/{id}/{field})

	@Test
	public void attachmentIsNotFoundForAFieldThatIsNotABinaryAttachment() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/admincategories/1/name");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	@Test
	public void attachmentFailsForAFieldNameTheEntityDoesNotHave() throws Throwable {
		loginAdmin();
		try {
			GET("/admin/admincategories/1/zzz");
			fail("expected CRUD.attachment to ask for a field that does not exist");
		} catch (Throwable t) {
			// object.getClass().getField(field) throws NoSuchFieldException, which Play wraps
			assertCauseChain(t, "play.exceptions.JavaExecutionException: zzz", "java.lang.NoSuchFieldException: zzz");
			assertTrue("expected a NoSuchFieldException at the root of the chain",
					causeRoot(t) instanceof NoSuchFieldException);
		}
	}

	/**
	 * GET /admin/admincategories/{id}/{field} on the collection property: CRUD$ObjectType.getField finds
	 * forums, but its ObjectField.type is "unknown", so CRUD.attachment ends with notFound() rather than
	 * streaming a file.
	 */
	@Test
	public void attachmentIsNotFoundForTheCollectionField() throws Throwable {
		loginAdmin();
		assertStatus(404, GET("/admin/admincategories/1/forums"));
	}

	/** The category relation of a forum, offered by AdminForums.show, is the same entity. */
	@Test
	public void theCategoryOptionsOfTheForumFormAreOrderedById() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminforums/1"));
		assertRelationField(body, "category", "(Please select a value)", "Required.", "2", CATEGORY_OPTIONS);
	}
}
