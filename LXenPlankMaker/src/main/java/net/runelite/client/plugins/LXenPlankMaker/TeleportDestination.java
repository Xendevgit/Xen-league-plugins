package net.runelite.client.plugins.LXenPlankMaker;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

@Getter
@AllArgsConstructor
public enum TeleportDestination {

	CATHERBY("Catherby",4, new WorldArea(new WorldPoint(2782,3430,0), (new WorldPoint(2815,3447,0))),
			new WorldPoint[]{
					new WorldPoint(2807, 3442, 0),
					new WorldPoint(2809, 3442, 0),
					new WorldPoint(2810, 3442, 0),
					new WorldPoint(2811, 3442, 0)
			}),
	CANIFIS("Canifis",9, new WorldArea(new WorldPoint(3487, 3467, 0), (new WorldPoint(3518, 3499, 0))),
			new WorldPoint[]{
					new WorldPoint(3513, 3479, 0),
					new WorldPoint(3513, 3480, 0),
					new WorldPoint(3513, 3481, 0)
			});

	private final String name;
	private final int action1Param;
	private final WorldArea bankArea;
	private final WorldPoint[] validBanks;


	@Override
	public String toString() {
		return getName();
	}
}