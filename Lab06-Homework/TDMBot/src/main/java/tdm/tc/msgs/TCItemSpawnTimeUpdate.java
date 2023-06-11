package tdm.tc.msgs;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;

public class TCItemSpawnTimeUpdate extends TCMessageData {
	
	private static final long serialVersionUID = 7866323423491298L;
	
	public UnrealId what;	
	
	public double spawnTime;
	
	public TCItemSpawnTimeUpdate(UnrealId what, double spawnTime) {
		this.what = what;
		this.spawnTime = spawnTime;
	}
}