import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

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
	private Integer transacting; // tells if not in a transaction (-1), transaction-in-progress (0)
	private Boolean[] livingProcs;
	private HashMap<String, String> playlist;
	private ArrayList<Integer> sinceLastMessage;
	private Long time;
	private String command;
	
	public Process(Integer id, Config configFile) {
		this.id = id;
		network = new NetController(configFile);
		alive = true;
		amCoord = false;
		isTransactionOn = false;
		stage = 0; //0 --> nothing | 1 --> vote_req | 2 --> precommit
		playlist = new HashMap<String, String>();
		time = System.currentTimeMillis()/1000;
		sinceLastMessage = new ArrayList<Integer>(Collections.nCopies(5, 0));
		livingProcs = new Boolean[5];
		for(int i = 0; i < 5; i++){
			livingProcs[i] = true;
		}
		this.transacting = -1;
	}
	
	public void run() {
		List<String> messages = new ArrayList<String>();
		int numYes = 0;
		while(alive){
			messages = network.getReceivedMsgs();
			for(String message : messages) {
				if(message.contains("VOTE_REQ")) {
					isTransactionOn = true;
					vote(message);
				}
				else if(message.contains("PRECOMMIT")) {
					preCommit(message);
				}
				else if(message.contains("COMMIT")) {
					commit();
				}
				else if(message.contains("ABORT")) {
					abort();
				}
				else if(message.contains("ACK")) {
					numYes++;
				}
				else if(message.contains("YES")) {
					numYes++;
				}
				else if(message.contains("NO")) {
					abort();
				}
				else if(message.contains("KEEPALIVE")) {
					keepalive(message);
				}
			}
			if(amCoord && numYes == 5) {
				//Waiting for votes
				if(stage == 1) {
					//TODO: need to vote still
					sendPreCommit();
					numYes = 0;
				}
				//Waiting for ACKs
				else if(stage == 2) {
					sendCommit();
					numYes = 0;
				}	
			}
			if(time < System.currentTimeMillis()/1000) {
				time++;
				broadcast(buildMessage("KEEPALIVE"));
				for(int i = 0; i < 5; i++) {
					sinceLastMessage.set(i, sinceLastMessage.get(i) + 1);
					if(sinceLastMessage.get(i) > 2) {
						livingProcs[i] = false;
					}
				}
			}
		}
		shutdown();
	}
	
	private void broadcast(String message) {
		for(int i=0; i < 5; i++) {
			if(livingProcs[i]){
				network.sendMsg(i, message);
			}
		}
	}
	
	private String buildMessage(String message) {
		return this.id + ":" + message;
	}
	
	private Integer getSender(String message) {
		return Integer.parseInt(message.split(":")[0]);
	}
	
	public void sendVoteReq(String command) {
		System.out.println(command);
		stage = 1;
		amCoord = true;
		broadcast(buildMessage("VOTE_REQ:" + command));
		
		// the controller knows of the state of the transaction 
		// from the coordinator's state...
		//
		// once the coordinator indicates TERMINATED, 
		// the controller can send the next instruction
		this.transacting = 1;
	}
	
	private void sendPreCommit() {
		stage = 2;
		broadcast(buildMessage("PRECOMMIT"));
	}
	
	private void sendCommit() {
		stage = 0;
		amCoord = false;
		broadcast(buildMessage("COMMIT"));
		// TODO: ensure that everyone receives commit before setting state to non-transaction
		this.transacting = -1;
	}
	
	private void sendAbort() {
		stage = 0;
		amCoord = false;
		broadcast(buildMessage("ABORT"));
	}
	
	private void vote(String message) {
		System.out.println(this.id + ":VOTING YES");
		Integer sender = getSender(message);
		command = message.split(":")[2];
		network.sendMsg(sender, buildMessage("YES"));
	}
	
	private void abort() {
		System.out.println(this.id + "ABORTING");
		command = "";
		isTransactionOn = false;
	}
	
	private void preCommit(String message) {
		System.out.println(this.id + ":ACK");
		Integer sender = getSender(message);
		network.sendMsg(sender, buildMessage("ACK"));
	}
	
	private void commit() {
		//EXECUTE COMMAND
		System.out.println(this.id + ":COMMITTING");
		command = "";
		isTransactionOn = false;
	}
	
	public int getTransactionState () {
		return transacting;
	}
	
	private void keepalive(String message) {
		//System.out.println(message);
		int sender = getSender(message);
		sinceLastMessage.set(sender,0);
		livingProcs[sender] = true;
	}
	
	public void shutdown() {
		alive = false;
		network.shutdown();
	}
	
	public void recover() {
		//do log stuff
	}
	
	public void log(String message) {
		//add it to a file
	}
}
