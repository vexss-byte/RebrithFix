package com.nebula.deathcinema.client;

import com.nebula.deathcinema.FxPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class NebulaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(FxPayload.TYPE, (payload, ctx) -> ClientFx.start(payload.id()));
        ClientTickEvents.END_CLIENT_TICK.register(ClientFx::handTick); // tangan turun perlahan saat jatuh

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("nebuladeath", "fx"), (graphics, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            for (int[] r : ClientFx.overlay(w, h)) graphics.fill(r[0], r[1], r[2], r[3], r[4]);
        });
    }
}
