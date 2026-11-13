package entity.enemy;

import java.util.ArrayList;
import java.util.List;

/**
 * The route an enemy flies, as a chain of straight segments through points
 * (curves are sampled finely enough to look smooth). Positions are looked up
 * by distance travelled, so every enemy moves at a constant speed and
 * formation members can follow the same route a fixed distance apart.
 * Points are enemy centers, in screen pixels. Immutable.
 */
public final class EnemyPath {

	/** X coordinate of each point. */
	private final double[] xs;
	/** Y coordinate of each point. */
	private final double[] ys;
	/** Distance from the first point to each point. */
	private final double[] distances;

	private EnemyPath(final List<double[]> points) {
		if (points.size() < 2)
			throw new IllegalArgumentException("A path needs at least two points");
		int count = points.size();
		this.xs = new double[count];
		this.ys = new double[count];
		this.distances = new double[count];
		for (int i = 0; i < count; i++) {
			this.xs[i] = points.get(i)[0];
			this.ys[i] = points.get(i)[1];
			if (i > 0)
				this.distances[i] = this.distances[i - 1] + Math.hypot(
						this.xs[i] - this.xs[i - 1], this.ys[i] - this.ys[i - 1]);
		}
	}

	/**
	 * @param fromX Start x.
	 * @param fromY Start y.
	 * @param toX   End x.
	 * @param toY   End y.
	 * @return A straight path.
	 */
	public static EnemyPath line(final double fromX, final double fromY,
			final double toX, final double toY) {
		return new Builder().to(fromX, fromY).to(toX, toY).build();
	}

	/** @return Total length in pixels. */
	public double length() {
		return this.distances[this.distances.length - 1];
	}

	/** @return First point, x. */
	public double startX() {
		return this.xs[0];
	}

	/** @return First point, y. */
	public double startY() {
		return this.ys[0];
	}

	/** @return Last point, x. */
	public double endX() {
		return this.xs[this.xs.length - 1];
	}

	/** @return Last point, y. */
	public double endY() {
		return this.ys[this.ys.length - 1];
	}

	/**
	 * Position after travelling a distance; clamped to the path's ends.
	 *
	 * @param distance Distance travelled, in pixels.
	 * @return {x, y}.
	 */
	public double[] pointAt(final double distance) {
		int segment = segmentAt(distance);
		double segmentLength = this.distances[segment + 1] - this.distances[segment];
		double t = segmentLength <= 0 ? 0
				: (clamp(distance) - this.distances[segment]) / segmentLength;
		return new double[] {
				this.xs[segment] + (this.xs[segment + 1] - this.xs[segment]) * t,
				this.ys[segment] + (this.ys[segment + 1] - this.ys[segment]) * t };
	}

	/**
	 * Direction of travel at a distance, as a unit vector.
	 *
	 * @param distance Distance travelled, in pixels.
	 * @return {x, y}; straight down if the segment has no length.
	 */
	public double[] headingAt(final double distance) {
		int segment = segmentAt(distance);
		double dx = this.xs[segment + 1] - this.xs[segment];
		double dy = this.ys[segment + 1] - this.ys[segment];
		double length = Math.hypot(dx, dy);
		if (length <= 0)
			return new double[] { 0, 1 };
		return new double[] { dx / length, dy / length };
	}

	private double clamp(final double distance) {
		return Math.max(0, Math.min(length(), distance));
	}

	/** Index of the segment that contains the distance. */
	private int segmentAt(final double distance) {
		double d = clamp(distance);
		int low = 0;
		int high = this.distances.length - 2;
		while (low < high) {
			int middle = (low + high + 1) / 2;
			if (this.distances[middle] <= d)
				low = middle;
			else
				high = middle - 1;
		}
		return low;
	}

	/** Collects points in flying order. */
	public static final class Builder {
		/** Points added so far. */
		private final List<double[]> points = new ArrayList<double[]>();

		/**
		 * Adds the next point.
		 *
		 * @param x Point x.
		 * @param y Point y.
		 * @return This builder.
		 */
		public Builder to(final double x, final double y) {
			this.points.add(new double[] { x, y });
			return this;
		}

		/** @return The finished path. */
		public EnemyPath build() {
			return new EnemyPath(this.points);
		}
	}
}
