package models.forum;

import java.util.Date;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;

import play.data.binding.As;
import play.data.validation.Required;
import play.db.jpa.Model;

@Entity
public class Message extends Model {
	@Required
	@ManyToOne
	public User from;

	@Required
	@ManyToOne
	public User to;

	@Required
	@As("yyyy-MM-dd")
	public Date at;

	public String subject;

	@Lob
	public String content;

	public Boolean readed = Boolean.FALSE;

	public static List<Message> inbox(User user, int page, int pagesize) {
		return find("to", user).fetch(page, pagesize);
	}

	public static long inboxCount(User user) {
		return count("to", user);
	}

	public static List<Message> outbox(User user, int page, int pagesize) {
		return find("from", user).fetch(page, pagesize);
	}

	public static long outboxCount(User user) {
		return count("from", user);
	}
}
