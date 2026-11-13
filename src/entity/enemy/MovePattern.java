package entity.enemy;

/**
 * The seven enemy movement patterns. Each builds one random route when a
 * formation spawns; every member of the formation then flies that route.
 * Routes start and end off screen, so enemies fly in and out of view.
 */
public enum MovePattern {

	/**
	 * #1: enters from the top and curves down toward the far side, leaving
	 * the screen sideways somewhere in the lower half.
	 */
	CURVE {
		@Override
		public EnemyPath create(final SpawnContext ctx, final int w, final int h) {
			double startX = ctx.between(0.15, 0.85) * ctx.width;
			int direction = startX < ctx.width / 2.0 ? 1 : -1;
			double sideX = direction > 0 ? ctx.width : 0;
			double startY = ctx.top - h / 2.0;
			double endY = ctx.playY(ctx.between(ctx.balance.getCurveEndY()));
			double radiusX = Math.abs(sideX - startX);
			double radiusY = endY - startY;

			// A quarter ellipse: straight down at the start, sideways at the end.
			EnemyPath.Builder path = new EnemyPath.Builder();
			for (int i = 0; i <= CURVE_STEPS; i++) {
				double angle = Math.PI / 2 * i / CURVE_STEPS;
				path.to(sideX - direction * radiusX * Math.cos(angle),
						startY + radiusY * Math.sin(angle));
			}
			return path.to(sideX + direction * w, endY).build();
		}
	},

	/** #2: straight down from a random x. */
	STRAIGHT_DOWN {
		@Override
		public EnemyPath create(final SpawnContext ctx, final int w, final int h) {
			double x = ctx.between(w / 2.0 + EDGE_GAP,
					ctx.width - w / 2.0 - EDGE_GAP);
			return EnemyPath.line(x, ctx.top - h / 2.0, x, ctx.height + h);
		}
	},

	/**
	 * #3: enters from the left or right side, between the top and the
	 * middle, and crosses the screen diagonally downward.
	 */
	SIDE_DIAGONAL {
		@Override
		public EnemyPath create(final SpawnContext ctx, final int w, final int h) {
			boolean fromLeft = ctx.random.nextBoolean();
			double startX = fromLeft ? -w / 2.0 : ctx.width + w / 2.0;
			double endX = fromLeft ? ctx.width + w : -w;
			double startY = ctx.playY(ctx.between(
					ctx.balance.getSideDiagonalStartY()));
			double slope = ctx.between(ctx.balance.getSideDiagonalSlope());
			return EnemyPath.line(startX, startY, endX,
					startY + slope * Math.abs(endX - startX));
		}
	},

	/**
	 * #4: enters from the left or right side, swings round a half circle
	 * and leaves through the same side lower down.
	 */
	SEMICIRCLE {
		@Override
		public EnemyPath create(final SpawnContext ctx, final int w, final int h) {
			boolean fromLeft = ctx.random.nextBoolean();
			int direction = fromLeft ? 1 : -1;
			double sideX = fromLeft ? 0 : ctx.width;
			double startY = ctx.playY(ctx.between(
					ctx.balance.getSemicircleStartY()));
			double radiusY = ctx.playHeight()
					* ctx.between(ctx.balance.getSemicircleDiameter()) / 2;
			// Half an ellipse: its depth into the screen is set on its own.
			double radiusX = ctx.width * Math.min(MAX_SEMICIRCLE_REACH,
					ctx.between(ctx.balance.getSemicircleReach()));
			double centerY = startY + radiusY;

			EnemyPath.Builder path = new EnemyPath.Builder()
					.to(sideX - direction * w, startY);
			for (int i = 0; i <= CURVE_STEPS * 2; i++) {
				double angle = -Math.PI / 2 + Math.PI * i / (CURVE_STEPS * 2);
				path.to(sideX + direction * radiusX * Math.cos(angle),
						centerY + radiusY * Math.sin(angle));
			}
			return path.to(sideX - direction * w, startY + 2 * radiusY).build();
		}
	},

	/**
	 * #5: falls from the top at a gentle angle; the start is picked so the
	 * enemy still reaches the bottom instead of leaving through a side.
	 */
	GENTLE_DIAGONAL {
		@Override
		public EnemyPath create(final SpawnContext ctx, final int w, final int h) {
			double drift = ctx.width * ctx.between(
					ctx.balance.getGentleDiagonalDrift());
			if (ctx.random.nextBoolean())
				drift = -drift;
			double low = w / 2.0 + EDGE_GAP;
			double high = ctx.width - w / 2.0 - EDGE_GAP;
			double startX = ctx.between(Math.max(low, low - drift),
					Math.min(high, high - drift));
			return EnemyPath.line(startX, ctx.top - h / 2.0,
					startX + drift, ctx.height + h);
		}
	},

	/**
	 * #6: starts on one of the two sides farthest from the player and
	 * flies straight at where the player was; the direction never changes.
	 */
	CHARGE {
		@Override
		public EnemyPath create(final SpawnContext ctx, final int w, final int h) {
			boolean playerOnLeft = ctx.playerX < ctx.width / 2.0;
			boolean playerOnTop = ctx.playerY < (ctx.top + ctx.height) / 2.0;
			double startX;
			double startY;
			if (ctx.random.nextBoolean()) {
				// Far left or right side.
				startX = playerOnLeft ? ctx.width + w / 2.0 : -w / 2.0;
				startY = ctx.playY(ctx.between(SIDE_SPAN_MIN, SIDE_SPAN_MAX));
			} else {
				// Far top or bottom side.
				startX = ctx.width * ctx.between(SIDE_SPAN_MIN, SIDE_SPAN_MAX);
				startY = playerOnTop ? ctx.height + h / 2.0 : ctx.top - h / 2.0;
			}
			double dx = ctx.playerX - startX;
			double dy = ctx.playerY - startY;
			double length = Math.hypot(dx, dy);
			if (length < 1) {
				dx = 0;
				dy = 1;
				length = 1;
			}
			// Long enough to cross the whole screen and leave it.
			double travel = Math.hypot(ctx.width, ctx.height) + 2 * Math.max(w, h);
			return EnemyPath.line(startX, startY,
					startX + dx / length * travel, startY + dy / length * travel);
		}
	},

	/**
	 * #7: falls from the top weaving left and right like a sine wave, around
	 * a random x picked so the whole wave stays on screen.
	 */
	SINE_WAVE {
		@Override
		public EnemyPath create(final SpawnContext ctx, final int w, final int h) {
			double amplitude = ctx.width * ctx.between(
					ctx.balance.getSineWaveAmplitude());
			double wavelength = Math.max(1, ctx.playHeight() * ctx.between(
					ctx.balance.getSineWaveWavelength()));
			// Keep the widest swing inside the screen.
			double room = (ctx.width - w) / 2.0 - EDGE_GAP;
			amplitude = Math.max(0, Math.min(amplitude, room));
			double low = w / 2.0 + EDGE_GAP + amplitude;
			double high = ctx.width - w / 2.0 - EDGE_GAP - amplitude;
			double centerX = low < high ? ctx.between(low, high) : ctx.width / 2.0;
			int side = ctx.random.nextBoolean() ? 1 : -1;
			double startY = ctx.top - h / 2.0;
			double endY = ctx.height + h;

			EnemyPath.Builder path = new EnemyPath.Builder();
			for (double y = startY; y < endY; y += SINE_STEP)
				path.to(centerX + side * amplitude
						* Math.sin(2 * Math.PI * (y - startY) / wavelength), y);
			return path.to(centerX + side * amplitude
					* Math.sin(2 * Math.PI * (endY - startY) / wavelength), endY)
					.build();
		}
	};

	/** Pixels down between the points of a sine wave route. */
	private static final double SINE_STEP = 4;

	/** Segments used to draw a quarter of a curve. */
	private static final int CURVE_STEPS = 24;
	/** Gap kept from the screen sides when picking a start x. */
	private static final double EDGE_GAP = 4;
	/** Widest a semicircle may reach into the screen, fraction of width. */
	private static final double MAX_SEMICIRCLE_REACH = 0.9;
	/** Part of a side a charge may start from, avoiding the corners. */
	private static final double SIDE_SPAN_MIN = 0.05;
	/** Part of a side a charge may start from, avoiding the corners. */
	private static final double SIDE_SPAN_MAX = 0.95;

	/**
	 * Builds one random route for an enemy of the given size.
	 *
	 * @param ctx Play area, player position, balance and random source.
	 * @param w   Enemy width in pixels.
	 * @param h   Enemy height in pixels.
	 * @return The route, through enemy centers.
	 */
	public abstract EnemyPath create(SpawnContext ctx, int w, int h);
}
