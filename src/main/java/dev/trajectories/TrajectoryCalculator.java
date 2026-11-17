package dev.trajectories;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Re-implementation of the vanilla 1.8.9 projectile physics (EntityArrow / EntityThrowable).
 *
 * Every constant, every float/double cast and the order of operations mirror the decompiled
 * game code so the predicted path is the same path the server computes. The only thing that
 * cannot be predicted is the random launch spread (a server-side gaussian, 0.0075 per axis)
 * and entities that move after you release.
 */
public final class TrajectoryCalculator {

    private static final float PI = (float) Math.PI;
    /** Vanilla expands entity boxes by (double) 0.3F when testing a projectile segment. */
    private static final double ENTITY_MARGIN = (double) 0.3F;

    private TrajectoryCalculator() {}

    // ------------------------------------------------------------------ data

    /** Everything needed to run the simulation: spawn point, initial motion, physics constants. */
    public static final class Launch {
        final double x, y, z, mx, my, mz;
        final boolean arrow;
        final float width, height, gravity, airDrag, waterDrag;

        private Launch(double x, double y, double z, double mx, double my, double mz, boolean arrow,
                       float width, float height, float gravity, float airDrag, float waterDrag) {
            this.x = x; this.y = y; this.z = z;
            this.mx = mx; this.my = my; this.mz = mz;
            this.arrow = arrow;
            this.width = width; this.height = height;
            this.gravity = gravity; this.airDrag = airDrag; this.waterDrag = waterDrag;
        }

        public double startX() { return x; }
        public double startZ() { return z; }
        public double initialSpeed() { return Math.sqrt(mx * mx + my * my + mz * mz); }
    }

    public static final class Result {
        /** One point per tick, starting at the spawn position and ending at the impact point. */
        public final List<Vec3> points = new ArrayList<Vec3>();
        public MovingObjectPosition blockHit;
        public Entity entityHit;
        public Vec3 hitVec;
        public int ticks;
        public float impactSpeed;
        public boolean unloaded;
        public boolean timedOut;
        public boolean fellIntoVoid;

        public boolean hitSomething() {
            return blockHit != null || entityHit != null;
        }
    }

    // ------------------------------------------------------------------ launch builders

    /** Same maths as ItemBow.onPlayerStoppedUsing: ticks held -> power in [0, 1]. */
    public static float bowPower(int ticksHeld) {
        float f = (float) ticksHeld / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }
        return f;
    }

    /** Below this the bow does not fire at all. */
    public static final float MIN_BOW_POWER = 0.1F;

    /** EntityArrow(World, EntityLivingBase, float velocity) where velocity = power * 2. */
    public static Launch arrow(EntityPlayer p, float power, float partialTicks, boolean smooth) {
        double[] s = sample(p, partialTicks, smooth);
        float yaw = (float) s[3];
        float pitch = (float) s[4];
        float yr = yaw / 180.0F * PI;
        float pr = pitch / 180.0F * PI;

        double x = s[0] - (double) (MathHelper.cos(yr) * 0.16F);
        double y = s[1] + (double) p.getEyeHeight() - 0.10000000149011612D;
        double z = s[2] - (double) (MathHelper.sin(yr) * 0.16F);

        double mx = (double) (-MathHelper.sin(yr) * MathHelper.cos(pr));
        double mz = (double) (MathHelper.cos(yr) * MathHelper.cos(pr));
        double my = (double) (-MathHelper.sin(pr));

        float velocity = power * 2.0F * 1.5F;
        float len = MathHelper.sqrt_double(mx * mx + my * my + mz * mz);
        mx = mx / (double) len;
        my = my / (double) len;
        mz = mz / (double) len;
        // (random spread is added here by the game; unpredictable)
        mx = mx * (double) velocity;
        my = my * (double) velocity;
        mz = mz * (double) velocity;

        return new Launch(x, y, z, mx, my, mz, true, 0.5F, 0.5F, 0.05F, 0.99F, 0.6F);
    }

    /** EntityThrowable(World, EntityLivingBase) with the subclass' velocity/gravity/inaccuracy. */
    public static Launch throwable(EntityPlayer p, float velocity, float gravity, float pitchOffset,
                                   float partialTicks, boolean smooth) {
        double[] s = sample(p, partialTicks, smooth);
        float yaw = (float) s[3];
        float pitch = (float) s[4];
        float yr = yaw / 180.0F * PI;
        float pr = pitch / 180.0F * PI;

        double x = s[0] - (double) (MathHelper.cos(yr) * 0.16F);
        double y = s[1] + (double) p.getEyeHeight() - 0.10000000149011612D;
        double z = s[2] - (double) (MathHelper.sin(yr) * 0.16F);

        float f = 0.4F;
        double mx = (double) (-MathHelper.sin(yr) * MathHelper.cos(pr) * f);
        double mz = (double) (MathHelper.cos(yr) * MathHelper.cos(pr) * f);
        double my = (double) (-MathHelper.sin((pitch + pitchOffset) / 180.0F * PI) * f);

        float len = MathHelper.sqrt_double(mx * mx + my * my + mz * mz);
        mx = mx / (double) len;
        my = my / (double) len;
        mz = mz / (double) len;
        mx = mx * (double) velocity;
        my = my * (double) velocity;
        mz = mz * (double) velocity;

        return new Launch(x, y, z, mx, my, mz, false, 0.25F, 0.25F, gravity, 0.99F, 0.8F);
    }

    private static double[] sample(EntityPlayer p, float pt, boolean smooth) {
        if (smooth) {
            return new double[] {
                    p.lastTickPosX + (p.posX - p.lastTickPosX) * (double) pt,
                    p.lastTickPosY + (p.posY - p.lastTickPosY) * (double) pt,
                    p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * (double) pt,
                    p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt,
                    p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt
            };
        }
        return new double[] { p.posX, p.posY, p.posZ, p.rotationYaw, p.rotationPitch };
    }

    // ------------------------------------------------------------------ simulation

    /** Entities a projectile could hit (computed once per frame, not once per simulated tick). */
    public static List<Entity> collectCandidates(World world, Entity shooter, double range) {
        List<Entity> out = new ArrayList<Entity>();
        double r2 = range * range;
        for (Entity e : world.loadedEntityList) {
            if (e == null || e.isDead || !e.canBeCollidedWith()) {
                continue;
            }
            if (e.getDistanceSqToEntity(shooter) > r2) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    public static Result simulate(World world, Entity shooter, Launch l, int maxTicks, List<Entity> candidates) {
        Result r = new Result();
        double x = l.x, y = l.y, z = l.z;
        double mx = l.mx, my = l.my, mz = l.mz;
        r.points.add(new Vec3(x, y, z));

        for (int tick = 1; tick <= maxTicks; tick++) {
            if (!world.isBlockLoaded(new BlockPos(x, y, z))) {
                r.unloaded = true;
                return r;
            }
            if (y < -64.0D) {
                r.fellIntoVoid = true;
                return r;
            }

            // Entity.onEntityUpdate() runs handleWaterMovement() before the projectile moves,
            // so water/air drag is decided from the position at the START of the tick.
            boolean inWater = isInWater(world, x, y, z, l.width, l.height);

            Vec3 start = new Vec3(x, y, z);
            Vec3 end = new Vec3(x + mx, y + my, z + mz);

            // Arrow: rayTraceBlocks(a, b, false, true, false). Throwable: rayTraceBlocks(a, b) == (.., false, false, false)
            MovingObjectPosition block = world.rayTraceBlocks(start, end, false, l.arrow, false);
            Vec3 segEnd = block != null ? block.hitVec : end;

            Entity hitEntity = null;
            Vec3 hitEntityVec = null;
            double best = 0.0D;
            for (Entity e : candidates) {
                // the shooter is ignored while ticksInAir < 5
                if (e == shooter && tick < 5) {
                    continue;
                }
                AxisAlignedBB bb = e.getEntityBoundingBox().expand(ENTITY_MARGIN, ENTITY_MARGIN, ENTITY_MARGIN);
                MovingObjectPosition ic = bb.calculateIntercept(start, segEnd);
                if (ic != null) {
                    double d = start.squareDistanceTo(ic.hitVec);
                    if (d < best || best == 0.0D) {
                        hitEntity = e;
                        hitEntityVec = ic.hitVec;
                        best = d;
                    }
                }
            }

            if (hitEntity != null) {
                r.entityHit = hitEntity;
                r.hitVec = hitEntityVec;
                r.points.add(hitEntityVec);
                r.ticks = tick;
                r.impactSpeed = MathHelper.sqrt_double(mx * mx + my * my + mz * mz);
                return r;
            }
            if (block != null) {
                r.blockHit = block;
                r.hitVec = block.hitVec;
                r.points.add(block.hitVec);
                r.ticks = tick;
                r.impactSpeed = MathHelper.sqrt_double(mx * mx + my * my + mz * mz);
                return r;
            }

            // No collision: advance, then apply drag and gravity (in that order, like vanilla).
            x += mx;
            y += my;
            z += mz;
            float drag = inWater ? l.waterDrag : l.airDrag;
            mx *= (double) drag;
            my *= (double) drag;
            mz *= (double) drag;
            my -= (double) l.gravity;

            r.points.add(new Vec3(x, y, z));
            r.ticks = tick;
        }

        r.timedOut = true;
        return r;
    }

    /**
     * Mirrors Entity.handleWaterMovement() -> World.handleMaterialAcceleration(), minus the
     * flow push, using the projectile's own bounding box.
     */
    private static boolean isInWater(World world, double x, double y, double z, float width, float height) {
        float hw = width / 2.0F;
        AxisAlignedBB bb = new AxisAlignedBB(x - (double) hw, y, z - (double) hw,
                x + (double) hw, y + (double) height, z + (double) hw)
                .expand(0.0D, -0.4000000059604645D, 0.0D)
                .contract(0.001D, 0.001D, 0.001D);

        int minX = MathHelper.floor_double(bb.minX);
        int maxX = MathHelper.floor_double(bb.maxX + 1.0D);
        int minY = MathHelper.floor_double(bb.minY);
        int maxY = MathHelper.floor_double(bb.maxY + 1.0D);
        int minZ = MathHelper.floor_double(bb.minZ);
        int maxZ = MathHelper.floor_double(bb.maxZ + 1.0D);

        for (int bx = minX; bx < maxX; ++bx) {
            for (int by = minY; by < maxY; ++by) {
                for (int bz = minZ; bz < maxZ; ++bz) {
                    IBlockState state = world.getBlockState(new BlockPos(bx, by, bz));
                    Block block = state.getBlock();
                    if (block.getMaterial() == Material.water) {
                        double surface = (double) ((float) (by + 1)
                                - BlockLiquid.getLiquidHeightPercent(((Integer) state.getValue(BlockLiquid.LEVEL)).intValue()));
                        if ((double) maxY >= surface) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
