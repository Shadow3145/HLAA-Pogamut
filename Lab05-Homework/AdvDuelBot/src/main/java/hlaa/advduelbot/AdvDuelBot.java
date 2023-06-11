package hlaa.advduelbot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import cz.cuni.amis.pathfinding.alg.astar.AStarResult;
import cz.cuni.amis.pathfinding.map.IPFMapView;
import cz.cuni.amis.pogamut.base.agent.navigation.IPathFuture;
import cz.cuni.amis.pogamut.base.agent.navigation.impl.PrecomputedPathFuture;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.ObjectClassEventListener;
import cz.cuni.amis.pogamut.base.communication.worldview.object.event.WorldObjectUpdatedEvent;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.base3d.worldview.object.Velocity;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.ManualControl;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.WeaponPref;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.visibility.model.VisibilityLocation;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.UT2004Skins;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.NavigationState;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.levelGeometry.RayCastResult;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.navmesh.NavMeshClearanceComputer.ClearanceLimit;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.navmesh.node.NavMeshPolygon;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UT2004ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType.Category;
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
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.Cooldown;
import cz.cuni.amis.utils.Heatup;
import cz.cuni.amis.utils.exception.PogamutException;
import cz.cuni.amis.utils.flag.FlagListener;
import hlaa.advduelbot.ItemInfo;
import math.geom2d.Vector2D;

@AgentScoped
public class AdvDuelBot extends UT2004BotModuleController {

	public static final boolean MANUAL_CONTROL = false;
	
	public static final boolean LOAD_LEVEL_GEOMETRY = true;
	
	private long   lastLogicTime        = -1;
    private long   logicIterationNumber = 0; 
    
    private boolean lastManualActive = false;
    private boolean clearDraw = false;
    
    private ManualControl manualControl;
    
    // Weapon levels
    private int currentShortRangeWeapon = 1;
    private int currentMidRangeWeapon = 1;
    private int currentLongRangeWeapon = 1;
    
    // Combat behaviours heatups and cooldown
    Cooldown lightCD = new Cooldown(2000);
    Heatup pursueEnemy = new Heatup(3000);
    
    Player enemy = null;
    
    // Combat constants
    private final int HEALTH_PACKS = 50;
    private final int FLEE = 35;
    private final int CAREFUL = 50;
    
    //Navigation
    PrecomputedPathFuture<ILocated> currentPath = null;
    private boolean rocketCoverFire = false;
    
    @Override
    protected void initializeModules(UT2004Bot bot) {
    	super.initializeModules(bot);
    	levelGeometryModule.setAutoLoad(LOAD_LEVEL_GEOMETRY);
    }
    
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
    	if (MANUAL_CONTROL) {
    		log.warning("INITIALIZING MANUAL CONTROL WINDOW");
    		manualControl = new ManualControl(bot, info, body, levelGeometryModule, draw, navPointVisibility, navMeshModule);
    	}
    	
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
    	navigation.addStrongNavigationListener(new FlagListener<NavigationState>() {
			@Override
			public void flagChanged(NavigationState changedValue) {
				navigationStateChanged(changedValue);
			}
        });
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
    	
    	// MANUAL CONTROL
    	if (manualControl != null && manualControl.isActive()) {
    		if (!lastManualActive) {
    			setDebugInfo("MANUAL CONTROL");
        		lastManualActive = true;
    		}
    		lastLogicTime = System.currentTimeMillis();
    		return;
    	} else {
    		if (lastManualActive) {
    			lastManualActive = false;
    			setDebugInfo(null);
    		}
    	}

    	log.info("---LOGIC: " + (++logicIterationNumber) + " / D=" + (System.currentTimeMillis() - lastLogicTime) + "ms ---");
    	lastLogicTime = System.currentTimeMillis();

    	// FOLLOWS THE BOT'S LOGIC
    	if (engageInCombat())
    		return;
    	shoot.stopShooting();
    	if (clearDraw) {
    		draw.clearAll();
    		clearDraw = false;
    	}
    	if (pickItems())
    		return;  	
    	rocketCoverFire = false;
	    if(!navigation.isNavigating()) {
	        navigation.navigate(navPoints.getRandomNavPoint());	  
	        dodgeMovement();
	    }
    	// use Bot Name to visualize high-level state of your bot to ease debugging
    	setDebugInfo("BRAIN-DEAD");
    }
    // ========
    // MOVEMENT
    // ========
    
    /* Supported movement behaviour
     * 1. Navigating for items (04 Base)
     * 2. Dodge movement (05 Advanced) 
     * 3. Safe projectile dodging (05 Advanced) 
     */
    
    private void dodgeMovement() { 	
    	if (!navigation.isNavigating())
    		return;
    	
    	if (info.getVelocity().equals(Velocity.ZERO))
    		return;
    	
    	Location current = info.getLocation();
    	double distance = raycastNavMesh(current, info.getVelocity().asLocation());
    	
    	Location dodgeTo = info.getVelocity().asLocation();
    	//log.info("DODGE TO " + dodgeTo.toString());
       	
    	double height = Math.abs(dodgeTo.getLocation().z);
    	
    	
    	if ((Double.isInfinite(distance) || distance > 500) && height < 100) {
    		this.move.dodge(dodgeTo, false);
    		log.info("Dodging to location");
    		this.getDraw().drawLine(Color.ORANGE, current, dodgeTo);
    		clearDraw = true;
    	}
    }
    
    private void dodgeProjectile(WorldObjectUpdatedEvent<IncomingProjectile> projectile) {
    	if (rocketCoverFire)
    		return;
    	
    	// Analytical geometry
    	Location projectileOrigin = projectile.getObject().getOrigin();
    	Location projectileDirection = new Location(projectile.getObject().getDirection()).getNormalized();
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
    
    // ======
    // COMBAT
    // ======
    
    /* Supported combat behaviour
     * 1. Use weapons well suited for the range (04 Base)
     * 2. Lightning gun shooting (04 Advanced)
     * 3. Pursue behaviour (04 Advanced)
     * 4. Disengage (04 + 05)
     * 		- Medkit behaviour (04 Advanced)
     * 		- Disengage in cover and get item (05 Base) 
     *      - Use shield gun while disengaging (05 Base) 
     * 5. Safe rocket shooting (05 Base) 
     */
    
    private boolean engageInCombat() {
    	findEnemy();
    	if (pursueEnemy.isCool())
    		return false;
    	
    	if (info.getHealth() <= FLEE) {
    		fleeAndFindMedkit();
    		return true;
    	}
    	
    	if (!readyToFight())
    		return disengage();
    	
    	if (!players.canSeePlayers() || currentShortRangeWeapon > currentLongRangeWeapon) {
    		navigation.navigate(enemy);
    		dodgeMovement();
    	}
    	
    	if (enemy.isVisible()) {
    		shootEnemy();
    	}
    	else
    		shoot.stopShooting();
    	
    	return true;
	}
    
    private boolean disengage() {
    	log.info("Disengaging");	
    	log.info("Finding nearest useful item");
    	Collection<ItemInfo> items = getAllInterestingItems();
    	ItemInfo item = this.getBestItem(items);
    	
    	    	
    	if (visibility.isVisible(enemy)) {
    		move.turnTo(enemy);
    		this.shoot.shoot(this.getWeaponry().getWeapon(UT2004ItemType.SHIELD_GUN), false, enemy);	
    	}
    	navigateAStarPath(item.getItem().getNavPoint(), false);
    	drawNavigationPath(true);
    	clearDraw = true;
    	
    	dodgeMovement();
    	
		return true;
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
				if (this.getLevelGeometry() != null && isVisible(info.getLocation(), target))
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
     
	private void fleeAndFindMedkit() {
		shoot.stopShooting();
		log.info("Running for medkit");
		ItemInfo medkit = getBestHealthPack();
		
		if (visibility.isVisible(enemy)) {
			move.turnTo(enemy);
			shoot.shoot(getWeaponry().getWeapon(UT2004ItemType.SHIELD_GUN), false, enemy);
		}
				
		navigateAStarPath(medkit.getItem().getNavPoint(), false);
		drawNavigationPath(true);
		clearDraw = true;
		
		dodgeMovement();
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
    
    /* Supported item collecting behaviour
     * 1. Reasoning about utility of items to pickup (04 Base)
     * 2. Rocket cover fire (05 Advanced) 
     */
    
	private Collection<ItemInfo> getAllInterestingItems() {
    	List<ItemInfo> result = new ArrayList<ItemInfo>();
    	
    	for (Item item : items.getSpawnedItems().values()) {
    		if (item.getType().getCategory() == Category.WEAPON) {
    			double value = 1;
    			if (!weaponry.hasLoadedWeapon(item.getType()))
    				value = Math.max(this.getShortRangeWeaponsScore(item), Math.max(this.getMidRangeWeaponsScore(item), this.getLongRangeWeaponsScore(item)));
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
 
	// TO TEST: Implement rocket cover fire
    private boolean navigateToItem(ItemInfo targetItem)
    {
    	if (targetItem == null)
    		return false;    	
    	
    	if (isVisible(targetItem.getItem(), info.getLocation()) && targetItem.getValue() >= 5 && 
    		this.weaponry.getAmmo(UT2004ItemType.ROCKET_LAUNCHER_AMMO) > 8 && 
    		targetItem.getItem().getLocation().getDistance(info.getLocation()) > 500 &&
    		targetItem.getItem().getLocation().getDistance(info.getLocation()) < 1150.0) {
    		log.info("Rocket cover fire");
    		rocketCoverFire = true;
    		shoot.shoot(this.weaponry.getWeapon(UT2004ItemType.ROCKET_LAUNCHER), false, targetItem.getItem());
    	}
    	
    	navigateAStarPath(navPoints.getNearestNavPoint(targetItem.getItem()), false);
    	dodgeMovement();
    	log.info("Going for " + targetItem.getItem().getType().getName());
    	return true;
    }
    
    private boolean pickItems() {
    	ItemInfo targetItem = null; 
    	
    	if (info.getHealth() <= this.HEALTH_PACKS) {
    		targetItem = getBestHealthPack();
    	}
    	
    	if (this.currentShortRangeWeapon < 5) {
    		targetItem = getBestShortToMidWeapon();
    	}
    	
    	if (this.currentMidRangeWeapon < 5 || this.currentLongRangeWeapon < 5) {
    		targetItem = getBestMidToLongWeapon();
    	}
    	
    	if (info.getArmor() < 50) {
    		targetItem = getBestShield();
    	}
    	
    	targetItem = getBestItem(getAllInterestingItems());
    	return navigateToItem(targetItem);
    }
    
	private ItemInfo getBestItem(Collection<ItemInfo> items) {
		return DistanceUtils.getNearest(
				items,
				info.getLocation(),
				new DistanceUtils.IGetDistance<ItemInfo>() {
					@Override
					public double getDistance(ItemInfo object, ILocated target) {
						double distance = aStar.findPath(navPoints.getNearestNavPoint(object.getItem().getLocation()), navPoints.getNearestNavPoint(target), mapView).getDistanceToGoal();
						return distance / object.getValue();
					}
				});		
	}

	private ItemInfo getBestShield() {
		List<ItemInfo> shields = new ArrayList<ItemInfo>();
		
		for (Item item : items.getSpawnedItems().values()) {
			if (item.getType().getCategory() == Category.ARMOR ||
				item.getType().getCategory() == Category.SHIELD)
				shields.add(new ItemInfo(item, this.getUtilityItemsScore(item)));
		}
		if (shields.isEmpty())
			return null;
		return getBestItem(shields);		
	}

	private ItemInfo getBestMidToLongWeapon() {
		List<ItemInfo> weapons = new ArrayList<ItemInfo>();
		
		for (Item item : items.getSpawnedItems().values()) {
			if (item.getType().getCategory() == Category.WEAPON) {
				int value = getLongRangeWeaponsScore(item);
				if (value > 1 && !weaponry.hasLoadedWeapon(item.getType())) 
					weapons.add(new ItemInfo(item, value));					
			}
		}
		if (weapons.isEmpty())
			return null;
		
		return getBestItem(weapons);		
	}

	private ItemInfo getBestShortToMidWeapon() {
		List<ItemInfo> weapons = new ArrayList<ItemInfo>();
		
		for (Item item : items.getSpawnedItems().values()) {
			if (item.getType().getCategory() == Category.WEAPON) {
				int value = getShortRangeWeaponsScore(item);
				if (value > 1 && !weaponry.hasLoadedWeapon(item.getType())) 
					weapons.add(new ItemInfo(item, value));					
			}
		}
		if (weapons.isEmpty())
			return null;
		return getBestItem(weapons);				
	}

	private ItemInfo getBestHealthPack() {
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
		return getBestItem(healthPacks);
		
	}
    
    // ===============
    // SENSORY METHODS
    // ===============
    
    /**
     * Tries to find a navmesh under given "location".
     * @param location
     * @return
     */
    public Location getNavMeshLocation(ILocated location) {
    	if (location == null || location.getLocation() == null) return null;
    	NavMeshPolygon nmPoly = navMeshModule.getDropGrounder().tryGround(location);
    	if (nmPoly == null) return null;
    	Location nmLoc = new Location(nmPoly.getShape().project(location.getLocation().asPoint3D()));
    	return nmLoc;    	
    }
    
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

    
    /**
     * Beware, returns NULL on "no-hit".
     * @param location
     * @param direction
     * @return
     */
    public ClearanceLimit clearanenceNavMesh(ILocated location, Location direction) {
    	return clearanenceNavMesh(location, direction, 1000);
    }
    
    /**
     * Beware, returns NULL on "no-hit".
     * @param location
     * @param direction
     * @return
     */
    public ClearanceLimit clearanenceNavMesh(ILocated location, Location direction, double maxDistance) {
    	return navMeshModule.getClearanceComputer().findEdge(location.getLocation(), new Vector2D(direction.x, direction.y), maxDistance, 1000);
    }
    
    public RayCastResult raycastGeom(ILocated from, ILocated to) {
    	if (from == null || from.getLocation() == null || to == null || to.getLocation() == null) return null;
    	return levelGeometryModule.getLevelGeometry().rayCast(from.getLocation(), to.getLocation());
    }
    
    public boolean isVisible(ILocated from, ILocated to) { 
    	if (from == null || from.getLocation() == null || to == null || to.getLocation() == null) return false;
    	return visibility.isVisible(from, to);
    }
    
    public double getVisibleEpsilon(ILocated from, ILocated to) {
    	if (from == null || from.getLocation() == null || to == null || to.getLocation() == null) return Double.POSITIVE_INFINITY;
    	VisibilityLocation fromVL = visibility.getNearestVisibilityLocationTo(from);
    	VisibilityLocation toVL = visibility.getNearestVisibilityLocationTo(to);
    	return fromVL.getLocation().sub(from.getLocation()).getLength() + toVL.getLocation().sub(to.getLocation()).getLength();
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
    	log.info("I was damaged, I'm running to cover");
    	if (enemy != null)
    		return;
    	enemy = info.getNearestPlayer();
    	NavPoint cover = visibility.getNearestCoverNavPointFrom(enemy);
    	navigation.navigate(cover);
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
        clearDraw = false;
        draw.clearAll();
        
        //fleeFromEnemy.clear();
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
    	log.info("Dodging rocket");
    	this.dodgeProjectile(event);
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
    
    public boolean navigateAStarPath(NavPoint targetNavPoint, boolean forceRecomputeAndRestart) {
        if (!forceRecomputeAndRestart && lastAStarTarget == targetNavPoint) {
            if (navigation.isNavigating()) return true;
        }
        PrecomputedPathFuture<ILocated> path = getAStarPath(targetNavPoint);
        if (path == null) {
            navigation.stopNavigation();
            return false;
        }
        currentPath = path;
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
        	if (enemy == null)
        		return 0;
        	int extraCost = (int) (5000 / (enemy.getLocation().getDistance(node.getLocation())));
        	if (isVisible(node, enemy))
        		return extraCost;
            return 0;
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

    // =========
    // UTILITIES
    // =========
    
    private void setDebugInfo(String info) {
    	bot.getBotName().setInfo(info);
    	log.info(info);
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
    	if (item.getType().equals(UT2004ItemType.U_DAMAGE_PACK)) return 15;
    	if (item.getType().equals(UT2004ItemType.SHIELD_PACK)) return 4;
    	if (item.getType().equals(UT2004ItemType.SUPER_SHIELD_PACK)) return 8;
    	
    	return 1;
    }
    
    // ===========
    // MAIN METHOD
    // ===========
    
    public static void main(String args[]) throws PogamutException {
        new UT2004BotRunner(     // class that wrapps logic for bots executions, suitable to run single bot in single JVM
                AdvDuelBot.class,   // which UT2004BotController it should instantiate
                "AdvDuelBot"        // what name the runner should be using
        ).setMain(true)          // tells runner that is is executed inside MAIN method, thus it may block the thread and watch whether agent/s are correctly executed
         .startAgents(1);        // tells the runner to start 1 agent
    }
}
