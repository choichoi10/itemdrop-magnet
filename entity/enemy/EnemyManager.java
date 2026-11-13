package entity.enemy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import engine.Core;
import engine.DrawManager;

/**
 * Owns every enemy and enemy bullet in a level. Takes spawn commands, looks
 * the type up in the catalogue and balance file, builds the formation with
 * the shared {@link Enemy} class, and moves, fires, draws and removes them.
 */
public final class EnemyManager implements Iterable<Enemy> {

	/** Height of one "row" when the game over sequence clears enemies. */
	private static final int GAME_OVER_ROW_HEIGHT = 20;

	/** Enemies on the field, including ones waiting to enter or exploding. */
	private final List<Enemy> enemies = new ArrayList<Enemy>();
	/** Enemy bullets in flight. */
	private final List<EnemyBullet> bullets = new ArrayList<EnemyBullet>();
	/** Enemies held by the current freeze: the ones present when it began. */
	private final Set<Enemy> frozenEnemies =
			Collections.newSetFromMap(new IdentityHashMap<Enemy, Boolean>());
	/** Whether the freeze was active last frame. */
	private boolean wasFrozen;
	/** Screen width. */
	private final int width;
	/** Screen height. */
	private final int height;
	/** Top of the play area (the HUD line). */
	private final int top;
	/** Balance values. */
	private final EnemyBalance balance;
	/** Random source for patterns and shot timing. */
	private final Random random;
	/** Stage multipliers applied to every enemy spawned from now on. */
	private StageModifiers stageModifiers = StageModifiers.NONE;
	/** Application logger. */
	private final Logger logger = Core.getLogger();

	/**
	 * Creates a manager using the balance file.
	 *
	 * @param width  Screen width.
	 * @param height Screen height.
	 * @param top    Top of the play area.
	 */
	public EnemyManager(final int width, final int height, final int top) {
		this(width, height, top, EnemyBalance.load(), new Random());
	}

	/**
	 * @param width   Screen width.
	 * @param height  Screen height.
	 * @param top     Top of the play area.
	 * @param balance Balance values.
	 * @param random  Random source.
	 */
	public EnemyManager(final int width, final int height, final int top,
			final EnemyBalance balance, final Random random) {
		this.width = width;
		this.height = height;
		this.top = top;
		this.balance = balance;
		this.random = random;
	}

	/**
	 * Spawns a formation. Members fly the same route one after another.
	 *
	 * @param request What to spawn.
	 * @param playerX Player center x, for patterns that aim at the player.
	 * @param playerY Player center y.
	 * @return The new enemies, leader first.
	 */
	public List<Enemy> spawn(final SpawnRequest request, final double playerX,
			final double playerY) {
		EnemyType type = request.getType();
		EnemyStats stats = statsOf(type);
		List<MovePattern> allowed = type.getPatterns();
		MovePattern pattern = request.getPattern() != null ? request.getPattern()
				: allowed.get(this.random.nextInt(allowed.size()));
		int count = request.getCount() > 0 ? request.getCount() : stats.formationSize;
		int enemyWidth = type.getSpriteColumns() * stats.pixelSize;
		int enemyHeight = type.getSpriteRows() * stats.pixelSize;

		EnemyPath path = pattern.create(new SpawnContext(this.width, this.height,
				this.top, playerX, playerY, this.balance, this.random),
				enemyWidth, enemyHeight);
		double gap = Math.max(enemyWidth, enemyHeight) + this.balance.getFormationGap();

		List<Enemy> spawned = new ArrayList<Enemy>();
		for (int i = 0; i < count; i++) {
			// Spread first shots so a formation does not fire in unison.
			int firstShot = (int) Math.round(stats.fireInterval
					* (0.5 + 0.5 * this.random.nextDouble()));
			Enemy enemy = new Enemy(type, stats, path, -i * gap, firstShot,
					this.width, this.height, this.top);
			this.enemies.add(enemy);
			spawned.add(enemy);
		}
		this.logger.info("Spawned " + count + " " + type + " on " + pattern);
		return spawned;
	}

	/**
	 * Moves every enemy and lets them shoot. Call only while gameplay runs.
	 *
	 * @param frozen  True while the freeze item is active. It holds only the
	 *                enemies that were on the field when it began; enemies
	 *                that spawn during the freeze move normally.
	 * @param playerX Player center x.
	 * @param playerY Player center y.
	 */
	public void updateEnemies(final boolean frozen, final double playerX,
			final double playerY) {
		if (frozen && !this.wasFrozen)
			this.frozenEnemies.addAll(this.enemies);
		else if (!frozen)
			this.frozenEnemies.clear();
		this.wasFrozen = frozen;
		for (Enemy enemy : this.enemies) {
			enemy.move(this.frozenEnemies.contains(enemy));
			enemy.tryFire(playerX, playerY, this.bullets);
		}
	}

	/**
	 * Moves enemy bullets, advances explosions, and removes finished enemies
	 * and bullets that left the screen. Call every frame, so explosions and
	 * bullets settle even after the level ends.
	 */
	public void updateEffects() {
		for (Enemy enemy : this.enemies)
			enemy.tickEffects();
		Iterator<Enemy> enemyIterator = this.enemies.iterator();
		while (enemyIterator.hasNext())
			if (enemyIterator.next().isFinished())
				enemyIterator.remove();
		Iterator<EnemyBullet> bulletIterator = this.bullets.iterator();
		while (bulletIterator.hasNext()) {
			EnemyBullet bullet = bulletIterator.next();
			bullet.update();
			if (bullet.isOutside(this.width, this.height, this.top))
				bulletIterator.remove();
		}
	}

	/**
	 * Draws enemies, then enemy bullets.
	 *
	 * @param drawManager Drawing target.
	 */
	public void draw(final DrawManager drawManager) {
		for (Enemy enemy : this.enemies)
			if (enemy.isVisible())
				drawManager.drawSpriteScaled(enemy.getSpriteType(),
						enemy.getDrawX(), enemy.getDrawY(),
						enemy.getPixelSize(), enemy.getDrawColor());
		for (EnemyBullet bullet : this.bullets)
			bullet.draw(drawManager);
	}

	/**
	 * @return The live list of enemy bullets; removing from it removes the
	 *         bullet from the game.
	 */
	public List<EnemyBullet> getBullets() {
		return this.bullets;
	}

	/** Removes every enemy bullet. */
	public void clearBullets() {
		this.bullets.clear();
	}

	/** Removes destroyed (exploding) enemies at once. */
	public void removeDestroyed() {
		Iterator<Enemy> iterator = this.enemies.iterator();
		while (iterator.hasNext())
			if (iterator.next().isDestroyed())
				iterator.remove();
	}

	/**
	 * Takes the lowest visible enemies (within one enemy row of the lowest)
	 * out of the field, for the game over sequence. When none are visible,
	 * removes the rest (still waiting to enter) and returns nothing.
	 *
	 * @return Removed enemies, empty once the field is clear.
	 */
	public List<Enemy> removeLowestGroup() {
		List<Enemy> group = new ArrayList<Enemy>();
		double lowest = Double.NEGATIVE_INFINITY;
		for (Enemy enemy : this.enemies)
			if (enemy.isHittable() && enemy.isOnScreen())
				lowest = Math.max(lowest, enemy.getCenterY());
		if (lowest == Double.NEGATIVE_INFINITY) {
			this.enemies.clear();
			return group;
		}
		for (Enemy enemy : this.enemies)
			if (enemy.isHittable() && enemy.isOnScreen()
					&& enemy.getCenterY() >= lowest - GAME_OVER_ROW_HEIGHT)
				group.add(enemy);
		this.enemies.removeAll(group);
		return group;
	}

	/** @return Enemies that can be hit and are inside the play area. */
	public int countOnScreen() {
		int count = 0;
		for (Enemy enemy : this.enemies)
			if (enemy.isHittable() && enemy.isOnScreen())
				count++;
		return count;
	}

	/**
	 * Sets the stage multipliers used for enemies spawned from now on.
	 *
	 * @param modifiers Stage multipliers.
	 */
	public void setStageModifiers(final StageModifiers modifiers) {
		this.stageModifiers = modifiers == null ? StageModifiers.NONE : modifiers;
	}

	/** @return Stage multipliers in use. */
	public StageModifiers getStageModifiers() {
		return this.stageModifiers;
	}

	/**
	 * @param type Enemy type.
	 * @return Its balance values with the stage multipliers applied.
	 */
	public EnemyStats statsOf(final EnemyType type) {
		return this.stageModifiers.apply(this.balance.stats(type.getBalanceKey()));
	}

	/**
	 * @return Total threat of enemies not yet destroyed, including formation
	 *         members still waiting to enter.
	 */
	public double currentThreat() {
		double total = 0;
		for (Enemy enemy : this.enemies)
			if (!enemy.isDestroyed())
				total += enemy.getStats().threat;
		return total;
	}

	/**
	 * @return Enemies not yet destroyed, including formation members still
	 *         waiting to enter: the "enemies left" of the old formation.
	 */
	public int countAlive() {
		int count = 0;
		for (Enemy enemy : this.enemies)
			if (!enemy.isDestroyed())
				count++;
		return count;
	}

	/** @return True when no enemy is left, including waiting or exploding ones. */
	public boolean isEmpty() {
		return this.enemies.isEmpty();
	}

	/** @return Number of enemies, including waiting or exploding ones. */
	public int size() {
		return this.enemies.size();
	}

	/** @return Balance values in use. */
	public EnemyBalance getBalance() {
		return this.balance;
	}

	/** Iterates over a copy, so enemies may be destroyed while iterating. */
	@Override
	public Iterator<Enemy> iterator() {
		return new ArrayList<Enemy>(this.enemies).iterator();
	}
}
