package tdm;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import cz.cuni.amis.pathfinding.alg.astar.AStarResult;
import cz.cuni.amis.pathfinding.map.IPFMapView;
import cz.cuni.amis.pogamut.base.agent.navigation.IPathFuture;
import cz.cuni.amis.pogamut.base.agent.navigation.impl.PrecomputedPathFuture;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.ObjectClassEventListener;
import cz.cuni.amis.pogamut.base.communication.worldview.object.event.WorldObjectUpdatedEvent;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.base3d.worldview.object.Rotation;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.AgentInfo;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.WeaponPref;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.UT2004Skins;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.NavigationState;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.levelGeometry.RayCastResult;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.navmesh.NavMeshClearanceComputer.ClearanceLimit;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.navmesh.pathfollowing.NavMeshNavigation;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType.Category;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UT2004ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotDamaged;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ConfigChange;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GameInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.HearNoise;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.IncomingProjectile;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.InitedMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ItemPickedUp;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPoint;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Player;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerDamaged;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Self;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.TeamScore;
import cz.cuni.amis.pogamut.ut2004.teamcomm.bot.UT2004BotTCController;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.Cooldown;
import cz.cuni.amis.utils.ExceptionToString;
import cz.cuni.amis.utils.Heatup;
import cz.cuni.amis.utils.collections.MyCollections;
import cz.cuni.amis.utils.exception.PogamutException;
import cz.cuni.amis.utils.flag.FlagListener;
import math.geom2d.Vector2D;
import tdm.tc.TDMCommItems;
import tdm.tc.TDMCommObjectUpdates;
import tdm.tc.msgs.TCEnemyInfo;
import tdm.tc.msgs.TCItemInfo;
import tdm.tc.msgs.TCItemSpawnTimeUpdate;
import tdm.tc.msgs.TCTargetItem;

/**
 * TDM BOT TEMPLATE CLASS
 * Version: 0.0.1
 */
@AgentScoped
public class TDMBot extends UT2004BotTCController<UT2004Bot> {

	private static Object CLASS_MUTEX = new Object();
	
	/**
	 * Whether to load Level Geometry information, slows down bot startup, thus you might
	 * consider disabling the load (== false) when developing behaviors not dependent on LevelGeometry.
	 */
	public static final boolean LEVEL_GEOMETRY_AUTOLOAD = true;
	
	/**
	 * TRUE => draws navmesh and terminates
	 */
	public static final boolean DRAW_NAVMESH = false;
	private static boolean navmeshDrawn = false;
	
	/**
	 * TRUE => rebinds NAVMESH+NAVIGATION GRAPH; useful when you add new map tweak into {@link MapTweaks}.
	 */
	public static final boolean UPDATE_NAVMESH = false;
	
	/**
	 * Whether to draw navigation path; works only if you are running 1 bot...
	 */
	public static final boolean DRAW_NAVIGATION_PATH = false;
	private boolean navigationPathDrawn = false;
	
	/**
	 * If true, all bots will enter RED team... 
	 */
	public static final boolean START_BOTS_IN_SINGLE_TEAM = false;
		
	/**
	 * How many bots we have started so far; used to split bots into teams.
	 */
	private static AtomicInteger BOT_COUNT = new AtomicInteger(0);
	/**
	 * How many bots have entered RED team.
	 */
	private static AtomicInteger BOT_COUNT_RED_TEAM = new AtomicInteger(0);
	/**
	 * How many bots have entered BLUE team.
	 */
	private static AtomicInteger BOT_COUNT_BLUE_TEAM = new AtomicInteger(0);
	
	/**
	 * 0-based; note that during the tournament all your bots will have botInstance == 0!
	 */
	private int botInstance = 0;
	
	/**
	 * 0-based; note that during the tournament all your bots will have botTeamInstance == 0!
	 */
	private int botTeamInstance = 0;
	
	private TDMCommItems<TDMBot> commItems;
	private TDMCommObjectUpdates<TDMBot> commObjectUpdates;
	
	
	private int currentShortRangeWeapon = 1;
    private int currentMidRangeWeapon = 1;
    private int currentLongRangeWeapon = 1;
    
    private Item targetItem = null;
    
 // Combat behaviours heatups and cooldown
    Cooldown lightCD = new Cooldown(2000);
    Heatup pursueEnemy = new Heatup(3000);
    
    Player enemy = null;
    
    // Combat constants
    private final int HEALTH_PACKS = 50;
    private final int CAREFUL = 50;
	
    // =============
    // BOT LIFECYCLE
    // =============
    
    /**
     * Bot's preparation - called before the bot is connected to GB2004 and launched into UT2004.
     */
    @Override
    public void prepareBot(UT2004Bot bot) {       	
        // DEFINE WEAPON PREFERENCES
        initWeaponPreferences();
        
        // INITIALIZATION OF COMM MODULES
        commItems = new TDMCommItems<TDMBot>(this);
        commObjectUpdates = new TDMCommObjectUpdates<TDMBot>(this);
    }
    
    @Override
    protected void initializeModules(UT2004Bot bot) {
    	super.initializeModules(bot);
    	levelGeometryModule.setAutoLoad(LEVEL_GEOMETRY_AUTOLOAD);
    }
    
    /**
     * This is a place where you should use map tweaks, i.e., patch original Navigation Graph that comes from UT2004.
     */
    @Override
    public void mapInfoObtained() {
    	// See {@link MapTweaks} for details; add tweaks in there if required.
    	MapTweaks.tweak(navBuilder);    	
    	if (botInstance == 0) navMeshModule.setReloadNavMesh(UPDATE_NAVMESH);    	
    }
    
    /**
     * Define your weapon preferences here (if you are going to use weaponPrefs).
     */
    private void initWeaponPreferences() {
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
    public Initialize getInitializeCommand() {
    	// IT IS FORBIDDEN BY COMPETITION RULES TO CHANGE DESIRED SKILL TO DIFFERENT NUMBER THAN 6
    	// IT IS FORBIDDEN BY COMPETITION RULES TO ALTER ANYTHING EXCEPT NAME & SKIN VIA INITIALIZE COMMAND
		// Change the name of your bot, e.g., Jakub Gemrot would rewrite this to: targetName = "JakubGemrot"
		String targetName = "MartinaFuskova";
		botInstance = BOT_COUNT.getAndIncrement();
		
		int targetTeam = AgentInfo.TEAM_RED;
		if (!START_BOTS_IN_SINGLE_TEAM) {
			targetTeam = botInstance % 2 == 0 ? AgentInfo.TEAM_RED : AgentInfo.TEAM_BLUE;
		}
		switch (targetTeam) {
		case AgentInfo.TEAM_RED: 
			botTeamInstance = BOT_COUNT_RED_TEAM.getAndIncrement();  
			targetName += "-RED-" + botTeamInstance; 
			break;
		case AgentInfo.TEAM_BLUE: 
			botTeamInstance = BOT_COUNT_BLUE_TEAM.getAndIncrement(); 
			targetName += "-BLUE-" + botTeamInstance;
			break;
		}		
        return new Initialize().setName(targetName).setSkin(targetTeam == AgentInfo.TEAM_RED ? UT2004Skins.SKINS[0] : UT2004Skins.SKINS[UT2004Skins.SKINS.length-1]).setTeam(targetTeam).setDesiredSkill(6);
    }

    /**
     * Bot has been initialized inside GameBots2004 (Unreal Tournament 2004) and is about to enter the play
     * (it does not have the body materialized yet).
     *  
     * @param gameInfo
     * @param currentConfig
     * @param init
     */
    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange currentConfig, InitedMessage init) {
    	// INITIALIZE TABOO SETS, if you have them, HERE
    }

    // ==========================
    // EVENT LISTENERS / HANDLERS
    // ==========================
	
    /**
     * {@link PlayerDamaged} listener that senses that "some other bot was hurt".
     *
     * @param event
     */
    @EventListener(eventClass = PlayerDamaged.class)
    public void playerDamaged(PlayerDamaged event) {
    	UnrealId botHurtId = event.getId();
    	if (botHurtId == null) return;
    	
    	int damage = event.getDamage();
    	Player botHurt = (Player)world.get(botHurtId); // MAY BE NULL!
    	
    	//log.info("OTHER HURT: " + damage + " DMG to " + botHurtId.getStringId() + " [type=" + event.getDamageType() + ", weapon=" + event.getWeaponName() + "]");
    }
    
    /**
     * {@link BotDamaged} listener that senses that "I was hurt".
     *
     * @param event
     */
    @EventListener(eventClass = BotDamaged.class)
    public void botDamaged(BotDamaged event) {
    	int damage = event.getDamage();
    	
    	if (event.getInstigator() == null) {
    		//log.info("HURT: " + damage + " DMG done to ME [type=" + event.getDamageType() + ", weapon=" + event.getWeaponName() + "] by UNKNOWN");
    	} else {
    		UnrealId whoCauseDmgId = event.getInstigator();
    		Player player = (Player) world.get(whoCauseDmgId);
    		
    		if (player != null && enemy == null)
    			enemy = player;
    		
    		//log.info("HURT: " + damage + " DMG done to ME [type=" + event.getDamageType() + ", weapon=" + event.getWeaponName() + "] by " + whoCauseDmgId.getStringId());
    	}
    }
    
    /**
     * {@link PlayerKilled} listener that senses that "some other bot has died".
     *
     * @param event
     */
    @EventListener(eventClass = PlayerKilled.class)
    public void playerKilled(PlayerKilled event) {
    	UnrealId botDiedId = event.getId();
    	if (botDiedId == null) return;
    	
    	Player botDied = (Player) world.get(botDiedId);
    	
    	if (event.getKiller() == null) {
    		//log.info("OTHER DIED: " + botDiedId.getStringId() + ", UNKNOWN killer");
    	} else {
    		UnrealId killerId = event.getKiller();
    		if (killerId.equals(info.getId())) {
    			//log.info("OTHER KILLED: " + botDiedId.getStringId() + " by ME");
    		} else {
    			Player killer = (Player) world.get(killerId);
    			if (botDiedId.equals(killerId)) {
    				//log.info("OTHER WAS KILLED: " + botDiedId.getStringId() + " comitted suicide");
    			} else {
    				//log.info("OTHER WAS KILLED: " + botDiedId.getStringId() + " by " + killerId.getStringId());
    			}
    		}
    	}
    }
    
    /**
     * {@link BotKilled} listener that senses that "your bot has died".
     */
	@Override
	public void botKilled(BotKilled event) {
		if (event.getKiller() == null) {
			//log.info("DEAD");
		} else {
			UnrealId killerId = event.getKiller();
			Player killer = (Player) world.get(killerId);
			//log.info("KILLED by" + killerId.getStringId());
		} 
		reset();
	}
	
    /**
     * {@link HearNoise} listener that senses that "some noise was heard by the bot".
     *
     * @param event
     */
    @EventListener(eventClass = HearNoise.class)
    public void hearNoise(HearNoise event) {
    	double noiseDistance = event.getDistance();   // 100 ~ 1 meter
    	Rotation faceRotation = event.getRotation();  // rotate bot to this if you want to face the location of the noise
    	//log.info("HEAR NOISE: distance = " + noiseDistance);
    }
    
    /**
     * {@link ItemPickedUp} listener that senses that "your bot has picked up some item".
     * 
     * See sources for {@link ItemType} for details about item types / categories / groups.
     *
     * @param event
     */
    @EventListener(eventClass = ItemPickedUp.class)
    public void itemPickedUp(ItemPickedUp event) {
    	if (info.getSelf() == null) return; // ignore the first equipment...
    	Item pickedUp = items.getItem(event.getId());
    	if (pickedUp == null) return; // ignore unknown items
    	if (pickedUp.getType().getCategory() == Category.WEAPON) {
    		this.currentShortRangeWeapon = this.getShortRangeWeaponsScore(pickedUp);
    		this.currentMidRangeWeapon = this.getMidRangeWeaponsScore(pickedUp);
    		this.currentLongRangeWeapon = this.getLongRangeWeaponsScore(pickedUp);   		
    	}
    	
    	double spawnTime = 0;
   		if (pickedUp.getType() == UT2004ItemType.U_DAMAGE_PACK)
    			spawnTime = 27.5 * 3;
   		else
    			spawnTime = 27.5;
   		itemsSpawnTimes.put(event.getId(), spawnTime);
   		tcClient.sendToTeam(new TCItemSpawnTimeUpdate(event.getId(), spawnTime));
    	targetItem = null;
    	//log.info("PICKED " + itemCategory.name + ": " + itemType.getName() + " [group=" + itemGroup.getName() + "]");    	
    }
    
    /**
     * {@link IncomingProjectile} listener that senses that "some projectile has appeared OR moved OR disappeared".
     *
     * @param event
     */
    @ObjectClassEventListener(objectClass = IncomingProjectile.class, eventClass = WorldObjectUpdatedEvent.class)
    public void incomingProjectileUpdated(WorldObjectUpdatedEvent<IncomingProjectile> event) {
    	// Analytical geometry
    	Location projectileOrigin = event.getObject().getOrigin();
    	Location projectileDirection = new Location(event.getObject().getDirection()).getNormalized();
    	Location toEnemy = projectileOrigin.sub(info.getLocation()).getNormalized();
    	Location upVector = new Location(0.0f, 0.0f, 1.0f);
    	Location plane = toEnemy.cross(upVector).getNormalized().cross(upVector.scale(-1)).getNormalized();
    	
    	double d = info.getLocation().dot(plane);
    	double divider = projectileDirection.dot(plane);
    	
    	double p = (d -plane.dot(projectileOrigin)) / divider;
    	Location rocketIntersection = projectileOrigin.add(projectileDirection.scale(p));
    	Location botIntersection = rocketIntersection.sub(info.getLocation()).getNormalized();
    	
    	if (botIntersection == new Location(0, 0, 0))
    		botIntersection = new Location(25, 5, 0);
    		
    	// Find escape location based on the surroundings using raycast nav mesh
    	Vector2D[] escapeDirections = new Vector2D[] { 
    			new Vector2D(botIntersection.x, botIntersection.y), 
    			new Vector2D(botIntersection.x, -botIntersection.y),
    			new Vector2D(-botIntersection.x, botIntersection.y),
    			new Vector2D(-botIntersection.x, - botIntersection.y)
    	};
    	
    	Location escapeLocation = info.getLocation();
    	double bestUtility = Double.NEGATIVE_INFINITY;
    	
    	for (Vector2D escapeDir : escapeDirections) {
    		if (info.getLocation() == null)
    			return;
    		double utility = this.raycastNavMesh(info.getLocation(), escapeLocation);
    		utility = Math.min(utility, 250);
    		
    		if (utility > bestUtility) {
    			bestUtility = utility;
    			escapeLocation = new Location(escapeDir.getX(), escapeDir.getY(), 0.0f);
    			escapeLocation = info.getLocation().add(escapeLocation.getNormalized().scale(15));
    		}    		
    	}
    	this.getMove().turnTo(projectileOrigin);
    	this.getMove().dodgeTo(escapeLocation.sub(info.getLocation()), true);
    }
    
    /**
     * {@link Player} listener that senses that "some other bot has appeared OR moved OR disappeared"
     *
     * WARNING: this method will also be called during handshaking GB2004.
     *
     * @param event
     */
    @ObjectClassEventListener(objectClass = Player.class, eventClass = WorldObjectUpdatedEvent.class)
    public void playerUpdated(WorldObjectUpdatedEvent<Player> event) {
    	if (info.getLocation() == null) {
    		// HANDSHAKING GB2004
    		return;
    	}
    	Player player = event.getObject();    	
    	// DO NOT SPAM... uncomment for debug
    	//log.info("PLAYER UPDATED: " + player.getId().getStringId());
    }
        
    
    /**
     * {@link TeamScore} listener that senses changes within scoring.
     *
     * @param event
     */
    @ObjectClassEventListener(objectClass = TeamScore.class, eventClass = WorldObjectUpdatedEvent.class)
    public void teamScoreUpdated(WorldObjectUpdatedEvent<TeamScore> event) {
    	switch (event.getObject().getTeam()) {
    	case AgentInfo.TEAM_RED: 
    		//log.info("RED TEAM SCORE UPDATED: " + event.getObject());
    		break;
    	case AgentInfo.TEAM_BLUE:
    		//log.info("BLUE TEAM SCORE UPDATED: " + event.getObject());
    		break;
    	}
    }
    
    
    private long selfLastUpdateStartMillis = 0;
    private long selfTimeDelta = 0;
    
    /**
     * {@link Self} object has been updated. This update is received about every 50ms. You can use this update
     * to fine-time some of your behavior like "weapon switching". I.e. SELF is updated every 50ms while LOGIC is invoked every 250ms.
     * 
     * Note that during "SELF UPDATE" only information about your bot location/rotation ({@link Self}) is updated. All other visibilities 
     * remains the same as during last {@link #logic()}.
     * 
     * Note that new {@link NavMeshNavigation} is using SELF UPDATES to fine-control the bot's navigation.
     * 
     * @param event
     */
    @ObjectClassEventListener(objectClass = Self.class, eventClass = WorldObjectUpdatedEvent.class)
    public void selfUpdated(WorldObjectUpdatedEvent<Self> event) {
    	if (lastLogicStartMillis == 0) {
    		// IGNORE ... logic has not been executed yet...
    		return;
    	}
    	if (selfLastUpdateStartMillis == 0) {
    		selfLastUpdateStartMillis = System.currentTimeMillis();
    		return;
    	}
    	long selfUpdateStartMillis = System.currentTimeMillis(); 
    	selfTimeDelta = selfUpdateStartMillis  - selfLastUpdateStartMillis;
    	selfLastUpdateStartMillis = selfUpdateStartMillis;
    	//log.info("---[ SELF UPDATE | D: " + (selfTimeDelta) + "ms ]---");
    	
    	try {
    		
    		// YOUR CODE HERE
    		
    	} catch (Exception e) {
    		// MAKE SURE THAT YOUR BOT WON'T FAIL!
    		log.info(ExceptionToString.process(e));
    	} finally {
    		//log.info("---[ SELF UPDATE END ]---");
    	}
    	
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

    // ==============
    // MAIN BOT LOGIC
    // ==============
    
    /**
     * Method that is executed only once before the first {@link TDMBot#logic()} 
     */
    @SuppressWarnings("unused")
	@Override
    public void beforeFirstLogic() {
    	lastLogicStartMillis = System.currentTimeMillis();
    	if (DRAW_NAVMESH && botInstance == 0) {
    		boolean drawNavmesh = false;
    		synchronized(CLASS_MUTEX) {
    			if (!navmeshDrawn) {
    				drawNavmesh = true;
    				navmeshDrawn = true;
    			}
    		}
    		if (drawNavmesh) {
    			log.warning("!!! DRAWING NAVMESH !!!");
    			navMeshModule.getNavMeshDraw().draw(true, true);
    			navmeshDrawn  = true;
    			log.warning("NavMesh drawn, waiting a bit to finish the drawing...");
    		}    		
    	}
    	
    	navigation.addStrongNavigationListener(new FlagListener<NavigationState>() {
			@Override
			public void flagChanged(NavigationState changedValue) {
				navigationStateChanged(changedValue);
			}
        });
    }
    
    private long lastLogicStartMillis = 0;
    private long lastLogicEndMillis = 0;
    private long timeDelta = 0;
    
    /**
     * Main method that controls the bot - makes decisions what to do next. It
     * is called iteratively by Pogamut engine every time a synchronous batch
     * from the environment is received. This is usually 4 times per second.
     * 
     * This is a typical place from where you start coding your bot. Even though bot
     * can be completely EVENT-DRIVEN, the reactive aproach via "ticking" logic()
     * method is more simple / straight-forward.
     */
    @Override
    public void logic() {
    	long logicStartTime = System.currentTimeMillis();
    	try {
	    	// LOG VARIOUS INTERESTING VALUES
    		//logLogicStart();
	    	//logMind();
	    	
	    	// UPDATE TEAM COMM
	    	commItems.update();
	    	commObjectUpdates.update();
	    	
	    	// MAIN BOT LOGIC
	    	botLogic();
	    	
    	} catch (Exception e) {
    		// MAKE SURE THAT YOUR BOT WON'T FAIL!
    		log.info(ExceptionToString.process(e));
    		// At this point, it is a good idea to reset all state variables you have...
    		reset();
    	} finally {
    		// MAKE SURE THAT YOUR LOGIC DOES NOT TAKE MORE THAN 250 MS (Honestly, we have never seen anybody reaching even 150 ms per logic cycle...)
    		// Note that it is perfectly OK, for instance, to count all path-distances between you and all possible pickup-points / items in the game
    		// sort it and do some inference based on that.
    		long timeSpentInLogic = System.currentTimeMillis() - logicStartTime;
    		//log.info("Logic time:         " + timeSpentInLogic + " ms");
    		if (timeSpentInLogic >= 245) {
    			log.warning("!!! LOGIC TOO DEMANDING !!!");
    		}
    		//log.info("===[ LOGIC END ]===");
    		lastLogicEndMillis = System.currentTimeMillis();
    	}    	
    }
    
    public void botLogic() {
    	// RANDOM NAVIGATION
    	if (navigation.isNavigating()) {
    		if (DRAW_NAVIGATION_PATH) {
    			if (!navigationPathDrawn) {
    				drawNavigationPath(true);
    				navigationPathDrawn = true;
    			}
    		}
    		return;
    	}
    	
    	for (Player ply : players.getFriends().values())
    		log.info("LOC" + ply.getLocation());
    	
    	updateItemsSpawnTime();
    	sendEnemyInfo();
    	recalculateItemCost();
    	getTargetItem();
    	
    	if (engageInCombat())
    		return;
    	
    	navigateToItem();
    }
    
    public void reset() {
    	navigationPathDrawn = false;
    }
    
    // ===========
    // MIND LOGGER
    // ===========
    
    /**
     * It is good to log that the logic has started so you can then retrospectively check the batches.
     */
    public void logLogicStart() {
    	long logicStartTime = System.currentTimeMillis();
    	timeDelta = logicStartTime - lastLogicStartMillis;
    	//log.info("===[ LOGIC ITERATION | Delta: " + (timeDelta) + "ms | Since last: " + (logicStartTime - lastLogicEndMillis) + "ms]===");    		
    	lastLogicStartMillis = logicStartTime;    	
    }
    
    /**
     * It is good in-general to periodically log anything that relates to your's {@link TDMBot#logic()} decision making.
     * 
     * You might consider exporting these values to some custom Swing window (you crete for yourself) that will be more readable.
     */
    public void logMind() {
    	log.info("My health/armor:   " + info.getHealth() + " / " + info.getArmor() + " (low:" + info.getLowArmor() + " / high:" + info.getHighArmor() + ")");
    	log.info("My weapon:         " + weaponry.getCurrentWeapon());
    }
    
    // ============
    // ITEM PICKING
    // ============
    
    /*
     * 1. Get a list of all interesting items
     * 2. Assign cost for each one of the items. 
     * The cost depends on the distance, spawn time, general value of the item and value for the bot
     * 3. Send the costs to the team
     * 4. Choose the plan for the team item picking by minimizing the sum of costs (greedy approach, not enough time for optimal algorithm)
     */
   
    
    // MAP LOCKINGs   
    private boolean navigateToItem()
    {
    	if (targetItem == null)
    		return false;    	
    	
    	tcClient.sendToTeam(new TCTargetItem(info.getId(), targetItem.getId()));
    	navigateAStarPath(navPoints.getNearestNavPoint(targetItem));
    	log.info("Going for " + targetItem.getType().getName());
    	return true;
    }
   
    private void getTargetItem() {    	
    	UnrealId target = null;
    	int minCost = Integer.MAX_VALUE;
    	
    	if (itemsCost == null || itemsCost.get(info.getId()) == null || itemsCost.get(info.getId()).entrySet() == null)
    		recalculateItemCost();
    	
    	for (Entry<UnrealId, Integer> entry : itemsCost.get(info.getId()).entrySet()) {
    		if (entry.getValue() < minCost) {
    			minCost = entry.getValue();
    			target = entry.getKey();
    		}
    	} 
    	
    	tcClient.sendToTeam(new TCTargetItem(info.getId(), target));  	   	
    	
    	targetItem = items.getItem(target);
    }
   
    public Collection<Item> getInterestingItems() {
    	List<Item> interestingItems = new ArrayList<Item>();
    	
    	for (Item item : items.getSpawnedItems().values()) {
    		if (item.getType().getCategory() == Category.WEAPON ||
    			(item.getType().getCategory() == Category.ARMOR && info.getArmor() < 50)||
    			(item.getType().getCategory() == Category.SHIELD && info.getArmor() < 50) ||
    			(item.getType().getCategory() == Category.HEALTH && info.getHealth() < 80) ||
    			item.getType().getCategory() == Category.OTHER)
    			interestingItems.add(item);
    	}    	
    	return interestingItems;
    }
    
    // ITEM COST CALCULATION
    Map<UnrealId, Map<UnrealId, Integer>> itemsCost = new HashMap<UnrealId, Map<UnrealId, Integer>>();
    
    public int calculateItemCost(Item item) {
    	double distance = aStar.getDistance(navPoints.getNearestNavPoint(item.getLocation()), 
    										navPoints.getNearestNavPoint(bot.getLocation()));
    	int generalValue = 10*(10 - getItemValue(item));
    	int bonus = getBonusValue(item);
    	double spawnTime = 0;
    	if (itemsSpawnTimes.get(item.getId()) != null)
    		spawnTime = itemsSpawnTimes.get(item.getId());
    	
    	return (int) (distance + generalValue + bonus + spawnTime);
    }
    
    public void recalculateItemCost() {
    	for (Item item : getInterestingItems()) {
    		int cost = calculateItemCost(item);
    		this.tcClient.sendToTeam(new TCItemInfo(info.getId(), item.getId(), cost));
    	}
    }
  
    // SPAWN TIMES
    Map<UnrealId, Double> itemsSpawnTimes = new HashMap<UnrealId, Double>();
    
    private void updateItemsSpawnTime() {
	   List<UnrealId> spawnedItems = new ArrayList<UnrealId>();
	   
	   for (Entry<UnrealId, Double> item : itemsSpawnTimes.entrySet()) {
		   if (item.getValue() <= 0) {
			   spawnedItems.add(item.getKey());
			   continue;
		   }
		   item.setValue(item.getValue() - timeDelta/1000d);
	   }
	   
	   for (UnrealId item : spawnedItems)
		   itemsSpawnTimes.remove(item);
   }
    
    // COMMUNICATION
    
    // updates item fitness
    @EventListener(eventClass = TCItemInfo.class)
    public void tcItemInfo(TCItemInfo event) {
    	UnrealId item = event.what;
    	UnrealId who = event.who;
    	int cost = event.cost;
    	
    	if (itemsCost.containsKey(who))
    		itemsCost.get(who).put(item, cost);
    	else {
    		Map<UnrealId, Integer> fit = new HashMap<UnrealId, Integer>();
    		fit.put(item, cost);
    		itemsCost.put(who, fit);
    	}    	
    }
    
    @EventListener(eventClass = TCTargetItem.class)
    public void tcTargetItem(TCTargetItem event) {
    	UnrealId item = event.what;
    	
    	for (UnrealId bot : itemsCost.keySet()) {
    		itemsCost.get(bot).remove(item);
    	}
    	
    	if (targetItem != null && targetItem.getId().equals(item))
    		targetItem = null;
    }
    
    @EventListener(eventClass = TCItemSpawnTimeUpdate.class)
    public void tcItemSpawnTimeUpdate(TCItemSpawnTimeUpdate event) {
    	itemsSpawnTimes.put(event.what, event.spawnTime);	   	
    } 
 
    
    // HELPER FUNCTIONS
    
    public int getItemValue(Item item) {
    	int itemValue = 0;
    	if (item.getType().getCategory() == Category.WEAPON) {
    		int longRange = getLongRangeWeaponsScore(item);
    		int mediumRange = getMidRangeWeaponsScore(item);
    		int shortRange = getShortRangeWeaponsScore(item);
    		
    		itemValue = (longRange + mediumRange + shortRange) / 3;
    		return itemValue;
    	}
    	
    	if (item.getType().getCategory() == Category.HEALTH) {
    		itemValue = getHealthScore(item);
    		return itemValue;
    	}
    	
    	if (item.getType().getCategory() == Category.ARMOR || 
    		item.getType().getCategory() == Category.SHIELD || 
    		item.getType().getCategory() == Category.OTHER) {
    		itemValue = getUtilityItemsScore(item);
    		return itemValue;    		
    	} 		
    	
    	return 1;
    }
    
    public int getBonusValue(Item item) {
    	if (item.getType().getCategory() == Category.WEAPON) {
    		if (getWeaponRange(item) == WeaponRange.SHORT)
    			return 10*(10 - Math.max(0, getShortRangeWeaponsScore(item) - currentShortRangeWeapon));
    		if (getWeaponRange(item) == WeaponRange.MEDIUM) 
    			return 10*(10 - Math.max(0, getMidRangeWeaponsScore(item) - currentMidRangeWeapon));
    		if (getWeaponRange(item) == WeaponRange.LONG)
    			return 10*(10 - Math.max(0, getLongRangeWeaponsScore(item) - currentLongRangeWeapon));
    	}
    	
    	if (item.getType().getCategory() == Category.HEALTH || 
    		item.getType().getCategory() == Category.ARMOR || 
    		item.getType().getCategory() == Category.SHIELD) {
    		return (info.getHealth() + info.getArmor());
    	}
    	    	
    	return 10;
    }
    
    public WeaponRange getWeaponRange(Item item) {
    	if (item.getType() == UT2004ItemType.FLAK_CANNON ||
    		item.getType() == UT2004ItemType.ASSAULT_RIFLE ||
    		item.getType() == UT2004ItemType.LINK_GUN ||
    		item.getType() == UT2004ItemType.SHIELD_GUN)
    		return WeaponRange.SHORT;
    	
    	if (item.getType() == UT2004ItemType.SNIPER_RIFLE ||
    		item.getType() == UT2004ItemType.LIGHTNING_GUN)
    		return WeaponRange.LONG;
    	
    	return WeaponRange.MEDIUM;    	
    }

   
    // ============
    // ITEMS VALUES
    // ============
    
    enum WeaponRange {
    	SHORT,
    	MEDIUM,
    	LONG
    }
    
    private int getHealthScore(Item item) {
    	if(item.getType().equals(UT2004ItemType.MINI_HEALTH_PACK)) return 2;
    	if (item.getType().equals(UT2004ItemType.HEALTH_PACK)) return 5;
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
    	if (item.getType().equals(UT2004ItemType.U_DAMAGE_PACK)) return 12;
    	if (item.getType().equals(UT2004ItemType.SHIELD_PACK)) return 5;
    	if (item.getType().equals(UT2004ItemType.SUPER_SHIELD_PACK)) return 10;
    	
    	return 1;
    }
    
    // ================
    // COMBAT BEHAVIOUR
    // ================
    
    Map<UnrealId, ArrayList<UnrealId>> enemyTargets = new HashMap<UnrealId, ArrayList<UnrealId>>();
    
    private void sendEnemyInfo() {
    	for (Player enemy : players.getEnemies().values()) {
    		if (enemy.isVisible())
    			tcClient.sendToTeam(new TCEnemyInfo(info.getId(), enemy.getId(), true));
    		else
    			tcClient.sendToTeam(new TCEnemyInfo(info.getId(), enemy.getId(), false));
    	}
    }
    
    @EventListener(eventClass = TCEnemyInfo.class)
    public void tcEnemyInfo(TCEnemyInfo event) {
    	if (!event.visible) {
    		if (enemyTargets.containsKey(event.enemy) && enemyTargets.get(event.enemy).contains(event.player))
        		enemyTargets.remove(event.player);
    		return;
    	}
    	
    	if (!enemyTargets.containsKey(event.enemy)) {
	    	ArrayList<UnrealId> players = new ArrayList<UnrealId>();
	    	players.add(event.player);
	    	enemyTargets.put(event.enemy, players);
	    	return;
	    }
    	
	    if (!enemyTargets.get(event.enemy).contains(event.player)) {
	    	enemyTargets.get(event.enemy).add(event.player);
	    	return;
    	}    	
    } 
    
    private boolean engageInCombat() {
    	findEnemy();
    	if (pursueEnemy.isCool())
    		return false;
    	
    	if (!readyToFight())
    		return disengage();
    	
    	if (!players.canSeePlayers()) {
    		navigation.navigate(enemy);
    	}
    	
    	if (enemy.isVisible()) {
    		shootEnemy();
    	}
    	else 
    		shoot.stopShooting();
    	
    	return true;
	}
    
    private boolean disengage() { 	
    	if (visibility.isVisible(enemy)) {
    		move.turnTo(enemy);
    		shoot.shoot(this.getWeaponry().getWeapon(UT2004ItemType.SHIELD_GUN), false, enemy);	
    	}
    	
    	// Go to cover using cover path - commented out because it behaves oddly
    	//navigateAStarPath(getNearestCoverPoint(enemy));  	
    	    	
    	getTargetItem();
    	navigateToItem();
    	
		return false;
    }
    
    private void shootEnemy() {
    	if (lightCD.tryUse()) {
    		//log.info("Shoot lightning");
			shoot.shoot(weaponPrefs, enemy);
		}
		else {
			WeaponPref pref = this.getWeaponPrefs().getWeaponPreference(this.info.getLocation().getDistance(enemy.getLocation()));
			if (pref.getWeapon().equals(UT2004ItemType.ROCKET_LAUNCHER))
			{
				Location target = enemy.getLocation().sub(new Location(0, 0, 60));
				if (getLevelGeometry() != null && visibility.isVisible(info.getLocation(), target))
					//log.info("Shoot rocket");
					shoot.shoot(weaponPrefs, target, UT2004ItemType.LIGHTNING_GUN);
				else {
					//log.info("Shoot");
					shoot.shoot(weaponPrefs, enemy, UT2004ItemType.ROCKET_LAUNCHER, UT2004ItemType.LIGHTNING_GUN);
				}
			}
			//log.info("Shoot");
			shoot.shoot(weaponPrefs, enemy, UT2004ItemType.LIGHTNING_GUN);
		}
	}
     		
	private void findEnemy() {
    	int amount = -1;
    	
    	for (Player enemyTarget : players.getVisibleEnemies().values()) {
    		if (!enemyTargets.containsKey(enemyTarget.getId()))
    				continue;
    		if (enemyTargets.get(enemyTarget.getId()).size() > amount) {
    			amount = enemyTargets.get(enemyTarget.getId()).size();
    			enemy = enemyTarget;
    		}    		
    	}

    	if (enemy == null)
    		return;
    	
    	log.info("Combat " + enemy.getId());
    	pursueEnemy.heat();
    }
	
	
	private boolean readyToFight()
	{
		boolean suitableWeapon = true;
		double distance = info.getLocation().getDistance(enemy.getLocation());
		
		if ((distance <= 400 && this.currentShortRangeWeapon <= 5) || 
			(distance <= 1150 && this.currentMidRangeWeapon <= 5) ||
			 this.currentLongRangeWeapon <= 5)
			suitableWeapon = false;
		
		if (!suitableWeapon && info.getHealth() <= CAREFUL && 
		   (!enemyTargets.containsKey(enemy.getId()) || enemyTargets.get(enemy.getId()).size() < 1)) {
			log.info("This combat doesn't look good");
			return false;
		}
		
		return true;
	}
    
    // =====================================
    // UT2004 DEATH-MATCH INTERESTING GETTERS
    // ======================================
    
    /**
     * Returns path-nearest {@link NavPoint} that is covered from 'enemy'. Uses {@link UT2004BotModuleController#getVisibility()}.
     * @param enemy
     * @return
     */
    public NavPoint getNearestCoverPoint(Player enemy) {
    	if (!visibility.isInitialized()) {
    		log.warning("VISIBILITY NOT INITIALIZED: returning random navpoint");    		
    		return MyCollections.getRandom(navPoints.getNavPoints().values());
    	}
    	List<NavPoint> coverPoints = new ArrayList<NavPoint>(visibility.getCoverNavPointsFrom(enemy.getLocation()));
    	return fwMap.getNearestNavPoint(coverPoints, info.getNearestNavPoint());
    }
    
    /**
     * Returns whether 'item' is possibly spawned (to your current knowledge).
     * @param item
     * @return
     */
    public boolean isPossiblySpawned(Item item) {
    	return items.isPickupSpawned(item);
    }
    
    /**
     * Returns whether you can actually pick this 'item', based on "isSpawned" and "isPickable" in your current state and knowledge.
     */
    public boolean isCurrentlyPickable(Item item) {
    	return isPossiblySpawned(item) && items.isPickable(item);
    }
        
    // ==========
    // RAYCASTING
    // ==========
    
    /**
     * Performs a client-side raycast against UT2004 map geometry.
     * 
     * It is not sensible to perform more than 1000 raycasts per logic() per bot.
     *  
     * NOTE THAT IN ORDER TO USE THIS, you have to rename "map_" folder into "map" ... so it would load the level geometry.
     * Note that loading a level geometry up takes quite a lot of time (>60MB large BSP tree...). 
     *  
     * @param from
     * @param to
     * @return
     */
    public RayCastResult raycast(ILocated from, ILocated to) {
    	if (!levelGeometryModule.isInitialized()) {
    		throw new RuntimeException("Level Geometry not initialized! Cannot RAYCAST!");
    	}
    	return levelGeometryModule.getLevelGeometry().rayCast(from.getLocation(), to.getLocation());
    }
    
    /**
     * Performs a client-side raycast against NavMesh in 'direction'. Returns distance of the edge in given 'direction' sending the ray 'from'.
     * @param from
     * @param escapeLocation
     * @return
     */
    public double raycastNavMesh(ILocated from, Location direction) {
		if (!navMeshModule.isInitialized()) return 0;
		try {
			ClearanceLimit limit = clearanenceNavMesh(from.getLocation(), direction);
			if (limit == null) return Double.POSITIVE_INFINITY;
			return from.getLocation().getDistance(limit.getLocation()); 
		} 
		catch (Exception e) {
			this.getLog().warning("Raycast Nav Mesh issues", e);
			return 0;
		}
	}
    
    public ClearanceLimit clearanenceNavMesh(ILocated location, Location direction) {
    	return clearanenceNavMesh(location, direction, 1000);
    }
    
    public ClearanceLimit clearanenceNavMesh(ILocated location, Location direction, double maxDistance) {
    	return navMeshModule.getClearanceComputer().findEdge(location.getLocation(), new Vector2D(direction.x, direction.y), maxDistance, 1000);
    }
    
    // =======
    // DRAWING
    // =======
    
    public void drawNavigationPath(boolean clearAll) {
    	if (clearAll) {
    		draw.clearAll();
    	}
    	List<ILocated> path = navigation.getCurrentPathCopy();
    	for (int i = 1; i < path.size(); ++i) {
    		draw.drawLine(path.get(i-1), path.get(i));
    	}
    }
    
    public void drawPath(IPathFuture<? extends ILocated> pathFuture, boolean clearAll) {
    	if (clearAll) {
    		draw.clearAll();
    	}
    	List<? extends ILocated> path = pathFuture.get();
    	for (int i = 1; i < path.size(); ++i) {
    		draw.drawLine(path.get(i-1), path.get(i));
    	}
    }
    
    public void drawPath(IPathFuture<? extends ILocated> pathFuture, Color color, boolean clearAll) {
    	if (clearAll) {
    		draw.clearAll();
    	}
    	if (color == null) color = Color.WHITE;
    	List<? extends ILocated> path = pathFuture.get();
    	for (int i = 1; i < path.size(); ++i) {
    		draw.drawLine(color, path.get(i-1), path.get(i));
    	}
    }
    
    // =====
    // AStar
    // =====
    
    private NavPoint lastAStarTarget = null;
    
    public boolean navigateAStarPath(NavPoint targetNavPoint) {
        if (lastAStarTarget == targetNavPoint) {
            if (navigation.isNavigating()) return true;
        }
        PrecomputedPathFuture<ILocated> path = getAStarPath(targetNavPoint);
        if (path == null) {
            navigation.stopNavigation();
            return false;
        }
        lastAStarTarget = targetNavPoint;
        navigation.navigate(path, true);
        return true;
    }
    
    private IPFMapView<NavPoint> mapView = new IPFMapView<NavPoint>() {

        @Override
        public Collection<NavPoint> getExtraNeighbors(NavPoint node, Collection<NavPoint> mapNeighbors) {
            return null;
        }

        @Override
        public int getNodeExtraCost(NavPoint node, int mapCost) {
        	int cost = 0;
        	
        	for (Player enemy : players.getVisibleEnemies().values()) {
        		int extraCost = (int) (enemy.getLocation().getDistance(node.getLocation())); 
        		cost += extraCost;
        	}        	
            return cost;
        }

        @Override
        public int getArcExtraCost(NavPoint nodeFrom, NavPoint nodeTo, int mapCost) {
            return 0;
        }

        @Override
        public boolean isNodeOpened(NavPoint node) {
            return true;
        }

        @Override
        public boolean isArcOpened(NavPoint nodeFrom, NavPoint nodeTo) {
            return true;
        }
    };
    
    private PrecomputedPathFuture<ILocated> getAStarPath(NavPoint targetNavPoint) {
        NavPoint startNavPoint = info.getNearestNavPoint();
        AStarResult<NavPoint> result = aStar.findPath(startNavPoint, targetNavPoint, mapView);
        if (result == null || !result.isSuccess()) return null;
        PrecomputedPathFuture path = new PrecomputedPathFuture(startNavPoint, targetNavPoint, result.getPath());
        return path;
    }
    
    // ===========
    // MAIN METHOD
    // ===========
    
    /**
     * Main execute method of the program.
     * 
     * @param args
     * @throws PogamutException
     */
    public static void main(String args[]) throws PogamutException {
    	// Starts N agents of the same type at once
    	// WHEN YOU WILL BE SUBMITTING YOUR CODE, MAKE SURE THAT YOU RESET NUMBER OF STARTED AGENTS TO '1' !!!
    	// => during the development, please use {@link Starter_Bots} instead to ensure you will leave "1" in here
    	new UT2004BotRunner(TDMBot.class, "TDMBot").setMain(true).setLogLevel(Level.INFO).startAgents(1);
    }
    
}
