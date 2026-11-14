import org.junit.Test;

import play.mvc.Http.Response;

import models.forum.Permission;

/**
 * Coverage of controllers.admin.AdminPermissions, the CRUD wrapper of models.forum.Permission.
 *
 * models.forum.Permission has three fields, all @Required: category and group (@ManyToOne, so they are
 * posted as object.category.id and object.group.id) and accessLevel (Integer, crud_number). Its
 * toString() is "<group name> - <accessLevel> - <category name>", which is what the breadcrumb and the
 * list rows print.
 *
 * test/data.yml provides permissions 1..5: postMain (General 3 Main Category), moderateMain
 * (Moderators 4 Main Category), postModerators (Moderators 3 Moderators Category), viewModerators
 * (General 1 Moderators Category) and viewAnonymous (Anonymous 1 Main Category). A permission created
 * through the form takes id 6.
 */
public class CrudAdminPermissionsTest extends BaseCrudTest {

	/** id, label, id, label... as crud.relationField lists them (categories are ordered by id here). */
	private static final String[] CATEGORY_OPTIONS = {"1", "Moderators Category", "2", "Main Category"};

	private static final String[] GROUP_OPTIONS = {"1", "Admin", "2", "Moderators", "3", "General", "4", "Anonymous"};

	// ~~~~~~~~~~~~ blank (GET /admin/adminpermissions/new)

	@Test
	public void blankFormAsksForBothRelationsAndTheNumericAccessLevel() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminpermissions/new");
		assertStatus(200, response);
		String body = markup(response);

		assertLayoutHeader(body);
		assertBlankTitle(body, "Permission");
		assertBreadcrumb(body, "adminpermissions", "AdminPermissions", "/admin/adminpermissions/new");
		assertCreateForm(body, "/admin/adminpermissions");
		assertBlankButtons(body);
		assertLayoutFooter(body);

		// Both relations are @Required, so crud.selectValue prints the placeholder that has no id
		assertRelationField(body, "category", "(Please select a value)", "Required.", null, CATEGORY_OPTIONS);
		assertRelationField(body, "group", "(Please select a value)", "Required.", null, GROUP_OPTIONS);
		assertNumberField(body, "accessLevel", "", "Numeric. Required.");
		assertNotContains("selected=\"true\"", body);
	}

	// ~~~~~~~~~~~~ show (GET /admin/adminpermissions/{id})

	@Test
	public void showFormSelectsTheStoredCategoryAndGroup() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminpermissions/1");
		assertStatus(200, response);
		String body = markup(response);

		assertShowTitle(body, "Permission");
		assertBreadcrumbOf(body, "adminpermissions", "AdminPermissions", "/admin/adminpermissions/1",
				"General - 3 - Main Category");
		assertSaveForm(body, "/admin/adminpermissions/1");
		assertDeleteForm(body, "/admin/adminpermissions/1");
		assertShowButtons(body);
		assertDeleteButton(body, "Permission");
		assertLayoutFooter(body);
		// The login POST is the flash this page prints: it is the first request after login()
		assertLoginFlash(body);

		assertRelationField(body, "category", "(Please select a value)", "Required.", "2", CATEGORY_OPTIONS);
		assertRelationField(body, "group", "(Please select a value)", "Required.", "3", GROUP_OPTIONS);
		assertNumberField(body, "accessLevel", "3", "Numeric. Required.");
	}

	@Test
	public void showOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, GET("/admin/adminpermissions/999"));
	}

	// ~~~~~~~~~~~~ create (POST /admin/adminpermissions)

	@Test
	public void createWithoutAnyValueReportsEveryFieldAsRequired() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminpermissions", form());
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertBlankTitle(body, "Permission");
		assertRelationFieldError(body, "category", "(Please select a value)", "Required.", "Required", null,
				CATEGORY_OPTIONS);
		assertRelationFieldError(body, "group", "(Please select a value)", "Required.", "Required", null,
				GROUP_OPTIONS);
		assertNumberFieldError(body, "accessLevel", "", "Required", "Numeric. Required.");
		assertEmptyMap(errorsOf(response));
		assertEquals(5, inTransaction(() -> Permission.count()).longValue());
	}

	@Test
	public void createWithANonNumericAccessLevelReportsIncorrectValueAndKeepsThePostedSelections() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminpermissions", form("object.category.id", "2", "object.group.id", "3",
				"object.accessLevel", "abc"));
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		// Binder binds the relation ids before validation fails, so the selects show the posted choice
		assertRelationField(body, "category", "(Please select a value)", "Required.", "2", CATEGORY_OPTIONS);
		assertRelationField(body, "group", "(Please select a value)", "Required.", "3", GROUP_OPTIONS);
		assertNumberFieldError(body, "accessLevel", "abc", "Incorrect value", "Numeric. Required.");
		assertEquals(5, inTransaction(() -> Permission.count()).longValue());
	}

	@Test
	public void createWithSaveRedirectsToTheListAndWritesTheRow() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminpermissions", form("object.category.id", "2", "object.group.id", "3",
				"object.accessLevel", "2", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminpermissions");
		assertFlashValue(response, "success", "The Permission has been created");

		String body = markup(GET("/admin/adminpermissions"));
		assertCrudFlash(body, "flashSuccess", "The Permission has been created");
		assertRowCount(body, 6, "AdminPermissions");
		assertListRow(body, "even", "/admin/adminpermissions/6", "General - 2 - Main Category");
		Permission createdPermission = inTransaction(() -> Permission.findById(6L));
		assertEquals(Integer.valueOf(2), createdPermission.accessLevel);
	}

	@Test
	public void createWithoutSaveRedirectsToTheNewRow() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminpermissions", form("object.category.id", "1", "object.group.id", "4",
				"object.accessLevel", "1"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminpermissions/6");
		assertFlashValue(response, "success", "The Permission has been created");

		String body = markup(GET("/admin/adminpermissions/6"));
		assertCrudFlash(body, "flashSuccess", "The Permission has been created");
		assertBreadcrumbOf(body, "adminpermissions", "AdminPermissions", "/admin/adminpermissions/6",
				"Anonymous - 1 - Moderators Category");
		assertRelationField(body, "category", "(Please select a value)", "Required.", "1", CATEGORY_OPTIONS);
		assertRelationField(body, "group", "(Please select a value)", "Required.", "4", GROUP_OPTIONS);
		assertNumberField(body, "accessLevel", "1", "Numeric. Required.");
	}

	@Test
	public void createWithSaveAndAddAnotherRedirectsToBlankAgain() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminpermissions", form("object.category.id", "2", "object.group.id", "3",
				"object.accessLevel", "2", "_saveAndAddAnother", "Save and create another"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminpermissions/new");
		assertFlashValue(response, "success", "The Permission has been created");
		assertBlankTitle(markup(GET("/admin/adminpermissions/new")), "Permission");
	}

	// ~~~~~~~~~~~~ save (POST /admin/adminpermissions/{id})

	@Test
	public void saveWithSaveRedirectsToTheListAndWritesTheRow() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminpermissions/2", form("object.category.id", "2", "object.group.id", "3",
				"object.accessLevel", "5", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminpermissions");
		assertFlashValue(response, "success", "The Permission has been saved");

		String body = markup(GET("/admin/adminpermissions"));
		assertCrudFlash(body, "flashSuccess", "The Permission has been saved");
		// The row label is Permission.toString(), so the new accessLevel is visible in the list
		assertListRow(body, "even", "/admin/adminpermissions/2", "General - 5 - Main Category");
		Permission savedPermission = inTransaction(() -> Permission.findById(2L));
		assertEquals(Integer.valueOf(5), savedPermission.accessLevel);
		assertEquals(Long.valueOf(3L), savedPermission.group.id);
	}

	@Test
	public void saveWithoutSaveRedirectsBackToTheShowPage() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminpermissions/3", form("object.category.id", "2", "object.group.id", "1",
				"object.accessLevel", "5"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminpermissions/3");
		assertFlashValue(response, "success", "The Permission has been saved");

		String body = markup(GET("/admin/adminpermissions/3"));
		assertCrudFlash(body, "flashSuccess", "The Permission has been saved");
		assertBreadcrumbOf(body, "adminpermissions", "AdminPermissions", "/admin/adminpermissions/3",
				"Admin - 5 - Main Category");
	}

	/**
	 * An omitted @Required relation is not reported as an error. CRUD.save binds the posted values onto the
	 * row loaded by GenericModel.edit(), and Validation.valid() runs on that object, whose group is still
	 * the one read from the database, so validation passes and only the posted fields are written.
	 */
	@Test
	public void saveWithoutTheRelationStillWritesBecauseTheLoadedRowIsValid() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminpermissions/1", form("object.category.id", "2", "object.accessLevel",
				"4"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminpermissions/1");
		assertFlashValue(response, "success", "The Permission has been saved");
		assertEmptyMap(errorsOf(response));

		String body = markup(GET("/admin/adminpermissions/1"));
		assertCrudFlash(body, "flashSuccess", "The Permission has been saved");
		assertRelationField(body, "group", "(Please select a value)", "Required.", "3", GROUP_OPTIONS);
		assertRelationField(body, "category", "(Please select a value)", "Required.", "2", CATEGORY_OPTIONS);
		assertNumberField(body, "accessLevel", "4", "Numeric. Required.");

		Permission saved = inTransaction(() -> Permission.findById(1L));
		assertEquals(Integer.valueOf(4), saved.accessLevel);
		assertEquals(Long.valueOf(3L), saved.group.id);
		assertEquals(Long.valueOf(2L), saved.category.id);
	}

	@Test
	public void saveOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, POST("/admin/adminpermissions/999", form("object.category.id", "2", "object.group.id", "3",
				"object.accessLevel", "1")));
	}

	// ~~~~~~~~~~~~ delete (DELETE /admin/adminpermissions/{id})

	@Test
	public void deleteOfAPermissionRemovesTheRowBecauseNothingReferencesIt() throws Throwable {
		loginAdmin();
		Response response = DELETE("/admin/adminpermissions/5");
		assertStatus(302, response);
		assertLocation(response, "/admin/adminpermissions");
		assertFlashValue(response, "success", "The Permission has been deleted");

		String body = markup(GET("/admin/adminpermissions"));
		assertCrudFlash(body, "flashSuccess", "The Permission has been deleted");
		assertRowCount(body, 4, "AdminPermissions");
		assertNotContains("/admin/adminpermissions/5", body);
		assertEquals(4, inTransaction(() -> Permission.count()).longValue());
	}

	@Test
	public void deleteOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, DELETE("/admin/adminpermissions/999"));
	}

	// ~~~~~~~~~~~~ attachment (GET /admin/adminpermissions/{id}/{field})

	@Test
	public void attachmentIsNotFoundForAFieldThatIsNotABinaryAttachment() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminpermissions/1/accessLevel");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	@Test
	public void attachmentFailsForAFieldNameTheEntityDoesNotHave() throws Throwable {
		loginAdmin();
		try {
			GET("/admin/adminpermissions/1/zzz");
			fail("expected CRUD.attachment to ask for a field that does not exist");
		} catch (Throwable t) {
			assertTrue("unexpected: " + t.getMessage(), t.getMessage().contains("zzz"));
		}
	}

	// ~~~~~~~~~~~~ list (GET /admin/adminpermissions)

	@Test
	public void listPagePrintsTheToStringOfEveryPermission() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminpermissions"));
		assertListBreadcrumb(body, "adminpermissions", "AdminPermissions");
		assertAddLink(body, "/admin/adminpermissions", "Permission");
		assertListTitle(body, "AdminPermissions");
		assertSearchForm(body, "/admin/adminpermissions");
		assertDefaultTableHead(body, "/admin/adminpermissions", "AdminPermissions");
		assertRowCount(body, 5, "AdminPermissions");
		assertNoPagination(body);
		assertListRow(body, "odd", "/admin/adminpermissions/1", "General - 3 - Main Category");
		assertListRow(body, "even", "/admin/adminpermissions/2", "Moderators - 4 - Main Category");
		assertListRow(body, "odd", "/admin/adminpermissions/3", "Moderators - 3 - Moderators Category");
		assertListRow(body, "even", "/admin/adminpermissions/4", "General - 1 - Moderators Category");
		assertListRow(body, "odd", "/admin/adminpermissions/5", "Anonymous - 1 - Main Category");
		assertLayoutFooter(body);
	}

	/**
	 * searchFields=group+category names two relation properties. JPAModelLoader.buildProperty marks a
	 * property searchable only when its type is String, so getSearchQuery returns an empty string and
	 * ObjectType.count/fetch add no condition: the keyword filters nothing, count equals totalCount, and
	 * every row is listed.
	 */
	@Test
	public void listPageSearchOnRelationFieldsFiltersNothingAndListsEveryRow() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminpermissions?search=moderators&searchFields=group+category"));
		assertSearchFormWithKeywords(body, "/admin/adminpermissions", "moderators", 5);
		assertDefaultTableHeadWithSearch(body, "/admin/adminpermissions", "moderators", "AdminPermissions");
		assertRowCount(body, 5, "AdminPermissions");
		assertListRow(body, "odd", "/admin/adminpermissions/1", "General - 3 - Main Category");
		assertListRow(body, "even", "/admin/adminpermissions/2", "Moderators - 4 - Main Category");
		assertListRow(body, "odd", "/admin/adminpermissions/3", "Moderators - 3 - Moderators Category");
		assertListRow(body, "even", "/admin/adminpermissions/4", "General - 1 - Moderators Category");
		assertListRow(body, "odd", "/admin/adminpermissions/5", "Anonymous - 1 - Main Category");
	}

	/** A keyword that is searched against a String property does filter: accessLevel is an Integer, so it
	 * cannot be matched, while name-less Permission has no String field at all and nothing matches. */
	@Test
	public void listPageSearchOfANonStringFieldFiltersNothing() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminpermissions?search=3&searchFields=accessLevel"));
		assertSearchFormWithKeywords(body, "/admin/adminpermissions", "3", 5);
		assertRowCount(body, 5, "AdminPermissions");
	}
}
