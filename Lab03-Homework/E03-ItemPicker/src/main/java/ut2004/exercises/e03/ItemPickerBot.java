package ut2004.exercises.e03;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.Items;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.UT2004Skins;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType.Category;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ConfigChange;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GameInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GlobalChat;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.InitedMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ItemPickedUp;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Self;
import cz.cuni.amis.pogamut.ut2004.teamcomm.bot.UT2004BotTCController;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.exception.PogamutException;
import ut2004.exercises.e03.comm.TCItemPicked;
import ut2004.exercises.e03.comm.TCTargetItem;

/**
 * EXERCISE 03
 * -----------
 * 
 * Your task is to pick all interesting items.
 * 
 * Interesting items are:
 * -- weapons
 * -- shields
 * -- armors
 * 
 * Target maps, where to test your squad are:
 * -- DM-1on1-Albatross
 * -- DM-1on1-Roughinery-FPS
 * -- DM-Rankin-FE
 * 
 * To start scenario:
 * 1. start either of startGamebotsTDMServer-DM-1on1-Albatross.bat, startGamebotsTDMServer-DM-1on1-Roughinery-FPS.bat, startGamebotsTDMServer-DM-Rankin-FE.bat
 * 2. start team communication view running {@link TCServerStarter#main(String[])}.
 * 3. start your squad
 * 4. use ItemPickerChecker methods to query the state of your run
 * 
 * Behavior tips:
 * 1. be sure not to repick item you have already picked
 * 2. be sure not to repick item some other bot has already picked (use {@link #tcClient} for that)
 * 3. do not try to pick items you are unable to, check by {@link Items#isPickable(Item)}
 * 4. be sure not to start before {@link ItemPickerChecker#isRunning()}
 * 5. you may terminate your bot as soon as {@link ItemPickerChecker#isVictory()}.
 * 
 * WATCH OUT!
 * 1. All your bots can be run from the same JVM (for debugging purposes), but they must not communicate via STATICs!
 * 2. If you want to test your bots in separate JVMs, switch {@link #RUN_STANDALONE} to TRUE and start this file 3x.
 * 
 * @author Jakub Gemrot aka Jimmy aka Kefik
 */

@AgentScoped
public class ItemPickerBot extends UT2004BotTCController {
    
	private static AtomicInteger INSTANCE = new AtomicInteger(1);
	
	private static Object MUTEX = new Object();
	
	private int instance = 0;
	
    private int logicIterationNumber;
    
    private long lastLogicTime = -1;

	/**
     * Here we can modify initializing command for our bot, e.g., sets its name or skin.
     *
     * @return instance of {@link Initialize}
     */
    @Override
    public Initialize getInitializeCommand() {  
    	instance = INSTANCE.getAndIncrement();
    	return new Initialize().setName("PickerBot-" + instance).setSkin(UT2004Skins.getSkin());
    }

    /**
     * Bot is ready to be spawned into the game; configure last minute stuff in here
     *
     * @param gameInfo information about the game type
     * @param config information about configuration
     * @param init information about configuration
     */
    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange currentConfig, InitedMessage init) {
    	// ignore any Yylex whining...
    	bot.getLogger().getCategory("Yylex").setLevel(Level.OFF);
    }
    
    /**
     * This method is called only once, right before actual logic() method is called for the first time.
     * At this point you have {@link Self} i.e., this.info fully initialized.
     */
    @Override
    public void beforeFirstLogic() {
    	// REGISTER TO ITEM PICKER CHECKER
    	ItemPickerChecker.register(info.getId());    	
    }
    
    /**
     * Say something through the global channel + log it into the console...    
     * @param msg
     */
    private void sayGlobal(String msg) {
    	// Simple way to send msg into the UT2004 chat
    	body.getCommunication().sendGlobalTextMessage(msg);
    	// And user log as well
    	log.info(msg);
    }
    
    @EventListener(eventClass=GlobalChat.class)
    public void chatReceived(GlobalChat msg) {
    }
    
    /**
     * THIS BOT has picked an item!
     * @param event
     */
    @EventListener(eventClass=ItemPickedUp.class)
    public void itemPickedUp(ItemPickedUp event) {
    	if (ItemPickerChecker.itemPicked(info.getId(), items.getItem(event.getId()))) {
	    	// AN ITEM HAD BEEN PICKED + ACKNOWLEDGED BY ItemPickerChecker
    		tcClient.sendToAllOthers(new TCItemPicked(info.getId(), event.getId()));
    		pickedUpItems.add(event.getId());
    	} else {
    		// should not happen... but if you encounter this, just wait with the bot a cycle and report item picked again
    		log.severe("SHOULD NOT BE HAPPENING! ItemPickerChecker refused our item!");
    	}
    }
    
    Set<UnrealId> pickedUpItems = new HashSet<UnrealId>();
    Map<UnrealId, ItemInfo> targetItems = new HashMap<UnrealId, ItemInfo>();
    
    /**
     * Someone else picked an item!
     * @param event
     */
    @EventListener(eventClass = TCItemPicked.class)
    public void tcItemPicked(TCItemPicked event) {    
    	if (RUN_STANDALONE) {
    		ItemPickerChecker.itemPicked(event.getWho(), items.getItem(event.getWhat()));	
    	} 
    	pickedUpItems.add(event.getWhat());
    }    
    
    public void targetItem(Item target) {   	
    	double distance = navMeshModule.getAStarPathPlanner().getDistance(info.getLocation(), target);
    	tcClient.sendToAll(new TCTargetItem(info.getId(), target.getId(), distance));
    }
    
    @EventListener(eventClass = TCTargetItem.class)
    public void tcTargetItem(TCTargetItem event) {
    	if (info.getId() != event.getWho())
    		targetItems.put(event.getWho(), new ItemInfo(event.getWhat(), event.getDistance()));
    }
    
    /**
     * Main method called 4 times / second. Do your action-selection here.
     */
    @Override
    public void logic() throws PogamutException {
    	log.info("---LOGIC: " + (++logicIterationNumber) + "---");
    	if (lastLogicTime > 0) log.info("   DELTA: " + (System.currentTimeMillis()-lastLogicTime + "ms"));
    	lastLogicTime = System.currentTimeMillis();  
    	
    	if (!tcClient.isConnected()) {
    		log.warning("TeamComm not running!");
    		return;
    	}
    	    
    	if (ItemPickerChecker.isVictory()) {
    		logGoal("VICTORY!!!");
    		return;
    	}
    	    	
    	if (!ItemPickerChecker.isRunning()) {
    		log.info("ITEM PICKER CHECKER IS INITIALIZING...");
    		if (RUN_STANDALONE) {
        		for (UnrealId connectedBot : tcClient.getConnectedAllBots()) {
        			ItemPickerChecker.register(connectedBot);
        		}
        	}
    		return;    		
    	}
    	
    	//log.info("ITEM PICKER RUNNING!");  
    	
    	Item targetItem = getTargetItem();
    	navigateToItem(targetItem);
    }
    
    private void navigateToItem(Item targetItem)
    {   	
    	if (targetItem == null) {
    		logGoal("NO ITEM!");
    		return;
    	}
    	
    	targetItem(targetItem);
    	navigation.navigate(targetItem); 	
    	logGoal("Going for " + targetItem.getType().getName());
    	
    }
    
    private void logGoal(String msg) {
		log.info(msg);
		bot.getBotName().setInfo(msg);		
	}

	private Item getTargetItem() {
    	return DistanceUtils.getNearest(
    			getInterestingItems(),
    			info.getLocation(),
    			new DistanceUtils.IGetDistance<Item>() {

					@Override
					public double getDistance(Item object, ILocated target) {
						return navMeshModule.getAStarPathPlanner().getDistance(target, object);
					}
    				
				});
    }
    
    private Collection<Item> getInterestingItems() {
    	List<Item> result = new ArrayList<Item>();
    	
    	for (Item item : items.getSpawnedItems().values()) {
    		if (pickedUpItems.contains(item.getId()))
    			continue;
    		
    		boolean targeted = false;
    		
    		if (item.getType().getCategory() == Category.ARMOR || 
    			item.getType().getCategory() == Category.SHIELD || 
    			item.getType().getCategory() == Category.WEAPON) {
    			
    			double distance = navMeshModule.getAStarPathPlanner().getDistance(info.getLocation(), item);
    			
    			for(ItemInfo itemInfo : targetItems.values()) {
    				if (itemInfo.getId() == item.getId() && itemInfo.getDistance() < distance) {
    					targeted = true;
    					break;
    				}					
    			}
    			if (targeted)
    				continue;
    			
    			result.add(item);
    		}
    	}
    	
    	return result;
    }
    /**
     * To run ItemPickerBots in standalone mode - switch this to TRUE and run this file 3 times.
     */
    public static boolean RUN_STANDALONE = false;
    
    /**
     * This method is called when the bot is started either from IDE or from command line.
     *
     * @param args
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
	public static void main(String args[]) throws PogamutException {    	    	
        new UT2004BotRunner(      // class that wrapps logic for bots executions, suitable to run single bot in single JVM
                ItemPickerBot.class,  // which UT2004BotController it should instantiate
                "PickerBot"       // what name the runner should be using
        ).setMain(true)           // tells runner that is is executed inside MAIN method, thus it may block the thread and watch whether agent/s are correctly executed
         .startAgents(RUN_STANDALONE ? 1 : ItemPickerChecker.ITEM_PICKER_BOTS); // tells the runner to start N agent
    }
}
