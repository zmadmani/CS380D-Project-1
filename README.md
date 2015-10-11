# CS380D-3PC

Group: Zain Admani and Juilan Sia (Z&J)
Project slip days used for 3PC: 2
Project slip days used (total): 2

COMMAND FORMAT: [ID]:[MESSAGE]

MESSAGES:
	VOTE_REQ:[THING TO VOTE ON]
	STATE_REQ
	STATE_REQ_COORD
	STATE_RESP
	STATE_RESP_COORD
	YES
	NO
	ABORT
	PRECOMMIT
	DOCOMMIT

COMMANDS:
	ADD(songName,URL)
	REMOVE(songName)
	EDIT(songName,newSongName,newSongURL)


## Objects (Controller.java)
1. Controller(String instructionsPath, int desirednumProcs): Create a controller that will execute a list of instructions found in instructionsPath on a system of desireNumProcs

## Methods (Controller.java)
1. executeinstructions(): run the instructions find on instructionsPath
2. kill(integer id): kill the id process
3. killAll(): kill all the processes in the network
4. killLeader(): kill the current leader
5. revive(Integer id): revive the id process
6. reviveLast(): revive the last process to have died
7. reviveAll(): revive all the processes
8. partialMessage(Integer id, Integer numMsgs): let id process send numMsgs, then stop sending protocol messages
9. resumeMessages(Integer id): resume messaging for the id process
10. partialKill(Integer id, Integer numMsgs): let id process send numMsgs, then shutdown
11. rejectNextChange(Integer id): vote no for next proposed change

----------------------------------------------------------------------------
##TEST CASES:
-------------------------------------------------------------------
PARTICIPANT
// Participant Failure and recovery
currLeaderIndex = 4
partialKill(0,1);
revive(0)

// before voted (just after voted0)
partialKill(0,0)
revive(0)

COORDINATOR FAILURE
// dies before sending anything
partialKill(4,0)
revive(4)

// dies after sending 1 VOTEREQ 
partialKill(0,2)
partialKill(0)


Partial PreCommit
partialKill(4,8)
revive(4)

Partial Commit
// got all ACKS and died
partialKill(4,14)
revive(4)

Cascading - pre VOTEREQ
// dies after sending 1 VOTEREQ to an external participant
partialKill(0,2)
partialKill(1,3)
revive(0)
revive(1)

Cascading - partial commit
partialKill(0,14)
partialKill(1,3)
revive(0)
revive(1)

cascading - partial pre-commit
partialKill(0,8)
partialKill(1,3)
revive(0)
revive(1)


future coordinator failure
partialKill(0, 6)
partialKill(1,2)
revive(0)
revive(1)

future coordinator failure Partial commit
partialKill(0,9)
partialKill(1,3)


Partial PrePrecommit
// got all votes and died
partialKill(4,6)
revive(4)

partial commit
// one guy get COMMIT
partialKill(4,14)
revive(4)


total failure 
partialKill(0,6)
partialKill(1,1);
partialKill(2,1);
partialKill(4,1)
revive(1)
revive(2)
revive(3)
revive(4