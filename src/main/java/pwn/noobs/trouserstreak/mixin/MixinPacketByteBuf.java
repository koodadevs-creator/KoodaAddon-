package pwn.noobs.trouserstreak.mixin;

import pwn.noobs.trouserstreak.modules.AntiBookBan;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PacketByteBuf.class)
public abstract class MixinPacketByteBuf {

    @ModifyVariable(method = "writeNbt", at = @At("HEAD"), argsOnly = true)
    private NbtElement onWriteNbt(NbtElement element) {
        if (element != null) {

            if (AntiBookBan.shouldStrip(element.toString().length())) {

                return new NbtCompound();
            }
        }
        // Return original if safe
        return element;
    }
}