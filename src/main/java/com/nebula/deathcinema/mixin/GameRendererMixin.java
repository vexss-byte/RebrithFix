package com.nebula.deathcinema.mixin;

import com.nebula.deathcinema.client.ClientFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Memakai blur shader bawaan Minecraft (post-effect "blur", yang dipakai background menu). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow public abstract void processBlurEffect();

    @Inject(method = "render", at = @At("TAIL"), require = 1)
    private void nebula$blur(CallbackInfo ci) {
        int[] b = ClientFx.blur();
        if (b == null) return;
        var opt = Minecraft.getInstance().options.menuBackgroundBlurriness();
        int old = opt.get();
        try {
            opt.set(b[0]);                       // radius blur
            for (int i = 0; i < b[1]; i++) processBlurEffect(); // tiap pass = makin buram
        } finally {
            opt.set(old);
        }
    }
}
