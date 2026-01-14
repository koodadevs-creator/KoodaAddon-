package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import pwn.noobs.trouserstreak.KoodaAddon;

public class KoodaNoPop extends Module {
    private int attackTimer = 0;

    public KoodaNoPop() {
        super(KoodaAddon.KOODA_MOVEMENT, "kooda-no-pop", "Cancels OnGround packets specifically when attacking with Mace.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (attackTimer > 0) {
            attackTimer--;
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null) return;

        if (event.packet instanceof PlayerInteractEntityC2SPacket) {
            IPlayerInteractEntityC2SPacket packet = (IPlayerInteractEntityC2SPacket) event.packet;
            if (packet.meteor$getType() == PlayerInteractEntityC2SPacket.InteractType.ATTACK) {
                if (mc.player.getMainHandStack().getItem() == Items.MACE) {
                    attackTimer = 5;
                }
            }
        }

        if (attackTimer > 0 && event.packet instanceof PlayerMoveC2SPacket movePacket) {
            if (movePacket.isOnGround()) {
                event.cancel();
            }
        }
    }
}