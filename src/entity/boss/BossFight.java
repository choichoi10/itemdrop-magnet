package entity.boss;

import engine.DrawManager;
import entity.Entity;
import entity.enemy.EnemyManager;

/**
 * A boss encounter, as the game screen sees it.
 *
 * Boss bullets are {@link entity.enemy.EnemyBullet}s in the enemy manager,
 * and summoned helpers are ordinary enemies there, so the screen's usual
 * enemy code handles them. The boss itself handles what only it has:
 * its body and weak points, lasers and other danger zones, bombs and
 * homing drones.
 */
public interface BossFight {

	/**
	 * Advances the boss one frame. Call only while gameplay runs.
	 *
	 * @param enemies Enemy manager, for bullets and summoned enemies.
	 * @param playerX Player center x.
	 * @param playerY Player center y.
	 * @param frozen  True while the freeze item holds enemies in place.
	 */
	void update(EnemyManager enemies, double playerX, double playerY,
			boolean frozen);

	/**
	 * Checks a player bullet against the boss and applies its damage.
	 *
	 * @param bullet Player bullet.
	 * @return What it hit, or null if it missed; a hit uses up the bullet.
	 */
	BossHit hit(Entity bullet);

	/**
	 * @param ship Player ship.
	 * @return True if something of the boss (body, zone, bomb, drone)
	 *         touches the ship this frame.
	 */
	boolean hurts(Entity ship);

	/** @param drawManager Drawing target. */
	void draw(DrawManager drawManager);

	/** Stops every attack and freezes the boss, for game over. */
	void stop();

	/** @return True once the boss is beaten (it may still be exploding). */
	boolean isDefeated();

	/** @return True once the defeat explosion is over: the stage is clear. */
	boolean isFinished();

	/** @return Remaining health, for the health bar. */
	int getHealth();

	/** @return Full health, for the health bar. */
	int getMaxHealth();

	/** @return Name shown above the health bar. */
	String getName();
}
