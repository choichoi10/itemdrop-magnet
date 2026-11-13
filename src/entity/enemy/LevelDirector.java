package entity.enemy;

/**
 * Decides when and what enemies spawn during a level: the slot for the
 * level designer system. The game calls {@link #update} once per active
 * frame (not while paused or counting down) and ends the level once
 * {@link #isFinished()} is true and every enemy is gone.
 */
public interface LevelDirector {

	/**
	 * Advances one frame and sends any spawn commands that are due.
	 *
	 * @param manager Receives spawn commands.
	 * @param playerX Player center x.
	 * @param playerY Player center y.
	 */
	void update(EnemyManager manager, double playerX, double playerY);

	/** @return True once nothing more will spawn this level. */
	boolean isFinished();
}
