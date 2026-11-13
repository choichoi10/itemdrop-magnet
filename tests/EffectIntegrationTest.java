import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.Point2D;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import item.ItemAPI;
import item.ItemAPI.*;
import item.ItemSystem;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import engine.*;
import entity.*;
import entity.Ship.ShipType;
import screen.*;
import shop.*;

/** Run in an isolated build/save directory, like the other standalone tests. */
public final class EffectIntegrationTest {
    private static int checks;
    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static Object get(Object target, String name) throws Exception {
        return field(target.getClass(), name).get(target);
    }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types); m.setAccessible(true);
        return m.invoke(target, args);
    }
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void buy(PurchaseManager p, ShopItem item) {
        check(p.isOwned(item) || p.purchase(item, shop.Currency.COINS) == PurchaseManager.PurchaseResult.SUCCESS,
                "Purchase " + item.getName());
    }
    private static BufferedImage canvas() throws Exception {
        BufferedImage image = new BufferedImage(448, 520, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        field(DrawManager.class, "backBufferGraphics").set(null, g);
        for (String suffix : new String[]{"Regular", "Big", "Selected"}) {
            Font font = (Font) field(DrawManager.class, "font" + suffix).get(null);
            field(DrawManager.class, "font" + suffix + "Metrics").set(null, g.getFontMetrics(font));
        }
        return image;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--item-demo".equals(args[0])) {
            if (args.length != 2) throw new IllegalArgumentException("Usage: EffectIntegrationTest --item-demo <output.png>");
            ItemDropDemo.render(args[1]);
            return;
        }
        Core.getLogger().setUseParentHandlers(false);
        DrawManager dm = Core.getDrawManager();
        // Verify every resource's bit pattern against its registered sprite, not just its size.
        Map<DrawManager.SpriteType, boolean[][]> sprites =
                (Map<DrawManager.SpriteType, boolean[][]>) field(DrawManager.class, "spriteMap").get(null);
        java.util.List<String> bits = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get("res/graphics")))
            if (line.matches("[01]+")) bits.add(line);
        check(bits.size() == sprites.size(), "All sprites registered");
        int index = 0;
        for (Map.Entry<DrawManager.SpriteType, boolean[][]> entry : sprites.entrySet()) {
            StringBuilder actual = new StringBuilder();
            for (boolean[] col : entry.getValue()) for (boolean pixel : col) actual.append(pixel ? '1' : '0');
            check(actual.toString().equals(bits.get(index++)), "Sprite order/data " + entry.getKey());
        }
        CurrencyManager.getInstance().addCoins(10000);
        PurchaseManager p = PurchaseManager.getInstance();
        buy(p, ShopCatalog.findByRef(ShopCategory.SHIP, ShipType.BIG_BULLET.name()));
        ShapeSkin shape = ShapeSkin.BIG_BULLET_CANNON;
        buy(p, ShopCatalog.findByRef(ShopCategory.SHAPE, shape.name()));
        check(p.selectShip(ShipType.BIG_BULLET), "Select Big Bullet");
        check(p.selectShape(ShipType.BIG_BULLET, shape), "Select A1 look");
        for (ShopItem effect : ShopCatalog.getByCategory(ShopCategory.EFFECT)) {
            buy(p, effect);
            if (!p.isEffectOn(effect)) p.toggleEffect(effect);
        }
        check(ShopCatalog.getByCategory(ShopCategory.EFFECT).size() == 4, "All four effects");
        ShopItem ghost = ShopCatalog.findByRef(ShopCategory.EFFECT, "ghost_trail");
        check(p.toggleEffect(ghost) && !p.isEffectOn(ghost), "Switch effect off");
        field(PurchaseManager.class, "instance").set(null, null);
        p = PurchaseManager.getInstance();
        check(!p.isEffectOn(ghost), "Effect off persists");
        check(p.getSelectedShape(ShipType.BIG_BULLET) == shape, "Look persists with effects");
        check(p.getSelectedShip() == ShipType.BIG_BULLET, "Ship persists with effects");
        check(p.isEffectOn("comet_trail"), "Other effect remains on");
        p.toggleEffect(ghost);
        Core.setSelectedShipType(ShipType.BIG_BULLET);

        ShipSelectScreen customize = new ShipSelectScreen(448, 520, 60);
        Class rowType = Class.forName("screen.ShipSelectScreen$Row");
        Object[] rows = rowType.getEnumConstants();
        check(rows.length == 5, "Look and effect rows coexist");
        Object lookRow = Enum.valueOf(rowType, "SHAPE");
        Object effectRow = Enum.valueOf(rowType, "EFFECT");
        call(customize, "change", new Class[]{rowType, int.class}, effectRow, 1);
        check((Integer)get(customize,"effectIndex") == 1, "Effect navigation independent");
        check(p.getSelectedShape(ShipType.BIG_BULLET) == shape, "Effect browse retains look");
        call(customize, "change", new Class[]{rowType, int.class}, lookRow, -1);
        check(p.getSelectedShape(ShipType.BIG_BULLET) == null, "Look navigation still works");
        call(customize, "change", new Class[]{rowType, int.class}, lookRow, 1);
        check(p.getSelectedShape(ShipType.BIG_BULLET) == shape, "Look restored");
        BufferedImage image = canvas();
        dm.drawScreenTitle(customize, "Customize");
        call(customize, "drawPreview", new Class[]{});
        for (Object row : rows) call(customize, "drawRow", new Class[]{rowType}, row);
        field(ShipSelectScreen.class, "row").set(customize, effectRow);
        call(customize, "drawInfo", new Class[]{});
        dm.drawKeyHints(customize, "up/down row | left/right browse | space on/off | esc back");
        if (args.length > 0) ImageIO.write(image, "png", new File(args[0], "customize.png"));

        ShopScreen shop = new ShopScreen(448, 520, 60);
        Class tabType = Class.forName("screen.ShopScreen$Tab");
        Object[] tabs = tabType.getEnumConstants();
        check(tabs.length == 6 && tabs[5].toString().equals("NIGHT_MARKET"), "Six tabs, night market last");
        for (String tab : new String[]{"LOOKS", "EFFECTS"}) {
            call(shop, "showTab", new Class[]{tabType}, Enum.valueOf(tabType, tab));
            check(((java.util.List<?>) get(shop, "entries")).size() == (tab.equals("LOOKS") ? 12 : 4), "Shop entries " + tab);
            image = canvas(); dm.drawScreenTitle(shop, "Shop");
            for (String method : new String[]{"drawBalances","drawTabs","drawList","drawDetail"})
                call(shop, method, new Class[]{});
            dm.drawKeyHints(shop, "arrows/click select | C coins | V diamonds | esc back");
            if (args.length > 0) ImageIO.write(image,"png",new File(args[0], "shop-" + tab + ".png"));
        }
        GameSettings settings = (GameSettings) field(Core.class, "SETTINGS_LEVEL_6").get(null);
        GameScreen game = new GameScreen(new GameState(6,0,3,0,0,0), settings, false,448,520,60);
        game.initialize();
        Ship ship = (Ship)get(game,"ship");
        check(ship.getType() == ShipType.BIG_BULLET && ship.getSpriteType() == shape.getSprite(), "Game keeps ship and look");
        check((Boolean)get(game,"cometTrailOn") && (Boolean)get(game,"lightningOn")
                && get(game,"ghostTrail") != null && get(game,"warpIn") != null, "Four effects initialized with A1 loadout");
        // A multi-hit enemy (tank) from the wave-based enemy system.
        entity.enemy.EnemyManager enemies = new entity.enemy.EnemyManager(448, 520, 40,
                entity.enemy.EnemyBalance.defaults(), new Random(4));
        field(GameScreen.class, "enemyManager").set(game, enemies);
        entity.enemy.Enemy tough = enemies.spawn(entity.enemy.SpawnRequest.of(
                entity.enemy.EnemyType.TANK, entity.enemy.MovePattern.STRAIGHT_DOWN), 224, 490).get(0);
        for (int frame = 0; frame < 600 && !(tough.isHittable() && tough.getCenterY() > 80); frame++)
            enemies.updateEnemies(false, 224, 490);
        check(tough.isHittable() && tough.getHealth() > 1, "Multi-hit enemy exists with effects");
        Set<Bullet> bullets = (Set<Bullet>)get(game,"bullets");
        LightningChain lightning = (LightningChain)get(game,"lightningChain");
        for(int hit=1, hits=tough.getHealth();hit<=hits;hit++) {
            bullets.add(new Bullet(tough.getPositionX(),tough.getPositionY(),-4));
            call(game,"manageCollisions",new Class[]{});
            check((Integer)get(lightning,"streak") == (hit<hits?0:1), "Lightning only on final hit " + hit);
        }
        Bullet big = BulletPool.getCustomBullet(200,300,-6,18,30);
        image = canvas(); dm.drawBulletTrail(big); dm.drawEntity(big,200,300);
        check(big.getWidth()==18 && big.getHeight()==30, "Comet does not change Big Bullet dimensions");
        int itemChecks = ItemDropChecks.run();
        System.out.println("EffectIntegrationTest: " + (checks + itemChecks) + " checks passed (effects "
                + checks + ", item drops/magnet " + itemChecks + ")");
    }

    // Focused adapters for item-package internals. Production visibility stays unchanged; public
    // manager operations are exercised through ItemAPI. Propagate original validation failures.
    private static final Class<?> BALANCE_TYPE = itemType("ItemBalance");
    private static final Class<?> DEFINITIONS_TYPE = itemType("ItemDefinitions");
    private static final Class<?> DROPS_TYPE = itemType("ItemDropSystem");

    private static Class<?> itemType(String name) {
        try { return Class.forName("item." + name); }
        catch (ClassNotFoundException error) { throw new AssertionError(error); }
    }

    private static RuntimeException reflectionFailure(ReflectiveOperationException error) {
        Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        return new IllegalStateException("Cannot exercise item internals", cause);
    }

    private static Object construct(Class<?> type, Class<?>[] signature, Object... arguments) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(signature);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException error) { throw reflectionFailure(error); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Object target, String name, Class<?>[] signature, Object... arguments) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, signature);
            method.setAccessible(true);
            return (T) method.invoke(target, arguments);
        } catch (ReflectiveOperationException error) { throw reflectionFailure(error); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Object target, String name) {
        try { return (T) field(target.getClass(), name).get(target); }
        catch (ReflectiveOperationException error) { throw reflectionFailure(error); }
        catch (Exception error) { throw new IllegalStateException("Cannot read item state", error); }
    }

    private static final class ItemBalance {
        static final String PATH_PROPERTY = "invaders.itemBalance";
        final Object delegate;
        final double horizontalSpeed, magnetRange, magnetSpeed;
        final long directionChangeMillis;
        ItemBalance(Properties settings) {
            delegate = construct(BALANCE_TYPE, new Class<?>[]{Properties.class}, settings);
            horizontalSpeed = read(delegate, "horizontalSpeed");
            directionChangeMillis = read(delegate, "directionChangeMillis");
            magnetRange = read(delegate, "magnetRange");
            magnetSpeed = read(delegate, "magnetSpeed");
        }
    }

    private static final class ItemDefinitions {
        final Object delegate;
        ItemDefinitions(ItemBalance balance) {
            delegate = construct(DEFINITIONS_TYPE, new Class<?>[]{BALANCE_TYPE}, balance.delegate);
        }
        ItemInfo find(String id) { return invoke(delegate, "find", new Class<?>[]{String.class}, id); }
    }

    private static final class ItemDropSystem {
        final Object delegate;
        ItemDropSystem(ItemDefinitions definitions, ItemBalance balance, Random random) {
            delegate = construct(DROPS_TYPE, new Class<?>[]{DEFINITIONS_TYPE, BALANCE_TYPE, Random.class},
                    definitions.delegate, balance.delegate, random);
        }
        void beginLevel(LevelRules rules) { invoke(delegate, "beginLevel", new Class<?>[]{LevelRules.class}, rules); }
        DropView spawn(DropSource source, double x, double y, Map<String, Integer> stacks) {
            return invoke(delegate, "spawn", new Class<?>[]{DropSource.class, double.class, double.class, Map.class},
                    source, x, y, stacks);
        }
        Frame advance(long delta, PlayerSnapshot player) { return advance(delta, player, false); }
        Frame advance(long delta, PlayerSnapshot player, boolean magnet) {
            return new Frame(invoke(delegate, "advance", new Class<?>[]{long.class, PlayerSnapshot.class, boolean.class},
                    delta, player, magnet));
        }
        void completePickup(long id) { invoke(delegate, "completePickup", new Class<?>[]{long.class}, id); }
        List<DropView> snapshot() { return invoke(delegate, "snapshot", new Class<?>[]{}); }
        static final class Frame {
            final List<DropView> expired, contacts;
            Frame(Object result) {
                expired = read(result, "expired");
                contacts = read(result, "contacts");
            }
        }
    }

    private static final class ItemManager {
        final item.ItemManager delegate;
        final ItemAPI api;
        ItemManager(int capacity, ItemBalance balance, Random random) {
            this((item.ItemManager) construct(item.ItemManager.class,
                    new Class<?>[]{int.class, BALANCE_TYPE, Random.class}, capacity, balance.delegate, random));
        }
        ItemManager(item.ItemManager delegate) { this.delegate = delegate; api = new ItemAPI(delegate); }
        void equip(ItemInfo item) { invoke(delegate, "equip", new Class<?>[]{ItemInfo.class}, item); }
        ItemInfo getItemInfo(String id) { return api.getItemInfo(id); }
        void beginLevel(LevelRules rules, LifePort port) { delegate.beginLevel(rules, port); }
        void onEnemyDefeated(DropSource source, double x, double y) { api.onEnemyDefeated(source, x, y); }
        void update(long delta, PlayerSnapshot player) { delegate.update(delta, player); }
        GrantResult tryGrant(GrantRequest request) { return api.tryGrant(request); }
        ItemAPI.View getView() { return api.getView(); }
        List<ItemEvent> drainEvents() { return api.drainEvents(); }
    }

    /** Item drop motion, swept pickup, magnet pursuit/conversion and configuration regressions. */
    private static final class ItemDropChecks {
        private static int checks;
        private static final LifePort LIFE = new LifePort() {
            public boolean canAddLife() { return true; }
            public boolean tryAddLife() { return true; }
        };

        private static void check(boolean condition, String message) {
            checks++;
            if (!condition) throw new AssertionError(message);
        }

        private static void near(double actual, double expected, String message) {
            check(Math.abs(actual - expected) < 1e-7, message + ": " + actual + " != " + expected);
        }

        static ItemBalance balance(String... pairs) {
            Properties properties = new Properties();
            for (int i = 0; i < pairs.length; i += 2) properties.setProperty(pairs[i], pairs[i + 1]);
            return new ItemBalance(properties);
        }

        static LevelRules rules(double width, double height, double speed, String item) {
            Map<String, Double> weights = new LinkedHashMap<String, Double>();
            weights.put(item, 1.0);
            Map<DropSource, DropRule> sources = new EnumMap<DropSource, DropRule>(DropSource.class);
            sources.put(DropSource.REGULAR_ENEMY, new DropRule(0.0, weights));
            sources.put(DropSource.SPECIAL_ENEMY, new DropRule(1.0, weights));
            return new LevelRules("test", 0, width, 0, height, speed, 24, 24, 1, sources);
        }

        static PlayerSnapshot player(double x, double y, boolean available) {
            return new PlayerSnapshot(new Bounds(x, y, 24, 16), available, available);
        }

        static final class Drops {
            final ItemDropSystem system;
            final LevelRules rules;
            Drops(ItemBalance balance, long seed, double width, double height, double speed) {
                system = new ItemDropSystem(new ItemDefinitions(balance), balance, new Random(seed));
                rules = rules(width, height, speed, "shield");
                system.beginLevel(rules);
            }
            DropView spawn(double x, double y) {
                return system.spawn(DropSource.SPECIAL_ENEMY, x, y, Collections.<String, Integer>emptyMap());
            }
            DropView only() {
                check(system.snapshot().size() == 1, "Exactly one live drop");
                return system.snapshot().get(0);
            }
        }

        private static void movementAndTiming() {
            Drops drops = new Drops(balance(), 18, 120, 10000, 80);
            DropView initial = drops.spawn(60, 50);
            drops.system.advance(0, player(500, 500, false));
            near(drops.only().bounds.x, initial.bounds.x, "Zero elapsed time preserves X");
            near(drops.only().bounds.y, initial.bounds.y, "Zero elapsed time preserves Y");
            boolean left = false, right = false;
            double lastX = initial.bounds.x, lastY = initial.bounds.y;
            for (int i = 0; i < 160; i++) {
                drops.system.advance(50, player(500, 500, false));
                DropView view = drops.only();
                check(view.bounds.y > lastY, "Free movement always descends");
                check(view.bounds.x >= 0 && view.bounds.x + view.bounds.width <= 120 + 1e-7,
                        "Walls contain the whole item");
                check(!view.grounded, "Drops never land or use the former one-millisecond ground TTL");
                left |= view.bounds.x < lastX;
                right |= view.bounds.x > lastX;
                lastX = view.bounds.x; lastY = view.bounds.y;
            }
            check(left && right, "Motion includes both leftward and rightward segments");

            Drops whole = new Drops(balance(), 77, 448, 2000, 80);
            Drops split = new Drops(balance(), 77, 448, 2000, 80);
            whole.spawn(220, 60); split.spawn(220, 60);
            whole.system.advance(4200, player(900, 900, false));
            for (int i = 0; i < 42; i++) split.system.advance(100, player(900, 900, false));
            near(whole.only().bounds.x, split.only().bounds.x, "Direction changes preserve split-frame X");
            near(whole.only().bounds.y, split.only().bounds.y, "Direction changes preserve split-frame Y");

            Drops fixed = new Drops(balance("drop.directionChangeMillis", "10000"), 77, 448, 2000, 80);
            fixed.spawn(220, 60);
            fixed.system.advance(4200, player(900, 900, false));
            check(Math.abs(fixed.only().bounds.x - whole.only().bounds.x) > 1,
                    "Direction interval changes the trajectory");
        }

        private static void exitAndReflection() {
            Drops drops = new Drops(balance("drop.directionChangeMillis", "10000"), 12, 90, 400, 80);
            drops.spawn(45, 40);
            double previousX = drops.only().bounds.x;
            double previousDelta = 0;
            boolean bounced = false;
            for (int i = 0; i < 200; i++) {
                ItemDropSystem.Frame frame = drops.system.advance(50, player(500, 500, false));
                if (!frame.expired.isEmpty()) break;
                double x = drops.only().bounds.x;
                double dx = x - previousX;
                if (dx * previousDelta < 0) bounced = true;
                previousX = x; previousDelta = dx;
            }
            check(bounced, "Horizontal direction reflects at a side wall before random reselection");
            check(drops.system.snapshot().isEmpty(), "Drop eventually leaves the bottom");

            Drops bottom = new Drops(balance(), 3, 400, 200, 80);
            bottom.spawn(200, 190);
            boolean partlyVisible = false, expired = false;
            for (int i = 0; i < 100; i++) {
                ItemDropSystem.Frame frame = bottom.system.advance(5, player(900, 900, false));
                if (!frame.expired.isEmpty()) {
                    check(frame.expired.size() == 1, "Exactly one expiry");
                    check(frame.expired.get(0).bounds.y >= 200 - 1e-7, "Expiry waits for the top edge to exit");
                    expired = true; break;
                }
                Bounds bounds = bottom.only().bounds;
                partlyVisible |= bounds.y + bounds.height > 200 && bounds.y < 200;
            }
            check(partlyVisible, "Partly visible drops keep falling past the former floor");
            check(expired && bottom.system.snapshot().isEmpty(), "Fully offscreen drops are removed");
        }

        private static void sweptContacts() {
            ItemBalance fast = balance("drop.horizontalSpeed", "400", "drop.directionChangeMillis", "10000");
            Drops reference = new Drops(fast, 4, 3000, 3000, 400);
            Bounds start = reference.spawn(1500, 100).bounds;
            reference.system.advance(1000, player(4000, 4000, false));
            Bounds end = reference.only().bounds;
            double hitX = (start.x + end.x) / 2 + 10;
            double hitY = (start.y + end.y) / 2 + 8;
            Drops hit = new Drops(fast, 4, 3000, 3000, 400);
            hit.spawn(1500, 100);
            ItemDropSystem.Frame frame = hit.system.advance(1000, player(hitX, hitY, true));
            check(frame.contacts.size() == 1, "Fast diagonal path collects without endpoint overlap");
            check(frame.expired.isEmpty(), "Contact is not expiry");
            hit.system.completePickup(frame.contacts.get(0).dropId);
            check(hit.system.snapshot().isEmpty(), "Successful contact can be completed exactly once");

            Drops miss = new Drops(fast, 4, 3000, 3000, 400);
            miss.spawn(1500, 100);
            double missX = start.x + (end.x - start.x) * 0.85;
            double missY = start.y + (end.y - start.y) * 0.15;
            check(miss.system.advance(1000, player(missX, missY, true)).contacts.isEmpty(),
                    "Enclosing rectangle corner is not a diagonal contact");

            Drops shortScreen = new Drops(fast, 4, 3000, 450, 400);
            shortScreen.spawn(1500, 100);
            ItemDropSystem.Frame crossed = shortScreen.system.advance(10000, player(hitX, hitY, true));
            check(crossed.contacts.size() == 1 && crossed.expired.isEmpty(),
                    "Crossing the player before exiting wins in a long frame");

            Drops bounceReference = new Drops(fast, 18, 120, 2000, 100);
            bounceReference.spawn(60, 60);
            bounceReference.system.advance(600, player(900, 900, false));
            Bounds mid = bounceReference.only().bounds;
            Drops bounceHit = new Drops(fast, 18, 120, 2000, 100);
            bounceHit.spawn(60, 60);
            check(bounceHit.system.advance(1500, player(mid.x, mid.y, true)).contacts.size() == 1,
                    "Contact checks the reflected path within one frame");
        }

        private static void magnetMovement() {
            ItemBalance settings = balance("equip.magnet.range", "100", "equip.magnet.speed", "260");
            Drops far = new Drops(settings, 7, 800, 1000, 80);
            far.spawn(100, 100);
            ItemDropSystem.Frame frame = far.system.advance(100, player(650, 800, true), true);
            check(frame.contacts.isEmpty() && !far.only().magnetized, "Magnet does not collect distant drops");

            Drops near = new Drops(settings, 7, 800, 1000, 80);
            Bounds initial = near.spawn(300, 300).bounds;
            near.system.advance(50, player(288, 220, true), true);
            DropView attracted = near.only();
            check(attracted.magnetized, "Entering range latches attraction");
            check(attracted.bounds.y < initial.y, "Attraction can move upward toward the ship");
            check(attracted.bounds.y + attracted.bounds.height >= 220, "Attraction does not overshoot the ship");
            double beforeX = attracted.bounds.x;
            near.system.advance(50, player(680, 220, true), true);
            check(near.only().bounds.x > beforeX && near.only().magnetized,
                    "Latched drop follows a moving ship outside its original radius");

            Bounds beforeUnavailable = near.only().bounds;
            check(near.system.advance(50, player(680, 0, false), true).contacts.isEmpty(),
                    "Unavailable player cannot collect");
            check(near.only().bounds.y > beforeUnavailable.y, "Unavailable player suspends homing");
            check(near.only().magnetized, "Unavailable player does not forget attraction");
            ItemDropSystem.Frame collected = near.system.advance(10000, player(680, 220, true), true);
            check(collected.contacts.size() == 1 && collected.contacts.get(0).magnetized,
                    "Large homing step reaches contact without overshooting");

            Drops contactAtZero = new Drops(settings, 9, 800, 1000, 80);
            contactAtZero.spawn(300, 300);
            check(contactAtZero.system.advance(0, player(288, 288, true), true).contacts.size() == 1,
                    "Zero-time overlapping pickup still works");

            ItemBalance tinyRange = balance("equip.magnet.range", "0.000001", "drop.directionChangeMillis", "10000");
            Drops endpointTrace = new Drops(tinyRange, 17, 800, 1000, 80);
            endpointTrace.spawn(300, 300);
            endpointTrace.system.advance(100, player(900, 900, false));
            Bounds endpoint = endpointTrace.only().bounds;
            Drops endpointCapture = new Drops(tinyRange, 17, 800, 1000, 80);
            endpointCapture.spawn(300, 300);
            ItemDropSystem.Frame endpointFrame = endpointCapture.system.advance(100,
                    new PlayerSnapshot(new Bounds(endpoint.x, endpoint.y, 24, 24), true, true), true);
            check(endpointFrame.contacts.size() == 1 && endpointFrame.contacts.get(0).magnetized,
                    "Capture near a segment endpoint marks an overlapping contact as magnetized");

            // A ship is placed ahead of a measured falling trajectory: the item starts outside the
            // radius and enters it during one long frame, so an endpoint-only range test would miss it.
            ItemBalance shortRange = balance("equip.magnet.range", "45", "drop.directionChangeMillis", "10000");
            Drops trace = new Drops(shortRange, 24, 2000, 2000, 300);
            trace.spawn(1000, 100);
            trace.system.advance(500, player(3000, 3000, false));
            Bounds target = trace.only().bounds;
            Drops capture = new Drops(shortRange, 24, 2000, 2000, 300);
            capture.spawn(1000, 100);
            ItemDropSystem.Frame captured = capture.system.advance(1000, player(target.x, target.y, true), true);
            check(captured.contacts.size() == 1 && captured.contacts.get(0).magnetized,
                    "A swept range entry switches to homing within the frame");
        }

        private static ItemManager manager(boolean magnet, String kind) {
            ItemBalance settings = balance("equip.magnet.range", "100");
            ItemManager manager = new ItemManager(1, settings, new Random(19));
            if (magnet) manager.equip(manager.getItemInfo("magnet"));
            manager.beginLevel(rules(800, 1000, 80, kind), LIFE);
            return manager;
        }

        private static void conversionAndEvents() {
            ItemManager normal = manager(false, "shield");
            check(normal.tryGrant(new GrantRequest("fill", "shield", GrantSource.REWARD, GrantTiming.NOW)).isSuccess(),
                    "Prepare a full ordinary inventory");
            normal.onEnemyDefeated(DropSource.SPECIAL_ENEMY, 300, 300);
            normal.drainEvents();
            normal.update(0, player(288, 288, true));
            check(normal.getView().drops.size() == 1, "Uncollectable ordinary drop remains");
            check(normal.drainEvents().isEmpty(), "Ordinary failure does not convert to points");
            double failedY = normal.getView().drops.get(0).bounds.y;
            normal.update(20, player(288, 288, true));
            check(normal.getView().drops.get(0).bounds.y > failedY,
                    "A rejected ordinary pickup keeps moving while still overlapping");
            normal.drainEvents();
            normal.update(30000, player(2000, 2000, true));
            List<ItemEvent> expiredEvents = normal.drainEvents();
            check(normal.getView().drops.isEmpty()
                    && expiredEvents.stream().filter(e -> e.type == EventType.ITEM_EXPIRED).count() == 1,
                    "Rejected ordinary drop eventually leaves the bottom and expires exactly once");
            check(expiredEvents.stream().noneMatch(e -> e.type == EventType.ITEM_CONVERTED),
                    "Ordinary drop expiration never grants conversion score");
            normal.update(100, player(2000, 2000, true));
            check(normal.drainEvents().isEmpty(), "Expired ordinary item does not expire twice");

            ItemManager magnet = manager(true, "shield");
            magnet.tryGrant(new GrantRequest("fill", "shield", GrantSource.REWARD, GrantTiming.NOW));
            magnet.onEnemyDefeated(DropSource.SPECIAL_ENEMY, 300, 300);
            magnet.drainEvents();
            magnet.update(0, player(288, 288, true));
            List<ItemEvent> events = magnet.drainEvents();
            check(events.size() == 1 && events.get(0).type == EventType.ITEM_CONVERTED,
                    "Magnet contact with full inventory emits one conversion");
            check(magnet.getView().drops.isEmpty(), "Converted drop is removed");
            near(magnet.getItemInfo("magnet").magnitude, 50, "Default conversion award remains fifty points");
            magnet.update(100, player(288, 288, true));
            check(magnet.drainEvents().isEmpty(), "Conversion is never awarded twice");

            ItemManager stacks = manager(true, "rapid_fire");
            stacks.onEnemyDefeated(DropSource.SPECIAL_ENEMY, 300, 300);
            for (int i = 0; i < stacks.getItemInfo("rapid_fire").maxStacks; i++) {
                check(stacks.tryGrant(new GrantRequest("stack" + i, "rapid_fire", GrantSource.REWARD,
                        GrantTiming.NOW)).isSuccess(), "Fill permanent stack " + i);
            }
            stacks.drainEvents();
            stacks.update(0, player(288, 288, true));
            check(stacks.drainEvents().stream().anyMatch(e -> e.type == EventType.ITEM_CONVERTED),
                    "Magnet also converts a drop that reaches the stack cap before contact");

            ItemManager succeeds = manager(true, "shield");
            succeeds.onEnemyDefeated(DropSource.SPECIAL_ENEMY, 300, 300);
            succeeds.drainEvents();
            succeeds.update(300, player(288, 220, true));
            check(succeeds.getView().drops.isEmpty() && succeeds.getView().slots.get(0) != null,
                    "Homing contact stores an item when space is available");
            check(succeeds.drainEvents().stream().anyMatch(e -> e.type == EventType.ITEM_COLLECTED),
                    "Successful magnet collection uses the existing collection event");

            ItemManager unavailable = manager(true, "shield");
            unavailable.onEnemyDefeated(DropSource.SPECIAL_ENEMY, 300, 300);
            unavailable.drainEvents();
            unavailable.update(0, player(288, 288, false));
            check(unavailable.getView().drops.size() == 1 && unavailable.drainEvents().isEmpty(),
                    "Unavailable ship gets neither pickup nor conversion");
        }

        private static void scoreBridge() throws Exception {
            String oldPath = System.getProperty(ItemBalance.PATH_PROPERTY);
            Path path = Files.createTempFile("item-magnet-test-", ".properties");
            try {
                for (int award : new int[]{50, 73}) {
                    Properties settings = new Properties();
                    settings.setProperty("inventory.capacity", "1");
                    for (String key : new String[]{"life", "rapidFire", "bulletSpeed", "freeze", "boost", "scoreBoost"})
                        settings.setProperty("drop.weight." + key, "0");
                    settings.setProperty("drop.weight.shield", "1");
                    if (award != 50) settings.setProperty("equip.magnet.convertScore", Integer.toString(award));
                    try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) { settings.store(writer, "Test"); }
                    System.setProperty(ItemBalance.PATH_PROPERTY, path.toString());
                    check(ItemSystem.grantEquipment("magnet") && ItemSystem.equip("magnet"), "Equip the real game magnet");
                    ItemSystem system = ItemSystem.forLevel(1);
                    final int[] score = {0};
                    Entity ship = new Entity(288, 220, 24, 16, Color.GREEN);
                    system.beginLevel(1, 800, 1000, ship, new ItemSystem.GameHooks() {
                        public int getLives() { return 3; }
                        public void addLife() { }
                        public void addScore(int value) { score[0] += value; }
                    });
                    Field managerField = ItemSystem.class.getDeclaredField("manager");
                    managerField.setAccessible(true);
                    ItemManager manager = new ItemManager((item.ItemManager) managerField.get(system));
                    Field rulesField = item.ItemManager.class.getDeclaredField("rules");
                    rulesField.setAccessible(true);
                    near(((LevelRules) rulesField.get(manager.delegate)).floorY, 1000,
                            "Game bridge uses the screen bottom instead of the ship bottom");
                    check(system.api().tryGrant(new GrantRequest("bridge-fill", "shield", GrantSource.REWARD,
                            GrantTiming.NOW)).isSuccess(), "Fill real game inventory");
                    manager.onEnemyDefeated(DropSource.SPECIAL_ENEMY, 300, 300);
                    manager.update(0, player(288, 220, true));
                    check(score[0] == 0 && system.api().getView().drops.size() == 1,
                            "Entering attraction radius does not award score before contact");
                    manager.update(1000, player(288, 220, true));
                    check(score[0] == award && system.api().getView().drops.isEmpty(),
                            "Actual ItemSystem GameHooks receives conversion award " + award);
                    manager.update(1000, player(288, 220, true));
                    check(score[0] == award, "Real game score conversion happens exactly once");
                    system.endLevel();
                }
            } finally {
                ItemSystem.unequip();
                if (oldPath == null) System.clearProperty(ItemBalance.PATH_PROPERTY);
                else System.setProperty(ItemBalance.PATH_PROPERTY, oldPath);
                Files.deleteIfExists(path);
            }
        }

        private static void configuration() {
            ItemBalance custom = balance("drop.horizontalSpeed", "90", "drop.directionChangeMillis", "300",
                    "equip.magnet.range", "180", "equip.magnet.speed", "500");
            near(custom.horizontalSpeed, 90, "Configured horizontal speed");
            check(custom.directionChangeMillis == 300, "Configured direction interval");
            near(custom.magnetRange, 180, "Configured magnet range");
            near(custom.magnetSpeed, 500, "Configured attraction speed");
            for (String key : new String[]{"drop.horizontalSpeed", "drop.directionChangeMillis",
                    "equip.magnet.range", "equip.magnet.speed"}) {
                for (String invalid : new String[]{"0", "-1", "NaN", "Infinity"}) {
                    boolean rejected = false;
                    try { balance(key, invalid); } catch (IllegalArgumentException expected) { rejected = true; }
                    check(rejected, "Reject invalid setting " + key + "=" + invalid);
                }
            }
            check(!new ItemDefinitions(custom).find("magnet").description.contains("land"),
                    "Magnet description no longer promises landing-based collection");
        }

        static int run() throws Exception {
            movementAndTiming();
            exitAndReflection();
            sweptContacts();
            magnetMovement();
            conversionAndEvents();
            configuration();
            scoreBridge();
            return checks;
        }
    }

    /** Optional storyboard using production movement, rendering and score callbacks. */
    private static final class ItemDropDemo {
        private static final int WIDTH = 448, HEIGHT = 520;
        private static final Color CYAN = new Color(80, 220, 255);

        private static final class Scene {
            final ItemSystem system;
            final ItemManager manager;
            final Ship ship = new Ship(208, 450);
            final Map<Long, List<Point2D.Double>> paths = new LinkedHashMap<Long, List<Point2D.Double>>();
            int score, collected, converted;

            Scene(boolean magnet, boolean full, long seed) throws Exception {
                if (magnet) {
                    if (!ItemSystem.grantEquipment("magnet") || !ItemSystem.equip("magnet"))
                        throw new AssertionError("Cannot prepare demo magnet");
                } else ItemSystem.unequip();
                Constructor<ItemSystem> constructor = ItemSystem.class.getDeclaredConstructor(Random.class);
                constructor.setAccessible(true);
                system = constructor.newInstance(new Random(seed));
                system.beginLevel(1, WIDTH, HEIGHT, ship, new ItemSystem.GameHooks() {
                    public int getLives() { return 3; }
                    public void addLife() { }
                    public void addScore(int points) { score += points; }
                });
                Field field = ItemSystem.class.getDeclaredField("manager");
                field.setAccessible(true);
                manager = new ItemManager((item.ItemManager) field.get(system));
                if (full && !system.api().tryGrant(new GrantRequest("demo-fill", "shield",
                        GrantSource.REWARD, GrantTiming.NOW)).isSuccess())
                    throw new AssertionError("Cannot fill demo inventory");
                manager.drainEvents();
            }

            void spawn(double x, double y) {
                manager.onEnemyDefeated(DropSource.SPECIAL_ENEMY, x, y);
                remember();
            }

            void step(long millis, boolean available) {
                manager.update(millis, new PlayerSnapshot(new Bounds(ship.getPositionX(), ship.getPositionY(),
                        ship.getWidth(), ship.getHeight()), available, available));
                remember();
                for (ItemEvent event : manager.drainEvents()) {
                    if (event.type == EventType.ITEM_COLLECTED) collected++;
                    if (event.type == EventType.ITEM_CONVERTED) converted++;
                    if ((event.type == EventType.ITEM_COLLECTED || event.type == EventType.ITEM_CONVERTED)
                            && event.bounds != null) remember(event.dropId, event.bounds);
                }
            }

            void remember() {
                for (DropView drop : system.api().getView().drops) remember(drop.dropId, drop.bounds);
            }

            void remember(long id, Bounds bounds) {
                paths.computeIfAbsent(id, ignored -> new ArrayList<Point2D.Double>()).add(
                        new Point2D.Double(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2));
            }

            void render(Graphics2D page, int offset, String title, String caption, boolean magnet) throws Exception {
                Graphics2D g = (Graphics2D) page.create(offset, 0, WIDTH, HEIGHT + 110);
                g.setColor(new Color(13, 19, 31)); g.fillRect(0, 0, WIDTH, HEIGHT + 110);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
                g.setColor(Color.WHITE); g.drawString(title, 18, 29);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                g.setColor(new Color(166, 183, 204)); g.drawString(caption, 18, 53);
                Graphics2D play = (Graphics2D) g.create(0, 70, WIDTH, HEIGHT);
                play.setColor(new Color(3, 8, 16)); play.fillRect(0, 0, WIDTH, HEIGHT);
                Random stars = new Random(6);
                play.setColor(new Color(71, 82, 99));
                for (int i = 0; i < 85; i++) play.fillRect(stars.nextInt(WIDTH), stars.nextInt(HEIGHT), 1, 1);
                play.setColor(new Color(80, 96, 118)); play.drawLine(0, 40, WIDTH, 40);
                play.drawLine(0, HEIGHT - 1, WIDTH, HEIGHT - 1);
                play.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
                play.setColor(Color.WHITE); play.drawString("SCORE " + score, 16, 24);
                play.drawString(magnet ? "MAGNET ON" : "MAGNET OFF", 304, 24);
                if (magnet) {
                    int cx = ship.getPositionX() + ship.getWidth() / 2;
                    int cy = ship.getPositionY() + ship.getHeight() / 2;
                    play.setColor(new Color(32, 117, 148));
                    play.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                            10, new float[]{5, 5}, 0));
                    play.drawOval(cx - 140, cy - 140, 280, 280);
                }
                play.setStroke(new BasicStroke(2));
                for (List<Point2D.Double> path : paths.values()) {
                    play.setColor(magnet ? new Color(45, 145, 178) : new Color(126, 104, 51));
                    for (int i = 1; i < path.size(); i++) {
                        Point2D.Double a = path.get(i - 1), b = path.get(i);
                        play.drawLine((int)a.x, (int)a.y, (int)b.x, (int)b.y);
                    }
                    Point2D.Double start = path.get(0);
                    play.drawOval((int)start.x - 4, (int)start.y - 4, 8, 8);
                    if (magnet && system.api().getView().drops.isEmpty()) {
                        Point2D.Double end = path.get(path.size() - 1);
                        play.setColor(CYAN); play.drawOval((int)end.x - 10, (int)end.y - 10, 20, 20);
                    }
                }
                // Use the same production item renderer and ship sprite as GameScreen.
                system.drawDrops(play);
                DrawManager drawing = Core.getDrawManager();
                Field graphicsField = DrawManager.class.getDeclaredField("backBufferGraphics");
                graphicsField.setAccessible(true);
                Object oldGraphics = graphicsField.get(null);
                try {
                    graphicsField.set(null, play);
                    drawing.drawEntity(ship, ship.getPositionX(), ship.getPositionY());
                } finally { graphicsField.set(null, oldGraphics); }
                play.setColor(new Color(175, 192, 214));
                play.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                play.drawString("Screen bottom: drops leave when fully outside", 76, HEIGHT - 8);
                play.dispose();
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                g.setColor(CYAN);
                String outcome = !magnet ? "Observed trails: sideways changes + wall reflection"
                        : converted > 0 ? "Physical contact -> +" + score + " points (inventory full)"
                        : "Nearby items move toward the ship; no instant pickup";
                g.drawString(outcome, 18, HEIGHT + 96);
                g.dispose();
            }
        }

        static void render(String outputPath) throws Exception {
            Core.getLogger().setUseParentHandlers(false);
            String previous = System.getProperty(ItemBalance.PATH_PROPERTY);
            Path config = Files.createTempFile("item-demo-", ".properties");
            List<Scene> scenes = new ArrayList<Scene>();
            try {
                Properties settings = new Properties();
                settings.setProperty("inventory.capacity", "1");
                for (String item : new String[]{"life", "rapidFire", "bulletSpeed", "freeze", "boost", "scoreBoost"})
                    settings.setProperty("drop.weight." + item, "0");
                settings.setProperty("drop.weight.shield", "1");
                try (Writer writer = Files.newBufferedWriter(config, StandardCharsets.UTF_8)) { settings.store(writer, "Demo"); }
                System.setProperty(ItemBalance.PATH_PROPERTY, config.toString());
                Scene free = new Scene(false, false, 15); scenes.add(free);
                free.spawn(26, 80); free.spawn(422, 100); free.spawn(225, 130);
                for (int i = 0; i < 70; i++) free.step(50, false);
                Scene homing = new Scene(true, false, 15); scenes.add(homing);
                homing.spawn(125, 365); homing.spawn(320, 375); homing.spawn(225, 325);
                for (int i = 0; i < 5; i++) homing.step(50, true);
                if (homing.system.api().getView().drops.isEmpty()) throw new AssertionError("Homing snapshot must precede contact");
                Scene conversion = new Scene(true, true, 15); scenes.add(conversion);
                conversion.spawn(125, 365);
                for (int i = 0; i < 20; i++) conversion.step(50, true);
                if (conversion.score != 50 || conversion.converted != 1)
                    throw new AssertionError("Expected exactly one real +50 conversion");
                BufferedImage image = new BufferedImage(WIDTH * 3 + 24, HEIGHT + 110, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setColor(new Color(38, 49, 66)); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                free.render(graphics, 0, "1. Random downward motion", "3.50 s  |  magnet unequipped  |  real recorded positions", false);
                homing.render(graphics, WIDTH + 12, "2. Nearby magnet attraction", "0.25 s  |  range 140 px  |  attraction speed 260 px/s", true);
                conversion.render(graphics, (WIDTH + 12) * 2, "3. Contact + score conversion", "1.00 s  |  one-slot inventory already full", true);
                graphics.dispose();
                File output = new File(outputPath);
                File parent = output.getAbsoluteFile().getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Cannot create output directory");
                if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("No PNG writer");
                System.out.println("EffectIntegrationTest item demo: saved " + output + "; actual score conversion +" + conversion.score);
            } finally {
                for (Scene scene : scenes) scene.system.endLevel();
                ItemSystem.unequip();
                if (previous == null) System.clearProperty(ItemBalance.PATH_PROPERTY);
                else System.setProperty(ItemBalance.PATH_PROPERTY, previous);
                Files.deleteIfExists(config);
            }
        }
    }
}
