import org.junit.Test;

import play.mvc.Http.Response;

import models.forum.Forum;
import models.forum.Post;
import models.forum.Topic;

/**
 * Coverage of controllers.admin.AdminForums, the CRUD wrapper of models.forum.Forum.
 *
 * models.forum.Forum declares name (@Required @MaxSize(50)), description (@Required @MaxSize(1000)),
 * displayOrder (@Required Integer), category (@Required @ManyToOne Category), locked (Boolean, default
 * FALSE) and topics (@OneToMany, mappedBy = "forum"). tags/crud/form.html therefore renders a text
 * field, a long text field, a number field, a select, a checkbox and one crud_unknown block:
 * CRUD$ObjectType.ObjectField only turns a CharSequence into text (and into longtext when @MaxSize is
 * above 100), a Number into number, a Boolean into boolean and a Date into date, while a collection
 * mapped the other way round is not a relation of this entity, so its type stays "unknown".
 * Forum.toString() is the name, which is what the breadcrumb and the show title print.
 *
 * Unlike the other five controllers, this one has an app template for the list page:
 * app/views/admin/AdminForums/list.html overrides the module's, and CRUD.list renders the override. Its
 * markup (two sortable columns, a name link plus a description cell) is asserted by CrudAdminPagesTest;
 * this suite only follows list redirects to pin the rows and the flash of its own actions.
 *
 * Every case fetches at least one page after login(), because Secure.authenticate() leaves a
 * "Welcome back Admin !" flash that the first rendered page prints. Cases that want a page without a
 * flash consume it with an earlier request.
 */
public class CrudAdminForumsTest extends BaseCrudTest {

	/** id, label pairs as crud.relationField lists the Category rows of the category select. */
	private static final String[] CATEGORY_OPTIONS = {"1", "Moderators Category", "2", "Main Category"};

	/** Forum.locked is a Boolean with no annotation, so crud.checkField writes no help text. */
	private static final String NAME_HELP = "Required. Max length is 50.";

	private static final String DESCRIPTION_HELP = "Required. Max length is 1000.";

	private static final String DISPLAY_ORDER_HELP = "Numeric. Required.";

	private static final String CATEGORY_HELP = "Required.";

	// ~~~~~~~~~~~~ blank (GET /admin/adminforums/new)

	@Test
	public void blankFormOffersTheFiveEditableFieldsAndTheTopicsCollectionItCannotEdit() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminforums/new");
		assertStatus(200, response);
		String body = markup(response);

		assertLayoutHeader(body);
		assertBlankTitle(body, "Forum");
		assertBreadcrumb(body, "adminforums", "AdminForums", "/admin/adminforums/new");
		assertCreateForm(body, "/admin/adminforums");
		assertBlankButtons(body);
		assertLayoutFooter(body);
		// CRUD/blank.html is the module template: the app overrides only list.html
		assertLoginFlash(body);

		assertTextField(body, "name", "", NAME_HELP);
		assertLongTextField(body, "description", "", DESCRIPTION_HELP);
		assertNumberField(body, "displayOrder", "", DISPLAY_ORDER_HELP);
		assertRelationField(body, "category", "(Please select a value)", CATEGORY_HELP, null, CATEGORY_OPTIONS);
		// Forum.locked = Boolean.FALSE, so the box starts unticked and the hidden input carries the false
		assertCheckbox(body, "locked", false);
		assertUnknownField(body, "topics");
	}

	// ~~~~~~~~~~~~ show (GET /admin/adminforums/{id})

	@Test
	public void showFormCarriesTheStoredValuesOfTheForum() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminforums/1");
		assertStatus(200, response);
		String body = markup(response);

		assertShowTitle(body, "Forum");
		assertBreadcrumbOf(body, "adminforums", "AdminForums", "/admin/adminforums/1", "mainForum1");
		assertSaveForm(body, "/admin/adminforums/1");
		assertDeleteForm(body, "/admin/adminforums/1");
		assertShowButtons(body);
		assertDeleteButton(body, "Forum");
		assertLayoutFooter(body);
		assertLoginFlash(body);

		assertTextField(body, "name", "mainForum1", NAME_HELP);
		assertLongTextField(body, "description", "Main forum 1 description", DESCRIPTION_HELP);
		assertNumberField(body, "displayOrder", "1", DISPLAY_ORDER_HELP);
		// test/data.yml gives mainForum1 the second Category, so option 2 is the selected one
		assertRelationField(body, "category", "(Please select a value)", CATEGORY_HELP, "2", CATEGORY_OPTIONS);
		assertCheckbox(body, "locked", false);
		assertUnknownField(body, "topics");
	}

	@Test
	public void showFormTicksTheBoxOfAForumStoredAsLocked() throws Throwable {
		loginAdmin();
		POST("/admin/adminforums/3", form("object.name", "modoForum1", "object.description",
				"Moderators forum 1 description", "object.displayOrder", "3", "object.category.id", "1",
				"object.locked", "true"));

		String body = markup(GET("/admin/adminforums/3"));
		assertCheckbox(body, "locked", true);
		Forum forum3 = inTransaction(() -> Forum.findById(3L));
		assertEquals(Boolean.TRUE, forum3.locked);
	}

	@Test
	public void showOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminforums/999");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	// ~~~~~~~~~~~~ create (POST /admin/adminforums)

	@Test
	public void createWithoutAnyValueReportsEveryRequiredField() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminforums", form());
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertBlankTitle(body, "Forum");
		// CRUD.create never calls validation.keep(), so nothing lands in the PLAY_ERRORS cookie
		assertEmptyMap(errorsOf(response));

		assertTextFieldError(body, "name", "", "Required", NAME_HELP);
		assertLongTextFieldError(body, "description", "", "Required", DESCRIPTION_HELP);
		assertNumberFieldError(body, "displayOrder", "", "Required", DISPLAY_ORDER_HELP);
		assertRelationFieldError(body, "category", "(Please select a value)", CATEGORY_HELP, "Required", null,
				CATEGORY_OPTIONS);
		assertCheckbox(body, "locked", false);
		assertUnknownField(body, "topics");

		assertEquals(5, inTransaction(() -> Forum.count()).longValue());
	}

	/**
	 * The four scalar fields survive a failed create while the relation is reported: CRUD.create binds the
	 * posted values onto the object it constructed, and #{crud.form} reads them from that object, so only
	 * object.category.id stays empty.
	 */
	@Test
	public void createWithoutTheCategoryRelationReportsTheRelationAndKeepsThePostedValues() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminforums", form("object.name", "Fresh", "object.description",
				"Fresh forum", "object.displayOrder", "6", "object.locked", "true"));
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertTextField(body, "name", "Fresh", NAME_HELP);
		assertLongTextField(body, "description", "Fresh forum", DESCRIPTION_HELP);
		assertNumberField(body, "displayOrder", "6", DISPLAY_ORDER_HELP);
		assertRelationFieldError(body, "category", "(Please select a value)", CATEGORY_HELP, "Required", null,
				CATEGORY_OPTIONS);
		assertCheckbox(body, "locked", true);

		assertEquals(5, inTransaction(() -> Forum.count()).longValue());
	}

	@Test
	public void createWithSaveRedirectsToTheListAndListsTheNewRow() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminforums", form("object.name", "Fresh", "object.description",
				"Fresh forum", "object.displayOrder", "6", "object.category.id", "2", "object.locked", "false",
				"_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminforums");
		assertFlashValue(response, "success", "The Forum has been created");

		// app/views/admin/AdminForums/list.html renders, so the redirect target can be followed
		String body = markup(GET("/admin/adminforums"));
		assertCrudFlash(body, "flashSuccess", "The Forum has been created");
		assertListTitle(body, "AdminForums");
		assertListBreadcrumb(body, "adminforums", "AdminForums");
		assertNameAndDescriptionTableHead(body, "/admin/adminforums");
		assertRowCount(body, 6, "AdminForums");
		assertNoPagination(body);
		assertForumListRow(body, "odd", "/admin/adminforums/1", "mainForum1", "Main forum 1 description");
		assertForumListRow(body, "even", "/admin/adminforums/2", "mainForum2", "Main forum 2 description");
		assertForumListRow(body, "odd", "/admin/adminforums/3", "modoForum1", "Moderators forum 1 description");
		assertForumListRow(body, "even", "/admin/adminforums/4", "modoForum2", "Moderators forum 2 description");
		assertForumListRow(body, "odd", "/admin/adminforums/5", "Home Page", "News, shown on the home page");
		assertForumListRow(body, "even", "/admin/adminforums/6", "Fresh", "Fresh forum");
		assertAddLink(body, "/admin/adminforums", "Forum");
		assertLayoutFooter(body);

		Forum created = inTransaction(() -> Forum.findById(6L));
		assertEquals("Fresh", created.name);
		assertEquals(Integer.valueOf(6), created.displayOrder);
		assertEquals(Long.valueOf(2L), created.category.id);
		assertEquals(Boolean.FALSE, created.locked);
	}

	@Test
	public void createWithoutSaveRedirectsToTheNewRowAndIgnoresTheTopicsCollection() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminforums", form("object.name", "Fresh", "object.description",
				"Fresh forum", "object.displayOrder", "6", "object.category.id", "2", "object.topics.id", "1"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminforums/6");
		assertFlashValue(response, "success", "The Forum has been created");

		String body = markup(GET("/admin/adminforums/6"));
		assertCrudFlash(body, "flashSuccess", "The Forum has been created");
		assertBreadcrumbOf(body, "adminforums", "AdminForums", "/admin/adminforums/6", "Fresh");
		assertTextField(body, "name", "Fresh", NAME_HELP);
		assertLongTextField(body, "description", "Fresh forum", DESCRIPTION_HELP);
		assertNumberField(body, "displayOrder", "6", DISPLAY_ORDER_HELP);
		assertRelationField(body, "category", "(Please select a value)", CATEGORY_HELP, "2", CATEGORY_OPTIONS);
		// object.locked was not posted, so the hidden false input decided it
		assertCheckbox(body, "locked", false);

		assertEquals(6, inTransaction(() -> Forum.count()).longValue());
		// crud_unknown renders no input, so object.topics.id is nothing the binder can read
		assertEquals(5, inTransaction(() -> Topic.count()).longValue());
		Topic topic1 = inTransaction(() -> Topic.findById(1L));
		assertEquals(Long.valueOf(1L), topic1.forum.id);
	}

	// ~~~~~~~~~~~~ save (POST /admin/adminforums/{id})

	@Test
	public void saveWithSaveRedirectsToTheListAndRewritesEveryField() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminforums/5", form("object.name", "Renamed home", "object.description",
				"Renamed news forum", "object.displayOrder", "5", "object.category.id", "1", "object.locked",
				"true", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminforums");
		assertFlashValue(response, "success", "The Forum has been saved");
		assertEmptyMap(errorsOf(response));

		String body = markup(GET("/admin/adminforums"));
		assertCrudFlash(body, "flashSuccess", "The Forum has been saved");
		assertRowCount(body, 5, "AdminForums");
		// forum 5 is the fifth row of the list, so its class is odd
		assertForumListRow(body, "odd", "/admin/adminforums/5", "Renamed home", "Renamed news forum");

		Forum renamed = inTransaction(() -> Forum.findById(5L));
		assertEquals("Renamed home", renamed.name);
		assertEquals("Renamed news forum", renamed.description);
		assertEquals(Long.valueOf(1L), renamed.category.id);
		assertEquals(Boolean.TRUE, renamed.locked);
	}

	/**
	 * Without _save, CRUD.save redirects back to show(id). object.locked is absent from the body, and
	 * crud.checkField always posts a hidden input with the false value, so the box is unticked in the form
	 * and the row ends with locked = false.
	 */
	@Test
	public void saveWithoutSaveAndWithoutTheCheckboxRedirectsToTheShowPageAndWritesFalse() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminforums/2", form("object.name", "Renamed main2", "object.description",
				"Renamed description", "object.displayOrder", "3", "object.category.id", "1"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminforums/2");
		assertFlashValue(response, "success", "The Forum has been saved");

		String body = markup(GET("/admin/adminforums/2"));
		assertCrudFlash(body, "flashSuccess", "The Forum has been saved");
		assertBreadcrumbOf(body, "adminforums", "AdminForums", "/admin/adminforums/2", "Renamed main2");
		assertTextField(body, "name", "Renamed main2", NAME_HELP);
		assertLongTextField(body, "description", "Renamed description", DESCRIPTION_HELP);
		assertNumberField(body, "displayOrder", "3", DISPLAY_ORDER_HELP);
		assertRelationField(body, "category", "(Please select a value)", CATEGORY_HELP, "1", CATEGORY_OPTIONS);
		assertCheckbox(body, "locked", false);

		Forum saved = inTransaction(() -> Forum.findById(2L));
		assertEquals("Renamed main2", saved.name);
		assertEquals(Long.valueOf(1L), saved.category.id);
		assertEquals(Boolean.FALSE, saved.locked);
	}

	/**
	 * An omitted @Required scalar is not reported either: CRUD.save binds the posted values onto the row
	 * loaded by GenericModel.edit(), and Validation.valid() runs on that object, whose displayOrder is
	 * still the one read from the database. Only the posted fields are written.
	 */
	@Test
	public void saveWithoutTheDisplayOrderStillWritesBecauseTheLoadedRowIsValid() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminforums/1", form("object.name", "Renamed forum", "object.description",
				"Renamed description", "object.category.id", "1"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminforums/1");
		assertFlashValue(response, "success", "The Forum has been saved");
		assertEmptyMap(errorsOf(response));

		String body = markup(GET("/admin/adminforums/1"));
		assertCrudFlash(body, "flashSuccess", "The Forum has been saved");
		assertTextField(body, "name", "Renamed forum", NAME_HELP);
		assertLongTextField(body, "description", "Renamed description", DESCRIPTION_HELP);
		// displayOrder was never posted, so the form still shows the value read from the row
		assertNumberField(body, "displayOrder", "1", DISPLAY_ORDER_HELP);
		assertRelationField(body, "category", "(Please select a value)", CATEGORY_HELP, "1", CATEGORY_OPTIONS);

		Forum saved = inTransaction(() -> Forum.findById(1L));
		assertEquals("Renamed forum", saved.name);
		assertEquals("Renamed description", saved.description);
		assertEquals(Integer.valueOf(1), saved.displayOrder);
		assertEquals(Long.valueOf(1L), saved.category.id);
	}

	@Test
	public void saveOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminforums/999", form("object.name", "X", "object.description",
				"X forum", "object.displayOrder", "1", "object.category.id", "2"));
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	// ~~~~~~~~~~~~ delete (DELETE /admin/adminforums/{id})

	/**
	 * Forum.topics is @OneToMany(cascade = CascadeType.ALL), so removing homePage takes its two news
	 * topics with them, and Topic.posts cascades further down to their posts.
	 */
	@Test
	public void deleteOfAForumWithTopicsCascadesToTheTopicsAndTheirPosts() throws Throwable {
		loginAdmin();
		assertEquals(5, inTransaction(() -> Forum.count()).longValue());
		assertEquals(5, inTransaction(() -> Topic.count()).longValue());
		assertEquals(6, inTransaction(() -> Post.count()).longValue());

		Response response = DELETE("/admin/adminforums/5");
		assertStatus(302, response);
		assertLocation(response, "/admin/adminforums");
		assertFlashValue(response, "success", "The Forum has been deleted");

		assertEquals(4, inTransaction(() -> Forum.count()).longValue());
		assertEquals(3, inTransaction(() -> Topic.count()).longValue());
		assertEquals(4, inTransaction(() -> Post.count()).longValue());
		assertNull(inTransaction(() -> Forum.findById(5L)));
		assertNull(inTransaction(() -> Topic.findById(4L)));
		assertNull(inTransaction(() -> Topic.findById(5L)));
		assertNull(inTransaction(() -> Post.findById(5L)));
		assertNull(inTransaction(() -> Post.findById(6L)));

		String body = markup(GET("/admin/adminforums"));
		assertCrudFlash(body, "flashSuccess", "The Forum has been deleted");
		assertRowCount(body, 4, "AdminForums");
		assertForumListRow(body, "odd", "/admin/adminforums/1", "mainForum1", "Main forum 1 description");
		assertForumListRow(body, "even", "/admin/adminforums/2", "mainForum2", "Main forum 2 description");
		assertForumListRow(body, "odd", "/admin/adminforums/3", "modoForum1", "Moderators forum 1 description");
		assertForumListRow(body, "even", "/admin/adminforums/4", "modoForum2", "Moderators forum 2 description");
		assertNotContains("/admin/adminforums/5", body);
	}

	@Test
	public void deleteOfAForumWithoutTopicsLeavesTheTopicsAndPostsAlone() throws Throwable {
		loginAdmin();
		Response response = DELETE("/admin/adminforums/2");
		assertStatus(302, response);
		assertLocation(response, "/admin/adminforums");
		assertFlashValue(response, "success", "The Forum has been deleted");

		assertEquals(4, inTransaction(() -> Forum.count()).longValue());
		assertEquals(5, inTransaction(() -> Topic.count()).longValue());
		assertEquals(6, inTransaction(() -> Post.count()).longValue());
		assertNull(inTransaction(() -> Forum.findById(2L)));
		assertNotNull(inTransaction(() -> Topic.findById(1L)));
		assertNotNull(inTransaction(() -> Post.findById(1L)));

		String body = markup(GET("/admin/adminforums"));
		assertCrudFlash(body, "flashSuccess", "The Forum has been deleted");
		assertRowCount(body, 4, "AdminForums");
		assertForumListRow(body, "odd", "/admin/adminforums/1", "mainForum1", "Main forum 1 description");
		// the parity follows the position in the list, so forum 3 lands on the second row and is even
		assertForumListRow(body, "even", "/admin/adminforums/3", "modoForum1", "Moderators forum 1 description");
		assertForumListRow(body, "odd", "/admin/adminforums/4", "modoForum2", "Moderators forum 2 description");
		assertForumListRow(body, "even", "/admin/adminforums/5", "Home Page", "News, shown on the home page");
		assertNotContains("/admin/adminforums/2", body);
	}

	@Test
	public void deleteOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		Response response = DELETE("/admin/adminforums/999");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	// ~~~~~~~~~~~~ guards of the write actions

	@Test
	public void anonymousIsRedirectedAndAModeratorIsForbiddenForBothWriteActions() throws Throwable {
		clearCookies();
		Response anonymous = POST("/admin/adminforums", form("object.name", "Nope", "object.description",
				"Nope forum", "object.displayOrder", "6", "object.category.id", "2"));
		assertStatus(302, anonymous);
		assertLocation(anonymous, "/login");
		// Secure.checkAccess() redirects to login(), which keeps "/" as the url to come back to
		assertFlashValue(anonymous, "url", "/");
		assertEquals(5, inTransaction(() -> Forum.count()).longValue());

		clearCookies();
		login("modo@domain.com", "secret");
		Response moderator = DELETE("/admin/adminforums/1");
		assertStatus(403, moderator);
		String body = markup(moderator);
		assertForbiddenBody(body);
		// errors/403.html is the app's template, so the kept login flash is an alert, not a crudFlash
		assertSuccessAlert("Welcome back Moderator !", body);
		assertNotContains("<div id=\"crudHeader\"", body);

		assertEquals(5, inTransaction(() -> Forum.count()).longValue());
		assertEquals(5, inTransaction(() -> Topic.count()).longValue());
	}

	// ~~~~~~~~~~~~ attachment (GET /admin/adminforums/{id}/{field})

	@Test
	public void attachmentIsNotFoundForAFieldThatIsNotABinaryAttachment() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminforums/1/name");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	/**
	 * CRUD.attachment reads the field by reflection and only streams Model.BinaryField or the deprecated
	 * play.db.jpa.FileAttachment; Forum has neither, so every field of this entity ends with notFound().
	 */
	@Test
	public void attachmentIsNotFoundForTheCollectionField() throws Throwable {
		loginAdmin();
		assertStatus(404, GET("/admin/adminforums/1/topics"));
	}

	@Test
	public void attachmentFailsForAFieldNameTheEntityDoesNotHave() throws Throwable {
		loginAdmin();
		try {
			GET("/admin/adminforums/1/zzz");
			fail("expected CRUD.attachment to ask for a field that does not exist");
		} catch (Throwable t) {
			// object.getClass().getField(field) throws NoSuchFieldException, which Play wraps
			assertCauseChain(t, "play.exceptions.JavaExecutionException: zzz", "java.lang.NoSuchFieldException: zzz");
			assertTrue("expected a NoSuchFieldException at the root of the chain",
					causeRoot(t) instanceof NoSuchFieldException);
		}
	}

	/** The forum relation of models.forum.Topic, offered by AdminTopics.show, lists the same rows. */
	@Test
	public void theForumOptionsOfTheTopicFormAreOrderedById() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/admintopics/1"));
		// Topic.forum is a @ManyToOne without @Required, so crud.relationField offers "(None)"
		assertRelationField(body, "forum", "(None)", "", "1", "1", "mainForum1", "2", "mainForum2", "3",
				"modoForum1", "4", "modoForum2", "5", "Home Page");
	}
}
