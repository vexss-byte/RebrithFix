package com.nebula.deathcinema;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class NebulaDeath implements ModInitializer {
    /** Jika versi 26.3 mengubah API permission, cukup ubah baris ini. */
    static final Predicate<CommandSourceStack> OP = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    @Override
    public void onInitialize() {
        Config.load();
        PayloadTypeRegistry.clientboundPlay().register(FxPayload.TYPE, FxPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTING.register(s -> DeathManager.server = s);
        ServerTickEvents.END_SERVER_TICK.register(DeathManager::tick);

        // batalkan kematian vanilla -> pakai sinematik kita
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer p) { DeathManager.onDeath(p); return false; }
            return true;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((h, srv) -> DeathManager.onLeave(h.getPlayer()));
        ServerPlayConnectionEvents.JOIN.register((h, sender, srv) -> DeathManager.onJoin(h.getPlayer()));

        CommandRegistrationCallback.EVENT.register((d, ctx, env) -> {
            d.register(literal("matikan").requires(OP)
                    .then(argument("nama_player", EntityArgument.player()).executes(c -> {
                        ServerPlayer p = EntityArgument.getPlayer(c, "nama_player");
                        if (DeathManager.busy(p)) { c.getSource().sendFailure(Component.literal("Player sedang dalam proses.")); return 0; }
                        DeathManager.begin(p);
                        c.getSource().sendSuccess(() -> Component.literal("§bNebula: " + p.getGameProfile().name() + " dimatikan."), true);
                        return 1;
                    })));

            d.register(literal("hidupin").requires(OP)
                    .then(argument("nama_player", EntityArgument.player()).executes(c -> {
                        ServerPlayer p = EntityArgument.getPlayer(c, "nama_player");
                        String err = DeathManager.revive(p);
                        if (err != null) { c.getSource().sendFailure(Component.literal(err)); return 0; }
                        c.getSource().sendSuccess(() -> Component.literal("§bNebula: " + p.getGameProfile().name() + " dihidupkan."), true);
                        return 1;
                    })));

            d.register(literal("setwaktu").requires(OP)
                    .then(argument("minute", DoubleArgumentType.doubleArg(0.05, 1440)).executes(c -> {
                        double m = DoubleArgumentType.getDouble(c, "minute");
                        Config.I.waitSeconds = m * 60;
                        Config.save();
                        c.getSource().sendSuccess(() -> Component.literal("§bWaktu tunggu: " + m + " menit"), true);
                        return 1;
                    })));

            d.register(literal("tempatmati").requires(OP).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                Config cf = Config.I;
                cf.set = true;
                cf.dim = p.level().dimension().toString();
                cf.x = p.getX(); cf.y = p.getY(); cf.z = p.getZ();
                cf.yaw = p.getYRot(); cf.pitch = p.getXRot();
                Config.save();
                c.getSource().sendSuccess(() -> Component.literal("§bTempat mati diset di posisimu sekarang."), true);
                return 1;
            }));

            d.register(literal("teskedipan").requires(OP).executes(c -> {
                DeathManager.send(c.getSource().getPlayerOrException(), 5);
                return 1;
            }));

            d.register(literal("tespartikel").requires(OP).executes(c -> {
                DeathManager.testParticles(c.getSource().getPlayerOrException());
                return 1;
            }));

            d.register(literal("tesorbit").requires(OP).executes(c -> {
                DeathManager.send(c.getSource().getPlayerOrException(), 6);
                return 1;
            }));

            d.register(literal("tesjatuh").requires(OP).executes(c -> { // tes kamera jatuh ke kanan (tanpa mati)
                DeathManager.send(c.getSource().getPlayerOrException(), 1);
                return 1;
            }));

            d.register(literal("batalnebula").requires(OP).executes(c -> { // hapus efek layar yang nyangkut
                DeathManager.send(c.getSource().getPlayerOrException(), 0);
                return 1;
            }));
        });
    }
}
