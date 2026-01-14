package pwn.noobs.trouserstreak.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import pwn.noobs.trouserstreak.utils.ILivingEntity; // Import from utils

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements ILivingEntity {

    @Shadow(aliases = "field_6281")
    private int jumpingCooldown;

    @Override
    public void setJumpCooldown(int ticks) {
        this.jumpingCooldown = ticks;
    }
}