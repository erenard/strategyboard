package openttd;

import com.openttd.client.OpenttdClient;
import com.openttd.network.core.Configuration;

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
	
	public synchronized void startup(int port) {
        Configuration conf = new Configuration();
        conf.clientPort = port;
        client = new OpenttdClient(conf);
        client.startup();
	}
	
	public synchronized void shutdown() {
		if(client != null) {
			client.shutdown();
		}
	}
}
