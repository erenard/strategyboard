package models.openttd;

import com.openttd.network.admin.GameInfo;
import com.openttd.robot.ExternalServices;
import java.text.SimpleDateFormat;

import models.leaderboard.Scenario;

import play.Logger;

import com.openttd.util.Convert;

public class OpenttdServer {
	public String name = "Plop";
	public String image = "highSpeed_on.gif";
	public int width = 512;
	public int height = 1024;
	public String startedDate = "01/01/1980";
	public String currentDate = "07/04/1991";
	public int companiesCount = 2;
	public int clientsCount = 5;
	public String goal = "Reach the best company performance in 10 years to win the game.";
	public String revision = "1.2.3";
	
	public static OpenttdServer getStatus(RobotConfiguration robotConfiguration) {
        ExternalServices.ExternalGameService gameService = ExternalServices.getInstance().getExternalGameService();
        try {
            GameInfo gameInfo = gameService.getGame(robotConfiguration.scenarioId);
            Scenario scenario = Scenario.findById(robotConfiguration.scenarioId);
            if(gameInfo != null && scenario != null) {
                return new OpenttdServer(gameInfo, scenario);
            }
        } catch (Exception e) {
            Logger.error(e.getMessage(), e);
        }
		return null;
	}
	
	private OpenttdServer(GameInfo gameInfo, Scenario scenario) {
		if(gameInfo == null) {
			throw new IllegalArgumentException("GameInfo == null");
		}
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        this.name = gameInfo.getServerName();
        this.revision = gameInfo.getServerRevision();
		this.goal = "Reach the best company performance in 10 years to win the game.";
        this.startedDate = sdf.format(gameInfo.getStartDate());
		if(scenario != null) this.image = scenario.getImage();

		this.width = gameInfo.getMapWidth();
		this.height = gameInfo.getMapHeight();
		this.currentDate = sdf.format(Convert.dayToCalendar(gameInfo.getCurrentDate()).getTime());
		this.clientsCount = gameInfo.clientsCount;
		this.companiesCount = gameInfo.companiesCount;
	}
}
