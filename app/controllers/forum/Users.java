package controllers.forum;

import java.util.List;

import org.apache.commons.lang.RandomStringUtils;

import controllers.Application;
import controllers.Check;
import controllers.Profile;
import controllers.Secure;

import models.forum.User;
import models.forum.UserGroup;
import notifiers.Notifier;
import play.Logger;
import play.cache.Cache;
import play.data.validation.Email;
import play.data.validation.Equals;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.libs.Codec;
import play.mvc.With;

@With(Profile.class)
public class Users extends Application {

	@Check(Profile.VIEWER)
	public static void index(Integer page) {
		List users = User.findAllActive(page == null ? 1 : page, pageSize);
		Long nbUsers = User.countActive();
		render(nbUsers, users, page);
	}

	@Check(Profile.VIEWER)
	public static void show(Long id) {
		User user = User.findById(id);
		notFoundIfNull(user);
		render(user);
	}

	@Check(Profile.VIEWER)
	public static void resendConfirmation(String uuid) {
		User user = User.findByUUID(uuid);
		notFoundIfNull(user);
		try {
			if (Notifier.welcome(user)) {
				flash.success("Please check your emails ...");
				flash.put("username", user.email);
				login();
			}
		} catch (Exception e) {
			Logger.error(e, "Mail error");
		}
		flash.error("Oops (the email cannot be sent)...");
		flash.put("username", user.email);
		login();
	}

	@Check(Profile.VIEWER)
	public static void signup() {
		String uuid = Codec.UUID();
		render(uuid);
	}

	@Check(Profile.VIEWER)
	public static void register(@Required @Email String email, @Required @MinSize(5) String password, @Equals("password") String password2, @Required String name, @Required String code, @Required String uuid) {
		validation.equals(code, Cache.get(uuid)).message("Invalid code. Please type it again");
		if (!User.isEmailAvailable(email)) {
			validation.addError("email", "This email is already registered.");
		}
		if (validation.hasErrors()) {
			validation.keep();
			params.flash();
			flash.error("Please correct these errors !");
			signup();
		}
		UserGroup generalPopulation = UserGroup.findByName(UserGroup.GENERAL);
		User user = new User(email, password, name, generalPopulation);
		try {
			if (Notifier.welcome(user)) {
				flash.success("Your account is created. Please check your emails ...");
				login();
			}
		} catch (Exception e) {
			Logger.error(e, "Mail error");
		}
		flash.error("Oops ... (the email cannot be sent)");
		login();
	}

	@Check(Profile.VIEWER)
	public static void confirmRegistration(String uuid) {
		User user = User.findByUUID(uuid);
		notFoundIfNull(user);
		user.uuid = null;
		user.confirmed = Boolean.TRUE;
		user.save();
		flash.success("Welcome %s !", user.name);
		flash.put("username", user.email);
		login();
	}

	@Check(Profile.VIEWER)
	public static void forgottenPassword() {
		render();
	}

	@Check(Profile.VIEWER)
	public static void sendPassword(@Required @Email String email) {
		User user = User.findByEmail(email);
		if (user == null) {
			validation.addError("email", "Unkown email: " + email);
		}
		if (validation.hasErrors()) {
			validation.keep();
			params.flash();
			flash.error("Please correct these errors !");
			forgottenPassword();
		} else {
			String password = RandomStringUtils.random(8, true, true);
			try {
				if(Notifier.sendPassword(user, password)) {
					user.passwordHash = Codec.hexMD5(password);
					user.save();
					flash.success("A new password has been sent to you !");
					flash.put("email", user.email);
					login();
				}
			} catch (Exception e) {
				Logger.error(e, "Mail error");
			}
		}
		flash.error("Oops ... (the email cannot be sent)");
		forgottenPassword();
	}

	@Check(Profile.VIEWER)
	public static void forgottenEmail() {
		render();
	}

	@Check(Profile.VIEWER)
	public static void showEmail(@Required String pseudo, @Required String password) {
		if (validation.hasErrors()) {
			validation.keep();
			params.flash();
			flash.error("Please correct these errors !");
			forgottenEmail();
		}
		User user = User.findByName(pseudo);
		if (user == null || !user.passwordHash.equals(Codec.hexMD5(password))) {
			flash.error("Incorrect pseudo or password !");
			forgottenEmail();
		}
		flash.success("Your email is " + user.email);
		flash.put("email", user.email);
		login();
	}

	@Check(Profile.LOGGED)
	public static void profile() {
		User user = connectedUser();
		render(user);
	}

	@Check(Profile.LOGGED)
	public static void update(@Required @MinSize(5) String password, @Required @Email String email, @Equals("email") String email2, @MinSize(5) String newPassword, @Equals("newPassword") String newPassword2) {
		User user = connectedUser();
		notFoundIfNull(user);
		if(!user.passwordHash.equals(Codec.hexMD5(password))) {
			validation.addError("password", "Wrong password");
		}
		if (!email.equals(user.email) && !User.isEmailAvailable(email)) {
			validation.addError("email", "This email (" + email + ") is already registered.");
		}
		if (validation.hasErrors()) {
			validation.keep();
			params.flash();
			flash.error("Please correct these errors !");
			profile();
		}
		String emailSentence = "";
		boolean accountUpdated = false;
		if(!email.equals(user.email)) {
			user.email = email;
			user.confirmed = false;
			user.uuid = Codec.UUID();
			try {
				if (Notifier.welcome(user)) {
					emailSentence = " Please check your emails ...";
					accountUpdated = true;
				}
			} catch (Exception e) {
				Logger.error(e, "Mail error");
				flash.error("Oops ... (the email cannot be sent)");
			}
			session.put("username", email);
		}
		if(newPassword != null && !newPassword.trim().equals("")) {
			user.passwordHash = Codec.hexMD5(newPassword);
			accountUpdated = true;
		}
		if(accountUpdated) {
			user.save();
			flash.success("Your account has been updated." + emailSentence);
		}
		profile();
	}

	// ~~~~~~~~~~~~ Some utils

	private static void login() {
		try {
			flash.keep();
			Secure.login();
		} catch (Throwable e) {
			Logger.error(e, e.getMessage());
		}
	}

	@SuppressWarnings("unused")
	private static void logout() {
		try {
			flash.keep();
			Secure.logout();
		} catch (Throwable e) {
			Logger.error(e, e.getMessage());
		}
	}

}

