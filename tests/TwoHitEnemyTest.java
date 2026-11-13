import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.Set;
import engine.Core;
import engine.CoinDropManager;
import engine.GameSettings;
import engine.GameState;
import engine.DrawManager.SpriteType;
import entity.Bullet;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import entity.enemy.Enemy;
import entity.enemy.EnemyBalance;
import entity.enemy.EnemyManager;
import entity.enemy.EnemyType;
import entity.enemy.MovePattern;
import entity.enemy.SpawnRequest;
import screen.GameScreen;

/** Standalone regression checks; run with isolated build/profile files. */
public final class TwoHitEnemyTest {
    private static int checks;

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void call(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    private static Set<Bullet> bullets(GameScreen screen) throws Exception {
        return (Set<Bullet>) get(screen, "bullets");
    }

    private static void hit(GameScreen screen, EnemyShip enemy) throws Exception {
        bullets(screen).add(new Bullet(enemy.getPositionX(), enemy.getPositionY(), -4));
        call(screen, "manageCollisions");
    }

    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);
        for (int level = 1; level <= 10; level++) {
            Field field = Core.class.getDeclaredField("SETTINGS_LEVEL_" + level);
            field.setAccessible(true);
            GameSettings settings = (GameSettings) field.get(null);
            check(settings.isDiagonalMovement() == (level == 3 || level == 9),
                "Diagonal stage placement " + level);
            EnemyShipFormation formation = new EnemyShipFormation(settings);
            int elites = 0;
            int ordinary = 0;
            for (EnemyShip enemy : formation) {
                if (enemy.getHealth() == 2) elites++;
                else ordinary++;
            }
            check(elites == (level == 6 ? 4 : level == 9 ? 7 : 0),
                "Stage placement " + level);
            check(ordinary + elites == settings.getFormationWidth()
                * settings.getFormationHeight(), "Formation size " + level);
        }
        for (int level : new int[] {6, 9}) {
            Field field = Core.class.getDeclaredField("SETTINGS_LEVEL_" + level);
            field.setAccessible(true);
            GameSettings settings = (GameSettings) field.get(null);
            Set<String> layouts = new java.util.HashSet<>();
            boolean upperRowElite = false;
            for (int attempt = 0; attempt < 50; attempt++) {
                int elites = 0;
                StringBuilder layout = new StringBuilder();
                for (EnemyShip enemy : new EnemyShipFormation(settings)) {
                    if (enemy.getHealth() == 2) {
                        elites++;
                        layout.append(enemy.getPositionX()).append(',')
                            .append(enemy.getPositionY()).append(';');
                        upperRowElite |= enemy.getPositionY()
                            < 100 + 40 * (settings.getFormationHeight() - 1);
                    }
                }
                check(elites == (level == 6 ? 4 : 7), "Exact random count " + level);
                layouts.add(layout.toString());
            }
            check(layouts.size() > 1, "Layouts vary across starts " + level);
            check(upperRowElite, "Selection includes upper rows " + level);
        }
        EnemyShip strongHit = new EnemyShip(0, 0, SpriteType.EnemyShipA1, 2);
        check(!strongHit.takeDamage(0), "Zero damage must not kill");
        check(strongHit.takeDamage(2), "Damage amount must be respected");
        check(!strongHit.takeDamage(1), "Lethal hit cannot be repeated");

        GameSettings combined = new GameSettings(3, 2, 10, 1000, true, 2);
        check(combined.isDiagonalMovement() && combined.getTwoHitEnemyCount() == 2,
            "Both settings can be enabled together");
        EnemyShipFormation combinedFormation = new EnemyShipFormation(combined);
        combinedFormation.attach(new screen.Screen(448, 520, 60));
        java.util.Map<EnemyShip, int[]> positions = new java.util.HashMap<>();
        int combinedElites = 0;
        for (EnemyShip enemy : combinedFormation) {
            positions.put(enemy, new int[] {enemy.getPositionX(), enemy.getPositionY()});
            if (enemy.getHealth() == 2) combinedElites++;
        }
        check(combinedElites == 2, "Combined formation has two elites");
        for (int frame = 0; frame < 20; frame++) combinedFormation.update();
        for (EnemyShip enemy : combinedFormation) {
            int[] before = positions.get(enemy);
            check(enemy.getPositionX() - before[0] == 8
                && enemy.getPositionY() - before[1] == 4,
                "Combined formation moves diagonally with spacing preserved");
        }

        // Normal stages now use wave-based enemies instead of a formation
        // (Team Entities enemy overhaul), so the game screen checks below use
        // the enemy manager.
        GameScreen screen = new GameScreen(new GameState(1, 0, 3, 0, 0, 0),
            new GameSettings(5, 2, 60, 2000), false, 448, 520, 60);
        screen.initialize();
        ((CoinDropManager) get(screen, "coinDropManager")).setDropChance(1);

        // A multi-hit enemy survives hits without rewards, then pays once.
        EnemyManager manager = waveManager(screen, 11);
        Enemy tank = bringIn(manager, manager.spawn(
            SpawnRequest.of(EnemyType.TANK, MovePattern.STRAIGHT_DOWN), 224, 490).get(0));
        check(tank.getHealth() > 1, "Tank needs several hits");
        int killsBefore = Core.getAchievementManager().getTotalEnemiesKilled();
        int health = tank.getHealth();
        shoot(screen, tank);
        check(tank.getHealth() == health - 1 && !tank.isDestroyed(), "First hit survives");
        check(bullets(screen).isEmpty(), "Nonlethal bullet consumed");
        check(screen.getGameState().getScore() == 0, "No score for first hit");
        check(screen.getGameState().getShipsDestroyed() == 0, "No kill for first hit");
        check(((Set<?>) get(screen, "coins")).isEmpty(), "No coin for first hit");
        check(Core.getAchievementManager().getTotalEnemiesKilled() == killsBefore,
            "No achievement progress for first hit");
        call(screen, "manageCollisions");
        check(tank.getHealth() == health - 1, "Consumed bullet cannot hit next frame");
        while (!tank.isDestroyed()) shoot(screen, tank);
        check(screen.getGameState().getScore() == tank.getPointValue(), "One score reward");
        check(screen.getGameState().getShipsDestroyed() == 1, "One kill");
        check(((Set<?>) get(screen, "coins")).size() == 1, "One coin reward");
        check(Core.getAchievementManager().getTotalEnemiesKilled() == killsBefore + 1,
            "One achievement increment");
        shoot(screen, tank);
        check(screen.getGameState().getShipsDestroyed() == 1, "Dead enemy cannot reward twice");
        bullets(screen).clear();

        // An ordinary enemy is still one hit.
        manager = waveManager(screen, 14);
        Enemy ordinary = bringIn(manager, manager.spawn(
            SpawnRequest.of(EnemyType.NORMAL, MovePattern.STRAIGHT_DOWN), 224, 490).get(0));
        shoot(screen, ordinary);
        check(ordinary.isDestroyed(), "Ordinary enemy remains one-hit");

        // A wide bullet damages every enemy it overlaps, once each.
        manager = waveManager(screen, 12);
        List<Enemy> line = manager.spawn(
            SpawnRequest.of(EnemyType.NORMAL, MovePattern.STRAIGHT_DOWN), 224, 490);
        bringIn(manager, line.get(line.size() - 1));
        bullets(screen).add(new Bullet((int) line.get(0).getCenterX() - 50, 40, -4, 100, 480));
        call(screen, "manageCollisions");
        int destroyed = 0;
        for (Enemy enemy : line) if (enemy.isDestroyed()) destroyed++;
        check(line.size() > 1 && destroyed == line.size(),
            "Wide bullet damages each overlapping enemy");
        check(bullets(screen).isEmpty(), "Wide bullet consumed");

        // A normal bullet stops at the first enemy it hits.
        manager = waveManager(screen, 15);
        List<Enemy> column = manager.spawn(
            SpawnRequest.of(EnemyType.NORMAL, MovePattern.STRAIGHT_DOWN), 224, 490);
        bringIn(manager, column.get(column.size() - 1));
        bullets(screen).add(new Bullet((int) column.get(0).getCenterX() - Bullet.DEFAULT_WIDTH / 2,
            40, -4, Bullet.DEFAULT_WIDTH, 480));
        call(screen, "manageCollisions");
        int downed = 0;
        for (Enemy enemy : column) if (enemy.isDestroyed()) downed++;
        check(downed == 1, "Normal bullet hits only one overlapping enemy");
        System.out.println("PASS: " + checks + " checks");
    }

    private static EnemyManager waveManager(GameScreen screen, long seed) throws Exception {
        EnemyManager manager = new EnemyManager(448, 520, 40, EnemyBalance.defaults(),
            new Random(seed));
        Field field = GameScreen.class.getDeclaredField("enemyManager");
        field.setAccessible(true);
        field.set(screen, manager);
        return manager;
    }

    /** Moves enemies until the given one is on screen and can be hit. */
    private static Enemy bringIn(EnemyManager manager, Enemy enemy) {
        for (int frame = 0; frame < 600 && !(enemy.isHittable() && enemy.isOnScreen()
            && enemy.getCenterY() > 70); frame++)
            manager.updateEnemies(false, 224, 490);
        check(enemy.isHittable() && enemy.isOnScreen(), "Enemy enters the screen");
        return enemy;
    }

    private static void shoot(GameScreen screen, Enemy enemy) throws Exception {
        bullets(screen).add(new Bullet(enemy.getPositionX(), enemy.getPositionY(), -4));
        call(screen, "manageCollisions");
    }
}
