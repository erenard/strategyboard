package models.forum;

import javax.persistence.*;

import java.util.*;

import play.data.binding.*;

import play.db.jpa.*;

@Entity
public class Post extends Model {

    public String subject;

    @Lob
    public String content;
    
    @As("yyyy-MM-dd")
    public Date postedAt;
    
    @As("yyyy-MM-dd")
    public Date editedAt;
    
    @OneToOne
    public User postedBy;
    
    @ManyToOne
    public Topic topic;
    
    // ~~~~~~~~~~~~ 
    
    public Post() {}
    
    public Post(Topic topic, User postedBy, String subject, String content) {
        this.topic = topic;
        this.postedBy = postedBy;
        this.subject = subject;
        this.content = content;
        this.postedAt = new Date();
        create();
    }
    
    // ~~~~~~~~~~~~ 
    
    public List<Attachment> getAttachments() {
    	return Attachment.find("post", this).fetch();
    }
}

