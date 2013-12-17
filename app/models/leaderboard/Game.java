package models.leaderboard;

import javax.persistence.Entity;

import play.data.validation.Required;
import play.db.jpa.Model;

@Entity
public class Game extends Model {
   @Required
   public String name;
}
