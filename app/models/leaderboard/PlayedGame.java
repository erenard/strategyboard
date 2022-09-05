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

    public static List<PlayedGame> getBestPlayedGamesForScenarioId(Scenario.Id scenarioId) {
        return find("select s.playedGame, max(s.achivement) as ma, s.user from PlayedGameScore s "
            + "where s.playedGame.scenario.id = " + scenarioId.longValue() + " "
            + "group by s.user order by ma desc").fetch(10);
    }
}
