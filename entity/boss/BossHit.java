package entity.boss;

/** Result of a player bullet hitting a boss. */
public final class BossHit {

	/** Points earned by this hit (0 for a plain hit). */
	public final int points;
	/** Where to show effects. */
	public final double x;
	/** Where to show effects. */
	public final double y;
	/** True if it hurt something; false if the armour absorbed it. */
	public final boolean damaged;
	/** True if it destroyed a part (a core or a drone). */
	public final boolean partDestroyed;
	/** True if it was the blow that beat the boss. */
	public final boolean bossDefeated;

	BossHit(final int points, final double x, final double y,
			final boolean damaged, final boolean partDestroyed,
			final boolean bossDefeated) {
		this.points = points;
		this.x = x;
		this.y = y;
		this.damaged = damaged;
		this.partDestroyed = partDestroyed;
		this.bossDefeated = bossDefeated;
	}

	/** @return A bullet stopped by armour. */
	static BossHit absorbed(final double x, final double y) {
		return new BossHit(0, x, y, false, false, false);
	}

	/** @return A hit that only took health. */
	static BossHit damage(final double x, final double y) {
		return new BossHit(0, x, y, true, false, false);
	}
}
