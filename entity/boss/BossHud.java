package entity.boss;

import java.awt.Color;

import engine.DrawManager;
import screen.Screen;

/** Draws the boss health bar. */
public final class BossHud {

	/** Bar width as a share of the screen width. */
	private static final double BAR_WIDTH_RATIO = 0.6;
	/** Bar height. */
	private static final int BAR_HEIGHT = 5;
	/** Gap between the name and the bar. */
	private static final int BAR_GAP = 4;

	private BossHud() {
	}

	/**
	 * @param drawManager Drawing target.
	 * @param screen      Screen, for centering.
	 * @param boss        Boss; nothing is drawn once it is beaten.
	 * @param positionY   Baseline of the name.
	 */
	public static void drawHealthBar(final DrawManager drawManager,
			final Screen screen, final BossFight boss, final int positionY) {
		if (boss == null || boss.isDefeated())
			return;
		int max = Math.max(1, boss.getMaxHealth());
		int health = Math.max(0, Math.min(boss.getHealth(), max));
		int barWidth = (int) (screen.getWidth() * BAR_WIDTH_RATIO);
		int barX = (screen.getWidth() - barWidth) / 2;
		int barY = positionY + BAR_GAP;
		drawManager.drawRegularString(boss.getName(), barX, positionY,
				Color.WHITE);
		drawManager.fillBox(barX, barY, barWidth, BAR_HEIGHT,
				new Color(60, 0, 0, 180));
		drawManager.fillBox(barX, barY, barWidth * health / max, BAR_HEIGHT,
				Color.RED);
		drawManager.drawBox(barX, barY, barWidth, BAR_HEIGHT, Color.RED);
	}
}
