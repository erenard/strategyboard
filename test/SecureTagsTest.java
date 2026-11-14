import java.util.Map;

import org.junit.*;

import play.mvc.Http;

/**
 * Coverage of app/views/tags/secure/checkNot.tag and app/views/tags/secure/isConnected.tag.
 *
 * Nothing in the application calls them: the secure module ships only tags/secure/check.tag
 * (modules/secure/app/views/tags/), which inlines its own #{if session.username &&
 * controllers.Secure.Security.invoke("check", _arg)} and never delegates to these two files. They are
 * therefore rendered directly, the way TagsTest renders the tags in app/views/tags/.
 *
 * #{doBody /} compiles to `if(_body || attrs[...]['body']) { ... out.print(toExecute.toString()) }`
 * (play/templates/GroovyTemplateCompiler.endTag), so binding _body with an object whose toString() is
 * the caller's markup is enough to make the body appear, and binding nothing makes it disappear.
 *
 * controllers.Secure.Security.invoke uses Java.invokeChildOrStatic, so the calls land on
 * Profile.Security (app/controllers/Profile.java), the app's subclass of the module's Security class:
 *   - isConnected() returns true unless the session holds Profile.ANONYMOUS_EMAIL
 *   - check(profile) is true for an admin account and, for other accounts, compares the accessLevel of
 *     the Permission of the default category (Category.findById(2), used when the route has no
 *     forumId) or of the forum named by request.routeArgs.forumId.
 */
public class SecureTagsTest extends BaseForumViewTest {

	/** The caller's body, as #{doBody /} prints it. */
	private static Object body(final String text) {
		return new Object() {
			@Override
			public String toString() {
				return text;
			}
		};
	}

	private static Map<String, Object> tagArgs(String username, String path) throws Throwable {
		Map<String, Object> args = viewArgs(username, path);
		args.put("_body", body("inner"));
		return args;
	}

	// ~~~~~~~~~~~~ tags/secure/isConnected.tag

	@Test
	public void isConnectedRendersTheBodyOfALoggedInAccount() throws Throwable {
		Map<String, Object> args = tagArgs("bob@domain.com", "/forums");
		String out = renderView("tags/secure/isConnected.tag", args);
		Assert.assertEquals("inner", out.trim());
	}

	@Test
	public void isConnectedRendersNothingForTheAnonymousAccount() throws Throwable {
		// Profile.Security.connected() turns Profile.ANONYMOUS_EMAIL into null
		Map<String, Object> args = tagArgs("anonymous@domain.com", "/forums");
		Assert.assertEquals("", renderView("tags/secure/isConnected.tag", args).trim());
	}

	@Test
	public void isConnectedRendersNothingWhenNobodyIsLogged() throws Throwable {
		Map<String, Object> args = tagArgs("", "/forums");
		Assert.assertEquals("", renderView("tags/secure/isConnected.tag", args).trim());
	}

	// ~~~~~~~~~~~~ tags/secure/checkNot.tag

	@Test
	public void checkNotRendersTheBodyWhenTheProfileIsRefused() throws Throwable {
		// Bob's group is not an admin one, and check() refuses ADMINISTRATOR without a permission check
		Map<String, Object> args = tagArgs("bob@domain.com", "/admin");
		args.put("_arg", "administrator");
		Assert.assertEquals("inner", renderView("tags/secure/checkNot.tag", args).trim());
	}

	@Test
	public void checkNotRendersNothingForAnAdministrator() throws Throwable {
		Map<String, Object> args = tagArgs("admin@domain.com", "/admin");
		args.put("_arg", "administrator");
		Assert.assertEquals("", renderView("tags/secure/checkNot.tag", args).trim());
	}

	@Test
	public void checkNotUsesThePermissionOfTheDefaultCategoryWhenTheRouteHasNoForumId() throws Throwable {
		// Category.findById(2) is mainCategory; the General group has postMain (post) and moderateMain?
		Map<String, Object> allowed = tagArgs("bob@domain.com", "/forums");
		allowed.put("_arg", "replyer");
		Assert.assertEquals("", renderView("tags/secure/checkNot.tag", allowed).trim());

		Map<String, Object> refused = tagArgs("bob@domain.com", "/forums");
		refused.put("_arg", "moderator");
		Assert.assertEquals("inner", renderView("tags/secure/checkNot.tag", refused).trim());

		// a profile that matches none of the four names keeps permissionLevel at administrate
		Map<String, Object> unknown = tagArgs("bob@domain.com", "/forums");
		unknown.put("_arg", "wizard");
		Assert.assertEquals("inner", renderView("tags/secure/checkNot.tag", unknown).trim());
	}

	@Test
	public void checkNotUsesThePermissionOfTheForumNamedByTheRoute() throws Throwable {
		Map<String, Object> args = tagArgs("modo@domain.com", "/forums/3");
		args.put("_arg", "poster");
		// modoForum1 belongs to moderatorsCategory, whose General group has no permission at all, so
		// the check fails for Bob; Moderator has postModerators on that category and passes.
		Http.Request.current.get().routeArgs.put("forumId", "3");
		Assert.assertEquals("", renderView("tags/secure/checkNot.tag", args).trim());

		Map<String, Object> refused = tagArgs("bob@domain.com", "/forums/3");
		refused.put("_arg", "poster");
		Http.Request.current.get().routeArgs.put("forumId", "3");
		Assert.assertEquals("inner", renderView("tags/secure/checkNot.tag", refused).trim());
	}

	@Test
	public void checkNotRendersTheBodyWhenNobodyIsLogged() throws Throwable {
		Map<String, Object> args = tagArgs("", "/forums");
		args.put("_arg", "administrator");
		Assert.assertEquals("inner", renderView("tags/secure/checkNot.tag", args).trim());
	}
}
