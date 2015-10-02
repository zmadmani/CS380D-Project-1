import java.util.ArrayList;
import java.util.HashMap;

/**
 * Process
 * @author Zain Admani
 *
 */
public class Process extends Thread {
	private NetController network;
	private Integer id;
	private Boolean amCoord;
	private Boolean isTransactionOn;
	private Boolean alive;
	private Integer stage; //Tells which stage of the transaction it is on currently
	private ArrayList<Integer> livingProcs;
	private HashMap<String, String> playlist;
	
	public Process(Integer id, Config configFile) {
		this.id = id;
		network = new NetController(configFile);
		alive = true;
		amCoord = false;
		isTransactionOn = false;
		stage = 0; //0 --> nothing | 1 --> vote_req | 2 --> voted | 3 --> precommit | 4 --> ack | 5 --> commit | -1 = abort
		playlist = new HashMap<String, String>();
		//livingProcs
	}
	
	public void run() {
		int i = 0;
		while(i < 5) {
			System.out.println("I AM IN PROCESS " + i);
		}
	}
	
	private void sendVoteReq() {
		
	}
	
	private Integer Vote() {
		return 0;
	}
	
	private void Abort() {
		
	}
	
	private void PreCommit() {
		
	}
	
	private void Commit() {
		
	}
	
	private void Ack() {
		
	}
}
