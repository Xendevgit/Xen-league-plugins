package net.runelite.client.plugins.LXenSeaweed;

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
		name = "XenLeague - Seaweed",
		description = "Gathers seaweed at piscatoris",
		tags = {"tags"},
		enabledByDefault = false
)
@Slf4j
public class LXenSeaweedPlugin extends Plugin {

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
	private LXenSeaweedConfig config;

	@Inject
	private LXenSeaweedOverlay overlay;

	public LegacyMenuEntry entry;
	private Player player;


	private boolean start;
	LXenSeaweedState state;
	Instant timer;
	int timeout;
	long sleepLength;

	private final WorldArea bankArea = new WorldArea(new WorldPoint(2326, 3685, 0), new WorldPoint(2333, 3694, 0));
	private final WorldPoint bankWalkTile = new WorldPoint(2330, 3690, 0);

	@Provides
	LXenSeaweedConfig provideConfig(ConfigManager configManager) {
		return (LXenSeaweedConfig) configManager.getConfig(LXenSeaweedConfig.class);
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked) {
		if (!configButtonClicked.getGroup().equalsIgnoreCase("LXenSeaweedConfig")) {
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
				case GATHER_SEAWEED:
					gatherSeaweed();
					timeout = tickDelay();
					break;
				case OPEN_BANK:
					openBank();
					timeout = tickDelay();
					break;
				case WALK_BANK:
					walkBank();
					timeout = tickDelay();
					break;
				case DEPOSIT:
					depositSeaweed();
					timeout = tickDelay();
					break;
				case WAIT:
					log.info("LXenSeaweed: unhandled state - stopping");
					resetVals();
					break;

			}

		}
	}

	private LXenSeaweedState getState() {
		if (timeout > 0) {
			playerUtils.handleRun(20, 20);
			return LXenSeaweedState.TIMEOUT;
		}

		if (playerUtils.isMoving() && !isInBankArea()) {
			return LXenSeaweedState.MOVING;
		}

		if (bank.isOpen()) {
			if (inventory.containsItem(ItemID.SEAWEED)) {
				return LXenSeaweedState.DEPOSIT;
			}
		}

		if (inventory.isFull() && inventory.containsItem(ItemID.SEAWEED)) {
			if (isInBankArea()) {
				return LXenSeaweedState.OPEN_BANK;
			}
			return LXenSeaweedState.WALK_BANK;
		}

		if (!inventory.isFull()) {
			return LXenSeaweedState.GATHER_SEAWEED;
		}

		return LXenSeaweedState.WAIT;
	}

	private boolean isInBankArea() {
		return player.getWorldArea().intersectsWith(bankArea);
	}


	private void gatherSeaweed() {
		GameObject seaweedNet = object.findNearestGameObject(ObjectID.NET_13609);
		if (seaweedNet != null) {
			clickGameObject(seaweedNet, GAME_OBJECT_FIRST_OPTION.getId());
		}
	}

	private void walkBank() {
		walk.sceneWalk(bankWalkTile, 2, sleepDelay());
	}

	private void openBank() {
		NPC banker = npc.findNearestNpc(NpcID.ARNOLD_LYDSPOR);
		if (banker != null) {
			clickNPC(banker, NPC_FOURTH_OPTION.getId());
		} else {
			log.debug("cannot find bank npc");
		}
	}

	private void depositSeaweed() {
		bank.depositAllOfItem(ItemID.SEAWEED);
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
