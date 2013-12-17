package models.forum;

import java.util.Date;

import javax.persistence.*;

import play.data.binding.As;
import play.data.validation.Required;
import play.db.jpa.Model;

@Entity
public class Attachment extends Model {

	@ManyToOne
	public Post post;
	
	@ManyToOne
	public Message message;
	
	@ManyToOne
	public User user;
	
	public String mimetype;

	public String description;

	public Integer downloads;
	
	@Required
	public String filename;
	
	@As("yyyy-MM-dd")
	public Date at;
	
	public Integer size;
	
	@Required
	public String realfilename;

	@Override
	public String toString() {
		return mimetype + ":" + filename
				+ (user != null ? " - User : " + user : "")
				+ (post != null ? " - Post : " + post : "")
				+ (message != null ? " - Message : " + message : "");
	}
}
