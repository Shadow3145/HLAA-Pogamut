package vip.tc.msgs;

import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.base3d.worldview.object.Rotation;
import cz.cuni.amis.pogamut.base3d.worldview.object.Velocity;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.AgentInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Player;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerMessage;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;

public class TCRunningTo extends TCMessageData {

	/**
	 * Auto-generated
	 */
	private static final long serialVersionUID = -7230526335342461314L;

	/**
	 * ID of the player broadcasting this message.
	 */
	public UnrealId id;
	
	/**
	 * Location the player is navigating to.
	 */
	public Location location;
	
	public TCRunningTo(UnrealId id, Location location) {
		this.id = id;
		this.location = location;
	}
	
}
