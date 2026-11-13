package entity.enemy;

import java.awt.Color;

import engine.DrawManager;
import engine.DrawManager.SpriteType;
import entity.Entity;

/**
 * A bullet fired by an enemy. Kept separate from {@link entity.Bullet},
 * which the player uses, so enemy shots can fly in any direction without
 * changing the shared bullet class. Moves with sub-pixel precision so aimed
 * shots keep their angle, and is drawn and hit-tested along its direction
 * of flight.
 */
public final class EnemyBullet extends Entity {

	/** Thickness of the bullet (its width when flying straight down). */
	public static final int WIDTH = 6;
	/** Length of the bullet (its height when flying straight down). */
	public static final int HEIGHT = 10;

	/** Exact x of the upper left corner. */
	private double x;
	/** Exact y of the upper left corner. */
	private double y;
	/** Horizontal speed, pixels per frame, positive is right. */
	private final double velocityX;
	/** Vertical speed, pixels per frame, positive is down. */
	private final double velocityY;
	/** Unit direction of flight. */
	private final double directionX;
	private final double directionY;

	/**
	 * @param centerX   Center x where the bullet appears.
	 * @param centerY   Center y where the bullet appears.
	 * @param velocityX Horizontal speed, pixels per frame.
	 * @param velocityY Vertical speed, pixels per frame.
	 */
	public EnemyBullet(final double centerX, final double centerY,
			final double velocityX, final double velocityY) {
		super((int) Math.round(centerX - boxWidth(velocityX, velocityY) / 2.0),
				(int) Math.round(centerY - boxHeight(velocityX, velocityY) / 2.0),
				boxWidth(velocityX, velocityY), boxHeight(velocityX, velocityY),
				Color.WHITE);
		this.spriteType = SpriteType.EnemyBullet;
		this.x = centerX - this.width / 2.0;
		this.y = centerY - this.height / 2.0;
		this.velocityX = velocityX;
		this.velocityY = velocityY;
		double speed = Math.hypot(velocityX, velocityY);
		this.directionX = speed > 0 ? velocityX / speed : 0;
		this.directionY = speed > 0 ? velocityY / speed : 1;
	}

	/**
	 * Hit box that follows the direction: 6x10 straight down, 10x6 sideways,
	 * about 9x9 diagonally.
	 */
	private static int boxWidth(final double vx, final double vy) {
		double speed = Math.hypot(vx, vy);
		double across = speed > 0 ? Math.abs(vx) / speed : 0;
		return (int) Math.round(WIDTH + (HEIGHT - WIDTH) * across);
	}

	private static int boxHeight(final double vx, final double vy) {
		double speed = Math.hypot(vx, vy);
		double down = speed > 0 ? Math.abs(vy) / speed : 1;
		return (int) Math.round(WIDTH + (HEIGHT - WIDTH) * down);
	}

	/** Moves the bullet one frame. */
	public void update() {
		this.x += this.velocityX;
		this.y += this.velocityY;
		this.positionX = (int) Math.round(this.x);
		this.positionY = (int) Math.round(this.y);
	}

	/**
	 * Draws the bullet pointing where it flies: a rounded pellet with an
	 * electric blue rim and a pale core that leans to the front, so it
	 * reads as an enemy shot and shows its direction.
	 *
	 * @param drawManager Drawing target.
	 */
	public void draw(final DrawManager drawManager) {
		// Rim: thick in the middle, thin at both ends.
		dot(drawManager, -4, 2, RIM_DARK);
		for (int step = -2; step <= 2; step += 2)
			dot(drawManager, step, 4, RIM);
		dot(drawManager, 4, 2, RIM);
		// Core, leaning to the front.
		dot(drawManager, -1, 2, CORE);
		dot(drawManager, 1, 2, CORE);
		dot(drawManager, 3, 2, CORE);
	}

	/** Rim color of an enemy pellet: bright alien blue. */
	private static final Color RIM = new Color(70, 175, 255);
	/** Rim color at the tail. */
	private static final Color RIM_DARK = new Color(30, 85, 210);
	/** Bright core of an enemy pellet. */
	private static final Color CORE = new Color(215, 250, 255);

	/** Fills a square of the given size centered on a point along the flight line. */
	private void dot(final DrawManager drawManager, final double along,
			final int size, final Color color) {
		double cx = this.x + this.width / 2.0 + this.directionX * along;
		double cy = this.y + this.height / 2.0 + this.directionY * along;
		drawManager.fillBox((int) Math.round(cx - size / 2.0),
				(int) Math.round(cy - size / 2.0), size, size, color);
	}

	/**
	 * @param width  Screen width.
	 * @param height Screen height.
	 * @param top    Top of the play area.
	 * @return True once the bullet has left the play area.
	 */
	public boolean isOutside(final int width, final int height, final int top) {
		return this.y + this.height < top || this.y > height
				|| this.x + this.width < 0 || this.x > width;
	}

	/** @return Horizontal speed, pixels per frame. */
	public double getVelocityX() {
		return this.velocityX;
	}

	/** @return Vertical speed, pixels per frame. */
	public double getVelocityY() {
		return this.velocityY;
	}
}
