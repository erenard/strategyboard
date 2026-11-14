import org.junit.Test;

import play.mvc.Http.Response;

import models.forum.UserGroup;

/**
 * Coverage of controllers.admin.AdminUserGroups, the CRUD wrapper of models.forum.UserGroup.
 *
 * models.forum.UserGroup exposes name (@Required @MaxSize(50)), description (@MaxSize(1000)), permissions
 * (@OneToMany, which renders as crud_unknown) and admin (Boolean, no annotation). Its toString() is the
 * name, so the breadcrumb and the list rows print it, and the generated key is id, which is why the form
 * posts object.group.id style names for relations (this entity has none).
 *
 * test/data.yml provides groups 1..4: Admin (admin=true), Moderators, General and Anonymous (admin=false).
 * A group created through the form therefore takes id 5.
 */
public class CrudAdminUserGroupsTest extends BaseCrudTest {

	private static final String LONG_NAME = "this name is definitely longer than fifty characters and must fail";

	// ~~~~~~~~~~~~ blank (GET /admin/adminusergroups/new)

	@Test
	public void blankFormShowsTheTextFieldTheLongTextAndTheUnknownCollection() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminusergroups/new");
		assertStatus(200, response);
		String body = markup(response);

		assertLayoutHeader(body);
		assertBlankTitle(body, "UserGroup");
		assertBreadcrumb(body, "adminusergroups", "AdminUserGroups", "/admin/adminusergroups/new");
		assertCreateForm(body, "/admin/adminusergroups");
		assertBlankButtons(body);
		assertLayoutFooter(body);
		assertCrudFlash(body, "flashSuccess", "Welcome back Admin !");

		assertTextField(body, "name", "", "Required. Max length is 50.");
		assertLongTextField(body, "description", "", "Max length is 1000.");

		// UserGroup.permissions is a one-to-many collection, so ObjectField.type is "unknown" and
		// tags/crud/form.html writes a bare div with no label and no input
		assertUnknownField(body, "permissions");

		// admin has no declared default, so the object field is null and the box stays unticked
		assertCheckbox(body, "admin", false);
		assertNotContains("selected=\"true\"", body);
	}

	// ~~~~~~~~~~~~ show (GET /admin/adminusergroups/{id})

	@Test
	public void showFormCarriesTheValuesOfTheAdministratorGroup() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminusergroups/1");
		assertStatus(200, response);
		String body = markup(response);

		assertShowTitle(body, "UserGroup");
		assertBreadcrumbOf(body, "adminusergroups", "AdminUserGroups", "/admin/adminusergroups/1", "Admin");
		assertSaveForm(body, "/admin/adminusergroups/1");
		assertDeleteForm(body, "/admin/adminusergroups/1");
		assertShowButtons(body);
		assertDeleteButton(body, "UserGroup");
		assertLayoutFooter(body);
		// The login POST is the flash this page prints: it is the first request after login()
		assertLoginFlash(body);

		assertTextField(body, "name", "Admin", "Required. Max length is 50.");
		assertLongTextField(body, "description", "Admin", "Max length is 1000.");
		assertUnknownField(body, "permissions");
		assertCheckbox(body, "admin", true);
	}

	@Test
	public void showFormOfAGroupWithoutAdministratorRightsLeavesTheCheckboxUnticked() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminusergroups/4"));
		assertBreadcrumbOf(body, "adminusergroups", "AdminUserGroups", "/admin/adminusergroups/4", "Anonymous");
		assertTextField(body, "name", "Anonymous", "Required. Max length is 50.");
		assertLongTextField(body, "description", "Anonymous", "Max length is 1000.");
		assertCheckbox(body, "admin", false);
	}

	@Test
	public void showOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, GET("/admin/adminusergroups/999"));
	}

	// ~~~~~~~~~~~~ create (POST /admin/adminusergroups)

	@Test
	public void createWithoutAnyValueReportsOnlyTheRequiredField() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusergroups", form());
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertBlankTitle(body, "UserGroup");
		assertTextFieldError(body, "name", "", "Required", "Required. Max length is 50.");

		// description is only @MaxSize and admin has no annotation, so neither is reported
		assertNotContains("object_description\" class=\"hasError\"", body);
		assertNotContains("object_admin\" class=\"hasError\"", body);
		assertEmptyMap(errorsOf(response));
	}

	@Test
	public void createWithANameOverTheMaxSizeReportsTheSizeAndKeepsTheValue() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusergroups", form("object.name", LONG_NAME, "object.description", "x"));
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertTextFieldError(body, "name", LONG_NAME, "Maximum size is 50", "Required. Max length is 50.");
		assertLongTextField(body, "description", "x", "Max length is 1000.");
		assertEquals(4, inTransaction(() -> UserGroup.count()).longValue());
	}

	@Test
	public void createWithSaveRedirectsToTheListAndWritesTheRow() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusergroups", form("object.name", "Extra", "object.description",
				"Extra group", "object.admin", "true", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusergroups");
		assertFlashValue(response, "success", "The UserGroup has been created");

		String body = markup(GET("/admin/adminusergroups"));
		assertCrudFlash(body, "flashSuccess", "The UserGroup has been created");
		assertRowCount(body, 5, "AdminUserGroups");
		assertListRow(body, "odd", "/admin/adminusergroups/5", "Extra");
		UserGroup createdGroup = inTransaction(() -> UserGroup.findById(5L));
		assertEquals(Boolean.TRUE, createdGroup.admin);
	}

	@Test
	public void createWithoutSaveRedirectsToTheNewRowAndTheHiddenInputDecidesTheCheckbox() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusergroups", form("object.name", "Extra", "object.description",
				"Extra group"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusergroups/5");
		assertFlashValue(response, "success", "The UserGroup has been created");

		String body = markup(GET("/admin/adminusergroups/5"));
		assertCrudFlash(body, "flashSuccess", "The UserGroup has been created");
		assertBreadcrumbOf(body, "adminusergroups", "AdminUserGroups", "/admin/adminusergroups/5", "Extra");
		assertTextField(body, "name", "Extra", "Required. Max length is 50.");
		// object.admin was not posted, so only crud.checkField's hidden false input reached the binder
		assertCheckbox(body, "admin", false);
	}

	@Test
	public void createWithSaveAndAddAnotherRedirectsToBlankAgain() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusergroups", form("object.name", "Extra", "_saveAndAddAnother",
				"Save and create another"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusergroups/new");
		assertFlashValue(response, "success", "The UserGroup has been created");
		assertBlankTitle(markup(GET("/admin/adminusergroups/new")), "UserGroup");
	}

	@Test
	public void createIgnoresTheCollectionFieldThatTheFormCannotEdit() throws Throwable {
		loginAdmin();
		// crud_unknown renders no input, so object.permissions.id is nothing the binder can read
		POST("/admin/adminusergroups", form("object.name", "Extra", "object.permissions.id", "1"));
		// UserGroup.permissions is a lazy @OneToMany, so it can only be read while the transaction is open
		boolean newGroupHasPermissions = inTransaction(() -> {
			UserGroup created = UserGroup.findById(5L);
			return created.permissions != null && !created.permissions.isEmpty();
		});
		assertFalse("expected no permissions on the new group", newGroupHasPermissions);
		assertEquals(5, inTransaction(() -> UserGroup.count()).longValue());
	}

	// ~~~~~~~~~~~~ save (POST /admin/adminusergroups/{id})

	@Test
	public void saveWithSaveRedirectsToTheListAndWritesTheRow() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusergroups/3", form("object.name", "Renamed general",
				"object.description", "Renamed general group", "object.admin", "true", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusergroups");
		assertFlashValue(response, "success", "The UserGroup has been saved");

		String body = markup(GET("/admin/adminusergroups"));
		assertCrudFlash(body, "flashSuccess", "The UserGroup has been saved");
		assertListRow(body, "odd", "/admin/adminusergroups/3", "Renamed general");
		UserGroup renamed = inTransaction(() -> UserGroup.findById(3L));
		assertEquals(Boolean.TRUE, renamed.admin);
	}

	@Test
	public void saveWithoutSaveRedirectsBackToTheShowPage() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusergroups/2", form("object.name", "Renamed moderators",
				"object.description", "Renamed moderators group"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusergroups/2");
		assertFlashValue(response, "success", "The UserGroup has been saved");

		String body = markup(GET("/admin/adminusergroups/2"));
		assertCrudFlash(body, "flashSuccess", "The UserGroup has been saved");
		assertLongTextField(body, "description", "Renamed moderators group", "Max length is 1000.");
		assertCheckbox(body, "admin", false);
	}

	@Test
	public void saveWithANameOverTheMaxSizeRendersTheShowPageWithoutWriting() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusergroups/1", form("object.name", LONG_NAME, "object.description",
				"Admin"));
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertShowTitle(body, "UserGroup");
		assertTextFieldError(body, "name", LONG_NAME, "Maximum size is 50", "Required. Max length is 50.");
		assertDeleteForm(body, "/admin/adminusergroups/1");

		// The breadcrumb prints the mutated object, exactly as the rejected name is in the input
		assertBreadcrumbOf(body, "adminusergroups", "AdminUserGroups", "/admin/adminusergroups/1", LONG_NAME);
		UserGroup untouched = inTransaction(() -> UserGroup.findById(1L));
		assertEquals("Admin", untouched.name);
	}

	@Test
	public void saveOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, POST("/admin/adminusergroups/999", form("object.name", "X")));
	}

	// ~~~~~~~~~~~~ delete (DELETE /admin/adminusergroups/{id})

	@Test
	public void deleteOfAGroupThatUsersBelongToIsRefusedAndRedirectsToTheShowPage() throws Throwable {
		loginAdmin();
		// models.forum.User.group is a @ManyToOne to this row, so the foreign key blocks the delete
		Response response = DELETE("/admin/adminusergroups/1");
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusergroups/1");
		assertFlashValue(response, "error", "Cannot delete this object");

		String body = markup(GET("/admin/adminusergroups/1"));
		assertCrudFlash(body, "flashError", "Cannot delete this object");
		assertShowTitle(body, "UserGroup");
		assertNotContains("The UserGroup has been deleted", body);
		assertEquals(4, inTransaction(() -> UserGroup.count()).longValue());
	}

	@Test
	public void deleteOfAGroupNobodyBelongsToRemovesTheRow() throws Throwable {
		loginAdmin();
		POST("/admin/adminusergroups", form("object.name", "Extra"));
		assertEquals(5, inTransaction(() -> UserGroup.count()).longValue());

		Response response = DELETE("/admin/adminusergroups/5");
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusergroups");
		assertFlashValue(response, "success", "The UserGroup has been deleted");

		String body = markup(GET("/admin/adminusergroups"));
		assertCrudFlash(body, "flashSuccess", "The UserGroup has been deleted");
		assertRowCount(body, 4, "AdminUserGroups");
		assertNotContains("/admin/adminusergroups/5", body);
	}

	@Test
	public void deleteOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, DELETE("/admin/adminusergroups/999"));
	}

	// ~~~~~~~~~~~~ attachment (GET /admin/adminusergroups/{id}/{field})

	@Test
	public void attachmentIsNotFoundForAFieldThatIsNotABinaryAttachment() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminusergroups/1/name");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	@Test
	public void attachmentFailsForAFieldNameTheEntityDoesNotHave() throws Throwable {
		loginAdmin();
		try {
			GET("/admin/adminusergroups/1/zzz");
			fail("expected CRUD.attachment to ask for a field that does not exist");
		} catch (Throwable t) {
			assertTrue("unexpected: " + t.getMessage(), t.getMessage().contains("zzz"));
		}
	}

	// ~~~~~~~~~~~~ list (GET /admin/adminusergroups)

	@Test
	public void listPageCountsTheGroupsAndLinksEveryRowToItsShowPage() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminusergroups"));
		assertListTitle(body, "AdminUserGroups");
		assertSearchForm(body, "/admin/adminusergroups");
		assertListBreadcrumb(body, "adminusergroups", "AdminUserGroups");
		assertAddLink(body, "/admin/adminusergroups", "UserGroup");
		assertDefaultTableHead(body, "/admin/adminusergroups", "AdminUserGroups");
		assertRowCount(body, 4, "AdminUserGroups");
		assertNoPagination(body);
		assertListRow(body, "odd", "/admin/adminusergroups/1", "Admin");
		assertListRow(body, "even", "/admin/adminusergroups/2", "Moderators");
		assertListRow(body, "odd", "/admin/adminusergroups/3", "General");
		assertListRow(body, "even", "/admin/adminusergroups/4", "Anonymous");
		assertLayoutFooter(body);
	}

	@Test
	public void listPageSearchesTheKeywordsItIsGiven() throws Throwable {
		loginAdmin();
		// "modo" is not a substring of any name or description: lower(name) like '%modo%' finds nobody,
		// because models.forum.UserGroup's rows spell "Moderators" (m-o-d-e), not "Modo"
		String none = markup(GET("/admin/adminusergroups?search=modo&searchFields=name+description"));
		assertSearchFormWithKeywords(none, "/admin/adminusergroups", "modo", 4);
		assertDefaultTableHeadWithSearch(none, "/admin/adminusergroups", "modo", "AdminUserGroups");
		assertRowCount(none, 0, "AdminUserGroups");
		assertNotContains("/admin/adminusergroups/1", none);

		// "mod" does match: one row, and the total link still counts the whole table
		String one = markup(GET("/admin/adminusergroups?search=mod&searchFields=name+description"));
		assertSearchFormWithKeywords(one, "/admin/adminusergroups", "mod", 4);
		assertDefaultTableHeadWithSearch(one, "/admin/adminusergroups", "mod", "AdminUserGroups");
		assertRowCount(one, 1, "AdminUserGroups");
		assertListRow(one, "odd", "/admin/adminusergroups/2", "Moderators");
		assertNotContains("/admin/adminusergroups/1", one);
	}

	/**
	 * CRUD.list passes (String) request.args.get("where") to findPage and count, but a query string is
	 * parsed into request.params, so ?where=... never reaches the JPQL factory and cannot filter the rows.
	 */
	@Test
	public void whereClauseOfTheQueryStringIsNeverApplied() throws Throwable {
		loginAdmin();
		// admin=true would keep one row, admin=false would keep three: both list all four groups
		String wouldKeepOne = markup(GET("/admin/adminusergroups?where=admin%3Dtrue"));
		assertRowCount(wouldKeepOne, 4, "AdminUserGroups");
		assertListRow(wouldKeepOne, "odd", "/admin/adminusergroups/1", "Admin");
		assertListRow(wouldKeepOne, "even", "/admin/adminusergroups/4", "Anonymous");

		String wouldDropOne = markup(GET("/admin/adminusergroups?where=admin%3Dfalse"));
		assertRowCount(wouldDropOne, 4, "AdminUserGroups");
		assertListRow(wouldDropOne, "odd", "/admin/adminusergroups/1", "Admin");
	}
}
