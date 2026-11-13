package level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import entity.enemy.EnemyManager;
import entity.enemy.EnemyType;
import entity.enemy.LevelDirector;
import entity.enemy.SpawnRequest;

/**
 * Spawns random formations to keep the stage's pressure up.
 *
 * Every enemy has a threat value; the pressure on screen is the total threat
 * of enemies still alive. The director draws the next formation's type by
 * weight in advance, then spawns it once the spawn gap has passed and it
 * fits under the current pressure cap, which rises from pressureStart to
 * pressureEnd over the stage. Drawing first keeps the mix true to the
 * weights however the player plays; the pressure only decides when. A
 * formation heavier than the cap still comes once the screen is empty. The
 * type spawned last has its weight halved to avoid long runs of the same
 * enemy.
 *
 * Time counts active frames only, so pausing and the start countdown do not
 * use it up.
 */
public final class RandomDirector implements LevelDirector {

	/** Frames per second the game runs at. */
	private static final int FPS = 60;
	/** Weight factor for the type picked last. */
	private static final double REPEAT_WEIGHT = 0.5;
	/** Tolerance when comparing threat totals. */
	private static final double EPSILON = 1e-9;

	/** Stage settings. */
	private final StageSettings settings;
	/** Random source for picks and gaps. */
	private final Random random;
	/** Frames the stage spawns enemies for. */
	private final int durationFrames;
	/** Active frames so far. */
	private int frame;
	/** Frame of the next allowed spawn. */
	private int nextSpawnFrame;
	/** Type spawned last, or null. */
	private EnemyType lastType;
	/** Type drawn for the next spawn, waiting to fit; null until drawn. */
	private EnemyType nextType;
	/** True once the stage multipliers were handed to the manager. */
	private boolean modifiersApplied;
	/** Formations spawned so far. */
	private int spawnCount;

	/**
	 * @param settings Stage settings.
	 * @param random   Random source.
	 */
	public RandomDirector(final StageSettings settings, final Random random) {
		this.settings = settings;
		this.random = random;
		this.durationFrames = Math.max(1, (int) Math.round(settings.getDuration() * FPS));
		this.nextSpawnFrame = (int) Math.round(settings.getStartDelay() * FPS);
	}

	@Override
	public void update(final EnemyManager manager, final double playerX,
			final double playerY) {
		if (!this.modifiersApplied) {
			manager.setStageModifiers(this.settings.getModifiers());
			this.modifiersApplied = true;
		}
		if (isFinished())
			return;
		this.frame++;
		if (this.frame < this.nextSpawnFrame || isFinished())
			return;

		if (this.nextType == null)
			this.nextType = draw();
		EnemyType type = this.nextType;
		if (!fits(manager, type))
			return; // Wait until enough pressure is gone.
		manager.spawn(SpawnRequest.of(type), playerX, playerY);
		this.lastType = type;
		this.nextType = null;
		this.spawnCount++;
		double gap = this.settings.getSpawnGap()
				+ this.random.nextDouble() * this.settings.getSpawnGapRandom();
		this.nextSpawnFrame = this.frame + Math.max(1, (int) Math.round(gap * FPS));
	}

	@Override
	public boolean isFinished() {
		return this.frame >= this.durationFrames;
	}

	/** @return Pressure allowed on screen right now. */
	public double pressureCap() {
		double progress = Math.min(1.0, (double) this.frame / this.durationFrames);
		return this.settings.getPressureStart() + progress
				* (this.settings.getPressureEnd() - this.settings.getPressureStart());
	}

	/** @return Active frames so far. */
	public int getFrame() {
		return this.frame;
	}

	/** @return Formations spawned so far. */
	public int getSpawnCount() {
		return this.spawnCount;
	}

	/** @return Type drawn for the next spawn, or null until drawn. */
	public EnemyType getNextType() {
		return this.nextType;
	}

	/**
	 * @param manager Enemy manager, for the current threat and type values.
	 * @param type    Type about to spawn.
	 * @return True if its formation fits under the cap, or the screen is empty.
	 */
	private boolean fits(final EnemyManager manager, final EnemyType type) {
		double current = manager.currentThreat();
		return current < EPSILON || current + manager.statsOf(type).formationThreat()
				<= pressureCap() + EPSILON;
	}

	/** @return A type drawn by weight, the last spawned type at half weight. */
	private EnemyType draw() {
		List<EnemyType> candidates = new ArrayList<EnemyType>();
		List<Double> weights = new ArrayList<Double>();
		double total = 0;
		for (Map.Entry<EnemyType, Double> entry : this.settings.getWeights().entrySet()) {
			double weight = entry.getValue()
					* (entry.getKey() == this.lastType ? REPEAT_WEIGHT : 1.0);
			candidates.add(entry.getKey());
			weights.add(weight);
			total += weight;
		}
		double roll = this.random.nextDouble() * total;
		for (int i = 0; i < candidates.size(); i++) {
			roll -= weights.get(i);
			if (roll < 0)
				return candidates.get(i);
		}
		return candidates.get(candidates.size() - 1);
	}
}
