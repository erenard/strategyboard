package models.forum;

import javax.persistence.*;
import java.util.*;

import play.db.jpa.*;

@Entity
public class Topic extends Model {

    @ManyToOne
    public Forum forum;
    
    public Integer views = 0;
    
    public Boolean locked = Boolean.FALSE;

    public Boolean hidden = Boolean.FALSE;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "topic")
    public List<Post> posts;
    
    // ~~~~~~~~~~~~ 
    
    public Topic() {};
    
    public Topic(Forum forum, User by, String subject, String content) {
        this.forum = forum;
        create();
        new Post(this, by, subject, content);
    }
    
    // ~~~~~~~~~~~~ 
    
    public Post reply(User by, String subject, String content) {
        return new Post(this, by, subject, content);
    }
    
    // ~~~~~~~~~~~~ 
    
    public List<Post> getPosts(int page, int pageSize) {
        return Post.find("topic", this).fetch(page, pageSize);
    }

    public Long getPostsCount() {
        return Post.count("topic", this);
    }

    public Long getVoicesCount() {
        return User.count("select count(distinct u) from User u, Topic t, Post p where p.postedBy = u and p.topic = t and t = ?", this);
    }

    public Post getFirstPost() {
       return Post.find("topic = ? order by postedAt asc", this).first();
   }
   
    public Post getLastPost() {
        return Post.find("topic = ? order by postedAt desc", this).first();
    }
    
}

