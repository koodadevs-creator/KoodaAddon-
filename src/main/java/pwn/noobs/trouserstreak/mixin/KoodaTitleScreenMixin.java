package pwn.noobs.trouserstreak.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class KoodaTitleScreenMixin extends Screen {

    protected KoodaTitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        int shiftUp = 20;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int koodaButtonY = 0;

        // Adjust position of existing buttons to make space
        for (Object child : this.children()) {
            if (child instanceof ClickableWidget widget) {
                String widgetText = widget.getMessage().getString();

                if (widgetText.contains("Singleplayer") ||
                        widgetText.contains("Multiplayer") ||
                        widgetText.contains("Realms")) {

                    widget.setY(widget.getY() - shiftUp);

                    if (widgetText.contains("Realms")) {
                        koodaButtonY = widget.getY() + 24;
                    }
                }
            }
        }

        // Fallback position if Realms button isn't found
        if (koodaButtonY == 0) {
            koodaButtonY = (this.height / 4 + 48) + 72 - shiftUp;
        }

        int x = this.width / 2 - 100;

        // The Kooda Button: Copies Discord link and shows a Toast notification
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Kooda"), button -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) {
                        client.keyboard.setClipboard("https://discord.gg/h3WS6Qv2");

                        SystemToast.add(
                                client.getToastManager(),
                                SystemToast.Type.PERIODIC_NOTIFICATION,
                                Text.literal("Discord Copied"),
                                Text.literal("Join the Kooda community!")
                        );
                    }
                })
                .dimensions(x, koodaButtonY, buttonWidth, buttonHeight)
                .build());
    }
}