package controllers.forum;

import controllers.Application;
import controllers.Check;
import controllers.Profile;

import play.i18n.Messages;
import play.mvc.*;
import play.data.validation.*;

import models.forum.Forum;
import models.forum.Topic;

@With(Profile.class)
public class Topics extends Application {

	/**
	 * Show the topic, list Posts
	 * @param forumId
	 * @param topicId
	 * @param page
	 */
	@Check(Profile.VIEWER)
	public static void show(Long forumId, Long topicId, Integer page) {
		Topic topic = Topic.findById(topicId);
		notFoundIfNull(topic);
		if(topic.hidden) {
			forbidden("This topic is hidden.");
		} else {
			topic.views += 1;
			render(topic, page);
		}
	}

	/**
	 * Access to the new Topic form
	 * @param forumId
	 */
	@Check(Profile.POSTER)
	public static void post(Long forumId) {
		Forum forum = Forum.findById(forumId);
		notFoundIfNull(forum);
		if(forum.locked) {
			flash.error(Messages.get("error.forum.locked"));
			Forums.show(forumId, null);
		} else {
			render(forum);
		}
	}

	/**
	 * Add a Topic
	 * @param forumId
	 * @param subject
	 * @param content
	 */
	@Check(Profile.POSTER)
	public static void create(Long forumId, @Required String subject,
			String content) {
		if (validation.hasErrors()) {
			validation.keep();
			params.flash();
			flash.error("Please correct these errors !");
			post(forumId);
		}
		Forum forum = Forum.findById(forumId);
		notFoundIfNull(forum);
		if(forum.locked) {
			flash.error(Messages.get("error.forum.locked"));
			Forums.show(forumId, null);
		} else {
			Topic newTopic = forum.newTopic(connectedUser(), subject, content);
			show(forumId, newTopic.id, null);
		}
	}

	/**
	 * Access to the reply form
	 * @param forumId
	 * @param topicId
	 */
	@Check(Profile.REPLYER)
	public static void reply(Long forumId, Long topicId) {
		Topic topic = Topic.findById(topicId);
		notFoundIfNull(topic);
		if(topic.locked) {
			flash.error(Messages.get("error.topic.locked"));
			Topics.show(forumId, topicId, null);
		} else {
			render(topic);
		}
	}

	/**
	 * Add a reply
	 * @param forumId
	 * @param topicId
	 * @param content
	 */
	@Check(Profile.REPLYER)
	public static void createReply(Long forumId, Long topicId, String subject, String content) {
		Topic topic = Topic.findById(topicId);
		notFoundIfNull(topic);
		if(topic.locked) {
			flash.error(Messages.get("error.topic.locked"));
			Topics.show(forumId, topicId, null);
		} else {
			topic.reply(connectedUser(), subject, content);
			show(forumId, topicId, null);
		}
	}

	/**
	 * (Un)Hide the Topic
	 * @param forumId
	 * @param topicId
	 */
	@Check(Profile.MODERATOR)
	public static void hide(Long forumId, Long topicId) {
		Topic topic = Topic.findById(topicId);
		notFoundIfNull(topic);
		topic.hidden = !topic.hidden;
		if(topic.hidden) {
			flash.success("success.topic.hidden");
		} else {
			flash.success("success.topic.restored");
		}
		Forums.show(forumId, null);
	}

	/**
	 * (Un)Lock the Topic
	 * @param forumId
	 * @param topicId
	 */
	@Check(Profile.MODERATOR)
	public static void lock(Long forumId, Long topicId) {
		Topic topic = Topic.findById(topicId);
		notFoundIfNull(topic);
		topic.locked = !topic.locked;
		if(topic.locked) {
			flash.success("success.topic.locked");
		} else {
			flash.success("success.topic.unlocked");
		}
		Forums.show(forumId, null);
	}

	/**
	 * Move the Topic to another Forum
	 * @param forumId source forum
	 * @param topicId topic
	 * @param toForumId destination forum
	 */
	@Check(Profile.MODERATOR)
	public static void move(Long forumId, Long topicId, Long toForumId) {
		Topic topic = Topic.findById(topicId);
		notFoundIfNull(topic);
		Forum toForum = Forum.findById(toForumId);
		notFoundIfNull(toForum);
		topic.forum = toForum;
		flash.success("success.topic.moved");
		Forums.show(forumId, null);
	}

	/**
	 * Permanently remove the Topic
	 * @param forumId
	 * @param topicId
	 */
	@Check(Profile.ADMINISTRATOR)
	public static void delete(Long forumId, Long topicId) {
		Topic topic = Topic.findById(topicId);
		notFoundIfNull(topic);
		topic.delete();
		flash.success("success.topic.deleted");
		Forums.show(forumId, null);
	}

}
