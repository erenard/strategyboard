package openttd;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import models.forum.User;
import models.leaderboard.PlayedGame;
import models.leaderboard.PlayedGameScore;
import models.leaderboard.Scenario;

import play.Logger;
import play.db.jpa.JPAPlugin;

import com.openttd.robot.ExternalServices.ExternalGameService;
import com.openttd.robot.ExternalServices.ExternalUserService;
import com.openttd.robot.model.ExternalUser;
import com.openttd.robot.model.GamePlayer;

public class OpenttdService implements ExternalUserService, ExternalGameService {
	
	@Override
	public ExternalUser identifyUser(String loginToken) {
		boolean readonly = true;
		boolean rollback = true;
		JPAPlugin.startTx(readonly);
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
			JPAPlugin.closeTx(rollback);
		}
	}

	@Override
	public void saveGame(Collection<GamePlayer> gamePlayers) {
		OpenttdServerHandler server = OpenttdServerHandler.getInstance();
		long scenarioId = server.getScenarioId();
		Logger.info("saveGameStat(" + scenarioId + ", " + gamePlayers + ")");

		//Vérifications sur les loggins
		//Logged user / companies
		Map<GamePlayer, User> userByGamePlayer = new HashMap<GamePlayer, User>();

		boolean readonly = false;
		boolean rollback = false;
		JPAPlugin.startTx(readonly);
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
			playedGame.begin = server.getStartingDate().getTime();
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
		} catch(Exception e) {
			rollback = true;
		} finally {
			JPAPlugin.closeTx(rollback);
		}
		Logger.info("saveGameStat() : Fin");
	}

	@Override
	public void endGame() {
		OpenttdAdminHandler admin = OpenttdAdminHandler.getInstance();
		if(admin.isRunning()) {
			admin.shutdown();
			Logger.info("Admin shutted down");
		}
		OpenttdClientHandler client = OpenttdClientHandler.getInstance();
		if(client.isRunning()) {
			client.shutdown();
			Logger.info("Client shutted down");
		}
		OpenttdServerHandler server = OpenttdServerHandler.getInstance();
		if(server.isRunning()) {
			server.shutdown();
			Logger.info("Server shutted down");
		}
		if(!server.isRunning()) {
			server.startup();
			Logger.info("Server started up");
		}
		if(server.isRunning() && !admin.isRunning()) {
			admin.startup();
			Logger.info("Admin started up");
		}
		if(server.isRunning() && !client.isRunning()) {
			client.startup();
			Logger.info("Client started up");
		}
	}

}
