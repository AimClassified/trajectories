package dev.trajectories;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemExpBottle;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class TrajectoryRenderer {

    private final Minecraft mc = Minecraft.getMinecraft();
    private List<String> hud = null;

    // ------------------------------------------------------------------ world

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        hud = null;
        if (!TrajectoryConfig.enabled) {
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || player.isSpectator()) {
            return;
        }
        ItemStack held = player.getHeldItem();
        if (held == null) {
            return;
        }

        Item item = held.getItem();
        float pt = event.partialTicks;
        boolean smooth = TrajectoryConfig.smoothAim;

        TrajectoryCalculator.Launch launch = null;
        String label = null;
        float power = -1.0F;
        boolean isBow = false;

        if (item instanceof ItemBow) {
            if (!TrajectoryConfig.showBow) {
                return;
            }
            isBow = true;
            label = "Bow";
            if (player.isUsingItem() && player.getItemInUse() != null && player.getItemInUse().getItem() instanceof ItemBow) {
                power = TrajectoryCalculator.bowPower(player.getItemInUseDuration());
            } else if (TrajectoryConfig.showWhenIdle && hasArrows(player, held)) {
                power = 1.0F;
            } else {
                return;
            }
            if (power < TrajectoryCalculator.MIN_BOW_POWER) {
                hud = new ArrayList<String>();
                hud.add("\u00a7bBow \u00a7cdraw further");
                return;
            }
            launch = TrajectoryCalculator.arrow(player, power, pt, smooth);
        } else if (TrajectoryConfig.showThrowables) {
            if (item instanceof ItemEnderPearl) {
                label = "Ender Pearl";
                launch = TrajectoryCalculator.throwable(player, 1.5F, 0.03F, 0.0F, pt, smooth);
            } else if (item instanceof ItemSnowball) {
                label = "Snowball";
                launch = TrajectoryCalculator.throwable(player, 1.5F, 0.03F, 0.0F, pt, smooth);
            } else if (item instanceof ItemEgg) {
                label = "Egg";
                launch = TrajectoryCalculator.throwable(player, 1.5F, 0.03F, 0.0F, pt, smooth);
            } else if (item instanceof ItemPotion && ItemPotion.isSplash(held.getMetadata())) {
                label = "Splash Potion";
                launch = TrajectoryCalculator.throwable(player, 0.5F, 0.05F, -20.0F, pt, smooth);
            } else if (item instanceof ItemExpBottle) {
                label = "XP Bottle";
                launch = TrajectoryCalculator.throwable(player, 0.7F, 0.07F, -20.0F, pt, smooth);
            }
        }
        if (launch == null) {
            return;
        }

        List<Entity> candidates = TrajectoryCalculator.collectCandidates(mc.theWorld, player, 160.0D);
        TrajectoryCalculator.Result r = TrajectoryCalculator.simulate(
                mc.theWorld, player, launch, TrajectoryConfig.maxTicks, candidates);

        // camera position for this frame
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            view = player;
        }
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * (double) pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * (double) pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * (double) pt;

        int color = r.entityHit != null ? TrajectoryConfig.entityColor
                : (r.blockHit != null ? TrajectoryConfig.lineColor : TrajectoryConfig.missColor);

        drawScene(r, vx, vy, vz, color);
        buildHud(r, launch, label, power, isBow, held);
    }

    private static boolean hasArrows(EntityPlayerSP p, ItemStack bow) {
        return p.capabilities.isCreativeMode
                || p.inventory.hasItem(Items.arrow)
                || EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, bow) > 0;
    }

    // ------------------------------------------------------------------ drawing
    // Plain immediate-mode OpenGL with every relevant state forced, so nothing the game left
    // enabled (textures, lightmap, fog, alpha test, lighting) can hide or tint the line.

    private void drawScene(TrajectoryCalculator.Result r, double vx, double vy, double vz, int rgb) {
        float cr = ((rgb >> 16) & 255) / 255.0F;
        float cg = ((rgb >> 8) & 255) / 255.0F;
        float cb = (rgb & 255) / 255.0F;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        // texturing off on BOTH texture units (lightmap + block atlas)
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        boolean lightmapOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        boolean texOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(TrajectoryConfig.lineWidth);

        if (TrajectoryConfig.throughWalls) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            drawPath(r, vx, vy, vz, cr, cg, cb, 1.0F);
        } else {
            // faint pass that ignores depth (so you always see the arc), then the solid depth-tested pass
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            drawPath(r, vx, vy, vz, cr, cg, cb, 0.30F);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            drawPath(r, vx, vy, vz, cr, cg, cb, 1.0F);
        }

        if (TrajectoryConfig.throughWalls) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }

        if (r.blockHit != null && r.blockHit.sideHit != null) {
            drawFaceMarker(r.hitVec, r.blockHit.sideHit, vx, vy, vz, cr, cg, cb);
        } else if (r.entityHit != null) {
            GL11.glLineWidth(2.0F);
            drawBox(r.entityHit.getEntityBoundingBox().expand(0.002D, 0.002D, 0.002D), vx, vy, vz, cr, cg, cb);
        } else if (!r.hitSomething() && !r.points.isEmpty()) {
            drawCross(r.points.get(r.points.size() - 1), vx, vy, vz, cr, cg, cb);
        }

        GL11.glPopMatrix();
        GL11.glPopAttrib();

        // put texturing back exactly as we found it
        if (texOn) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        if (lightmapOn) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawPath(TrajectoryCalculator.Result r, double vx, double vy, double vz,
                          float cr, float cg, float cb, float alphaScale) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        int n = r.points.size();
        for (int i = 0; i < n; i++) {
            Vec3 p = r.points.get(i);
            float a = (i < 3 ? (70 + i * 50) / 255.0F : 0.95F) * alphaScale;
            GL11.glColor4f(cr, cg, cb, a);
            GL11.glVertex3d(p.xCoord - vx, p.yCoord - vy, p.zCoord - vz);
        }
        GL11.glEnd();
    }

    private void drawFaceMarker(Vec3 hit, EnumFacing face, double vx, double vy, double vz,
                                float r, float g, float b) {
        double s = 0.3D;
        double ox = hit.xCoord - vx + face.getFrontOffsetX() * 0.004D;
        double oy = hit.yCoord - vy + face.getFrontOffsetY() * 0.004D;
        double oz = hit.zCoord - vz + face.getFrontOffsetZ() * 0.004D;

        double[][] c;
        switch (face.getAxis()) {
            case X:
                c = new double[][] { {ox, oy - s, oz - s}, {ox, oy - s, oz + s}, {ox, oy + s, oz + s}, {ox, oy + s, oz - s} };
                break;
            case Y:
                c = new double[][] { {ox - s, oy, oz - s}, {ox - s, oy, oz + s}, {ox + s, oy, oz + s}, {ox + s, oy, oz - s} };
                break;
            default:
                c = new double[][] { {ox - s, oy - s, oz}, {ox + s, oy - s, oz}, {ox + s, oy + s, oz}, {ox - s, oy + s, oz} };
                break;
        }

        GL11.glColor4f(r, g, b, 0.28F);
        GL11.glBegin(GL11.GL_QUADS);
        for (double[] p : c) {
            GL11.glVertex3d(p[0], p[1], p[2]);
        }
        GL11.glEnd();

        GL11.glColor4f(r, g, b, 1.0F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (double[] p : c) {
            GL11.glVertex3d(p[0], p[1], p[2]);
        }
        GL11.glEnd();
    }

    private void drawCross(Vec3 p, double vx, double vy, double vz, float r, float g, float b) {
        double x = p.xCoord - vx, y = p.yCoord - vy, z = p.zCoord - vz, s = 0.25D;
        GL11.glColor4f(r, g, b, 1.0F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x - s, y, z);
        GL11.glVertex3d(x + s, y, z);
        GL11.glVertex3d(x, y - s, z);
        GL11.glVertex3d(x, y + s, z);
        GL11.glVertex3d(x, y, z - s);
        GL11.glVertex3d(x, y, z + s);
        GL11.glEnd();
    }

    private void drawBox(AxisAlignedBB bb, double vx, double vy, double vz, float r, float g, float b) {
        double x1 = bb.minX - vx, y1 = bb.minY - vy, z1 = bb.minZ - vz;
        double x2 = bb.maxX - vx, y2 = bb.maxY - vy, z2 = bb.maxZ - vz;
        GL11.glColor4f(r, g, b, 1.0F);
        GL11.glBegin(GL11.GL_LINES);
        // bottom
        line(x1, y1, z1, x2, y1, z1); line(x2, y1, z1, x2, y1, z2);
        line(x2, y1, z2, x1, y1, z2); line(x1, y1, z2, x1, y1, z1);
        // top
        line(x1, y2, z1, x2, y2, z1); line(x2, y2, z1, x2, y2, z2);
        line(x2, y2, z2, x1, y2, z2); line(x1, y2, z2, x1, y2, z1);
        // pillars
        line(x1, y1, z1, x1, y2, z1); line(x2, y1, z1, x2, y2, z1);
        line(x2, y1, z2, x2, y2, z2); line(x1, y1, z2, x1, y2, z2);
        GL11.glEnd();
    }

    private static void line(double ax, double ay, double az, double bx, double by, double bz) {
        GL11.glVertex3d(ax, ay, az);
        GL11.glVertex3d(bx, by, bz);
    }

    // ------------------------------------------------------------------ HUD

    private void buildHud(TrajectoryCalculator.Result r, TrajectoryCalculator.Launch l, String label,
                          float power, boolean isBow, ItemStack held) {
        List<String> lines = new ArrayList<String>();

        StringBuilder head = new StringBuilder("\u00a7b").append(label);
        if (isBow) {
            head.append(" \u00a7f").append(Math.round(power * 100.0F)).append("%");
            if (power >= 1.0F) {
                head.append(" \u00a76CRIT");
            }
        }
        lines.add(head.toString());

        double speed = l.initialSpeed();
        lines.add(String.format("\u00a77Speed \u00a7f%.2f b/t  \u00a77Flight \u00a7f%.2fs", speed, r.ticks / 20.0D));

        Vec3 last = r.points.get(r.points.size() - 1);
        double dx = last.xCoord - l.startX();
        double dz = last.zCoord - l.startZ();
        lines.add(String.format("\u00a77Range \u00a7f%.1f m", Math.sqrt(dx * dx + dz * dz)));

        if (isBow && r.hitSomething()) {
            int lvl = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, held);
            double dmg = 2.0D;
            if (lvl > 0) {
                dmg += (double) lvl * 0.5D + 0.5D;
            }
            int base = MathHelper.ceiling_double_int((double) r.impactSpeed * dmg);
            int max = power >= 1.0F ? base + base / 2 + 1 : base;
            String num = base == max ? String.valueOf(base) : base + "-" + max;
            String hearts = base == max ? trim(base / 2.0D) : trim(base / 2.0D) + "-" + trim(max / 2.0D);
            lines.add("\u00a77Damage \u00a7f" + num + " \u00a7c\u2665" + hearts + " \u00a78(before armor)");
        }

        if (r.entityHit != null) {
            lines.add("\u00a7cHit: " + r.entityHit.getName());
        } else if (r.blockHit != null) {
            BlockPos bp = r.blockHit.getBlockPos();
            String name = bp != null ? mc.theWorld.getBlockState(bp).getBlock().getLocalizedName() : "block";
            lines.add("\u00a7aHit: " + name);
        } else if (r.unloaded) {
            lines.add("\u00a7eLeaves loaded area");
        } else if (r.fellIntoVoid) {
            lines.add("\u00a7eFalls into the void");
        } else {
            lines.add("\u00a7eNo impact within " + r.ticks + " ticks");
        }
        hud = lines;
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        if (hud == null || !TrajectoryConfig.showHud || mc.gameSettings.showDebugInfo) {
            return;
        }
        FontRenderer fr = mc.fontRendererObj;
        ScaledResolution sr = event.resolution;
        int cx = sr.getScaledWidth() / 2;
        int y = sr.getScaledHeight() / 2 + 16;
        for (String s : hud) {
            fr.drawStringWithShadow(s, (float) (cx - fr.getStringWidth(s) / 2), (float) y, 0xFFFFFF);
            y += 10;
        }
    }
}
