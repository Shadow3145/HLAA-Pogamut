package vip.tc.msgs;


import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;

public class TCEnemyInfo extends TCMessageData {

	/**
	 * Auto-generated.
	 */
	private static final long serialVersionUID = 7866323423491248L;
	
	public UnrealId player;
	public UnrealId enemy;
	public boolean visible;
	
	public TCEnemyInfo(UnrealId playerId, UnrealId enemyId, boolean visible) {
		this.player = playerId;
		this.enemy = enemyId;
		this.visible = visible;
	}
}