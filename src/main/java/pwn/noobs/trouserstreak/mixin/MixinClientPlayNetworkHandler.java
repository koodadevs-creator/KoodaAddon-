package pwn.noobs.trouserstreak.mixin;

import pwn.noobs.trouserstreak.modules.AntiBookBan;
import meteordevelopment.meteorclient.systems.modules.Modules;
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

    @Inject(method = "onInventory", at = @At("HEAD"))
    private void onInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        if (Modules.get().get(AntiBookBan.class).isActive()) {
            List<ItemStack> stacks = ((InventoryS2CPacketAccessor) (Object) packet).getContents();
            for (ItemStack stack : stacks) {
                cleanStack(stack);
            }
        }
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
    private void onSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        if (Modules.get().get(AntiBookBan.class).isActive()) {
            cleanStack(packet.getStack());
        }
    }

    @Unique
    private void cleanStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        String itemString = stack.toString();
        int len = itemString.length();

        if (AntiBookBan.shouldStrip(len)) {
            stack.applyComponentsFrom(ItemStack.EMPTY.getComponents());
            AntiBookBan.notifyStrip(len);
        }
    }
}