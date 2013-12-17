package models.forum;

import javax.persistence.*;

import java.util.*;

import play.db.jpa.*;
import play.data.validation.*;

@Entity
public class UserGroup extends Model {
   
   public static final String ANONYMOUS = "Anonymous";
   public static final String GENERAL = "General";
   public static final String MODERATORS = "Moderators";
   public static final String ADMINISTRATORS = "Admin";

   @Required
   @MaxSize(50)
   public String name;
   
   @MaxSize(1000)
   public String description;

   @OneToMany(cascade = CascadeType.ALL, mappedBy = "group")
   public List<Permission> permissions;
   
   public Boolean admin;

   // ~~~~~~~~~~~~ 

   @Override
   public String toString() {
	return name;
   }

   // ~~~~~~~~~~~~ 

   public long getUsersCount() {
	return User.count("group", this);
   }

   public List<User> getUsers(int page, int pageSize) {
	return User.find("group", this).fetch(page, pageSize);
   }

   // ~~~~~~~~~~~~ 

   public static UserGroup findByName(String name) {
	return UserGroup.find("name", name).first();
   }
}