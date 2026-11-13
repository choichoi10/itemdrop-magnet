import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import engine.Core;
import engine.DrawManager;
import engine.Frame;
import engine.GameSettings;
import engine.GameState;
import screen.GameScreen;
import screen.Starfield;
import screen.TitleScreen;

/** Standalone integration test; run with a display and isolated save files. */
public final class GameplayStarfieldTest {
    private static int checks;

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void call(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static float[] positions(Starfield stars) throws Exception {
        return ((float[]) field(Starfield.class, "y").get(stars)).clone();
    }

    private static void snapshot(String directory, String name) throws Exception {
        if (directory != null) ImageIO.write((BufferedImage)
                field(DrawManager.class, "backBuffer").get(null), "png", new File(directory, name));
    }

    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);
        final Frame[] window = new Frame[1];
        SwingUtilities.invokeAndWait(() -> window[0] = new Frame(448, 548));
        try {
            DrawManager dm = Core.getDrawManager();
            dm.setFrame(window[0]);
            int width = window[0].getWidth(), height = window[0].getHeight();
            String output = args.length == 0 ? null : args[0];
            TitleScreen title = new TitleScreen(width, height, 60);
            Starfield titleStars = (Starfield) field(TitleScreen.class, "starfield").get(null);
            float[] titleBefore = positions(titleStars);
            call(title, "update");
            check(!Arrays.equals(titleBefore, positions(titleStars)), "Title stars move");
            snapshot(output, "title-starfield.png");
            titleBefore = positions(titleStars);

            for (int level : new int[]{1, 5, 10}) {
                GameSettings settings = (GameSettings) field(Core.class, "SETTINGS_LEVEL_" + level).get(null);
                GameScreen game = new GameScreen(new GameState(level, 0, 3, 0, 0, 0),
                        settings, false, width, height, 60);
                game.initialize();
                Starfield stars = (Starfield) field(GameScreen.class, "starfield").get(game);
                check(stars != titleStars, "Gameplay has independent stars");
                float[] before = positions(stars);
                call(game, "update");
                check(!Arrays.equals(before, positions(stars)), "Countdown scrolls at level " + level);
                field(screen.Screen.class, "inputDelay").set(game, Core.getCooldown(0));
                before = positions(stars);
                call(game, "update");
                check(!Arrays.equals(before, positions(stars)), "Gameplay scrolls at level " + level);
                snapshot(output, "gameplay-starfield-level-" + level + ".png");
                // A quiet star pixel must survive rendering behind the action.
                BufferedImage image = (BufferedImage) field(DrawManager.class, "backBuffer").get(null);
                boolean visible = false;
                for (int i = 0; i < stars.getCount(); i++) {
                    int y = stars.getY(i);
                    if (y > height / 2 && y < height - 70
                            && image.getRGB(stars.getX(i), y) == stars.getColor(i).getRGB()) visible = true;
                }
                check(visible, "Stars are visible in rendered gameplay");
                field(GameScreen.class, "paused").setBoolean(game, true);
                field(GameScreen.class, "pauseStartedAt").setLong(game, System.currentTimeMillis());
                before = positions(stars);
                call(game, "update");
                check(Arrays.equals(before, positions(stars)), "Pause freezes stars");
                call(game, "resumeGameplay");
                call(game, "update");
                check(!Arrays.equals(before, positions(stars)), "Resume moves stars");
                call(game, "restartLevel");
                check(field(GameScreen.class, "starfield").get(game) == stars, "Restart retains background");
                before = positions(stars);
                call(game, "update");
                check(!Arrays.equals(before, positions(stars)), "Restart keeps scrolling");
                engine.GameEvents.unsubscribe(engine.GameEvents.Type.PLAYER_HIT,
                        (engine.DamageDimEffect) field(GameScreen.class, "damageDim").get(game));
                engine.GameEvents.unsubscribe(engine.GameEvents.Type.PLAYER_HIT,
                        (engine.LightningChain) field(GameScreen.class, "lightningChain").get(game));
            }
            check(Arrays.equals(titleBefore, positions(titleStars)), "Gameplay never advances title stars");
            TitleScreen returned = new TitleScreen(width, height, 60);
            check(field(TitleScreen.class, "starfield").get(null) == titleStars, "Title retains background on return");
            call(returned, "update");
            check(!Arrays.equals(titleBefore, positions(titleStars)), "Returned title still scrolls");
            // Exercise wraparound over many frames, independently of rendering.
            for (int n = 0; n < 10000; n++) {
                titleStars.update();
                for (int i = 0; i < titleStars.getCount(); i++)
                    if (titleStars.getY(i) < 0 || titleStars.getY(i) >= height)
                        throw new AssertionError("Star escaped vertical bounds");
            }
            check(true, "Wraparound remains in bounds");
            System.out.println("GameplayStarfieldTest: " + checks + " checks passed");
        } finally {
            SwingUtilities.invokeAndWait(() -> window[0].dispose());
        }
    }
}
