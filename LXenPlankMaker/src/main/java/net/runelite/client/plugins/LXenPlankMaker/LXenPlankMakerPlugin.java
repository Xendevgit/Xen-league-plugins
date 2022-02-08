package net.runelite.client.plugins.LXenPlankMaker;

import java.time.Instant;

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
		name = "Xen League - Plank maker",
		description = "Makes planks at sawmill using last recall",
		tags = {"tags"},
		enabledByDefault = false
)
@Slf4j
public class LXenPlankMakerPlugin extends Plugin {

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
	private LXenPlankMakerConfig config;

	@Inject
	private LXenPlankMakerOverlay overlay;

	public LegacyMenuEntry entry;
	private Player player;


	private boolean start;
	LXenPlankMakerState state;
	Instant timer;
	int timeout;
	long sleepLength;

	@Provides
	LXenPlankMakerConfig provideConfig(ConfigManager configManager) {
		return (LXenPlankMakerConfig) configManager.getConfig(LXenPlankMakerConfig.class);
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked) {
		if (!configButtonClicked.getGroup().equalsIgnoreCase("LXenPlankMakerConfig")) {
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
					timeout = tickDelay();
					break;
				case MAKE_PLANK:
					makePlanks();
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
					depositItems();
					timeout = tickDelay();
					break;
				case TELE_RECALL:
					teleportRecall();
					timeout = tickDelay();
					break;
				case UNHANDLED_STATE:
					log.info("LXenPlankMaker: unhandled state - stopping");
				case STOPPING:
					resetVals();
					break;

			}

		}
	}

	private LXenPlankMakerState getState() {
		if (timeout > 0) {
			playerUtils.handleRun(20, 20);
			return LXenPlankMakerState.TIMEOUT;
		}

		if (!inventory.containsStackAmount(ItemID.COINS_995, config.stopAtCoins())) {
			return LXenPlankMakerState.STOPPING;
		}

		if (playerUtils.isMoving()) {
			return LXenPlankMakerState.MOVING;
		}

		if (isAtBank()) {
			if (bank.isOpen()) {
				if (havePlanks()) {
					return LXenPlankMakerState.DEPOSIT;
				}
				if (!bank.contains(ItemID.TEAK_LOGS, 26)) {
					return LXenPlankMakerState.STOPPING;
				}
				if (!haveLogs()) {
					return LXenPlankMakerState.WITHDRAW;
				}
				return LXenPlankMakerState.TELE_RECALL;
			}

			if (havePlanks()) {
				return LXenPlankMakerState.OPEN_BANK;
			}
			if (haveLogs()) {
				return LXenPlankMakerState.TELE_RECALL;
			}
		}

		if (isAtSawmill()) {
			 if (haveLogs()) {
				 return LXenPlankMakerState.MAKE_PLANK;
			 }
			return LXenPlankMakerState.TELE_BANK;
		}


		return LXenPlankMakerState.UNHANDLED_STATE;
	}

	private boolean havePlanks() {
		return inventory.containsItemAmount(ItemID.TEAK_PLANK, 26, false, false);
	}

	private boolean haveLogs() {
		return inventory.containsItemAmount(ItemID.TEAK_LOGS, 26, false, false);
	}

	private boolean isAtSawmill() {
		return player.getWorldLocation().getRegionID() == 13110;
	}

	private boolean isAtBank() {
		return player.getWorldArea().intersectsWith(config.teleportDestination().getBankArea());
	}

	private void makePlanks() {
		Widget skillMenu = client.getWidget(WidgetInfo.MULTI_SKILL_MENU);
		if (skillMenu != null) {
			clickItemInWidget(17694736, skillMenu);
			return;
		}
		NPC sawmill = npc.findNearestNpc(NpcID.SAWMILL_OPERATOR);
		if (sawmill != null) {
			clickNPC(sawmill, NPC_THIRD_OPTION.getId());
			return;
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
		bank.withdrawAllItem(ItemID.TEAK_LOGS);
	}

	private void depositItems() {
		bank.depositAllOfItem(ItemID.TEAK_PLANK);
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
