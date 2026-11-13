package item;

import java.awt.Color;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import entity.Entity;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import item.ItemAPI.*;

/** Deterministic standalone regression checks. Run with an isolated classes/save directory. */
public final class ItemDropMagnetTest {
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
                ItemManager manager = (ItemManager) managerField.get(system);
                Field rulesField = ItemManager.class.getDeclaredField("rules");
                rulesField.setAccessible(true);
                near(((LevelRules) rulesField.get(manager)).floorY, 1000,
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

    public static void main(String[] args) throws Exception {
        movementAndTiming();
        exitAndReflection();
        sweptContacts();
        magnetMovement();
        conversionAndEvents();
        configuration();
        scoreBridge();
        System.out.println("ItemDropMagnetTest: " + checks + " checks passed");
    }
}
