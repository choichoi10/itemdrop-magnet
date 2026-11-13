package level;

import java.util.Random;

import entity.enemy.LevelDirector;

/**
 * Picks the level director for a stage. Normal stages use a
 * {@link RandomDirector} driven by the stage's file. Another director (for
 * example an endless mode) can be returned from here later without changing
 * the game screen.
 */
public final class LevelDirectors {

	/** System property that fixes the random seed, for testing and balancing. */
	public static final String SEED_PROPERTY = "invaders.levelSeed";

	private LevelDirectors() {
	}

	/**
	 * @param level Stage number.
	 * @return The director for that stage.
	 * @throws IllegalStateException When the stage file is missing.
	 * @throws IllegalArgumentException When the stage file has a wrong value.
	 */
	public static LevelDirector forLevel(final int level) {
		Long seed = Long.getLong(SEED_PROPERTY);
		Random random = seed == null ? new Random() : new Random(seed + level);
		return new RandomDirector(StageSettings.load(level), random);
	}
}
