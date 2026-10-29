package com.nebula.deathcinema;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Partikel, suara, dan teks galaxy (server-side). */
public final class Fx {
    // biru muda, biru tua, ungu, putih, biru langit
    static final int[] COLORS = {0x8FE6FF, 0x1E3BFF, 0xA25CFF, 0xFFFFFF, 0x5AB4FF};

    // ---- Ledakan saat masuk dunia nebula (ubah angka di sini kalau mau lebih sedikit / lebih banyak) ----
    static final int BLAST_RODS = 44;        // bintang putih yang melesat keluar dari badan player
    static final int BLAST_CORE_DUST = 18;   // percikan warna nebula tepat di sekitar badan
    static final int BLAST_WAVE_TICKS = 7;   // berapa tick gelombang menyebar (20 tick = 1 detik)
    static final int BLAST_WAVE_DUST = 9;    // jumlah percikan gelombang per tick
    static final double BLAST_RADIUS = 2.8;  // seberapa jauh gelombang menyebar (blok)

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

    // ---- Suara sinematik ----
    private static final Map<String, SoundEvent> SOUNDS = new HashMap<>();

    /** Cari SoundEvents.NAMA lewat namanya. Aman kalau nama itu tidak ada / bertipe Holder di versi Minecraft ini: suara dilewati, tidak error. */
    static SoundEvent soundByName(String name) {
        if (SOUNDS.containsKey(name)) return SOUNDS.get(name);
        SoundEvent found = null;
        try {
            Object o = SoundEvents.class.getField(name).get(null);
            if (o instanceof Holder<?> h) o = h.value();
            if (o instanceof SoundEvent se) found = se;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // dibiarkan: found tetap null
        }
        if (found == null) System.err.println("[NebulaDeath] SoundEvents." + name + " tidak ditemukan, suara dilewati");
        SOUNDS.put(name, found);
        return found;
    }

    /** Putar suara bawaan Minecraft berdasarkan namanya di SoundEvents. */
    static void play(ServerLevel l, double x, double y, double z, String name, float vol, float pitch) {
        SoundEvent s = soundByName(name);
        if (s != null) sound(l, x, y, z, s, vol, pitch);
    }

    /** Satu suara dalam rangkaian kematian: dimainkan pada tick ke-[tick] fase DYING (1 tick = 0,05 detik). */
    record Cue(int tick, String sound, float vol, float pitch) {}

    /**
     * Rangkaian suara saat mati, mengikuti animasi kamera: jatuh tick 0-34, menyentuh tanah ~34,
     * kelopak mata menutup 40-98, lalu pindah ke dunia nebula di tick 110.
     * Kolom: tick, nama suara (SoundEvents.NAMA), volume, pitch (0.5 = berat/dalam, 2.0 = tinggi).
     */
    static final List<Cue> DYING_CUES = List.of(
            new Cue(1, "BEACON_DEACTIVATE", 1.2f, 0.70f),      // nyawa "dimatikan"
            new Cue(3, "WARDEN_HEARTBEAT", 1.0f, 1.00f),       // detak jantung 1
            new Cue(12, "PHANTOM_SWOOP", 0.7f, 0.55f),         // desir angin saat tubuh jatuh
            new Cue(15, "WARDEN_HEARTBEAT", 1.0f, 0.85f),      // detak 2, melambat
            new Cue(27, "WARDEN_HEARTBEAT", 1.0f, 0.70f),      // detak 3
            new Cue(34, "PLAYER_BIG_FALL", 1.0f, 0.60f),       // badan menghantam tanah
            new Cue(34, "WARDEN_HEARTBEAT", 1.3f, 0.55f),      // dentum berat
            new Cue(46, "ELDER_GUARDIAN_CURSE", 0.5f, 0.80f),  // rintihan saat mata menutup
            new Cue(62, "WARDEN_HEARTBEAT", 0.9f, 0.50f),      // detak terakhir
            new Cue(84, "BEACON_DEACTIVATE", 1.0f, 0.50f),     // semuanya padam
            new Cue(104, "ENDERMAN_TELEPORT", 1.0f, 0.55f));   // menghilang ke dunia nebula

    /** Dipanggil tiap tick fase DYING (tick mulai dari 1): memutar suara yang jadwalnya pas. */
    public static void dyingTick(ServerLevel l, double x, double y, double z, int tick) {
        for (Cue c : DYING_CUES) if (c.tick() == tick) play(l, x, y + 0.9, z, c.sound(), c.vol(), c.pitch());
    }

    static double[] dir(ThreadLocalRandom r) {
        double a = r.nextDouble() * Math.PI * 2, z = r.nextDouble() * 2 - 1, s = Math.sqrt(1 - z * z);
        return new double[]{s * Math.cos(a), z, s * Math.sin(a)};
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

    /** Efek di dunia nebula. wt<0 = belum mulai; wt==0 = ledakan dari badan player; lalu gelombang tipis menyebar. Setelah itu tidak ada partikel lagi. */
    public static void waitTick(ServerLevel l, double x, double y, double z, int wt) {
        if (wt < 0) return;
        double cy = y + 0.9; // tengah badan player
        if (wt == 0) blast(l, x, cy, z);
        else if (wt <= BLAST_WAVE_TICKS) blastWave(l, x, cy, z, wt);
        if (wt > 150 && wt % 80 == 0) sound(l, x, y, z, SoundEvents.BEACON_AMBIENT, 0.8f, 1.0f);
    }

    /** Ledakan kecil dari tengah badan player: bintang melesat keluar ke sekeliling, lalu pudar sendiri dalam beberapa detik. */
    static void blast(ServerLevel l, double x, double cy, double z) {
        var r = ThreadLocalRandom.current();
        for (int i = 0; i < BLAST_RODS; i++) {
            double[] d = spread(r);
            rod(l, x, cy, z, d[0], d[1], d[2], 0.14 + r.nextDouble() * 0.16);
        }
        for (int i = 0; i < BLAST_CORE_DUST; i++) {
            double[] d = spread(r);
            double rad = 0.9 + r.nextDouble() * 0.4;
            dust(l, x + d[0] * rad, cy + d[1] * rad, z + d[2] * rad, 0.9f);
        }
        sound(l, x, cy, z, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.2f);
        sound(l, x, cy, z, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.5f);
        play(l, x, cy, z, "AMETHYST_BLOCK_RESONATE", 1.2f, 0.8f);
    }

    /** Gelombang tipis yang menyebar keluar: cepat di awal, melambat di akhir (wt = 1..BLAST_WAVE_TICKS). */
    static void blastWave(ServerLevel l, double x, double cy, double z, int wt) {
        var r = ThreadLocalRandom.current();
        double f = wt / (double) BLAST_WAVE_TICKS;
        double rad = 0.9 + (BLAST_RADIUS - 0.9) * (1 - (1 - f) * (1 - f));
        for (int i = 0; i < BLAST_WAVE_DUST; i++) {
            double[] d = spread(r);
            dust(l, x + d[0] * rad, cy + d[1] * rad, z + d[2] * rad, 0.8f);
        }
    }

    /** Arah acak ke segala penjuru. Arah yang menembus lantai dibalik ke atas, dan arah tepat di atas kepala dijauhkan dari kamera. */
    static double[] spread(ThreadLocalRandom r) {
        double[] d = dir(r);
        if (d[1] < -0.35) d[1] = -d[1];
        if (d[1] > 0.8) {
            double h = Math.hypot(d[0], d[2]), k = h < 1e-6 ? 0 : 0.6 / h;
            d[0] *= k; d[2] *= k; d[1] = 0.8;
        }
        return d;
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
