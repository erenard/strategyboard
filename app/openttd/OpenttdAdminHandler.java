package openttd;

import com.openttd.admin.OpenttdAdmin;
import com.openttd.robot.ExternalServices;
import com.openttd.robot.rule.Administration;
import com.openttd.robot.rule.CompanyLifeCycle;
import com.openttd.robot.rule.CompanyPasswordRemainder;
import com.openttd.robot.rule.ExternalUsers;
import com.openttd.robot.rule.ServerAnnouncer;
import com.openttd.robot.rule.TimerObjective;

/**
 * Cette classe gère le cycle de vie de l'admin robot openttd
 */
public class OpenttdAdminHandler {
	private static OpenttdAdminHandler instance = new OpenttdAdminHandler();
	
	private OpenttdAdmin robot = null;
	
	private OpenttdAdminHandler() {
		
	}
	
	public static OpenttdAdminHandler getInstance() {
		return instance;
	}
	
	public synchronized boolean isRunning() {
		return robot != null && robot.isConnected();
	}
	
	public synchronized void startup() {
		OpenttdService service = new OpenttdService();
		ExternalServices.getInstance().setExternalGameService(service);
		ExternalServices.getInstance().setExternalUserService(service);
		OpenttdServerHandler server = OpenttdServerHandler.getInstance();
		if(server.isRunning()) {
			robot = new OpenttdAdmin(server.getConfiguration());
			robot.startup();
			ExternalUsers externalUsers = new ExternalUsers(robot);
			//robot.addListener(externalUsers);
			CompanyLifeCycle companyLifeCycle = new CompanyLifeCycle(robot, externalUsers);
			companyLifeCycle.checkLogin = false;
			//robot.addListener(companyLifeCycle);
			CompanyPasswordRemainder companyPasswordRemainder = new CompanyPasswordRemainder(robot);
			//robot.addListener(companyPasswordRemainder);
			ServerAnnouncer serverAnnouncer = new ServerAnnouncer(robot, externalUsers);
			//robot.addListener(serverAnnouncer);
//			CompanyEconomyObjective companyEconomyObjective = new CompanyEconomyObjective(robot, companyLifeCycle, externalUsers, server.getGoalType(), server.getGoalValue());
//			robot.addListener(companyEconomyObjective);
			TimerObjective timerObjective = new TimerObjective(robot, externalUsers, server.getTimerDuration());
			//robot.addListener(timerObjective);
			Administration administration = new Administration(robot, externalUsers);
			//robot.addListener(administration);
		}
	}
	
	public synchronized void shutdown() {
		if(robot != null) {
			robot.shutdown();
		}
	}
}
