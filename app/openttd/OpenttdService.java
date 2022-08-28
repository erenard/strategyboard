package openttd;

import com.openttd.network.admin.GameInfo;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import models.forum.User;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.Scenario;

import play.Logger;
import play.db.jpa.JPA;

import com.openttd.robot.ExternalServices.ExternalGameService;
import com.openttd.robot.ExternalServices.ExternalUserService;
import com.openttd.robot.model.ExternalUser;
import com.openttd.robot.model.GamePlayer;

public class OpenttdService implements ExternalUserService, ExternalGameService {
	
	@Override
	public ExternalUser identifyUser(String loginToken) {
		boolean readonly = true;
		JPA.startTx(JPA.DEFAULT, readonly);
		try {
			User user = User.findByUUID(loginToken);
			if(user != null) {
				ExternalUser externalUser = new ExternalUser();
				externalUser.setAdmin(user.group.admin);
				externalUser.setId(user.id);
				externalUser.setName(user.name);
				return externalUser;
			}
			return null;
		} finally {
			JPA.rollbackTx(JPA.DEFAULT);
		}
	}

    private final Map<Long, GameInfo> gameInfoByScenario = new HashMap<>();
    private final Map<Long, Date> gameStartingDateByScenario = new HashMap<>();

	@Override
	public void saveGame(long scenarioId, Collection<GamePlayer> gamePlayers) {
		Logger.info("saveGameStat(" + scenarioId + ", " + gamePlayers + ")");

		//Vérifications sur les loggins
		//Logged user / companies
		Map<GamePlayer, User> userByGamePlayer = new HashMap<>();

		JPA.startTx(JPA.DEFAULT, false);
		try {
			//Filtrage des companies présentent loggées...
			for(GamePlayer gamePlayer : gamePlayers) {
				ExternalUser externalUser = gamePlayer.getExternalUser();
				if(externalUser != null) {
					long userId = externalUser.getId();
					if(userId != 0) {
						User user = User.findById(userId);
						if(user != null) {
							userByGamePlayer.put(gamePlayer, user);
						} else Logger.error("user null");
					} else Logger.error("userId null");
				} else Logger.info("Pas de CEO");
			}

            //Ecriture des stats générales de la partie
			PlayedGame playedGame = new PlayedGame();
			playedGame.scenario = Scenario.findById(scenarioId);
			playedGame.begin = gameStartingDateByScenario.containsKey(scenarioId) ? gameStartingDateByScenario.get(scenarioId) : new Date();
			playedGame.end = new Date();
			playedGame.create();
			
			//Ecriture des stats par participant
			for(GamePlayer gamePlayer : userByGamePlayer.keySet()) {
				User user = userByGamePlayer.get(gamePlayer);
				
				PlayedGameScore score = new PlayedGameScore();
				score.playedGame = playedGame;
				score.achivement = gamePlayer.getAccomplishment();
				score.presenceRatio = gamePlayer.getDuration();
				score.user = user;
				score.create();
			}
			JPA.closeTx(JPA.DEFAULT);
		} catch(Exception e) {
			JPA.rollbackTx(JPA.DEFAULT);
		}
		Logger.info("saveGameStat() : Fin");
	}

	@Override
	public void endGame(long scenarioId) {
		OpenttdAdminHandler admin = OpenttdAdminHandler.getInstance();
		if(admin.isRunning(scenarioId)) {
			admin.restart(scenarioId);
			Logger.info("Admin restart");
		}
        
        if(admin.isRunning(scenarioId)) {
			admin.shutdown(scenarioId);
			Logger.info("Admin shutted down");
		}
//		OpenttdClientHandler client = OpenttdClientHandler.getInstance();
//		if(client.isRunning()) {
//			client.shutdown();
//			Logger.info("Client shutted down");
//		}
		if(!admin.isRunning(scenarioId)) {
			admin.startup(scenarioId);
			Logger.info("Admin started up");
		}
//		if(!client.isRunning()) {
//			client.startup();
//			Logger.info("Client started up");
//		}
        gameStartingDateByScenario.put(scenarioId, new Date());
	}
    

    @Override
    public void exposeGame(long scenarioId, GameInfo gameState) {
        gameInfoByScenario.put(scenarioId, gameState);
    }

    @Override
    public GameInfo getGame(long scenarioId) {
        return gameInfoByScenario.get(scenarioId);
    }
}
