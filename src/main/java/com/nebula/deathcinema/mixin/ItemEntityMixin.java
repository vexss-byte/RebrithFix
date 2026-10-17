package com.nebula.deathcinema.mixin;

import com.nebula.deathcinema.DeathManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blokir pemungutan item oleh player yang sedang dalam animasi nebula. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true, require = 1)
    private void nebula$noPickup(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp && DeathManager.blocksPickup(sp)) ci.cancel();
    }
}
