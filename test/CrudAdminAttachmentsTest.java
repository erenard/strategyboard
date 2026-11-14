import java.io.File;

import org.junit.Before;
import org.junit.Test;

import play.libs.IO;
import play.mvc.Http.Response;

import models.forum.Attachment;

/**
 * Coverage of controllers.admin.AdminAttachments, the CRUD wrapper of models.forum.Attachment.
 *
 * models.forum.Attachment declares post, message and user as optional @ManyToOne relations (so
 * crud.relationField offers "(None)"), then mimetype, description, downloads, filename (@Required), at
 * (@As("yyyy-MM-dd")), size and realfilename (@Required). Message.toString()/Post.toString() style labels
 * are what the relation options and the list rows print, because Attachment.toString() concatenates
 * mimetype, filename, user and post.
 *
 * Attachment has no Binary field, so CRUD.attachment always ends with notFound() (see
 * modules/crud/app/controllers/CRUD.java), and tags/crud/fileField.html is never rendered for this entity.
 *
 * test/attachments-data.yml adds attachments 1..5 on top of test/data.yml; the files it points at are
 * written here in @Before, because %test.forum.attachments.path is /home/eric/www/upload/test. The CRUD
 * form never reads those files, they only matter to forum/Attachments.download (see AttachmentsDownloadTest).
 */
public class CrudAdminAttachmentsTest extends BaseCrudTest {

	private static final File UPLOAD = new File("/home/eric/www/upload/test");

	/** id, label pairs as crud.relationField lists them, taken from test/data.yml and attachments-data.yml. */
	private static final String[] POST_OPTIONS = {"1", "Post[1]", "2", "Post[2]", "3", "Post[3]", "4", "Post[4]", "5",
			"Post[5]", "6", "Post[6]", "7", "Post[7]"};

	private static final String[] USER_OPTIONS = {"1", "Admin", "2", "Moderator", "3", "Bob", "4", "Anonymous"};

	@Before
	public void writeAttachmentFiles() throws Throwable {
		loadAttachments();
		UPLOAD.mkdirs();
		IO.write("1234567890ab".getBytes(), new File(UPLOAD, "probe-report.txt"));
		IO.write(new byte[70000], new File(UPLOAD, "probe-large.bin"));
	}

	// ~~~~~~~~~~~~ blank (GET /admin/adminattachments/new)

	@Test
	public void blankFormOffersNoneForTheThreeRelationsAndNamesTheRequiredOnes() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminattachments/new");
		assertStatus(200, response);
		String body = markup(response);

		assertLayoutHeader(body);
		assertBlankTitle(body, "Attachment");
		assertBreadcrumb(body, "adminattachments", "AdminAttachments", "/admin/adminattachments/new");
		assertCreateForm(body, "/admin/adminattachments");
		assertBlankButtons(body);
		assertLayoutFooter(body);
		assertCrudFlash(body, "flashSuccess", "Welcome back Admin !");

		assertRelationField(body, "post", "(None)", "", null, POST_OPTIONS);
		// models.forum.Message has no row in the fixtures, so the select holds only the placeholder
		assertRelationField(body, "message", "(None)", "", null);
		assertRelationField(body, "user", "(None)", "", null, USER_OPTIONS);

		assertTextField(body, "mimetype", "", "");
		assertTextField(body, "description", "", "");
		assertNumberField(body, "downloads", "", "Numeric.");
		assertTextField(body, "filename", "", "Required.");
		assertDateField(body, "at", "", "yyyy-MM-dd format.");
		assertNumberField(body, "size", "", "Numeric.");
		assertTextField(body, "realfilename", "", "Required.");

		// tags/crud/fileField.html needs ObjectField.type "binary", which this entity never produces
		assertNotContains("crud_binary", body);
	}

	// ~~~~~~~~~~~~ show (GET /admin/adminattachments/{id})

	@Test
	public void showFormCarriesTheStoredValuesIncludingTheDateFormat() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminattachments/1");
		assertStatus(200, response);
		String body = markup(response);

		assertShowTitle(body, "Attachment");
		assertBreadcrumbOf(body, "adminattachments", "AdminAttachments", "/admin/adminattachments/1",
				"text/plain:report.txt - User : Bob - Post : Post[1]");
		assertSaveForm(body, "/admin/adminattachments/1");
		assertDeleteForm(body, "/admin/adminattachments/1");
		assertShowButtons(body);
		assertDeleteButton(body, "Attachment");
		assertLayoutFooter(body);

		assertRelationField(body, "post", "(None)", "", "1", POST_OPTIONS);
		assertRelationField(body, "user", "(None)", "", "3", USER_OPTIONS);
		assertTextField(body, "mimetype", "text/plain", "");
		assertTextField(body, "description", "Small attachment", "");
		assertNumberField(body, "downloads", "1", "Numeric.");
		assertTextField(body, "filename", "report.txt", "Required.");
		// crud.dateField formats the java.util.Date with the configured pattern
		assertDateField(body, "at", "2024-01-01", "yyyy-MM-dd format.");
		assertNumberField(body, "size", "12", "Numeric.");
		assertTextField(body, "realfilename", "probe-report.txt", "Required.");
	}

	@Test
	public void showFormOfARowWithoutAMimetypePrintsTheNullInTheBreadcrumbOnly() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminattachments/2"));
		// Attachment.toString() concatenates the raw null mimetype
		assertBreadcrumbOf(body, "adminattachments", "AdminAttachments", "/admin/adminattachments/2",
				"null:no-mime.txt - User : Admin - Post : Post[2]");
		assertTextField(body, "mimetype", "", "");
		assertRelationField(body, "message", "(None)", "", null);
	}

	@Test
	public void showOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, GET("/admin/adminattachments/999"));
	}

	// ~~~~~~~~~~~~ create (POST /admin/adminattachments)

	@Test
	public void createWithoutAnyValueReportsOnlyTheTwoRequiredFields() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminattachments", form());
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertBlankTitle(body, "Attachment");
		assertTextFieldError(body, "filename", "", "Required", "Required.");
		assertTextFieldError(body, "realfilename", "", "Required", "Required.");

		// relations, dates and numbers are optional here, so nothing else is reported
		assertNotContains("object_mimetype\" class=\"hasError\"", body);
		assertNotContains("object_downloads\" class=\"hasError\"", body);
		assertNotContains("object_at\" class=\"hasError\"", body);
		assertNotContains("object_size\" class=\"hasError\"", body);
		assertEmptyMap(errorsOf(response));
		assertEquals(5, inTransaction(() -> Attachment.count()).longValue());
	}

	@Test
	public void createWithADateThatIsNotTheDeclaredFormatReportsIncorrectValueAndDropsTheValue() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminattachments", form("object.filename", "f.txt", "object.realfilename",
				"r.txt", "object.at", "not-a-date"));
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		// Binder could not parse the date, so _value stays null and the input renders empty
		assertDateFieldError(body, "at", "", "Incorrect value", "yyyy-MM-dd format.");
		assertTextField(body, "filename", "f.txt", "Required.");
		assertTextField(body, "realfilename", "r.txt", "Required.");
		assertEquals(5, inTransaction(() -> Attachment.count()).longValue());
	}

	@Test
	public void createWithANonNumericDownloadsReportsIncorrectValue() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminattachments", form("object.filename", "f.txt", "object.realfilename",
				"r.txt", "object.downloads", "many", "object.size", "large"));
		assertStatus(200, response);
		String body = markup(response);

		assertCrudFlash(body, "flashError", "Please correct errors");
		assertNumberFieldError(body, "downloads", "many", "Incorrect value", "Numeric.");
		assertNumberFieldError(body, "size", "large", "Incorrect value", "Numeric.");
	}

	@Test
	public void createWithSaveRedirectsToTheListAndWritesTheRelations() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminattachments", form("object.filename", "Fresh.txt",
				"object.realfilename", "fresh.txt", "object.mimetype", "text/plain", "object.description",
				"Created by the CRUD form", "object.downloads", "3", "object.size", "12", "object.at", "2024-02-03",
				"object.post.id", "1", "object.user.id", "2", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminattachments");
		assertFlashValue(response, "success", "The Attachment has been created");

		String body = markup(GET("/admin/adminattachments"));
		assertCrudFlash(body, "flashSuccess", "The Attachment has been created");
		assertRowCount(body, 6, "AdminAttachments");
		assertListRow(body, "even", "/admin/adminattachments/6", "text/plain:Fresh.txt - User : Moderator - Post : Post[1]");

		Attachment created = inTransaction(() -> Attachment.findById(6L));
		assertEquals("Fresh.txt", created.filename);
		assertEquals(Long.valueOf(1L), created.post.id);
		assertEquals(Long.valueOf(2L), created.user.id);
		assertEquals(Integer.valueOf(3), created.downloads);
	}

	@Test
	public void createWithoutSaveRedirectsToTheNewRowAndLeavesTheOptionalFieldsNull() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminattachments", form("object.filename", "Fresh.txt",
				"object.realfilename", "fresh.txt"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminattachments/6");
		assertFlashValue(response, "success", "The Attachment has been created");

		String body = markup(GET("/admin/adminattachments/6"));
		assertBreadcrumbOf(body, "adminattachments", "AdminAttachments", "/admin/adminattachments/6",
				"null:Fresh.txt");
		// Neither relation was posted, so both selects fall back to (None)
		assertRelationField(body, "post", "(None)", "", null, POST_OPTIONS);
		assertRelationField(body, "user", "(None)", "", null, USER_OPTIONS);
		assertTextFieldMatching(body, "mimetype", "", "");
		Attachment freshAttachment = inTransaction(() -> Attachment.findById(6L));
		assertTrue("expected no post on the new attachment", freshAttachment.post == null);
	}

	@Test
	public void createWithSaveAndAddAnotherRedirectsToBlankAgain() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminattachments", form("object.filename", "Fresh.txt",
				"object.realfilename", "fresh.txt", "_saveAndAddAnother", "Save and create another"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminattachments/new");
		assertFlashValue(response, "success", "The Attachment has been created");
		assertBlankTitle(markup(GET("/admin/adminattachments/new")), "Attachment");
	}

	// ~~~~~~~~~~~~ save (POST /admin/adminattachments/{id})

	@Test
	public void saveWithSaveRedirectsToTheListAndRewritesTheRelations() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminattachments/2", form("object.filename", "Renamed.txt",
				"object.realfilename", "no-mime.txt", "object.downloads", "9", "object.post.id", "2",
				"object.user.id", "1", "_save", "Save"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminattachments");
		assertFlashValue(response, "success", "The Attachment has been saved");

		String body = markup(GET("/admin/adminattachments"));
		assertCrudFlash(body, "flashSuccess", "The Attachment has been saved");
		// id 2 is the second row of the list, so its class is even whatever its id is
		assertListRow(body, "even", "/admin/adminattachments/2", "null:Renamed.txt - User : Admin - Post : Post[2]");

		Attachment saved = inTransaction(() -> Attachment.findById(2L));
		assertEquals("Renamed.txt", saved.filename);
		assertEquals(Integer.valueOf(9), saved.downloads);
		assertEquals(Long.valueOf(1L), saved.user.id);
	}

	@Test
	public void saveWithoutSaveRedirectsBackToTheShowPageAndKeepsTheDate() throws Throwable {
		loginAdmin();
		Response response = POST("/admin/adminattachments/4", form("object.filename", "Renamed.bin",
				"object.realfilename", "probe-large.bin", "object.size", "70000"));
		assertStatus(302, response);
		assertLocation(response, "/admin/adminattachments/4");
		assertFlashValue(response, "success", "The Attachment has been saved");

		String body = markup(GET("/admin/adminattachments/4"));
		assertDateField(body, "at", "2024-01-04", "yyyy-MM-dd format.");
		assertNumberField(body, "size", "70000", "Numeric.");
	}

	@Test
	public void saveOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, POST("/admin/adminattachments/999", form("object.filename", "x.txt")));
	}

	// ~~~~~~~~~~~~ delete (DELETE /admin/adminattachments/{id})

	@Test
	public void deleteOfAnAttachmentRemovesTheRowAndLeavesTheFileOnDisk() throws Throwable {
		loginAdmin();
		Response response = DELETE("/admin/adminattachments/1");
		assertStatus(302, response);
		assertLocation(response, "/admin/adminattachments");
		assertFlashValue(response, "success", "The Attachment has been deleted");

		String body = markup(GET("/admin/adminattachments"));
		assertCrudFlash(body, "flashSuccess", "The Attachment has been deleted");
		assertRowCount(body, 4, "AdminAttachments");
		assertNotContains("/admin/adminattachments/1", body);
		assertEquals(4, inTransaction(() -> Attachment.count()).longValue());

		// The CRUD module only deletes the row; forum/Attachments.download() is the only reader of the file
		assertTrue("expected the file to survive its row", new File(UPLOAD, "probe-report.txt").exists());
	}

	@Test
	public void deleteOfAnUnknownIdIsNotFound() throws Throwable {
		loginAdmin();
		assertStatus(404, DELETE("/admin/adminattachments/999"));
	}

	// ~~~~~~~~~~~~ attachment (GET /admin/adminattachments/{id}/{field})

	@Test
	public void attachmentIsNotFoundForARelationFieldBecauseNoFieldIsBinary() throws Throwable {
		loginAdmin();
		Response response = GET("/admin/adminattachments/2/post");
		assertStatus(404, response);
		assertNotFoundBody(markup(response));
	}

	@Test
	public void attachmentIsNotFoundWhenTheRowWasDeletedBeforeTheFieldWasRequested() throws Throwable {
		loginAdmin();
		DELETE("/admin/adminattachments/1");
		// notFoundIfNull(object) fires before object.getClass().getField(field) is reached
		assertStatus(404, GET("/admin/adminattachments/1/post"));
	}

	@Test
	public void attachmentFailsForAFieldNameTheEntityDoesNotHave() throws Throwable {
		loginAdmin();
		try {
			GET("/admin/adminattachments/2/zzz");
			fail("expected CRUD.attachment to ask for a field that does not exist");
		} catch (Throwable t) {
			assertTrue("unexpected: " + t.getMessage(), t.getMessage().contains("zzz"));
			assertTrue("unexpected cause: " + t.getCause(), t.getCause().getMessage().contains("zzz"));
		}
	}

	// ~~~~~~~~~~~~ list (GET /admin/adminattachments)

	@Test
	public void listPagePrintsTheConcatenatedToStringOfEveryAttachment() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminattachments"));
		assertListBreadcrumb(body, "adminattachments", "AdminAttachments");
		assertAddLink(body, "/admin/adminattachments", "Attachment");
		assertListTitle(body, "AdminAttachments");
		assertSearchForm(body, "/admin/adminattachments");
		assertDefaultTableHead(body, "/admin/adminattachments", "AdminAttachments");
		assertRowCount(body, 5, "AdminAttachments");
		assertNoPagination(body);
		assertListRow(body, "odd", "/admin/adminattachments/1", "text/plain:report.txt - User : Bob - Post : Post[1]");
		assertListRow(body, "even", "/admin/adminattachments/2", "null:no-mime.txt - User : Admin - Post : Post[2]");
		assertListRow(body, "odd", "/admin/adminattachments/3", "text/plain:absent.txt - User : Moderator - Post : Post[3]");
		assertListRow(body, "even", "/admin/adminattachments/4", "application/octet-stream:large.bin - User : Admin - Post : Post[4]");
		assertListRow(body, "odd", "/admin/adminattachments/5", "text/plain:view-only.txt - User : Bob - Post : Post[7]");
		assertLayoutFooter(body);
	}

	@Test
	public void listPageSearchesFilenamesAndLinksTheTotalSize() throws Throwable {
		loginAdmin();
		// filename and realfilename are String properties, so lower(...) like '%txt%' matches four rows;
		// large.bin (id 4) is the only one whose two names hold no "txt"
		String body = markup(GET("/admin/adminattachments?search=txt&searchFields=filename+realfilename"));
		assertSearchFormWithKeywords(body, "/admin/adminattachments", "txt", 5);
		assertDefaultTableHeadWithSearch(body, "/admin/adminattachments", "txt", "AdminAttachments");
		assertRowCount(body, 4, "AdminAttachments");
		assertListRow(body, "odd", "/admin/adminattachments/1", "text/plain:report.txt - User : Bob - Post : Post[1]");
		assertListRow(body, "even", "/admin/adminattachments/2", "null:no-mime.txt - User : Admin - Post : Post[2]");
		assertListRow(body, "odd", "/admin/adminattachments/3", "text/plain:absent.txt - User : Moderator - Post : Post[3]");
		// the parity follows the position in the filtered list, so id 5 is even here
		assertListRow(body, "even", "/admin/adminattachments/5", "text/plain:view-only.txt - User : Bob - Post : Post[7]");
		assertNotContains("/admin/adminattachments/4", body);
	}

	/**
	 * CRUD.list passes (String) request.args.get("where") to findPage and count, but the query string is
	 * parsed into request.params, so ?where=downloads>0 never reaches the JPQL factory and all five
	 * attachments stay listed even though only two of them have downloads.
	 */
	@Test
	public void whereClauseOfTheQueryStringIsNeverApplied() throws Throwable {
		loginAdmin();
		String body = markup(GET("/admin/adminattachments?where=downloads%3E0"));
		assertDefaultTableHead(body, "/admin/adminattachments", "AdminAttachments");
		assertRowCount(body, 5, "AdminAttachments");
		assertListRow(body, "odd", "/admin/adminattachments/1", "text/plain:report.txt - User : Bob - Post : Post[1]");
		assertListRow(body, "even", "/admin/adminattachments/2", "null:no-mime.txt - User : Admin - Post : Post[2]");
		assertListRow(body, "odd", "/admin/adminattachments/3", "text/plain:absent.txt - User : Moderator - Post : Post[3]");
		assertListRow(body, "even", "/admin/adminattachments/4", "application/octet-stream:large.bin - User : Admin - Post : Post[4]");
		assertListRow(body, "odd", "/admin/adminattachments/5", "text/plain:view-only.txt - User : Bob - Post : Post[7]");
	}
}
