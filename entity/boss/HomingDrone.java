package entity.boss;

import java.awt.Color;

import engine.DrawManager;
import entity.Entity;

/**
 * A small enemy that turns toward the player for a while, then flies
 * straight on in its last direction until it leaves the screen. It can be
 * shot down, and it is used up when it touches the player.
 */
final class HomingDrone {

	/** Size on screen. */
	static final int SIZE = 12;
	/** Frames it flashes after a hit. */
	private static final int FLASH_FRAMES = 4;

	private double x;
	private double y;
	private double heading;
	private final double speed;
	private final double turnRate;
	private int chaseFrames;
	private int health;
	private int flash;
	private boolean dead;
	/** Frames alive, for the spin and the trail. */
	private int age;
	/** Recent positions, oldest first, for the glowing trail. */
	private final double[] trailX = new double[TRAIL];
	private final double[] trailY = new double[TRAIL];

	/** Trail length in samples. */
	private static final int TRAIL = 4;
	/** Frames between trail samples. */
	private static final int TRAIL_GAP = 3;
	private static final Color SHELL = new Color(150, 50, 210);
	private static final Color HEART = new Color(240, 120, 255);
	private static final Color SPARK = new Color(140, 245, 255);

	/**
	 * @param x           Center x.
	 * @param y           Center y.
	 * @param heading     Start direction, radians.
	 * @param speed       Pixels per frame.
	 * @param turnRate    Largest turn per frame, radians.
	 * @param chaseFrames Frames it keeps turning toward the player.
	 * @param health      Hits it takes.
	 */
	HomingDrone(final double x, final double y, final double heading,
			final double speed, final double turnRate, final int chaseFrames,
			final int health) {
		this.x = x;
		this.y = y;
		this.heading = heading;
		this.speed = speed;
		this.turnRate = turnRate;
		this.chaseFrames = chaseFrames;
		this.health = health;
		for (int i = 0; i < TRAIL; i++) {
			this.trailX[i] = x;
			this.trailY[i] = y;
		}
	}

	/** Moves one frame toward the player while chasing. */
	void update(final double playerX, final double playerY) {
		if (this.dead)
			return;
		if (this.flash > 0)
			this.flash--;
		if (++this.age % TRAIL_GAP == 0) {
			System.arraycopy(this.trailX, 1, this.trailX, 0, TRAIL - 1);
			System.arraycopy(this.trailY, 1, this.trailY, 0, TRAIL - 1);
			this.trailX[TRAIL - 1] = this.x;
			this.trailY[TRAIL - 1] = this.y;
		}
		if (this.chaseFrames > 0) {
			this.chaseFrames--;
			double wanted = Math.atan2(playerY - this.y, playerX - this.x);
			double turn = Math.atan2(Math.sin(wanted - this.heading),
					Math.cos(wanted - this.heading));
			this.heading += Math.max(-this.turnRate, Math.min(this.turnRate, turn));
		}
		this.x += Math.cos(this.heading) * this.speed;
		this.y += Math.sin(this.heading) * this.speed;
	}

	/** @return True once shot down, or gone off screen after chasing. */
	boolean isGone(final int width, final int height, final int top) {
		if (this.dead)
			return true;
		if (this.chaseFrames > 0)
			return false;
		int margin = SIZE * 2;
		return this.x < -margin || this.x > width + margin
				|| this.y < top - margin || this.y > height + margin;
	}

	/** @return True if the entity overlaps this drone. */
	boolean touches(final Entity entity) {
		return !this.dead && BossHazard.overlaps(this.x - SIZE / 2.0,
				this.y - SIZE / 2.0, SIZE, SIZE, entity);
	}

	/** @return True if this hit destroyed it. */
	boolean takeHit() {
		this.health--;
		this.flash = FLASH_FRAMES;
		if (this.health <= 0)
			this.dead = true;
		return this.dead;
	}

	/** Removes it without rewards (it rammed the player). */
	void crash() {
		this.dead = true;
	}

	double getX() {
		return this.x;
	}

	double getY() {
		return this.y;
	}

	boolean isChasing() {
		return this.chaseFrames > 0;
	}

	void draw(final DrawManager drawManager) {
		if (this.dead)
			return;
		boolean white = this.flash > 0;
		// Fading trail of light behind it.
		for (int i = 0; i < TRAIL; i++) {
			int size = 2 + i;
			drawManager.fillBox((int) Math.round(this.trailX[i]) - size / 2,
					(int) Math.round(this.trailY[i]) - size / 2, size, size,
					new Color(240, 120, 255, 50 + 35 * i));
		}
		int cx = (int) Math.round(this.x);
		int cy = (int) Math.round(this.y);
		Color shell = white ? Color.WHITE : SHELL;
		Color heart = white ? Color.WHITE : HEART;
		// A spinning crystal: a diamond, then a square with spikes, in turn.
		if ((this.age / 5) % 2 == 0) {
			drawManager.fillBox(cx - 2, cy - 6, 4, 12, shell);
			drawManager.fillBox(cx - 4, cy - 4, 8, 8, shell);
			drawManager.fillBox(cx - 6, cy - 2, 12, 4, shell);
		} else {
			drawManager.fillBox(cx - 4, cy - 4, 8, 8, shell);
			for (int dx = -1; dx <= 1; dx += 2)
				for (int dy = -1; dy <= 1; dy += 2)
					drawManager.fillBox(cx + dx * 5 - 1, cy + dy * 5 - 1, 2, 2, shell);
		}
		drawManager.fillBox(cx - 2, cy - 2, 4, 4, heart);
		boolean pulse = (this.age / 4) % 2 == 0;
		drawManager.fillBox(cx - 1, cy - 1, 2, 2, pulse ? Color.WHITE : SPARK);
		// A spark at the front shows where it is heading.
		int nx = (int) Math.round(this.x + Math.cos(this.heading) * (SIZE / 2.0 + 2));
		int ny = (int) Math.round(this.y + Math.sin(this.heading) * (SIZE / 2.0 + 2));
		drawManager.fillBox(nx - 1, ny - 1, 3, 3, white ? Color.WHITE : SPARK);
	}
}
