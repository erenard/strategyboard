package controllers.forum;

import java.util.List;

import controllers.Application;
import controllers.Check;
import controllers.Profile;

import models.forum.Category;
import models.forum.Forum;
import models.forum.Permission;
import models.forum.Topic;
import models.forum.User;
import play.i18n.Messages;
import play.mvc.With;

@With(Profile.class)
public class Forums extends Application {

	/**
	 * List all Categories / Forums
	 */
	@Check(Profile.VIEWER)
	public static void index() {
		List<Category> categories;
		{
			User user = (User) renderArgs.get("connectedUser");
			if(user == null) {
				categories = Category.forUser(Profile.ANONYMOUS_EMAIL);
			} else if(user.group != null && user.group.admin) {
				categories = Category.findAll();
			} else {
				categories = Category.forUser(user.email);
			}
		}
		long topicsCount = 0;
		long postsCount = 0;
		for(Category category : categories) {
			topicsCount += category.getTopicsCount();
			postsCount += category.getPostsCount();
		}
		render(categories, topicsCount, postsCount);
	}

	/**
	 * Show a forum, list all topics
	 * @param forumId
	 * @param page
	 */
	@Check(Profile.VIEWER)
	public static void show(Long forumId, Integer page) {
		Forum forum = Forum.findById(forumId);
		notFoundIfNull(forum);
		if(page == null) page = 1;
		long topicsCount;
		long postsCount;
		List<Topic> topics;
		if(accessLevel() >= Permission.moderate) {
			topicsCount = forum.getTopicsCount();
			postsCount = forum.getPostsCount();
			topics = forum.getTopics(page, pageSize);
		} else {
			topicsCount = forum.getVisibleTopicsCount();
			postsCount = forum.getVisiblePostsCount();
			topics = forum.getVisibleTopics(page, pageSize);
		}
		render(forum, topicsCount, postsCount, topics, page);
	}

	/**
	 * (Un)Lock a forum
	 * @param forumId
	 */
	@Check(value = {Profile.MODERATOR})
	public static void lock(Long forumId) {
		Forum forum = Forum.findById(forumId);
		notFoundIfNull(forum);
		forum.locked = !forum.locked;
		if(forum.locked) {
			flash.success(Messages.get("success.forum.locked"));
		} else {
			flash.success(Messages.get("success.forum.unlocked"));
		}
		index();
	}
}
