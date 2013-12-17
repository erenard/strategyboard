package controllers.forum;

import java.util.List;

import controllers.Application;
import controllers.Check;
import controllers.Profile;

import models.forum.Message;
import models.forum.User;
import play.mvc.With;

@With(Profile.class)
public class Messages extends Application {

	@Check(Profile.LOGGED)
	public static void inbox(Integer page) {
		User user = connectedUser();
		List<Message> messages = Message.inbox(user, page == null ? 1 : page, pageSize);
		render(messages);
	}

	@Check(Profile.LOGGED)
	public static void outbox(Integer page) {
		User user = connectedUser();
		List<Message> messages = Message.inbox(user, page == null ? 1 : page, pageSize);
		render(messages);
	}

}
