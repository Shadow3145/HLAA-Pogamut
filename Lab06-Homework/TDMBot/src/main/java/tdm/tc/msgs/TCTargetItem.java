package tdm.tc.msgs;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;

public class TCTargetItem extends TCMessageData {
	
	private static final long serialVersionUID = 7866323423491241L;
	
	public UnrealId who;
	
	public UnrealId what;	
	
	public TCTargetItem(UnrealId who, UnrealId what) {
		this.who = who;
		this.what = what;
	}
}