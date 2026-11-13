package entity.enemy;

/**
 * Tunable numbers of one enemy type, read from the enemy balance file.
 * Speeds are pixels per frame and intervals are frames (the game runs at
 * 60 frames per second). Immutable.
 */
public final class EnemyStats {

	/** Hits needed to destroy the enemy. */
	public final int health;
	/** Score awarded when the player destroys it. */
	public final int points;
	/** Movement speed along its path, in pixels per frame. */
	public final double speed;
	/** Number of enemies flying in one formation. */
	public final int formationSize;
	/** Frames between two shots. Unused by enemies that do not shoot. */
	public final int fireInterval;
	/** Speed of the bullets it fires, in pixels per frame. */
	public final double bulletSpeed;
	/** Size of one sprite pixel on screen; 2 is the game's usual size. */
	public final int pixelSize;
	/** Angle between the bullets of a fan shot, in degrees. */
	public final double spreadDegrees;
	/** How much one enemy of this type weighs on the level's pressure. */
	public final double threat;

	/**
	 * @param health        Hits needed to destroy the enemy.
	 * @param points        Score for destroying it.
	 * @param speed         Movement speed, pixels per frame.
	 * @param formationSize Enemies per formation.
	 * @param fireInterval  Frames between two shots.
	 * @param bulletSpeed   Bullet speed, pixels per frame.
	 * @param pixelSize     Size of one sprite pixel on screen.
	 * @param spreadDegrees Angle between the bullets of a fan shot.
	 * @param threat        Pressure one enemy puts on the level.
	 */
	public EnemyStats(final int health, final int points, final double speed,
			final int formationSize, final int fireInterval,
			final double bulletSpeed, final int pixelSize,
			final double spreadDegrees, final double threat) {
		this.health = health;
		this.points = points;
		this.speed = speed;
		this.formationSize = formationSize;
		this.fireInterval = fireInterval;
		this.bulletSpeed = bulletSpeed;
		this.pixelSize = pixelSize;
		this.spreadDegrees = spreadDegrees;
		this.threat = threat;
	}

	/** @return Pressure a whole default-size formation puts on the level. */
	public double formationThreat() {
		return this.threat * this.formationSize;
	}
}
