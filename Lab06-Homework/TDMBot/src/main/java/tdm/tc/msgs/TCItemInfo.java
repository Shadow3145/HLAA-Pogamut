package tdm.tc.msgs;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;

public class TCItemInfo extends TCMessageData {
	
	private static final long serialVersionUID = 7866323423491242L;
	
	public UnrealId who;
	
	public UnrealId what;	
	
	public int cost;
	
	public TCItemInfo(UnrealId who, UnrealId what, int cost) {
		this.who = who;
		this.what = what;
		this.cost = cost;
	}
}


