package entity.enemy;

import java.util.List;

/** How an enemy shoots. Bullet speed and fan angle come from its stats. */
public enum FireStyle {

	/** Does not shoot. */
	NONE {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			// Never shoots.
		}
	},

	/** One bullet in the direction the enemy is flying. */
	FORWARD {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			shoot(shooter, shooter.getHeadingX(), shooter.getHeadingY(), out);
		}
	},

	/** One bullet at the player. */
	AIMED {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			double angle = angleTo(shooter, targetX, targetY);
			shoot(shooter, Math.cos(angle), Math.sin(angle), out);
		}
	},

	/** Three bullets in a fan, the middle one at the player. */
	AIMED_FAN3 {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			double angle = angleTo(shooter, targetX, targetY);
			double spread = Math.toRadians(shooter.getStats().spreadDegrees);
			for (int i = -1; i <= 1; i++)
				shoot(shooter, Math.cos(angle + i * spread),
						Math.sin(angle + i * spread), out);
		}
	},

	/** One bullet straight down, whichever way the enemy flies. */
	DROP {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			shoot(shooter, 0, 1, out);
		}
	},

	/** Three bullets in a fan around straight down. */
	DROP_FAN3 {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			fan(shooter, Math.PI / 2, 3, out);
		}
	},

	/** A quick burst of three bullets at the player, each one re-aimed. */
	AIMED_BURST3 {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			AIMED.fire(shooter, targetX, targetY, out);
		}

		@Override
		public int getVolley() {
			return 3;
		}
	},

	/**
	 * A ring of bullets all around; the gap between them is the fan angle
	 * (45 degrees gives eight). One bullet points at the player.
	 */
	RING {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			double spread = Math.max(10, shooter.getStats().spreadDegrees);
			int count = (int) Math.round(360 / spread);
			double start = angleTo(shooter, targetX, targetY);
			for (int i = 0; i < count; i++) {
				double angle = start + 2 * Math.PI * i / count;
				shoot(shooter, Math.cos(angle), Math.sin(angle), out);
			}
		}
	},

	/** Five bullets in a fan, the middle one at the player. */
	AIMED_FAN5 {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			fan(shooter, angleTo(shooter, targetX, targetY), 5, out);
		}
	};

	/**
	 * @return Shots fired in a row each time the enemy fires, a few frames
	 *         apart; 1 for a single shot.
	 */
	public int getVolley() {
		return 1;
	}

	/** Adds an odd number of bullets fanned around the given angle. */
	private static void fan(final Enemy shooter, final double angle,
			final int count, final List<EnemyBullet> out) {
		double spread = Math.toRadians(shooter.getStats().spreadDegrees);
		for (int i = -(count / 2); i <= count / 2; i++)
			shoot(shooter, Math.cos(angle + i * spread),
					Math.sin(angle + i * spread), out);
	}

	/**
	 * Fires this style's bullets.
	 *
	 * @param shooter Enemy that shoots.
	 * @param targetX Player center x.
	 * @param targetY Player center y.
	 * @param out     List the new bullets are added to.
	 */
	public abstract void fire(Enemy shooter, double targetX, double targetY,
			List<EnemyBullet> out);

	/** @return Angle from the shooter's center to the target, in radians. */
	private static double angleTo(final Enemy shooter, final double targetX,
			final double targetY) {
		double dx = targetX - shooter.getCenterX();
		double dy = targetY - shooter.getCenterY();
		if (dx == 0 && dy == 0)
			return Math.PI / 2;
		return Math.atan2(dy, dx);
	}

	/** Adds one bullet leaving the shooter's edge in the given direction. */
	private static void shoot(final Enemy shooter, final double directionX,
			final double directionY, final List<EnemyBullet> out) {
		double edge = Math.max(shooter.getWidth(), shooter.getHeight()) / 2.0;
		double speed = shooter.getStats().bulletSpeed;
		out.add(new EnemyBullet(shooter.getCenterX() + directionX * edge,
				shooter.getCenterY() + directionY * edge,
				directionX * speed, directionY * speed));
	}
}
