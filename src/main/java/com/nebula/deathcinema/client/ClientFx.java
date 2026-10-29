package com.nebula.deathcinema.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Semua logika kamera + overlay (kedip, haze, putih) di sisi client. */
public final class ClientFx {
    /** Ganti ke -1 kalau kamera jatuh ke KIRI, bukan kanan. */
    static final double ROLL_DIR = 1.0;

    static int mode = 0;
    static long start;
    static boolean captured;
    static double yaw0, pitch0;
    static CameraType saved;
    static boolean overridden;

    static final double[][] DEATH_KF = {{0, 0}, {40, 0}, {47, .6}, {53, .15}, {62, .85}, {68, .3}, {80, .95}, {86, .5}, {98, 1}};
    static final double[][] WAKE_KF = {{0, 1}, {8, .8}, {16, .95}, {26, .5}, {34, .85}, {46, .3}, {56, .55}, {70, 0}};
    static final double[][] BLINK_KF = {{0, 0}, {7, .6}, {13, .15}, {22, .85}, {28, .3}, {40, .95}, {46, .5}, {56, 1}, {66, 1},
            {74, .8}, {82, .95}, {92, .5}, {100, .85}, {110, .3}, {118, .55}, {125, 0}};

    public static void start(int id) {
        mode = id;
        start = System.nanoTime();
        captured = false;
        applyCamType();
    }

    /** Selama orbit: paksa kamera TPP supaya tangan player tidak dirender; kembalikan setelah selesai. */
    static void applyCamType() {
        boolean want = mode == 3 || mode == 4 || mode == 6;
        var o = Minecraft.getInstance().options;
        if (want && !overridden) {
            saved = o.getCameraType();
            o.setCameraType(CameraType.THIRD_PERSON_BACK);
            overridden = true;
        } else if (!want && overridden) {
            o.setCameraType(saved);
            overridden = false;
        }
    }

    /** True selama animasi apa pun berjalan: input mouse untuk menoleh diblokir. */
    public static boolean locked() {
        update();
        return mode != 0;
    }

    static double t() { return (System.nanoTime() - start) / 50_000_000.0; }
    static double clamp(double x) { return x < 0 ? 0 : Math.min(1, x); }
    static double smooth(double x) { x = clamp(x); return x * x * (3 - 2 * x); }
    static double ease(double x) { x = clamp(x); return x < .5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2; }
    static double lerp(double a, double b, double f) { return a + (b - a) * f; }

    static double kf(double[][] k, double t) {
        if (t <= k[0][0]) return k[0][1];
        for (int i = 1; i < k.length; i++) {
            if (t <= k[i][0]) {
                double f = (t - k[i - 1][0]) / (k[i][0] - k[i - 1][0]);
                return lerp(k[i - 1][1], k[i][1], smooth(f));
            }
        }
        return k[k.length - 1][1];
    }

    static void update() {
        if (mode == 0) return;
        double t = t();
        boolean end = switch (mode) {
            case 1 -> t > 600;
            case 2 -> t > 76;
            case 3 -> t > 400;
            case 4 -> t > 101;
            case 5 -> t > 126;
            case 6 -> t > 151;
            default -> true;
        };
        if (end) mode = 0;
        applyCamType();
    }

    /** @return {x,y,z,yaw,pitch,rollDeg,detached(0/1)} atau null kalau kamera normal. */
    public static double[] camera(float pt) {
        update();
        if (!(mode == 1 || mode == 3 || mode == 4 || mode == 6)) return null;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return null;
        if (!captured) { yaw0 = p.getYRot(); pitch0 = p.getXRot(); captured = true; }
        double t = t();
        Vec3 eye = p.getEyePosition(pt);

        if (mode == 1) { // POV jatuh ke kanan
            double e = ease(t / 40.0);
            return new double[]{eye.x, eye.y - 1.15 * e, eye.z, yaw0, pitch0 + 14 * e, ROLL_DIR * 85 * e, 0};
        }

        double theta, R;
        switch (mode) {
            case 3 -> { theta = 360 * ease(t / 150.0); R = 4.2; }
            case 6 -> { theta = 360 * ease(t / 100.0); R = 4.5; }
            default -> { theta = 360 * smooth(t / 100.0); R = 5.0; }
        }
        Vec3 c = p.getPosition(pt).add(0, 1.0, 0);
        double ang = Math.toRadians(yaw0 + theta);
        double hOff = 0.7 + 0.5 * Math.sin(Math.toRadians(theta));
        double cx = c.x + Math.sin(ang) * R, cy = c.y + hOff, cz = c.z - Math.cos(ang) * R;
        double yaw = yaw0 + theta;
        double hz = Math.hypot(c.x - cx, c.z - cz);
        double pitch = Math.toDegrees(-Math.atan2(c.y - cy, hz));

        if (mode == 4) { // akhir turun: kamera menyatu kembali ke POV
            double k = smooth((t - 88) / 12.0);
            cx = lerp(cx, eye.x, k); cy = lerp(cy, eye.y, k); cz = lerp(cz, eye.z, k);
            yaw = lerp(yaw, yaw0 + 360, k);
            pitch = lerp(pitch, p.getViewXRot(pt), k);
        }
        double detached = (mode == 4 && t > 96) ? 0 : 1; // tubuh asli player terlihat (TPP)
        return new double[]{cx, cy, cz, yaw, pitch, 0, detached};
    }

    /** @return {radius 1..10, jumlah pass} untuk blur shader asli, atau null kalau tidak blur. */
    public static int[] blur() {
        update();
        if (!(mode == 3 || mode == 4 || mode == 6)) return null;
        double t = t(), amt;
        switch (mode) {
            case 3 -> amt = smooth((t - 60) / 60.0);            // blur makin kuat perlahan
            case 4 -> amt = 1 - smooth(t / 45.0);               // blur memudar saat turun
            default -> amt = smooth((t - 40) / 60.0) * (1 - smooth((t - 110) / 35.0));
        }
        int radius = (int) Math.round(amt * 10);
        if (radius < 1) return null;
        int passes = 1 + (amt > 0.6 ? 1 : 0) + (amt > 0.9 ? 1 : 0);
        return new int[]{radius, passes};
    }

    // ---------- tangan first-person turun perlahan saat jatuh ----------
    /** Seberapa jauh tangan "diayun" ke bawah (derajat x 10): 700 = tangan berputar ±70° ke bawah, cukup untuk keluar dari layar. Perbesar kalau FOV kamu sangat lebar. */
    static final double HAND_SWAY = 700;

    static Field xBobF, xBobOF;
    static boolean handInit, handOk, madeInvisible;
    static float lastSway = Float.NaN;

    /** 0 = tangan normal, 1 = tangan sudah turun sepenuhnya keluar layar. */
    static double handDrop() {
        double t = t();
        return switch (mode) {
            case 1 -> smooth((t - 2) / 34.0);        // mulai turun begitu mau jatuh, hilang total sebelum badan menyentuh tanah (~tick 36)
            case 2 -> 1 - smooth((t - 44) / 30.0);   // setelah bangun di dunia nebula, tangan naik lagi pelan-pelan
            default -> 0.0;
        };
    }

    /** Cari field float (termasuk di superclass) lewat namanya, atau null kalau tidak ada. */
    static Field floatField(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                if (f.getType() != float.class) return null;
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // coba superclass
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Dipanggil tiap tick client. Tangan diturunkan lewat "ayunan lengan" bawaan Minecraft
     * (xBob/xBobO yang dipakai ItemInHandRenderer: tangan berputar (pitch - xBob) * 0.1 derajat).
     * Memakai refleksi supaya build tidak error kalau nama field berbeda di versi Minecraft lain.
     */
    public static void handTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) { lastSway = Float.NaN; madeInvisible = false; return; }
        update();
        double h = handDrop();
        if (h <= 0.0005) {                       // tidak sedang animasi: serahkan ke Minecraft
            lastSway = Float.NaN;
            if (madeInvisible) { p.setInvisible(false); madeInvisible = false; }
            return;
        }
        if (!handInit) {
            handInit = true;
            xBobF = floatField(LocalPlayer.class, "xBob");
            xBobOF = floatField(LocalPlayer.class, "xBobO");
            handOk = xBobF != null;
            if (!handOk) System.err.println("[NebulaDeath] field xBob tidak ditemukan: tangan hanya disembunyikan, tanpa animasi turun");
        }
        if (handOk) {
            try {
                float target = (float) (p.getXRot() + HAND_SWAY * h);
                float prev = Float.isNaN(lastSway) ? xBobF.getFloat(p) : lastSway;
                if (xBobOF != null) xBobOF.setFloat(p, prev);
                xBobF.setFloat(p, target);
                lastSway = target;
            } catch (ReflectiveOperationException | RuntimeException e) {
                handOk = false;
            }
        }
        // Pengaman: begitu tangan turun penuh, lengan disembunyikan di sisi client saja (player lain tidak terpengaruh)
        if (h >= 0.999) {
            if (!p.isInvisible()) { p.setInvisible(true); madeInvisible = true; }
        } else if (madeInvisible) {
            p.setInvisible(false);
            madeInvisible = false;
        }
    }

    static int argb(double a, int rgb) { return ((int) (clamp(a) * 255) << 24) | rgb; }

    public static List<int[]> overlay(int W, int H) {
        update();
        List<int[]> r = new ArrayList<>();
        if (mode == 0) return r;
        double t = t();
        double lid = 0, haze = 0, white = 0;
        switch (mode) {
            case 1 -> lid = kf(DEATH_KF, t);
            case 2 -> lid = kf(WAKE_KF, t);
            case 5 -> lid = kf(BLINK_KF, t);
            case 3 -> white = smooth((t - 105) / 45.0);
            case 4 -> white = 1 - smooth(t / 30.0);
            default -> {}
        }

        if (lid > 0.001) { // kelopak mata
            int bar = (int) (H / 2.0 * lid);
            r.add(new int[]{0, 0, W, bar, argb(1, 0)});
            r.add(new int[]{0, H - bar, W, H, argb(1, 0)});
            if (lid < 1) {
                for (int i = 0; i < 10; i++) { // pinggir kelopak lembut
                    double a = (1 - i / 10.0) * 0.6;
                    r.add(new int[]{0, bar + i * 3, W, bar + i * 3 + 3, argb(a, 0)});
                    r.add(new int[]{0, H - bar - i * 3 - 3, W, H - bar - i * 3, argb(a, 0)});
                }
            }
            r.add(new int[]{0, 0, W, H, argb(lid * lid * 0.5, 0)});
        }

        if (haze > 0.001) { // kabut putih dari tepi layar (efek buram)
            int step = (int) (Math.min(W, H) * 0.035);
            for (int i = 0; i < 14; i++) {
                int in = i * step;
                int col = argb(haze * 0.10 * (1 - i / 14.0), 0xFFFFFF);
                r.add(new int[]{in, in, W - in, in + step, col});
                r.add(new int[]{in, H - in - step, W - in, H - in, col});
                r.add(new int[]{in, in + step, in + step, H - in - step, col});
                r.add(new int[]{W - in - step, in + step, W - in, H - in - step, col});
            }
            r.add(new int[]{0, 0, W, H, argb(haze * 0.18, 0xFFFFFF)});
        }

        if (white > 0.001) r.add(new int[]{0, 0, W, H, argb(white, 0xFFFFFF)});
        return r;
    }
}
