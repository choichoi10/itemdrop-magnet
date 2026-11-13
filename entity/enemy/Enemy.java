package entity.enemy;

import java.awt.Color;
import java.util.List;

import engine.DrawManager.SpriteType;
import engine.soundEngine.Sound;
import engine.soundEngine.SoundManager;
import entity.Entity;

/**
 * The one enemy class shared by every enemy type. What it looks like, how
 * it shoots and its numbers come from its {@link EnemyType} and
 * {@link EnemyStats}; where it flies comes from its {@link EnemyPath}.
 *
 * All timing is counted in frames, so pausing the game (which stops
 * updates) also pauses movement and shooting.
 */
public class Enemy extends Entity {

	/** Frames between the two animation frames. */
	private static final int ANIMATION_FRAMES = 30;
	/** Frames the explosion is shown after the enemy is destroyed. */
	private static final int EXPLOSION_FRAMES = 24;
	/** Frames the explosion stays on or off while blinking. */
	private static final int EXPLOSION_BLINK_FRAMES = 4;
	/** Explosion sprite width in sprite pixels. */
	private static final int EXPLOSION_COLUMNS = 13;
	/** Explosion sprite height in sprite pixels. */
	private static final int EXPLOSION_ROWS = 7;
	/** Frames the enemy flashes white after a hit it survives. */
	private static final int HIT_FLASH_FRAMES = 6;
	/** Frames before the same enemy can ram the player again. */
	private static final int RAM_COOLDOWN_FRAMES = 45;
	/** Frames between the shots of one volley (burst fire). */
	private static final int VOLLEY_GAP_FRAMES = 8;

	/** Catalogue entry. */
	private final EnemyType type;
	/** Numbers from the balance file. */
	private final EnemyStats stats;
	/** Route it flies. */
	private final EnemyPath path;
	/** Screen width, for the on-screen check. */
	private final int screenWidth;
	/** Screen height, for the on-screen check. */
	private final int screenHeight;
	/** Top of the play area; the enemy is hidden while above it. */
	private final int top;

	/** Distance flown along the path; negative while waiting to enter. */
	private double distance;
	/** Exact center x. */
	private double centerX;
	/** Exact center y. */
	private double centerY;
	/** Direction of travel, unit vector x. */
	private double headingX;
	/** Direction of travel, unit vector y. */
	private double headingY = 1;
	/** Hits left. */
	private int health;
	/** True once destroyed (exploding or gone). */
	private boolean destroyed;
	/** Frames since destruction. */
	private int explosionFrames;
	/** Frames since the last animation change. */
	private int animationFrames;
	/** Frames until the next shot. */
	private int fireTimer;
	/** Shots fired so far in the current volley. */
	private int volleyShots;
	/** Frames of white flash left. */
	private int hitFlashFrames;
	/** Frames before it can ram again. */
	private int ramCooldown;

	/**
	 * @param type          Catalogue entry.
	 * @param stats         Numbers for this type.
	 * @param path          Route to fly.
	 * @param startDistance Distance along the path at spawn; negative values
	 *                      wait off screen, used for formation members.
	 * @param firstShotIn   Frames before the first shot.
	 * @param screenWidth   Screen width.
	 * @param screenHeight  Screen height.
	 * @param top           Top of the play area.
	 */
	public Enemy(final EnemyType type, final EnemyStats stats,
			final EnemyPath path, final double startDistance,
			final int firstShotIn, final int screenWidth,
			final int screenHeight, final int top) {
		super(0, 0, type.getSpriteColumns() * stats.pixelSize,
				type.getSpriteRows() * stats.pixelSize, type.getColor());
		this.type = type;
		this.stats = stats;
		this.path = path;
		this.distance = startDistance;
		this.fireTimer = Math.max(1, firstShotIn);
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.top = top;
		this.health = stats.health;
		this.spriteType = type.getSpriteA();
		updatePosition();
	}

	/**
	 * Moves the enemy one frame along its path and animates it.
	 *
	 * @param frozen True while the freeze item holds enemies in place.
	 */
	public final void move(final boolean frozen) {
		if (this.destroyed)
			return;
		if (!frozen)
			this.distance += this.stats.speed;
		if (++this.animationFrames >= ANIMATION_FRAMES) {
			this.animationFrames = 0;
			this.spriteType = this.spriteType == this.type.getSpriteA()
					? this.type.getSpriteB() : this.type.getSpriteA();
		}
		updatePosition();
	}

	/**
	 * Counts down to the next shot and fires when it is due and the enemy is
	 * on screen.
	 *
	 * @param targetX Player center x.
	 * @param targetY Player center y.
	 * @param out     List new bullets are added to.
	 * @return True if it fired.
	 */
	public final boolean tryFire(final double targetX, final double targetY,
			final List<EnemyBullet> out) {
		if (this.type.getFireStyle() == FireStyle.NONE || !isHittable())
			return false;
		if (this.fireTimer > 0)
			this.fireTimer--;
		if (this.fireTimer > 0 || !isOnScreen())
			return false;
		FireStyle style = this.type.getFireStyle();
		style.fire(this, targetX, targetY, out);
		// A volley fires its shots a few frames apart, then waits as usual.
		if (++this.volleyShots < style.getVolley()) {
			this.fireTimer = VOLLEY_GAP_FRAMES;
		} else {
			this.volleyShots = 0;
			this.fireTimer = this.stats.fireInterval;
		}
		return true;
	}

	/** Advances flash, ram and explosion timers; runs every frame. */
	public final void tickEffects() {
		if (this.hitFlashFrames > 0)
			this.hitFlashFrames--;
		if (this.ramCooldown > 0)
			this.ramCooldown--;
		if (this.destroyed)
			this.explosionFrames++;
	}

	/**
	 * Applies damage. The caller handles rewards and calls {@link #destroy()}
	 * on a lethal hit.
	 *
	 * @param damage Hits to take.
	 * @return True if this hit was lethal.
	 */
	public final boolean takeDamage(final int damage) {
		if (damage <= 0 || this.destroyed || this.health == 0)
			return false;
		this.health = Math.max(0, this.health - damage);
		if (this.health > 0)
			this.hitFlashFrames = HIT_FLASH_FRAMES;
		return this.health == 0;
	}

	/** Destroys the enemy and starts its explosion. */
	public final void destroy() {
		if (this.destroyed)
			return;
		this.destroyed = true;
		this.health = 0;
		this.spriteType = SpriteType.Explosion;
		SoundManager.getInstance().playSfx(Sound.ENEMY_EXPLOSION);
	}

	/**
	 * Starts a ram on the player unless one happened a moment ago, so a
	 * tough enemy touching the player is not damaged every frame.
	 *
	 * @return True if this contact counts as a ram.
	 */
	public final boolean tryRam() {
		if (this.ramCooldown > 0 || !isHittable())
			return false;
		this.ramCooldown = RAM_COOLDOWN_FRAMES;
		return true;
	}

	/** @return True while it can be hit: entered, not destroyed, below the HUD. */
	public final boolean isHittable() {
		return !this.destroyed && this.distance >= 0
				&& this.distance <= this.path.length() && this.centerY >= this.top;
	}

	/** @return True while its center is inside the play area. */
	public final boolean isOnScreen() {
		return this.centerX >= 0 && this.centerX <= this.screenWidth
				&& this.centerY >= this.top && this.centerY <= this.screenHeight;
	}

	/** @return True while it should be drawn this frame. */
	public final boolean isVisible() {
		if (this.destroyed)
			return this.explosionFrames < EXPLOSION_FRAMES
					&& (this.explosionFrames / EXPLOSION_BLINK_FRAMES) % 2 == 0;
		return isHittable();
	}

	/** @return True once it can be removed: explosion over, or route flown. */
	public final boolean isFinished() {
		if (this.destroyed)
			return this.explosionFrames >= EXPLOSION_FRAMES;
		return this.distance > this.path.length();
	}

	/** @return True if destroyed (exploding or gone). */
	public final boolean isDestroyed() {
		return this.destroyed;
	}

	/** @return Colour to draw with this frame: white while flashing. */
	public final Color getDrawColor() {
		return this.hitFlashFrames > 0 ? Color.WHITE : getColor();
	}

	/** @return Left edge to draw the current sprite at. */
	public final int getDrawX() {
		if (!this.destroyed)
			return this.positionX;
		return (int) Math.round(this.centerX
				- EXPLOSION_COLUMNS * this.stats.pixelSize / 2.0);
	}

	/** @return Top edge to draw the current sprite at. */
	public final int getDrawY() {
		if (!this.destroyed)
			return this.positionY;
		return (int) Math.round(this.centerY
				- EXPLOSION_ROWS * this.stats.pixelSize / 2.0);
	}

	/** @return Catalogue entry. */
	public final EnemyType getType() {
		return this.type;
	}

	/** @return Numbers for this enemy. */
	public final EnemyStats getStats() {
		return this.stats;
	}

	/** @return Route it flies. */
	public final EnemyPath getPath() {
		return this.path;
	}

	/** @return Score for destroying it. */
	public final int getPointValue() {
		return this.stats.points;
	}

	/** @return Hits left. */
	public final int getHealth() {
		return this.health;
	}

	/** @return Size of one sprite pixel on screen. */
	public final int getPixelSize() {
		return this.stats.pixelSize;
	}

	/** @return Exact center x. */
	public final double getCenterX() {
		return this.centerX;
	}

	/** @return Exact center y. */
	public final double getCenterY() {
		return this.centerY;
	}

	/** @return Direction of travel, unit vector x. */
	public final double getHeadingX() {
		return this.headingX;
	}

	/** @return Direction of travel, unit vector y. */
	public final double getHeadingY() {
		return this.headingY;
	}

	/** @return Distance flown along the path. */
	public final double getDistance() {
		return this.distance;
	}

	/** Places the enemy where its distance along the path says. */
	private void updatePosition() {
		double[] point = this.path.pointAt(this.distance);
		double[] heading = this.path.headingAt(this.distance);
		this.centerX = point[0];
		this.centerY = point[1];
		this.headingX = heading[0];
		this.headingY = heading[1];
		this.positionX = (int) Math.round(this.centerX - this.width / 2.0);
		this.positionY = (int) Math.round(this.centerY - this.height / 2.0);
	}
}
