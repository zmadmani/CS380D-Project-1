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
	private int numProcs;
	private Integer id;
	private Integer transCounter;
	private Boolean amCoord;
	private Integer currentCoord;
	private Boolean isTransactionOn;
	public Boolean alive;
	private Boolean amMessaging;
	private LinkedList<String[]> outgoingMessages;
	private Integer stopCountdown;
	private Integer killCountdown;
	private Integer stage; //Tells which stage of the transaction it is on currently
	private Boolean[] livingProcs;
	private HashMap<String, String> playlist;
	private ArrayList<Integer> sinceLastKeepAlive;
	private Long time;
	private String command;
	private String logName;
	private String upLogName;
	private BufferedWriter logWrite;
	private BufferedWriter upLogWrite;
	private Boolean inRecovery;
	private ArrayList<Integer> needStateResp;
	private Integer collectedState;
	private Boolean[] waitingOn;
	int numYes;
		
	public Process(Integer id, Config configFile, Integer desiredNumProcs) throws IOException {
		this.id = id;
		network = new NetController(configFile);
		this.numProcs = desiredNumProcs;
		alive = true;
		amCoord = false;
		currentCoord = -1;
		transCounter = 0;
		amMessaging = true;
		numYes = 0;
		stopCountdown = -1; //-1 means not counting
		killCountdown = -1; //-1 means not counting
		isTransactionOn = false;
		stage = 0; //0 --> nothing | 1 --> vote_req | 2 --> precommit //symmetric for both coord and participant
		playlist = new HashMap<String, String>();
		time = System.currentTimeMillis()/1000;
		collectedState = 0; // 1 --> undecided | 2 --> precommit
		sinceLastKeepAlive = new ArrayList<Integer>(Collections.nCopies(this.numProcs, 0));
		outgoingMessages = new LinkedList<String[]>();
		needStateResp = new ArrayList<Integer>();
		livingProcs = new Boolean[numProcs];
		waitingOn = new Boolean[numProcs];
		for(int i = 0; i < numProcs; i++){
			livingProcs[i] = true;
			waitingOn[i] = true;
		}
		logName = "log_p" + this.id + ".txt";
		upLogName = "upLog_p" + this.id + ".txt";
		inRecovery = false;
		logWrite = new BufferedWriter(new FileWriter(logName, true));
		upLogWrite = new BufferedWriter(new FileWriter(upLogName, true));
		File f = new File(logName);
		if (f.exists()) {
			recover();
		}
	}
	
	public void run() {
		List<String> messages = new ArrayList<String>();
		while(alive){
			messages = network.getReceivedMsgs();
			for(String message : messages) {
				if(!message.contains("KEEPALIVE") && !message.contains("STATE_REQ") && !message.contains("STATE_RESP")) {
					Integer sender = getSender(message);
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
				else if(message.contains("STATE_REQ_COORD") && !inRecovery) {
					try {
						newCoord(message);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.contains("STATE_RESP_COORD") && !inRecovery && amWaiting()) {
					try {
						gotHelpCoord(message);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.contains("STATE_REQ:") && !inRecovery) {
					try {
						helpOthers(message);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.contains("STATE_RESP:") && inRecovery) {
					try {
						gotHelp(message);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.contains("URELECTED") && !inRecovery && !amCoord) {
					try {
						amElected(message);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.endsWith("PRECOMMIT") && isTransactionOn && !inRecovery) {
					try {
						preCommit(message);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.endsWith("DOCOMMIT") && isTransactionOn && !inRecovery) {
					try {
						commit(message);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.endsWith("ABORT") && isTransactionOn && !inRecovery) {
					try {
						abort();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else if(message.endsWith("ACK") && isTransactionOn && !inRecovery) {
					numYes++;
				}
				else if(message.endsWith("YES") && isTransactionOn && !inRecovery) {
					numYes++;
				}
				else if(message.endsWith("NO") && isTransactionOn && !inRecovery) {
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
				if(stage == 1 && numYes == numProcs) {
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
				else if(stage == 2 && numYes == numLivingProcesses()) {
					try {
						sendCommit();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					numYes = 0;
				}
			}
			if(amCoord) {
				if(stage == 1 && numLivingProcesses() < numProcs) {
					try {
						sendAbort();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			if(time < System.currentTimeMillis()/1000 && !inRecovery && livingProcs[id]) {
				time++;
				if(false) {
					System.out.println(this.id + ":" + Arrays.toString(livingProcs));
				}
				for(int i=0; i < numProcs; i++) {
					if(livingProcs[i]){
						network.sendMsg(i, buildMessage("KEEPALIVE"));
					}
				}
				for(int i = 0; i < numProcs; i++) {
					sinceLastKeepAlive.set(i, sinceLastKeepAlive.get(i) + 1);
					if(sinceLastKeepAlive.get(i) > 3 && livingProcs[i] == true) {
						livingProcs[i] = false;
						//System.out.println(this.id + ":TIMING OUT PROC " + i + "| COORD IS " + currentCoord);
						if(currentCoord == i) {
							try {
								initiateElection();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}
				// log up set
				try {
					upLogWrite.write(Arrays.toString(livingProcs));
					upLogWrite.newLine();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					//e1.printStackTrace();
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
	
	private void broadcast(String message) throws IOException {
		for(int i=0; i < numProcs; i++) {
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
	
	private Boolean shouldMessage() throws IOException {
		Boolean return_stmt = false;
		if(stopCountdown == -1 && killCountdown == -1) {
			return true;
		}
		else if(amMessaging == false) {
			return false;
		}
		
		if(stopCountdown > 0) {
			stopCountdown--;
			return_stmt = true;
		}
		else if(stopCountdown == 0) {
			amMessaging = false;
			return_stmt = false;
		}
		
		if(killCountdown > 0) {
			//System.out.println(this.id + ":KILLCOUNTDOWN: " + killCountdown);
			killCountdown--;
			return_stmt = true;
		}
		else if(killCountdown == 0 && alive) {
			shutdown();
			return_stmt = false;
		}
		
		return return_stmt;
	}
	
	private String buildMessage(String message) {
		return this.id + ":" + message;
	}
	
	private Integer getSender(String message) {
		return Integer.parseInt(message.split(":")[0]);
	}
	
	public void sendVoteReq(String command, int transNum) throws IOException {
		// the controller knows of the state of the transaction 
		// from the coordinator's state...
		//
		// once the coordinator indicates TERMINATED, 
		// the controller can send the next instruction
		this.isTransactionOn = true;
		System.out.println("------------");
		System.out.println(this.id + ":" + command);
		stage = 1;
		for(int i = 0; i < numProcs; i++) {
			waitingOn[i] = true;
		}
		amCoord = true;
		currentCoord = this.id;
		transCounter = transNum;
		numYes = 0;
		log(transCounter + ";VOTE_REQ:" + command);
		broadcast(buildMessage(transCounter + ":VOTE_REQ:" + command));
	}
	
	private void sendPreCommit() throws IOException {
		stage = 2;
		//log("PRECOMMIT");
		for(int i = 0; i < numProcs; i++) {
			waitingOn[i] = true;
		}
		broadcast(buildMessage("PRECOMMIT"));
	}
	
	private void sendCommit() throws IOException {
		stage = 0;
//		isTransactionOn = false;
//		log("COMMIT:" + command);
//		commit("");
		broadcast(buildMessage("DOCOMMIT"));
		clearWaitingOn();
	}
	
	private void sendAbort() throws IOException {
		//stage = 0;
		//amCoord = false;
		//abort();
		broadcast(buildMessage("ABORT"));
		clearWaitingOn();
	}
	
	private void newCoord(String message) throws IOException {
		System.out.println(this.id + ":GOT STATE_REQ_COORD from " + getSender(message) + " while currentCoord = " + currentCoord);
		int transNum = Integer.parseInt(message.split(":")[2]);
		int sender = getSender(message);
		if(currentCoord < sender) {
			for(int i = 0; i < sender; i++) {
				livingProcs[i] = false;
			}
			currentCoord = sender;
		}
		else if(currentCoord == sender) {
			currentCoord = sender;
		}
		else {
			return;
		}
		isTransactionOn = true;
		BufferedReader logRead = new BufferedReader(new FileReader(logName));
		String response = logRead.readLine();
		String answer = "";
		for(int i = 0; i < transNum; i++) {
			if(response != null && Integer.parseInt(response.split(";")[0]) != transNum) {
				response = logRead.readLine();
			}
		}
		
		if(response == null) {
			transCounter = transNum;
		}
		
		if(response == null || response.contains(";YES")) {
			answer = "STATE_RESP_COORD:UNCERTAIN:" + command;
		}
		if(response != null) {
			if(response.contains("PRECOMMIT")) {
				answer = "STATE_RESP_COORD:PRECOMMIT:" + command;
			}
			if(response.contains(";COMMIT")) {
				String[] responseArr = response.split(";");
				answer = "STATE_RESP_COORD:COMMIT:" + responseArr[responseArr.length-1].split(":")[1];
				isTransactionOn = false;
			}
			if(response.contains("ABORT")) {
				answer = "STATE_RESP_COORD:ABORT:" + command;
				isTransactionOn = false;
			}
		}
		System.out.println(this.id + ":HELPING COORD: " + answer);
		//System.out.println("SENDING STATE TO " + sender + " with message: " + answer);
		network.sendMsg(sender, buildMessage(answer));
		logRead.close();
	}
	
	private void gotHelpCoord(String message) throws IOException {
		//System.out.println(message);
		Integer sender = getSender(message);
		if(message.contains("ABORT")) {
			sendAbort();
		}
		else if(message.contains(":COMMIT")) {
			sendCommit();
		}
		else {
			waitingOn[sender] = false;
			if(message.contains("UNCERTAIN")) {
				collectedState = Math.max(collectedState,1);
			}
			else if(message.contains("PRECOMMIT")) {
				collectedState = 2;
			}
			if(!amWaiting()) {
				if(collectedState == 1) {
					sendAbort();
				}
				else if(collectedState == 2) {
					sendPreCommit();
				}
				collectedState = 0;
			}
		}
	}
	
	private void helpOthers(String message) throws IOException {
		int transNum = Integer.parseInt(message.split(":")[2]);
		//System.out.println(this.id + ":" + transCounter + ":" + isTransactionOn);
		if(transCounter == transNum && isTransactionOn) {
			needStateResp.add(getSender(message));
		}
		else {
			BufferedReader logRead = new BufferedReader(new FileReader(logName));
			String response = logRead.readLine();
			String answer = "";
			for(int i = 0; i <= transNum; i++) {
				if(response != null && Integer.parseInt(response.split(";")[0]) != transNum) {
					response = logRead.readLine();
				}
			}

			if(response == null || response.contains("YES")) {
				answer = "STATE_RESP:UNCERTAIN:" + command;
			}
			if(response != null) {
				if(response.contains("PRECOMMIT")) {
					answer = "STATE_RESP:PRECOMMIT:" + command;
				}
				if(response.contains(";COMMIT")) {
					String[] responseArr = response.split(";");
					answer = "STATE_RESP:COMMIT:" + responseArr[responseArr.length-1].split(":")[1];
				}
				if(response.contains("ABORT")){
					answer = "STATE_RESP:ABORT:";
				}
			}
			Integer sender = getSender(message);
			//System.out.println(this.id + ":RESPONSE:" + answer);
			network.sendMsg(sender, buildMessage(answer));
			logRead.close();
		}
	}
	
	private void gotHelp(String message) throws IOException {		
		String decision = message.split(":")[2];
		//System.out.println("GOTHELP: " + message);
		inRecovery = false;
		if(decision.equals("COMMIT")) {
			command = message.split(":")[3];
			commit("");
		}
		else {
			abort();
		}
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
		transCounter = Integer.parseInt(message.split(":")[1]);
		Integer sender = getSender(message);
		currentCoord = sender;
		if(currentCoord != this.id) {
			amCoord = false;
		}
		if(!amCoord) {
			log(Integer.toString(transCounter));
			stage = 1;
		}
		Integer vote = 1;
		waitingOn[currentCoord] = true;
		command = message.split(":")[3];
				
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
				abort();
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
		stage = 0;
		currentCoord = -1;
		log("ABORT");
		for(Integer i: needStateResp) {
			network.sendMsg(i, buildMessage("STATE_RESP:ABORT:" + command));
		}
		needStateResp.clear();
		clearWaitingOn();
		System.out.println(this.id + ":ABORTING");
	}
	
	private void preCommit(String message) throws IOException {
		stage = 2;
		Integer sender = getSender(message);
		if(shouldMessage()) {
			System.out.println(this.id + ":ACK");
			log("PRECOMMIT");
			network.sendMsg(sender, buildMessage("ACK"));
			waitingOn[currentCoord] = true;
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
			String[] args = command.substring(numProcs,command.length()-1).split(",");
			playlist.replace(args[0], args[2]);
		}
		System.out.println(this.id + ":COMMITTING");
		isTransactionOn = false;
		log("COMMIT:" + command);
		for(Integer i: needStateResp) {
			network.sendMsg(i, buildMessage("STATE_RESP:COMMIT:" + command));
		}
		currentCoord = -1;
		stage = 0;
		needStateResp.clear();
		clearWaitingOn();
		command = "";
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
	
	private void initiateElection() throws IOException {
		System.out.println(this.id + ":STARTING ELECTION");
		int i = 0;
		while(i < numProcs-1 && livingProcs[i] == false) {
			i++;
		}
		currentCoord = i;
		if(shouldMessage()) {
			network.sendMsg(i, buildMessage("URELECTED:" + transCounter));
		}
		else {
			String[] temp = {buildMessage("URELECTED:" + transCounter),Integer.toString(i)};
			outgoingMessages.add(temp);
		}
	}
	
	private void amElected(String message) throws IOException {
		amCoord = true;
		transCounter = Integer.parseInt(message.split(":")[2]);
		this.isTransactionOn = true;
		System.out.println(this.id + ":ELECTED COORDINATOR");
		stage = 0;
		numYes = 0;
		currentCoord = this.id;
		broadcast(buildMessage("STATE_REQ_COORD:" + transCounter));
		for(int i = 0; i < numProcs; i++) {
			if(livingProcs[i]) {
				waitingOn[i] = true;
			}
		}
		//Check logs for commit and abort
		BufferedReader logRead = new BufferedReader(new FileReader(logName));
		String response = logRead.readLine();
		for(int i = 0; i <= transCounter; i++) {
			if(response != null && Integer.parseInt(response.split(";")[0]) != transCounter) {
				response = logRead.readLine();
			}
		}
		if(response.contains(";COMMIT")) {
			clearWaitingOn();
			isTransactionOn = false;
			sendCommit();
		}
		else if(response.contains(";ABORT")) {
			clearWaitingOn();
			isTransactionOn = false;
			sendAbort();
		}
		logRead.close();
	}
	
	public void partialMessage(Integer numMessages) {
		stopCountdown = numMessages;
	}
	
	public void partialKill(Integer numMessages) {
		killCountdown = numMessages;
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
	
	private void clearWaitingOn() {
		for(int i = 0; i < numProcs; i++) {
			waitingOn[i] = false;
		}
	}
	
	private Boolean[] intersect(Boolean[] arr1, Boolean[] arr2) {
		Boolean[] result = new Boolean[arr1.length];
		for(int i = 0; i < arr1.length; i++) {
			result[i] = arr1[i] & arr2[i];
		}
		return result;
	}
	
	private boolean amWaiting() {
		boolean resp = false;
		for(int i = 0; i < numProcs; i++) {
			if(waitingOn[i] == true && livingProcs[i] == true){
				resp = true;
			}
		}
		return resp;
	}
	
	public void shutdown() throws IOException {
		if(alive) {
			alive = false;
			network.shutdown();
			logWrite.flush();
			logWrite.close();
			upLogWrite.flush();
			upLogWrite.close();
			System.out.println("die " + this.id);
//			BufferedReader logRead = new BufferedReader(new FileReader(logName));
//			String line;
//			while((line = logRead.readLine()) != null) {
//				System.out.println(line);
//			}
		}
	}
	
	public void recover() throws IOException {
		BufferedReader logRead = new BufferedReader(new FileReader(logName));
		BufferedReader upLogRead = new BufferedReader(new FileReader(upLogName));
		inRecovery = true;
		Boolean waiting = false;
		String line;
		Boolean amCurrentCoord = false;
		while((line = logRead.readLine()) != null && inRecovery) {
			if(line.contains(";COMMIT")) {
				String[] lineArray = line.split(";");
				//System.out.println(Arrays.toString(lineArray));
				command = lineArray[lineArray.length-1].split(":")[1];
				transCounter = Integer.parseInt(lineArray[0]);
				//System.out.println(command);
				commit("");
			}
			else if(line.contains("ABORT")) {
				String[] lineArray = line.split(";");
				transCounter = Integer.parseInt(lineArray[0]);
				abort();
			}
			else {
				if(line.endsWith("YES;") || line.contains("PRECOMMIT")) {
					String[] lineArray = line.split(";");
					System.out.println(Arrays.toString(lineArray));
					transCounter = Integer.parseInt(lineArray[0]);
					broadcast(buildMessage("STATE_REQ:" + transCounter));
					for(int i = 0; i < numProcs; i++) {
						waitingOn[i] = true;
					}
					waiting = true;
				}
				else if(line.contains("VOTE_REQ")) {
					inRecovery = false;
					transCounter = Integer.parseInt(line.split(";")[0]);
					log("");
					System.out.println(this.id + ":ABORTING");
				}
				else{
					inRecovery = false;
					System.out.println(this.id + ":ABORTING");
					if(line.isEmpty()) {
						log("-1");
					}
					else {
						log("");
					}
				}
			}
		}
		if(!waiting) {
			inRecovery = false;
		}
		logRead.close();
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
