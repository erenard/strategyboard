package openttd;

import com.openttd.client.OpenttdClient;

/**
 * Cette classe gère le cycle de vie du client openttd
 */
public class OpenttdClientHandler {
	private static OpenttdClientHandler instance = new OpenttdClientHandler();
	
	private OpenttdClient client = null;
	
	private OpenttdClientHandler() {
		
	}
	
	public static OpenttdClientHandler getInstance() {
		return instance;
	}
	
	public synchronized boolean isRunning() {
		return client != null && client.isConnected();
	}
	
	public synchronized void startup() {
		OpenttdServerHandler server = OpenttdServerHandler.getInstance();
		if(server.isRunning()) {
			client = new OpenttdClient(server.getConfiguration());
			client.startup();
		}
	}
	
	public synchronized void shutdown() {
		if(client != null) {
			client.shutdown();
		}
	}
}
