package models.forum;

import javax.persistence.*;
import java.util.*;
import play.db.jpa.*;
import play.data.validation.*;

@Entity
public class Forum extends Model {

	@Required
	@MaxSize(50)
	public String name;
	@Required
	@MaxSize(1000)
	public String description;
	@Required
	public Integer displayOrder;
	@Required
	@ManyToOne
	public Category category;

	public Boolean locked = Boolean.FALSE;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "forum")
	public List<Topic> topics;

	// ~~~~~~~~~~~~

	@Override
	public String toString() {
		return name;
	}

	// ~~~~~~~~~~~~

	public Forum(Category category, String name, String description, Integer displayOrder) {
		this.category = category;
		this.name = name;
		this.description = description;
		this.displayOrder = displayOrder;
		create();
	}

	// ~~~~~~~~~~~~

	public Topic newTopic(User by, String subject, String content) {
		Topic t = new Topic(this, by, subject, content);
		this.refresh();
		return t;
	}

	// ~~~~~~~~~~~~

	public long getVisibleTopicsCount() {
		return Topic.count("forum = ? and hidden = false", this);
	}

	public long getTopicsCount() {
		return Topic.count("forum", this);
	}

	public long getVisiblePostsCount() {
		return Post.count("topic.forum = ? and topic.hidden = false", this);
	}
	
	public long getPostsCount() {
		return Post.count("topic.forum", this);
	}

	public List<Topic> getVisibleTopics(int page, int pageSize) {
		return Topic.find("select distinct t from Topic t, Post p where p.topic = t and t.forum = ? and hidden = false order by p.postedAt desc", this).fetch(page, pageSize);
	}

	public List<Topic> getTopics(int page, int pageSize) {
		return Topic.find("select distinct t from Topic t, Post p where p.topic = t and t.forum = ? order by p.postedAt desc", this).fetch(page, pageSize);
	}

	public Post getLastPost() {
		return Post.find("topic.forum = ? and topic.hidden = false order by postedAt desc", this).first();
	}

}
