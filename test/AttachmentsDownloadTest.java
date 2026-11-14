import java.io.File;
import java.io.FileNotFoundException;

import org.junit.Before;
import org.junit.Test;

import play.libs.IO;
import play.mvc.Http.Response;
import play.test.Fixtures;

import models.forum.Attachment;

/**
 * Coverage of controllers.forum.Attachments.download(), reached as GET /attachments/{id}.
 *
 * The route argument is named id (conf/routes) because the action parameter is `Long id`: Play binds
 * route arguments by parameter name, so an argument called attachmentId left id null and the loading
 * failed with "id to load is required for loading".
 *
 * Branches and behaviors pinned below:
 *   - Attachment.findById(id) then notFoundIfNull(): an unknown id answers 404 with app/views/errors/404.html.
 *   - @Check(Profile.REPLYER): anonymous visitors get Secure.checkAccess()'s redirect, which keeps the
 *     GET url in the flash, while replyer, moderator and administrator all pass Profile.Security.check().
 *   - `if(file != null)` is always true: new File(path) never returns null, so the false branch of that
 *     guard is unreachable and a name that is not on disk reaches FileInputStream and fails.
 *   - response.setContentTypeIfNotSet(attachment.mimetype): a row with no mimetype leaves the response
 *     contentType null, and renderBinary() is never used, so no Content-Length is written.
 *   - the read loop writes the whole 64 KB buffer instead of the bytes that were read, so a 12-byte file
 *     produces 65536 bytes (the untouched tail of the freshly allocated buffer is NUL), and a 70000-byte
 *     file produces 131072 bytes because the second iteration re-uses the same buffer.
 *   - Attachment.downloads is never incremented by this action: only the CRUD form writes it
 *     (see CrudAdminAttachmentsTest), so the counter of a downloaded attachment stays as it was.
 *   - Profile.Security.check() looks for a forumId route argument, which this route does not have, so it
 *     falls back to Category 2 (Main Category). Bob has reply permission there and therefore downloads an
 *     attachment whose post lives in modoForum1, a forum he cannot reply in.
 *
 * test/attachments-data.yml adds attachments 1..5 (ids continue from test/data.yml) and the topic/post the
 * fifth one hangs on; the files it names are written here in @Before, since %test.forum.attachments.path is
 * /home/eric/www/upload/test. probe-absent.txt is deliberately never written: attachment 3 points at it.
 */
public class AttachmentsDownloadTest extends BaseForumViewTest {

	private static final File UPLOAD = new File("/home/eric/www/upload/test");

	@Before
	public void writeUploadedFilesAndLoadAttachments() throws Throwable {
		UPLOAD.mkdirs();
		IO.write("1234567890ab".getBytes(), new File(UPLOAD, "probe-report.txt"));
		IO.write(new byte[70000], new File(UPLOAD, "probe-large.bin"));
		inTransaction(() -> {
			Fixtures.loadModels("attachments-data.yml");
			return null;
		});
	}

	/** Every byte from `from` to the end of the body must be the NUL the read buffer carries. */
	private static void assertNulPadding(byte[] raw, int from) {
		for (int i = from; i < raw.length; i++) {
			if (raw[i] != 0) {
				fail("expected a NUL at offset " + i + " of the padded body, got " + raw[i]);
			}
		}
	}

	/** The download action never writes a Location, so a 200 answer has no redirect header. */
	private static void assertNoRedirect(Response response) {
		assertNull(header(response, "Location"));
	}

	// ~~~~~~~~~~~~ the read loop

	@Test
	public void downloadOfASmallFileWritesTheWholeReadBufferOnce() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/attachments/1");
		assertStatus(200, response);
		assertEquals("text/plain", response.contentType);
		assertNoRedirect(response);
		assertHeader(response, "Content-Disposition", "attachment;filename=report.txt");
		assertNull(header(response, "Content-Length"));
		assertEquals("bob@domain.com", sessionUsername(response));

		byte[] raw = rawBody(response);
		assertEquals(65536, raw.length);
		assertEquals("1234567890ab", new String(raw, 0, 12, "UTF-8"));
		// fis.read(buffer) returned 12 and the loop wrote the whole buffer anyway
		assertNulPadding(raw, 12);
	}

	/** Attachment 2 has no mimetype, so setContentTypeIfNotSet() leaves response.contentType untouched. */
	@Test
	public void downloadOfAnAttachmentWithoutMimeTypeLeavesTheContentTypeNull() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/attachments/2");
		assertStatus(200, response);
		assertNull(response.contentType);
		assertHeader(response, "Content-Disposition", "attachment;filename=no-mime.txt");

		byte[] raw = rawBody(response);
		assertEquals(65536, raw.length);
		assertEquals("1234567890ab", new String(raw, 0, 12, "UTF-8"));
		assertNulPadding(raw, 12);
	}

	/** Attachment 4 is 70000 bytes, so fis.read() is called twice and both writes are 64 KB long. */
	@Test
	public void downloadOfAFileBiggerThanTheBufferWritesTheBufferTwice() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/attachments/4");
		assertStatus(200, response);
		assertEquals("application/octet-stream", response.contentType);
		assertHeader(response, "Content-Disposition", "attachment;filename=large.bin");

		byte[] raw = rawBody(response);
		assertEquals(131072, raw.length);
		// probe-large.bin is all NUL, so the stale tail of the second write is indistinguishable
		assertNulPadding(raw, 0);
	}

	/** The action returns nothing after the loop: response.out is flushed and closed, and the status stays 200. */
	@Test
	public void downloadWritesNoBodyBeyondTheFileContent() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/attachments/1");
		assertStatus(200, response);
		// No template is rendered for a binary answer, so nothing follows the buffer
		byte[] raw = rawBody(response);
		assertEquals("byte[] body of " + raw.length + " bytes", 65536, raw.length);
		assertNotContains("<html", new String(raw, "UTF-8"));
	}

	// ~~~~~~~~~~~~ the profiles of Profile.Security.check()

	@Test
	public void downloadIsAllowedForTheAdministratorAndTheModerator() throws Throwable {
		clearCookies();
		login("admin@domain.com", "secret");
		Response asAdmin = GET("/attachments/1");
		assertStatus(200, asAdmin);
		assertEquals("text/plain", asAdmin.contentType);

		clearCookies();
		login("modo@domain.com", "secret");
		Response asModerator = GET("/attachments/1");
		assertStatus(200, asModerator);
		assertEquals("text/plain", asModerator.contentType);
		assertHeader(asModerator, "Content-Disposition", "attachment;filename=report.txt");
	}

	/** Attachment 5 belongs to a post of modoForum1, whose category only gives Bob view permission. */
	@Test
	public void downloadIgnoresTheForumOfThePostBecauseTheRouteHasNoForumIdArgument() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/attachments/5");
		assertStatus(200, response);
		assertEquals("text/plain", response.contentType);
		assertHeader(response, "Content-Disposition", "attachment;filename=view-only.txt");
		assertEquals(65536, rawBody(response).length);
	}

	@Test
	public void anonymousIsRedirectedToLoginAndTheAttachmentUrlIsKept() throws Throwable {
		clearCookies();
		Response response = GET("/attachments/1");
		assertStatus(302, response);
		assertLocation(response, "/login");
		// Secure.checkAccess() keeps request.url for a GET, which Secure.login() then redirects back to
		assertFlashValue(response, "url", "/attachments/1");
		assertEmptyMap(errorsOf(response));
		assertNull(sessionUsername(response));
		assertEquals(5, inTransaction(() -> Attachment.count()).longValue());
	}

	// ~~~~~~~~~~~~ rows that cannot be streamed

	@Test
	public void downloadOfAnUnknownIdIsNotFound() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Response response = GET("/attachments/999");
		assertStatus(404, response);
		assertEquals("text/html; charset=utf-8", response.contentType);
		assertNotFoundBody(markup(response));
	}

	/** Attachment 3 names probe-absent.txt, which no test writes. */
	@Test
	public void downloadOfAFileMissingFromDiskFailsWithFileNotFoundException() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		try {
			GET("/attachments/3");
			fail("expected FileInputStream to reject probe-absent.txt");
		} catch (Throwable t) {
			assertCauseChain(t, "play.exceptions.JavaExecutionException: /home/eric/www/upload/test/probe-absent.txt (No such file or directory)",
					"java.io.FileNotFoundException: /home/eric/www/upload/test/probe-absent.txt (No such file or directory)");
			assertTrue("expected a FileNotFoundException at the root of the chain",
					causeRoot(t) instanceof FileNotFoundException);
		}

		Attachment absent = inTransaction(() -> Attachment.findById(3L));
		assertEquals(Integer.valueOf(0), absent.downloads);
	}

	/**
	 * A row whose realfilename is null makes the path ".../null", and the `file != null` guard cannot
	 * stop it: File construction never returns null, so the only reachable outcome is a failed open.
	 */
	@Test
	public void downloadOfARowWithoutRealfilenameFailsOnTheNullName() throws Throwable {
		inTransactionVoid(() -> {
			Attachment row = Attachment.findById(1L);
			row.realfilename = null;
			row.save();
			return null;
		});

		clearCookies();
		login("bob@domain.com", "secret");
		try {
			GET("/attachments/1");
			fail("expected FileInputStream to reject the path ending with null");
		} catch (Throwable t) {
			assertCauseChain(t, "play.exceptions.JavaExecutionException: /home/eric/www/upload/test/null (No such file or directory)",
					"java.io.FileNotFoundException: /home/eric/www/upload/test/null (No such file or directory)");
			assertTrue("expected a FileNotFoundException at the root of the chain",
					causeRoot(t) instanceof FileNotFoundException);
		}
	}

	// ~~~~~~~~~~~~ the counter nobody updates

	@Test
	public void downloadNeverIncrementsTheDownloadsCounter() throws Throwable {
		clearCookies();
		login("bob@domain.com", "secret");
		Attachment before = inTransaction(() -> Attachment.findById(1L));
		assertEquals(Integer.valueOf(1), before.downloads);

		assertStatus(200, GET("/attachments/1"));
		assertStatus(200, GET("/attachments/1"));

		Attachment after = inTransaction(() -> Attachment.findById(1L));
		assertEquals(Integer.valueOf(1), after.downloads);
		assertEquals(Long.valueOf(1L), after.id);
	}
}
