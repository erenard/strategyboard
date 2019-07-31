package models.forum;

import javax.persistence.*;
import play.db.jpa.*;
import play.data.validation.*;

@Entity
public class Permission extends Model {

    @Required
    @ManyToOne
    public Category category;

    @Required
    @ManyToOne
    public UserGroup group;

    static public final int administrate = 5;
    static public final int moderate	 = 4;
    static public final int post		 = 3;
    static public final int reply		 = 2;
    static public final int view		 = 1;

    @Required
    public Integer accessLevel;
    
    public Permission(Category category, UserGroup group, Integer accessLevel) {
	 this.accessLevel = accessLevel;
	 this.group = group;
	 this.category = category;
   }
    
    static public Permission findByUserGroupAndCategory(UserGroup userGroup, Category category) {
    	return Permission.find("group = ?0 and category = ?1", userGroup, category).first();
    }
    
    @Override
    public String toString() {
    	return group.name + " - " + accessLevel + " - " + category.name;
    }
}

