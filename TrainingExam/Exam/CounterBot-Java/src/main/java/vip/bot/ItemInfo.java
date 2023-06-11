package vip.bot;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;

public class ItemInfo {
	private UnrealId id;
	private double distance;
	
	public ItemInfo(UnrealId id, double distance) {
		this.id = id;
		this.distance = distance;
	}
	
	public UnrealId getId() {
		return id;
	}
	
	public double getDistance() {
		return distance;
	}
}
