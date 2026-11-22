package controllers.forum;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
		User user = (User) renderArgs.get("connectedUser");
		List<Category> categories;
		if(user == null) {
			categories = Category.forUser(Profile.ANONYMOUS_EMAIL);
		} else if(user.group != null && user.group.admin) {
			categories = Category.findAll();
		} else {
			categories = Category.forUser(user.email);
		}
		// Permission is one row per (group, category), so the counts a visitor is shown are decided per
		// category: the Moderators group holds Permission.moderate in Main Category but only
		// Permission.post in Moderators Category, and accessLevel() of this action is the Main Category
		// fallback value. Below moderate, hidden topics stay out of the totals, as in forum.Forums.show().
		Map<Category, Boolean> fullCountsByCategory = new LinkedHashMap<Category, Boolean>();
		long topicsCount = 0;
		long postsCount = 0;
		for(Category category : categories) {
			boolean seeHiddenTopics = canSeeHiddenTopics(user, category);
			fullCountsByCategory.put(category, seeHiddenTopics);
			topicsCount += seeHiddenTopics ? category.getTopicsCount() : category.getVisibleTopicsCount();
			postsCount += seeHiddenTopics ? category.getPostsCount() : category.getVisiblePostsCount();
		}
		render(categories, fullCountsByCategory, topicsCount, postsCount);
	}

	/**
	 * Hidden topics are counted at Permission.moderate or above, the level forum.Forums.show() uses to
	 * list them. The administrator passes through UserGroup.admin, as in Profile.Security.check().
	 */
	static boolean canSeeHiddenTopics(User user, Category category) {
		if(user == null) {
			return false;
		}
		if(Boolean.TRUE.equals(user.group.admin)) {
			return true;
		}
		Permission permission = Permission.findByUserGroupAndCategory(user.group, category);
		return permission != null && permission.accessLevel >= Permission.moderate;
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
		// save() is required: Play's Hibernate setup does not flush a plain field mutation, so
		// without it the forum stayed unlocked while the flash claimed it had been locked.
		forum.save();
		if(forum.locked) {
			flash.success(Messages.get("success.forum.locked"));
		} else {
			flash.success(Messages.get("success.forum.unlocked"));
		}
		index();
	}
}
