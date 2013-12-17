package models.forum;

import javax.persistence.*;
import java.util.*;

import play.db.jpa.*;
import play.data.validation.*;

@Entity
public class Category extends Model {

	@Required
	@MaxSize(50)
	public String name;
	@Required
	public Integer displayOrder;
	@OneToMany(cascade = CascadeType.ALL, mappedBy = "category")
	public List<Forum> forums;

	// @OneToMany(cascade = CascadeType.ALL, mappedBy = "category")
	// public List<Permission> permissions;

	// ~~~~~~~~~~~~

	@Override
	public String toString() {
		return name;
	}

	// ~~~~~~~~~~~~

	public Category(String name, Integer displayOrder) {
		this.name = name;
		this.displayOrder = displayOrder;
		create();
	}

	// ~~~~~~~~~~~~

	public long getForumsCount() {
		return Forum.count("category", this);
	}

	public long getTopicsCount() {
		return Topic.count("forum.category", this);
	}

	public long getPostsCount() {
		return Post.count("topic.forum.category", this);
	}

	public List<Forum> getForums(int page, int pageSize) {
		return Forum.find("category = ?", this).fetch(page, pageSize);
	}

	public Post getLastPost() {
		return Post.find("topic.forum.category = ? order by postedAt desc",
				this).first();
	}

	// ~~~~~~~~~~~~

	public static List<Category> forUser(String email) {
		return Category.find("select distinct c from Category c, Permission p, UserGroup g, User u " +
				"where p.category = c and p.group = g and u.group = g and u.email = ? and p.accessLevel > 0", email).fetch();
	}

}
