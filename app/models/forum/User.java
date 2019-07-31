package models.forum;

import java.util.List;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import play.data.validation.Email;
import play.data.validation.Required;
import play.db.jpa.Model;
import play.libs.Codec;
import controllers.legacy.MD5;

@Entity
public class User extends Model {

	@Email
	@Required
	public String email;

	@Required
	public String passwordHash;

	@Required
	public String name;

	@Required
	@ManyToOne
	public UserGroup group;

	public String uuid;

	public Boolean confirmed = Boolean.FALSE;

	public Boolean active = Boolean.TRUE;

	// ~~~~~~~~~~~~ 

	public User() {}

	public User(String email, String password, String name, UserGroup group) {
		this.email = email;
		this.passwordHash = Codec.hexMD5(password);
		this.name = name;
		this.uuid = Codec.UUID();
		this.group = group;
		create();
	}

	// ~~~~~~~~~~~~ 

	@Override
	public String toString() {
		return name;
	}

	// ~~~~~~~~~~~~ 

	public boolean checkPassword(String password) {
		return passwordHash.equals(Codec.hexMD5(password)) || passwordHash.equals(MD5.crypt(password));
	}

	// ~~~~~~~~~~~~ 

	public List<Post> getRecentsPosts() {
		return Post.find("postedBy = ?0 and topic.hidden = false order by postedAt desc", this).fetch(1, 10);
	}

	public Long getPostsCount() {
		return Post.count("postedBy", this);
	}

	public Long getGamesCount() {
		return PlayedGameScore.count("user", this);
	}

	public Long getTopicsCount() {
		return Post.count("select count(distinct t) from Topic t, Post p, User u where p.postedBy = ?0 and p.topic = t", this);
	}

	public List<Category> getCategories() {
		if(this.group.admin) {
			return Category.findAll();
		} else {
			return Category.find("select distinct c from Category c, Permission p where p.category = c and p.group = ?0 and p.accessLevel > 0", this.group).fetch();
		}
	}

	public List<PlayedGame> getRecentsGames() {
		return PlayedGameScore.find("select s.playedGame from PlayedGameScore s where s.user = ?0 order by s.playedGame.end desc", this).fetch(10);
	}

	public Long getNewMessagesCount() {
		return Message.count("to = ?0 and readed = false", this);
	}

	// ~~~~~~~~~~~~ 

	public static User findByEmail(String email) {
		return find("email", email).first();
	}

	public static User findByName(String name) {
		return find("name", name).first();
	}

	public static User findByUUID(String uuid) {
		return find("uuid", uuid).first();
	}

	public static List<User> findAllActive(int page, int pageSize) {
		JPAQuery q = find("from User u where (u in (select u from PlayedGameScore s join s.user u) or u in (select u from Post p join p.postedBy u)) and active = true order by name");
		return q.fetch(page, pageSize);
	}

	public static Long countActive() {
		return count("from User u where (u in (select u from PlayedGameScore s join s.user u) or u in (select u from Post p join p.postedBy u)) and active = true order by name");
	}

	public static boolean isEmailAvailable(String email) {
		return count("email", email) == 0;
	}

	public static boolean checkUuid(String uuid) {
		return count("uuid", uuid) == 0;
	}

}

