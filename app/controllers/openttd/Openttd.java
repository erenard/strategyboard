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
import play.cache.Cache;
import play.mvc.Before;
import play.mvc.With;

@With(Profile.class)
public class Openttd extends Application {

    private static OpenttdAdminHandler adminHandler;

    @Before
    static void dependencies() {
        adminHandler = OpenttdAdminHandler.getInstance();
    }

    @Check(value = {Profile.VIEWER})
    public static void index() {
        OpenttdServer articStatus = OpenttdServer.getStatus(RobotConfiguration.ARTIC);
        OpenttdServer deserticStatus = OpenttdServer.getStatus(RobotConfiguration.DESERTIC);
        OpenttdServer highSpeedStatus = OpenttdServer.getStatus(RobotConfiguration.HIGH_SPEED);
        OpenttdServer steamStatus = OpenttdServer.getStatus(RobotConfiguration.STEAM);
        OpenttdServer toylandStatus = OpenttdServer.getStatus(RobotConfiguration.TOYLAND);

        List<PlayerScore> playerScores = Cache.get("lastMonthPlayerScores", List.class);
        if (playerScores == null) {
            playerScores = PlayedGameScore.getLastMonthPlayerScores(1, 10);
            Cache.set("lastMonthPlayerScores", playerScores, "5s");
        }
        List<PlayedGame> lastPlayedGames = Cache.get("lastPlayedGames", List.class);
        if (lastPlayedGames == null) {
            lastPlayedGames = PlayedGame.getLastPlayedGames();
            Cache.set("lastPlayedGames", lastPlayedGames, "5s");
        }
        List bestArticGames = Cache.get("bestArticGames", List.class);
        if (bestArticGames == null) {
            bestArticGames = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.Artic);
            Cache.set("bestArticGames", bestArticGames, "5s");
        }
        List bestDeserticGames = Cache.get("bestDeserticGames", List.class);
        if (bestDeserticGames == null) {
            bestDeserticGames = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.Desertic);
            Cache.set("bestDeserticGames", bestDeserticGames, "5s");
        }
        List bestHighSpeedGames = Cache.get("bestHighSpeedGames", List.class);
        if (bestHighSpeedGames == null) {
            bestHighSpeedGames = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.HighSpeed);
            Cache.set("bestHighSpeedGames", bestHighSpeedGames, "5s");
        }
        List bestSteamGames = Cache.get("bestSteamGames", List.class);
        if (bestSteamGames == null) {
            bestSteamGames = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.Steam);
            Cache.set("bestSteamGames", bestSteamGames, "5s");
        }
        List bestToylandGames = Cache.get("bestToylandGames", List.class);
        if (bestToylandGames == null) {
            bestToylandGames = PlayedGame.getBestPlayedGamesForScenarioId(Scenario.Id.Toyland);
            Cache.set("bestToylandGames", bestToylandGames, "5s");
        }
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
        if (adminHandler != null && !adminHandler.isRunning(scenarioId)) {
            adminHandler.startup(scenarioId);
        }
        index();
    }

    @Check(value = {Profile.MODERATOR})
    public static void stop(Long scenarioId) {
        if (adminHandler != null && adminHandler.isRunning(scenarioId)) {
            adminHandler.shutdown(scenarioId);
        }
        index();
    }
}
