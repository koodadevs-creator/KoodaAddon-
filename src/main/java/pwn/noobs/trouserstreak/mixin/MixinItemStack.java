package pwn.noobs.trouserstreak.mixin;

import pwn.noobs.trouserstreak.modules.AntiBookBan;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public class MixinItemStack {

    @Inject(method = "getTooltip", at = @At("HEAD"), cancellable = true)
    private void onGetTooltip(
            Item.TooltipContext context,
            @Nullable PlayerEntity player,
            TooltipType type,
            CallbackInfoReturnable<List<Text>> cir
    ) {
        if (AntiBookBan.isEffective()) {
            ItemStack self = (ItemStack) (Object) this;

            // Check against the static limit
            if (self.toString().length() > AntiBookBan.LIMIT) {
                cir.setReturnValue(List.of(
                        Text.literal("§c[AntiBookBan] Too Heavy!"),
                        Text.literal("§7Data stripped (" + self.toString().length() + " chars)")
                ));
            }
        }
    }
}