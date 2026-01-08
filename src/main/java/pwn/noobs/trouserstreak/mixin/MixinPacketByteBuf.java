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

    // Used @ModifyVariable instead of @ModifyArg.
    // This correctly intercepts the input argument at the start (HEAD) of the method.
    // We use NbtElement because the 1.21 signature is writeNbt(NbtElement).
    @ModifyVariable(method = "writeNbt", at = @At("HEAD"), argsOnly = true)
    private NbtElement onWriteNbt(NbtElement element) {
        if (element != null) {
            // Check if the NBT data is too large (AntiBookBan logic)
            if (AntiBookBan.shouldStrip(element.toString().length())) {
                // Return an empty NBT Compound to prevent the kick
                return new NbtCompound();
            }
        }
        // Return original if safe
        return element;
    }
}