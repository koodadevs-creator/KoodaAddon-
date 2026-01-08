package pwn.noobs.trouserstreak.mixin;

import pwn.noobs.trouserstreak.modules.KoodaGrimVelocity;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity {

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(double x, double z, CallbackInfo ci) {
        KoodaGrimVelocity velocity = Modules.get().get(KoodaGrimVelocity.class);

        if (velocity != null && velocity.isActive() && velocity.noPushBlocks.get()) {
            ci.cancel();
        }
    }
}