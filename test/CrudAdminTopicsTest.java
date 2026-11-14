import org.junit.Test;

import play.mvc.Http.Response;

import models.forum.Post;
import models.forum.Topic;

/**
 * Coverage of controllers.admin.AdminTopics, the CRUD wrapper of models.forum.Topic.
 *
 * models.forum.Topic has forum (@ManyToOne, no @Required, so crud.relationField offers "(None)"), views
 * (Integer, default 0), locked and hidden (Boolean, default FALSE) and posts (@OneToMany, which renders as
 * crud_unknown). Nothing is @Required, so an empty POST creates a row instead of reporting errors, which is
 * the branch CRUD.create takes when validation passes with no bound value.
 *
 * The entity inherits play.db.jpa.Model.toString(), so the breadcrumb and the list rows print Topic[n].
 * test/data.yml provides topics 1..5 (t1, t2 locked, t3 hidden, news1, news2); a topic created through the
 * form takes id 6.
 */
public class CrudAdminTopicsTest extends BaseCrudTest {

	/** id, label pairs as crud.relationField lists them (forums are ordered by id here). */
	private static final String[] FORUM_OPTIONS = {"1", "mainForum1", "2", "mainForum2", "3", "modoForum1", "4",
			"modoForum2", "5", "Home Page"};

	// ~~~~~~~~~~~~ blank (GET /admin/admintopics/new)

	@Test
	public void blankFormOffersNoneForTheOptionalForumAndZeroForViews() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/admintopics/new");
		assertStatus(200, response);
		String body = markup(response);

		assertLayoutHeader(body);
		assertBlankTitle(body, "Topic");
		assertBreadcrumb(body, "admintopics", "AdminTopics", "/admin/admintopics/new");
		assertCreateForm(body, "/admin/admintopics");
		assertBlankButtons(body);
		assertLayoutFooter(body);
		assertCrudFlash(body, "flashSuccess", "Welcome back Admin !");

		// forum is not @Required, so crud.none replaces the "(Please select a value)" placeholder
		assertRelationField(body, "forum", "(None)", "", null, FORUM_OPTIONS);
		assertNumberField(body, "views", "0", "Numeric.");
		assertCheckbox(body, "locked", false);
		assertCheckbox(body, "hidden", false);
		assertUnknownField(body, "posts");
	}

	// ~~~~~~~~~~~~ show (GET /admin/admintopics/{id})

	@Test
	public void showFormCarriesTheForumAndFlagsOfTheRow() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/admintopics/2");
		assertStatus(200, response);
		String body = markup(response);

		assertShowTitle(body, "Topic");
		assertBreadcrumbOf(body, "admintopics", "AdminTopics", "/admin/admintopics/2", "Topic[2]");
		assertSaveForm(body, "/admin/admintopics/2");
		assertDeleteForm(body, "/admin/admintopics/2");
		assertShowButtons(body);
		assertDeleteButton(body, "Topic");
		assertLayoutFooter(body);

		assertRelationField(body, "forum", "(None)", "", "1", FORUM_OPTIONS);
		assertNumberField(body, "views", "0", "Numeric.");
		// t2 is the locked fixture
		assertCheckbox(body, "locked", true);
		assertCheckbox(body, "hidden", false);
		assertUnknownField(body, "posts");
	}

	@Test
	public void showFormTicksTheHiddenFlagOfTheHiddenTopic() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/admintopics/3"));
		assertBreadcrumbOf(body, "admintopics", "AdminTopics", "/admin/admintopics/3", "Topic[3]");
		assertCheckbox(body, "locked", false);
		assertCheckbox(body, "hidden", true);
	}

	@Test
	public void showOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, GET("/admin/admintopics/999"));
	}

	// ~~~~~~~~~~~~ create (POST /admin/admintopics)

	@Test
	public void createWithoutAnyValueStillWritesARowBecauseNoFieldIsRequired() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/admintopics", form());
		assertStatus(302, response);
		assertLocation(response, "/admin/admintopics/6");
		assertFlashValue(response, "success", "The Topic has been created");
		assertNotContains("Please correct errors", markup(GET("/admin/admintopics/new")));

		String body = markup(GET("/admin/admintopics/6"));
		// The blank page fetched above already consumed the create flash, so this page shows no flash
		assertNoCrudFlash(body);
		// No forum was posted, so the select falls back to the (None) placeholder
		assertRelationField(body, "forum", "(None)", "", null, FORUM_OPTIONS);
		assertNumberField(body, "views", "0", "Numeric.");
		assertCheckbox(body, "locked", false);
		assertCheckbox(body, "hidden", false);
		assertEquals(6, inTransaction(() -> Topic.count()).longValue());
		Topic bareTopic = inTransaction(() -> Topic.findById(6L));
		assertTrue("expected the new topic to have no forum", bareTopic.forum == null);
	}

	@Test
	public void createIgnoresThePostsCollectionTheFormCannotEdit() throws Throwable {
		loginAdmin();
		POST("/admin/admintopics", form("object.forum.id", "1", "object.views", "7", "object.locked", "true",
				"object.hidden", "false", "object.posts.id", "1", "_save", "Save"));

		assertEquals(6, inTransaction(() -> Topic.count()).longValue());
		assertEquals(6, inTransaction(() -> Post.count()).longValue());
		// Topic.posts is a lazy @OneToMany, so it can only be read while the transaction is still open
		boolean newTopicHasPosts = inTransaction(() -> {
			Topic createdTopic = Topic.findById(6L);
			return createdTopic.posts != null && !createdTopic.posts.isEmpty();
		});
		assertFalse("expected no posts on the new topic", newTopicHasPosts);
		Post firstPost = inTransaction(() -> Post.findById(1L));
		assertEquals(Long.valueOf(1L), firstPost.topic.id);
	}

	@Test
	public void createWithSaveRedirectsToTheListAndShowsTheNewRow() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/admintopics", form("object.forum.id", "2", "object.views", "4", "_save",
				"Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/admintopics");
		assertFlashValue(response, "success", "The Topic has been created");

		String body = markup(GET("/admin/admintopics"));
		assertCrudFlash(body, "flashSuccess", "The Topic has been created");
		assertRowCount(body, 6, "AdminTopics");
		assertListRow(body, "even", "/admin/admintopics/6", "Topic[6]");
		Topic freshTopic = inTransaction(() -> Topic.findById(6L));
		assertEquals(Integer.valueOf(4), freshTopic.views);
	}

	@Test
	public void createWithSaveAndAddAnotherRedirectsToBlankAgain() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/admintopics", form("object.forum.id", "2", "_saveAndAddAnother",
				"Save and create another"));
		assertStatus(302, response);
		assertLocation(response, "/admin/admintopics/new");
		assertFlashValue(response, "success", "The Topic has been created");
		assertBlankTitle(markup(GET("/admin/admintopics/new")), "Topic");
	}

	// ~~~~~~~~~~~~ save (POST /admin/admintopics/{id})

	@Test
	public void saveWithSaveRedirectsToTheListAndWritesTheRow() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/admintopics/1", form("object.forum.id", "2", "object.views", "12",
				"object.locked", "false", "object.hidden", "true", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/admintopics");
		assertFlashValue(response, "success", "The Topic has been saved");

		String body = markup(GET("/admin/admintopics/1"));
		assertCrudFlash(body, "flashSuccess", "The Topic has been saved");
		assertRelationField(body, "forum", "(None)", "", "2", FORUM_OPTIONS);
		assertNumberField(body, "views", "12", "Numeric.");
		assertCheckbox(body, "hidden", true);
		Topic movedTopic = inTransaction(() -> Topic.findById(1L));
		assertEquals(Long.valueOf(2L), movedTopic.forum.id);
	}

	@Test
	public void saveWithoutSaveRedirectsBackToTheShowPage() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/admintopics/4", form("object.forum.id", "5", "object.views", "3"));
		assertStatus(302, response);
		assertLocation(response, "/admin/admintopics/4");
		assertFlashValue(response, "success", "The Topic has been saved");

		String body = markup(GET("/admin/admintopics/4"));
		assertBreadcrumbOf(body, "admintopics", "AdminTopics", "/admin/admintopics/4", "Topic[4]");
		assertNumberField(body, "views", "3", "Numeric.");
		// object.locked was not posted, so the hidden false input wrote FALSE
		assertCheckbox(body, "locked", false);
	}

	@Test
	public void saveWithANonNumericViewsRendersTheShowPageWithoutWriting() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/admintopics/2", form("object.forum.id", "1", "object.views", "abc"));
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertShowTitle(body, "Topic");
		assertNumberFieldError(body, "views", "abc", "Incorrect value", "Numeric.");
		assertDeleteForm(body, "/admin/admintopics/2");
		Topic rejectedTopic = inTransaction(() -> Topic.findById(2L));
		assertEquals(Integer.valueOf(0), rejectedTopic.views);
	}

	@Test
	public void saveOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, POST("/admin/admintopics/999", form("object.views", "1")));
	}

	// ~~~~~~~~~~~~ delete (DELETE /admin/admintopics/{id})

	@Test
	public void deleteOfATopicCascadesOntoItsPosts() throws Throwable {
		loginAdmin();
		assertEquals(5, inTransaction(() -> Topic.count()).longValue());
		assertEquals(6, inTransaction(() -> Post.count()).longValue());

		// news2 is topic 5 and owns post p6, which Topic.posts cascades
		Response response = DELETE("/admin/admintopics/5");
		assertStatus(302, response);
		assertLocation(response, "/admin/admintopics");
		assertFlashValue(response, "success", "The Topic has been deleted");

		String body = markup(GET("/admin/admintopics"));
		assertCrudFlash(body, "flashSuccess", "The Topic has been deleted");
		assertRowCount(body, 4, "AdminTopics");
		assertNotContains("/admin/admintopics/5", body);
		assertEquals(4, inTransaction(() -> Topic.count()).longValue());
		assertEquals(5, inTransaction(() -> Post.count()).longValue());
	}

	@Test
	public void deleteOfAHiddenTopicWorksBecauseTheCrudFormIsNotTheForumPage() throws Throwable {
		loginAdmin();
		Response response = DELETE("/admin/admintopics/3");
		assertStatus(302, response);
		assertLocation(response, "/admin/admintopics");
		assertFlashValue(response, "success", "The Topic has been deleted");
		assertEquals(4, inTransaction(() -> Topic.count()).longValue());
	}

	@Test
	public void deleteOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, DELETE("/admin/admintopics/999"));
	}

	// ~~~~~~~~~~~~ attachment (GET /admin/admintopics/{id}/{field})

	@Test
	public void attachmentIsNotFoundForAFieldThatIsNotABinaryAttachment() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/admintopics/1/forum");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	@Test
	public void attachmentFailsForAFieldNameTheEntityDoesNotHave() throws Throwable {
		loginAdmin();
		try {
			GET("/admin/admintopics/1/zzz");
			fail("expected CRUD.attachment to ask for a field that does not exist");
		} catch (Throwable t) {
			assertTrue("unexpected: " + t.getMessage(), t.getMessage().contains("zzz"));
		}
	}

	// ~~~~~~~~~~~~ list (GET /admin/admintopics)

	@Test
	public void listPageListsEveryTopicIncludingTheHiddenAndLockedOnes() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/admintopics"));
		assertListBreadcrumb(body, "admintopics", "AdminTopics");
		assertAddLink(body, "/admin/admintopics", "Topic");
		assertListTitle(body, "AdminTopics");
		assertSearchForm(body, "/admin/admintopics");
		assertDefaultTableHead(body, "/admin/admintopics", "AdminTopics");
		assertRowCount(body, 5, "AdminTopics");
		assertNoPagination(body);
		assertListRow(body, "odd", "/admin/admintopics/1", "Topic[1]");
		assertListRow(body, "even", "/admin/admintopics/2", "Topic[2]");
		assertListRow(body, "odd", "/admin/admintopics/3", "Topic[3]");
		assertListRow(body, "even", "/admin/admintopics/4", "Topic[4]");
		assertListRow(body, "odd", "/admin/admintopics/5", "Topic[5]");
		assertLayoutFooter(body);
	}

	/**
	 * CRUD.list passes (String) request.args.get("where") to findPage and count, but the query string is
	 * parsed into request.params, so ?where=views>0 is not a JPQL condition: the five fixture topics stay,
	 * and views is not compared.
	 */
	@Test
	public void whereClauseOfTheQueryStringIsNeverApplied() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/admintopics?where=views%3E0"));
		assertDefaultTableHead(body, "/admin/admintopics", "AdminTopics");
		assertRowCount(body, 5, "AdminTopics");
		assertListRow(body, "odd", "/admin/admintopics/1", "Topic[1]");
		assertListRow(body, "even", "/admin/admintopics/2", "Topic[2]");
		assertListRow(body, "odd", "/admin/admintopics/3", "Topic[3]");
		assertListRow(body, "even", "/admin/admintopics/4", "Topic[4]");
		assertListRow(body, "odd", "/admin/admintopics/5", "Topic[5]");
	}

	/**
	 * A page beyond the rows keeps the count and the table head and prints no row; #{crud.pagination} stays
	 * silent because models.forum.Topic has fewer rows than crud.pageSize (30).
	 */
	@Test
	public void listPageBeyondTheRowsKeepsTheHeadAndCountsWithoutAnyRow() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/admintopics?page=2"));
		assertDefaultTableHead(body, "/admin/admintopics", "AdminTopics");
		assertRowCount(body, 5, "AdminTopics");
		assertNotContains("<tr class=\"odd\">", body);
		assertNotContains("<tr class=\"even\">", body);
		assertNoPagination(body);
	}
}
