package controllers;

import models.forum.Category;
import models.forum.Forum;
import models.forum.Permission;
import models.forum.User;
import play.Logger;
import play.Play;
import play.libs.Codec;
import play.mvc.Before;
import play.mvc.Controller;
import org.apache.commons.lang.RandomStringUtils;


public class Profile extends Controller {

	public static final String VIEWER			= "anonymous";
	public static final String LOGGED			= "";
	public static final String REPLYER			= "replyer";
	public static final String POSTER			= "poster";
	public static final String MODERATOR		= "moderator";
	public static final String ADMINISTRATOR	= "administrator";

	public static final String ANONYMOUS_EMAIL = Play.configuration.getProperty("anonymous.email", "");

	@Before(unless={"login", "signup", "authenticate", "logout"})
	static void checkAccess() throws Throwable {
		if(!session.contains("username") && request.method.equalsIgnoreCase("GET")) {
			if(isAnonymous()) {
				session.put("username", ANONYMOUS_EMAIL);
			}
		}
		Secure.checkAccess();
	}

	static private boolean isAnonymous() {
		Check check = null;
		check = getActionAnnotation(Check.class);
		if(check != null) for(String profile : check.value()) {
			if(profile.equals(VIEWER)) return true;
		}
		check = getControllerInheritedAnnotation(Check.class);
		if(check != null) for(String profile : check.value()) {
			if(profile.equals(VIEWER)) return true;
		}
		return false;
	}

	static class Security extends Secure.Security {

		static boolean authenticate(String username, String password) {
			User user = User.findByEmail(username);
			if (user == null || !user.checkPassword(password)) {
				flash.put("errorDetail", "Bad email or bad password");
				return false;
			} else if (!Boolean.TRUE.equals(user.active)) {
				flash.put("errorDetail", "This account is not active");
				return false;
			} else if (!Boolean.TRUE.equals(user.confirmed)) {
				flash.put("errorDetail", "This account is not confirmed");
				user.uuid = Codec.UUID();
				user.save();
				flash.put("notconfirmed", user.uuid);
				return false;
			} else {
				flash.success("Welcome back %s !", user.name);
				return true;
			}
		}

		/**
		 * 
		 * @param profile name of profile
		 * @return
		 */
		static boolean check(String profile) {
			boolean result = false;
			String email = session.get("username");
			User user = User.findByEmail(email);
			if(profile.equals(LOGGED)) {
				//If no profile is asked, check only if a user is logged
				result = user != null;
			} else if(user == null) {
				//If nobody is logged and a profile is asked, then the check failed.
				result = false;
			} else if(user.group.admin) {
				//If the admin is here, then the check succeed.
				result = true;
				renderArgs.put("accessLevel", Permission.administrate);
			} else if(!profile.equals(ADMINISTRATOR)) {
				String forumIdString = request.routeArgs.get("forumId");
				Permission permissions = null;
				if(forumIdString == null) {
					//TODO Find a better way
					//Default category is the general category for general forum
					Category category = Category.findById(Long.valueOf(2));
					permissions = Permission.findByUserGroupAndCategory(user.group, category);
				} else {
					Long forumId = Long.parseLong(forumIdString);
					Forum forum = Forum.find("id = ?1", forumId).first();
					notFoundIfNull(forum);
					permissions = Permission.findByUserGroupAndCategory(user.group, forum.category);
				}
				if(permissions != null) {
					Integer accessLevel = permissions.accessLevel;
					renderArgs.put("accessLevel", accessLevel);
					int permissionLevel = Permission.administrate;
					if(profile.equals(VIEWER)) {
						permissionLevel = Permission.view;
					} else if(profile.equals(REPLYER)) {
						permissionLevel = Permission.reply;
					} else if(profile.equals(POSTER)) {
						permissionLevel = Permission.post;
					} else if(profile.equals(MODERATOR)) {
						permissionLevel = Permission.moderate;
					}
					result = accessLevel >= permissionLevel;
				} else {
					result = false;
				}
			}
			if(!result && Logger.isDebugEnabled()) Logger.debug(user.name + "+" + profile + " failed for " + request.querystring);
			return result;
		}

		static void onDisconnected() {
			flash.success("You've been logged out");
			Application.index(null);
		}

		static String connected() {
			String username = session.get("username");
			return Profile.ANONYMOUS_EMAIL.equals(username) ? null : username;
		}

		static boolean isConnected() {
			return connected() != null;
		}

		static void onAuthenticated() {
			String uuid = RandomStringUtils.random(4, true, true);
			while(!User.checkUuid(uuid)) {
				uuid = RandomStringUtils.random(4, true, true);
			}
			User user = User.findByEmail(connected());
			user.uuid = uuid;
			user.save();
		}

		static void onDisconnect() {
			String connected = connected();
			if(connected != null) {
				User user = User.findByEmail(connected);
				if(user != null) {
					user.uuid = null;
					user.save();
				}
			}
		}
	}
}
