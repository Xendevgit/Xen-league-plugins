package net.runelite.client.plugins.LXenSanfew;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.LXenSanfew.LXenSanfewConfig;
import net.runelite.client.plugins.LXenSanfew.LXenSanfewState;
import net.runelite.client.plugins.iutils.*;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.MouseEvent;

import static net.runelite.api.MenuAction.*;
import static net.runelite.client.plugins.iutils.iUtils.iterating;

@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
		name = "XenLeague - Sanfew",
		description = "Makes sanfew serum from super restore 1",
		tags = {"tags"},
		enabledByDefault = false
)
@Slf4j
public class LXenSanfewPlugin extends Plugin {

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
	private LXenSanfewConfig config;

	@Inject
	private LXenSanfewOverlay overlay;

	public LegacyMenuEntry entry;
	private Player player;


	private boolean start;
	LXenSanfewState state;
	Instant timer;
	int timeout;
	long sleepLength;

	@Provides
	LXenSanfewConfig provideConfig(ConfigManager configManager) {
		return (LXenSanfewConfig) configManager.getConfig(LXenSanfewConfig.class);
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked) {
		if (!configButtonClicked.getGroup().equalsIgnoreCase("LXenSanfewConfig")) {
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
				case ITERATING:
					timeout = tickDelay();
					break;
				case WITHDRAW_DUST:
					withdrawDust();
					timeout = tickDelay();
					break;
				case WITHDRAW_GRASS:
					withdrawGrass();
					timeout = tickDelay();
					break;
				case WITHDRAW_NAILS:
					withdrawNails();
					timeout = tickDelay();
					break;
				case WITHDRAW_RESTORES:
					withdrawRestores();
					timeout = tickDelay();
					break;
				case CLOSE_BANK:
					closeBank();
					timeout = tickDelay();
					break;
				case COMBINE_DUST:
					combineDust();
					timeout = tickDelay();
					break;
				case COMBINE_GRASS:
					combineGrass();
					timeout = tickDelay();
					break;
				case COMBINE_NAILS:
					combineNails();
					timeout = tickDelay();
					break;
				case OPEN_BANK:
					openBank();
					timeout = tickDelay();
					break;
				case DEPOSIT:
					depositSanfew();
					timeout = tickDelay();
					break;
				case UNHANDLED_STATE:
					log.info("LXenSanfew: unhandled state - stopping");
					resetVals();
					break;
			}

		}
	}

	private LXenSanfewState getState() {
		if (timeout > 0) {
			playerUtils.handleRun(20, 20);
			return LXenSanfewState.TIMEOUT;
		}

		if (playerUtils.isMoving()) {
			return LXenSanfewState.MOVING;
		}

		if (iterating) {
			return LXenSanfewState.ITERATING;
		}

		if (bank.isOpen()) {
			if (inventory.containsItem(ItemID.SANFEW_SERUM4)) {
				return LXenSanfewState.DEPOSIT;
			}
			if (!inventory.containsItem(ItemID.UNICORN_HORN_DUST)) {
				return LXenSanfewState.WITHDRAW_DUST;
			}
			if (!inventory.containsItem(ItemID.SNAKE_WEED)) {
				return LXenSanfewState.WITHDRAW_GRASS;
			}
			if (!inventory.containsItem(ItemID.NAIL_BEAST_NAILS)) {
				return LXenSanfewState.WITHDRAW_NAILS;
			}
			if (!inventory.containsItem(ItemID.SUPER_RESTORE1)) {
				return LXenSanfewState.WITHDRAW_RESTORES;
			}
			return LXenSanfewState.CLOSE_BANK;
		}

		if (inventory.containsItem(ItemID.SUPER_RESTORE1)) {
			return LXenSanfewState.COMBINE_DUST;
		}

		if (inventory.containsItem(ItemID.MIXTURE__STEP_11)) {
			return LXenSanfewState.COMBINE_GRASS;
		}

		if (inventory.containsItem(ItemID.MIXTURE__STEP_21)) {
			return LXenSanfewState.COMBINE_NAILS;
		}

		if (inventory.containsItem(ItemID.SANFEW_SERUM4)) {
			return LXenSanfewState.OPEN_BANK;
		}

		return LXenSanfewState.UNHANDLED_STATE;
	}


	private void withdrawDust() {
		bank.withdrawItem(ItemID.UNICORN_HORN_DUST);
	}

	private void withdrawGrass() {
		bank.withdrawItem(ItemID.SNAKE_WEED);
	}

	private void withdrawNails() {
		bank.withdrawItem(ItemID.NAIL_BEAST_NAILS);
	}

	private void withdrawRestores() {
		bank.withdrawAllItem(ItemID.SUPER_RESTORE1);
	}

	private void closeBank() {
		bank.close();
	}

	private void combineDust() {
		inventory.combineItems(Collections.singleton(ItemID.SUPER_RESTORE1), ItemID.UNICORN_HORN_DUST, 38, false, true, config.sleepMin(), config.sleepMax());
	}

	private void combineGrass() {
		inventory.combineItems(Collections.singleton(ItemID.MIXTURE__STEP_11), ItemID.SNAKE_WEED, 38, false, true, config.sleepMin(), config.sleepMax());
	}

	private void combineNails() {
		inventory.combineItems(Collections.singleton(ItemID.MIXTURE__STEP_21), ItemID.NAIL_BEAST_NAILS, 38, false, true, config.sleepMin(), config.sleepMax());
	}

	private void openBank() {
		GameObject bank = object.findNearestBank();
		if (bank != null) {
			clickGameObject(bank, GAME_OBJECT_SECOND_OPTION.getId());
		}
	}

	private void depositSanfew() {
		bank.depositAllOfItem(ItemID.SANFEW_SERUM4);
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
