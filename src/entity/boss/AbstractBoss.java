package entity.boss;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import engine.DrawManager;
import entity.Entity;
import entity.enemy.Enemy;
import entity.enemy.EnemyBullet;
import entity.enemy.EnemyManager;
import entity.enemy.StageModifiers;

/**
 * Shared parts of the bosses: frame clock, stage multipliers, danger
 * zones, stun, defeat explosion and bullet helpers.
 *
 * Time counts active frames only, so pausing does not use up warnings.
 */
abstract class AbstractBoss implements BossFight {

	/** Frames per second the game runs at. */
	protected static final int FPS = 60;
	/** Length of the defeat explosion. */
	private static final int DEATH_FRAMES = 100;
	/** Frames a part flashes white after a hit. */
	protected static final int FLASH_FRAMES = 4;

	protected final BossBalance balance;
	protected final StageModifiers modifiers;
	protected final Random random;
	protected final int width;
	protected final int height;
	/** Top of the play area (the HUD line). */
	protected final int top;
	/** Danger zones on screen. */
	protected final List<BossHazard> hazards = new ArrayList<BossHazard>();

	/** Enemy manager of the current frame. */
	protected EnemyManager enemies;
	/** Active frames so far. */
	protected int frame;
	private int stunFrames;
	/** Frames since defeat, or -1 while fighting. */
	private int deathFrames = -1;
	private boolean stopped;
	private boolean modifiersGiven;
	/** True while the freeze item holds the boss: it hurts no one. */
	private boolean frozen;
	/** Frames left before the boss can hurt the player again. */
	private int hitGrace;

	/**
	 * @param balance Boss balance values.
	 * @param level   Stage number, for the stage multipliers.
	 * @param random  Random source.
	 * @param width   Screen width.
	 * @param height  Screen height.
	 * @param top     Top of the play area.
	 */
	AbstractBoss(final BossBalance balance, final int level,
			final Random random, final int width, final int height,
			final int top) {
		this.balance = balance;
		this.modifiers = balance.modifiers(level);
		this.random = random;
		this.width = width;
		this.height = height;
		this.top = top;
	}

	@Override
	public final void update(final EnemyManager manager, final double playerX,
			final double playerY, final boolean frozen) {
		this.enemies = manager;
		this.frozen = frozen;
		if (this.hitGrace > 0)
			this.hitGrace--;
		if (!this.modifiersGiven) {
			// Summoned helpers get the same stage multipliers.
			manager.setStageModifiers(this.modifiers);
			this.modifiersGiven = true;
		}
		if (this.stopped)
			return;
		if (this.deathFrames >= 0) {
			if (this.deathFrames == 0)
				clearField(manager);
			this.deathFrames++;
			tickEffects();
			return;
		}
		if (frozen)
			return;
		this.frame++;
		tickEffects();
		tickParts(playerX, playerY);
		if (this.stunFrames > 0) {
			this.stunFrames--;
			return;
		}
		act(playerX, playerY);
		Iterator<BossHazard> it = this.hazards.iterator();
		while (it.hasNext()) {
			BossHazard hazard = it.next();
			hazard.tick();
			if (hazard.isDone())
				it.remove();
		}
	}

	/** Advances flashes, bombs, drones; runs even while stunned. */
	protected abstract void tickParts(double playerX, double playerY);

	/** Runs the current pattern for one frame. */
	protected abstract void act(double playerX, double playerY);


	/**
	 * Advances visual effects; runs while stunned and during the defeat
	 * explosion too, so effects started by the last blow finish playing.
	 */
	protected void tickEffects() {
	}

	/** Drops everything the boss has in flight (bombs, drones...). */
	protected abstract void clearParts();

	/** Removes bullets, helpers and zones when the boss is beaten. */
	private void clearField(final EnemyManager manager) {
		manager.clearBullets();
		for (Enemy enemy : manager)
			if (!enemy.isDestroyed())
				enemy.destroy();
		this.hazards.clear();
		clearParts();
	}

	/** Starts the defeat explosion. */
	protected final void defeat() {
		if (this.deathFrames < 0)
			this.deathFrames = 0;
	}

	/**
	 * Stuns the boss: it stops its pattern and zones for a while.
	 *
	 * @param frames Stun length.
	 */
	protected final void stun(final int frames) {
		this.stunFrames = frames;
		this.hazards.clear();
		onStun();
	}

	/** Lets a boss reset its pattern when stunned. */
	protected void onStun() {
	}

	/** @return True while stunned. */
	public final boolean isStunned() {
		return this.stunFrames > 0;
	}

	@Override
	public final void stop() {
		this.stopped = true;
		this.hazards.clear();
		clearParts();
	}

	@Override
	public final boolean isDefeated() {
		return this.deathFrames >= 0;
	}

	@Override
	public final boolean isFinished() {
		return this.deathFrames >= DEATH_FRAMES;
	}

	/** @return True while it can be hit. */
	protected final boolean isFighting() {
		return this.deathFrames < 0 && !this.stopped;
	}

	/** @return Frames since defeat, or -1. */
	protected final int getDeathFrames() {
		return this.deathFrames;
	}

	/**
	 * Lasers, the rising attack, charge lanes and the raider's body stay on
	 * the ship for many frames, so after one hit (or a shield block) the
	 * boss cannot hurt the player again for common.hitGrace seconds. A
	 * frozen boss hurts no one.
	 */
	@Override
	public final boolean hurts(final Entity ship) {
		if (!isFighting() || this.frozen || this.hitGrace > 0
				|| !touches(ship))
			return false;
		this.hitGrace = this.balance.frames("common.hitGrace");
		return true;
	}

	/**
	 * Called only while the boss may hurt the player.
	 *
	 * @param ship Player ship.
	 * @return True if a zone, the body, a bomb or a drone touches the ship;
	 *         a bomb or drone that touches it is used up.
	 */
	protected boolean touches(final Entity ship) {
		for (BossHazard hazard : this.hazards)
			if (hazard.hurts(ship))
				return true;
		return false;
	}

	/* ---- Balance helpers with stage multipliers. ---- */

	/** @return A movement speed with the stage multiplier. */
	protected final double speed(final String key) {
		return this.balance.get(key) * this.modifiers.getSpeed();
	}

	/** @return A bullet speed with the stage multiplier. */
	protected final double bulletSpeed(final String key) {
		return this.balance.get(key) * this.modifiers.getBulletSpeed();
	}

	/** @return A gap between shots, in frames, shortened by the fire rate. */
	protected final int gapFrames(final String key) {
		return Math.max(1, (int) Math.round(this.balance.get(key) * FPS
				/ this.modifiers.getFireRate()));
	}

	/* ---- Bullet helpers. ---- */

	/** Fires one bullet at an angle (radians, 0 = right, PI/2 = down). */
	protected final void fire(final double x, final double y,
			final double angle, final double speed) {
		this.enemies.getBullets().add(new EnemyBullet(x, y,
				Math.cos(angle) * speed, Math.sin(angle) * speed));
	}

	/** Fires an aimed fan of bullets. */
	protected final void fireFan(final double x, final double y,
			final double targetX, final double targetY, final int count,
			final double spreadDegrees, final double speed) {
		double aim = Math.atan2(targetY - y, targetX - x);
		double step = Math.toRadians(spreadDegrees);
		for (int i = 0; i < count; i++)
			fire(x, y, aim + (i - (count - 1) / 2.0) * step, speed);
	}

	/* ---- Drawing helpers. ---- */

	/** Draws the defeat explosion; returns true while the boss is still visible. */
	protected final boolean drawDeath(final DrawManager drawManager,
			final double centerX, final double centerY, final double spreadX,
			final double spreadY) {
		int d = this.deathFrames;
		if (d < 0)
			return true;
		Random sparks = new Random(d * 7919L);
		int count = d < DEATH_FRAMES / 2 ? 6 : 3;
		for (int i = 0; i < count; i++) {
			int size = 6 + sparks.nextInt(14);
			int px = (int) (centerX + (sparks.nextDouble() * 2 - 1) * spreadX);
			int py = (int) (centerY + (sparks.nextDouble() * 2 - 1) * spreadY);
			drawManager.fillBox(px - size / 2, py - size / 2, size, size,
					sparks.nextBoolean() ? new Color(255, 200, 40)
							: new Color(255, 90, 30));
		}
		// The ship blinks, then is gone for the second half.
		return d < DEATH_FRAMES / 2 && (d / 5) % 2 == 0;
	}

	/** Draws the zones. */
	protected final void drawHazards(final DrawManager drawManager) {
		for (BossHazard hazard : this.hazards)
			hazard.draw(drawManager);
	}


	/** @return The color between a and b; t from 0 (a) to 1 (b). */
	protected static Color mix(final Color a, final Color b, final double t) {
		double k = Math.max(0, Math.min(1, t));
		return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * k),
				(int) (a.getGreen() + (b.getGreen() - a.getGreen()) * k),
				(int) (a.getBlue() + (b.getBlue() - a.getBlue()) * k));
	}

	/** @return The color with the given see-through value (0-255). */
	protected static Color alpha(final Color color, final int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.max(0, Math.min(255, alpha)));
	}
	/** @return Copy of the zones, for tests and tools. */
	public final List<BossHazard> getHazards() {
		return new ArrayList<BossHazard>(this.hazards);
	}
}
