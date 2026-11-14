import org.junit.Test;

import play.mvc.Http.Response;

import models.forum.User;

/**
 * Coverage of controllers.admin.AdminUsers, the CRUD wrapper of models.forum.User.
 *
 * models.forum.User exposes email (@Email @Required), passwordHash (@Required), name (@Required),
 * group (@Required @ManyToOne), uuid, confirmed (default FALSE) and active (default TRUE). The generated
 * key is id, so the relation is posted as object.group.id, and the field ids the templates write are
 * object_email, object_group, ... because FastTags._field turns the dots of a field name into underscores.
 *
 * The ids below come from test/data.yml: users 1..4 are Admin, Moderator, Bob and Anonymous, so a user
 * created through the form takes id 5 (Fixtures.deleteDatabase() truncates the tables, which restarts the
 * auto increment at 1 for every test method).
 */
public class CrudAdminUsersTest extends BaseCrudTest {

	// ~~~~~~~~~~~~ blank (GET /admin/adminusers/new)

	@Test
	public void blankFormShowsEveryEditableFieldOfUserWithItsHelpText() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminusers/new");
		assertStatus(200, response);
		String body = markup(response);

		assertLayoutHeader(body);
		assertBlankTitle(body, "User");
		assertBreadcrumb(body, "adminusers", "AdminUsers", "/admin/adminusers/new");
		assertCreateForm(body, "/admin/adminusers");
		assertBlankButtons(body);
		assertLayoutFooter(body);

		// flash.keep() of the login POST, which CRUD/layout.html prints before the form
		assertCrudFlash(body, "flashSuccess", "Welcome back Admin !");

		assertTextField(body, "email", "", "Valid email address. Required.");
		assertTextField(body, "passwordHash", "", "Required.");
		assertTextField(body, "name", "", "Required.");
		assertRelationField(body, "group", "(Please select a value)", "Required.", null, "1", "Admin", "2", "Moderators",
				"3", "General", "4", "Anonymous");

		// uuid carries no validation annotation, so its help span stays empty
		assertTextField(body, "uuid", "", "");

		// The declared defaults of confirmed and active are read from the freshly constructed object
		assertCheckbox(body, "confirmed", false);
		assertCheckbox(body, "active", true);

		// Nothing is bound to the form yet
		assertNotContains("selected=\"true\"", body);
	}

	// ~~~~~~~~~~~~ show (GET /admin/adminusers/{id})

	@Test
	public void showFormCarriesTheStoredValuesOfTheRow() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminusers/1");
		assertStatus(200, response);
		String body = markup(response);

		assertShowTitle(body, "User");
		assertBreadcrumbOf(body, "adminusers", "AdminUsers", "/admin/adminusers/1", "Admin");
		assertSaveForm(body, "/admin/adminusers/1");
		assertDeleteForm(body, "/admin/adminusers/1");
		assertShowButtons(body);
		assertDeleteButton(body, "User");
		assertLayoutFooter(body);
		// The login POST is the flash this page prints: it is the first request after login()
		assertLoginFlash(body);

		assertTextField(body, "email", "admin@domain.com", "Valid email address. Required.");
		assertTextField(body, "passwordHash", "5ebe2294ecd0e0f08eab7690d2a6ee69", "Required.");
		assertTextField(body, "name", "Admin", "Required.");
		assertRelationField(body, "group", "(Please select a value)", "Required.", "1", "1", "Admin", "2", "Moderators",
				"3", "General", "4", "Anonymous");
		assertCheckbox(body, "confirmed", true);
		assertCheckbox(body, "active", true);

		// Profile.Security.onAuthenticated() writes a four-character uuid on every login, so the value
		// cannot be pinned to a literal
		assertTextFieldMatching(body, "uuid", "[A-Za-z0-9]{4}", "");
	}

	@Test
	public void showOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminusers/999");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	// ~~~~~~~~~~~~ create (POST /admin/adminusers)

	@Test
	public void createWithoutAnyValueReportsEveryRequiredFieldAndKeepsNoCookie() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusers", form());
		assertStatus(200, response);
		String body = markup(response);

		// CRUD.create puts messages.get('crud.hasErrors') into renderArgs.error, which layout.html prints
		assertCrudFlash(body, "flashError", "Please correct errors");
		assertBlankTitle(body, "User");

		assertTextFieldError(body, "email", "", "Required", "Valid email address. Required.");
		assertTextFieldError(body, "passwordHash", "", "Required", "Required.");
		assertTextFieldError(body, "name", "", "Required", "Required.");
		assertRelationFieldError(body, "group", "(Please select a value)", "Required.", "Required", null, "1", "Admin",
				"2", "Moderators", "3", "General", "4", "Anonymous");

		// uuid, confirmed and active are not @Required, so they stay clean
		assertNotContains("object_uuid\" class=\"hasError\"", body);
		assertNotContains("object_confirmed\" class=\"hasError\"", body);
		assertNotContains("object_active\" class=\"hasError\"", body);

		// CRUD.create never calls validation.keep(), so the errors are not carried in PLAY_ERRORS
		assertEmptyMap(errorsOf(response));
		assertRowCount(markup(GET("/admin/adminusers")), 4, "AdminUsers");
	}

	@Test
	public void createWithABadEmailKeepsTheSubmittedValuesOfTheOtherFields() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusers", form("object.email", "nope", "object.passwordHash", "hash",
				"object.name", "Fresh", "object.group.id", "2", "object.confirmed", "true", "object.active", "true"));
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertTextFieldError(body, "email", "nope", "Invalid email address", "Valid email address. Required.");

		// params[field.name] wins over the object value, so the other fields show what was posted
		assertTextField(body, "passwordHash", "hash", "Required.");
		assertTextField(body, "name", "Fresh", "Required.");
		assertRelationField(body, "group", "(Please select a value)", "Required.", "2", "1", "Admin", "2", "Moderators",
				"3", "General", "4", "Anonymous");
		assertCheckbox(body, "confirmed", true);
		assertCheckbox(body, "active", true);
	}

	@Test
	public void createWithSaveRedirectsToTheListAndWritesTheRow() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusers", form("object.email", "fresh@domain.com", "object.passwordHash",
				"freshhash", "object.name", "Fresh", "object.group.id", "2", "object.confirmed", "true",
				"object.active", "true", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusers");
		assertFlashValue(response, "success", "The User has been created");

		String body = markup(GET("/admin/adminusers"));
		assertCrudFlash(body, "flashSuccess", "The User has been created");
		assertRowCount(body, 5, "AdminUsers");
		assertListRow(body, "odd", "/admin/adminusers/5", "Fresh");
		assertEquals(5, inTransaction(() -> User.count()).longValue());
		User fresh = inTransaction(() -> User.findById(5L));
		assertEquals("fresh@domain.com", fresh.email);
		assertEquals(Boolean.TRUE, fresh.active);
	}

	@Test
	public void createWithoutSaveRedirectsToTheNewRowAndKeepsTheDefaultsOfTheFieldsItWasNotGiven()
			throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusers", form("object.email", "fresh@domain.com", "object.passwordHash",
				"freshhash", "object.name", "Fresh", "object.group.id", "2"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusers/5");
		assertFlashValue(response, "success", "The User has been created");

		// The create POST consumed the login flash, so the target page prints the create flash
		String body = markup(GET("/admin/adminusers/5"));
		assertCrudFlash(body, "flashSuccess", "The User has been created");
		assertSaveForm(body, "/admin/adminusers/5");
		assertTextField(body, "name", "Fresh", "Required.");
		// Neither checkbox was posted, so CRUD.create keeps the field initializers of models.forum.User:
		// confirmed = Boolean.FALSE (unchecked) and active = Boolean.TRUE (checked)
		assertCheckbox(body, "confirmed", false);
		assertCheckbox(body, "active", true);
		// CRUD.create constructs the object itself, which never sets uuid
		assertTextField(body, "uuid", "", "");

		User fresh = inTransaction(() -> User.findById(5L));
		assertEquals(Boolean.FALSE, fresh.confirmed);
		assertEquals(Boolean.TRUE, fresh.active);
		assertNull(fresh.uuid);
	}

	@Test
	public void createWithSaveAndAddAnotherRedirectsToBlankAgain() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusers", form("object.email", "fresh@domain.com", "object.passwordHash",
				"freshhash", "object.name", "Fresh", "object.group.id", "2", "_saveAndAddAnother",
				"Save and create another"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusers/new");
		assertFlashValue(response, "success", "The User has been created");

		String body = markup(GET("/admin/adminusers/new"));
		assertCrudFlash(body, "flashSuccess", "The User has been created");
		assertBlankTitle(body, "User");
		assertRowCount(markup(GET("/admin/adminusers")), 5, "AdminUsers");
	}

	@Test
	public void createWithSaveAndContinueRedirectsToTheNewRow() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusers", form("object.email", "fresh@domain.com", "object.passwordHash",
				"freshhash", "object.name", "Fresh", "object.group.id", "2", "_saveAndContinue",
				"Save and continue editing"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusers/5");
		assertFlashValue(response, "success", "The User has been created");
	}

	// ~~~~~~~~~~~~ save (POST /admin/adminusers/{id})

	@Test
	public void saveOfAnInvalidEmailRendersTheShowPageAgainWithoutWriting() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusers/1", form("object.email", "nope", "object.passwordHash", "hash",
				"object.name", "Renamed", "object.group.id", "1"));
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertShowTitle(body, "User");
		assertTextFieldError(body, "email", "nope", "Invalid email address", "Valid email address. Required.");
		assertDeleteForm(body, "/admin/adminusers/1");

		// The breadcrumb prints object.toString(), and the object Binder mutated is the one Hibernate
		// still holds, so the rejected name appears even though the row was never written
		assertBreadcrumbOf(body, "adminusers", "AdminUsers", "/admin/adminusers/1", "Renamed");

		User unchanged = inTransaction(() -> User.findById(1L));
		assertEquals("Admin", unchanged.name);
		assertEquals("admin@domain.com", unchanged.email);
	}

	@Test
	public void saveWithoutSaveRedirectsBackToTheShowPage() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusers/2", form("object.email", "modo@domain.com", "object.passwordHash",
				"hash2", "object.name", "Renamed modo", "object.group.id", "2", "object.confirmed", "true",
				"object.active", "false"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusers/2");
		assertFlashValue(response, "success", "The User has been saved");

		String body = markup(GET("/admin/adminusers/2"));
		assertCrudFlash(body, "flashSuccess", "The User has been saved");
		assertTextField(body, "name", "Renamed modo", "Required.");
		assertTextField(body, "passwordHash", "hash2", "Required.");
		assertCheckbox(body, "active", false);
	}

	@Test
	public void saveWithSaveRedirectsToTheList() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminusers/3", form("object.email", "bob@domain.com", "object.passwordHash",
				"hash3", "object.name", "Renamed bob", "object.group.id", "3", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusers");
		assertFlashValue(response, "success", "The User has been saved");

		String body = markup(GET("/admin/adminusers"));
		assertCrudFlash(body, "flashSuccess", "The User has been saved");
		assertListRow(body, "odd", "/admin/adminusers/3", "Renamed bob");
	}

	@Test
	public void saveLeavesTheFieldsTheFormDidNotPostAlone() throws Throwable {
		loginAdmin();
		User admin = inTransaction(() -> User.findById(1L));
		// The login wrote admin's uuid; object.uuid is absent from the body, so Binder.bindBean does not
		// touch it and CRUD.save cannot erase it
		String uuid = admin.uuid;
		assertMatches("[A-Za-z0-9]{4}", uuid);

		POST("/admin/adminusers/1", form("object.email", "admin@domain.com", "object.passwordHash", "hash",
				"object.name", "Renamed", "object.group.id", "1"));
		User afterSave = inTransaction(() -> User.findById(1L));
		assertEquals(uuid, afterSave.uuid);
		assertEquals("Renamed", afterSave.name);
	}

	@Test
	public void saveOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, POST("/admin/adminusers/999", form("object.email", "x@domain.com", "object.passwordHash",
				"hash", "object.name", "X", "object.group.id", "1")));
	}

	// ~~~~~~~~~~~~ delete (DELETE /admin/adminusers/{id})

	@Test
	public void deleteOfAUserThatHasPostsIsRefusedAndRedirectsToTheShowPage() throws Throwable {
		loginAdmin();
		// Bob owns posts p1 and p2, so the foreign key Post.postedBy blocks the delete
		Response response = DELETE("/admin/adminusers/3");
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusers/3");
		assertFlashValue(response, "error", "Cannot delete this object");

		String body = markup(GET("/admin/adminusers/3"));
		assertCrudFlash(body, "flashError", "Cannot delete this object");
		assertShowTitle(body, "User");
		assertEquals(4, inTransaction(() -> User.count()).longValue());

		// CRUD.delete redirects from inside its catch, so the success flash below it is never written
		assertNotContains("The User has been deleted", body);
	}

	@Test
	public void deleteOfAnUnreferencedUserRemovesTheRow() throws Throwable {
		loginAdmin();
		POST("/admin/adminusers", form("object.email", "fresh@domain.com", "object.passwordHash", "freshhash",
				"object.name", "Fresh", "object.group.id", "2"));
		assertEquals(5, inTransaction(() -> User.count()).longValue());

		Response response = DELETE("/admin/adminusers/5");
		assertStatus(302, response);
		assertLocation(response, "/admin/adminusers");
		assertFlashValue(response, "success", "The User has been deleted");

		String body = markup(GET("/admin/adminusers"));
		assertCrudFlash(body, "flashSuccess", "The User has been deleted");
		assertRowCount(body, 4, "AdminUsers");
		assertNotContains("/admin/adminusers/5", body);
		assertEquals(4, inTransaction(() -> User.count()).longValue());
	}

	@Test
	public void deleteOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, DELETE("/admin/adminusers/999"));
	}

	// ~~~~~~~~~~~~ attachment (GET /admin/adminusers/{id}/{field})

	@Test
	public void attachmentIsNotFoundForAFieldThatIsNotABinaryAttachment() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminusers/1/email");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	@Test
	public void attachmentFailsForAFieldNameTheEntityDoesNotHave() throws Throwable {
		loginAdmin();
		try {
			GET("/admin/adminusers/1/zzz");
			fail("expected CRUD.attachment to ask for a field that does not exist");
		} catch (Throwable t) {
			// object.getClass().getField(field) throws NoSuchFieldException, which Play wraps
			assertTrue("unexpected: " + t.getMessage(), t.getMessage().contains("zzz"));
			assertTrue("unexpected cause: " + t.getCause(), t.getCause().getMessage().contains("zzz"));
			assertTrue("unexpected root cause: " + rootCause(t), rootCause(t) instanceof NoSuchFieldException);
		}
	}

	private static Throwable rootCause(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		return c;
	}

	// ~~~~~~~~~~~~ list (GET /admin/adminusers)

	@Test
	public void listPageOrdersByTheGeneratedKeyAndCountsTheRows() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminusers"));
		assertListTitle(body, "AdminUsers");
		assertSearchForm(body, "/admin/adminusers");
		assertListBreadcrumb(body, "adminusers", "AdminUsers");
		assertAddLink(body, "/admin/adminusers", "User");
		assertDefaultTableHead(body, "/admin/adminusers", "AdminUsers");
		assertRowCount(body, 4, "AdminUsers");
		assertNoPagination(body);
		assertListRow(body, "odd", "/admin/adminusers/1", "Admin");
		assertListRow(body, "even", "/admin/adminusers/2", "Moderator");
		assertListRow(body, "odd", "/admin/adminusers/3", "Bob");
		assertListRow(body, "even", "/admin/adminusers/4", "Anonymous");
		assertLayoutFooter(body);
	}

	@Test
	public void listPageTurnsPageZeroIntoTheFirstPage() throws Throwable {
		loginAdmin();
		// CRUD.list clamps page to 1, so ?page=0 renders the first page
		String body = markup(GET("/admin/adminusers?page=0"));
		assertRowCount(body, 4, "AdminUsers");
		assertDefaultTableHead(body, "/admin/adminusers", "AdminUsers");
		assertListRow(body, "odd", "/admin/adminusers/1", "Admin");
	}

	/**
	 * ?order=DESC on the generated key: tags/crud/table.html marks the column as sorted descending and its
	 * link asks for ASC again, and JPAModelLoader.BasicCRUDFactory.fetch reverses the rows. The odd/even
	 * classes follow the position in the list, not the id, so the first row of the reversed list is odd.
	 */
	@Test
	public void listPageSortsTheGeneratedKeyDescendingWhenAsked() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminusers?order=DESC"));
		assertSortedDescColumn(body, "/admin/adminusers", "AdminUsers");
		assertRowCount(body, 4, "AdminUsers");
		assertListRow(body, "odd", "/admin/adminusers/4", "Anonymous");
		assertListRow(body, "even", "/admin/adminusers/3", "Bob");
		assertListRow(body, "odd", "/admin/adminusers/2", "Moderator");
		assertListRow(body, "even", "/admin/adminusers/1", "Admin");
	}

	/**
	 * The search box sends searchFields as a space-separated list, which ObjectType.count splits on " ".
	 * JPAModelLoader.getSearchQuery only offers properties whose type is String, so the keyword is matched
	 * against lower(email) and lower(name) and one row survives.
	 */
	@Test
	public void listPageSearchesTheFieldsItKnowsAboutAndLinksTheTotalSize() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminusers?search=bob&searchFields=name+email");
		assertStatus(200, response);
		String body = markup(response);

		assertSearchFormWithKeywords(body, "/admin/adminusers", "bob", 4);
		assertDefaultTableHeadWithSearch(body, "/admin/adminusers", "bob", "AdminUsers");
		assertRowCount(body, 1, "AdminUsers");
		assertListRow(body, "odd", "/admin/adminusers/3", "Bob");
		assertNotContains("/admin/adminusers/1", body);
	}

	/** A keyword no String property contains: the count drops to 0 while the head stays. */
	@Test
	public void listPageSearchWithoutAMatchKeepsTheHeadAndTheTotalLink() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminusers?search=zeta&searchFields=name"));
		assertSearchFormWithKeywords(body, "/admin/adminusers", "zeta", 4);
		assertDefaultTableHeadWithSearch(body, "/admin/adminusers", "zeta", "AdminUsers");
		assertRowCount(body, 0, "AdminUsers");
		assertNotContains("<tr class=\"odd\">", body);
		assertNotContains("<tr class=\"even\">", body);
	}

	@Test
	public void listPagePaginatesOnceTheRowsOverflowThePageSize() throws Throwable {
		loginAdmin();
		// crud.pageSize is 30 (CRUD.getPageSize() falls back to the default), so 31 rows make two pages
		for (int i = 0; i < 27; i++) {
			POST("/admin/adminusers", form("object.email", "extra" + i + "@domain.com", "object.passwordHash",
					"hash" + i, "object.name", "Extra" + i, "object.group.id", "3"));
		}
		assertEquals(31, inTransaction(() -> User.count()).longValue());

		String first = markup(GET("/admin/adminusers"));
		assertRowCount(first, 31, "AdminUsers");
		assertFirstPagePagination(first, "/admin/adminusers");
		assertListRow(first, "even", "/admin/adminusers/30", "Extra25");
		assertNotContains("/admin/adminusers/31", first);

		String second = markup(GET("/admin/adminusers?page=2"));
		assertRowCount(second, 31, "AdminUsers");
		assertLastPagePagination(second, "/admin/adminusers");
		assertListRow(second, "odd", "/admin/adminusers/31", "Extra26");
		assertNotContains("/admin/adminusers/30", second);
	}

	/**
	 * A page beyond the rows keeps the count, the total link and the table head, and prints no row: the
	 * JPQL query is offset past the last result, and #{crud.pagination} writes nothing because the table
	 * holds fewer rows than crud.pageSize.
	 */
	@Test
	public void listPageBeyondTheRowsKeepsTheHeadAndCountsWithoutAnyRow() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminusers?page=2"));
		assertDefaultTableHead(body, "/admin/adminusers", "AdminUsers");
		assertRowCount(body, 4, "AdminUsers");
		assertNotContains("<tr class=\"odd\">", body);
		assertNotContains("<tr class=\"even\">", body);
		assertNoPagination(body);
	}

	/**
	 * CRUD.list passes (String) request.args.get("where") to findPage and count, but a query string is
	 * parsed into request.params, not request.args, so ?where=... is never a JPQL condition and the whole
	 * table is listed whatever clause is asked for.
	 */
	@Test
	public void whereClauseOfTheQueryStringIsNeverApplied() throws Throwable {
		loginAdmin();
		// A clause that would keep every row, and one that would keep none: both list the four users
		String all = markup(GET("/admin/adminusers?where=active%3Dtrue"));
		assertRowCount(all, 4, "AdminUsers");
		assertListRow(all, "even", "/admin/adminusers/4", "Anonymous");

		String unfiltered = markup(GET("/admin/adminusers?where=active%3Dfalse"));
		assertRowCount(unfiltered, 4, "AdminUsers");
		assertDefaultTableHead(unfiltered, "/admin/adminusers", "AdminUsers");
		assertListRow(unfiltered, "odd", "/admin/adminusers/1", "Admin");
		assertListRow(unfiltered, "even", "/admin/adminusers/4", "Anonymous");
	}
}
