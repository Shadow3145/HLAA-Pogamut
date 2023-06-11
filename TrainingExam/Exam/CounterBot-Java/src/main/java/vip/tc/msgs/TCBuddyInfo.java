package vip.tc.msgs;


import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;


public class TCBuddyInfo extends TCMessageData {

	/**
	 * Auto-generated.
	 */
	private static final long serialVersionUID = 786632348487491248L;
	
	public UnrealId player;
	public boolean back;
	
	public TCBuddyInfo(UnrealId playerId, boolean back) {
		this.player = playerId;
		this.back = back;
	}
}