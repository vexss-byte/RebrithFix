package com.nebula.deathcinema;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ThreadLocalRandom;

/** Partikel, suara, dan teks galaxy (server-side). */
public final class Fx {
    // biru muda, biru tua, ungu, putih, biru langit
    static final int[] COLORS = {0x8FE6FF, 0x1E3BFF, 0xA25CFF, 0xFFFFFF, 0x5AB4FF};

    static void dust(ServerLevel l, double x, double y, double z, float size) {
        int c = COLORS[ThreadLocalRandom.current().nextInt(COLORS.length)];
        l.sendParticles(new DustParticleOptions(c, size), x, y, z, 1, 0, 0, 0, 0);
    }

    static void rod(ServerLevel l, double x, double y, double z, double vx, double vy, double vz, double speed) {
        l.sendParticles(ParticleTypes.END_ROD, x, y, z, 0, vx, vy, vz, speed);
    }

    static void sound(ServerLevel l, double x, double y, double z, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        l.playSound((Player) null, x, y, z, s, SoundSource.PLAYERS, vol, pitch);
    }

    static double[] dir(ThreadLocalRandom r) {
        double a = r.nextDouble() * Math.PI * 2, z = r.nextDouble() * 2 - 1, s = Math.sqrt(1 - z * z);
        return new double[]{s * Math.cos(a), z, s * Math.sin(a)};
    }

    static void ring(ServerLevel l, double x, double y, double z, double r, int n, double phase, float size) {
        for (int i = 0; i < n; i++) {
            double a = phase + i * Math.PI * 2 / n;
            dust(l, x + Math.cos(a) * r, y, z + Math.sin(a) * r, size);
        }
    }

    /** Ledakan dari dalam tubuh player. */
    public static void burst(ServerLevel l, double x, double y, double z) {
        var r = ThreadLocalRandom.current();
        for (int i = 0; i < 140; i++) {
            double[] d = dir(r);
            double rad = 0.2 + r.nextDouble() * 0.8;
            dust(l, x + d[0] * rad, y + d[1] * rad, z + d[2] * rad, 1.4f);
        }
        for (int i = 0; i < 90; i++) {
            double[] d = dir(r);
            rod(l, x, y, z, d[0], d[1], d[2], 0.25 + r.nextDouble() * 0.35);
        }
        sound(l, x, y, z, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.2f);
        sound(l, x, y, z, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.5f);
    }

    /** Efek di dunia nebula. wt<0 = belum mulai; wt==0 = ledakan; lalu gelombang + circular beam bertahap. */
    public static void waitTick(ServerLevel l, double x, double y, double z, int wt) {
        if (wt < 0) return;
        var r = ThreadLocalRandom.current();
        if (wt == 0) burst(l, x, y + 1.0, z);
        if (wt >= 1 && wt <= 10) { // shell ledakan mengembang
            double rad = wt * 0.55;
            for (int i = 0; i < 40; i++) {
                double[] d = dir(r);
                dust(l, x + d[0] * rad, y + 1 + d[1] * rad, z + d[2] * rad, 1.2f);
            }
        }
        // 4 tahap circular beam: muncul lalu menghilang
        for (int s = 0; s < 4; s++) {
            int p = wt - 10 - s * 25;
            if (p < 0 || p >= 40) continue;
            double f = Math.sin(Math.PI * p / 40.0);
            double rad = (0.8 + s * 0.45) * (0.3 + 0.7 * f);
            if (p == 0) sound(l, x, y, z, SoundEvents.BEACON_POWER_SELECT, 1f, 0.8f + s * 0.2f);
            ring(l, x, y + 0.1, z, rad, 30, wt * 0.15, 1.2f);
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4 + wt * 0.1;
                double h = f * 2.8 * r.nextDouble();
                rod(l, x + Math.cos(a) * rad, y + 0.1 + h, z + Math.sin(a) * rad, 0, 0.02, 0, 0.02);
                dust(l, x + Math.cos(a) * rad, y + 0.1 + h, z + Math.sin(a) * rad, 1.0f);
            }
        }
        if (wt > 150 && wt % 10 == 0) ring(l, x, y + 0.1, z, 1.2, 10, wt * 0.1, 1.0f);
        if (wt > 150 && wt % 80 == 0) sound(l, x, y, z, SoundEvents.BEACON_AMBIENT, 0.8f, 1.0f);
    }

    /** Spiral naik saat levitasi. */
    public static void spiralUp(ServerLevel l, double x, double y, double z, int t) {
        for (int strand = 0; strand < 2; strand++) {
            for (int j = 0; j < 8; j++) {
                double a = t * 0.35 + strand * Math.PI + j * 0.55;
                double rad = 1.0 + 0.2 * Math.sin(t * 0.1 + j);
                dust(l, x + Math.cos(a) * rad, y - 0.3 + j * 0.35, z + Math.sin(a) * rad, 1.3f);
            }
        }
        if (t % 3 == 0) rod(l, x, y + 0.5, z, 0, 1, 0, 0.12);
        if (t == 1) sound(l, x, y, z, SoundEvents.BEACON_AMBIENT, 1.2f, 1.0f);
    }

    /** Spiral kaya tangga memutar saat turun dari langit. */
    public static void stairs(ServerLevel l, double x, double y, double z, int t) {
        for (int j = 0; j < 26; j++) {
            double a = t * 0.22 + j * 0.5;
            double yy = y + 1.2 + j * 0.2;
            for (double rad : new double[]{0.8, 1.15, 1.5}) {
                dust(l, x + Math.cos(a) * rad, yy, z + Math.sin(a) * rad, 1.1f);
            }
            if (j % 5 == 0) rod(l, x + Math.cos(a) * 1.15, yy, z + Math.sin(a) * 1.15, 0, -0.5, 0, 0.03);
        }
        if (t % 20 == 0) sound(l, x, y, z, SoundEvents.BEACON_AMBIENT, 0.8f, 1.3f);
    }

    private static int lerp(int a, int b, double f) {
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    /** Teks gradient biru muda -> biru -> ungu ala galaxy. */
    public static MutableComponent galaxy(String s) {
        int[] stops = {0x8FE9FF, 0x5E8BFF, 0xB78CFF};
        MutableComponent out = Component.empty();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            double t = n <= 1 ? 0 : (double) i / (n - 1) * 2;
            int k = Math.min(1, (int) t);
            int c = lerp(stops[k], stops[k + 1], t - k);
            out.append(Component.literal(String.valueOf(s.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(c))));
        }
        return out;
    }

    public static String fmt(long sec) { return String.format("%d:%02d", sec / 60, sec % 60); }
}
