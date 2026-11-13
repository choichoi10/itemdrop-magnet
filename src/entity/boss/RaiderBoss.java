package entity.boss;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import engine.DrawManager;
import entity.Entity;
import entity.enemy.EnemyType;
import entity.enemy.SpawnRequest;

/**
 * Boss 2: a mid-sized, fast ship with less health than the fortress.
 *
 * Patterns:
 * <ol>
 * <li>Patrol (default): roams left and right, fires down now and then,
 * and summons enemy formations (normal, shooter, bat).</li>
 * <li>Charge: a warning lane, then it charges down spraying bullets to
 * both sides, leaves at the bottom and comes back in from the top.</li>
 * <li>Drones: for a few seconds homing drones come in from every side;
 * they chase the player for a while, then fly straight on and leave.</li>
 * </ol>
 * After each patrol it uses charge or drones, never the same one twice in a
 * row. Touching its body hurts the player.
 */
public final class RaiderBoss extends AbstractBoss {

	/** What the raider is doing. */
	private enum State { ENTER, PATROL, CHARGE_WARNING, CHARGE, RETURN, DRONES }

	/** Body width. */
	static final int WIDTH = 128;
	/** Body height. */
	static final int HEIGHT = 60;
	/** Gap between the HUD line and the body at rest, below the health bar. */
	private static final int HOME_GAP = 36;
	/** Distance of the guns from the body sides. */
	private static final int GUN_INSET = 26;
	/** Width of the fuselage between the wings. */
	private static final int FUSELAGE = 50;
	/** Enemy types it can summon, with their balance keys. */
	private static final EnemyType[] SUMMON_TYPES = {
			EnemyType.NORMAL, EnemyType.SHOOTER, EnemyType.BAT };
	/** Distance kept from the screen sides while roaming. */
	private static final int SIDE_MARGIN = 8;
	/** Chance per frame to turn around while roaming. */
	private static final double TURN_CHANCE = 0.006;
	/** Drones never appear closer than this to the player. */
	private static final double DRONE_SAFE_DISTANCE = 150;
	/** Bullet angle off horizontal while charging, radians. */
	private static final double SPRAY_ANGLE = 0.35;

	private static final Color CARAPACE = new Color(52, 34, 92);
	private static final Color CARAPACE_ALT = new Color(44, 28, 78);
	private static final Color CARAPACE_LIGHT = new Color(125, 85, 190);
	private static final Color CARAPACE_DARK = new Color(28, 16, 50);
	private static final Color MEMBRANE = new Color(150, 90, 220, 150);
	private static final Color GLOW = new Color(110, 240, 255);
	private static final Color GLOW_DIM = new Color(30, 90, 120);
	private static final Color HOT = new Color(240, 90, 230);
	private static final Color HOT_DIM = new Color(100, 30, 95);
	private static final Color EYE = new Color(120, 255, 150);
	private static final Color PUPIL = new Color(8, 6, 18);
	private static final Color SHINE = new Color(235, 255, 240);

	private final int maxHealth;
	private int health;
	private int flash;
	private double x;
	private double y;
	private final double homeY;
	private int direction = 1;

	private State state = State.ENTER;
	private State lastSpecial;
	private int stateFrames;
	private int fireTimer;
	private int summonTimer;
	private int spawnTimer;
	/** Player x, for the eye to look at. */
	private double lookX;
	private final List<HomingDrone> drones = new ArrayList<HomingDrone>();

	/**
	 * @param balance Boss balance values.
	 * @param level   Stage number, for the stage multipliers.
	 * @param random  Random source.
	 * @param width   Screen width.
	 * @param height  Screen height.
	 * @param top     Top of the play area.
	 */
	public RaiderBoss(final BossBalance balance, final int level,
			final Random random, final int width, final int height,
			final int top) {
		super(balance, level, random, width, height, top);
		this.maxHealth = balance.count("raider.health");
		this.health = this.maxHealth;
		this.x = (width - WIDTH) / 2.0;
		this.y = top - HEIGHT;
		this.homeY = top + HOME_GAP;
		this.summonTimer = balance.frames("raider.patrol.summonGap");
	}

	/* ---------------- Frame logic ---------------- */

	@Override
	protected void tickParts(final double playerX, final double playerY) {
		this.lookX = playerX;
		if (this.flash > 0)
			this.flash--;
		Iterator<HomingDrone> it = this.drones.iterator();
		while (it.hasNext()) {
			HomingDrone drone = it.next();
			drone.update(playerX, playerY);
			if (drone.isGone(this.width, this.height, this.top))
				it.remove();
		}
	}

	@Override
	protected void act(final double playerX, final double playerY) {
		switch (this.state) {
		case ENTER:
		case RETURN:
			this.y += speed("raider.charge.returnSpeed");
			if (this.y >= this.homeY) {
				this.y = this.homeY;
				startPatrol();
			}
			break;
		case PATROL:
			roam(1);
			if (--this.fireTimer <= 0) {
				double speed = bulletSpeed("raider.patrol.bulletSpeed");
				fire(this.x + GUN_INSET, this.y + HEIGHT, Math.PI / 2, speed);
				fire(this.x + WIDTH - GUN_INSET, this.y + HEIGHT, Math.PI / 2, speed);
				this.fireTimer = gapFrames("raider.patrol.fireGap");
			}
			if (--this.summonTimer <= 0) {
				this.enemies.spawn(SpawnRequest.of(summonType()), playerX,
						playerY);
				this.summonTimer = this.balance.frames("raider.patrol.summonGap");
			}
			if (--this.stateFrames <= 0)
				startSpecial();
			break;
		case CHARGE_WARNING:
			if (--this.stateFrames <= 0) {
				this.state = State.CHARGE;
				this.fireTimer = 0;
			}
			break;
		case CHARGE:
			this.y += speed("raider.charge.speed");
			if (--this.fireTimer <= 0) {
				double speed = bulletSpeed("raider.charge.bulletSpeed");
				double sideY = this.y + HEIGHT / 2.0;
				fire(this.x, sideY, Math.PI - SPRAY_ANGLE, speed);
				fire(this.x + WIDTH, sideY, SPRAY_ANGLE, speed);
				this.fireTimer = gapFrames("raider.charge.fireGap");
			}
			if (this.y > this.height) {
				this.y = this.top - HEIGHT - 10;
				this.state = State.RETURN;
			}
			break;
		default: // DRONES
			roam(0.5);
			if (--this.spawnTimer <= 0) {
				spawnDrone(playerX, playerY);
				this.spawnTimer = gapFrames("raider.drones.spawnGap");
			}
			if (--this.stateFrames <= 0)
				startPatrol();
			break;
		}
	}

	private void startPatrol() {
		this.state = State.PATROL;
		this.stateFrames = this.balance.frames("raider.patrol.time");
		this.fireTimer = gapFrames("raider.patrol.fireGap");
	}

	private void startSpecial() {
		State next = this.lastSpecial == State.CHARGE_WARNING ? State.DRONES
				: this.lastSpecial == State.DRONES ? State.CHARGE_WARNING
				: this.random.nextBoolean() ? State.CHARGE_WARNING : State.DRONES;
		this.lastSpecial = next;
		this.state = next;
		if (next == State.CHARGE_WARNING) {
			int warning = this.balance.frames("raider.charge.warning");
			this.stateFrames = warning;
			double laneTop = this.y + HEIGHT;
			this.hazards.add(new BossHazard(this.x, laneTop, WIDTH,
					this.height - laneTop, warning, 0, HOT));
		} else {
			this.stateFrames = this.balance.frames("raider.drones.time");
			this.spawnTimer = 0;
		}
	}

	/** @return A summon type drawn by the raider.patrol.summon.* weights. */
	private EnemyType summonType() {
		double total = 0;
		for (EnemyType type : SUMMON_TYPES)
			total += summonWeight(type);
		if (total <= 0)
			return EnemyType.NORMAL;
		double roll = this.random.nextDouble() * total;
		for (EnemyType type : SUMMON_TYPES) {
			roll -= summonWeight(type);
			if (roll < 0)
				return type;
		}
		return EnemyType.NORMAL;
	}

	private double summonWeight(final EnemyType type) {
		return this.balance.get("raider.patrol.summon." + type.getBalanceKey());
	}

	/** Moves left and right, turning at the sides and now and then. */
	private void roam(final double speedFactor) {
		this.x += this.direction * speed("raider.speed") * speedFactor;
		if (this.x <= SIDE_MARGIN) {
			this.x = SIDE_MARGIN;
			this.direction = 1;
		} else if (this.x >= this.width - WIDTH - SIDE_MARGIN) {
			this.x = this.width - WIDTH - SIDE_MARGIN;
			this.direction = -1;
		} else if (this.random.nextDouble() < TURN_CHANCE) {
			this.direction = -this.direction;
		}
	}

	private void spawnDrone(final double playerX, final double playerY) {
		double sx = 0;
		double sy = 0;
		for (int attempt = 0; attempt < 12; attempt++) {
			int side = this.random.nextInt(4);
			double alongX = 10 + this.random.nextDouble() * (this.width - 20);
			double alongY = this.top + 20 + this.random.nextDouble()
					* (this.height - this.top - 60);
			if (side == 0) {
				sx = -HomingDrone.SIZE;
				sy = alongY;
			} else if (side == 1) {
				sx = this.width + HomingDrone.SIZE;
				sy = alongY;
			} else if (side == 2) {
				sx = alongX;
				sy = this.top + HomingDrone.SIZE;
			} else {
				sx = alongX;
				sy = this.height + HomingDrone.SIZE;
			}
			if (Math.hypot(sx - playerX, sy - playerY) >= DRONE_SAFE_DISTANCE)
				break;
		}
		this.drones.add(new HomingDrone(sx, sy,
				Math.atan2(playerY - sy, playerX - sx),
				speed("raider.drones.speed"),
				Math.toRadians(this.balance.get("raider.drones.turnDegrees")),
				this.balance.frames("raider.drones.chaseTime"),
				this.balance.count("raider.drones.health")));
	}

	/* ---------------- Hits ---------------- */

	@Override
	public BossHit hit(final Entity bullet) {
		if (!isFighting())
			return null;
		for (HomingDrone drone : this.drones) {
			if (!drone.touches(bullet))
				continue;
			if (!drone.takeHit())
				return BossHit.damage(drone.getX(), drone.getY());
			return new BossHit((int) Math.round(this.balance.get(
					"raider.drones.points")), drone.getX(), drone.getY(),
					true, true, false);
		}
		if (!bodyOnScreen() || !BossHazard.overlaps(this.x, this.y, WIDTH,
				HEIGHT, bullet))
			return null;
		this.health--;
		this.flash = FLASH_FRAMES;
		double hitX = bullet.getPositionX() + bullet.getWidth() / 2.0;
		if (this.health > 0)
			return BossHit.damage(hitX, this.y + HEIGHT);
		defeat();
		return new BossHit((int) Math.round(this.balance.get(
				"raider.defeatPoints")), this.x + WIDTH / 2.0,
				this.y + HEIGHT / 2.0, true, true, true);
	}

	@Override
	protected boolean touches(final Entity ship) {
		if (super.touches(ship))
			return true;
		if (bodyOnScreen() && BossHazard.overlaps(this.x, this.y, WIDTH,
				HEIGHT, ship))
			return true;
		for (HomingDrone drone : this.drones)
			if (drone.touches(ship)) {
				drone.crash();
				return true;
			}
		return false;
	}

	@Override
	protected void clearParts() {
		this.drones.clear();
	}

	private boolean bodyOnScreen() {
		return this.y + HEIGHT > this.top && this.y < this.height;
	}

	/* ---------------- Drawing ---------------- */

	@Override
	public void draw(final DrawManager drawManager) {
		drawHazards(drawManager);
		boolean visible = drawDeath(drawManager, this.x + WIDTH / 2.0,
				this.y + HEIGHT / 2.0, WIDTH / 2.0, HEIGHT / 2.0);
		if (visible && bodyOnScreen())
			drawBody(drawManager);
		if (getDeathFrames() < 0)
			for (HomingDrone drone : this.drones)
				drone.draw(drawManager);
	}

	private void drawBody(final DrawManager drawManager) {
		int shake = this.state == State.CHARGE_WARNING
				? (this.frame % 4 < 2 ? -2 : 2) : 0;
		int bx = (int) Math.round(this.x) + shake;
		int by = (int) Math.round(this.y);
		boolean white = this.flash > 0;
		Color shell = white ? Color.WHITE : CARAPACE;
		Color shellAlt = white ? Color.WHITE : CARAPACE_ALT;
		Color light = white ? Color.WHITE : CARAPACE_LIGHT;
		Color dark = white ? Color.WHITE : CARAPACE_DARK;
		Color membrane = white ? Color.WHITE : MEMBRANE;
		double pulse = 0.5 + 0.5 * Math.sin(this.frame * 0.12);
		Color glow = white ? Color.WHITE : mix(GLOW_DIM, GLOW, pulse);
		Color hot = white ? Color.WHITE : mix(HOT_DIM, HOT, 1 - pulse);
		int cx = bx + WIDTH / 2;

		drawExhaust(drawManager, cx, by);

		// Feelers at the back (top), tipped with light.
		pair(drawManager, cx, by, 8, 0, 3, 12, dark);
		pair(drawManager, cx, by, 11, 0, 3, 3, dark);
		pair(drawManager, cx, by, 11, 0, 3, 1, hot);

		// Blade wings: hard shell by the body, see-through membrane outward,
		// stepping up toward the tips.
		pair(drawManager, cx, by, 20, 24, 24, 18, shell);
		pair(drawManager, cx, by, 44, 20, 12, 16, membrane);
		pair(drawManager, cx, by, 56, 16, 8, 14, membrane);
		// Wing bones out to the tips.
		pair(drawManager, cx, by, 20, 30, 40, 2, light);
		pair(drawManager, cx, by, 42, 21, 2, 19, dark);
		pair(drawManager, cx, by, 54, 17, 2, 15, dark);
		// Glowing leading edge and bio-dots.
		pair(drawManager, cx, by, 20, 40, 24, 2, glow);
		pair(drawManager, cx, by, 44, 34, 12, 2, glow);
		pair(drawManager, cx, by, 56, 28, 8, 2, glow);
		pair(drawManager, cx, by, 28, 34, 3, 3, hot);
		pair(drawManager, cx, by, 47, 26, 3, 3, hot);
		pair(drawManager, cx, by, 58, 21, 3, 3, hot);
		// Horns at the wing tips.
		pair(drawManager, cx, by, 60, 8, 4, 8, light);
		pair(drawManager, cx, by, 62, 4, 2, 4, light);

		// Glands under the wings with bone spikes that shoot.
		pair(drawManager, cx, by, 32, 24, 12, 20, dark);
		pair(drawManager, cx, by, 34, 26, 2, 15, light);
		pair(drawManager, cx, by, 32, 42, 12, 3, hot);
		pair(drawManager, cx, by, 37, 45, 2, 10, light);
		pair(drawManager, cx, by, 36, 54, 4, 3, glow);
		if (this.state == State.PATROL
				&& gapFrames("raider.patrol.fireGap") - this.fireTimer < 4)
			pair(drawManager, cx, by, 35, 56, 6, 5, white ? Color.WHITE : GLOW);

		// Segmented carapace narrowing to pincers at the front (bottom).
		box(drawManager, cx - 16, by, 32, 4, dark);
		for (int seg = 0; seg < 5; seg++) {
			int sy = by + 4 + seg * 8;
			int half = FUSELAGE / 2 - (seg == 0 ? 4 : 0);
			box(drawManager, cx - half, sy, half * 2, 8, seg % 2 == 0 ? shell : shellAlt);
			box(drawManager, cx - half, sy, half * 2, 1, light);
		}
		box(drawManager, cx - 19, by + 44, 38, 8, shell);
		box(drawManager, cx - 19, by + 44, 38, 1, light);
		pair(drawManager, cx, by, 5, 50, 6, 8, light);
		pair(drawManager, cx, by, 3, 58, 4, 2, light);
		box(drawManager, cx - 5, by + 50, 10, 6, dark);
		box(drawManager, cx - 3, by + 52, 6, 2, hot);

		// Glowing spine and small side eyes.
		for (int sy = by + 6; sy < by + 32; sy += 6)
			box(drawManager, cx - 1, sy, 2, 3, hot);
		pair(drawManager, cx, by, 12, 28, 3, 3, glow);

		// Big eye where a cockpit would be, looking at the player; red
		// before a charge.
		Color iris = white ? Color.WHITE
				: this.state == State.CHARGE_WARNING && (this.frame / 4) % 2 == 0
						? new Color(255, 60, 60) : EYE;
		box(drawManager, cx - 9, by + 35, 18, 14, dark);
		box(drawManager, cx - 7, by + 37, 14, 10, iris);
		if (!white) {
			int look = (int) Math.round(Math.max(-4, Math.min(4,
					(this.lookX - (this.x + WIDTH / 2.0)) / 30)));
			box(drawManager, cx - 1 + look, by + 38, 3, 8, PUPIL);
			box(drawManager, cx - 6, by + 38, 2, 2, SHINE);
		}

		drawDamage(drawManager, cx, by);
	}

	/** Plasma jet behind the raider (top): blue, pink before a charge, long while charging. */
	private void drawExhaust(final DrawManager drawManager, final int cx,
			final int by) {
		int flicker = (this.frame / 2) % 3;
		int length;
		Color outer;
		Color inner;
		if (this.state == State.CHARGE) {
			length = 14 + flicker * 2;
			outer = new Color(90, 200, 255);
			inner = Color.WHITE;
		} else if (this.state == State.CHARGE_WARNING) {
			length = 6 + flicker * 2;
			outer = HOT;
			inner = Color.WHITE;
		} else {
			length = 4 + flicker;
			outer = new Color(60, 150, 255);
			inner = new Color(200, 240, 255);
		}
		box(drawManager, cx - 10, by - length, 20, length, alpha(outer, 170));
		box(drawManager, cx - 5, by - length + 2, 10, length - 2, inner);
	}

	/** Purple smoke and dripping ichor once the raider is badly hurt. */
	private void drawDamage(final DrawManager drawManager, final int cx,
			final int by) {
		if (this.health * 2 > this.maxHealth)
			return;
		for (int puff = 0; puff < 2; puff++) {
			int age = (this.frame + puff * 17) % 34;
			int side = puff == 0 ? -40 : 34;
			int size = 4 + age / 7;
			box(drawManager, cx + side, by + 22 - age, size, size,
					new Color(130, 80, 160, 170 - age * 4));
		}
		if (this.health * 4 <= this.maxHealth)
			for (int drip = 0; drip < 2; drip++) {
				int length = (this.frame / 3 + drip * 6) % 12;
				box(drawManager, cx - 20 + drip * 38, by + 42, 2, length,
						alpha(new Color(120, 255, 120), 210 - length * 14));
			}
	}

	/**
	 * Fills a box and its mirror image across the body's center line.
	 *
	 * @param cx     Center line x.
	 * @param by     Body top.
	 * @param inner  Distance from the center line to the box's inner edge.
	 * @param dy     Top of the box below the body top.
	 */
	private void pair(final DrawManager drawManager, final int cx, final int by,
			final int inner, final int dy, final int w, final int h,
			final Color color) {
		box(drawManager, cx - inner - w, by + dy, w, h, color);
		box(drawManager, cx + inner, by + dy, w, h, color);
	}

	/** Fills a box, cut off at the HUD line. */
	private void box(final DrawManager drawManager, final int bx, final int by,
			final int bw, final int bh, final Color color) {
		int y0 = Math.max(by, this.top);
		int y1 = by + bh;
		if (y1 > y0)
			drawManager.fillBox(bx, y0, bw, y1 - y0, color);
	}

	/* ---------------- Info ---------------- */

	@Override
	public int getHealth() {
		return this.health;
	}

	@Override
	public int getMaxHealth() {
		return this.maxHealth;
	}

	@Override
	public String getName() {
		return "RAIDER";
	}

	/** @return Current state name. */
	public String getPatternName() {
		return this.state.name();
	}

	/** @return Drones in flight. */
	public int getDroneCount() {
		return this.drones.size();
	}

	/** @return Body width. */
	public int getBodyWidth() {
		return WIDTH;
	}

	/** @return Body height. */
	public int getBodyHeight() {
		return HEIGHT;
	}

	/** @return Body left edge. */
	public double getX() {
		return this.x;
	}

	/** @return Body top edge. */
	public double getY() {
		return this.y;
	}
}
