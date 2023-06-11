package vip.tc.msgs;


import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;


public class TCAllyDeath extends TCMessageData {

	/**
	 * Auto-generated.
	 */
	private static final long serialVersionUID = 786637687891248L;
	
	public UnrealId player;
	
	public TCAllyDeath(UnrealId playerId) {
		this.player = playerId;
	}
}