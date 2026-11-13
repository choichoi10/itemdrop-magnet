package entity.enemy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Enemy balance values (health, speed, bullet speed, path shapes...), read
 * from res/enemy-balance.properties so they can be tuned without
 * recompiling.
 *
 * File lookup order: -Dinvaders.enemyBalance=&lt;path&gt; &gt;
 * res/enemy-balance.properties &gt; enemy-balance.properties on the
 * classpath. A missing file or key uses the default below. An invalid value
 * throws instead of being silently corrected, like the item balance file.
 * A loaded instance is immutable; the game reads the file again for every
 * level, so edits apply from the next level.
 */
public final class EnemyBalance {

	/** System property that overrides the file location. */
	public static final String PATH_PROPERTY = "invaders.enemyBalance";
	/** Name of the balance file. */
	public static final String FILE_NAME = "enemy-balance.properties";

	/** Default stats per key prefix, in file order. */
	private static final Map<String, EnemyStats> DEFAULT_STATS;

	static {
		Map<String, EnemyStats> defaults = new LinkedHashMap<String, EnemyStats>();
		// health, points, speed, formationSize, fireInterval, bulletSpeed, pixelSize, spread, threat
		defaults.put("normal", new EnemyStats(1, 10, 2.0, 5, 60, 4.0, 2, 0, 0.2));
		defaults.put("shooter", new EnemyStats(1, 20, 1.5, 4, 90, 4.0, 2, 0, 0.4));
		defaults.put("bat", new EnemyStats(1, 30, 1.8, 3, 120, 3.5, 2, 0, 1.0));
		defaults.put("assassin", new EnemyStats(1, 40, 5.0, 1, 60, 4.0, 2, 0, 2.0));
		defaults.put("tank", new EnemyStats(8, 100, 0.6, 1, 150, 3.0, 4, 15, 4.0));
		// Elites: late-game versions of the types above.
		defaults.put("eliteNormal", new EnemyStats(3, 40, 2.2, 4, 150, 2.5, 3, 0, 0.6));
		defaults.put("eliteShooter", new EnemyStats(3, 70, 1.6, 3, 80, 4.0, 3, 15, 1.2));
		defaults.put("eliteBat", new EnemyStats(4, 100, 1.9, 2, 130, 3.8, 3, 0, 2.5));
		defaults.put("eliteAssassin", new EnemyStats(3, 150, 5.5, 1, 50, 2.5, 3, 45, 4.0));
		defaults.put("eliteTank", new EnemyStats(20, 350, 0.6, 1, 130, 3.0, 5, 12, 8.0));
		DEFAULT_STATS = Collections.unmodifiableMap(defaults);
	}

	/** Stats per key prefix. */
	private final Map<String, EnemyStats> stats;
	/** Extra pixels between formation members along their path. */
	private final int formationGap;
	/** Curve (#1): where it reaches the side, fraction of play height. */
	private final double[] curveEndY;
	/** Side diagonal (#3): start height, fraction of play height. */
	private final double[] sideDiagonalStartY;
	/** Side diagonal (#3): pixels down per pixel across. */
	private final double[] sideDiagonalSlope;
	/** Semicircle (#4): start height, fraction of play height. */
	private final double[] semicircleStartY;
	/** Semicircle (#4): diameter, fraction of play height. */
	private final double[] semicircleDiameter;
	/** Semicircle (#4): how far it reaches into the screen, fraction of width. */
	private final double[] semicircleReach;
	/** Gentle diagonal (#5): sideways drift, fraction of screen width. */
	private final double[] gentleDiagonalDrift;
	/** Sine wave (#7): sideways swing, fraction of screen width. */
	private final double[] sineWaveAmplitude;
	/** Sine wave (#7): fall per full left-right swing, fraction of play height. */
	private final double[] sineWaveWavelength;

	/**
	 * Builds the balance from loaded settings.
	 *
	 * @param settings Loaded key/value pairs; may be empty.
	 */
	EnemyBalance(final Properties settings) {
		Map<String, EnemyStats> loaded = new LinkedHashMap<String, EnemyStats>();
		for (Map.Entry<String, EnemyStats> entry : DEFAULT_STATS.entrySet())
			loaded.put(entry.getKey(),
					readStats(settings, entry.getKey(), entry.getValue()));
		this.stats = Collections.unmodifiableMap(loaded);
		this.formationGap = nonNegativeInt(settings, "formation.gap", 14);
		this.curveEndY = fractionRange(settings, "pattern.curve.endY", 0.5, 0.8);
		this.sideDiagonalStartY = fractionRange(settings,
				"pattern.sideDiagonal.startY", 0.1, 0.45);
		this.sideDiagonalSlope = positiveRange(settings,
				"pattern.sideDiagonal.slope", 0.35, 0.5);
		this.semicircleStartY = fractionRange(settings,
				"pattern.semicircle.startY", 0.1, 0.25);
		this.semicircleDiameter = fractionRange(settings,
				"pattern.semicircle.diameter", 0.45, 0.6);
		this.semicircleReach = fractionRange(settings,
				"pattern.semicircle.reach", 0.45, 0.6);
		this.gentleDiagonalDrift = fractionRange(settings,
				"pattern.gentleDiagonal.drift", 0.12, 0.25);
		this.sineWaveAmplitude = fractionRange(settings,
				"pattern.sineWave.amplitude", 0.12, 0.2);
		this.sineWaveWavelength = positiveRange(settings,
				"pattern.sineWave.wavelength", 0.35, 0.5);
	}

	/** @return The built-in default values. */
	public static EnemyBalance defaults() {
		return new EnemyBalance(new Properties());
	}

	/**
	 * Finds and reads the balance file.
	 *
	 * @return Loaded balance, or the defaults when no file exists.
	 * @throws IllegalStateException When a configured file cannot be read.
	 */
	public static EnemyBalance load() {
		Properties settings = new Properties();
		String configured = System.getProperty(PATH_PROPERTY);
		Path path = Paths.get(configured != null ? configured : "res/" + FILE_NAME);
		if (configured != null || Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				settings.load(reader);
			} catch (IOException | IllegalArgumentException error) {
				throw new IllegalStateException("cannot read enemy balance: "
						+ path.toAbsolutePath(), error);
			}
		} else {
			InputStream stream = EnemyBalance.class.getClassLoader()
					.getResourceAsStream(FILE_NAME);
			if (stream != null) {
				try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
					settings.load(reader);
				} catch (IOException | IllegalArgumentException error) {
					throw new IllegalStateException("cannot read enemy balance resource", error);
				}
			}
		}
		return new EnemyBalance(settings);
	}

	/**
	 * @param key Key prefix of an enemy type, such as "tank".
	 * @return Stats for that type.
	 */
	public EnemyStats stats(final String key) {
		EnemyStats found = this.stats.get(key);
		if (found == null)
			throw new IllegalArgumentException("Unknown enemy balance key: " + key);
		return found;
	}

	/** @return Extra pixels between formation members along their path. */
	public int getFormationGap() {
		return this.formationGap;
	}

	/** @return Curve end height range, fraction of play height. */
	public double[] getCurveEndY() {
		return this.curveEndY.clone();
	}

	/** @return Side diagonal start height range, fraction of play height. */
	public double[] getSideDiagonalStartY() {
		return this.sideDiagonalStartY.clone();
	}

	/** @return Side diagonal slope range, pixels down per pixel across. */
	public double[] getSideDiagonalSlope() {
		return this.sideDiagonalSlope.clone();
	}

	/** @return Semicircle start height range, fraction of play height. */
	public double[] getSemicircleStartY() {
		return this.semicircleStartY.clone();
	}

	/** @return Semicircle diameter range, fraction of play height. */
	public double[] getSemicircleDiameter() {
		return this.semicircleDiameter.clone();
	}

	/** @return Semicircle reach range, fraction of screen width. */
	public double[] getSemicircleReach() {
		return this.semicircleReach.clone();
	}

	/** @return Gentle diagonal drift range, fraction of screen width. */
	public double[] getGentleDiagonalDrift() {
		return this.gentleDiagonalDrift.clone();
	}

	/** @return Sine wave swing range, fraction of screen width. */
	public double[] getSineWaveAmplitude() {
		return this.sineWaveAmplitude.clone();
	}

	/** @return Sine wave fall per full swing, fraction of play height. */
	public double[] getSineWaveWavelength() {
		return this.sineWaveWavelength.clone();
	}

	private static EnemyStats readStats(final Properties settings,
			final String prefix, final EnemyStats fallback) {
		return new EnemyStats(
				positiveInt(settings, prefix + ".health", fallback.health),
				nonNegativeInt(settings, prefix + ".points", fallback.points),
				positiveDouble(settings, prefix + ".speed", fallback.speed),
				positiveInt(settings, prefix + ".formationSize", fallback.formationSize),
				positiveInt(settings, prefix + ".fireInterval", fallback.fireInterval),
				positiveDouble(settings, prefix + ".bulletSpeed", fallback.bulletSpeed),
				positiveInt(settings, prefix + ".pixelSize", fallback.pixelSize),
				nonNegativeDouble(settings, prefix + ".spreadDegrees", fallback.spreadDegrees),
				positiveDouble(settings, prefix + ".threat", fallback.threat));
	}

	private static String raw(final Properties settings, final String key) {
		String value = settings.getProperty(key);
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}

	private static double number(final Properties settings, final String key,
			final double fallback) {
		String value = raw(settings, key);
		if (value == null)
			return fallback;
		try {
			double parsed = Double.parseDouble(value);
			if (Double.isNaN(parsed) || Double.isInfinite(parsed))
				throw invalid(key, value, "a finite number");
			return parsed;
		} catch (NumberFormatException error) {
			throw invalid(key, value, "a number");
		}
	}

	private static long integer(final Properties settings, final String key,
			final long fallback) {
		String value = raw(settings, key);
		if (value == null)
			return fallback;
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException error) {
			throw invalid(key, value, "an integer");
		}
	}

	private static double positiveDouble(final Properties settings,
			final String key, final double fallback) {
		double value = number(settings, key, fallback);
		if (value <= 0)
			throw invalid(key, value, "> 0");
		return value;
	}

	private static double nonNegativeDouble(final Properties settings,
			final String key, final double fallback) {
		double value = number(settings, key, fallback);
		if (value < 0)
			throw invalid(key, value, ">= 0");
		return value;
	}

	private static int positiveInt(final Properties settings, final String key,
			final int fallback) {
		long value = integer(settings, key, fallback);
		if (value <= 0 || value > Integer.MAX_VALUE)
			throw invalid(key, value, "> 0");
		return (int) value;
	}

	private static int nonNegativeInt(final Properties settings,
			final String key, final int fallback) {
		long value = integer(settings, key, fallback);
		if (value < 0 || value > Integer.MAX_VALUE)
			throw invalid(key, value, ">= 0");
		return (int) value;
	}

	/** Reads key + "Min" and key + "Max", each between 0 and 1. */
	private static double[] fractionRange(final Properties settings,
			final String key, final double min, final double max) {
		double[] range = range(settings, key, min, max);
		if (range[0] < 0 || range[1] > 1)
			throw invalid(key + "Min/Max", range[0] + ".." + range[1],
					"between 0 and 1");
		return range;
	}

	/** Reads key + "Min" and key + "Max", both above zero. */
	private static double[] positiveRange(final Properties settings,
			final String key, final double min, final double max) {
		double[] range = range(settings, key, min, max);
		if (range[0] <= 0)
			throw invalid(key + "Min", range[0], "> 0");
		return range;
	}

	private static double[] range(final Properties settings, final String key,
			final double min, final double max) {
		double low = number(settings, key + "Min", min);
		double high = number(settings, key + "Max", max);
		if (low > high)
			throw invalid(key + "Min", low, "<= " + key + "Max (" + high + ")");
		return new double[] { low, high };
	}

	private static IllegalArgumentException invalid(final String key,
			final Object value, final String expected) {
		return new IllegalArgumentException("enemy balance " + key + "="
				+ value + " must be " + expected);
	}
}
