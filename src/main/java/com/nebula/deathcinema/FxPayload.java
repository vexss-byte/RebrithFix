package com.nebula.deathcinema;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** id: 0 clear, 1 death-fall, 2 wake(buka mata), 3 revive-orbit, 4 descent-orbit, 5 tes kedip, 6 tes orbit */
public record FxPayload(int id) implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("nebuladeath", "fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, FxPayload::id, FxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
