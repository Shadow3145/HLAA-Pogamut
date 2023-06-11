package vip;

import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.pogamut.ut2004.teamcomm.server.UT2004TCServer;

public class StartTeamComm {

	/**
	 * Starts up TeamComm server for UT2004 bots. There must be only one
	 * instance of UT2004TCServer running at a given time.
	 * 
	 * TeamComm server acts as a "instant messaging" for UT2004 bots allowing
	 * them to easily exchange {@link TCMessageData}, it supports teams and
	 * sub-channels.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		UT2004TCServer.startTCServer();
	}

}