package controllers.forum;

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
		// The anonymous session passes the LOGGED check but has no real user: see Application.backToPreviousPage().
		if(connectedUser() == null) {
			backToPreviousPage();
		}
		User user = connectedUser();
		// Play binds every Groovy template with a play.i18n.Messages object under the name "messages"
		// (GroovyTemplate.setUpBindingVariables), so render(messages) would replace the message list with
		// the i18n object. The list is therefore passed as userMessages, and the template needs ${user}.
		renderArgs.put("user", user);
		renderArgs.put("userMessages", Message.inbox(user, page == null ? 1 : page, pageSize));
		render();
	}

	@Check(Profile.LOGGED)
	public static void outbox(Integer page) {
		if(connectedUser() == null) {
			backToPreviousPage();
		}
		User user = connectedUser();
		// Message.outbox() lists the messages the viewer sent, which is what outbox.html's
		// ${message.to.name} column shows. As in inbox(), the list is named userMessages: Play binds
		// every Groovy template with a play.i18n.Messages object under the name "messages"
		// (GroovyTemplate.setUpBindingVariables), so render(messages) would replace it.
		renderArgs.put("user", user);
		renderArgs.put("userMessages", Message.outbox(user, page == null ? 1 : page, pageSize));
		render();
	}

}
