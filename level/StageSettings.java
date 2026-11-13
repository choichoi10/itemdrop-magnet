package level;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import entity.enemy.EnemyType;
import entity.enemy.StageModifiers;

/**
 * One stage's difficulty settings, read from res/levels/stageN.properties:
 * how long enemies keep coming, how much pressure may be on screen, which
 * enemies can appear and how often, and the stage multipliers.
 *
 * File lookup order: -Dinvaders.levels=&lt;directory&gt; &gt; res/levels &gt;
 * levels/ on the classpath. Values are checked strictly: a wrong value or an
 * unknown key (usually a typo) stops the game with the file and key named.
 * Immutable.
 */
public final class StageSettings {

	/** System property that overrides the stage file directory. */
	public static final String PATH_PROPERTY = "invaders.levels";
	/** Default stage file directory. */
	public static final String DEFAULT_DIRECTORY = "res/levels";

	/** Seconds enemies keep spawning. */
	private final double duration;
	/** Seconds before the first spawn. */
	private final double startDelay;
	/** Pressure allowed on screen at the start of the stage. */
	private final double pressureStart;
	/** Pressure allowed on screen when the spawning time ends. */
	private final double pressureEnd;
	/** Minimum seconds between two spawns. */
	private final double spawnGap;
	/** Extra random seconds, 0 to this, added to each gap. */
	private final double spawnGapRandom;
	/** How often each enemy type is picked; missing types never appear. */
	private final Map<EnemyType, Double> weights;
	/** Stage multipliers on enemy values. */
	private final StageModifiers modifiers;

	/**
	 * Reads settings from loaded key/value pairs.
	 *
	 * @param settings Loaded key/value pairs.
	 * @param source   File name used in error messages.
	 * @return The checked settings.
	 * @throws IllegalArgumentException When a value is wrong or a key unknown.
	 */
	public static StageSettings fromProperties(final Properties settings,
			final String source) {
		return new StageSettings(settings, source);
	}

	/**
	 * Finds and reads a stage's file.
	 *
	 * @param level Stage number.
	 * @return The stage's settings.
	 * @throws IllegalStateException When the file is missing or unreadable.
	 * @throws IllegalArgumentException When a value is wrong.
	 */
	public static StageSettings load(final int level) {
		String fileName = "stage" + level + ".properties";
		String configured = System.getProperty(PATH_PROPERTY);
		Path path = Paths.get(configured != null ? configured : DEFAULT_DIRECTORY,
				fileName);
		Properties settings = new Properties();
		if (configured != null || Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				settings.load(reader);
			} catch (IOException | IllegalArgumentException error) {
				throw new IllegalStateException("cannot read stage file: "
						+ path.toAbsolutePath(), error);
			}
			return new StageSettings(settings, path.toString());
		}
		InputStream stream = StageSettings.class.getClassLoader()
				.getResourceAsStream("levels/" + fileName);
		if (stream == null)
			throw new IllegalStateException("No stage file for level " + level
					+ ": expected " + path.toAbsolutePath());
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			settings.load(reader);
		} catch (IOException | IllegalArgumentException error) {
			throw new IllegalStateException("cannot read stage resource levels/"
					+ fileName, error);
		}
		return new StageSettings(settings, "levels/" + fileName);
	}

	private StageSettings(final Properties settings, final String source) {
		Values read = new Values(settings, source);
		this.duration = read.positive("duration", null);
		this.startDelay = read.nonNegative("startDelay", 1.0);
		this.pressureStart = read.positive("pressureStart", null);
		this.pressureEnd = read.positive("pressureEnd", this.pressureStart);
		this.spawnGap = read.nonNegative("spawnGap", 1.0);
		this.spawnGapRandom = read.nonNegative("spawnGapRandom", 1.0);

		Map<EnemyType, Double> loaded = new EnumMap<EnemyType, Double>(EnemyType.class);
		for (EnemyType type : EnemyType.values()) {
			double weight = read.nonNegative("weight." + type.getBalanceKey(), 0.0);
			if (weight > 0)
				loaded.put(type, weight);
		}
		if (loaded.isEmpty())
			throw read.invalid("weight.*", "(none)",
					"set for at least one enemy type, such as weight.normal=1");
		this.weights = Collections.unmodifiableMap(loaded);

		this.modifiers = new StageModifiers(
				read.positive("speed", 1.0),
				read.positive("bulletSpeed", 1.0),
				read.positive("health", 1.0),
				read.positive("fireRate", 1.0),
				read.positive("points", 1.0));
		read.rejectUnknownKeys();
	}

	/** @return Seconds enemies keep spawning. */
	public double getDuration() {
		return this.duration;
	}

	/** @return Seconds before the first spawn. */
	public double getStartDelay() {
		return this.startDelay;
	}

	/** @return Pressure allowed on screen at the start. */
	public double getPressureStart() {
		return this.pressureStart;
	}

	/** @return Pressure allowed on screen when spawning ends. */
	public double getPressureEnd() {
		return this.pressureEnd;
	}

	/** @return Minimum seconds between two spawns. */
	public double getSpawnGap() {
		return this.spawnGap;
	}

	/** @return Extra random seconds added to each gap, up to this. */
	public double getSpawnGapRandom() {
		return this.spawnGapRandom;
	}

	/** @return Pick weight of each enemy type that can appear. */
	public Map<EnemyType, Double> getWeights() {
		return this.weights;
	}

	/** @return Stage multipliers on enemy values. */
	public StageModifiers getModifiers() {
		return this.modifiers;
	}

	/** Reads and checks values, remembering which keys were used. */
	private static final class Values {
		/** Loaded key/value pairs. */
		private final Properties settings;
		/** File name for error messages. */
		private final String source;
		/** Keys read so far. */
		private final Set<String> known = new HashSet<String>();

		Values(final Properties settings, final String source) {
			this.settings = settings;
			this.source = source;
		}

		/** Reads a number above zero; a null fallback makes the key required. */
		double positive(final String key, final Double fallback) {
			double value = number(key, fallback);
			if (value <= 0)
				throw invalid(key, value, "> 0");
			return value;
		}

		/** Reads a number of zero or more. */
		double nonNegative(final String key, final Double fallback) {
			double value = number(key, fallback);
			if (value < 0)
				throw invalid(key, value, ">= 0");
			return value;
		}

		private double number(final String key, final Double fallback) {
			this.known.add(key);
			String raw = this.settings.getProperty(key);
			if (raw == null || raw.trim().isEmpty()) {
				if (fallback == null)
					throw invalid(key, "(missing)", "set");
				return fallback;
			}
			try {
				double value = Double.parseDouble(raw.trim());
				if (Double.isNaN(value) || Double.isInfinite(value))
					throw invalid(key, raw.trim(), "a finite number");
				return value;
			} catch (NumberFormatException error) {
				throw invalid(key, raw.trim(), "a number");
			}
		}

		/** Fails on any key that was never read, which is usually a typo. */
		void rejectUnknownKeys() {
			for (String key : this.settings.stringPropertyNames())
				if (!this.known.contains(key))
					throw new IllegalArgumentException(this.source
							+ ": unknown key '" + key + "'. Known keys: "
							+ new java.util.TreeSet<String>(this.known));
		}

		IllegalArgumentException invalid(final String key, final Object value,
				final String expected) {
			return new IllegalArgumentException(this.source + ": " + key + "="
					+ value + " must be " + expected);
		}
	}
}
