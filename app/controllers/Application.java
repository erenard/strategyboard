package controllers;

import java.util.List;
import models.forum.Forum;
import models.forum.Topic;
import models.forum.User;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.PlayerScore;
import models.openttd.OpenttdServer;
import play.Play;
import play.cache.Cache;
import play.cache.CacheFor;
import play.libs.Images;
import play.mvc.Before;
import play.mvc.Controller;
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

	@CacheFor("5s")
	public static void index(Integer page) {
		List<PlayerScore> playerScores = PlayedGameScore.getLastMonthPlayerScores(1, 10);
		
		OpenttdServer openTTDStatus = OpenttdServer.getStatus();
		
		Forum forum = Forum.find("name", "Home Page").first();

		List<Topic> news = forum.getTopics(page != null ? page : 1, pageSize);
		int newsCount = news.size();
		
		render(playerScores, openTTDStatus, news, newsCount);
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
}