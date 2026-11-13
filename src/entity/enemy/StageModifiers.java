package entity.enemy;

/**
 * Multipliers a stage applies on top of the enemy balance values, so later
 * stages can be harder without changing each enemy type. 1.0 leaves a value
 * unchanged. Immutable.
 */
public final class StageModifiers {

	/** No change to any value. */
	public static final StageModifiers NONE = new StageModifiers(1, 1, 1, 1, 1);

	/** Multiplies movement speed. */
	private final double speed;
	/** Multiplies bullet speed. */
	private final double bulletSpeed;
	/** Multiplies health (rounded, at least 1). */
	private final double health;
	/** Multiplies how often enemies fire: 1.1 fires 10% more often. */
	private final double fireRate;
	/** Multiplies the score for a kill. */
	private final double points;

	/**
	 * @param speed       Movement speed multiplier.
	 * @param bulletSpeed Bullet speed multiplier.
	 * @param health      Health multiplier.
	 * @param fireRate    Fire rate multiplier.
	 * @param points      Score multiplier.
	 */
	public StageModifiers(final double speed, final double bulletSpeed,
			final double health, final double fireRate, final double points) {
		requirePositive("speed", speed);
		requirePositive("bulletSpeed", bulletSpeed);
		requirePositive("health", health);
		requirePositive("fireRate", fireRate);
		requirePositive("points", points);
		this.speed = speed;
		this.bulletSpeed = bulletSpeed;
		this.health = health;
		this.fireRate = fireRate;
		this.points = points;
	}

	/**
	 * @param stats Balance values of an enemy type.
	 * @return The values with this stage's multipliers applied. Formation
	 *         size, size on screen, fan angle and threat are not changed.
	 */
	public EnemyStats apply(final EnemyStats stats) {
		return new EnemyStats(
				Math.max(1, (int) Math.round(stats.health * this.health)),
				(int) Math.round(stats.points * this.points),
				stats.speed * this.speed,
				stats.formationSize,
				Math.max(1, (int) Math.round(stats.fireInterval / this.fireRate)),
				stats.bulletSpeed * this.bulletSpeed,
				stats.pixelSize,
				stats.spreadDegrees,
				stats.threat);
	}

	/** @return Movement speed multiplier. */
	public double getSpeed() {
		return this.speed;
	}

	/** @return Bullet speed multiplier. */
	public double getBulletSpeed() {
		return this.bulletSpeed;
	}

	/** @return Health multiplier. */
	public double getHealth() {
		return this.health;
	}

	/** @return Fire rate multiplier. */
	public double getFireRate() {
		return this.fireRate;
	}

	/** @return Score multiplier. */
	public double getPoints() {
		return this.points;
	}

	private static void requirePositive(final String name, final double value) {
		if (!(value > 0) || Double.isInfinite(value))
			throw new IllegalArgumentException("Stage multiplier " + name
					+ "=" + value + " must be > 0");
	}
}
