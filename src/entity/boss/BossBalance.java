package entity.boss;

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

import entity.enemy.StageModifiers;

/**
 * Boss balance values, read from res/boss-balance.properties so they can be
 * tuned without recompiling.
 *
 * File lookup order: -Dinvaders.bossBalance=&lt;path&gt; &gt;
 * res/boss-balance.properties &gt; boss-balance.properties on the classpath.
 * A missing key uses the default below. Every value must be a number above
 * 0, and unknown keys are rejected so a typo is not silently ignored.
 */
public final class BossBalance {

	/** System property that overrides the file location. */
	public static final String PATH_PROPERTY = "invaders.bossBalance";
	/** Name of the balance file. */
	public static final String FILE_NAME = "boss-balance.properties";
	/** Frames per second the game runs at. */
	private static final int FPS = 60;

	/** Default value per key. */
	private static final Map<String, Double> DEFAULTS;

	static {
		Map<String, Double> d = new LinkedHashMap<String, Double>();
		d.put("fortress.sideCoreHealth", 40.0);
		d.put("fortress.centerCoreHealth", 60.0);
		d.put("fortress.defeatPoints", 1000.0);
		d.put("fortress.sway", 6.0);
		d.put("fortress.restTime", 1.2);
		d.put("fortress.fan.bullets", 5.0);
		d.put("fortress.fan.spreadDegrees", 12.0);
		d.put("fortress.fan.volleys", 4.0);
		d.put("fortress.fan.volleyGap", 0.45);
		d.put("fortress.fan.bulletSpeed", 3.5);
		d.put("fortress.bomb.count", 3.0);
		d.put("fortress.bomb.launchGap", 0.5);
		d.put("fortress.bomb.speed", 3.0);
		d.put("fortress.bomb.bullets", 9.0);
		d.put("fortress.bomb.bulletSpeed", 2.8);
		d.put("fortress.laser.warning", 1.2);
		d.put("fortress.laser.duration", 1.6);
		d.put("fortress.laser.width", 80.0);
		d.put("fortress.sweep.trackTime", 2.0);
		d.put("fortress.sweep.lockTime", 0.8);
		d.put("fortress.sweep.riseSpeed", 10.0);
		d.put("fortress.sweep.columnWidth", 28.0);
		d.put("fortress.sweep.crossfireGap", 0.35);
		d.put("fortress.sweep.crossfireSpeed", 3.0);
		d.put("fortress.sweep.stunTime", 2.0);
		d.put("raider.health", 110.0);
		d.put("raider.defeatPoints", 1500.0);
		d.put("raider.speed", 1.6);
		d.put("raider.patrol.time", 4.0);
		d.put("raider.patrol.fireGap", 0.7);
		d.put("raider.patrol.bulletSpeed", 4.0);
		d.put("raider.patrol.summonGap", 2.0);
		d.put("raider.patrol.summon.normal", 3.0);
		d.put("raider.patrol.summon.shooter", 1.0);
		d.put("raider.patrol.summon.bat", 1.0);
		d.put("raider.charge.warning", 1.0);
		d.put("raider.charge.speed", 9.0);
		d.put("raider.charge.returnSpeed", 3.0);
		d.put("raider.charge.fireGap", 0.1);
		d.put("raider.charge.bulletSpeed", 3.0);
		d.put("raider.drones.time", 3.0);
		d.put("raider.drones.spawnGap", 0.35);
		d.put("raider.drones.speed", 2.6);
		d.put("raider.drones.turnDegrees", 4.0);
		d.put("raider.drones.chaseTime", 2.5);
		d.put("raider.drones.health", 1.0);
		d.put("raider.drones.points", 20.0);
		d.put("common.hitGrace", 2.0);
		for (int level : new int[] {5, 10}) {
			double multiplier = 1 + 0.05 * (level - 1);
			d.put("stage" + level + ".speed", multiplier);
			d.put("stage" + level + ".bulletSpeed", multiplier);
			d.put("stage" + level + ".fireRate", multiplier);
		}
		DEFAULTS = Collections.unmodifiableMap(d);
	}

	/** Value per key, defaults filled in. */
	private final Map<String, Double> values;

	private BossBalance(final Properties settings, final String source) {
		Map<String, Double> loaded = new LinkedHashMap<String, Double>(DEFAULTS);
		for (String key : settings.stringPropertyNames()) {
			if (!DEFAULTS.containsKey(key))
				throw new IllegalArgumentException(source + ": unknown key '"
						+ key + "'");
			String raw = settings.getProperty(key).trim();
			double value;
			try {
				value = Double.parseDouble(raw);
			} catch (NumberFormatException error) {
				throw new IllegalArgumentException(source + ": " + key
						+ " must be a number, was '" + raw + "'");
			}
			// Summon weights may be 0 to leave a type out; the rest must be > 0.
			boolean zeroAllowed = key.contains(".summon.");
			if (Double.isNaN(value) || Double.isInfinite(value) || value < 0
					|| (value == 0 && !zeroAllowed))
				throw new IllegalArgumentException(source + ": " + key
						+ (zeroAllowed ? " must be 0 or more" : " must be above 0")
						+ ", was '" + raw + "'");
			loaded.put(key, value);
		}
		this.values = Collections.unmodifiableMap(loaded);
	}

	/** @return The built-in default values. */
	public static BossBalance defaults() {
		return new BossBalance(new Properties(), "defaults");
	}

	/**
	 * @param settings Key/value pairs.
	 * @param source   Name used in error messages.
	 * @return The checked balance.
	 * @throws IllegalArgumentException When a value is wrong or a key unknown.
	 */
	public static BossBalance fromProperties(final Properties settings,
			final String source) {
		return new BossBalance(settings, source);
	}

	/**
	 * Finds and reads the balance file.
	 *
	 * @return Loaded balance, or the defaults when no file exists.
	 * @throws IllegalStateException When a configured file cannot be read.
	 * @throws IllegalArgumentException When a value is wrong.
	 */
	public static BossBalance load() {
		Properties settings = new Properties();
		String configured = System.getProperty(PATH_PROPERTY);
		Path path = Paths.get(configured != null ? configured : "res/" + FILE_NAME);
		if (configured != null || Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				settings.load(reader);
			} catch (IOException | IllegalArgumentException error) {
				throw new IllegalStateException("cannot read boss balance: "
						+ path.toAbsolutePath(), error);
			}
			return new BossBalance(settings, path.toString());
		}
		InputStream stream = BossBalance.class.getClassLoader()
				.getResourceAsStream(FILE_NAME);
		if (stream == null)
			return defaults();
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			settings.load(reader);
		} catch (IOException | IllegalArgumentException error) {
			throw new IllegalStateException("cannot read boss balance resource "
					+ FILE_NAME, error);
		}
		return new BossBalance(settings, FILE_NAME);
	}

	/**
	 * @param key Balance key.
	 * @return Its value.
	 * @throws IllegalArgumentException When the key does not exist.
	 */
	public double get(final String key) {
		Double value = this.values.get(key);
		if (value == null)
			throw new IllegalArgumentException("no boss balance key " + key);
		return value;
	}

	/** @return The value rounded to a whole count, at least 1. */
	public int count(final String key) {
		return (int) Math.max(1, Math.round(get(key)));
	}

	/** @return The value, in seconds, as frames, at least 1. */
	public int frames(final String key) {
		return Math.max(1, (int) Math.round(get(key) * FPS));
	}

	/**
	 * @param level Stage number.
	 * @return The stage's multipliers, or none for a stage without keys.
	 */
	public StageModifiers modifiers(final int level) {
		String prefix = "stage" + level + ".";
		if (!this.values.containsKey(prefix + "speed"))
			return StageModifiers.NONE;
		return new StageModifiers(get(prefix + "speed"),
				get(prefix + "bulletSpeed"), 1, get(prefix + "fireRate"), 1);
	}

	/** @return Every key with its default, in file order. */
	public static Map<String, Double> defaultValues() {
		return DEFAULTS;
	}
}
