package pwn.noobs.trouserstreak.mixin;

import pwn.noobs.trouserstreak.KoodaAddon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient {

    @Shadow public abstract ClientPlayNetworkHandler getNetworkHandler();
    @Shadow public ClientPlayerEntity player;

    private long lastTitleUpdate = 0;

    @Inject(method = "getWindowTitle", at = @At("HEAD"), cancellable = true)
    private void onGetWindowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(generateKoodaTitle());
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        long now = System.currentTimeMillis();
        if (now - lastTitleUpdate > 500) {
            MinecraftClient.getInstance().getWindow().setTitle(generateKoodaTitle());
            lastTitleUpdate = now;
        }
    }

    private String generateKoodaTitle() {
        StringBuilder sb = new StringBuilder();

        sb.append("Kooda Addon v0.3.2");

        if (this.player != null) {
            sb.append(" | User: ").append(MinecraftClient.getInstance().getSession().getUsername());

            sb.append(" | FPS: ").append(MinecraftClient.getInstance().getCurrentFps());

            if (getNetworkHandler() != null && getNetworkHandler().getPlayerListEntry(this.player.getUuid()) != null) {
                sb.append(" | Ping: ").append(getNetworkHandler().getPlayerListEntry(this.player.getUuid()).getLatency()).append("ms");
            }

            if (MinecraftClient.getInstance().getCurrentServerEntry() != null) {
                String ip = MinecraftClient.getInstance().getCurrentServerEntry().address;
                sb.append(" | Server: ").append(ip);
            } else if (MinecraftClient.getInstance().isInSingleplayer()) {
                sb.append(" | Singleplayer");
            }
        } else {
            sb.append(" | Idle");
        }

        return sb.toString();
    }
}