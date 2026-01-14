package pwn.noobs.trouserstreak.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pwn.noobs.trouserstreak.modules.KoodaNoGlitchBlocks;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class MixinClientPlayerInteractionManager {

    /**
     * Injects logic into the block breaking sequence.
     * Checks if the module is active and forces a sync packet if necessary.
     */
    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> info) {
        KoodaNoGlitchBlocks module = Modules.get().get(KoodaNoGlitchBlocks.class);


        if (module != null && module.isActive()) {

            module.forceSync(pos);
        }
    }
}