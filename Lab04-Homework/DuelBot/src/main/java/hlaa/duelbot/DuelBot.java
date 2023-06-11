package hlaa.duelbot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;


import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.ObjectClassEventListener;
import cz.cuni.amis.pogamut.base.communication.worldview.object.event.WorldObjectUpdatedEvent;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensomotoric.Weapon;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.UT2004Skins;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.NavigationState;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType.Category;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UT2004ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotDamaged;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ConfigChange;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GameInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.IncomingProjectile;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.InitedMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ItemPickedUp;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPoint;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Player;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerDamaged;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Self;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.Cooldown;
import cz.cuni.amis.utils.Heatup;
import cz.cuni.amis.utils.exception.PogamutException;
import cz.cuni.amis.utils.flag.FlagListener;

@AgentScoped
public class DuelBot extends UT2004BotModuleController {

	private long   lastLogicTime        = -1;
    private long   logicIterationNumber = 0;    

    private int currentShortRangeWeapon = 1;
    private int currentMidRangeWeapon = 1;
    private int currentLongRangeWeapon = 1;
    
    Cooldown lightCD = new Cooldown(2000);
    Heatup pursueEnemy = new Heatup(3000);
    Heatup fleeFromEnemy = new Heatup(3000);
    
    Player enemy = null;
    
    private final int HEALTH_PACKS = 50;
    private final int FLEE = 35;
    private final int CAREFUL = 50;
    
    /**
     * Here we can modify initializing command for our bot, e.g., sets its name or skin.
     *
     * @return instance of {@link Initialize}
     */
    @Override
    public Initialize getInitializeCommand() {
    	return new Initialize().setName("DuelBot").setSkin(UT2004Skins.getRandomSkin()).setDesiredSkill(6);
    }

    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange currentConfig, InitedMessage init) {
    	bot.getLogger().getCategory("Yylex").setLevel(Level.OFF);
    	
    	// GENERAL WEAPON PREFERENCES
    	weaponPrefs.addGeneralPref(UT2004ItemType.MINIGUN, false);
    	weaponPrefs.addGeneralPref(UT2004ItemType.LIGHTNING_GUN, true);    	
    	weaponPrefs.addGeneralPref(UT2004ItemType.LINK_GUN, false);
    	weaponPrefs.addGeneralPref(UT2004ItemType.SHOCK_RIFLE, true);
    	weaponPrefs.addGeneralPref(UT2004ItemType.SNIPER_RIFLE, true);
    	weaponPrefs.addGeneralPref(UT2004ItemType.ROCKET_LAUNCHER, true);
    	weaponPrefs.addGeneralPref(UT2004ItemType.FLAK_CANNON, true);
    	weaponPrefs.addGeneralPref(UT2004ItemType.ASSAULT_RIFLE, true);
    	weaponPrefs.addGeneralPref(UT2004ItemType.SHIELD_GUN, false);
    	weaponPrefs.addGeneralPref(UT2004ItemType.BIO_RIFLE, true);
    	
    	
    	// RANGE WEAPON PREFERENCES
    	weaponPrefs.newPrefsRange(400)
    		.add(UT2004ItemType.FLAK_CANNON, true)
    		.add(UT2004ItemType.LINK_GUN, true);
    	
    	weaponPrefs.newPrefsRange(1150)
    		.add(UT2004ItemType.MINIGUN, false)
    		.add(UT2004ItemType.ROCKET_LAUNCHER, true)
    		.add(UT2004ItemType.LINK_GUN, true);
    	weaponPrefs.newPrefsRange(2500)
    		.add(UT2004ItemType.LIGHTNING_GUN, true)
    		.add(UT2004ItemType.SHOCK_RIFLE, true)
    		.add(UT2004ItemType.SNIPER_RIFLE, true);    	
    }
    
    @Override
    public void botFirstSpawn(GameInfo gameInfo, ConfigChange config, InitedMessage init, Self self) {
        navigation.addStrongNavigationListener(new FlagListener<NavigationState>() {
			@Override
			public void flagChanged(NavigationState changedValue) {
				navigationStateChanged(changedValue);
			}
        });
    }
    
    /**
     * The navigation state has changed...
     * @param changedValue
     */
    private void navigationStateChanged(NavigationState changedValue) {
    	switch(changedValue) {
    	case TARGET_REACHED:
    		return;
		case PATH_COMPUTATION_FAILED:
			return;
		case STUCK:
			return;
		}
    }
    
    @Override
    public void beforeFirstLogic() {    	
    }
    
    // ====================
    // BOT MIND MAIN METHOD
    // ====================
        
    @Override
    public void logic() throws PogamutException {
    	if (lastLogicTime < 0) {
    		lastLogicTime = System.currentTimeMillis();
    		return;
    	}

    	//log.info("---LOGIC: " + (++logicIterationNumber) + " / D=" + (System.currentTimeMillis() - lastLogicTime) + "ms ---");
    	lastLogicTime = System.currentTimeMillis();

    	// use Bot Name to visualize high-level state of your bot to ease debugging
    	setDebugInfo("BRAIN-DEAD");
    	
    	// FOLLOWS THE BOT'S LOGIC
    	if (engageInCombat())
    		return;
    	shoot.stopShooting();
    	if (pickItems())
    		return;  	
    	
	    if(!navigation.isNavigating())
	        navigation.navigate(navPoints.getRandomNavPoint());	  	
    }
    
    // ======
    // COMBAT
    // ======
    
    private boolean engageInCombat() {
    	findEnemy();
    	if (pursueEnemy.isCool())
    		return false;
    	
    	if (info.getHealth() <= FLEE) {
    		fleeAndFindMedkit();
    		return true;
    	}
    	
    	if (!readyToFight())
    		return false;
    	
    	if (!players.canSeePlayers() || currentShortRangeWeapon > currentLongRangeWeapon)
    		navigation.navigate(enemy);
    	
    	if (enemy.isVisible()) {
    		shootEnemy();
    	}
    	else
    		shoot.stopShooting();
    	
    	return true;
	}
    
    private void shootEnemy() {
    	if (lightCD.tryUse()) {
			shoot.shoot(weaponPrefs, enemy);
		}
		else {
			shoot.shoot(weaponPrefs, enemy, UT2004ItemType.LIGHTNING_GUN);
		}
	}

	private void fleeAndFindMedkit() {
		if (enemy.isVisible())
			fleeFromEnemy.heat();
		
		if (fleeFromEnemy.isHot()) {
			flee();
			return;
		}
		shoot.stopShooting();
		log.info("Running for medkit");
		getBestHealthPack();		
	}
	
	private void flee() {
		log.info("Running from enemy");
		shoot.stopShooting();
		
		NavPoint point = null;
		for (NavPoint p : navPoints.getNavPoints().values()) {
			if (p.getLocation().getDistance(enemy.getLocation()) > info.getLocation().getDistance(enemy.getLocation())) {
				point = p;
				break;
			}
		}
		navigation.navigate(point);	
	}
	
	private boolean findEnemy() {
    	Player recentEnemy = players.getNearestEnemy(300);
    	if (recentEnemy == null) {
    		//log.info("No recent enemy");
    		return false;
    	}
    	
    	enemy = recentEnemy;
    	pursueEnemy.heat();
    	return true;
    }
	
	private boolean readyToFight()
	{
		boolean suitableWeapon = true;
		double distance = info.getLocation().getDistance(enemy.getLocation());
		
		if ((distance <= 400 && this.currentShortRangeWeapon <= 5) || 
			(distance <= 1150 && this.currentMidRangeWeapon <= 5) ||
			 this.currentLongRangeWeapon <= 5)
			suitableWeapon = false;
		
		if (!suitableWeapon && info.getHealth() <= CAREFUL)
		{
			log.info("This combat doesn't look good");
			return false;
		}
		
		return true;
	}

    
    // ===============
    // ITEM COLLECTING
    // ===============
    private Collection<ItemInfo> getAllInterestingItems() {
    	List<ItemInfo> result = new ArrayList<ItemInfo>();
    	
    	for (Item item : items.getSpawnedItems().values()) {
    		if (item.getType().getCategory() == Category.WEAPON) {
    			double value = Math.max(this.getShortRangeWeaponsScore(item), Math.max(this.getMidRangeWeaponsScore(item), this.getLongRangeWeaponsScore(item)));
    			result.add(new ItemInfo(item, value));
    		}
    		else if (item.getType().getCategory() == Category.ARMOR ||
    				item.getType().getCategory() == Category.SHIELD ||
    				item.getType().getCategory() == Category.OTHER) {
    			double value = this.getUtilityItemsScore(item);
    			result.add(new ItemInfo(item, value));
    		}
    		else if (item.getType().getCategory() == Category.HEALTH) {
    			double value = info.getHealth() >= 90 ? 0.0001 : this.getHealthScore(item);
    			result.add(new ItemInfo(item, value));
    		}  		
    	}    	
    	return result;
    }
 
    private boolean navigateToItem(Item targetItem)
    {
    	if (targetItem == null)
    		return false;
    	
    	navigation.navigate(targetItem);
    	log.info("Going for " + targetItem.getType().getName());
    	return true;
    }
    
    private boolean pickItems() {
    	Item targetItem = null; 
    	
    	if (info.getHealth() <= this.HEALTH_PACKS) {
    		targetItem = getBestHealthPack();
    	}
    	
    	if (this.currentShortRangeWeapon < 5 || this.currentMidRangeWeapon < 5) {
    		targetItem = getBestShortToMidWeapon();
    	}
    	
    	if (this.currentMidRangeWeapon < 5 || this.currentLongRangeWeapon < 5) {
    		targetItem = getBestMidToLongWeapon();
    	}
    	
    	if (info.getArmor() < 50) {
    		targetItem = getBestShield();
    	}
    	
    	targetItem = getBestItem(getAllInterestingItems()).getItem();
    	return navigateToItem(targetItem);
    }
    
	private ItemInfo getBestItem(Collection<ItemInfo> items) {
		return DistanceUtils.getNearest(
				items,
				info.getLocation(),
				new DistanceUtils.IGetDistance<ItemInfo>() {
					@Override
					public double getDistance(ItemInfo object, ILocated target) {
						double distance = navMeshModule.getAStarPathPlanner().getDistance(object.getItem(), target);
						return distance / object.getValue();
					}
				});		
	}

	private Item getBestShield() {
		List<ItemInfo> shields = new ArrayList<ItemInfo>();
		
		for (Item item : items.getSpawnedItems().values()) {
			if (item.getType().getCategory() == Category.ARMOR ||
				item.getType().getCategory() == Category.SHIELD)
				shields.add(new ItemInfo(item, this.getUtilityItemsScore(item)));
		}
		if (shields.isEmpty())
			return null;
		return getBestItem(shields).getItem();		
	}

	private Item getBestMidToLongWeapon() {
		List<ItemInfo> weapons = new ArrayList<ItemInfo>();
		
		for (Item item : items.getSpawnedItems().values()) {
			if (item.getType().getCategory() == Category.WEAPON) {
				int value = getLongRangeWeaponsScore(item);
				if (value > 1) 
					weapons.add(new ItemInfo(item, value));					
			}
		}
		if (weapons.isEmpty())
			return null;
		
		return getBestItem(weapons).getItem();		
	}

	private Item getBestShortToMidWeapon() {
		List<ItemInfo> weapons = new ArrayList<ItemInfo>();
		
		for (Item item : items.getSpawnedItems().values()) {
			if (item.getType().getCategory() == Category.WEAPON) {
				int value = getShortRangeWeaponsScore(item);
				if (value > 1) 
					weapons.add(new ItemInfo(item, value));					
			}
		}
		if (weapons.isEmpty())
			return null;
		return getBestItem(weapons).getItem();				
	}

	private Item getBestHealthPack() {
		List<ItemInfo> healthPacks = new ArrayList<ItemInfo>();
		
		for (Item item : items.getSpawnedItems().values()) {
			if (item.getType().getCategory() == Category.HEALTH)
			{
				int value = getHealthScore(item);
				healthPacks.add(new ItemInfo(item, value));
			}
		}
		if (healthPacks.isEmpty())
			return null;
		return getBestItem(healthPacks).getItem();
		
	}

	// ==============
    // EVENT HANDLERS
    // ==============
    
    /**
     * You have just picked up some item.
     * @param event
     */
    @EventListener(eventClass=ItemPickedUp.class)
    public void itemPickedUp(ItemPickedUp event) {
    	if (info.getSelf() == null) return; // ignore the first equipment...
    	Item pickedUp = items.getItem(event.getId());
    	if (pickedUp == null) return; // ignore unknown items
    	if (pickedUp.getType().getCategory() == Category.WEAPON) {
    		this.currentShortRangeWeapon = this.getShortRangeWeaponsScore(pickedUp);
    		this.currentMidRangeWeapon = this.getMidRangeWeaponsScore(pickedUp);
    		this.currentLongRangeWeapon = this.getLongRangeWeaponsScore(pickedUp);
    	}
    }
    
    /**
     * YOUR bot has just been damaged.
     * @param event
     */
    @EventListener(eventClass=BotDamaged.class)
    public void botDamaged(BotDamaged event) {
    	log.info("I have been DAMAGED");
    }	

    /**
     * YOUR bot has just been killed. 
     */
    @Override
    public void botKilled(BotKilled event) {
        sayGlobal("I was KILLED!");
        
        navigation.stopNavigation();
        shoot.stopShooting();
        
        // RESET YOUR MEMORY VARIABLES HERE
        enemy = null;
        currentLongRangeWeapon = 1;
        currentMidRangeWeapon = 1;
        currentShortRangeWeapon = 1;
        
        fleeFromEnemy.clear();
        lightCD.clear();
        pursueEnemy.clear();
    }
    
    /**
     * Some other BOT has just been damaged by someone (may be even by you).
     * @param event
     */
    @EventListener(eventClass=PlayerDamaged.class)
    public void playerDamaged(PlayerDamaged event) {
    }
    
    /**
     * Some other BOT has just been killed by someone (may be even by you).
     * @param event
     */
    @EventListener(eventClass=PlayerKilled.class)
    public void playerKilled(PlayerKilled event) {    	
    }
    
    @ObjectClassEventListener(eventClass=WorldObjectUpdatedEvent.class, objectClass=IncomingProjectile.class)
    public void incomingProjectile(WorldObjectUpdatedEvent<IncomingProjectile> event) {  
    	double speed = event.getObject().getSpeed();    	
    	double distance = info.getLocation().getDistance(event.getObject().getLocation());
    	
    	if ((distance/speed) <= 0.2) {
    		move.dodgeLeft(event.getObject().getLocation(), true);
        	log.info("Dodging projectile");  
    	}
    }

    // =========
    // UTILITIES
    // =========
    
    private void setDebugInfo(String info) {
    	bot.getBotName().setInfo(info);
    	log.info(info);
    }
    
    private void setDebugValue(String tag, String value) {
    	bot.getBotName().setInfo(tag, value);
    	log.info(tag + ": " + value);
    }
    
    private void sayGlobal(String msg) {
    	// Simple way to send msg into the UT2004 chat
    	body.getCommunication().sendGlobalTextMessage(msg);
    	// And user log as well
    	log.info(msg);
    }
    
    // ============
    // ITEMS VALUES
    // ============
    
    private int getHealthScore(Item item) {
    	if(item.getType().equals(UT2004ItemType.MINI_HEALTH_PACK)) return 2;
    	if (item.getType().equals(UT2004ItemType.HEALTH_PACK)) return 4;
    	if (item.getType().equals(UT2004ItemType.SUPER_HEALTH_PACK)) return 10;
    	
    	return 1;
    }
    
    private int getShortRangeWeaponsScore(Item item) {
    	// Primary short ranged weapons
    	if (item.getType().equals(UT2004ItemType.FLAK_CANNON)) return 10;
    	if (item.getType().equals(UT2004ItemType.LINK_GUN)) return 8;
    	
    	// Primary mid ranged weapons
    	if (item.getType().equals(UT2004ItemType.MINIGUN)) return 6;
    	if (item.getType().equals(UT2004ItemType.ROCKET_LAUNCHER)) return 5;
    	
    	// Long ranged weapons and weak weapons
    	return 1;
    }
    
    private int getMidRangeWeaponsScore(Item item) {
    	// Primary mid ranged weapons
    	if (item.getType().equals(UT2004ItemType.MINIGUN)) return 10;
    	if (item.getType().equals(UT2004ItemType.ROCKET_LAUNCHER)) return 8;
    	if (item.getType().equals(UT2004ItemType.LINK_GUN)) return 7;
    	
    	// Primary long ranged weapons
    	if (item.getType().equals(UT2004ItemType.LIGHTNING_GUN)) return 7;
    	if (item.getType().equals(UT2004ItemType.SHOCK_RIFLE)) return 6;
    	if (item.getType().equals(UT2004ItemType.SNIPER_RIFLE)) return 6;
    	
    	// Short ranged weapons and weak weapons
    	return 1;
    }
    
    private int getLongRangeWeaponsScore (Item item) {
    	// Primary long ranged weapons
    	if (item.getType().equals(UT2004ItemType.LIGHTNING_GUN)) return 10;
    	if (item.getType().equals(UT2004ItemType.SHOCK_RIFLE)) return 8;
    	if (item.getType().equals(UT2004ItemType.SNIPER_RIFLE)) return 8;
    	
    	// Primary mid ranged weapons
    	if (item.getType().equals(UT2004ItemType.MINIGUN)) return 6;
    	if (item.getType().equals(UT2004ItemType.ROCKET_LAUNCHER)) return 5;
    	
    	return 1;
    }
    
    private int getUtilityItemsScore (Item item) {
    	if (item.getType().equals(UT2004ItemType.U_DAMAGE_PACK)) return 15;
    	if (item.getType().equals(UT2004ItemType.SHIELD_PACK)) return 4;
    	if (item.getType().equals(UT2004ItemType.SUPER_SHIELD_PACK)) return 5;
    	
    	return 1;
    }
     
    
    // ===========
    // MAIN METHOD
    // ===========
    
    public static void main(String args[]) throws PogamutException {
        new UT2004BotRunner(     // class that wrapps logic for bots executions, suitable to run single bot in single JVM
                DuelBot.class,   // which UT2004BotController it should instantiate
                "DuelBot"        // what name the runner should be using
        ).setMain(true)          // tells runner that is is executed inside MAIN method, thus it may block the thread and watch whether agent/s are correctly executed
         .startAgents(1);        // tells the runner to start 2 agents
    }
}
