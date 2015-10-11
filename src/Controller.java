import java.io.BufferedReader;
import java.io.Console;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Controller
 * @author Julian
 *
 */
public class Controller {
	public static Integer numProcs;
	
	NetController masterNetwork;
	Config configFile;
	ArrayList<Process> idToProc; // i-th position corresponds to i-th process
	Integer currLeaderIndex;
	Process currLeader;
	Integer lastKilledIndex;
	Process lastKilled;
	ArrayList<String> instructions;
	Boolean[] living;
	
	public Controller(String instructionsPath, int desirednumProcs) throws FileNotFoundException, IOException  {
		idToProc = new ArrayList<Process>();
		numProcs = desirednumProcs;
		for(int i = 0; i < numProcs; i++) {
			Config config = new Config("properties_p" + i + ".txt");
			idToProc.add(new Process(i, config, numProcs));
		}
		
		this.living = new Boolean[numProcs];
		Arrays.fill(this.living, Boolean.TRUE);
		
		
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
	
	public void executeInstructions() throws InterruptedException, IOException {
		int i = 0;
		for (String instr : instructions) {
			String[] parsedInstr = instr.split(":");
			String command = parsedInstr[0];
			
			Process workingProc = this.idToProc.get(0);
			
			this.currLeaderIndex = 0;
			this.currLeader = workingProc;
						
			if(i == 0) {
				int PROC = 1;
				int numMsg = 8;
				kill(PROC);
				//partialKill(1,2);
				workingProc.sendVoteReq(command,i);
				Thread.sleep(10 * 1000);
				revive(PROC);
				//revive(1);
				Thread.sleep(4 * 1000);
			}
			else {
				workingProc.sendVoteReq(command,i);	
			}
			
			int readyProcesses = 0;
			Long resume = System.currentTimeMillis() + 10000;
			while(readyProcesses < 5) {
				readyProcesses = 0;
				for(Process p: idToProc) {
					if(p.getTransactionState() == false) {
						readyProcesses++;
					}
				}
			}
			Thread.sleep(500);
			i++;
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
	
	public void kill(Integer id) throws IOException {
		System.out.println("killing " + id);
		idToProc.get(id).shutdown();
		this.lastKilledIndex = id;
		this.lastKilled = idToProc.get(id);
		this.living[id] = false;
	}
	
	public void killAll() throws IOException {
		System.out.println("killing all");
		for (Process p : idToProc) {
			p.shutdown();
		}
		this.lastKilledIndex = idToProc.size() - 1;
		this.lastKilled = idToProc.get(idToProc.size() - 1);
		Arrays.fill(this.living, Boolean.FALSE);
	}
	
	public void killLeader() throws IOException {
		System.out.println("kill leader");
		currLeader.shutdown();
		this.lastKilledIndex = currLeaderIndex;
		this.lastKilled = currLeader;
		this.living[currLeaderIndex] = false;		
	}
	
	public void revive(Integer id) throws FileNotFoundException, IOException {
		if(idToProc.get(id).alive == null || idToProc.get(id).alive == false) {
			System.out.println("Revive " + id);
			Config config = new Config("properties_p" + id + ".txt");
			idToProc.set(id, new Process(id,config, numProcs));
			idToProc.get(id).start();
		}
	}
	
	public void reviveLast() throws FileNotFoundException, IOException {
		Config config = new Config("properties_p" + lastKilledIndex + ".txt");
		idToProc.set(lastKilledIndex, new Process(lastKilledIndex, config, numProcs));
		idToProc.get(lastKilledIndex).start();
	}
	
	public void reviveAll() throws FileNotFoundException, IOException {
		for (int i = 0; i < idToProc.size(); i++) {
			Config config = new Config("properties_p" + i+ ".txt");
			idToProc.set(i, new Process(i, config, numProcs));
			idToProc.get(i).start();
		}
	}
	
	public void partialMessage(Integer id, Integer numMsgs) {
		idToProc.get(id).partialMessage(numMsgs);
	}
	
	public void resumeMessages(Integer id) {
		idToProc.get(id).resumeMessages();
	}
	
	public void partialKill(Integer id, Integer numMsgs) {
		System.out.println("partialKill(" + id + "," + numMsgs + ")");
		idToProc.get(id).partialKill(numMsgs);
	}
	
	public void allClear() {
		
	}
	
	public void rejectNextChange(Integer id) {
		
	}
	
	
	public void runCommmands() {
		
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {
		Integer desiredNumProcs = 5;
		Controller mainController = new Controller("instructions_test.txt", desiredNumProcs);
		for(Process p : mainController.idToProc) {
			p.start();
		}
//		mainController.idToProc.get(0).sendVoteReq("COMMAND");
		//mainController.idToProc.get(1).partialMessage(5);
		
		mainController.executeInstructions();
		//Thread.sleep(10 * 1000);
		for(Process p: mainController.idToProc) {
			p.displayPlaylist();
			p.shutdown();
		}
	}

	
	
}