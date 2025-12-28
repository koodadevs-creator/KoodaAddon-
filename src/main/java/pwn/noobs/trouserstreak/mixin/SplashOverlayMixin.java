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

    private static final int KOODA_CYAN = 0xFF00FFFF; // Alpha 255, R 0, G 255, B 255

    // 1. INSTANT FIX: Force background paint immediately when render starts.
    // This fixes the "delay" issue where you see red for a split second.
    @Inject(method = "render", at = @At("HEAD"))
    private void forceBackgroundStart(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        // We manually fill the screen with Cyan immediately.
        // Since this is at "HEAD", it happens before Mojang draws the red background.
        // Later, Mojang will draw over this, but our ModifyArg below will catch that too.
        context.fill(0, 0, width, height, KOODA_CYAN);
    }

    // 2. FADE FIX: Intercept the color argument when the game tries to fill the background.
    // This handles the fade-out animation correctly.
    // We use require=0 to avoid crashes if signatures differ.
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

    // 3. LAYER FIX: Catches the version with RenderLayer if it exists (for newer versions).
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

    // Logic to detect Mojang Red and swap it
    private int swapRedForCyan(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // Detect Mojang Red (approx R239, G50, B61)
        if (r > 200 && g < 150 && b < 150) {
            int alpha = (color >> 24) & 0xFF;
            // Return Kooda Cyan with original Alpha
            return (alpha << 24) | (0 << 16) | (255 << 8) | 255;
        }
        return color;
    }
}