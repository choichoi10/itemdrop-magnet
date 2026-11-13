package entity.boss;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import engine.DrawManager;
import entity.Entity;

/**
 * Boss 1: a ship wider than the screen across the top, with three cores
 * (left, center, right). Only the cores take damage; the hull absorbs
 * bullets. It is beaten when all three cores are destroyed, and a
 * destroyed core's attack stops.
 *
 * Patterns, picked at random without repeating the last one:
 * <ol>
 * <li>Fan: the side cores fire aimed 5-way fans, several volleys.</li>
 * <li>Bombs: bombs fly to random spots and burst into rings.</li>
 * <li>Laser: the center core glows, then fires a wide laser down the
 * middle (needs the center core).</li>
 * <li>Sweep: crossfire flies in from the sides while a warning column
 * follows the player; it stops, then an attack rises from below. If it
 * reaches a core, the fortress is stunned for a while.</li>
 * </ol>
 */
public final class FortressBoss extends AbstractBoss {

	/** Attack patterns. */
	private enum Pattern { FAN, BOMB, LASER, SWEEP }

	/** Steps of the sweep pattern. */
	private enum Sweep { TRACK, LOCK, RISE, HOLD }

	/** Hull extends this far past each screen side. */
	private static final int HULL_OVERHANG = 30;
	/** Hull top, below the HUD line. */
	private static final int HULL_TOP = 2;
	/** Hull height. */
	private static final int HULL_HEIGHT = 56;
	/** Frames of one sway back and forth. */
	private static final int SWAY_PERIOD = 300;
	/** Pause before the first attack. */
	private static final int FIRST_REST = 90;
	/** Largest step of the sweep column toward the player per frame. */
	private static final double TRACK_STEP = 5;
	/** Frames the risen attack stays before it ends. */
	private static final int HOLD_FRAMES = 12;

	private static final Color HULL = new Color(62, 36, 98);
	private static final Color HULL_DARK = new Color(36, 20, 58);
	private static final Color HULL_LIGHT = new Color(130, 86, 185);
	private static final Color HULL_STUNNED = new Color(45, 32, 62);
	private static final Color RIB = new Color(40, 22, 66);
	private static final Color RIB_LIGHT = new Color(100, 64, 150);
	private static final Color VEIN = new Color(80, 255, 200);
	private static final Color POD_DIM = new Color(20, 60, 62);
	private static final Color POD_BRIGHT = new Color(130, 255, 215);
	private static final Color GILL = new Color(28, 14, 42);
	private static final Color GILL_GLOW = new Color(225, 70, 215);
	private static final Color SPIKE = new Color(96, 60, 140);
	private static final Color SOCKET = new Color(88, 28, 72);
	private static final Color SOCKET_RIM = new Color(150, 55, 120);
	private static final Color IRIS_DARK = new Color(10, 70, 55);
	private static final Color IRIS = new Color(100, 255, 170);
	private static final Color PUPIL = new Color(8, 6, 18);
	private static final Color SHINE = new Color(235, 255, 240);
	private static final Color OOZE = new Color(120, 255, 120);
	private static final Color LASER = new Color(70, 255, 160);

	/** One weak point. */
	private static final class Core {
		/** Center x without sway. */
		final double centerX;
		/** Top edge. */
		final double y;
		final double width;
		final double height;
		final boolean side;
		final int maxHealth;
		int health;
		int flash;

		Core(final double centerX, final double y, final double width,
				final double height, final boolean side, final int health) {
			this.centerX = centerX;
			this.y = y;
			this.width = width;
			this.height = height;
			this.side = side;
			this.maxHealth = health;
			this.health = health;
		}

		boolean alive() {
			return this.health > 0;
		}
	}

	private final Core left;
	private final Core center;
	private final Core right;
	private final Core[] cores;
	private final List<BossBomb> bombs = new ArrayList<BossBomb>();

	private Pattern current;
	private Pattern last;
	private int restLeft = FIRST_REST;
	private int timer;
	private int countLeft;
	private int swayFrame;
	private double sway;

	private BossHazard laser;
	private BossHazard column;
	private Sweep sweep;
	private double columnX;
	private double columnFront;
	private int crossfireTimer;
	/** Player x, for the eyes to look at. */
	private double lookX;
	private boolean crossfireFromLeft;
	/** Length of the sweep's lock step, for the charging glow. */
	private int lockFrames = 1;
	/** Blasts of destroyed cores, still playing. */
	private final List<Burst> bursts = new ArrayList<Burst>();
	/** Frames left of the white flash over the hull after a core dies. */
	private int hullFlash;

	/** Frames a core blast plays. */
	private static final int BURST_FRAMES = 45;
	/** Frames the hull flashes white when a core dies. */
	private static final int HULL_FLASH_FRAMES = 8;
	/** Debris pieces thrown out by a core blast. */
	private static final int DEBRIS = 14;

	/** The blast where a core was destroyed. */
	private static final class Burst {
		final double x;
		final double y;
		final long seed;
		int age;

		Burst(final double x, final double y, final long seed) {
			this.x = x;
			this.y = y;
			this.seed = seed;
		}
	}

	/**
	 * @param balance Boss balance values.
	 * @param level   Stage number, for the stage multipliers.
	 * @param random  Random source.
	 * @param width   Screen width.
	 * @param height  Screen height.
	 * @param top     Top of the play area.
	 */
	public FortressBoss(final BossBalance balance, final int level,
			final Random random, final int width, final int height,
			final int top) {
		super(balance, level, random, width, height, top);
		int sideHealth = balance.count("fortress.sideCoreHealth");
		this.left = new Core(width * 0.17, top + 44, 36, 24, true, sideHealth);
		this.right = new Core(width * 0.83, top + 44, 36, 24, true, sideHealth);
		this.center = new Core(width / 2.0, top + 49, 60, 22, false,
				balance.count("fortress.centerCoreHealth"));
		this.cores = new Core[] {this.left, this.center, this.right};
	}

	/* ---------------- Frame logic ---------------- */

	@Override
	protected void tickParts(final double playerX, final double playerY) {
		this.lookX = playerX;
		for (Core core : this.cores)
			if (core.flash > 0)
				core.flash--;
		Iterator<BossBomb> it = this.bombs.iterator();
		while (it.hasNext()) {
			BossBomb bomb = it.next();
			bomb.update(this.enemies.getBullets());
			if (bomb.isDone())
				it.remove();
		}
	}

	@Override
	protected void act(final double playerX, final double playerY) {
		this.swayFrame++;
		this.sway = this.balance.get("fortress.sway")
				* Math.sin(this.swayFrame * 2 * Math.PI / SWAY_PERIOD);
		if (this.current == null) {
			if (--this.restLeft <= 0)
				start(choose(), playerX);
			return;
		}
		switch (this.current) {
		case FAN:
			tickFan(playerX, playerY);
			break;
		case BOMB:
			tickBomb();
			break;
		case LASER:
			tickLaser();
			break;
		default:
			tickSweep(playerX);
			break;
		}
	}

	private Pattern choose() {
		List<Pattern> options = new ArrayList<Pattern>();
		if (this.left.alive() || this.right.alive())
			options.add(Pattern.FAN);
		options.add(Pattern.BOMB);
		if (this.center.alive())
			options.add(Pattern.LASER);
		options.add(Pattern.SWEEP);
		if (options.size() > 1)
			options.remove(this.last);
		return options.get(this.random.nextInt(options.size()));
	}

	private void start(final Pattern pattern, final double playerX) {
		this.current = pattern;
		this.timer = 0;
		switch (pattern) {
		case FAN:
			this.countLeft = this.balance.count("fortress.fan.volleys");
			break;
		case BOMB:
			this.countLeft = this.balance.count("fortress.bomb.count");
			break;
		case LASER:
			double w = this.balance.get("fortress.laser.width");
			double y = coreBottom(this.center);
			this.laser = new BossHazard(coreCenterX(this.center) - w / 2, y, w,
					this.height - y, this.balance.frames("fortress.laser.warning"),
					this.balance.frames("fortress.laser.duration"), LASER);
			this.hazards.add(this.laser);
			break;
		default:
			double cw = columnWidth();
			this.columnX = clampColumn(playerX - cw / 2);
			this.column = new BossHazard(this.columnX, hullBottom(), cw,
					this.height - hullBottom(), -1, -1, LASER);
			this.hazards.add(this.column);
			this.sweep = Sweep.TRACK;
			this.timer = this.balance.frames("fortress.sweep.trackTime");
			this.crossfireTimer = 0;
			break;
		}
	}

	private void end() {
		this.last = this.current;
		this.current = null;
		this.restLeft = gapFrames("fortress.restTime");
		if (this.laser != null)
			this.laser.finish();
		if (this.column != null)
			this.column.finish();
		this.laser = null;
		this.column = null;
	}

	@Override
	protected void onStun() {
		end();
		this.restLeft = Math.max(this.restLeft, 1);
	}

	private void tickFan(final double playerX, final double playerY) {
		if (--this.timer > 0)
			return;
		for (Core core : new Core[] {this.left, this.right})
			if (core.alive())
				fireFan(coreCenterX(core), coreBottom(core), playerX, playerY,
						this.balance.count("fortress.fan.bullets"),
						this.balance.get("fortress.fan.spreadDegrees"),
						bulletSpeed("fortress.fan.bulletSpeed"));
		this.countLeft--;
		this.timer = gapFrames("fortress.fan.volleyGap");
		if (this.countLeft <= 0 || !(this.left.alive() || this.right.alive()))
			end();
	}

	private void tickBomb() {
		if (this.countLeft > 0 && --this.timer <= 0) {
			double startX = this.width * (0.25 + 0.5 * this.random.nextDouble())
					+ this.sway;
			double playHeight = this.height - this.top;
			double targetX = 30 + this.random.nextDouble() * (this.width - 60);
			double targetY = this.top + playHeight
					* (0.3 + 0.45 * this.random.nextDouble());
			this.bombs.add(new BossBomb(startX, hullBottom(), targetX, targetY,
					speed("fortress.bomb.speed"),
					this.balance.count("fortress.bomb.bullets"),
					bulletSpeed("fortress.bomb.bulletSpeed"),
					this.random.nextDouble() * 2 * Math.PI));
			this.countLeft--;
			this.timer = gapFrames("fortress.bomb.launchGap");
		}
		if (this.countLeft <= 0 && this.bombs.isEmpty())
			end();
	}

	private void tickLaser() {
		if (this.laser.isDone() || !this.center.alive()) {
			end();
			return;
		}
		double w = this.laser.getWidth();
		double y = coreBottom(this.center);
		this.laser.setBounds(coreCenterX(this.center) - w / 2, y, w,
				this.height - y);
	}

	private void tickSweep(final double playerX) {
		if (this.sweep != Sweep.HOLD && --this.crossfireTimer <= 0) {
			double playHeight = this.height - this.top;
			double y = Math.min(this.height - 12, this.top + playHeight
					* (0.35 + 0.6 * this.random.nextDouble()));
			double speed = bulletSpeed("fortress.sweep.crossfireSpeed");
			if (this.crossfireFromLeft)
				fire(3, y, 0, speed);
			else
				fire(this.width - 3, y, Math.PI, speed);
			this.crossfireFromLeft = !this.crossfireFromLeft;
			this.crossfireTimer = gapFrames("fortress.sweep.crossfireGap");
		}
		double cw = columnWidth();
		switch (this.sweep) {
		case TRACK:
			double wanted = clampColumn(playerX - cw / 2);
			this.columnX += Math.max(-TRACK_STEP, Math.min(TRACK_STEP,
					wanted - this.columnX));
			this.column.setBounds(this.columnX, hullBottom(), cw,
					this.height - hullBottom());
			if (--this.timer <= 0) {
				this.sweep = Sweep.LOCK;
				this.lockFrames = this.balance.frames("fortress.sweep.lockTime");
				this.timer = this.lockFrames;
				this.column.setUrgent(true);
			}
			break;
		case LOCK:
			if (--this.timer <= 0) {
				this.sweep = Sweep.RISE;
				this.columnFront = this.height;
				this.column.activate(-1);
			}
			break;
		case RISE:
			this.columnFront -= speed("fortress.sweep.riseSpeed");
			for (Core core : this.cores)
				if (core.alive() && columnOverlaps(core)
						&& this.columnFront <= coreBottom(core)) {
					stun(this.balance.frames("fortress.sweep.stunTime"));
					return;
				}
			if (this.columnFront <= hullBottom()) {
				this.columnFront = hullBottom();
				this.sweep = Sweep.HOLD;
				this.timer = HOLD_FRAMES;
			}
			this.column.setBounds(this.columnX, this.columnFront, cw,
					this.height - this.columnFront);
			break;
		default:
			if (--this.timer <= 0)
				end();
			break;
		}
	}

	/* ---------------- Hits ---------------- */

	@Override
	public BossHit hit(final Entity bullet) {
		if (!isFighting())
			return null;
		double bulletX = bullet.getPositionX() + bullet.getWidth() / 2.0;
		for (Core core : this.cores) {
			if (!core.alive() || !BossHazard.overlaps(coreLeft(core), core.y,
					core.width, core.height, bullet))
				continue;
			core.health--;
			core.flash = FLASH_FRAMES;
			if (core.alive())
				return BossHit.damage(bulletX, coreBottom(core));
			this.bursts.add(new Burst(coreCenterX(core),
					core.y + core.height / 2, this.frame * 31L + (long) core.centerX));
			this.hullFlash = HULL_FLASH_FRAMES;
			// Cores give no points of their own; only the defeat scores.
			int points = 0;
			boolean beaten = !this.left.alive() && !this.center.alive()
					&& !this.right.alive();
			if (beaten) {
				points = (int) Math.round(this.balance.get("fortress.defeatPoints"));
				defeat();
			}
			return new BossHit(points, coreCenterX(core),
					core.y + core.height / 2, true, true, beaten);
		}
		if (BossHazard.overlaps(-HULL_OVERHANG + this.sway, hullTop(),
				this.width + 2 * HULL_OVERHANG, HULL_HEIGHT, bullet))
			return BossHit.absorbed(bulletX, hullBottom());
		return null;
	}

	@Override
	protected boolean touches(final Entity ship) {
		if (super.touches(ship))
			return true;
		for (BossBomb bomb : this.bombs)
			if (bomb.hurts(ship))
				return true;
		return false;
	}

	@Override
	protected void tickEffects() {
		if (this.hullFlash > 0)
			this.hullFlash--;
		Iterator<Burst> it = this.bursts.iterator();
		while (it.hasNext())
			if (++it.next().age >= BURST_FRAMES)
				it.remove();
	}

	/** @return Core blasts still playing. */
	public int getBurstCount() {
		return this.bursts.size();
	}

	// Bursts are kept here, so the last core's blast plays over the defeat.
	@Override
	protected void clearParts() {
		this.bombs.clear();
		this.laser = null;
		this.column = null;
		this.current = null;
	}

	/* ---------------- Geometry ---------------- */

	private double hullTop() {
		return this.top + HULL_TOP;
	}

	private double hullBottom() {
		return this.top + HULL_TOP + HULL_HEIGHT;
	}

	private double coreCenterX(final Core core) {
		return core.centerX + this.sway;
	}

	private double coreLeft(final Core core) {
		return coreCenterX(core) - core.width / 2;
	}

	private double coreBottom(final Core core) {
		return core.y + core.height;
	}

	private double columnWidth() {
		return this.balance.get("fortress.sweep.columnWidth");
	}

	private double clampColumn(final double x) {
		return Math.max(0, Math.min(this.width - columnWidth(), x));
	}

	private boolean columnOverlaps(final Core core) {
		return this.columnX < coreLeft(core) + core.width
				&& coreLeft(core) < this.columnX + columnWidth();
	}

	/* ---------------- Drawing ---------------- */

	@Override
	public void draw(final DrawManager drawManager) {
		drawHazards(drawManager);
		if (this.current == Pattern.SWEEP && this.sweep == Sweep.LOCK
				&& this.column != null)
			drawChargingGlow(drawManager);
		boolean visible = drawDeath(drawManager, this.width / 2.0,
				hullTop() + HULL_HEIGHT / 2.0, this.width / 2.0, HULL_HEIGHT / 2.0);
		if (visible) {
			drawHull(drawManager);
			if (this.hullFlash > 0)
				drawManager.fillBox((int) Math.round(-HULL_OVERHANG + this.sway),
						(int) Math.round(hullTop()), this.width + 2 * HULL_OVERHANG,
						HULL_HEIGHT, new Color(255, 255, 255,
								25 * this.hullFlash));
		}
		if (getDeathFrames() < 0)
			for (BossBomb bomb : this.bombs)
				bomb.draw(drawManager);
		for (Burst burst : this.bursts)
			drawBurst(drawManager, burst);
	}

	/**
	 * A core blast: a white flash, a growing fireball, two shock rings and
	 * flying debris, all fading out.
	 */
	private void drawBurst(final DrawManager drawManager, final Burst burst) {
		int age = burst.age;
		double t = (double) age / BURST_FRAMES;
		double fade = 1 - t;
		int cx = (int) Math.round(burst.x);
		int cy = (int) Math.round(burst.y);

		// Fireball: white at first, then green, growing and fading.
		if (age < 20) {
			int r = 6 + age * 2;
			double heat = age / 20.0;
			Color ball = mix(Color.WHITE, IRIS, heat);
			drawManager.fillBox(cx - r, cy - r / 2, 2 * r, r,
					alpha(ball, (int) (220 * (1 - heat))));
			drawManager.fillBox(cx - r / 2, cy - r, r, 2 * r,
					alpha(ball, (int) (220 * (1 - heat))));
			int inner = Math.max(2, r / 2);
			drawManager.fillBox(cx - inner, cy - inner, 2 * inner, 2 * inner,
					alpha(Color.WHITE, (int) (255 * (1 - heat))));
		}

		// Two shock rings: a green one, and a slower magenta one.
		drawRing(drawManager, cx, cy, 8 + age * 2.6, alpha(POD_BRIGHT,
				(int) (230 * fade)), 18, 3);
		if (age > 4)
			drawRing(drawManager, cx, cy, 4 + (age - 4) * 1.6, alpha(GILL_GLOW,
					(int) (200 * fade)), 14, 2);

		// Debris flying out and falling.
		Random pieces = new Random(burst.seed);
		Color[] colors = { HULL_LIGHT, OOZE, GILL_GLOW, SHINE };
		for (int i = 0; i < DEBRIS; i++) {
			double angle = pieces.nextDouble() * 2 * Math.PI;
			double speed = 1.2 + pieces.nextDouble() * 2.4;
			int size = 2 + pieces.nextInt(3);
			Color color = colors[pieces.nextInt(colors.length)];
			double dx = Math.cos(angle) * speed * age;
			double dy = Math.sin(angle) * speed * age + 0.06 * age * age;
			drawManager.fillBox((int) Math.round(cx + dx) - size / 2,
					(int) Math.round(cy + dy) - size / 2, size, size,
					alpha(color, (int) (255 * fade)));
		}

		// Bright sparks right after the hit.
		if (age < 12 && age % 2 == 0)
			for (int i = 0; i < 8; i++) {
				double angle = Math.PI / 4 * i + age * 0.2;
				double d = 6 + age * 3;
				drawManager.fillBox((int) Math.round(cx + Math.cos(angle) * d) - 1,
						(int) Math.round(cy + Math.sin(angle) * d) - 1, 3, 3,
						Color.WHITE);
			}
	}

	/** A dotted ring of square points. */
	private static void drawRing(final DrawManager drawManager, final int cx,
			final int cy, final double radius, final Color color, final int points,
			final int size) {
		for (int i = 0; i < points; i++) {
			double angle = 2 * Math.PI * i / points;
			drawManager.fillBox((int) Math.round(cx + Math.cos(angle) * radius)
					- size / 2, (int) Math.round(cy + Math.sin(angle) * radius)
					- size / 2, size, size, color);
		}
	}

	/**
	 * Before the rising attack: light gathers at the bottom of the column
	 * and grows until it fires, with sparks at its top.
	 */
	private void drawChargingGlow(final DrawManager drawManager) {
		double progress = 1 - (double) this.timer / this.lockFrames;
		int x = (int) Math.round(this.columnX);
		int w = (int) Math.round(columnWidth());
		int glow = (int) Math.round(8 + progress * 52);
		int y = this.height - glow;
		drawManager.fillBox(x, y, w, glow, new Color(80, 255, 170,
				(int) (80 + 120 * progress)));
		drawManager.fillBox(x + w / 3, y, Math.max(2, w / 3), glow,
				new Color(220, 255, 235));
		if ((this.frame / 2) % 2 == 0)
			for (int i = 0; i < 3; i++) {
				int sx = x + (int) ((i + 0.5) * w / 3) - 1;
				drawManager.fillBox(sx, y - 4 - ((this.frame + i * 3) % 7), 3, 3,
						new Color(200, 255, 220));
			}
	}

	private void drawHull(final DrawManager drawManager) {
		int x = (int) Math.round(-HULL_OVERHANG + this.sway);
		int y = (int) Math.round(hullTop());
		int w = this.width + 2 * HULL_OVERHANG;
		int centerX = (int) Math.round(this.width / 2.0 + this.sway);
		boolean stunned = isStunned();

		// Body and a soft, uneven membrane along the top edge.
		drawManager.fillBox(x, y, w, HULL_HEIGHT, stunned ? HULL_STUNNED : HULL);
		drawManager.fillBox(x, y, w, 2, HULL_LIGHT);
		int bumpIndex = 0;
		for (int mx = x; mx < x + w; mx += 8, bumpIndex++) {
			int bump = 1 + (int) Math.round(1.5 + 1.5 * Math.sin(bumpIndex * 1.3));
			drawManager.fillBox(mx, y + 2, 8, bump, HULL_LIGHT);
		}

		// Ribs, like the bones of a living ship.
		for (int rib = x + 20; rib < x + w; rib += 40) {
			drawManager.fillBox(rib, y + 5, 4, HULL_HEIGHT - 15, RIB);
			drawManager.fillBox(rib, y + 5, 1, HULL_HEIGHT - 15, RIB_LIGHT);
			drawManager.fillBox(rib - 2, y + 5, 8, 3, RIB);
		}

		// Glow pods along the upper deck, pulsing in a slow wave.
		int pod = 0;
		for (int px = x + 8; px < x + w - 4; px += 14, pod++) {
			double glow = stunned ? 0.1
					: 0.5 + 0.5 * Math.sin(this.frame * 0.07 + pod * 0.6);
			drawManager.fillBox(px, y + 9, 4, 4, mix(POD_DIM, POD_BRIGHT, glow));
		}

		// A glowing vein along the ship, with branches between the ribs.
		int segment = 0;
		for (int vx = x; vx < x + w; vx += 8, segment++) {
			double pulse = stunned ? 0.15
					: 0.5 + 0.5 * Math.sin(this.frame * 0.12 - segment * 0.45);
			drawManager.fillBox(vx, y + 21, 8, 2, alpha(VEIN, 60 + (int) (170 * pulse)));
		}
		for (int branch = x + 40; branch < x + w; branch += 40) {
			drawManager.fillBox(branch, y + 23, 2, 8, alpha(VEIN, 110));
			drawManager.fillBox(branch + 2, y + 30, 4, 2, alpha(VEIN, 90));
		}

		// Gills beside the center core, glowing as the ship breathes.
		double breath = 0.5 + 0.5 * Math.sin(this.frame * 0.05);
		for (int side = -1; side <= 1; side += 2) {
			int gx = side < 0 ? centerX - 76 : centerX + 46;
			drawManager.fillBox(gx, y + 27, 30, 16, GILL);
			for (int slat = 0; slat < 4; slat++)
				drawManager.fillBox(gx + 3 + slat, y + 29 + slat * 4, 24 - slat * 2, 2,
						alpha(GILL_GLOW, stunned ? 40 : 70 + (int) (150 * breath)));
		}

		// Dark underside, teeth hanging below it and bio-lights.
		drawManager.fillBox(x, y + HULL_HEIGHT - 10, w, 10, HULL_DARK);
		int bottom = y + HULL_HEIGHT;
		for (int tx = x + 6; tx < x + w; tx += 16)
			if (!nearCore(tx + 3)) {
				drawManager.fillBox(tx, bottom, 6, 2, SPIKE);
				drawManager.fillBox(tx + 1, bottom + 2, 4, 2, SPIKE);
				drawManager.fillBox(tx + 2, bottom + 4, 2, 2, SPIKE);
			}
		int light = 0;
		for (int lx = x + 16; lx < x + w; lx += 32, light++) {
			double glow = stunned ? 0 : 0.5 + 0.5 * Math.sin(this.frame * 0.1 + light);
			drawManager.fillBox(lx, y + HULL_HEIGHT - 6, 3, 3,
					mix(POD_DIM, POD_BRIGHT, glow));
		}

		for (Core core : this.cores)
			drawCore(drawManager, core, stunned);
		if (stunned && (this.frame / 8) % 2 == 0)
			drawManager.drawRegularString("STUN", this.width / 2 - 18, y + 16,
					Color.YELLOW);
	}

	/** @return True if x is under one of the cores (no teeth there). */
	private boolean nearCore(final int x) {
		for (Core core : this.cores)
			if (Math.abs(x - coreCenterX(core)) < core.width / 2 + 8)
				return true;
		return false;
	}

	/** Draws a core as an alien eye in a fleshy socket; its pupil follows the player. */
	private void drawCore(final DrawManager drawManager, final Core core,
			final boolean stunned) {
		int x = (int) Math.round(coreLeft(core));
		int y = (int) Math.round(core.y);
		int w = (int) core.width;
		int h = (int) core.height;
		boolean laserCharge = core == this.center && this.current == Pattern.LASER
				&& this.laser != null
				&& this.laser.getPhase() != BossHazard.Phase.ACTIVE;

		// Socket with a lighter rim and small tendrils that twitch.
		drawManager.fillBox(x - 4, y - 3, w + 8, h + 6,
				laserCharge && (this.frame / 4) % 2 == 0 ? new Color(170, 30, 60) : SOCKET);
		drawManager.fillBox(x - 4, y - 3, w + 8, 1, SOCKET_RIM);
		drawManager.fillBox(x - 4, y + h + 2, w + 8, 1, SOCKET_RIM);
		int twitch = (this.frame / 12) % 2;
		drawManager.fillBox(x - 7, y + 2 + twitch, 3, 3, SOCKET);
		drawManager.fillBox(x - 9, y + 5 + twitch, 2, 3, SOCKET_RIM);
		drawManager.fillBox(x + w + 4, y + 2 + twitch, 3, 3, SOCKET);
		drawManager.fillBox(x + w + 7, y + 5 + twitch, 2, 3, SOCKET_RIM);

		if (!core.alive()) {
			drawDeadCore(drawManager, x, y, w, h);
			return;
		}
		Color iris = IRIS;
		if (stunned)
			iris = (this.frame / 6) % 2 == 0 ? new Color(150, 230, 255) : IRIS;
		else if (laserCharge)
			iris = (this.frame / 4) % 2 == 0 ? new Color(255, 60, 60)
					: new Color(255, 160, 60);
		else if (core.side && this.current == Pattern.FAN)
			iris = new Color(255, 190, 70);
		if (core.flash > 0) {
			drawManager.fillBox(x, y, w, h, Color.WHITE);
		} else {
			drawManager.fillBox(x, y, w, h, IRIS_DARK);
			drawManager.fillBox(x + 3, y + 2, w - 6, h - 4, iris);
			// Slit pupil, wide open while stunned, looking at the player.
			boolean big = core == this.center;
			int pupilW = stunned ? (big ? 14 : 10) : (big ? 6 : 4);
			int pupilH = stunned ? h - 10 : h - 6;
			double room = (w - 6 - pupilW) / 2.0 - 2;
			double look = Math.max(-room, Math.min(room,
					(this.lookX - coreCenterX(core)) / 12));
			int px = (int) Math.round(coreCenterX(core) - pupilW / 2.0 + look);
			drawManager.fillBox(px, y + (h - pupilH) / 2, pupilW, pupilH, PUPIL);
			drawManager.fillBox(x + 5, y + 4, 3, 2, SHINE);
		}
		if (stunned && (this.frame / 3) % 2 == 0)
			drawSparks(drawManager, x, y, w, h);
		// Health tick under the core.
		int barWidth = w * core.health / core.maxHealth;
		drawManager.fillBox(x, y + h + 5, barWidth, 2, new Color(255, 80, 80));
	}

	/** A destroyed core: a shut, scarred socket dripping glowing ooze. */
	private void drawDeadCore(final DrawManager drawManager, final int x,
			final int y, final int w, final int h) {
		drawManager.fillBox(x, y, w, h, new Color(30, 14, 26));
		drawManager.fillBox(x + 2, y + h / 2 - 1, w - 4, 2, new Color(80, 32, 66));
		for (int sx = x + 5; sx < x + w - 4; sx += 6)
			drawManager.fillBox(sx, y + h / 2 - 3, 1, 6, new Color(50, 20, 42));
		double glow = 0.5 + 0.5 * Math.sin(this.frame * 0.15);
		drawManager.fillBox(x + w / 2 - 2, y + h / 2 - 1, 4, 2,
				alpha(OOZE, 80 + (int) (120 * glow)));
		for (int drip = 0; drip < 3; drip++) {
			int length = (this.frame / 3 + drip * 5) % 12;
			drawManager.fillBox(x + w * (drip + 1) / 4, y + h, 2, length,
					alpha(OOZE, 200 - length * 12));
		}
	}

	/** Electric sparks around a stunned core. */
	private void drawSparks(final DrawManager drawManager, final int x,
			final int y, final int w, final int h) {
		Random sparks = new Random(this.frame * 31L + x);
		for (int i = 0; i < 4; i++) {
			int sx = x - 6 + sparks.nextInt(w + 12);
			int sy = y - 5 + sparks.nextInt(h + 10);
			boolean across = sparks.nextBoolean();
			drawManager.fillBox(sx, sy, across ? 5 : 1, across ? 1 : 5,
					new Color(180, 240, 255));
		}
	}

	/* ---------------- Info ---------------- */

	@Override
	public int getHealth() {
		return this.left.health + this.center.health + this.right.health;
	}

	@Override
	public int getMaxHealth() {
		return this.left.maxHealth + this.center.maxHealth + this.right.maxHealth;
	}

	@Override
	public String getName() {
		return "FORTRESS";
	}

	/** @return Health of the left, center and right core. */
	public int[] getCoreHealth() {
		return new int[] {this.left.health, this.center.health, this.right.health};
	}

	/**
	 * @param index 0 left, 1 center, 2 right.
	 * @return Hit box of that core: x, y, width, height.
	 */
	public double[] getCoreBox(final int index) {
		Core core = this.cores[index];
		return new double[] {coreLeft(core), core.y, core.width, core.height};
	}

	/** @return Current pattern name, or REST between patterns. */
	public String getPatternName() {
		if (isStunned())
			return "STUNNED";
		return this.current == null ? "REST" : this.current.name();
	}

	/** @return Bombs in flight. */
	public int getBombCount() {
		return this.bombs.size();
	}
}
