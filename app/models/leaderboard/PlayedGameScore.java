package models.leaderboard;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import javax.persistence.*;
import models.forum.User;
import play.data.validation.Required;
import play.db.jpa.Model;

@Entity
public class PlayedGameScore extends Model {

	@Required
	@ManyToOne
	public PlayedGame playedGame;

	@Required
	@ManyToOne
	public User user;

	public double presenceRatio;

	public double achivement;

	public static PlayerScore getWinner(PlayedGame playedGame) {
		Query q = Model.em()
				.createQuery("select cast(score.achivement / score.presenceRatio as int), score from PlayedGameScore score where score.playedGame = ? order by 1 desc");
		q.setParameter(1, playedGame);
		List<Object[]> results = q.getResultList();
		for (Object[] result : results) {
			PlayerScore playerScore = new PlayerScore();
			playerScore.points = (Integer) result[0];
			playerScore.player = ((PlayedGameScore) result[1]).user;
			return playerScore;
		}
		return null;
	}

	public static List<PlayerScore> getLastMonthPlayerScores(Integer page, Integer pageSize) {
		Calendar from = Calendar.getInstance();
		from.add(Calendar.MONTH, -1);
		Calendar to = Calendar.getInstance();
		return PlayedGameScore.getPlayerScores(from, to, 1, 10);
	}

	public static List<PlayerScore> getPlayerScores(Calendar from, Calendar to, Integer page, Integer pageSize) {
		if (from == null) {
			from = Calendar.getInstance();
			from.setTimeInMillis(0);
		}
		if (to == null) {
			to = Calendar.getInstance();
		}
		Query q = Model.em()
				.createQuery("select distinct cast(sum(score.achivement / score.presenceRatio) as int), score.user, count(score) from PlayedGameScore score where score.playedGame.end >= ? and score.playedGame.end < ? group by score.user order by 1 desc, 3 desc, 2 asc");
		if (page != null && pageSize != null) {
			q.setFirstResult((page - 1) * pageSize);
			q.setMaxResults(pageSize);
		}
		q.setParameter(1, from.getTime());
		q.setParameter(2, to.getTime());
		List<Object[]> rows = q.getResultList();

		List<PlayerScore> playerScores = new ArrayList<PlayerScore>(rows.size());
		int i = 1 + ((page != null && pageSize != null) ? (page - 1) * pageSize : 0);
		for (Object[] row : rows) {
			PlayerScore playerScore = new PlayerScore();
			playerScore.index = i++;
			playerScore.points = (Integer) row[0];
			playerScore.player = (User) row[1];
			playerScore.gameCount = (Long) row[2];
			playerScores.add(playerScore);
		}

		return playerScores;
	}

	public static long count(Calendar from, Calendar to) {
		Query q = Model.em()
				.createQuery("select distinct score.user from PlayedGameScore score where score.playedGame.end >= ? and score.playedGame.end < ?");
		q.setParameter(1, from.getTime());
		q.setParameter(2, to.getTime());
		return (long) q.getResultList().size();
	}

}
