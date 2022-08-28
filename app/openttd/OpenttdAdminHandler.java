package openttd;

import play.Play;
import com.openttd.admin.OpenttdAdmin;
import com.openttd.network.admin.NetworkAdminSender;
import com.openttd.network.core.Configuration;
import com.openttd.robot.ExternalServices;
import com.openttd.robot.rule.Administration;
import com.openttd.robot.rule.CompanyLifeCycle;
import com.openttd.robot.rule.CompanyPasswordRemainder;
import com.openttd.robot.rule.ExposeGameInfo;
import com.openttd.robot.rule.ExternalUsers;
import com.openttd.robot.rule.ServerAnnouncer;
import com.openttd.robot.rule.TimerObjective;
import java.util.HashMap;
import java.util.Map;
import models.openttd.RobotConfiguration;

/**
 * Cette classe gère le cycle de vie de l'admin robot openttd
 */
public class OpenttdAdminHandler {
	private static final OpenttdAdminHandler instance = new OpenttdAdminHandler();
	
    private final Map<Long, OpenttdAdmin> robots = new HashMap<>();
	
	private OpenttdAdminHandler() {
        OpenttdService service = new OpenttdService();
		ExternalServices.getInstance().setExternalGameService(service);
		ExternalServices.getInstance().setExternalUserService(service);
	}
	
	public static OpenttdAdminHandler getInstance() {
		return instance;
	}
	
	public synchronized boolean isRunning(Long scenarioId) {
        OpenttdAdmin robot = robots.get(scenarioId);
		return robot != null && robot.isConnected();
	}

	public synchronized void startup(Long scenarioId) {
        RobotConfiguration configuration = RobotConfiguration.fromScenarioId(scenarioId);
        Configuration c = new Configuration();
        c.adminPort = configuration.adminPort;
        c.password = Play.configuration.getProperty("openttd.password");
        OpenttdAdmin robot = new OpenttdAdmin(c);
        robots.put(scenarioId, robot);
        robot.startup();
        ExternalUsers externalUsers = new ExternalUsers(robot);
        CompanyLifeCycle companyLifeCycle = new CompanyLifeCycle(robot, externalUsers);
        companyLifeCycle.checkLogin = false;
        CompanyPasswordRemainder companyPasswordRemainder = new CompanyPasswordRemainder(robot);
        ServerAnnouncer serverAnnouncer = new ServerAnnouncer(robot);
        TimerObjective timerObjective = new TimerObjective(robot, externalUsers, configuration.scenarioId, 10);
        Administration administration = new Administration(robot, externalUsers);
        ExposeGameInfo exposeGameInfo = new ExposeGameInfo(robot, configuration.scenarioId);
	}

	public synchronized void restart(Long scenarioId) {
        OpenttdAdmin robot = robots.get(scenarioId);
		if(robot != null) {
            NetworkAdminSender send = robot.getSend();
            if(send != null) send.rcon("restart");
		}
	}

	public synchronized void shutdown(Long scenarioId) {
        OpenttdAdmin robot = robots.get(scenarioId);
		if(robot != null) {
			robot.shutdown();
		}
	}
}
