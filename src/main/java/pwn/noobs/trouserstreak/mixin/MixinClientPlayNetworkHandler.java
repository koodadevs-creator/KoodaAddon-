package pwn.noobs.trouserstreak.mixin;

import pwn.noobs.trouserstreak.modules.AntiBookBan;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {

    // 1. Intercept full inventory packets
    @Inject(method = "onInventory", at = @At("HEAD"))
    private void onInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        if (AntiBookBan.isEffective()) {
            List<ItemStack> stacks = ((InventoryS2CPacketAccessor) (Object) packet).getContents();
            for (ItemStack stack : stacks) {
                cleanStack(stack);
            }
        }
    }

    // 2. Intercept single slot updates
    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
    private void onSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        if (AntiBookBan.isEffective()) {
            cleanStack(packet.getStack());
        }
    }

    // Utility method to strip data
    @Unique
    private void cleanStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        // Use the hardcoded static limit directly
        int limit = AntiBookBan.LIMIT;
        String itemString = stack.toString();
        int itemLength = itemString.length();

        if (itemLength > limit) {
            // Strip the data components to prevent the crash
            stack.applyComponentsFrom(ItemStack.EMPTY.getComponents());

            // Notify the user in chat
            if (Modules.get().get(AntiBookBan.class).isActive()) {
                ChatUtils.info("AntiBookBan", "Blocked heavy item! (Size: " + itemLength + ")");
            }
        }
    }
}