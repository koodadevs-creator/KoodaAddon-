package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import pwn.noobs.trouserstreak.utils.ILivingEntity;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class NoJumpDelay extends Module {

    public NoJumpDelay() {
        super(KoodaAddon.KOODA_EXPLOIT, "no-jump-delay", "Removes the delay between jumps.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player != null) {
            ((ILivingEntity) mc.player).setJumpCooldown(0);
        }
    }
}