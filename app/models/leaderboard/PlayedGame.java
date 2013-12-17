package models.leaderboard;

import java.util.*;

import javax.persistence.*;

import play.data.binding.As;
import play.data.validation.Required;
import play.db.jpa.Model;

@Entity
public class PlayedGame extends Model {

   @ManyToOne
   public Scenario scenario;

   @Required
   @As("yyyy-MM-dd kk:mm")
   public Date begin;
   
   @Required
   @As("yyyy-MM-dd kk:mm")
   public Date end;
   
   @OneToMany(cascade = CascadeType.ALL, mappedBy = "playedGame")
   public List<PlayedGameScore> scores = new ArrayList<PlayedGameScore>();
   
   public PlayerScore getWinner() {
	   return PlayedGameScore.getWinner(this);
   }
   
   public static List<PlayedGame> getLastPlayedGames() {
	   return find("select p from PlayedGame p order by p.end desc").fetch(10);
   }
   
   public static List<PlayedGame> getBestPlayedGames() {
	   return find("select p.playedGame from PlayedGameScore p order by p.achivement desc").fetch(10);
   }
}
