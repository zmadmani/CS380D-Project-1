import java.util.ArrayList;
import java.util.HashMap;

/**
 * Process
 * @author Zain Admani
 *
 */
public class Process {
	private NetController network;
	private Integer id;
	private Boolean amCoord;
	private Boolean isTransactionOn;
	private Integer stage; //Tells which stage of the transaction it is on currently
	private ArrayList<Integer> livingProcs;
	private HashMap<String, String> playlist;
	
	public Process(Integer id) {
		
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
