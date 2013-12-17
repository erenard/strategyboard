package controllers.openttd;

import java.util.Calendar;
import java.util.List;

import controllers.Application;
import controllers.Check;
import controllers.Profile;

import models.leaderboard.PlayedGameScore;
import models.leaderboard.PlayerScore;
import play.cache.CacheFor;
import play.mvc.With;
import tasks.ExportScoresCsvGenerator;

@With(Profile.class)
public class Scores extends Application {

	public static String period_last_month = "last_month";
	public static String period_forever = "forever";
	
	@Check(value = {Profile.VIEWER})
	public static void index() {
		show(period_last_month, 1);
	}
	
	@CacheFor("5s")
	@Check(value = {Profile.VIEWER})
	public static void show(String period, Integer page) {
		long nbScores = 0;
		List<PlayerScore> playerScores = null;
		Calendar from = Calendar.getInstance();
		Calendar to = Calendar.getInstance();
		if(period != null && period_forever.equals(period)) {
			from.setTimeInMillis(0);
		} else {
			period = period_last_month;
			from.add(Calendar.MONTH, -1);
		}
		playerScores = PlayedGameScore.getPlayerScores(from, to, page == null ? 1 : page, pageSize);
		nbScores = PlayedGameScore.count(from, to);
		render(playerScores, nbScores, period, page);
	}

	@Check(value = {Profile.MODERATOR})
	public static void exportAsCSV() {
		List<PlayedGameScore> scores = PlayedGameScore.all().fetch(10);
		ExportScoresCsvGenerator generator = new ExportScoresCsvGenerator(scores);
		response.contentType = "text/csv";
		while(generator.hasMoreData()) {
			String someCsvData = await(generator.nextDataChunk());
			response.writeChunk(someCsvData);
		}
	}

}
