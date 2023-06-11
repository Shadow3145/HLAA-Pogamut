package hlaa.advduelbot;

import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;

public class ItemInfo {
	private Item item;
	private double value;
	
	public ItemInfo(Item item, double value) {
		this.item = item;
		this.value = value;
	}
	
	public Item getItem() {
		return item;
	}
	
	public double getValue() {
		return value;
	}	
}
