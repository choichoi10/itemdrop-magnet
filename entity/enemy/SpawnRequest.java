package entity.enemy;

/**
 * A spawn command: which enemy type to bring in, and optionally which
 * movement pattern and how many. Anything left out is taken from the
 * catalogue: a random allowed pattern and the type's formation size.
 */
public final class SpawnRequest {

	/** Enemy type to spawn. */
	private final EnemyType type;
	/** Movement pattern, or null for a random allowed one. */
	private final MovePattern pattern;
	/** Formation size, or 0 for the type's default. */
	private final int count;

	private SpawnRequest(final EnemyType type, final MovePattern pattern,
			final int count) {
		if (type == null)
			throw new IllegalArgumentException("Enemy type is required");
		if (pattern != null && !type.allows(pattern))
			throw new IllegalArgumentException(type + " cannot fly " + pattern
					+ "; allowed: " + type.getPatterns());
		if (count < 0)
			throw new IllegalArgumentException("Count must be 0 (default) or more");
		this.type = type;
		this.pattern = pattern;
		this.count = count;
	}

	/**
	 * @param type Enemy type.
	 * @return A formation of the type's default size on a random pattern.
	 */
	public static SpawnRequest of(final EnemyType type) {
		return new SpawnRequest(type, null, 0);
	}

	/**
	 * @param type    Enemy type.
	 * @param pattern One of the type's allowed patterns.
	 * @return A formation of the type's default size on that pattern.
	 */
	public static SpawnRequest of(final EnemyType type, final MovePattern pattern) {
		return new SpawnRequest(type, pattern, 0);
	}

	/**
	 * @param type    Enemy type.
	 * @param pattern One of the type's allowed patterns, or null for random.
	 * @param count   Formation size, or 0 for the type's default.
	 * @return The request.
	 */
	public static SpawnRequest of(final EnemyType type,
			final MovePattern pattern, final int count) {
		return new SpawnRequest(type, pattern, count);
	}

	/** @return Enemy type to spawn. */
	public EnemyType getType() {
		return this.type;
	}

	/** @return Movement pattern, or null for a random allowed one. */
	public MovePattern getPattern() {
		return this.pattern;
	}

	/** @return Formation size, or 0 for the type's default. */
	public int getCount() {
		return this.count;
	}

	@Override
	public String toString() {
		return this.type + (this.pattern == null ? "" : " " + this.pattern)
				+ (this.count == 0 ? "" : " x" + this.count);
	}
}
