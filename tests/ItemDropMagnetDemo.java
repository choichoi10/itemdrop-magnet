package item;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import javax.imageio.ImageIO;
import engine.Core;
import engine.DrawManager;
import entity.Ship;
import item.ItemAPI.*;

/** Reproducible visual evidence: production movement, item drawing, ship sprite and score hook. */
public final class ItemDropMagnetDemo {
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
            manager = (ItemManager) field.get(system);
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

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected an output PNG path");
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
            File output = new File(args[0]);
            File parent = output.getAbsoluteFile().getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Cannot create output directory");
            if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("No PNG writer");
            System.out.println("ItemDropMagnetDemo: saved " + output + "; actual score conversion +" + conversion.score);
        } finally {
            for (Scene scene : scenes) scene.system.endLevel();
            ItemSystem.unequip();
            if (previous == null) System.clearProperty(ItemBalance.PATH_PROPERTY);
            else System.setProperty(ItemBalance.PATH_PROPERTY, previous);
            Files.deleteIfExists(config);
        }
    }
}
