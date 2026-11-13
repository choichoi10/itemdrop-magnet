package entity.boss;

import java.util.Locale;
import java.util.Random;

import level.LevelDirectors;

/**
 * Decides which boss appears on which boss stage. Each run meets both
 * bosses once, one on stage 5 and the other on stage 10, in a random order
 * picked when the run reaches its first boss.
 *
 * -Dinvaders.levelSeed fixes the order and the bosses' moves;
 * -Dinvaders.bossOrder=fortress|raider picks the stage 5 boss.
 */
public final class BossRoster {

	/** System property naming the stage 5 boss. */
	public static final String ORDER_PROPERTY = "invaders.bossOrder";

	/** The bosses. */
	public enum Kind {
		/** Boss 1: wide ship with three cores. */
		FORTRESS,
		/** Boss 2: fast ship that charges. */
		RAIDER;

		/** @return A new boss of this kind. */
		public BossFight create(final BossBalance balance, final int level,
				final Random random, final int width, final int height,
				final int top) {
			return this == FORTRESS
					? new FortressBoss(balance, level, random, width, height, top)
					: new RaiderBoss(balance, level, random, width, height, top);
		}

		/** @return The other boss. */
		public Kind other() {
			return this == FORTRESS ? RAIDER : FORTRESS;
		}
	}

	/** Boss of the first boss stage in this run, or null until picked. */
	private static Kind firstBoss;

	private BossRoster() {
	}

	/** Forgets the order; call when a new run starts. */
	public static synchronized void newRun() {
		firstBoss = null;
	}

	/**
	 * @param level Boss stage (5 or 10).
	 * @return Which boss it gets in this run.
	 */
	public static synchronized Kind kindFor(final int level) {
		if (firstBoss == null)
			firstBoss = pickFirst();
		return level >= 10 ? firstBoss.other() : firstBoss;
	}

	/**
	 * Creates the boss of a boss stage, reading the balance file.
	 *
	 * @param level  Boss stage (5 or 10).
	 * @param width  Screen width.
	 * @param height Screen height.
	 * @param top    Top of the play area.
	 * @return The boss.
	 */
	public static BossFight create(final int level, final int width,
			final int height, final int top) {
		Long seed = Long.getLong(LevelDirectors.SEED_PROPERTY);
		Random random = seed == null ? new Random() : new Random(seed + level);
		return kindFor(level).create(BossBalance.load(), level, random, width,
				height, top);
	}

	private static Kind pickFirst() {
		String forced = System.getProperty(ORDER_PROPERTY);
		if (forced != null && !forced.trim().isEmpty())
			return Kind.valueOf(forced.trim().toUpperCase(Locale.ROOT));
		Long seed = Long.getLong(LevelDirectors.SEED_PROPERTY);
		Random random = seed == null ? new Random() : new Random(seed);
		return random.nextBoolean() ? Kind.FORTRESS : Kind.RAIDER;
	}
}
