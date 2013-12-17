package models.openttd;

import java.text.SimpleDateFormat;

import models.leaderboard.Scenario;

import openttd.OpenttdServerHandler;

import play.Logger;

import com.openttd.network.udp.GameInfo;
import com.openttd.network.udp.GameInfoClient;
import com.openttd.util.Convert;

public class OpenttdServer {
	public String name = "Plop";
	public String image = "highSpeed_on.gif";
	public int width = 512;
	public int height = 1024;
	public String startedDate = "01/01/1980";
	public String currentDate = "07/04/1991";
	public int companiesCount = 2;
	public int companiesCapacity = 10;
	public int clientsCount = 5;
	public int clientsCapacity = 30;
	public String goal = "Reach the best company performance in 10 years to win the game.";
	public String revision = "1.2.3";
	
	public static OpenttdServer getStatus() {
		if(OpenttdServerHandler.getInstance().isRunning()) {
			try {
				GameInfoClient gameInfoClient = new GameInfoClient("localhost", 3979);
				GameInfo gameInfo = gameInfoClient.getGameInfo();
				Scenario scenario = Scenario.findById(OpenttdServerHandler.getInstance().getScenarioId());
				if(gameInfo != null) {
					return new OpenttdServer(gameInfo, scenario);
				}
			} catch (Exception e) {
				Logger.error(e.getMessage(), e);
			}
		}
		return null;
	}
	
	private OpenttdServer(GameInfo gameInfo, Scenario scenario) {
		if(gameInfo == null) {
			throw new IllegalArgumentException("GameInfo == null");
		}
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
		this.clientsCapacity = gameInfo.getClientsMax();
		this.clientsCount = gameInfo.getClientsOn();
		this.companiesCapacity = gameInfo.getCompaniesMax();
		this.companiesCount = gameInfo.getCompaniesOn();
		this.currentDate = sdf.format(Convert.dayToCalendar(gameInfo.getGameDate()).getTime());
		this.height = gameInfo.getMapHeight();
		if(scenario != null) this.image = scenario.getImage();
		this.name = gameInfo.getServerName();
		this.revision = gameInfo.getServerRevision();
		this.startedDate = sdf.format(Convert.dayToCalendar(gameInfo.getGameStartDate()).getTime());
		this.width = gameInfo.getMapWidth();
	}
}
