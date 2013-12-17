package models.leaderboard;

import javax.persistence.*;
import play.data.validation.Required;
import play.db.jpa.Model;

@Entity
public class Scenario extends Model {
   @Required
   public String name;
   
   @Required
   @ManyToOne
   public Game game;
   
   @Override
	public String toString() {
		return name;
	}
   
   public String getImage() {
	   switch(this.id.intValue()) {
	   case 1:
		   return "/images/openttd/highSpeed_on.gif";
	   case 2:
		   return "/images/openttd/toyland_on.gif";
	   case 3:
		   return "/images/openttd/artic_on.gif";
	   case 4:
		   return "/images/openttd/desertic_on.gif";
	   case 5:
		   return "/images/openttd/steam_on.gif";
	   default:
		   return "/images/openttd/openttdLogo.png";
	   }
   }

   public String getTrainImage() {
	   switch(this.id.intValue()) {
	   default:
	   case 1:
		   return "/images/openttd/high_speed_train.gif";
	   case 2:
		   return "/images/openttd/toyland_train.gif";
	   case 3:
		   return "/images/openttd/artic_train.gif";
	   case 4:
		   return "/images/openttd/desertic_train.gif";
	   case 5:
		   return "/images/openttd/steam_train.gif";
	   }
   }
}
