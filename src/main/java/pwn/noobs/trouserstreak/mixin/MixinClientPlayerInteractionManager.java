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

        // Verify if module is active and configured to sync breaks
        if (module != null && module.isActive()) {
            // We force a sync request on the exact block being broken.
            // This ensures that if the break fails on the server, we get the update immediately.
            module.forceSync(pos);
        }
    }
}