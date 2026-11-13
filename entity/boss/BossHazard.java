package entity.boss;

import java.awt.Color;

import engine.DrawManager;
import entity.Entity;

/**
 * A rectangular danger zone: first a harmless warning, then an active
 * phase that hurts the player, then done. Used for lasers, the rising
 * attack and charge lanes. The owner may move or resize it every frame.
 */
public final class BossHazard {

	/** Phase of the zone. */
	public enum Phase {
		/** Shown, harmless. */
		WARNING,
		/** Hurts the player. */
		ACTIVE,
		/** Over; the owner drops it. */
		DONE
	}

	private double x;
	private double y;
	private double width;
	private double height;
	private Phase phase;
	/** Frames left in this phase; -1 means until the owner changes it. */
	private int framesLeft;
	/** Frames of the active phase that follows the warning. */
	private final int activeFrames;
	/** Color of the active phase. */
	private final Color color;
	/** Frames since creation, for blinking. */
	private int age;
	/** True when the attack is about to start: faster, brighter blinking. */
	private boolean urgent;

	/**
	 * @param x              Left edge.
	 * @param y              Top edge.
	 * @param width          Width.
	 * @param height         Height.
	 * @param warningFrames  Warning length; -1 to wait for the owner.
	 * @param activeFrames   Active length after the warning; 0 for a
	 *                       warning-only zone; -1 to wait for the owner.
	 * @param color          Color of the active phase.
	 */
	BossHazard(final double x, final double y, final double width,
			final double height, final int warningFrames,
			final int activeFrames, final Color color) {
		setBounds(x, y, width, height);
		this.phase = Phase.WARNING;
		this.framesLeft = warningFrames;
		this.activeFrames = activeFrames;
		this.color = color;
	}

	/** Advances one frame. */
	void tick() {
		this.age++;
		if (this.framesLeft > 0 && --this.framesLeft == 0) {
			if (this.phase == Phase.WARNING && this.activeFrames != 0)
				activate(this.activeFrames);
			else
				finish();
		}
	}

	/** Moves or resizes the zone. */
	void setBounds(final double x, final double y, final double width,
			final double height) {
		this.x = x;
		this.y = y;
		this.width = Math.max(0, width);
		this.height = Math.max(0, height);
	}

	/** @param frames Active length; -1 to wait for the owner. */
	void activate(final int frames) {
		this.phase = Phase.ACTIVE;
		this.framesLeft = frames;
	}

	/** Makes the warning blink faster and brighter: the attack is close. */
	void setUrgent(final boolean urgent) {
		this.urgent = urgent;
	}

	/** Ends the zone. */
	void finish() {
		this.phase = Phase.DONE;
		this.framesLeft = -1;
	}

	/** @return Current phase. */
	public Phase getPhase() {
		return this.phase;
	}

	/** @return True once over. */
	public boolean isDone() {
		return this.phase == Phase.DONE;
	}

	public double getX() {
		return this.x;
	}

	public double getY() {
		return this.y;
	}

	public double getWidth() {
		return this.width;
	}

	public double getHeight() {
		return this.height;
	}

	/** @return True if it is active and touches the entity. */
	public boolean hurts(final Entity entity) {
		return this.phase == Phase.ACTIVE
				&& overlaps(this.x, this.y, this.width, this.height, entity);
	}

	/** @param drawManager Drawing target. */
	void draw(final DrawManager drawManager) {
		int ix = (int) Math.round(this.x);
		int iy = (int) Math.round(this.y);
		int iw = (int) Math.round(this.width);
		int ih = (int) Math.round(this.height);
		if (iw <= 0 || ih <= 0)
			return;
		if (this.phase == Phase.WARNING) {
			boolean bright = (this.age / (this.urgent ? 3 : 6)) % 2 == 0;
			int fill = this.urgent ? (bright ? 120 : 60) : (bright ? 70 : 40);
			drawManager.fillBox(ix, iy, iw, ih, new Color(255, 60, 60, fill));
			drawManager.drawBox(ix, iy, iw - 1, ih - 1, this.urgent && bright
					? new Color(255, 230, 120)
					: new Color(255, 80, 80, bright ? 200 : 120));
		} else if (this.phase == Phase.ACTIVE) {
			drawManager.fillBox(ix, iy, iw, ih, this.color);
			int core = Math.max(2, iw / 3);
			drawManager.fillBox(ix + (iw - core) / 2, iy, core, ih,
					new Color((this.color.getRed() + 255 * 3) / 4,
							(this.color.getGreen() + 255 * 3) / 4,
							(this.color.getBlue() + 255 * 3) / 4));
		}
	}

	/** @return True if the rectangle and the entity's box overlap. */
	static boolean overlaps(final double x, final double y, final double width,
			final double height, final Entity entity) {
		return x < entity.getPositionX() + entity.getWidth()
				&& entity.getPositionX() < x + width
				&& y < entity.getPositionY() + entity.getHeight()
				&& entity.getPositionY() < y + height;
	}
}
