import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Process
 * @author Zain Admani
 *
 */
public class Process extends Thread {
	public static Integer TIMEOUT = 3;
	
	private NetController network;
	private Integer id;
	private Integer transCounter;
	private Boolean amCoord;
	private Integer currentCoord;
	private Boolean isTransactionOn;
	private Boolean alive;
	private Boolean amMessaging;
	private LinkedList<String[]> outgoingMessages;
	private Integer stopCountdown;
	private Integer stage; //Tells which stage of the transaction it is on currently
	private Boolean[] livingProcs;
	private Boolean[] timedOut;
	private ArrayList<Integer> sinceLastMessage;
	private Boolean waiting;
	private HashMap<String, String> playlist;
	private ArrayList<Integer> sinceLastKeepAlive;
	private Long time;
	private String command;
	private String logName;
	private BufferedWriter logWrite;
	private Boolean inRecovery;
	private ArrayList<Integer> needStateResp;
		
	public Process(Integer id, Config configFile) throws IOException {
		this.id = id;
		network = new NetController(configFile);
		alive = true;
		amCoord = false;
		currentCoord = -1;
		transCounter = 0;
		amMessaging = true;
		stopCountdown = -1; //-1 means not counting
		isTransactionOn = false;
		stage = 0; //0 --> nothing | 1 --> vote_req | 2 --> precommit
		playlist = new HashMap<String, String>();
		time = System.currentTimeMillis()/1000;
		timedOut = new Boolean[5];
		waiting = false;
		sinceLastMessage = new ArrayList<Integer>(Collections.nCopies(5, 0));
		sinceLastKeepAlive = new ArrayList<Integer>(Collections.nCopies(5, 0));
		outgoingMessages = new LinkedList<String[]>();
		needStateResp = new ArrayList<Integer>();
		livingProcs = new Boolean[5];
		for(int i = 0; i < 5; i++){
			livingProcs[i] = true;
			timedOut[i] = false;
		}
		logName = "log_p" + this.id + ".txt";
		inRecovery = false;
		File f = new File(logName);
		if (f.exists()) {
			recover();
		}
		logWrite = new BufferedWriter(new FileWriter(logName, true));
	}
	
	public void run() {
		List<String> messages = new ArrayList<String>();
		int numYes = 0;
		while(alive){
			messages = network.getReceivedMsgs();
			for(String message : messages) {
				if(!message.contains("KEEPALIVE") && !message.contains("STATE_REQ") && !message.contains("STATE_RESP")) {
					Integer sender = getSender(message);
					sinceLastMessage.set(sender,0);
				}
				
				if(message.contains("VOTE_REQ") && !inRecovery) {
					isTransactionOn = true;
					try {
						vote(message);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.contains("STATE_REQ") && !inRecovery) {
					try {
						helpOthers(message);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.contains("STATE_RESP") && inRecovery) {
					try {
						gotHelp(message);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.contains("PRECOMMIT") && isTransactionOn && !inRecovery) {
					preCommit(message);
				}
				else if(message.contains("DOCOMMIT") && isTransactionOn && !inRecovery) {
					try {
						commit(message);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.contains("ABORT") && isTransactionOn && !inRecovery) {
					try {
						abort();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.contains("ACK") && isTransactionOn && !inRecovery) {
					numYes++;
				}
				else if(message.contains("YES") && isTransactionOn && !inRecovery) {
					numYes++;
				}
				else if(message.contains("NO") && isTransactionOn && !inRecovery) {
					try {
						sendAbort();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.contains("KEEPALIVE")) {
					keepalive(message);
				}
			}
			if(amCoord) {
				//Waiting for votes
				if(stage == 1 && numYes == 5) {
					//TODO: need to vote still
					try {
						sendPreCommit();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					numYes = 0;
				}
				//Waiting for ACKs
				else if(stage == 2 && numYes >= numNotTimedOutProcesses()) {
					try {
						sendCommit();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					numYes = 0;
				}
			}
			if(waiting) {
				if(amCoord) {
					for(int i = 0; i < 5; i++) {
						if(livingProcs[i]) {
							if(sinceLastMessage.get(i) > TIMEOUT) {
								timedOut[i] = true;
								System.out.println(this.id + ":PROCESS " + i + " TIMEDOUT WITH VALUE: " + sinceLastMessage.get(i));
							}
						}
					}
				}
				else {
					if(sinceLastMessage.get(currentCoord) > TIMEOUT) {
						timedOut[currentCoord] = true;
						//INITIATE ELECTION
					}
				}
			}
			if(amCoord) {
				if(stage == 1 && numNotTimedOutProcesses() < 5) {
					try {
						sendAbort();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			if(time < System.currentTimeMillis()/1000 && !inRecovery) {
				time++;
				for(int i=0; i < 5; i++) {
					if(livingProcs[i]){
						network.sendMsg(i, buildMessage("KEEPALIVE"));
					}
				}
				for(int i = 0; i < 5; i++) {
					sinceLastKeepAlive.set(i, sinceLastKeepAlive.get(i) + 1);
					sinceLastMessage.set(i, sinceLastMessage.get(i) + 1);
					if(sinceLastKeepAlive.get(i) > 2) {
						livingProcs[i] = false;
					}
				}
			}
		}
		try {
			shutdown();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
	
	public void sendVoteReq(String command) throws IOException {
		// the controller knows of the state of the transaction 
		// from the coordinator's state...
		//
		// once the coordinator indicates TERMINATED, 
		// the controller can send the next instruction
		this.isTransactionOn = true;
		clearTimeouts();
		System.out.println("------------");
		System.out.println(this.id + ":" + command);
		stage = 1;
		amCoord = true;
		currentCoord = this.id;
		log(transCounter + ";VOTE_REQ:" + command);
		broadcast(buildMessage("VOTE_REQ:" + command));
		waiting = true;
	}
	
	private void sendPreCommit() throws IOException {
		stage = 2;
		//log("PRECOMMIT");
		broadcast(buildMessage("PRECOMMIT"));
		waiting = true;
	}
	
	private void sendCommit() throws IOException {
		stage = 0;
		isTransactionOn = false;
		log("COMMIT:" + command);
		commit("");
		amCoord = false;
		broadcast(buildMessage("DOCOMMIT"));
		waiting = false;
	}
	
	private void sendAbort() throws IOException {
		stage = 0;
		amCoord = false;
		abort();
		broadcast(buildMessage("ABORT"));
		waiting = false;
	}
	
	private void helpOthers(String message) throws IOException {
		int transNum = Integer.parseInt(message.split(":")[2]);
		if(transCounter == transNum && isTransactionOn) {
			needStateResp.add(getSender(message));
		}
		else {
			BufferedReader logRead = new BufferedReader(new FileReader(logName));
			String response = "";
			for(int i = 0; i < transNum; i++) {
				response = logRead.readLine();
			}
			if(response.contains("COMMIT")) {
				response = "STATE_RESP:COMMIT:" + command;
			}
			else {
				response = "STATE_RESP:ABORT:" + command;
			}
			Integer sender = getSender(message);
			network.sendMsg(sender, buildMessage(response));
		}
	}
	
	private void gotHelp(String message) throws IOException {
		String decision = message.split(":")[2];
		command = message.split(":")[3];
		System.out.println("GOTHELP: " + command);
		inRecovery = false;
		if(decision.contains("COMMIT")) {
			commit("");
		}
		else {
			abort();
		}
	}
	
	private void clearTimeouts() {
		for(int i = 0; i < 5; i++) {
			sinceLastMessage.set(i, 0);
		}
	}
	
	private Integer numNotTimedOutProcesses() {
		Integer num_nonTimeout = 0;
		for(Boolean timeout: timedOut) {
			if(!timeout) {
				num_nonTimeout++;
			}
		}
		return num_nonTimeout;
	}
	
	private Integer numLivingProcesses() {
		Integer num_living = 0;
		for(Boolean living: livingProcs) {
			if(living) {
				num_living++;
			}
		}
		return num_living;
	}
	
	private void vote(String message) throws IOException {
		if(!amCoord) {
			log(Integer.toString(transCounter));
		}
		clearTimeouts();
		transCounter++;
		Integer vote = 1;
		Integer sender = getSender(message);
		currentCoord = sender;
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
				log("YES");
				network.sendMsg(sender, buildMessage("YES"));
				waiting = true;
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
				log("ABORT");
				waiting = false;
			}
			else {
				String[] temp = {message,Integer.toString(sender)};
				outgoingMessages.add(temp);
			}
		}		
	}
	
	private void abort() throws IOException {
		command = "";
		isTransactionOn = false;
		waiting = false;
		for(int i = 0; i < 5; i++) {
			timedOut[i] = false;
		}
		log("ABORT");
		for(Integer i: needStateResp) {
			network.sendMsg(i, buildMessage("STATE_RESP:ABORT:" + command));
		}
		needStateResp.clear();
		System.out.println(this.id + ":ABORTING");
	}
	
	private void preCommit(String message) {
		Integer sender = getSender(message);
		if(shouldMessage()) {
			System.out.println(this.id + ":ACK");
			network.sendMsg(sender, buildMessage("ACK"));
			waiting = true;
		}
		else {
			String[] temp = {message,Integer.toString(sender)};
			outgoingMessages.add(temp);
		}
	}
	
	private void commit(String message) throws IOException {
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
		isTransactionOn = false;
		System.out.println(this.id + ":COMMITTING");
		if(!amCoord) {
			log("COMMIT:" + command);
		}
		for(Integer i: needStateResp) {
			network.sendMsg(i, buildMessage("STATE_RESP:COMMIT:" + command));
		}
		needStateResp.clear();
		
		command = "";
		waiting = false;
		for(int i = 0; i < 5; i++) {
			timedOut[i] = false;
		}
	}
	
	public Boolean getTransactionState () {
		return this.isTransactionOn;
	}
	
	private void keepalive(String message) {
		//System.out.println(message);
		int sender = getSender(message);
		sinceLastKeepAlive.set(sender,0);
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
	
	public void shutdown() throws IOException {
		if(alive) {
			alive = false;
			network.shutdown();
			logWrite.flush();
			logWrite.close();
			BufferedReader logRead = new BufferedReader(new FileReader(logName));
			String line;
//			while((line = logRead.readLine()) != null) {
//				System.out.println(line);
//			}
		}
	}
	
	public void recover() throws IOException {
		BufferedReader logRead = new BufferedReader(new FileReader(logName));
		inRecovery = true;
		Boolean waiting = false;
		String line;
		Boolean amCurrentCoord = false;
		while((line = logRead.readLine()) != null) {
			if(line.startsWith("VOTE_REQ")) {
				amCurrentCoord = true;
			}
			if(line.contains("COMMIT")) {
				String[] lineArray = line.split(";");
				//System.out.println(Arrays.toString(lineArray));
				command = lineArray[lineArray.length-1].split(":")[1];
				Integer transNum = Integer.parseInt(lineArray[0]);
				transCounter++;
				//System.out.println(command);
				commit("");
			}
			else if(line.contains("ABORT")) {
				transCounter++;
				//DO NOTHING
			}
			else {
				if(line.endsWith("YES;")) {
					String[] lineArray = line.split(";");
					System.out.println(Arrays.toString(lineArray));
					Integer transNum = Integer.parseInt(lineArray[0]);
					transCounter++;
					broadcast(buildMessage("STATE_REQ:" + transNum));
					waiting = true;
				}
				else if(line.startsWith("VOTE_REQ")) {
					//I WUZ COORD
				}
			}
		}
		if(!waiting) {
			inRecovery = false;
		}
	}
	
	public void log(String message) throws IOException {
		if(!inRecovery) {
			logWrite.write(message + ";");
			if(isTransactionOn == false) {
				logWrite.newLine();
			}
			logWrite.flush();
		}
	}
	
	public void displayPlaylist() {
		System.out.println(playlist);
	}
	
	public void printLog() {
		//finish
	}
}
