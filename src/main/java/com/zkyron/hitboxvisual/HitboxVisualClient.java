package com.zkyron.hitboxvisual;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class HitboxVisualClient implements ClientModInitializer {

    static final double REACH = 3.0;
    static final double MAX_DISTANCE = 40.0;
    static final int THICKNESS = 1;
    static final int COLOR_NORMAL = 0xFFFFFFFF;
    static final int COLOR_IN_REACH = 0xFFFF2020;
    static final boolean ONLY_VISIBLE = true;

    private static final double NEAR = 0.05;

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(HitboxVisualClient::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity self = mc.player;
        if (self == null || mc.world == null) return;
        if (mc.options.hudHidden || mc.currentScreen != null) return;
        if (!mc.options.getPerspective().isFirstPerson()) return;

        float td = tickCounter.getTickProgress(true);

        Vec3d eye = self.getCameraPosVec(td);
        double yaw = Math.toRadians(self.getYaw(td));
        double pitch = Math.toRadians(self.getPitch(td));

        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double cy = Math.cos(yaw), sy = Math.sin(yaw);

        double fx = -sy * cp, fy = -sp, fz = cy * cp;
        double rx = -cy, ry = 0, rz = -sy;
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        double aspect = (double) w / h;
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);

        for (AbstractClientPlayerEntity other : mc.world.getPlayers()) {
            if (other == self || other.isSpectator() || other.isRemoved()) continue;
            if (ONLY_VISIBLE && !self.canSee(other)) continue;

            Vec3d pos = other.getLerpedPos(td);
            if (pos.distanceTo(eye) > MAX_DISTANCE) continue;

            Box box = other.getDimensions(other.getPose()).getBoxAt(pos);

            double dx = Math.max(Math.max(box.minX - eye.x, 0), eye.x - box.maxX);
            double dy = Math.max(Math.max(box.minY - eye.y, 0), eye.y - box.maxY);
            double dz = Math.max(Math.max(box.minZ - eye.z, 0), eye.z - box.maxZ);
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int color = dist <= REACH ? COLOR_IN_REACH : COLOR_NORMAL;

            double[][] c = new double[8][3];
            for (int i = 0; i < 8; i++) {
                double px = (i & 1) == 0 ? box.minX : box.maxX;
                double py = (i & 2) == 0 ? box.minY : box.maxY;
                double pz = (i & 4) == 0 ? box.minZ : box.maxZ;
                double vx = px - eye.x, vy = py - eye.y, vz = pz - eye.z;
                c[i][0] = vx * rx + vy * ry + vz * rz;
                c[i][1] = vx * ux + vy * uy + vz * uz;
                c[i][2] = vx * fx + vy * fy + vz * fz;
            }

            for (int i = 0; i < 8; i++) {
                for (int b = 1; b <= 4; b <<= 1) {
                    if ((i & b) != 0) continue;
                    drawEdge(ctx, c[i], c[i | b], w, h, aspect, tanHalf, color);
                }
            }
        }
    }

    private static void drawEdge(DrawContext ctx, double[] a, double[] b,
                                 int w, int h, double aspect, double tanHalf, int color) {
        double ax = a[0], ay = a[1], az = a[2];
        double bx = b[0], by = b[1], bz = b[2];

        if (az < NEAR && bz < NEAR) return;
        if (az < NEAR) {
            double t = (NEAR - az) / (bz - az);
            ax += (bx - ax) * t; ay += (by - ay) * t; az = NEAR;
        } else if (bz < NEAR) {
            double t = (NEAR - bz) / (az - bz);
            bx += (ax - bx) * t; by += (ay - by) * t; bz = NEAR;
        }

        double sx1 = w / 2.0 * (1 + (ax / (az * tanHalf * aspect)));
        double sy1 = h / 2.0 * (1 - (ay / (az * tanHalf)));
        double sx2 = w / 2.0 * (1 + (bx / (bz * tanHalf * aspect)));
        double sy2 = h / 2.0 * (1 - (by / (bz * tanHalf)));

        double ddx = sx2 - sx1, ddy = sy2 - sy1;
        int steps = (int) Math.min(3000, Math.ceil(Math.max(Math.abs(ddx), Math.abs(ddy))));
        if (steps <= 0) return;

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.round(sx1 + ddx * t);
            int y = (int) Math.round(sy1 + ddy * t);
            if (x < -THICKNESS || y < -THICKNESS || x > w || y > h) continue;
            ctx.fill(x, y, x + THICKNESS, y + THICKNESS, color);
        }
    }
          }
