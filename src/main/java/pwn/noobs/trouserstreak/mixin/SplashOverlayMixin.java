package pwn.noobs.trouserstreak.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {

    private static final int KOODA_CYAN = 0xFF00FFFF;


    @Inject(method = "render", at = @At("HEAD"))
    private void forceBackgroundStart(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();


        context.fill(0, 0, width, height, KOODA_CYAN);
    }


    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"
            ),
            index = 4,
            require = 0
    )
    private int changeColorSimple(int color) {
        return swapRedForCyan(color);
    }


    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(Lnet/minecraft/client/render/RenderLayer;IIIII)V"
            ),
            index = 5,
            require = 0
    )
    private int changeColorLayer(int color) {
        return swapRedForCyan(color);
    }


    private int swapRedForCyan(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
      if (r > 200 && g < 150 && b < 150) {
            int alpha = (color >> 24) & 0xFF;

            return (alpha << 24) | (0 << 16) | (255 << 8) | 255;
        }
        return color;
    }
}