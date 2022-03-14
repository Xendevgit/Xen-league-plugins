package net.runelite.client.plugins.LXenBarrows;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.LXenBarrows.LXenBarrowsConfig;
import net.runelite.client.plugins.LXenBarrows.LXenBarrowsState;
import net.runelite.client.plugins.iutils.*;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.MouseEvent;

import static net.runelite.api.MenuAction.*;

@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
		name = "XenLeague - Barrows",
		description = "plugin template description",
		tags = {"tags"},
		enabledByDefault = false
)
@Slf4j
public class LXenBarrowsPlugin extends Plugin {

	@Inject
	private Client client;

	@Inject
	private iUtils utils;

	@Inject
	private MouseUtils mouse;

	@Inject
	private PlayerUtils playerUtils;

	@Inject
	private InventoryUtils inventory;

	@Inject
	private InterfaceUtils interfaceUtils;

	@Inject
	private CalculationUtils calc;

	@Inject
	private MenuUtils menu;

	@Inject
	private ObjectUtils object;

	@Inject
	private BankUtils bank;

	@Inject
	private NPCUtils npc;

	@Inject
	private WalkUtils walk;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private LXenBarrowsConfig config;

	@Inject
	private LXenBarrowsOverlay overlay;

	public LegacyMenuEntry entry;
	private Player player;


	private boolean start;
	LXenBarrowsState state;
	Instant timer;
	int timeout;
	long sleepLength;

	private boolean looted;

	private final WorldArea chestArea = new WorldArea(new WorldPoint(3546, 9689, 0), (new WorldPoint(3558, 9701, 0)));
	private final WorldArea veracDigArea = new WorldArea(new WorldPoint(3554, 3296, 0), new WorldPoint(3558, 3300, 0));
	private final WorldArea veracCoffinArea = new WorldArea(new WorldPoint(3567, 9701, 3), new WorldPoint(3581, 9712, 3));
	private final WorldPoint veracDigPoint = new WorldPoint(3556, 3298, 0);

	@Provides
	LXenBarrowsConfig provideConfig(ConfigManager configManager) {
		return (LXenBarrowsConfig) configManager.getConfig(LXenBarrowsConfig.class);
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked) {
		if (!configButtonClicked.getGroup().equalsIgnoreCase("LXenBarrowsConfig")) {
			return;
		}
		switch (configButtonClicked.getKey()) {
			case "startButton":
				if (!start) {
					start = true;
					player = client.getLocalPlayer();
					overlayManager.add(overlay);
					timer = Instant.now();
					looted = false;
				} else {
					resetVals();
				}

				break;
		}
	}

	@Override
	protected void startUp() throws Exception {

	}

	@Override
	protected void shutDown() throws Exception {
		resetVals();
	}

	private void resetVals() {
		start = false;
		player = null;
		overlayManager.remove(overlay);
		timer = null;
		looted = false;
	}

	@Subscribe
	private void onGameTick(GameTick event) {
		if (start) {
			state = getState();
			switch (state) {
				case TIMEOUT:
					timeout--;
					break;
				case MOVING:
				case WAIT_COMBAT:
					timeout = tickDelay();
					break;
				case TELEPORT_BARROWS:
					teleportBarrows();
					timeout = 4 + tickDelay();
					break;
				case MOVE_TO_HILL:
					moveToHill();
					timeout = tickDelay();
					break;
				case DIG:
					digSpot();
					timeout = tickDelay();
					break;
				case ACTIVATE_PRAYER:
					activatePrayer(WidgetInfo.PRAYER_PROTECT_FROM_MELEE);
					timeout = tickDelay();
					break;
				case OPEN_SARCHOPHAGUS:
					openCoffin();
					timeout = tickDelay();
					break;
				case ATTACK:
					attackBrother();
					timeout = tickDelay();
					break;
				case RECALL_CHEST:
					teleportRecall();
					timeout = 4 + tickDelay();
					break;
				case OPEN_CHEST:
					openChest();
					timeout = tickDelay();
					break;
				case LOOT_CHEST:
					lootChest();
					timeout = tickDelay();
					break;
				case TELEPORT_HOUSE:
					teleportConstructionCape();
					timeout = tickDelay();
					break;
				case USE_POOL:
					useRestorePool();
					timeout = tickDelay();
					break;
				case UNHANDLED_STATE:
					//log.info("LXenBarrows: unhandled state - stopping");
					//resetVals();
					break;
			}
		}
	}

	@Subscribe
	private void onWidgetLoaded(WidgetLoaded event) {
		if (start) {
			if (event.getGroupId() == 155) {
				looted = true;
			}
		}
	}

	private LXenBarrowsState getState() {
		if (timeout > 0) {
			playerUtils.handleRun(20, 20);
			return LXenBarrowsState.TIMEOUT;
		}

		if (playerUtils.isMoving()) {
			return LXenBarrowsState.MOVING;
		}

		if (isInHouse()) {
			if (isPrayerActive(Prayer.PROTECT_FROM_MELEE.getVarbit())) {
				return LXenBarrowsState.ACTIVATE_PRAYER;
			}
			if (needToRestoreStats()) {
				return LXenBarrowsState.USE_POOL;
			}
			return LXenBarrowsState.TELEPORT_BARROWS;
		}

		if (isInChestArea()) {
			if (looted) {
				return LXenBarrowsState.TELEPORT_HOUSE;
			}
			if (isNPCSpawned(NpcID.VERAC_THE_DEFILED)) {
				if (isInCombat()) {
					return LXenBarrowsState.WAIT_COMBAT;
				}
				return LXenBarrowsState.ATTACK;
			}
			return LXenBarrowsState.OPEN_CHEST;
		}

		if (isInArea(veracCoffinArea)) {
			if (hiddenTunnelMessage()) {
				return LXenBarrowsState.RECALL_CHEST;
			}
			if (!isPrayerActive(Prayer.PROTECT_FROM_MELEE.getVarbit())) {
				return LXenBarrowsState.ACTIVATE_PRAYER;
			}
			if (isNPCSpawned(NpcID.VERAC_THE_DEFILED)) {
				if (isInCombat()) {
					return LXenBarrowsState.WAIT_COMBAT;
				}
				return LXenBarrowsState.ATTACK;
			}
			if (isVeracDead()) {
				return LXenBarrowsState.RECALL_CHEST;
			}
			return LXenBarrowsState.OPEN_SARCHOPHAGUS;
		}

		if (isAtBarrows()) {
			if (isInArea(veracDigArea)) {
				return LXenBarrowsState.DIG;
			}
			return LXenBarrowsState.MOVE_TO_HILL;
		}


		return LXenBarrowsState.UNHANDLED_STATE;
	}

	private boolean hiddenTunnelMessage() {
		Widget dialogWidget = client.getWidget(WidgetInfo.DIALOG_NOTIFICATION_CONTINUE);
		if (dialogWidget != null && !dialogWidget.isHidden()) {
			dialogWidget.getText().contains("hidden tunnel");
			return true;
		}
		return false;
	}

	private boolean needToRestoreStats() {
		return client.getBoostedSkillLevel(Skill.HITPOINTS) < client.getRealSkillLevel(Skill.HITPOINTS) ||
				client.getBoostedSkillLevel(Skill.PRAYER) < client.getRealSkillLevel(Skill.PRAYER);
	}

	private boolean isInHouse() {
		return object.findNearestGameObject(4525) != null;
	}

	private boolean isChestClosed() {
		return object.findNearestGameObject(20973) != null; //20973
		//Target = <col=ffff>Chest, Type = 20973, Opcode = GAME_OBJECT_FIRST_OPTION, actionParam = 23, actionParam1 = 39
		//location: 3551, 9695
	}

	private boolean isVeracDead() {
		return client.getVar(Varbits.BARROWS_KILLED_VERAC) == 1;
	}

	private boolean isInChestArea() {
		return isInArea(chestArea);
	}

	private boolean isAtBarrows() {
		return client.getLocalPlayer().getWorldLocation().getRegionID() == 14131;
	}

	private boolean isInArea(WorldArea area) {
		return client.getLocalPlayer().getWorldArea().intersectsWith(area);
	}

	private boolean isPrayerActive(Varbits prayerVarbit) {
		return client.getVar(prayerVarbit) == 1;
	}

	private boolean isNPCSpawned(int npcID) {
		return npc.findNearestNpc(npcID) != null;
	}

	private boolean isInCombat() {
		return npc.getFirstNPCWithLocalTarget() != null && client.getLocalPlayer().getInteracting() != null;
	}

	private void openChest() {
		GameObject closedChest = object.findNearestGameObject(20973);
		if (closedChest != null) {
			clickGameObject(closedChest, GAME_OBJECT_FIRST_OPTION.getId());
		}
	}

	private void lootChest() {
		GameObject openChest = object.findNearestGameObject(20973);
		if (openChest != null) {
			clickGameObject(openChest, GAME_OBJECT_FIRST_OPTION.getId());
		}
	}

	private void teleportBarrows() {
		looted = false;
		castSpell(WidgetInfo.SPELL_BARROWS_TELEPORT);
	}

	private void moveToHill() {
		walk.sceneWalk(veracDigPoint, 1, sleepDelay());
	}

	private void digSpot() {
		clickInventoryItem(ItemID.SPADE, ITEM_FIRST_OPTION.getId());
	}

	private void openCoffin() {
		GameObject coffin = object.findNearestGameObject(ObjectID.SARCOPHAGUS_20772);
		if (coffin != null) {
			clickGameObject(coffin, GAME_OBJECT_FIRST_OPTION.getId());
		}
	}

	private void attackBrother() {
		NPC verac = npc.findNearestNpc(NpcID.VERAC_THE_DEFILED); //targetingLocal?
		if (verac != null) {
			clickNPC(verac, NPC_SECOND_OPTION.getId());
		}
	}


	private void teleportRecall() {
		if (inventory.containsItem(ItemID.CRYSTAL_OF_MEMORIES)) {
			clickInventoryItem(ItemID.CRYSTAL_OF_MEMORIES, ITEM_FIRST_OPTION.getId());
		} else {
			log.info("cannot find last recall in inventory");
		}
	}

	private int numberOfBrothersKilled() {
		return client.getVar(Varbits.BARROWS_KILLED_AHRIM)
				+ client.getVar(Varbits.BARROWS_KILLED_DHAROK)
				+ client.getVar(Varbits.BARROWS_KILLED_GUTHAN)
				+ client.getVar(Varbits.BARROWS_KILLED_KARIL)
				+ client.getVar(Varbits.BARROWS_KILLED_TORAG)
				+ client.getVar(Varbits.BARROWS_KILLED_VERAC);
	}

	private void useRestorePool() {
		GameObject pool = object.findNearestGameObject(ObjectID.ORNATE_POOL_OF_REJUVENATION);
		if (pool != null) {
			clickGameObject(pool, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
		}
	}

	private void teleportConstructionCape() {
		//menuOption = Tele to POH, Target = Tele to POH, Type = 9790, Opcode = ITEM_FOURTH_OPTION, actionParam = 24, actionParam1 = 9764864
		if (inventory.containsItem(ItemID.CONSTRUCT_CAPET)) {
			clickInventoryItem(ItemID.CONSTRUCT_CAPET, ITEM_FOURTH_OPTION.getId());
		}
	}


	private void activatePrayer(WidgetInfo widgetInfo) {
		Widget prayer_widget = client.getWidget(widgetInfo);
		if (prayer_widget == null) {
			return;
		}
		if (client.getBoostedSkillLevel(Skill.PRAYER) <= 0) {
			return;
		}
		menu.setEntry(new LegacyMenuEntry("Activate", prayer_widget.getName(), 1, 57, prayer_widget.getItemId(), prayer_widget.getId(), false));
		mouse.delayMouseClick(client.getMouseCanvasPosition(), sleepDelay());
	}

	private void clickGameObject(GameObject targetObject, int opcode) {
		menu.setEntry(new LegacyMenuEntry("", "", targetObject.getId(), opcode, targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false));
		mouse.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
	}

	private void clickGroundObject(GroundObject targetObject, int option) {
		menu.setEntry(new LegacyMenuEntry("", "", targetObject.getId(), option, targetObject.getLocalLocation().getSceneX(), targetObject.getLocalLocation().getSceneY(), false));
		mouse.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
	}

	private void clickWallObject(WallObject targetObject, int option) {
		menu.setEntry(new LegacyMenuEntry("", "", targetObject.getId(), option, targetObject.getLocalLocation().getSceneX(), targetObject.getLocalLocation().getSceneY(), false));
		mouse.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
	}

	private void clickDecorObject(DecorativeObject targetObject, int option) {
		menu.setEntry(new LegacyMenuEntry("", "", targetObject.getId(), option, targetObject.getLocalLocation().getSceneX(), targetObject.getLocalLocation().getSceneY(), false));
		mouse.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
	}

	private void clickNPC(NPC npc, int option) {
		menu.setEntry(new LegacyMenuEntry("", "", npc.getIndex(), option, 0, 0, false));
		mouse.delayMouseClick(npc.getConvexHull().getBounds(), sleepDelay());
	}

	private void clickInventoryItem(int itemID, int opcode) {
		menu.setEntry(new LegacyMenuEntry("", "", itemID, opcode, inventory.getWidgetItem(itemID).getIndex(), WidgetInfo.INVENTORY.getId(), false));
		mouse.handleMouseClick(inventory.getWidgetItem(itemID).getCanvasBounds());
	}

	private void useItemOnGameObject(GameObject targetObject, int itemID) {
		LegacyMenuEntry targetMenu = new LegacyMenuEntry("", "", targetObject.getId(), ITEM_USE_ON_GAME_OBJECT.getId(), targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
		menu.setModifiedEntry(targetMenu, itemID, inventory.getWidgetItem(itemID).getIndex(), ITEM_USE_ON_GAME_OBJECT.getId());
		mouse.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
	}

	private void useItemOnItem(int item1ID, int item2ID) {
		WidgetItem item1 = inventory.getWidgetItem(item1ID);
		WidgetItem item2 = inventory.getWidgetItem(item2ID);
		menu.setModifiedEntry(new LegacyMenuEntry("", "", item1.getId(), ITEM_USE.getId(), item1.getIndex(), WidgetInfo.INVENTORY.getId(), false),
				item2.getId(), item2.getIndex(), MenuAction.ITEM_USE_ON_WIDGET_ITEM.getId());
		mouse.click(item1.getCanvasBounds());
	}

	private void clickItemInWidget(int action1Param, Widget widget) { //adjust as needed, widgets can be different
		menu.setEntry(new LegacyMenuEntry("", "", 1, CC_OP.getId(), -1, action1Param, false));
		mouse.delayMouseClick(widget.getBounds(), sleepDelay());
	}

	private void castSpell(WidgetInfo spell) {
		menu.setEntry(new LegacyMenuEntry("", "", 1, MenuAction.CC_OP.getId(), -1, spell.getId(), false));
		mouse.delayMouseClick(client.getWidget(spell).getBounds(), sleepDelay());
	}

	private long sleepDelay() {
		return calc.randomDelay(config.sleepWeightedDistribution(), config.sleepMin(), config.sleepMax(), config.sleepDeviation(), config.sleepTarget());
	}

	private int tickDelay() {
		return (int) calc.randomDelay(config.tickDelayWeightedDistribution(), config.tickDelayMin(), config.tickDelayMax(), config.tickDelayDeviation(), config.tickDelayTarget());
	}


}
