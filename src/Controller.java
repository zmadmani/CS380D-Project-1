import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;

/**
 * Controller
 * @author Julian
 *
 */
public class Controller {
	NetController masterNetwork;
	Config configFile;
	Process[] idToProc; // i-th position corresponds to i-th process
	Process currLeader;
	Process lastKilled;
	
	public Controller() throws FileNotFoundException, IOException {
		Config configP0 = new Config("properties_p0.txt");
		NetController controlP0 = new NetController(configP0);
		Config configP1 = new Config("properties_p1.txt");
		NetController controlP1 = new NetController(configP1);
		Config configP2 = new Config("properties_p2.txt");
		NetController controlP2 = new NetController(configP2);
		Config configP3 = new Config("properties_p3.txt");
		NetController controlP3 = new NetController(configP3);
		Config configP4 = new Config("properties_p4.txt");
		NetController controlP4 = new NetController(configP4);
		controlP0.shutdown();
		controlP1.shutdown();
		controlP2.shutdown();
		controlP3.shutdown();
		controlP4.shutdown();		}
	
	public void initiateAdd(String songName, String URL) {
		
	}
	
	public void initiateRemove(String songName) {
		
	}
	
	public void initiateEdit(String songName, String URL) {
		
	}
	
	public void createProcesses() {
		
	}
	
	public void kill(Integer id) {
		
	}
	
	public void killAll() {
		
	}
	
	public void killLeader() {
		
	}
	
	public void revive(Integer id) {
		
	}
	
	public void reviveLast(Integer id) {
		
	}
	
	public void reviveAll() {
		
	}
	
	public void partialMessage(Integer id, Integer numMsgs) {
		
	}
	
	public void resumeMessages(Integer id) {
		
	}
	
	public void allClear() {
		
	}
	
	public void rejectNextChange(Integer id) {
		
	}
	
	
	public void runCommmands() {
		
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException {
		Controller mainController = new Controller();
	}

	
	
}