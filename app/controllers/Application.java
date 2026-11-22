package controllers;

import java.util.List;
import models.forum.Forum;
import models.forum.Topic;
import models.forum.User;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.PlayerScore;
import play.Play;
import play.cache.Cache;
import play.libs.Images;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.Http;
import controllers.Profile.Security;

public class Application extends Controller {

	protected static Integer pageSize = Integer.parseInt(Play.configuration.getProperty("forum.pageSize", "10"));

	// ~~~~~~~~~~~~ @Before interceptors

	@Before
	static void globals() {
		renderArgs.put("connectedUser", connectedUser());
		renderArgs.put("pageSize", pageSize);
	}

	// ~~~~~~~~~~~~ Actions

	public static void index(Integer page) {
		List<PlayerScore> playerScores = PlayedGameScore.getLastMonthPlayerScores(1, 10);
		Forum forum = Forum.find("name", "Home Page").first();
		// getVisibleTopics() filters topic.hidden: the home page is public, so a hidden news topic
		// never appears on it - not for an anonymous visitor, not for a moderator, not for an
		// administrator. They read it through forum.Forums.show(), which lists it above moderate.
		List<Topic> news = forum.getVisibleTopics(page != null ? page : 1, pageSize);
		int newsCount = news.size();
		// page must be rendered: index.html passes it to the pagination tag (page: page ?: 1),
		// and action parameters are not added to renderArgs by the framework.
		render(playerScores, news, newsCount, page);
	}

	public static void captcha(String id) {
		Images.Captcha captcha = Images.captcha();
		String code = captcha.getText("#aaaaaa");
		Cache.set(id, code, "10mn");
		renderBinary(captcha);
	}

	// ~~~~~~~~~~~~ Some utils

	protected static User connectedUser() {
		String username = Security.connected();
		return username != null ? User.findByEmail(username) : null;
	}
	
	protected static Integer accessLevel() {
		return (Integer) renderArgs.get("accessLevel");
	}

	/**
	 * Sends a visitor with no real user back to the page that referred it here, or to the home page.
	 *
	 * Profile.checkAccess() puts anonymous.email into the session for a @Check(VIEWER) request, and
	 * Profile.Security.check("") only needs User.findByEmail() to answer something, so a LOGGED action
	 * runs with the anonymous row as its user while connectedUser() - Security.connected() filters that
	 * address out - stays null. Rendering profile.html or inbox.html then dereferences null; the
	 * anonymous visitor has no profile, no inbox and nothing to update, so it is sent back instead.
	 *
	 * Both branches answer 302. The referer branch is a plain Location redirect: Controller.redirect(String)
	 * takes a url when it contains a slash. The fallback calls Application.index(), and Play's
	 * ControllersEnhancer inserts an auto-redirect in front of every public static void action, so an action
	 * called from another action never runs - it turns into redirect("Application.index"), which reverse
	 * routes to "/" through the GET / route. Same as Profile.Security.onDisconnected().
	 *
	 * The current path is refused: a form posts to the page it is on, so POST /profile carries /profile as
	 * its referer, and bouncing back there would loop.
	 */
	protected static void backToPreviousPage() {
		// PlayHandler.getHeaders() lowercases every header name, so this key is the only one to look for.
		Http.Header referer = request.headers.get("referer");
		String url = referer == null ? null : referer.value();
		if(url != null && url.startsWith(Play.ctxPath) && !url.contains(request.path)) {
			redirect(url);
		}
		Application.index(null);
	}
}