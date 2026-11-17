package dev.trajectories;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Simple Forge config wrapper. Values live in public static fields; save() writes them back. */
public final class TrajectoryConfig {

    private static final String CAT = Configuration.CATEGORY_GENERAL;
    private static Configuration config;

    public static boolean enabled = true;
    public static boolean showHud = true;
    public static boolean showBow = true;
    public static boolean showThrowables = true;
    public static boolean showWhenIdle = false;
    public static boolean throughWalls = false;
    public static boolean smoothAim = true;
    public static float lineWidth = 2.5F;
    public static int maxTicks = 300;
    public static int lineColor = 0x00E5FF;
    public static int entityColor = 0xFF3B3B;
    public static int missColor = 0xFFD54F;

    private static Property pEnabled, pHud, pBow, pThrow, pIdle, pWalls, pSmooth,
            pWidth, pTicks, pLine, pEntity, pMiss;

    private TrajectoryConfig() {}

    public static void load(File file) {
        config = new Configuration(file);

        pEnabled = config.get(CAT, "enabled", true, "Master switch.");
        pHud = config.get(CAT, "showHud", true, "Show the info text under the crosshair.");
        pBow = config.get(CAT, "showBow", true, "Show bow trajectories (scales with draw power).");
        pThrow = config.get(CAT, "showThrowables", true,
                "Show ender pearls, snowballs, eggs, splash potions and XP bottles.");
        pIdle = config.get(CAT, "showWhenIdle", false,
                "Preview a full-power bow shot while holding a bow without drawing it.");
        pWalls = config.get(CAT, "throughWalls", false, "Draw the line through blocks.");
        pSmooth = config.get(CAT, "smoothAim", true,
                "Use frame-interpolated position/rotation so the line stays glued to the crosshair. "
                        + "false = use exact tick values (what the server last saw).");
        pWidth = config.get(CAT, "lineWidth", 2.5D, "Line width in pixels (1-10).");
        pTicks = config.get(CAT, "maxSimulationTicks", 300, "Maximum ticks simulated (20-1200).");
        pLine = config.get(CAT, "lineColor", "00E5FF", "Hex RRGGBB for a path that ends on a block.");
        pEntity = config.get(CAT, "entityColor", "FF3B3B", "Hex RRGGBB for a path that ends on an entity.");
        pMiss = config.get(CAT, "missColor", "FFD54F", "Hex RRGGBB for a path that hits nothing.");

        enabled = pEnabled.getBoolean(true);
        showHud = pHud.getBoolean(true);
        showBow = pBow.getBoolean(true);
        showThrowables = pThrow.getBoolean(true);
        showWhenIdle = pIdle.getBoolean(false);
        throughWalls = pWalls.getBoolean(false);
        smoothAim = pSmooth.getBoolean(true);
        lineWidth = clamp((float) pWidth.getDouble(2.5D), 1.0F, 10.0F);
        maxTicks = Math.max(20, Math.min(1200, pTicks.getInt(300)));
        lineColor = parseColor(pLine.getString(), 0x00E5FF);
        entityColor = parseColor(pEntity.getString(), 0xFF3B3B);
        missColor = parseColor(pMiss.getString(), 0xFFD54F);

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static void save() {
        if (config == null) {
            return;
        }
        pEnabled.set(enabled);
        pHud.set(showHud);
        pBow.set(showBow);
        pThrow.set(showThrowables);
        pIdle.set(showWhenIdle);
        pWalls.set(throughWalls);
        pSmooth.set(smoothAim);
        pWidth.set((double) lineWidth);
        pTicks.set(maxTicks);
        pLine.set(hex(lineColor));
        pEntity.set(hex(entityColor));
        pMiss.set(hex(missColor));
        config.save();
    }

    public static String hex(int rgb) {
        return String.format("%06X", rgb & 0xFFFFFF);
    }

    public static int parseColor(String s, int fallback) {
        try {
            String t = s.trim();
            if (t.startsWith("#")) {
                t = t.substring(1);
            }
            return Integer.parseInt(t, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
