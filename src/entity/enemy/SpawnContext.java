package entity.enemy;

import java.util.Random;

/**
 * What a movement pattern needs to know when a formation is spawned: the
 * play area, where the player is, the balance values and a random source.
 */
public final class SpawnContext {

	/** Screen width in pixels. */
	public final int width;
	/** Screen height in pixels. */
	public final int height;
	/** Top of the play area (the HUD line). */
	public final int top;
	/** Player center x at spawn time. */
	public final double playerX;
	/** Player center y at spawn time. */
	public final double playerY;
	/** Balance values, including path shapes. */
	public final EnemyBalance balance;
	/** Random source for the pattern's choices. */
	public final Random random;

	/**
	 * @param width   Screen width.
	 * @param height  Screen height.
	 * @param top     Top of the play area.
	 * @param playerX Player center x.
	 * @param playerY Player center y.
	 * @param balance Balance values.
	 * @param random  Random source.
	 */
	public SpawnContext(final int width, final int height, final int top,
			final double playerX, final double playerY,
			final EnemyBalance balance, final Random random) {
		this.width = width;
		this.height = height;
		this.top = top;
		this.playerX = playerX;
		this.playerY = playerY;
		this.balance = balance;
		this.random = random;
	}

	/** @return Height of the play area, below the HUD line. */
	public int playHeight() {
		return this.height - this.top;
	}

	/**
	 * @param min Lowest value.
	 * @param max Highest value.
	 * @return A random value from min to max.
	 */
	public double between(final double min, final double max) {
		return min + (max - min) * this.random.nextDouble();
	}

	/**
	 * @param range {min, max}.
	 * @return A random value in the range.
	 */
	public double between(final double[] range) {
		return between(range[0], range[1]);
	}

	/**
	 * @param fraction 0 = just below the HUD line, 1 = bottom of the screen.
	 * @return The matching y coordinate.
	 */
	public double playY(final double fraction) {
		return this.top + playHeight() * fraction;
	}
}
