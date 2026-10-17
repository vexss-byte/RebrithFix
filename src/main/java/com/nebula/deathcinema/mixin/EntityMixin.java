package com.nebula.deathcinema.mixin;

import com.nebula.deathcinema.client.ClientFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blokir putaran mouse (Entity.turn) pada player lokal selama animasi nebula. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 1)
    private void nebula$lockLook(double yRot, double xRot, CallbackInfo ci) {
        if (ClientFx.locked() && (Object) this == Minecraft.getInstance().player) ci.cancel();
    }
}
