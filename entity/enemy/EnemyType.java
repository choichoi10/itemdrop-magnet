package entity.enemy;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import engine.DrawManager.SpriteType;

/**
 * The enemy catalogue: each type's look, attack and allowed movement
 * patterns in one list. Numbers (health, speed, bullet speed...) are not
 * here; they come from the balance file under the type's key.
 *
 * Sprites reuse the original invaders, told apart by colour. New art can
 * replace them later without changing anything else.
 */
public enum EnemyType {

	/** Flies in formation without shooting. */
	NORMAL("normal", SpriteType.EnemyShipA1, SpriteType.EnemyShipA2, 12, 8,
			Color.WHITE, FireStyle.NONE,
			MovePattern.CURVE, MovePattern.STRAIGHT_DOWN, MovePattern.SIDE_DIAGONAL),

	/** Weaves down and shoots straight down. */
	SHOOTER("shooter", SpriteType.EnemyShipB1, SpriteType.EnemyShipB2, 12, 8,
			Color.CYAN, FireStyle.DROP,
			MovePattern.SINE_WAVE, MovePattern.SEMICIRCLE, MovePattern.GENTLE_DIAGONAL),

	/** Shoots at the player. */
	BAT("bat", SpriteType.EnemyShipC1, SpriteType.EnemyShipC2, 12, 8,
			new Color(190, 120, 255), FireStyle.AIMED,
			MovePattern.CURVE, MovePattern.SEMICIRCLE, MovePattern.GENTLE_DIAGONAL),

	/** Flies fast at the player, alone. */
	ASSASSIN("assassin", SpriteType.EnemyShipSpecial, SpriteType.EnemyShipSpecial,
			16, 7, new Color(255, 150, 0), FireStyle.NONE,
			MovePattern.CHARGE),

	/** Slow, big and tough; fires a fan of three at the player. */
	TANK("tank", SpriteType.EnemyShipB1, SpriteType.EnemyShipB2, 12, 8,
			new Color(120, 220, 120), FireStyle.AIMED_FAN3,
			MovePattern.STRAIGHT_DOWN, MovePattern.SIDE_DIAGONAL),

	/*
	 * Elites: late-game versions of the types above. Same sprite and routes,
	 * drawn bigger in a warm colour of their own, with more health and a
	 * stronger attack.
	 */

	/** Elite normal (gold): tougher, and drops slow bullets straight down. */
	ELITE_NORMAL("eliteNormal", NORMAL, new Color(255, 205, 60), FireStyle.DROP),

	/** Elite shooter (coral): fires a fan of three straight down. */
	ELITE_SHOOTER("eliteShooter", SHOOTER, new Color(255, 110, 70),
			FireStyle.DROP_FAN3),

	/** Elite bat (hot pink): fires bursts of three quick shots at the player. */
	ELITE_BAT("eliteBat", BAT, new Color(255, 80, 190), FireStyle.AIMED_BURST3),

	/** Elite assassin (red): tougher, and sheds rings of bullets as it charges. */
	ELITE_ASSASSIN("eliteAssassin", ASSASSIN, new Color(255, 45, 45), FireStyle.RING),

	/** Elite tank (amber): very tough; fires a fan of five at the player. */
	ELITE_TANK("eliteTank", TANK, new Color(255, 170, 40), FireStyle.AIMED_FAN5);

	/** Key prefix in the balance file. */
	private final String balanceKey;
	/** First animation frame. */
	private final SpriteType spriteA;
	/** Second animation frame. */
	private final SpriteType spriteB;
	/** Sprite width in sprite pixels. */
	private final int spriteColumns;
	/** Sprite height in sprite pixels. */
	private final int spriteRows;
	/** Colour the sprite is drawn in. */
	private final Color color;
	/** How it shoots. */
	private final FireStyle fireStyle;
	/** Movement patterns it may use. */
	private final List<MovePattern> patterns;
	/** The regular type an elite is based on; null for regular types. */
	private final EnemyType base;

	EnemyType(final String balanceKey, final SpriteType spriteA,
			final SpriteType spriteB, final int spriteColumns,
			final int spriteRows, final Color color, final FireStyle fireStyle,
			final MovePattern... patterns) {
		this.balanceKey = balanceKey;
		this.spriteA = spriteA;
		this.spriteB = spriteB;
		this.spriteColumns = spriteColumns;
		this.spriteRows = spriteRows;
		this.color = color;
		this.fireStyle = fireStyle;
		this.patterns = Collections.unmodifiableList(Arrays.asList(patterns));
		this.base = null;
	}

	/** An elite: the base type's sprite and routes, its own colour and attack. */
	EnemyType(final String balanceKey, final EnemyType base, final Color color,
			final FireStyle fireStyle) {
		this.balanceKey = balanceKey;
		this.spriteA = base.spriteA;
		this.spriteB = base.spriteB;
		this.spriteColumns = base.spriteColumns;
		this.spriteRows = base.spriteRows;
		this.color = color;
		this.fireStyle = fireStyle;
		this.patterns = base.patterns;
		this.base = base;
	}

	/** @return True for the late-game elite versions. */
	public boolean isElite() {
		return this.base != null;
	}

	/** @return The regular type an elite is based on, or this type itself. */
	public EnemyType getBase() {
		return this.base == null ? this : this.base;
	}

	/** @return Key prefix in the balance file, such as "tank". */
	public String getBalanceKey() {
		return this.balanceKey;
	}

	/** @return First animation frame. */
	public SpriteType getSpriteA() {
		return this.spriteA;
	}

	/** @return Second animation frame. */
	public SpriteType getSpriteB() {
		return this.spriteB;
	}

	/** @return Sprite width in sprite pixels. */
	public int getSpriteColumns() {
		return this.spriteColumns;
	}

	/** @return Sprite height in sprite pixels. */
	public int getSpriteRows() {
		return this.spriteRows;
	}

	/** @return Colour the sprite is drawn in. */
	public Color getColor() {
		return this.color;
	}

	/** @return How it shoots. */
	public FireStyle getFireStyle() {
		return this.fireStyle;
	}

	/** @return Movement patterns it may use. */
	public List<MovePattern> getPatterns() {
		return this.patterns;
	}

	/**
	 * @param pattern Movement pattern.
	 * @return True if this type may fly that pattern.
	 */
	public boolean allows(final MovePattern pattern) {
		return this.patterns.contains(pattern);
	}
}
