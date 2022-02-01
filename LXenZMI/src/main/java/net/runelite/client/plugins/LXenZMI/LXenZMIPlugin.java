package net.runelite.client.plugins.LXenZMI;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import com.google.inject.Provides;
import net.runelite.api.*;
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
import net.runelite.client.plugins.iutils.*;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

import javax.inject.Inject;

import static net.runelite.api.MenuAction.*;

@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
		name = "XenLeague - ZMI",
		description = "ZMI with last recall",
		tags = {"tags"},
		enabledByDefault = false
)
@Slf4j
public class LXenZMIPlugin extends Plugin {

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
	private OverlayManager overlayManager;

	@Inject
	private LXenZMIConfig config;

	@Inject
	private LXenZMIOverlay overlay;

	public LegacyMenuEntry entry;
	private Player player;


	private boolean start;
	LXenZMIState state;
	Instant timer;
	int timeout;
	long sleepLength;

	private static final Set<Integer> essIDs = Set.of(ItemID.PURE_ESSENCE, ItemID.DAEYALT_ESSENCE);

	@Provides
	LXenZMIConfig provideConfig(ConfigManager configManager) {
		return (LXenZMIConfig) configManager.getConfig(LXenZMIConfig.class);
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked) {
		if (!configButtonClicked.getGroup().equalsIgnoreCase("LXenZMIConfig")) {
			return;
		}
		switch (configButtonClicked.getKey()) {
			case "startButton":
				if (!start) {
					start = true;
					player = client.getLocalPlayer();
					overlayManager.add(overlay);
					timer = Instant.now();
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
				case ANIMATING:
					timeout = tickDelay();
					break;
				case CRAFT_RUNES:
					craftRunesAltar();
					timeout = tickDelay();
					break;
				case TELE_BANK:
					teleportToBank();
					timeout = tickDelay();
					break;
				case OPEN_BANK:
					openBank();
					timeout = tickDelay();
					break;
				case WITHDRAW:
					withdrawItems();
					timeout = tickDelay();
					break;
				case DEPOSIT:
					depositAllItems();
					timeout = tickDelay();
					break;
				case TELE_RECALL:
					teleportRecall();
					timeout = tickDelay();
					break;
				case UNHANDLED_STATE:
					log.info("LXenZMI: unhandled state - stopping");
					resetVals();
					break;

			}

		}
	}

	private LXenZMIState getState() {
		if (timeout > 0) {
			playerUtils.handleRun(20, 20);
			return LXenZMIState.TIMEOUT;
		}

		if (playerUtils.isMoving()) {
			return LXenZMIState.MOVING;
		}

		if (playerUtils.isAnimating()) {
			return LXenZMIState.ANIMATING;
		}

		if (isAtBank()) {
			if (bank.isOpen()) {
				if (!haveEss()) {
					if (inventory.getEmptySlots() >= 27) {
						return LXenZMIState.WITHDRAW;
					}
					return LXenZMIState.DEPOSIT;
				}
				return LXenZMIState.TELE_RECALL;
			}
			if (haveEss()) {
				return LXenZMIState.TELE_RECALL;
			}
			return LXenZMIState.OPEN_BANK;
		}

		if (isAtAltar()) {
			if (haveEss()) {
				return LXenZMIState.CRAFT_RUNES;
			}
			return LXenZMIState.TELE_BANK;
		}

		return LXenZMIState.UNHANDLED_STATE;
	}

	private boolean haveEss() {
		return inventory.containsItem(essIDs) && inventory.isFull();
	}

	private boolean isAtBank() {
		return player.getWorldArea().intersectsWith(config.teleportDestination().getBankArea());
	}

	private boolean isAtAltar() {
		//WorldArea altarArea = new WorldArea(new WorldPoint(), new WorldPoint());
		//player.getWorldArea().intersectsWith(altarArea);
		return client.getLocalPlayer().getWorldLocation().getRegionID() == 12119;
	}

	private void craftRunesAltar() {
		GameObject zmiAltar = object.findNearestGameObject(ObjectID.RUNECRAFTING_ALTAR); //todo 29631
		if (zmiAltar != null) {
			clickGameObject(zmiAltar, GAME_OBJECT_FIRST_OPTION.getId());
		} else {
			log.info("Cannot find object");
		}
	}

	private void teleportToBank() {
		Widget teleportList = client.getWidget(WidgetInfo.ADVENTURE_LOG);
		if (teleportList != null) {
			clickItemInWaystoneWidget(config.teleportDestination().getAction1Param(),teleportList);
			return;
		}
		clickEquippedItem(2, WidgetInfo.EQUIPMENT_AMULET);
	}

	private void openBank() {
		WorldPoint[] validBankLocations = config.teleportDestination().getValidBanks();
		int randomBankIndex = calc.getRandomIntBetweenRange(0, validBankLocations.length - 1);
		GameObject bank = object.getGameObjectAtWorldPoint(validBankLocations[randomBankIndex]);
		if (bank != null) {
			clickGameObject(bank, GAME_OBJECT_SECOND_OPTION.getId());
		} else {
			log.info("Cannot find bank");
		}
	}

	private void withdrawItems() {
		if (bank.contains(ItemID.DAEYALT_ESSENCE, 27)) {
			bank.withdrawAllItem(ItemID.DAEYALT_ESSENCE);
			return;
		}
		if (bank.contains(ItemID.PURE_ESSENCE, 27)) {
			bank.withdrawAllItem(ItemID.PURE_ESSENCE);
			return;
		}
		log.info("out of essence");
	}

	private void depositAllItems() {
		bank.depositAllExcept(Collections.singleton(ItemID.CRYSTAL_OF_MEMORIES));
	}

	private void teleportRecall() {
		if (inventory.containsItem(ItemID.CRYSTAL_OF_MEMORIES)) {
			clickInventoryItem(ItemID.CRYSTAL_OF_MEMORIES, ITEM_FIRST_OPTION.getId());
		}
		else {
			log.info("cannot find last recall in inventory");
		}
	}

	private void clickEquippedItem(int type, WidgetInfo equipmentSlot) {
		// menuOption = Teleport, Target = <col=ff9040>Portable waystone</col>, Type = 2, Opcode = CC_OP, actionParam = -1, actionParam1 = 25362449
		menu.setEntry(new LegacyMenuEntry("", "", type, CC_OP.getId(), -1, equipmentSlot.getId(), false));
		mouse.delayMouseClick(client.getWidget(equipmentSlot).getBounds(), sleepDelay());
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

	private void clickItemInWaystoneWidget(int actionParam, Widget widget) { //adjust as needed, widgets can be different
		menu.setEntry(new LegacyMenuEntry("", "", 0, WIDGET_TYPE_6.getId(), actionParam, 12255235, false));
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
