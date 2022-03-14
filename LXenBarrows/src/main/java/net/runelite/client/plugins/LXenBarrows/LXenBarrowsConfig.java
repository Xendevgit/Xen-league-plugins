package net.runelite.client.plugins.LXenBarrows;

import net.runelite.client.config.*;

@ConfigGroup("LXenBarrowsConfig")
public interface LXenBarrowsConfig extends Config {


	//region Sleepdelays
	@ConfigSection(keyName = "delayConfig", name = "Sleep Delay Configuration", description = "Configure how the bot handles sleep delays", position = 1, closedByDefault = true)
	String delayConfig = "delayConfig";

	@Range(min = 0, max = 550)
	@ConfigItem(keyName = "sleepMin", name = "Sleep Min", description = "", position = 2, section = "delayConfig")
	default int sleepMin() {
		return 60;
	}

	@Range(min = 0, max = 550)
	@ConfigItem(keyName = "sleepMax", name = "Sleep Max", description = "", position = 3, section = "delayConfig")
	default int sleepMax() {
		return 350;
	}

	@Range(min = 0, max = 550)
	@ConfigItem(keyName = "sleepTarget", name = "Sleep Target", description = "", position = 4, section = "delayConfig")
	default int sleepTarget() {
		return 100;
	}

	@Range(min = 0, max = 550)
	@ConfigItem(keyName = "sleepDeviation", name = "Sleep Deviation", description = "", position = 5, section = "delayConfig")
	default int sleepDeviation() {
		return 10;
	}

	@ConfigItem(keyName = "sleepWeightedDistribution", name = "Sleep Weighted Distribution", description = "Shifts the random distribution towards the lower end at the target, otherwise it will be an even distribution", position = 6, section = "delayConfig")
	default boolean sleepWeightedDistribution() {
		return false;
	}
	//endregion

	//region Tickdelays
	@ConfigSection(keyName = "delayTickConfig", name = "Game Tick Configuration", description = "Configure how the bot handles game tick delays, 1 game tick equates to roughly 600ms", position = 7, closedByDefault = true)
	String delayTickConfig = "delayTickConfig";

	@Range(min = 1, max = 25)
	@ConfigItem(keyName = "tickDelayMin", name = "Game Tick Min", description = "", position = 8, section = "delayTickConfig")
	default int tickDelayMin() {
		return 1;
	}

	@Range(min = 1, max = 30)
	@ConfigItem(keyName = "tickDelayMax", name = "Game Tick Max", description = "", position = 9, section = "delayTickConfig")
	default int tickDelayMax() {
		return 3;
	}

	@Range(min = 1, max = 30)
	@ConfigItem(keyName = "tickDelayTarget", name = "Game Tick Target", description = "", position = 10, section = "delayTickConfig")
	default int tickDelayTarget() {
		return 2;
	}

	@Range(min = 1, max = 30)
	@ConfigItem(keyName = "tickDelayDeviation", name = "Game Tick Deviation", description = "", position = 11, section = "delayTickConfig")
	default int tickDelayDeviation() {
		return 1;
	}

	@ConfigItem(keyName = "tickDelayWeightedDistribution", name = "Game Tick Weighted Distribution", description = "Shifts the random distribution towards the lower end at the target, otherwise it will be an even distribution", position = 12, section = "delayTickConfig")
	default boolean tickDelayWeightedDistribution() {
		return false;
	}
	//endregion

	@ConfigItem(
			keyName = "instructions",
			name = "",
			description = "Instructions.",
			position = 13
	)
	default String instructions() {
		return "example text for instructions.";
	}

	@ConfigItem(
			keyName = "enableUI",
			name = "Enable UI",
			description = "Enable to turn on overlay",
			position = 14
	)
	default boolean enableUI() {
		return true;
	}

	@ConfigItem(
			keyName = "exampleInt",
			name = "example item ID",
			description = "example description",
			position = 15
	)
	default int exampleInt() {
		return 0;
	}

	@ConfigItem(
			keyName = "exampleBoolean",
			name = "example tickbox",
			description = "example description",
			position = 16
	)
	default boolean exampleBoolean() {
		return false;
	}

	@ConfigItem(
			keyName = "startButton",
			name = "Start/Stop",
			description = "Start or stop",
			position = 80
	)
	default Button startButton() {
		return new Button();
	}

}
