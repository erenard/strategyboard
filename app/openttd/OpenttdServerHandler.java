package openttd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Properties;
import java.util.Random;

import org.apache.log4j.Logger;

import play.Play;

import com.openttd.network.core.Configuration;
import com.openttd.robot.launcher.GameProcess;
import com.openttd.robot.launcher.GameProcessOptions;
import com.openttd.robot.rule.CompanyEconomyObjective.ObjectiveType;

/**
 * Cette classe gère le cycle de vie du serveur openttd
 */
public class OpenttdServerHandler {

	private static final Logger log = Logger.getLogger(OpenttdServerHandler.class);

	//From properties file
	private final GameProcessOptions options;
	
	//From openttd.cfg
	private Configuration configuration = new Configuration();
	private int		game_league		 = -1;
	private double	goal_value		 = -1;
	private int		goal_performance = -1;
	private int		timer_duration	 = -1;
	
	private Calendar startingDate = Calendar.getInstance();

	private static OpenttdServerHandler instance = new OpenttdServerHandler();
	
	private OpenttdServerHandler() {
		String openttdPath = Play.configuration.getProperty("openttd.working.directory", "/usr/share/games/openttd/");
		String openttdLaunchCmd = Play.configuration.getProperty("openttd.launch.cmd", "/usr/games/openttd");
		options = new GameProcessOptions(openttdPath, openttdLaunchCmd);
	}
	
	public static OpenttdServerHandler getInstance() {
		return instance;
	}
	
	private GameProcess gameProcess;

	public synchronized boolean isRunning() {
		return gameProcess != null && gameProcess.isAlive();
	}
	
	public synchronized void startup() {
		try {
			randomNextMap();
			gameProcess = new GameProcess(options);
			gameProcess.create();
			startingDate = Calendar.getInstance();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
	}
	
	public synchronized void shutdown() {
		if(gameProcess != null) gameProcess.destroy();
		gameProcess = null;
	}
	
	private void randomNextMap() throws FileNotFoundException, IOException {
		configuration = new Configuration();
		game_league		 = -1;
		goal_value		 = -1;
		goal_performance = -1;
		timer_duration	 = -1;
		String openttdConfPath = "conf" + File.separator + "openttd" + File.separator;
		File propertyFile = Play.getFile(openttdConfPath + "fichiers.properties");
		Properties properties = new Properties();
		properties.load(new FileReader(propertyFile));
		String [] tirage = properties.getProperty("fichiers.tirage").split("#");
		Random random = new Random(System.currentTimeMillis());
		int suffixNumber = random.nextInt(tirage.length);
		String propertySuffix = tirage[suffixNumber];
		String fileName = properties.getProperty("fichier." + propertySuffix + ".name");
		Boolean randomSeed = Boolean.valueOf(properties.getProperty("fichier." + propertySuffix + ".randomSeed"));
		File file = Play.getFile(openttdConfPath + fileName);
		if(file.exists()) {
			File openttd_cfg = new File(options.getOpenttdPath().getAbsolutePath() + File.separator + "openttd.cfg");
			if(!openttd_cfg.exists()) openttd_cfg.createNewFile();
			BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
			PrintWriter bufferedWriter = new PrintWriter(openttd_cfg);
			String line = null;
			while((line = bufferedReader.readLine()) != null) {
				if(randomSeed && line.startsWith("generation_seed")) {
					line = "generation_seed = " + random.nextInt(65535);
				}
				if(line.startsWith("goal_value = ")) {
					goal_value = Double.parseDouble(line.substring("goal_value = ".length()));
				}
				if(line.startsWith("goal_performance = ")) {
					goal_performance = Integer.parseInt(line.substring("goal_performance = ".length()));
				}
				if(line.startsWith("timer_duration = ")) {
					timer_duration = Integer.parseInt(line.substring("timer_duration = ".length()));
				}
				if(line.startsWith("game_league = ")) {
					game_league = Integer.parseInt(line.substring("game_league = ".length()));
				}
				if(line.startsWith("server_port = ")) {
					Integer server_port = Integer.parseInt(line.substring("server_port = ".length()));
					configuration.clientPort = server_port;
				}
				if(line.startsWith("server_admin_port = ")) {
					Integer server_admin_port = Integer.parseInt(line.substring("server_admin_port = ".length()));
					configuration.adminPort = server_admin_port;
				}
//				if(line.startsWith("rcon_password = ")) {
//					String rcon_password = line.substring("rcon_password = ".length());
//					configuration.rconPassword = rcon_password;
//				}
				if(line.startsWith("admin_password = ")) {
					String server_password = line.substring("admin_password = ".length());
					configuration.password = server_password;
				}
				bufferedWriter.println(line);
			}
			bufferedWriter.flush();
			bufferedWriter.close();
			bufferedReader.close();
		} else {
			log.error("File not found : " + fileName);
		}
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public long getScenarioId() {
		return (long) game_league;
	}

	public double getGoalValue() {
		if(goal_performance > 0) {
			return goal_performance;
		} else {
			return goal_value;
		}
	}
	
	public int getTimerDuration() {
		return timer_duration;
	}

	public ObjectiveType getGoalType() {
		if(goal_performance > 0) {
			return ObjectiveType.PERFORMANCE;
		} else {
			return ObjectiveType.VALUE;
		}
	}

	public Calendar getStartingDate() {
		return startingDate;
	}
}
