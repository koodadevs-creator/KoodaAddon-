package pwn.noobs.trouserstreak.mixin;

import pwn.noobs.trouserstreak.modules.KoodaGrimVelocity;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void onPushAwayFrom(Entity entity, CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity) {
            KoodaGrimVelocity velocity = Modules.get().get(KoodaGrimVelocity.class);

            if (velocity != null && velocity.isActive() && velocity.noPushEntities.get()) {
                ci.cancel();
            }
        }
    }
}