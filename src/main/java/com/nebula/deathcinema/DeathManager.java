package com.nebula.deathcinema;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class DeathManager {
    enum Phase { DYING, WAIT, REVIVE, DESCENT }

    static final class Session {
        Phase phase = Phase.DYING;
        int tick = 0;
        GameType prev = GameType.SURVIVAL;
        boolean skipWait = false;
        ServerBossEvent bar;
        int waitTotal, waitLeft;
        ServerLevel land;
        double gx, gy, gz, sky;
        float gyaw, gpitch;
        boolean resist;
    }

    /** fall=true: tes /tesjatuh (suara kematian); fall=false: tes /tespartikel (ledakan nebula). */
    record TestShow(ServerLevel l, double x, double y, double z, int[] t, boolean fall) {}

    public static MinecraftServer server;
    static final Map<UUID, Session> S = new HashMap<>();
    static final Set<UUID> pending = new HashSet<>();
    static final Map<UUID, TestShow> tests = new HashMap<>();

    static final int DYING_TICKS = 110, WAKE_TICKS = 80, REVIVE_TICKS = 150, DESCENT_TICKS = 100;

    public static void send(ServerPlayer p, int id) {
        if (ServerPlayNetworking.canSend(p, FxPayload.TYPE)) ServerPlayNetworking.send(p, new FxPayload(id));
    }

    /** Invulnerable lewat abilities (setInvulnerable tidak ada lagi di ServerPlayer). */
    static void invul(ServerPlayer p, boolean b) {
        if (p.getAbilities().invulnerable != b) {
            p.getAbilities().invulnerable = b;
            p.onUpdateAbilities();
        }
    }

    static void broadcast(Component c) { server.getPlayerList().broadcastSystemMessage(c, false); }

    /** Player tidak boleh memungut item selama animasi mati / revive / turun. */
    public static boolean blocksPickup(ServerPlayer p) {
        Session s = S.get(p.getUUID());
        return s != null && s.phase != Phase.WAIT;
    }

    public static boolean busy(ServerPlayer p) { return S.containsKey(p.getUUID()); }

    // ---------- kematian ----------
    public static void onDeath(ServerPlayer p) {
        if (S.containsKey(p.getUUID())) { heal(p); return; }
        begin(p);
    }

    static void heal(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.getFoodData().setFoodLevel(20);
        p.resetFallDistance();
    }

    public static void begin(ServerPlayer p) {
        if (S.containsKey(p.getUUID())) return;
        Session s = new Session();
        s.prev = p.gameMode.getGameModeForPlayer();
        p.getInventory().dropAll();           // semua barang drop
        p.removeAllEffects();
        heal(p);
        invul(p, true);
        p.setNoGravity(true);
        p.setDeltaMovement(Vec3.ZERO);
        S.put(p.getUUID(), s);
        send(p, 1);
    }

    static ServerLevel findLevel(String dim) {
        for (ServerLevel l : server.getAllLevels()) if (l.dimension().toString().equals(dim)) return l;
        return server.overworld();
    }

    static void freeze(ServerPlayer p, Session s, boolean zeroY) {
        if (s.tick % 4 == 1) p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 9, false, false, false));
        Vec3 m = p.getDeltaMovement();
        p.setDeltaMovement(0, zeroY ? 0 : m.y, 0);
    }

    // ---------- tick ----------
    public static void tick(MinecraftServer srv) {
        for (Map.Entry<UUID, Session> e : new ArrayList<>(S.entrySet())) {
            ServerPlayer p = srv.getPlayerList().getPlayer(e.getKey());
            if (p == null) { S.remove(e.getKey()); continue; }
            step(p, e.getValue());
        }
        for (Iterator<TestShow> it = tests.values().iterator(); it.hasNext(); ) {
            TestShow t = it.next();
            if (t.fall()) {
                Fx.dyingTick(t.l(), t.x(), t.y(), t.z(), ++t.t()[0]);
                if (t.t()[0] >= DYING_TICKS) it.remove();
            } else {
                Fx.waitTick(t.l(), t.x(), t.y(), t.z(), t.t()[0]++);
                if (t.t()[0] > 220) it.remove();
            }
        }
    }

    static void step(ServerPlayer p, Session s) {
        s.tick++;
        ServerLevel lvl = (ServerLevel) p.level();
        switch (s.phase) {
            case DYING -> {
                freeze(p, s, true);
                heal(p);
                Fx.dyingTick(lvl, p.getX(), p.getY(), p.getZ(), s.tick);
                if (s.tick >= DYING_TICKS) toWait(p, s);
            }
            case WAIT -> {
                if (s.tick <= WAKE_TICKS) freeze(p, s, false);
                invul(p, true);
                Fx.waitTick(lvl, p.getX(), p.getY(), p.getZ(), s.tick - WAKE_TICKS);
                s.waitLeft--;
                s.bar.setProgress(Math.max(0f, Math.min(1f, (float) s.waitLeft / Math.max(1, s.waitTotal))));
                if (s.waitLeft % 20 == 0)
                    s.bar.setName(Fx.galaxy("Respawn dalam " + Fx.fmt(Math.max(0, s.waitLeft / 20))));
                if (s.waitLeft <= 0) startRevive(p, s);
            }
            case REVIVE -> {
                freeze(p, s, false);
                p.resetFallDistance();
                Fx.spiralUp(lvl, p.getX(), p.getY(), p.getZ(), s.tick);
                if (s.tick >= REVIVE_TICKS) toDescent(p, s);
            }
            case DESCENT -> {
                double y = s.gy + s.sky * (1.0 - s.tick / (double) DESCENT_TICKS);
                p.teleportTo(s.land, s.gx, y, s.gz, Set.<Relative>of(), s.gyaw, s.gpitch, false);
                p.resetFallDistance();
                Fx.stairs(s.land, s.gx, y, s.gz, s.tick);
                if (s.tick >= DESCENT_TICKS) finish(p, s);
            }
        }
    }

    // ---------- fase ----------
    static void toWait(ServerPlayer p, Session s) {
        Config c = Config.I;
        if (!c.set || s.skipWait) {
            if (!c.set) p.sendSystemMessage(Component.literal("§c[Nebula] /tempatmati belum diset, langsung respawn."));
            startRevive(p, s);
            return;
        }
        ServerLevel lvl = findLevel(c.dim);
        p.setNoGravity(false);
        p.teleportTo(lvl, c.x, c.y, c.z, Set.<Relative>of(), c.yaw, c.pitch, true);
        Fx.play(lvl, c.x, c.y + 0.9, c.z, "ENDERMAN_TELEPORT", 0.9f, 0.8f); // tiba di dunia nebula
        p.setGameMode(GameType.ADVENTURE);
        s.phase = Phase.WAIT;
        s.tick = 0;
        s.waitTotal = (int) (c.waitSeconds * 20);
        s.waitLeft = s.waitTotal;
        s.bar = new ServerBossEvent(UUID.randomUUID(), Fx.galaxy("Respawn dalam " + Fx.fmt((long) c.waitSeconds)),
                BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
        s.bar.setProgress(1f);
        s.bar.addPlayer(p);
        send(p, 2);
        broadcast(Fx.galaxy(p.getGameProfile().name() + " entering the world of nebula, " + Fx.fmt((long) c.waitSeconds)));
    }

    static void startRevive(ServerPlayer p, Session s) {
        if (s.bar != null) { s.bar.removeAllPlayers(); s.bar = null; }
        s.phase = Phase.REVIVE;
        s.tick = 0;
        p.setNoGravity(false);
        invul(p, true);
        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, REVIVE_TICKS + 20, 0, false, false, false));
        send(p, 3);
        broadcast(Fx.galaxy(p.getGameProfile().name() + " has been reborn"));
    }

    static void toDescent(ServerPlayer p, Session s) {
        p.removeEffect(MobEffects.LEVITATION);
        TeleportTransition tr = p.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.DO_NOTHING);
        boolean hasBed = p.getRespawnConfig() != null && !tr.missingRespawnBlock();
        if (hasBed) {
            s.land = tr.newLevel();
            s.gx = tr.position().x; s.gy = tr.position().y; s.gz = tr.position().z;
            s.gyaw = tr.yRot(); s.gpitch = tr.xRot();
            s.resist = false;
        } else {
            s.land = server.overworld();
            Vec3 v = randomSafe(s.land);
            s.gx = v.x; s.gy = v.y; s.gz = v.z;
            s.gyaw = p.getYRot(); s.gpitch = 0;
            s.resist = true;
        }
        s.sky = s.land.dimensionType().hasCeiling() ? 8 : 40;
        s.phase = Phase.DESCENT;
        s.tick = 0;
        p.setNoGravity(true);
        p.setDeltaMovement(Vec3.ZERO);
        p.teleportTo(s.land, s.gx, s.gy + s.sky, s.gz, Set.<Relative>of(), s.gyaw, s.gpitch, true);
        send(p, 4);
    }

    static void finish(ServerPlayer p, Session s) {
        p.teleportTo(s.land, s.gx, s.gy, s.gz, Set.<Relative>of(), s.gyaw, s.gpitch, false);
        p.setNoGravity(false);
        invul(p, false);
        p.setGameMode(s.prev);
        p.removeEffect(MobEffects.SLOWNESS);
        p.resetFallDistance();
        if (s.resist) p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 4, false, true, true)); // resistance 5, 5 detik
        Fx.burst(s.land, s.gx, s.gy + 1, s.gz);
        send(p, 0);
        S.remove(p.getUUID());
    }

    static Vec3 randomSafe(ServerLevel lvl) {
        var r = ThreadLocalRandom.current();
        for (int i = 0; i < 60; i++) {
            int x = r.nextInt(-1500, 1500), z = r.nextInt(-1500, 1500);
            lvl.getChunk(x >> 4, z >> 4);
            int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= lvl.getMinY() + 1) continue;
            BlockState b = lvl.getBlockState(new BlockPos(x, y - 1, z));
            if (b.isAir() || !b.getFluidState().isEmpty()) continue;
            if (b.is(Blocks.MAGMA_BLOCK) || b.is(Blocks.CACTUS) || b.is(Blocks.FIRE) || b.is(Blocks.CAMPFIRE)) continue;
            return new Vec3(x + 0.5, y, z + 0.5);
        }
        int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        return new Vec3(0.5, y, 0.5);
    }

    // ---------- command helpers ----------
    /** @return pesan error atau null jika sukses */
    public static String revive(ServerPlayer p) {
        Session s = S.get(p.getUUID());
        if (s == null) {
            s = new Session();
            s.prev = p.gameMode.getGameModeForPlayer();
            S.put(p.getUUID(), s);
            invul(p, true);
            startRevive(p, s);
            return null;
        }
        switch (s.phase) {
            case WAIT -> s.waitLeft = 0;
            case DYING -> s.skipWait = true;
            default -> { return "Player sedang dalam animasi respawn."; }
        }
        return null;
    }

    public static void testParticles(ServerPlayer p) {
        tests.put(p.getUUID(), new TestShow((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), new int[]{0}, false));
    }

    /** /tesjatuh: kamera jatuh + tangan turun (client) dan suara kematian (server), tanpa benar-benar mati. */
    public static void testFall(ServerPlayer p) {
        send(p, 1);
        tests.put(p.getUUID(), new TestShow((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), new int[]{0}, true));
    }

    // ---------- disconnect / join ----------
    public static void onLeave(ServerPlayer p) {
        Session s = S.remove(p.getUUID());
        if (s == null) return;
        if (s.bar != null) s.bar.removeAllPlayers();
        invul(p, false);
        p.setNoGravity(false);
        p.setGameMode(s.prev);
        pending.add(p.getUUID()); // revive otomatis saat join lagi
    }

    public static void onJoin(ServerPlayer p) {
        if (pending.remove(p.getUUID())) server.execute(() -> revive(p));
    }
}
