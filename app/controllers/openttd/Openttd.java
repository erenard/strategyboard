package controllers.openttd;

import java.util.List;

import controllers.Application;
import controllers.Check;
import controllers.Profile;

import openttd.OpenttdAdminHandler;

import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.PlayerScore;
import models.leaderboard.Scenario;
import models.openttd.OpenttdServer;
import models.openttd.RobotConfiguration;
import play.mvc.With;

@With(Profile.class)
public class Openttd extends Application {
	
	@Check(value = {Profile.VIEWER})
	public static void index() {
		OpenttdServer articStatus = OpenttdServer.getStatus(RobotConfiguration.ARTIC);
        OpenttdServer deserticStatus = OpenttdServer.getStatus(RobotConfiguration.DESERTIC);
		OpenttdServer highSpeedStatus = OpenttdServer.getStatus(RobotConfiguration.HIGH_SPEED);
		OpenttdServer steamStatus = OpenttdServer.getStatus(RobotConfiguration.STEAM);
		OpenttdServer toylandStatus = OpenttdServer.getStatus(RobotConfiguration.TOYLAND);

		List<PlayerScore> playerScores = PlayedGameScore.getLastMonthPlayerScores(1, 10);
		List<PlayedGame> lastPlayedGames = PlayedGame.getLastPlayedGames();
		List<PlayedGame> bestArticGames = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.Artic);
		List<PlayedGame> bestDeserticGames = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.Desertic);
		List<PlayedGame> bestHighSpeedGames = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.HighSpeed);
		List<PlayedGame> bestSteamGames = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.Steam);
		List<PlayedGame> bestToylandGames = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.Toyland);
		render(
				articStatus,
                deserticStatus,
                highSpeedStatus,
                steamStatus,
                toylandStatus,
                playerScores,
				lastPlayedGames,
				bestArticGames,
				bestDeserticGames,
				bestHighSpeedGames,
				bestSteamGames,
				bestToylandGames
		);
	}
	
	@Check(value = {Profile.MODERATOR})
	public static void start(Long scenarioId) {
		OpenttdAdminHandler adminHandler = OpenttdAdminHandler.getInstance();
		if(!adminHandler.isRunning(scenarioId)) {
			adminHandler.startup(scenarioId);
		}
		index();
	}

	@Check(value = {Profile.MODERATOR})
	public static void stop(Long scenarioId) {
		OpenttdAdminHandler adminHandler = OpenttdAdminHandler.getInstance();
		if(adminHandler.isRunning(scenarioId)) {
			adminHandler.shutdown(scenarioId);
		}
		index();
	}
}
