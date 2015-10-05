import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Controller
 * @author Julian
 *
 */
public class Controller {
	NetController masterNetwork;
	Config configFile;
	ArrayList<Process> idToProc; // i-th position corresponds to i-th process
	Process currLeader;
	Process lastKilled;
	ArrayList<String> instructions;
	
	public Controller(String instructionsPath) throws FileNotFoundException, IOException {
		idToProc = new ArrayList<Process>();
		for(int i = 0; i < 5; i++) {
			Config config = new Config("properties_p" + i + ".txt");
			idToProc.add(new Process(i, config));
		}
		
		// initialize arraylist of instructions to be executed
		this.instructions = new ArrayList<String>();
		
		// filepath assumes the following newline delimited file of commands:
		// [id]:[proposed command 1]
		// [id]:[proposed command 2]
		// ...
		// [id]:[proposed command n]
		//
		// where n is the number of commands
		BufferedReader reader = new BufferedReader(new FileReader(instructionsPath));
		String cmd = null;
		while ((cmd = reader.readLine()) != null) {
			this.instructions.add(cmd);
		}
	}
	
	public void executeInstructions() {
		for (String instr : instructions) {
			String[] parsedInstr = instr.split(":");
			Integer process = Integer.parseInt(parsedInstr[0]);
			String command = parsedInstr[1];
			
			Process workingProc = this.idToProc.get(process);
			
			workingProc.sendVoteReq(command);
			// Not sure if this is the correct approach
			// wait until the transaction finishes before going 
			// to the next one
			while (workingProc.getTransactionState() == 1);
		}
	}
	
	public void initiateAdd(String songName, String URL, Process p) {
		
	}
	
	public void initiateRemove(String songName, Process p) {
		
	}
	
	public void initiateEdit(String songName, String URL, Process p) {
		
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
	
	public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {
		Controller mainController = new Controller("instructions_test.txt");
		for(Process p : mainController.idToProc) {
			p.start();
		}
//		mainController.idToProc.get(0).sendVoteReq("COMMAND");
		mainController.executeInstructions();
		Thread.sleep(100 * 1000);
		for(Process p: mainController.idToProc) {
			p.shutdown();
		}
	}

	
	
}