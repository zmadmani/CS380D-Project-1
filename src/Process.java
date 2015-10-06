import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
	private Boolean amMessaging;
	private LinkedList<String[]> outgoingMessages;
	private Integer stopCountdown;
	private Integer stage; //Tells which stage of the transaction it is on currently
	private Boolean[] livingProcs;
	private HashMap<String, String> playlist;
	private ArrayList<Integer> sinceLastMessage;
	private Long time;
	private String command;
	
	private Lock lock;
	
	public Process(Integer id, Config configFile) {
		this.id = id;
		network = new NetController(configFile);
		alive = true;
		amCoord = false;
		amMessaging = true;
		stopCountdown = -1; //-1 means not counting
		isTransactionOn = false;
		stage = 0; //0 --> nothing | 1 --> vote_req | 2 --> precommit
		playlist = new HashMap<String, String>();
		time = System.currentTimeMillis()/1000;
		sinceLastMessage = new ArrayList<Integer>(Collections.nCopies(5, 0));
		outgoingMessages = new LinkedList<String[]>();
		livingProcs = new Boolean[5];
		for(int i = 0; i < 5; i++){
			livingProcs[i] = true;
		}
		// set state to non-transacting at init
		// lock to ensure that COMMIT finishes before changing state
		this.lock = new ReentrantLock();
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
				else if(message.contains("DOCOMMIT")) {
					commit(message);
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
				else if(message.contains("NO") && isTransactionOn) {
					sendAbort();
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
				for(int i=0; i < 5; i++) {
					if(livingProcs[i]){
						network.sendMsg(i, buildMessage("KEEPALIVE"));
					}
				}
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
				if(shouldMessage()) {
					network.sendMsg(i, message);
				}
				else {
					String[] temp = {message,Integer.toString(i)};
					outgoingMessages.add(temp);
				}
			}
		}
	}
	
	private Boolean shouldMessage() {
		if(this.id == 1) {
			System.out.println("IN SHOULD MESSAGE: stopCountdown=" + stopCountdown + "/amMessaging=" + amMessaging);
		}
		
		if(stopCountdown == -1) {
			return true;
		}
		else if(amMessaging == false) {
			return false;
		}
		else if(stopCountdown > 0) {
			stopCountdown--;
			return true;
		}
		else if(stopCountdown == 0) {
			amMessaging = false;
			return false;
		}
		//never be reached
		return true;
	}
	
	private String buildMessage(String message) {
		return this.id + ":" + message;
	}
	
	private Integer getSender(String message) {
		return Integer.parseInt(message.split(":")[0]);
	}
	
	public void sendVoteReq(String command) {
		// the controller knows of the state of the transaction 
		// from the coordinator's state...
		//
		// once the coordinator indicates TERMINATED, 
		// the controller can send the next instruction
		this.isTransactionOn = true;
		
		System.out.println(command);
		stage = 1;
		amCoord = true;
		broadcast(buildMessage("VOTE_REQ:" + command));
	}
	
	private void sendPreCommit() {
		stage = 2;
		broadcast(buildMessage("PRECOMMIT"));
	}
	
	private void sendCommit() {
		stage = 0;
		amCoord = false;
		broadcast(buildMessage("DOCOMMIT"));
	}
	
	private void sendAbort() {
		stage = 0;
		amCoord = false;
		isTransactionOn = false;
		broadcast(buildMessage("ABORT"));
	}
	
	private void vote(String message) {
		Integer vote = 1;
		Integer sender = getSender(message);
		command = message.split(":")[2];
				
		if(command.startsWith("ADD")) {
			vote = 1;
		}
		else if(command.startsWith("REMOVE")) {
			if(playlist.get(command.split("[(]")[1].split("[)]")[0]) == null) {
				vote = 0;
			}
		}
		else if(command.startsWith("EDIT")) {
			if(playlist.get(command.split("[(]")[1].split(",")[0]) == null) {
				vote = 0;
			}
		}
		
		if(vote == 1) {
			if(shouldMessage()) {
				System.out.println(this.id + ":VOTING YES");
				network.sendMsg(sender, buildMessage("YES"));
			}
			else {
				String[] temp = {message,Integer.toString(sender)};
				outgoingMessages.add(temp);
			}
		}
		else {
			if(shouldMessage()) {
				System.out.println(this.id + ":VOTING NO");
				network.sendMsg(sender, buildMessage("NO"));
			}
			else {
				String[] temp = {message,Integer.toString(sender)};
				outgoingMessages.add(temp);
			}
		}
	}
	
	private void abort() {
		System.out.println(this.id + ":ABORTING");
		command = "";
		isTransactionOn = false;
	}
	
	private void preCommit(String message) {
		Integer sender = getSender(message);
		if(shouldMessage()) {
			System.out.println(this.id + ":ACK");
			network.sendMsg(sender, buildMessage("ACK"));
		}
		else {
			String[] temp = {message,Integer.toString(sender)};
			outgoingMessages.add(temp);
		}
	}
	
	private void commit(String message) {
		//EXECUTE COMMAND		
		if(command.startsWith("ADD")) {
			String[] args = command.substring(4,command.length()-1).split(",");
			playlist.put(args[0], args[1]);
		}
		else if(command.startsWith("REMOVE")) {
			String arg= command.substring(7,command.length()-1);
			playlist.remove(arg);
		}
		else if(command.startsWith("EDIT")) {
			String[] args = command.substring(5,command.length()-1).split(",");
			playlist.replace(args[0], args[2]);
		}
		System.out.println(this.id + ":COMMITTING");
		command = "";
		isTransactionOn = false;
	}
	
	public Boolean getTransactionState () {
		return this.isTransactionOn;
	}
	
	private void keepalive(String message) {
		//System.out.println(message);
		int sender = getSender(message);
		sinceLastMessage.set(sender,0);
		livingProcs[sender] = true;
	}
	
	public void partialMessage(Integer numMessages) {
		stopCountdown = numMessages;
	}
	
	public void resumeMessages() {
		amMessaging = true;
		stopCountdown = -1;
		Integer size = outgoingMessages.size();
		for(int i = 0; i < size; i++) {
			String[] message = outgoingMessages.pop();
			network.sendMsg(Integer.parseInt(message[1]), message[0]);
		}
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
	
	public void displayPlaylist() {
		System.out.println(playlist);
	}
}
