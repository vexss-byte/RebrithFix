package com.nebula.deathcinema.mixin;

import com.nebula.deathcinema.client.ClientFx;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow private boolean detached;
    @Shadow @Final private Quaternionf rotation;

    // "update" (versi baru) atau "setup" (versi lama) - salah satu yang ada akan dipakai
    @Inject(method = {"update", "setup"}, at = @At("TAIL"), require = 1)
    private void nebula$cinematic(CallbackInfo ci) {
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double[] c = ClientFx.camera(pt);
        if (c == null) return;
        setPosition(c[0], c[1], c[2]);
        setRotation((float) c[3], (float) c[4]);
        if (c[5] != 0) rotation.rotateZ((float) Math.toRadians(-c[5]));
        if (c[6] > 0.5) detached = true;
    }
}
