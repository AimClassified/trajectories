package dev.trajectories;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
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

    private void drawScene(TrajectoryCalculator.Result r, double vx, double vy, double vz, int rgb) {
        int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        if (TrajectoryConfig.throughWalls) {
            GlStateManager.disableDepth();
        }
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(TrajectoryConfig.lineWidth);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        // the path: fades in slightly from the shooter so it never clutters the screen centre
        wr.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int n = r.points.size();
        for (int i = 0; i < n; i++) {
            Vec3 p = r.points.get(i);
            int a = i < 3 ? 70 + i * 50 : 230;
            wr.pos(p.xCoord - vx, p.yCoord - vy, p.zCoord - vz).color(cr, cg, cb, a).endVertex();
        }
        tess.draw();

        if (r.blockHit != null && r.blockHit.sideHit != null) {
            drawFaceMarker(tess, wr, r.hitVec, r.blockHit.sideHit, vx, vy, vz, cr, cg, cb);
        } else if (r.entityHit != null) {
            AxisAlignedBB box = r.entityHit.getEntityBoundingBox().expand(0.002D, 0.002D, 0.002D)
                    .offset(-vx, -vy, -vz);
            GL11.glLineWidth(2.0F);
            RenderGlobal.drawOutlinedBoundingBox(box, cr, cg, cb, 255);
        } else if (r.hitVec == null && n > 0) {
            // no impact: small cross where the simulation stopped
            Vec3 p = r.points.get(n - 1);
            drawCross(tess, wr, p, vx, vy, vz, cr, cg, cb);
        }

        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        if (TrajectoryConfig.throughWalls) {
            GlStateManager.enableDepth();
        }
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void drawFaceMarker(Tessellator tess, WorldRenderer wr, Vec3 hit, EnumFacing face,
                                double vx, double vy, double vz, int r, int g, int b) {
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

        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (double[] p : c) {
            wr.pos(p[0], p[1], p[2]).color(r, g, b, 70).endVertex();
        }
        tess.draw();

        wr.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        for (double[] p : c) {
            wr.pos(p[0], p[1], p[2]).color(r, g, b, 255).endVertex();
        }
        tess.draw();
    }

    private void drawCross(Tessellator tess, WorldRenderer wr, Vec3 p, double vx, double vy, double vz,
                           int r, int g, int b) {
        double x = p.xCoord - vx, y = p.yCoord - vy, z = p.zCoord - vz, s = 0.25D;
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x - s, y, z).color(r, g, b, 255).endVertex();
        wr.pos(x + s, y, z).color(r, g, b, 255).endVertex();
        wr.pos(x, y - s, z).color(r, g, b, 255).endVertex();
        wr.pos(x, y + s, z).color(r, g, b, 255).endVertex();
        wr.pos(x, y, z - s).color(r, g, b, 255).endVertex();
        wr.pos(x, y, z + s).color(r, g, b, 255).endVertex();
        tess.draw();
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
