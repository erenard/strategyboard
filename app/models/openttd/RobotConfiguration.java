package models.openttd;

public enum RobotConfiguration {
    ARTIC(3977, 3),
    DESERTIC(4077, 4),
    HIGH_SPEED(4177, 1),
    STEAM(4277, 5),
    TOYLAND(4377, 2);
    
    RobotConfiguration(int adminPort, long scenarioId) {
        this.adminPort = adminPort;
        this.scenarioId = scenarioId;
    }
    
    public final int adminPort;
    public final long scenarioId;
    
    public static RobotConfiguration fromScenarioId(long scenarioId) {
        for(RobotConfiguration rc : RobotConfiguration.values()) {
            if(rc.scenarioId == scenarioId) return rc;
        }
        return null;
    }
}
